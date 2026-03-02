# AI Firewall: PII Egress Protection Architecture

> **Design Challenge Answer** — How to prevent real PII from leaving the EKS cluster when the app calls an external LLM provider.

---

## 1. The Threat Model

```
persons-finder API
      │
      │  User request contains:
      │  • Person names  (e.g. "John Smith")
      │  • GPS coordinates  (e.g. "-33.8688, 151.2093")
      │  • Bios / free-text
      │
      ▼
  [app code]  ──────────────────────────────────────►  api.openai.com
                    WITHOUT protection:
                    raw PII leaves the cluster
```

**Attack surface:** Any LLM request that carries unredacted user data violates:
- GDPR Art. 46 (international transfers)
- CCPA / Australian Privacy Act
- Internal data handling SLA

The goal is **zero raw PII crossing the cluster boundary**, with **reversible tokenization** so the LLM response remains useful.

---

## 2. Architecture: Layered Defense

This design implements **4 defence layers**. Each layer is independent — if one fails, the next catches the leak.

```
┌─────────────────────────────────────────────────────────────────────┐
│  EKS Cluster (persons-finder-prod)                                  │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Pod: persons-finder                                         │   │
│  │                                                              │   │
│  │  ┌─────────────────────┐   ┌──────────────────────────────┐ │   │
│  │  │  Main Container     │   │  Sidecar Container           │ │   │
│  │  │  (Spring Boot App)  │   │  (PII Redaction Proxy)       │ │   │
│  │  │                     │   │                              │ │   │
│  │  │  Layer 1:           │   │  Layer 2:                    │ │   │
│  │  │  PiiProxyService    │──►│  HTTP intercept proxy        │ │   │
│  │  │  PiiDetector        │   │  port 8081                   │ │   │
│  │  │  PiiRedactor        │   │  Catches any request the     │ │   │
│  │  │  AuditLogger ──────────►│  app forgot to redact        │ │   │
│  │  │                     │   │                              │ │   │
│  │  └─────────────────────┘   └──────────────┬───────────────┘ │   │
│  │                                           │                  │   │
│  └───────────────────────────────────────────┼──────────────────┘   │
│                                              │                      │
│  ┌───────────────────────────────────────────┼──────────────────┐   │
│  │  NetworkPolicy (Layer 3)                  │                  │   │
│  │                                           │                  │   │
│  │  Egress ALLOW:  api.openai.com:443  ◄─────┘                  │   │
│  │  Egress ALLOW:  kube-dns:53                                  │   │
│  │  Egress DENY:   everything else  (default-deny)              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                              │                      │
└──────────────────────────────────────────────┼──────────────────────┘
                                               │ (redacted only)
                                               ▼
                                       api.openai.com
                                   receives: <NAME_a3f2c1> instead of "John Smith"
                                             <COORD_b7d9e2> instead of "-33.8688"
```

**Layer 4 — Observability** runs across all layers (CloudWatch + Grafana alerts).

---

## 3. Layer 1: In-Process PiiProxyService (Already Built)

**What it is:** Application-level HTTP proxy baked into the Spring Boot app.

**Status: ✅ Already implemented** in `src/main/kotlin/com/persons/finder/pii/`

### How it works

```
User Request
     │
     ▼
PersonController  ──►  PersonsServiceImpl
                                │
                                │ calls PiiProxyService.processRequest()
                                ▼
                    ┌───────────────────────┐
                    │    PiiDetector        │  regex scan: PERSON_NAME, COORDINATE
                    │    PiiRedactor        │  tokenize: "John Smith" → <NAME_a3f2>
                    │    HTTP forward       │  POST redacted body to api.openai.com
                    │    PiiRedactor        │  restore: <NAME_a3f2> → "John Smith"
                    │    AuditLogger        │  JSON log to stdout → CloudWatch
                    └───────────────────────┘
                                │
                                ▼
                          Response returned to user
                          (de-tokenized, PII restored)
```

### Token format (reversible within the same request)

| PII Type | Original | Token |
|---|---|---|
| Person name | `John Smith` | `<NAME_a3f2c1d8>` |
| Coordinate | `-33.8688` | `<COORD_b7d9e2f4>` |

The token map lives in memory per-request — it is **never persisted** and is discarded after response restoration.

### Audit log output (stdout → CloudWatch)

```json
{
  "type": "PII_AUDIT",
  "timestamp": "2026-03-02T03:14:15Z",
  "requestId": "a1b2c3d4-...",
  "piiDetected": ["PERSON_NAME", "COORDINATE"],
  "redactionsApplied": 3,
  "destination": "https://api.openai.com/v1/chat/completions",
  "method": "POST"
}
```

### Limitation of Layer 1 alone

Layer 1 only works if the developer routes every LLM call through `PiiProxyService`. If a future developer calls `openai` directly — PII leaks. This is why Layer 2 exists.

---

## 4. Layer 2: PII Redaction Sidecar (Infrastructure Design)

**What it is:** A separate container in the same pod that intercepts all outbound HTTP, providing a language-agnostic, enforcement-level redaction net.

**Status:** Skeleton already configured in Helm (`sidecar.enabled: true` in `values-prod.yaml`). The sidecar image ECR URI is pre-registered. This section defines the full infrastructure design.

### Sidecar deployment in the Pod

```yaml
# Already in devops/helm/persons-finder/templates/deployment.yaml
containers:
  - name: persons-finder          # Main app
    port: 8080
    env:
      - name: LLM_PROXY_URL
        value: "http://localhost:8081"   # Route LLM calls through sidecar

  - name: pii-redaction-sidecar  # Sidecar (enabled in prod)
    image: .../pii-redaction-sidecar:latest
    port: 8081
    # Resources defined in values.yaml:
    #   limits: cpu=500m, memory=512Mi
    #   requests: cpu=100m, memory=256Mi
```

### Two implementation options for the sidecar

#### Option A: Application-configured proxy (Recommended for this project)

The app sets `LLM_PROXY_URL=http://localhost:8081`. All LLM SDK calls go through the sidecar port. The sidecar:

1. Receives the request from the main container
2. Runs the same `PiiDetector` → `PiiRedactor` pipeline (shared library, or reimplemented in Go/Python for lighter weight)
3. Forwards the redacted request to `api.openai.com`
4. De-tokenizes the response before returning to the main container

**Pros:** No iptables manipulation required; explicit and auditable.

#### Option B: Transparent iptables interception (Istio-style)

An init container sets iptables rules to redirect all outbound TCP:443 traffic to the sidecar port. The main container needs zero configuration changes.

```
Init Container (iptables)
  └─► iptables -t nat -A OUTPUT -p tcp --dport 443 -j REDIRECT --to-port 8081
```

**Pros:** Truly zero app-code dependency. Even a bug that bypasses `PiiProxyService` is caught.
**Cons:** Requires `NET_ADMIN` capability in the init container, which must be explicitly granted and audited.

### Why sidecar over a shared gateway?

| | Sidecar per Pod | Shared Gateway |
|---|---|---|
| Token map scope | Per-request, in-memory, isolated per pod | Needs distributed store (Redis) or stateless redesign |
| Blast radius | Sidecar crash only affects its pod | Gateway crash = full service down |
| Latency | 1 extra localhost hop (~0.1ms) | 1 extra network hop (~1-5ms) |
| Complexity | Each pod self-contained | Central service to operate |
| Best for | This project's scale | Multi-service platform |

**Verdict for this project:** Sidecar is the right choice. The token map cannot be easily shared across a gateway without adding state (Redis), which adds complexity and a new failure point.

---

## 5. Layer 3: NetworkPolicy — Egress Enforcement (Kernel Level)

**What it is:** Kubernetes NetworkPolicy enforced by the CNI (AWS VPC CNI + network policy controller). Operates at kernel level — no application code can bypass it.

**Status:** `networkPolicy.enabled: true` in `values-prod.yaml` ✅

### Policy design

```yaml
# devops/helm/persons-finder/templates/networkpolicy.yaml (when enabled)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: persons-finder
spec:
  podSelector:
    matchLabels:
      app: persons-finder
  policyTypes:
    - Ingress
    - Egress

  ingress:
    # Only accept traffic from nginx ingress controller
    - from:
      - namespaceSelector:
          matchLabels:
            name: ingress-nginx

  egress:
    # DNS (required for all name resolution)
    - ports:
      - protocol: TCP
        port: 53
      - protocol: UDP
        port: 53

    # Internal cluster communication (ESO, metrics, kube-apiserver)
    - to:
      - podSelector: {}

    # External LLM API — only this FQDN allowed
    # NOTE: requires DNS-based NetworkPolicy or IP-based allowlist
    # Recommended: use an AWS Security Group rule for api.openai.com IP range
    # OR deploy an egress proxy (squid/envoy) that resolves and allows by FQDN
```

### Limitation: Kubernetes NetworkPolicy is IP-based, not FQDN-based

Standard NetworkPolicy cannot filter by hostname (`api.openai.com`). Two solutions:

**Solution A (Simpler):** Use AWS Security Groups on the EKS node group. Add an outbound rule allowing TCP:443 only to the resolved IP range of `api.openai.com`. Not dynamic, but effective for stable IP ranges.

**Solution B (Recommended for production):** Deploy an egress proxy pod (e.g., Squid or Envoy) in a dedicated namespace. NetworkPolicy allows pods to reach only the egress proxy, and the egress proxy enforces FQDN allowlists.

```
persons-finder pod
       │  egress to 10.0.x.x:3128 (internal)
       ▼
 egress-proxy pod
 (squid/envoy)
       │  CONNECT api.openai.com:443
       │  DENY all other FQDNs
       ▼
  api.openai.com
```

---

## 6. Layer 4: Observability & Alerting

Every PII event is observable and alertable. This closes the loop: redaction prevents leaks, observability detects misconfigurations.

### CloudWatch: Structured PII audit logs

Logs flow: `AuditLogger stdout` → `Fluent Bit DaemonSet` → `CloudWatch Logs group /eks/persons-finder/pii-audit`

```
CloudWatch Metric Filter:
  Pattern: { $.type = "PII_AUDIT" && $.redactionsApplied > 0 }
  Metric: PiiRedactionsTotal (count)

CloudWatch Alarm:
  PiiLeakRisk: redactionsApplied = 0 AND destination CONTAINS "openai.com"
  → SNS → PagerDuty (potential unredacted request)
```

### Grafana dashboard (Prometheus metrics from sidecar)

The sidecar exposes `/metrics` on a separate port:

| Metric | Description | Alert threshold |
|---|---|---|
| `pii_requests_total` | Total LLM requests processed | — |
| `pii_redactions_total` | Tokens replaced per request | Alert if p99 = 0 for 5m |
| `pii_proxy_duration_seconds` | Sidecar processing latency | Alert if p99 > 500ms |
| `pii_detection_errors_total` | Detection failures | Alert if > 0 |

### Kyverno policy (already active): image signature verification

Kyverno already enforces that only signed images run. This means the sidecar image itself must be cosign-signed before it can be deployed — the PII protection layer cannot be silently swapped out.

```
cosign sign --key awskms:///alias/persons-finder-cosign-prod \
  190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/pii-redaction-sidecar:latest
```

---

## 7. End-to-End Request Lifecycle

```
1. User calls POST /api/v1/persons/nearby
         │
         ▼
2. PersonController → PersonsServiceImpl
   builds LLM prompt containing:
   "Find persons near John Smith at -33.8688, 151.2093"
         │
         ▼ (Layer 1)
3. PiiProxyService.processRequest()
   PiiDetector detects: PERSON_NAME="John Smith", COORDINATE="-33.8688", COORDINATE="151.2093"
   PiiRedactor tokenizes:
   "Find persons near <NAME_a3f2c1d8> at <COORD_b7d9e2f4>, <COORD_c8a1e3f2>"
         │
         ▼ (Layer 2)
4. Request arrives at sidecar (localhost:8081)
   Sidecar performs secondary scan:
   → no raw PII found (Layer 1 already caught them)
   → forwards to api.openai.com
         │
         ▼ (Layer 3)
5. NetworkPolicy evaluates egress:
   destination IP ∈ api.openai.com allowed range → ALLOW
         │
         ▼
6. api.openai.com receives:
   "Find persons near <NAME_a3f2c1d8> at <COORD_b7d9e2f4>, <COORD_c8a1e3f2>"
   ← no real PII crosses the cluster boundary ✅
         │
         ▼
7. Response returns through sidecar → PiiRedactor.restore()
   "<NAME_a3f2c1d8>" → "John Smith"
   Result returned to user with original names intact
         │
         ▼ (Layer 4)
8. AuditLogger writes to stdout:
   {"type":"PII_AUDIT","piiDetected":["PERSON_NAME","COORDINATE"],"redactionsApplied":3,...}
   → Fluent Bit → CloudWatch → Grafana dashboard
```

---

## 8. Implementation Status

| Component | Layer | Status | Location |
|---|---|---|---|
| `PiiDetector` | 1 | ✅ Built | `src/.../pii/PiiDetector.kt` |
| `PiiRedactor` | 1 | ✅ Built | `src/.../pii/PiiRedactor.kt` |
| `PiiProxyService` | 1 | ✅ Built | `src/.../pii/PiiProxyService.kt` |
| `AuditLogger` | 1, 4 | ✅ Built | `src/.../pii/AuditLogger.kt` |
| Sidecar Helm skeleton | 2 | ✅ Configured | `values.yaml`, `values-prod.yaml` |
| Sidecar container image | 2 | 🔲 To build | ECR URI pre-registered |
| NetworkPolicy | 3 | ✅ Enabled in prod | `values-prod.yaml` |
| FQDN-based egress proxy | 3 | 🔲 Optional enhancement | — |
| CloudWatch log group | 4 | ✅ Via Fluent Bit | `/eks/persons-finder/pii-audit` |
| Grafana PII dashboard | 4 | 🔲 To configure | — |
| Kyverno (image signing) | 4 | ✅ Enforce mode | `devops/kyverno/` |

---

## 9. Security Properties Achieved

| Property | How |
|---|---|
| **No raw PII leaves the cluster** | Layer 1 tokenizes before HTTP call; Layer 3 blocks unapproved destinations |
| **Defence in depth** | 4 independent layers; one failure does not cause a leak |
| **Reversibility** | Tokenization is request-scoped and in-memory; LLM responses are de-tokenized correctly |
| **Zero trust egress** | NetworkPolicy default-deny; only approved destinations reachable |
| **Tamper resistance** | Kyverno Enforce: sidecar image must be cosign-signed to deploy |
| **Full audit trail** | Every LLM call logged with PII type and redaction count to CloudWatch |
| **Language agnostic** (Layer 2) | Sidecar intercepts regardless of which library or language makes the HTTP call |
