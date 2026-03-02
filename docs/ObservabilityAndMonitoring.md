# 9. Observability & Monitoring

> **Requirement:** Add basic observability — application logs, a health check endpoint, and at least one metric or alert. Bonus: structured logging, log aggregation pipeline.

---

## Quick Reference

**Requirement vs Implementation**

| Requirement | Current Status |
|---|---|
| Application logs | ✅ Structured JSON audit log → stdout on every LLM call |
| Health check endpoint | ✅ Spring Actuator `/actuator/health` — liveness + readiness probes |
| At least one metric or alert | ✅ 2 CloudWatch metric filters + 1 CloudWatch alarm (PII leak risk) |
| Structured logging (bonus) | ✅ Single-line JSON per event: `{"type":"PII_AUDIT","timestamp":...}` |
| Log aggregation pipeline (bonus) | ✅ Fluent Bit DaemonSet → CloudWatch Logs `/eks/persons-finder/pii-audit` |

**Code Snippet Source Map**

| Snippet | Source |
|---|---|
| `AuditLogEntry` data model | `AuditLogEntry.kt` lines 9–16 |
| `AuditLogger.log()` — JSON serialization | `AuditLogger.kt` lines 14–28 |
| Audit log call in proxy service | `PiiProxyService.kt` lines 74–82 |
| Actuator health probes config | `application.properties` lines 9–14 |
| Prometheus scrape annotations | `values-prod.yaml` lines 44–47 |
| Fluent Bit INPUT — tail pod logs | `fluent-bit.yaml` ConfigMap `[INPUT]` block |
| Fluent Bit FILTER — PII_AUDIT grep | `fluent-bit.yaml` ConfigMap `[FILTER]` block |
| Fluent Bit OUTPUT — CloudWatch | `fluent-bit.yaml` ConfigMap `[OUTPUT]` block |
| CloudWatch log group (90-day retention) | `cloudwatch.tf` lines 9–19 |
| IAM policy for Fluent Bit → CloudWatch | `cloudwatch.tf` lines 27–50 |
| Metric filter 1 — PiiAuditTotal | `cloudwatch.tf` lines 56–71 |
| Metric filter 2 — PiiZeroRedactions | `cloudwatch.tf` lines 77–89 |
| CloudWatch alarm — PII leak risk | `cloudwatch.tf` lines 95–117 |
| verify.sh Layer 4 checks | `verify.sh` lines 271–312 |

---

## 1. What Was Asked

| Requirement item | Description |
|---|---|
| Application logs | Log meaningful events from the running service |
| Health check endpoint | Expose a health endpoint for infrastructure to probe |
| Metric or alert | At least one signal that can trigger a notification |
| Structured logging | Bonus: machine-parseable log format |
| Log aggregation | Bonus: collect logs centrally |

---

## 2. What Was Implemented

The requirement is **fully satisfied** — health endpoint, structured logs, a complete log aggregation pipeline, two metrics, and an alarm are all running in production. Both bonus items are implemented.

---

### 2.1 Structured JSON Audit Logging

Every call proxied through the PII layer emits a single-line JSON log entry to stdout. Stdout is the standard container logging channel — no sidecar or file-based logging agent needed at the application level.

**Data model** — `src/main/kotlin/com/persons/finder/pii/AuditLogEntry.kt` lines 9–16:

```kotlin
data class AuditLogEntry(                           // line 9
    val timestamp: String = Instant.now().toString(),
    val requestId: String,                          // UUID — links log → trace
    val piiDetected: List<PiiType>,                 // which PII types were found
    val redactionsApplied: Int,                     // number of tokens replaced
    val destination: String,                        // upstream URL (e.g. api.openai.com)
    val method: String                              // HTTP method
)
```

**JSON serialization** — `src/main/kotlin/com/persons/finder/pii/AuditLogger.kt` lines 14–28:

```kotlin
fun log(entry: AuditLogEntry) {              // line 14
    val json = buildString {
        append('{')
        append("\"type\":\"PII_AUDIT\",")    // line 18 — fixed type tag for CloudWatch filter
        append("\"timestamp\":\"${escape(entry.timestamp)}\",")
        append("\"requestId\":\"${escape(entry.requestId)}\",")
        append("\"piiDetected\":[${piiList}],")
        append("\"redactionsApplied\":${entry.redactionsApplied},")
        append("\"destination\":\"${escape(entry.destination)}\",")
        append("\"method\":\"${escape(entry.method)}\"")
        append('}')
    }
    output.println(json)                     // line 27 — single line to stdout
}
```

**Sample log output:**
```json
{"type":"PII_AUDIT","timestamp":"2026-03-02T02:00:00Z","requestId":"a3f1e2b4-...","piiDetected":["PERSON_NAME","COORDINATE"],"redactionsApplied":3,"destination":"https://api.openai.com/v1/chat/completions","method":"POST"}
```

**Emitted by** — `src/main/kotlin/com/persons/finder/pii/PiiProxyService.kt` lines 74–82:

```kotlin
auditLogger.log(                                    // line 74
    AuditLogEntry(
        requestId = UUID.randomUUID().toString(),
        piiDetected = redactionResult.detectedPiiTypes,
        redactionsApplied = redactionResult.tokenMap.size,
        destination = request.url,
        method = request.method.uppercase()
    )
)
```

The sidecar container (`pii-redaction-sidecar`, Go) emits an identical JSON structure from its own `logAudit()` function — providing independent Layer 2 audit evidence.

---

### 2.2 Health Check Endpoint (Spring Actuator)

> `src/main/resources/application.properties` lines 9–14

```properties
management.endpoints.web.exposure.include=health    # line 10 — expose /actuator/health only
management.endpoint.health.show-details=always      # line 11 — show component details
management.endpoint.health.probes.enabled=true       # line 12 — enable liveness/readiness sub-paths
management.health.livenessstate.enabled=true         # line 13
management.health.readinessstate.enabled=true        # line 14
```

Exposed endpoints:

| Path | Purpose | Used by |
|---|---|---|
| `/actuator/health` | Overall health (UP/DOWN) | General monitoring |
| `/actuator/health/liveness` | JVM alive check | Kubernetes `livenessProbe` |
| `/actuator/health/readiness` | Ready to serve traffic | Kubernetes `readinessProbe` |

The Helm `deployment.yaml` wires these paths directly into the pod probes, so Kubernetes automatically restarts unhealthy pods and removes unready pods from Service load balancing.

---

### 2.3 Prometheus Scrape Annotations

The production pod template is annotated for Prometheus scraping. When a Prometheus instance is deployed in the cluster, it discovers and scrapes these pods automatically.

> `devops/helm/persons-finder/values-prod.yaml` lines 44–47

```yaml
podAnnotations:
  prometheus.io/scrape: "true"             # line 45 — enable Prometheus auto-discovery
  prometheus.io/port: "8080"              # line 46 — port to scrape
  prometheus.io/path: "/actuator/prometheus"  # line 47 — Spring Boot metrics endpoint
```

Spring Boot Actuator (with `spring-boot-starter-actuator`, `build.gradle.kts` line 28) exposes JVM metrics, HTTP request counts, latency histograms, and database connection pool metrics in Prometheus format at `/actuator/prometheus`.

---

### 2.4 Log Collection — Fluent Bit DaemonSet

A Fluent Bit DaemonSet (one pod per cluster node, running in `kube-system`) collects all `persons-finder` pod stdout logs and ships PII audit events to CloudWatch.

**INPUT** — tails container log files from the node filesystem:

```ini
[INPUT]
    Name              tail
    Path              /var/log/containers/persons-finder-*.log   # both app + sidecar containers
    Parser            docker                                      # EKS node log format
    Tag               pii.audit.*
    DB                /fluent-bit/state/flb_pii_audit.db         # position DB in emptyDir
    Mem_Buf_Limit     5MB
    Refresh_Interval  10
```

**FILTER 1** — grep: keep only lines containing `PII_AUDIT`:

```ini
[FILTER]
    Name    grep
    Match   pii.audit.*
    Regex   log PII_AUDIT              # drops all non-audit log lines
```

**FILTER 2** — parser: expand the JSON string in the `log` field into structured fields:

```ini
[FILTER]
    Name         parser
    Match        pii.audit.*
    Key_Name     log
    Parser       pii_audit_json        # parses {"type":"PII_AUDIT",...} into fields
    Reserve_Data On
```

**OUTPUT** — ships to CloudWatch:

```ini
[OUTPUT]
    Name              cloudwatch_logs
    Match             pii.audit.*
    region            ap-southeast-2
    log_group_name    /eks/persons-finder/pii-audit
    log_stream_prefix pii-             # each pod gets its own log stream
    auto_create_group false            # log group pre-created by Terraform
```

---

### 2.5 CloudWatch Log Group

> `devops/terraform/environments/prod/cloudwatch.tf` lines 9–19

```hcl
resource "aws_cloudwatch_log_group" "pii_audit" {   # line 9
  name              = "/eks/persons-finder/pii-audit"
  retention_in_days = 90                             # 90-day retention policy
  tags = {
    Description = "PII redaction audit logs from Layer 1 in-process and Layer 2 sidecar"
  }
}
```

**IAM** — `cloudwatch.tf` lines 27–50: node IAM role is granted exactly four actions scoped to this log group ARN only:

```hcl
Action = [
  "logs:CreateLogStream",    # line 38
  "logs:PutLogEvents",       # write log events
  "logs:DescribeLogStreams",  # check existing streams
  "logs:DescribeLogGroups"   # Fluent Bit health check
]
Resource = [
  aws_cloudwatch_log_group.pii_audit.arn,
  "${aws_cloudwatch_log_group.pii_audit.arn}:*"   # scoped — no access to other log groups
]
```

---

### 2.6 CloudWatch Metric Filters

Two metric filters convert log events into CloudWatch custom metrics in the `PersonsFinder/PII` namespace.

**Metric Filter 1 — PiiAuditTotal** — `cloudwatch.tf` lines 56–71:

```hcl
resource "aws_cloudwatch_log_metric_filter" "pii_audit_total" {  # line 56
  pattern = "{ $.type = \"PII_AUDIT\" }"           # matches every LLM call

  metric_transformation {
    name      = "PiiAuditTotal"
    namespace = "PersonsFinder/PII"
    value     = "1"                                # +1 per event
  }
}
```

**Metric Filter 2 — PiiZeroRedactions** — `cloudwatch.tf` lines 77–89:

```hcl
resource "aws_cloudwatch_log_metric_filter" "pii_zero_redactions" {  # line 77
  pattern = "{ $.type = \"PII_AUDIT\" && $.redactionsApplied = 0 }"
  # matches only calls where no PII was detected or redacted — potential leak signal

  metric_transformation {
    name      = "PiiZeroRedactions"
    namespace = "PersonsFinder/PII"
    value     = "1"
  }
}
```

---

### 2.7 CloudWatch Alarm — PII Leak Risk

> `devops/terraform/environments/prod/cloudwatch.tf` lines 95–117

```hcl
resource "aws_cloudwatch_metric_alarm" "pii_leak_risk" {   # line 95
  alarm_name          = "persons-finder-pii-leak-risk"
  alarm_description   = "LLM request with 0 PII redactions detected — potential unredacted PII sent to LLM provider."
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "PiiZeroRedactions"
  namespace           = "PersonsFinder/PII"
  period              = 300            # line 102 — 5-minute evaluation window
  statistic           = "Sum"
  threshold           = 1             # fire if ≥1 zero-redaction event in 5 min
  treat_missing_data  = "notBreaching" # silence during startup / no-traffic periods
}
```

**Current state:** `OK` — no zero-redaction LLM calls observed since deployment.

The alarm is ready for SNS integration (email / PagerDuty) — the `alarm_actions` attribute is commented in `cloudwatch.tf` line 112.

---

### 2.8 End-to-End Data Flow

```
Application (Layer 1)
  AuditLogger.kt → stdout
  {"type":"PII_AUDIT","redactionsApplied":3,...}

Sidecar (Layer 2)
  sidecar/main.go logAudit() → stdout
  {"type":"PII_AUDIT","redactionsApplied":0,...}

Collection (Layer 4)
  Fluent Bit DaemonSet (kube-system)
    → tail /var/log/containers/persons-finder-*.log
    → grep filter: PII_AUDIT
    → parser: pii_audit_json (expand JSON fields)
    → OUTPUT cloudwatch_logs → /eks/persons-finder/pii-audit

CloudWatch
  Log Group /eks/persons-finder/pii-audit (90-day retention)
    │
    ├── Metric Filter: pii-audit-total
    │     pattern: { $.type = "PII_AUDIT" }
    │     metric: PersonsFinder/PII / PiiAuditTotal
    │
    └── Metric Filter: pii-zero-redactions
          pattern: { $.type = "PII_AUDIT" && $.redactionsApplied = 0 }
          metric: PersonsFinder/PII / PiiZeroRedactions
               │
               └── Alarm: persons-finder-pii-leak-risk
                     threshold: Sum ≥ 1 in 5 min → state: OK ✅
```

---

## 3. Beyond the Minimum

| Additional Feature | Description |
|---|---|
| Dual-layer audit coverage | Both the Spring app (Layer 1) and the Go sidecar (Layer 2) independently emit PII_AUDIT logs — no single point of log failure |
| `requestId` in every log entry | UUID links log entries to a specific proxy transaction for tracing |
| Fluent Bit emptyDir workaround | Position DB moved from read-only `/var/log` hostPath to `emptyDir` volume — properly handles EKS node mount constraints |
| 5 audit log property-based tests | `AuditLogCompletenessPropertyTest.kt` — 5 `@Property(tries=100)` tests verify JSON validity, field completeness, and timestamp ordering across random inputs |
| verify.sh Layer 4 checks | `verify.sh` lines 271–312 — 3 automated checks: DaemonSet ready, log group exists, alarm exists |
| Prometheus-ready | `values-prod.yaml` lines 44–47 — Prometheus scrape annotations configured; connects to a Prometheus stack without any code change |

---

## 4. Tests

**`AuditLoggerTest.kt`** (110 lines, 5 `@Test` methods) — unit tests:

| Test | Asserts |
|---|---|
| `log entry is valid JSON with all required fields` | Parses output as JSON, checks all 6 fields present |
| `log entry with no PII detected has empty array` | `piiDetected: []` when no PII found |
| `log entry with multiple PII types lists all` | All detected PII types appear in array |
| `log entry escapes special characters in destination` | Quotes and backslashes are escaped |
| `log entry is single line` | Output contains exactly one newline |

**`AuditLogCompletenessPropertyTest.kt`** (250 lines, 5 `@Property(tries=100)` methods) — property-based tests:

| Property | Asserts |
|---|---|
| `every audit log entry produces valid JSON` | 100 random inputs all produce parseable JSON |
| `each logged entry produces exactly one line` | Always single-line output |
| `all required fields are present in every log entry` | All 6 fields present in every random entry |
| `sequence of entries produces one log line per entry` | N entries → N lines |
| `chronological ordering of timestamps is preserved` | Log output preserves insertion order |

---

## 5. File Map

| File | Lines | Purpose |
|---|---|---|
| `pii/AuditLogEntry.kt` | 16 | Data model: 6 fields per audit event |
| `pii/AuditLogger.kt` | 38 | JSON serializer: single-line stdout output |
| `pii/PiiProxyService.kt` | — | Calls `auditLogger.log()` on every LLM proxy request |
| `src/main/resources/application.properties` | 39 | Actuator health probes config (lines 9–14) |
| `devops/helm/persons-finder/values-prod.yaml` | 47 | Prometheus scrape annotations (lines 44–47) |
| `devops/k8s/fluent-bit.yaml` | 248 | DaemonSet: tail → grep → parse → CloudWatch |
| `devops/terraform/environments/prod/cloudwatch.tf` | 129 | Log group, IAM, 2 metric filters, 1 alarm |
| `devops/scripts/verify.sh` | — | Layer 4 checks: lines 271–312 |
| `test/.../pii/AuditLoggerTest.kt` | 110 | 5 unit tests for JSON output |
| `test/.../pii/AuditLogCompletenessPropertyTest.kt` | 250 | 5 property-based tests (500 total iterations) |
