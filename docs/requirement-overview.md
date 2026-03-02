# Requirement Overview

> This document summarises all implemented requirements for the Persons Finder DevOps project.
> Each section maps the requirement to its implementation status and links to the detailed document.

---

## Status at a Glance

| # | Requirement | Status | Detail Doc |
|---|---|---|---|
| 1 | Application Design & API | ✅ Fully satisfied + bonus | [ApplicationDesignAndAPI.md](ApplicationDesignAndAPI.md) |
| 2 | Infrastructure as Code | ✅ Fully satisfied + bonus | [InfrastructureAsCode.md](InfrastructureAsCode.md) |
| 3 | Security & Secrets Management | ✅ Fully satisfied + bonus | [SecurityAndSecrets.md](SecurityAndSecrets.md) |
| 4 | CI/CD & AI Usage | ✅ Fully satisfied + bonus | [CICDandAIUsage.md](CICDandAIUsage.md) |
| 5 | Observability & Monitoring | ✅ Fully satisfied + bonus | [ObservabilityAndMonitoring.md](ObservabilityAndMonitoring.md) |
| 6 | AI/LLM Integration | ✅ Fully satisfied + bonus | [AILLMIntegration.md](AILLMIntegration.md) |
| — | Containerization & Dockerfile | ✅ Fully satisfied + bonus | [ContainerizationAndDockerfile.md](ContainerizationAndDockerfile.md) |
| — | AI Firewall (PII Egress Design) | ✅ Fully implemented (4 layers) | [AIfirewall.md](AIfirewall.md) |
| — | System Architecture Diagrams | ✅ Full diagram set | [ARCHITECTURE_DIAGRAM.md](ARCHITECTURE_DIAGRAM.md) |

---

## Requirement 1 — Application Design & API

> Build a RESTful API service (Spring Boot preferred). Implement at least 3 endpoints. Use proper HTTP status codes. Bonus: OpenAPI/Swagger documentation.

| Item | Implementation |
|---|---|
| Framework | Spring Boot 2.7.x + Kotlin, `@RestController` at `/api/v1/persons` |
| Endpoints | 4 endpoints: `POST`, `PUT /{id}/location`, `GET /{id}/nearby`, `GET ?ids=` |
| HTTP status codes | 201 Created / 200 OK / 400 Bad Request / 404 Not Found |
| 3-layer architecture | `presentation/` → `domain/services/` → `data/` (H2) |
| Distance calculation | Haversine formula (`LocationsServiceImpl.kt` lines 45–53) |
| OpenAPI/Swagger (bonus) | springdoc-openapi, live Swagger UI, env-switchable via `SWAGGER_ENABLED` |
| Tests | 13 controller integration tests (MockMvc) + 15 service unit tests |

**Key files:** `PersonController.kt` (274 lines) · `LocationsServiceImpl.kt` · `Person.kt` · `Location.kt` · `OpenAPIConfig.kt` · `CorsConfig.kt`

---

## Requirement 2 — Infrastructure as Code

> Deploy to a local cluster (Kind/Minikube) or output Terraform for AWS. Inject secrets securely. Configure HPA. Use AI to generate K8s manifests and document what you fixed.

| Item | Implementation |
|---|---|
| Local cluster | Kind + Helm via `devops/scripts/local-test.sh` |
| AWS deployment | Terraform: EKS + VPC + ECR + IAM + KMS + Secrets Manager (61 resources) |
| Secret injection | `envFrom.secretRef` → K8s Secret; prod: ESO syncs from AWS Secrets Manager |
| HPA | `autoscaling/v2`, CPU 70% trigger, min 1 / max 3 (prod: min 3 / max 20) |
| K8s manifests | Helm Chart: 10 templates (Deployment, Service, Ingress, HPA, RBAC, NetworkPolicy, …) |
| AI fixes documented | 8 fixes: missing readinessProbe, no resources, no podSecurityContext, maxSurge deadlock, secret checksum, HPA v1→v2, ingress annotation, named targetPort |

**Key files:** `devops/helm/persons-finder/templates/` · `devops/terraform/modules/` · `devops/terraform/environments/prod/`

---

## Requirement 3 — Security & Secrets Management

> No hardcoded secrets. Use Kubernetes Secrets or Vault. Implement RBAC and network policies. Bonus: pod security contexts, image scanning.

| Item | Implementation |
|---|---|
| No hardcoded secrets | `envFrom.secretRef` — OPENAI_API_KEY injected at runtime from K8s Secret |
| Secrets backend | AWS Secrets Manager + KMS (`enable_key_rotation = true`) + ESO sync |
| RBAC | Dedicated Role (get/list/watch only) + ServiceAccount + RoleBinding per release |
| NetworkPolicy | Ingress/Egress allowlist; prod-enabled; VPC CNI eBPF enforcement (Layer 3) |
| Pod Security Context (bonus) | `runAsNonRoot: true`, `allowPrivilegeEscalation: false`, `capabilities: drop: [ALL]` |
| Image scanning (bonus) | Trivy CI gate (CRITICAL/HIGH exit-code 1) + cosign KMS signing + Kyverno Enforce |
| TLS | cert-manager + Let's Encrypt; rate-limit 100 req/s; IP allowlist support |

**Key files:** `rbac.yaml` · `networkpolicy.yaml` · `deployment.yaml` · `secrets-manager/main.tf` · `kyverno/verify-image-signatures.yaml` · `.trivyignore`

**See also:** [AIfirewall.md](AIfirewall.md) — full 4-layer PII egress protection design (NetworkPolicy = Layer 3)

---

## Requirement 4 — CI/CD & AI Usage

> Create a GitHub Actions CI pipeline. Add a Trivy/Snyk security scanner step OR a mocked AI Code Reviewer that fails the build on unsafe code.

| Item | Implementation |
|---|---|
| CI pipeline | GitHub Actions, 3-job pipeline: Build & Test → Docker Build & Scan → Deploy to EKS |
| Security gate | Real Trivy scan (not a mock), both main image + sidecar, CRITICAL/HIGH → exit-code 1 |
| Fail condition | `--exit-code 1` blocks ECR push and EKS deploy on any unfixed CRITICAL/HIGH CVE |
| Current result | 0 CVEs on every CI run (main image + sidecar) |
| SBOM | CycloneDX format, generated every build, uploaded as GitHub artifact (90-day retention) |
| Image signing | cosign + AWS KMS ECC_NIST_P256; attests SBOM to ECR |
| Runtime enforcement | Kyverno ClusterPolicy Enforce mode — blocks unsigned images at admission |
| Periodic re-scan | `security-rescan.yml`: every Monday 02:00 UTC, scans 5 most recent deployed images |

**Key files:** `.github/workflows/ci-cd.yml` (282 lines) · `.github/workflows/security-rescan.yml` · `.trivyignore` · `devops/kyverno/verify-image-signatures.yaml`

---

## Requirement 5 — Observability & Monitoring

> Add application logs, a health check endpoint, and at least one metric or alert. Bonus: structured logging, log aggregation pipeline.

| Item | Implementation |
|---|---|
| Application logs | `AuditLogger.kt` — single-line JSON to stdout on every LLM proxy call |
| Log format | `{"type":"PII_AUDIT","timestamp":…,"requestId":…,"redactionsApplied":…}` |
| Health check | Spring Actuator `/actuator/health` with liveness + readiness sub-paths |
| Metrics | 2 CloudWatch metric filters: `PiiAuditTotal` + `PiiZeroRedactions` |
| Alert | CloudWatch alarm `persons-finder-pii-leak-risk`: fires if `redactionsApplied = 0` in 5 min |
| Structured logging (bonus) | Fixed JSON schema per event; `type: PII_AUDIT` field enables CloudWatch pattern matching |
| Log aggregation (bonus) | Fluent Bit DaemonSet (kube-system): tail → grep → parse → CloudWatch `/eks/persons-finder/pii-audit` (90-day retention) |
| Prometheus-ready | `values-prod.yaml` scrape annotations: `/actuator/prometheus` on port 8080 |
| Tests | 5 unit tests (AuditLoggerTest) + 5 property tests × 100 iterations (AuditLogCompletenessPropertyTest) |

**Key files:** `AuditLogger.kt` · `AuditLogEntry.kt` · `devops/k8s/fluent-bit.yaml` (248 lines) · `devops/terraform/environments/prod/cloudwatch.tf` (129 lines)

---

## Requirement 6 — AI/LLM Integration

> Integrate an LLM or AI service. The AI component must do something meaningful. Bonus: privacy-preserving proxy.

| Item | Implementation |
|---|---|
| LLM integration | OpenAI API — all outbound calls pass through `PiiProxyService` |
| Meaningful use | Privacy-by-default proxy pipeline: detect PII → tokenize → call LLM → de-tokenize response |
| PII detection | Regex rules for `PERSON_NAME` (`First Last` patterns) + `COORDINATE` (decimal lat/lon) |
| Reversible tokenization | `<NAME_xxxx>` / `<COORD_xxxx>` tokens — LLM sees tokens, app restores originals |
| Layer 1 | `PiiProxyService.kt` (Kotlin, in-process Spring Bean) |
| Layer 2 (bonus) | `pii-redaction-sidecar` (Go, sidecar container) — same regex patterns, independent enforcement |
| Audit log | Every LLM call logged with `requestId`, PII types found, `redactionsApplied` count |
| Secret management | `OPENAI_API_KEY` injected from K8s Secret — never in code or Helm values |
| Tests | 11 unit tests + 5 property tests × 100 iterations (500 random inputs) |

**Key files:** `PiiProxyService.kt` (115 lines) · `PiiDetector.kt` · `PiiRedactor.kt` · `RedactionConfig.kt` · `sidecar/main.go` (234 lines)

**See also:** [AIfirewall.md](AIfirewall.md) — end-to-end threat model, layer-by-layer design rationale, token lifecycle, and implementation status table

---

## Containerization & Dockerfile

> Create a Dockerfile. Ask AI to write it. Audit and fix what the AI missed (non-root user, multi-stage build, pinned versions).

| Item | Implementation |
|---|---|
| Dockerfile | `devops/docker/Dockerfile` (48 lines), two-stage build |
| AI-generated | Initial version from AI; documented in `AI_LOG.md` Section 1 |
| Multi-stage build | Stage 1: `gradle:7.6.4-jdk11-focal` (build) → Stage 2: `eclipse-temurin:11.0.26_4-jre-alpine` (runtime, JRE only) |
| Non-root user | `addgroup -S appgroup && adduser -S appuser` + `USER appuser` |
| Version pinning | Full patch versions on both base images — no `:latest` |
| Alpine CVE patch | `apk update && apk upgrade --no-cache` at runtime stage |
| HEALTHCHECK | `wget` against `/actuator/health`, 30s interval, 40s start period |
| `.dockerignore` | 69 lines — excludes `.git/`, `build/`, `devops/`, `src/test/`, Terraform state |
| AI flaws fixed | 3 issues: unpinned versions → pinned; no `.dockerignore` → added; masked errors → commented |
| Sidecar Dockerfile | `Dockerfile.sidecar` (38 lines): static Go binary → alpine:3.21, ~13 MB final image |
| Tests | `DockerfileBestPracticesTest.kt` (9 tests) + `ContainerImageDeterminismPropertyTest.kt` (5 × 100 = 500 iterations) |

**Key files:** `devops/docker/Dockerfile` · `devops/docker/Dockerfile.sidecar` · `devops/docker/.dockerignore` · `AI_LOG.md`

---

## System Architecture

```
Internet
    │  HTTPS (TLS via cert-manager)
    ▼
NGINX Ingress Controller
    │  rate-limit: 100 req/s
    ▼
┌──────────────────────────────────────────────┐
│  Pod: persons-finder (default namespace)      │
│                                              │
│  ┌──────────────────────┐                   │
│  │  Main App (Spring)   │  Layer 1: PiiProxyService  │
│  │  port 8080           │─────────────────────────▶ localhost:8081
│  └──────────────────────┘                   │
│                                              │
│  ┌──────────────────────┐                   │
│  │  Go Sidecar          │  Layer 2: PII redaction    │
│  │  port 8081           │─────────────────────────▶ api.openai.com
│  └──────────────────────┘                   │
└──────────────────────────────────────────────┘
    │  stdout (PII_AUDIT JSON)
    ▼
Fluent Bit DaemonSet (kube-system)
    │  Layer 4: grep + parse + ship
    ▼
CloudWatch Logs /eks/persons-finder/pii-audit
    │
    ├── Metric: PiiAuditTotal
    └── Metric: PiiZeroRedactions → Alarm: pii-leak-risk
```

**NetworkPolicy (Layer 3):** Ingress from ingress-nginx only; Egress: DNS + same namespace + external TCP 443 (public IPs). Enforced at kernel level via VPC CNI eBPF.

**Kyverno:** Enforce mode — any unsigned image is blocked at Kubernetes admission webhook before it can start.

Full component diagrams (HPA, security layers, CI/CD pipeline, observability): [ARCHITECTURE_DIAGRAM.md](ARCHITECTURE_DIAGRAM.md)
Full 4-layer PII egress design with threat model and end-to-end lifecycle: [AIfirewall.md](AIfirewall.md)

---

## Test Coverage Summary

| Category | File | Tests |
|---|---|---|
| REST API (integration) | `PersonControllerTest.kt` | 13 `@Test` |
| Service / Haversine (unit) | `ServiceTest.kt` | 15 `@Test` |
| PII detection + tokenization | `PiiRedactionServiceTest.kt` | 11 `@Test` |
| PII completeness (property) | `PiiRedactionCompletenessPropertyTest.kt` | 5 × 100 = 500 runs |
| Audit log format (unit) | `AuditLoggerTest.kt` | 5 `@Test` |
| Audit log completeness (property) | `AuditLogCompletenessPropertyTest.kt` | 5 × 100 = 500 runs |
| Dockerfile best practices | `DockerfileBestPracticesTest.kt` | 9 `@Test` |
| Image determinism (property) | `ContainerImageDeterminismPropertyTest.kt` | 5 × 100 = 500 runs |
| Helm chart structure | `HelmChartStructureTest.kt` | `helm lint` + `helm template` |
| Infrastructure layout | `DevOpsFolderStructureTest.kt` | folder/file assertions |
| **Total** | | **≥ 317 tests, 1500+ property iterations** |

---

## Key Infrastructure Numbers

| Resource | Value |
|---|---|
| AWS resources (Terraform) | 61 (EKS, VPC, ECR, IAM, KMS, Secrets Manager) |
| EKS node type | t3.small SPOT, ap-southeast-2 |
| ECR repositories | 2 (persons-finder + pii-redaction-sidecar), IMMUTABLE tags |
| cosign KMS key | ECC_NIST_P256, alias `alias/persons-finder-cosign-prod` |
| CI pipeline jobs | 3 (Build & Test → Docker Build & Scan → Deploy to EKS) |
| Trivy CVEs (current) | 0 CRITICAL, 0 HIGH (main image + sidecar) |
| CloudWatch log retention | 90 days |
| verify.sh checks | 17/17 passing |
