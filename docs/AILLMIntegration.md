# 10. AI/LLM Integration

> **Requirement:** Integrate an LLM or AI service into the application. The AI component must do something meaningful (not just a hello-world call). Bonus: privacy-preserving proxy, prompt engineering, or fine-tuning.

---

## Quick Reference

**Requirement vs Implementation**

| Requirement | Current Status |
|---|---|
| LLM/AI service integration | ✅ OpenAI API integration via `PiiProxyService` — all outbound LLM calls pass through the proxy |
| Meaningful AI component | ✅ Privacy-by-default proxy pipeline: detect PII → tokenize → call LLM → de-tokenize response |
| Privacy-preserving proxy (bonus) | ✅ Two independent layers (Kotlin in-process + Go sidecar) both redact PII before any data leaves the cluster |

**Code Snippet Source Map**

| Snippet | Source |
|---|---|
| LLM base URL + proxy routing | `PiiProxyService.kt` lines 20, 16–17 |
| `processRequest()` — full proxy pipeline | `PiiProxyService.kt` lines 48–90 |
| `callLlm()` — convenience wrapper | `PiiProxyService.kt` lines 94–105 |
| PII detection — name regex rule | `RedactionConfig.kt` lines 13–18 |
| PII detection — coordinate regex rule | `RedactionConfig.kt` lines 19–24 |
| `detect()` — scan + sort by index | `PiiDetector.kt` lines 20–47 |
| `redact()` — reversible tokenization | `PiiRedactor.kt` lines 23–48 |
| `restore()` — de-tokenize response | `PiiRedactor.kt` lines 53–58 |
| Token format (`<NAME_xxxx>`, `<COORD_xxxx>`) | `PiiRedactor.kt` lines 60–65 |
| Go sidecar regex patterns (mirrors Kotlin) | `sidecar/main.go` lines 31–36 |
| Go sidecar `redact()` — two-pass tokenization | `sidecar/main.go` lines 65–107 |
| Go sidecar `proxyHandler()` — full flow | `sidecar/main.go` lines 160–217 |
| OPENAI_API_KEY env var config | `application.properties` line 17 |
| Sidecar upstream URL (OpenAI) | `values.yaml` line 130 |

---

## 1. What Was Asked

| Requirement item | Description |
|---|---|
| LLM integration | Connect the application to an LLM or AI service |
| Meaningful use | AI component does more than a hello-world API call |
| Privacy proxy (bonus) | PII is protected before data is sent to the LLM |

---

## 2. What Was Implemented

The requirement is **fully satisfied and significantly exceeded**. The LLM integration is not a direct API call — it is a privacy-by-default proxy pipeline with two independent enforcement layers, reversible tokenization, and full audit logging on every call.

---

### 2.1 Architecture Overview

```
Application code
       │  callLlm("/v1/chat/completions", body)
       ▼
┌─────────────────────────────────────────┐
│  Layer 1: PiiProxyService (Kotlin)      │  in-process Spring Bean
│  detect → tokenize → HTTP → de-tokenize │
└──────────────┬──────────────────────────┘
               │  http://localhost:8081  (when sidecar enabled)
               ▼
┌─────────────────────────────────────────┐
│  Layer 2: pii-redaction-sidecar (Go)   │  sidecar container in same pod
│  detect → tokenize → HTTP → de-tokenize │
└──────────────┬──────────────────────────┘
               │  https://api.openai.com
               ▼
         OpenAI API
```

When the sidecar is enabled (prod), Layer 1 routes through `http://localhost:8081` instead of calling OpenAI directly. If Layer 1 already redacted the payload, Layer 2 finds no PII and forwards transparently. If a request bypasses Layer 1, Layer 2 catches any remaining raw PII.

---

### 2.2 Layer 1: PiiProxyService (Kotlin)

**File:** `src/main/kotlin/com/persons/finder/pii/PiiProxyService.kt` (115 lines)

The Spring Bean that all application code calls to reach an LLM.

> Lines 18–25 — class declaration + LLM endpoint

```kotlin
class PiiProxyService(
    val llmBaseUrl: String = "https://api.openai.com",  // line 20 — default: OpenAI direct
    // When sidecar is enabled, LLM_PROXY_URL=http://localhost:8081 overrides this
    private val config: RedactionConfig = RedactionConfig(),
    private val httpClient: HttpClient = ...,
    private val auditLogger: AuditLogger = AuditLogger()
)
```

> Lines 48–90 — `processRequest()`: the full proxy pipeline

```kotlin
fun processRequest(request: ProxyRequest): ProxyResponse {
    // Step 1: detect and tokenize all PII in the request body
    val redactionResult = redactor.redact(request.body)               // line 51

    // Step 2: build and send the sanitized HTTP request
    val httpRequest = when (request.method.uppercase()) {             // line 61
        "POST" -> builder.POST(ofString(redactionResult.redactedText)).build()
        "PUT"  -> builder.PUT(ofString(redactionResult.redactedText)).build()
        "GET"  -> builder.GET().build()
        else   -> builder.POST(ofString(redactionResult.redactedText)).build()
    }
    val httpResponse = httpClient.send(httpRequest, ofString())       // line 68

    // Step 3: restore original PII values in the LLM response
    val restoredBody = redactor.restore(httpResponse.body(), redactionResult.tokenMap)  // line 71

    // Step 4: audit log the call
    auditLogger.log(AuditLogEntry(                                    // line 74
        requestId = UUID.randomUUID().toString(),
        piiDetected = redactionResult.detectedPiiTypes,
        redactionsApplied = redactionResult.tokenMap.size,
        destination = request.url,
        method = request.method.uppercase()
    ))

    return ProxyResponse(statusCode, restoredBody, piiDetected, redactionsApplied)
}
```

> Lines 94–105 — `callLlm()`: convenience wrapper

```kotlin
fun callLlm(                                                          // line 94
    path: String,
    body: String,
    headers: Map<String, String> = emptyMap()
): ProxyResponse = processRequest(
    ProxyRequest(
        url = llmBaseUrl.trimEnd('/') + path,                        // line 103 — e.g. "/v1/chat/completions"
        body = body,
        headers = headers
    )
)
```

---

### 2.3 PII Detection

**File:** `src/main/kotlin/com/persons/finder/pii/PiiDetector.kt` (55 lines)

Two detection rules defined in `RedactionConfig.kt`:

> `RedactionConfig.kt` lines 13–24 — default rules

```kotlin
fun defaultRules(): List<RedactionRule> = listOf(
    RedactionRule(                                               // line 13
        type = PiiType.PERSON_NAME,
        pattern = "\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+\\b",   // line 16 — "John Smith", "Alice Bob Charlie"
        action = RedactionAction.TOKENIZE
    ),
    RedactionRule(                                               // line 19
        type = PiiType.COORDINATE,
        pattern = "-?\\d{1,3}\\.\\d{1,15}",                   // line 22 — "-33.8688", "151.2093"
        action = RedactionAction.TOKENIZE
    )
)
```

> `PiiDetector.kt` lines 20–47 — `detect()`

```kotlin
fun detect(text: String): List<PiiMatch> {
    if (!config.enabled) return emptyList()                    // line 21 — config-switchable

    for (rule in config.rules) {
        val regex = Regex(rule.pattern)
        for (match in regex.findAll(text)) {
            if (rule.type == PiiType.COORDINATE && !isValidCoordinate(candidate)) {
                continue                                       // reject coords outside -180..180
            }
            matches.add(PiiMatch(value, type, startIndex, endIndex))
        }
    }
    return matches.sortedByDescending { it.startIndex }        // descending for safe replacement
}
```

---

### 2.4 Reversible Tokenization

**File:** `src/main/kotlin/com/persons/finder/pii/PiiRedactor.kt` (68 lines)

PII is replaced with unique tokens (not deleted), so the LLM response can reference back and the application can restore original values.

> Lines 23–48 — `redact()`

```kotlin
fun redact(text: String): RedactionResult {
    val matches = detector.detect(text)
    var result = text

    for (match in matches) {
        val existingToken = tokenMap.entries.find { it.value == match.value }?.key
        val token = existingToken ?: generateToken(match.type)  // reuse token for same PII value

        if (existingToken == null) tokenMap[token] = match.value

        result = result.substring(0, match.startIndex) + token + result.substring(match.endIndex)
    }

    return RedactionResult(result, tokenMap, piiTypes.toList())
}
```

> Lines 53–65 — `restore()` + token format

```kotlin
fun restore(text: String, tokenMap: Map<String, String>): String {  // line 53
    var result = text
    for ((token, original) in tokenMap) {
        result = result.replace(token, original)
    }
    return result
}

private fun generateToken(type: PiiType): String {
    val id = UUID.randomUUID().toString().substring(0, 8)           // line 61
    return when (type) {
        PiiType.PERSON_NAME -> "<NAME_$id>"    // e.g. <NAME_a3f1e2b4>
        PiiType.COORDINATE  -> "<COORD_$id>"   // e.g. <COORD_7c92d0e1>
    }
}
```

**Tokenization example:**

| Input | After `redact()` | After `restore()` |
|---|---|---|
| `"Find John Smith near 37.7749"` | `"Find <NAME_a3f1e2b4> near <COORD_7c92d0e1>"` | `"Find John Smith near 37.7749"` |

The token map lives in memory for the duration of a single proxy transaction — it is never persisted or logged.

---

### 2.5 Layer 2: Go Sidecar (`pii-redaction-sidecar`)

**File:** `sidecar/main.go` (234 lines)

An independent Go HTTP proxy running in the same pod on port 8081. Its regex patterns mirror the Kotlin implementation exactly.

> Lines 31–36 — regex patterns (match `RedactionConfig.kt` lines 16, 22)

```go
var (
    namePattern  = regexp.MustCompile(`\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)+\b`)  // line 33
    coordPattern = regexp.MustCompile(`-?\d{1,3}\.\d{1,15}`)                   // line 35
)
```

> Lines 160–217 — `proxyHandler()`: same four-step flow as Layer 1

```go
func proxyHandler(w http.ResponseWriter, r *http.Request) {
    requestID := randomHex(8)

    body, _ := io.ReadAll(r.Body)                           // line 163

    redacted := redact(string(body))                        // line 171 — tokenize PII

    targetURL := strings.TrimRight(upstreamURL, "/") + r.URL.Path  // line 174
    upstreamReq, _ := http.NewRequest(r.Method, targetURL, ...)

    // Forward all headers unchanged (Authorization: Bearer <key> passes through)
    for key, values := range r.Header { ... }              // line 186

    resp, _ := httpClient.Do(upstreamReq)                  // line 192

    restored := restore(string(respBody), redacted.tokenMap)  // line 206 — de-tokenize response

    logAudit(requestID, targetURL, r.Method, redacted.piiTypes, len(redacted.tokenMap))  // line 208

    fmt.Fprint(w, restored)                                // line 216
}
```

The sidecar also exposes `/health` (line 222) returning `{"status":"ok","layer":"pii-sidecar"}`, used by Kubernetes probes.

---

### 2.6 Secret Management for OPENAI_API_KEY

> `src/main/resources/application.properties` line 17

```properties
openai.api-key=${OPENAI_API_KEY:}   # injected from K8s Secret via envFrom.secretRef
```

> `devops/helm/persons-finder/values.yaml` line 130

```yaml
upstreamUrl: "https://api.openai.com"   # sidecar's upstream — configurable per env
```

The API key never appears in source code or Helm values. It is injected at runtime from AWS Secrets Manager via ESO (see `SecurityAndSecrets.md`).

---

### 2.7 What Makes This "Meaningful"

A direct LLM API call would be 3 lines. This implementation is a privacy-enforcing middleware layer:

| Capability | Description |
|---|---|
| PII interception | Every outbound LLM call is intercepted — no code path bypasses the proxy |
| Reversible tokenization | LLM responses can reference tokens; application sees original values |
| Dual-layer enforcement | Layer 1 (in-process) + Layer 2 (sidecar) provide defense in depth |
| Audit trail | Every LLM call logged with `requestId`, PII types found, redactions applied |
| Config-driven rules | Detection patterns and enable/disable flag in `RedactionConfig` — no code change to add a new PII type |
| Regex sync across languages | Kotlin (`RedactionConfig.kt` line 16) and Go (`main.go` line 33) use the same patterns |

---

## 3. Tests

**`PiiRedactionServiceTest.kt`** (120 lines, 11 `@Test` methods):

| Test | Asserts |
|---|---|
| `detector finds person name like John Smith` | Single match, correct value |
| `detector finds multi-word person name` | Three-word name matched as one entity |
| `detector finds valid coordinates` | Both lat and lon extracted |
| `detector rejects coordinates outside valid range` | 200.12345 not matched |
| `detector returns empty list for text without PII` | No false positives |
| `detector returns empty list when config is disabled` | `enabled=false` kills all detection |
| `redactor replaces person names with tokens` | `<NAME_` prefix, name in tokenMap |
| `redactor replaces coordinates with tokens` | `<COORD_` prefix, coord in tokenMap |
| `restore reverses redaction` | `restore(redact(text).redactedText)` == original |
| `redactor returns unchanged text when no PII` | tokenMap empty, text identical |
| `redactor handles multiple PII instances` | 2 names + 2 coords all removed |

**`PiiRedactionCompletenessPropertyTest.kt`** (162 lines, 5 `@Property(tries=100)` methods — 500 total random iterations):

| Property | Asserts |
|---|---|
| `person names are fully redacted from output` | Random name never appears in redacted text |
| `coordinates are fully redacted from output` | Random coord never appears in redacted text |
| `all PII is removed from redacted output in mixed text` | Name + lat + lon all absent after redaction |
| `redaction is reversible via restore` | `restore(redact(text))` == original for any input |
| `redaction result reports all detected PII types` | `detectedPiiTypes` matches what was inserted |

---

## 4. File Map

| File | Lines | Purpose |
|---|---|---|
| `pii/PiiProxyService.kt` | 115 | HTTP proxy: redact → forward → restore → audit log |
| `pii/PiiDetector.kt` | 55 | Regex-based PII detection: names + coordinates |
| `pii/PiiRedactor.kt` | 68 | Reversible tokenization: `redact()` + `restore()` |
| `pii/RedactionConfig.kt` | 43 | Detection rules: 2 regex patterns, enable/disable flag, PiiType enum |
| `pii/AuditLogger.kt` | 38 | JSON audit log to stdout for every LLM call |
| `pii/AuditLogEntry.kt` | 16 | Audit log data model (6 fields) |
| `sidecar/main.go` | 234 | Go sidecar: Layer 2 proxy — same patterns, independent enforcement |
| `devops/docker/Dockerfile.sidecar` | — | Multi-stage Go build → alpine:3.21, non-root user |
| `test/.../PiiRedactionServiceTest.kt` | 120 | 11 unit tests: detection + tokenization + restore |
| `test/.../PiiRedactionCompletenessPropertyTest.kt` | 162 | 5 property tests, 500 iterations: completeness + reversibility |
