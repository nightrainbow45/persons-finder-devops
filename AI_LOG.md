# AI Usage Log / AI使用日志

This document records all AI-assisted work performed during the DevOps production deployment implementation for the Persons Finder application. For each artifact, the original prompt/intent, generated output, identified issues, and applied fixes are documented.

---

## Table of Contents

1. [Dockerfile (Multi-Stage Build)](#1-dockerfile-multi-stage-build)
2. [Kubernetes Manifests (Helm Chart Templates)](#2-kubernetes-manifests-helm-chart-templates)
3. [CI/CD Workflow (GitHub Actions)](#3-cicd-workflow-github-actions)
4. [PII Redaction Sidecar](#4-pii-redaction-sidecar)
5. [Terraform Configurations](#5-terraform-configurations)
6. [Application API Endpoints](#6-application-api-endpoints)
7. [Spring Boot Actuator Configuration](#7-spring-boot-actuator-configuration)

---

## 1. Dockerfile (Multi-Stage Build)

**File:** `devops/docker/Dockerfile`

### Original Prompt / Intent

Create a multi-stage Docker build for the Persons Finder Spring Boot application (Kotlin 1.6.21, Spring Boot 2.7.0, JDK 11). The container should use a minimal runtime image, run as a non-root user, pin all base image versions, expose port 8080, and include a health check instruction.

### What Was Generated

A two-stage Dockerfile:
- **Build stage:** Uses `gradle:7.6-jdk11` to compile the application and produce an executable JAR.
- **Runtime stage:** Uses `eclipse-temurin:11-jre-alpine` as a minimal JRE base image. Creates a non-root user (`appuser`), copies the JAR, exposes port 8080, and adds a `HEALTHCHECK` instruction using `wget` against `/actuator/health`.

### Identified Flaws / Issues

1. **Wildcard JAR copy:** `COPY --from=builder /app/build/libs/*.jar app.jar` may copy multiple JARs if the build produces more than one artifact (e.g., a plain JAR alongside the fat JAR). This could cause unpredictable behavior.
2. **No `.dockerignore` in root context:** The `.dockerignore` is placed in `devops/docker/` but Docker builds from the project root need a root-level `.dockerignore` or explicit build context configuration.
3. **Dependency caching layer:** The `RUN gradle dependencies` step uses `|| true` to suppress failures, which masks real dependency resolution errors.

### Fixes Applied

1. Documented the wildcard JAR copy risk in the Dockerfile comments; the Spring Boot Gradle plugin is configured to produce a single bootJar, mitigating the issue in practice.
2. Created `devops/docker/.dockerignore` with appropriate exclusions; deployment scripts reference the correct build context.
3. Kept the `|| true` pattern as it is a common Gradle Docker caching strategy, but added a comment explaining the trade-off.

---

## 2. Kubernetes Manifests (Helm Chart Templates)

**Directory:** `devops/helm/persons-finder/`

### Original Prompt / Intent

Create a complete Helm Chart for deploying the Persons Finder application to Kubernetes. Include templates for: Deployment (with health probes, resource limits, rolling updates), Service (ClusterIP), Ingress (conditional), HPA (CPU-based autoscaling, 2-10 replicas), Secret (OPENAI_API_KEY), RBAC (minimal permissions), NetworkPolicy (default deny with selective allow), ServiceAccount (with IRSA annotations), and ImagePullSecret (ECR authentication). All values should be parameterized via `values.yaml` with environment-specific overrides (`values-dev.yaml`, `values-prod.yaml`).

### What Was Generated

A full Helm Chart with the following templates:
- `templates/deployment.yaml` — Deployment with readiness/liveness probes, resource requests/limits, rolling update strategy, Secret-based env injection, optional PII sidecar container, and non-root security context.
- `templates/service.yaml` — ClusterIP Service with parameterized type and port mapping (80 → 8080).
- `templates/ingress.yaml` — Conditional Ingress with host/path/TLS parameterization.
- `templates/hpa.yaml` — Conditional HPA targeting CPU utilization at 70%, min 2 / max 10 replicas, with stabilization windows.
- `templates/secret.yaml` — Opaque Secret for OPENAI_API_KEY with conditional creation.
- `templates/rbac.yaml` — Role and RoleBinding with minimal read permissions on ConfigMaps and Secrets.
- `templates/networkpolicy.yaml` — Default deny with selective ingress (from Ingress controller) and egress (DNS, LLM endpoints).
- `templates/serviceaccount.yaml` — ServiceAccount with optional IRSA annotation for AWS IAM role association.
- `templates/imagepullsecret.yaml` — Docker registry Secret for ECR authentication.
- `templates/_helpers.tpl` — Template helper functions for labels, selectors, and naming.
- `Chart.yaml`, `values.yaml`, `values-dev.yaml`, `values-prod.yaml` — Chart metadata and parameterized configuration.

### Identified Flaws / Issues

1. **HPA API version:** Initial generation used `autoscaling/v2beta2` which is deprecated in Kubernetes 1.26+. Needed to use `autoscaling/v2` for forward compatibility.
2. **NetworkPolicy egress CIDR:** The egress rules used a broad `0.0.0.0/0` CIDR for LLM provider access. This is overly permissive and should be narrowed to specific provider IP ranges in production.
3. **Ingress `pathType`:** The initial template omitted the `pathType` field, which is required in `networking.k8s.io/v1` Ingress resources.
4. **Secret base64 encoding:** The Secret template used `{{ .Values.secrets.openaiApiKey | b64enc }}` which double-encodes if the value is already base64-encoded in values.yaml.
5. **RBAC scope:** Initial RBAC granted `list` and `watch` on Secrets at namespace level, which is broader than needed for a single application secret.

### Fixes Applied

1. Updated HPA template to use `autoscaling/v2` API version.
2. Added comments in `values.yaml` documenting that egress CIDRs should be restricted to specific LLM provider ranges in production deployments.
3. Added `pathType: Prefix` to the Ingress template path specification.
4. Changed Secret template to accept plain-text values and apply `b64enc` once, with clear documentation in values.yaml.
5. Narrowed RBAC to `get` permission on specific named resources rather than namespace-wide `list`/`watch`.

---

## 3. CI/CD Workflow (GitHub Actions)

**File:** `devops/ci/ci-cd.yml` (copied to `.github/workflows/ci-cd.yml`)

### Original Prompt / Intent

Create a GitHub Actions CI/CD workflow that: builds the application with Gradle, runs unit tests, builds a Docker image, scans it with Trivy for security vulnerabilities (failing on HIGH/CRITICAL), authenticates to AWS using OIDC (no long-lived credentials), pushes the image to Amazon ECR, and supports deployment triggers on push to `main`.

### What Was Generated

A multi-job GitHub Actions workflow:
- **build-and-test:** Checks out code, sets up JDK 11 with Gradle caching, builds the application, runs tests, and uploads test results as artifacts.
- **docker-build-and-scan:** Builds the Docker image using `devops/docker/Dockerfile`, runs Trivy vulnerability scanner with `CRITICAL,HIGH` severity filter, and uploads the scan report.
- **push-to-ecr:** Authenticates to AWS via OIDC using `aws-actions/configure-aws-credentials`, logs into ECR, and pushes the image tagged with commit SHA and `latest`.

### Identified Flaws / Issues

1. **OIDC role ARN hardcoded:** The initial workflow contained a placeholder `arn:aws:iam::role/` that would fail at runtime. The ARN must be provided via GitHub repository secrets.
2. **Trivy exit code handling:** The initial configuration did not set `exit-code: '1'` for the Trivy action, meaning HIGH/CRITICAL vulnerabilities would not actually fail the build.
3. **Missing `permissions` block:** OIDC authentication requires `id-token: write` and `contents: read` permissions on the job, which were initially omitted.
4. **Gradle wrapper validation:** The workflow did not validate the Gradle wrapper checksum, which is a supply-chain security best practice.
5. **Docker layer caching:** No build cache was configured, leading to slower builds on repeated runs.

### Fixes Applied

1. Changed the OIDC role ARN to reference `${{ secrets.AWS_ROLE_ARN }}` from GitHub repository secrets.
2. Added `exit-code: '1'` and `severity: 'CRITICAL,HIGH'` to the Trivy scanner step.
3. Added `permissions: { id-token: write, contents: read }` to the relevant jobs.
4. Added Gradle wrapper validation step using `gradle/wrapper-validation-action`.
5. Configured Docker Buildx with GitHub Actions cache (`type=gha`) for layer caching.

---

## 4. PII Redaction Sidecar

**Directory:** `src/main/kotlin/com/persons/finder/pii/`

### Original Prompt / Intent

Implement a PII (Personally Identifiable Information) redaction sidecar service in Kotlin that: intercepts outbound requests to external LLM providers, detects PII using regex patterns (person names, geographic coordinates), redacts/tokenizes sensitive data with reversible mapping, maintains JSON-formatted audit logs of all external API calls, and supports configurable redaction rules.

### What Was Generated

Six Kotlin source files:
- **`PiiDetector.kt`** — Detects PII using configurable regex patterns. Supports `NAME` and `COORDINATE` PII types. Returns matches sorted by descending start index for safe in-place replacement.
- **`PiiRedactor.kt`** — Redacts detected PII by replacing matches with UUID-based tokens. Produces a `RedactionResult` containing the redacted text, a reversible token map, and detected PII types. Supports de-tokenization to restore original values.
- **`PiiProxyService.kt`** — HTTP proxy service that intercepts outbound LLM requests, applies PII redaction, forwards sanitized requests, de-tokenizes responses, and logs all operations via the audit logger.
- **`AuditLogger.kt`** — Writes structured JSON audit log entries to stdout. Each entry includes timestamp, request ID, destination URL, detected PII types, redaction count, and request status.
- **`AuditLogEntry.kt`** — Data class defining the audit log schema with fields for timestamp, requestId, destination, piiDetected, redactionsApplied, and status.
- **`RedactionConfig.kt`** — Configuration data classes for redaction rules, PII types, and pattern definitions. Provides sensible defaults for name and coordinate detection.

### Identified Flaws / Issues

1. **Name regex over-matching:** The initial name pattern `[A-Z][a-z]+ [A-Z][a-z]+` matches any two capitalized words, producing false positives on non-name text (e.g., "Spring Boot", "New York").
2. **Coordinate regex precision:** The coordinate pattern matched integers as coordinates, leading to false positives on port numbers, IDs, and other numeric values.
3. **Token collision risk:** Using short UUID segments (8 characters) for tokens has a non-trivial collision probability in high-throughput scenarios.
4. **Thread safety:** The `PiiProxyService` token map was stored as a mutable instance field without synchronization, creating race conditions under concurrent requests.
5. **Audit log timestamp format:** Initial implementation used `System.currentTimeMillis()` as a raw long value instead of ISO-8601 formatted timestamps, making logs harder to read and correlate.

### Fixes Applied

1. Refined the name regex to require at least 2 characters per word and added common false-positive exclusions. Documented that production deployments should use NER (Named Entity Recognition) for higher accuracy.
2. Tightened the coordinate regex to require decimal points and valid latitude/longitude ranges (-90 to 90, -180 to 180).
3. Extended token length to full UUID (36 characters) to eliminate collision risk.
4. Made the proxy service create a new token map per request, eliminating shared mutable state.
5. Changed timestamp format to ISO-8601 (`Instant.now().toString()`) for standard log compatibility.

---

## 5. Terraform Configurations

**Directory:** `devops/terraform/`

### Original Prompt / Intent

Create Terraform configurations for provisioning AWS infrastructure to support the Persons Finder application. Include modules for: VPC (public/private subnets, NAT gateways), EKS (managed Kubernetes cluster with node groups), ECR (container registry with lifecycle policies), IAM (OIDC provider for GitHub Actions, least-privilege roles and policies), and Secrets Manager (encrypted secret storage with rotation). Provide environment-specific configurations for dev and prod.

### What Was Generated

A modular Terraform configuration with five reusable modules:
- **`modules/vpc/`** — VPC with public and private subnets across 2 AZs, Internet Gateway, NAT Gateways, route tables, and security groups for EKS control plane and worker nodes.
- **`modules/eks/`** — EKS cluster with managed node groups, aws-auth ConfigMap for IAM-to-RBAC mapping, cluster add-ons (VPC CNI, CoreDNS, kube-proxy), and IRSA support.
- **`modules/ecr/`** — ECR repository with image scanning on push, lifecycle policies for image retention, and repository access policies.
- **`modules/iam/`** — GitHub OIDC Identity Provider, IAM roles for GitHub Actions (ECR push, EKS access), trust policies, and least-privilege policy documents.
- **`modules/secrets-manager/`** — AWS Secrets Manager secret for OPENAI_API_KEY with KMS encryption and rotation configuration.
- **`environments/dev/`** and **`environments/prod/`** — Environment-specific compositions of all modules with appropriate sizing (smaller instances for dev, production-grade for prod).

### Identified Flaws / Issues

1. **S3 backend bootstrap:** The `backend.tf` references an S3 bucket and DynamoDB table that must exist before `terraform init`. No bootstrap instructions were provided.
2. **NAT Gateway cost:** The dev environment was configured with 2 NAT Gateways (one per AZ), which is expensive for a development environment.
3. **EKS node group AMI:** The initial configuration did not pin the EKS-optimized AMI version, which could cause unexpected node behavior after AMI updates.
4. **OIDC thumbprint:** The GitHub OIDC provider configuration used a hardcoded thumbprint that may change when GitHub rotates their TLS certificates.
5. **Secrets Manager rotation:** The rotation configuration referenced a Lambda function ARN that was not defined in the Terraform modules.

### Fixes Applied

1. Added bootstrap documentation in `devops/terraform/README.md` with instructions for creating the S3 bucket and DynamoDB table before first use.
2. Reduced dev environment to a single NAT Gateway with a comment explaining the cost-availability trade-off.
3. Added `ami_type` parameter to the EKS node group configuration and documented the AMI pinning strategy.
4. Changed OIDC thumbprint to use the AWS-recommended approach of fetching it dynamically, with a fallback to the documented GitHub thumbprint.
5. Made Secrets Manager rotation optional via a `rotation_enabled` variable, defaulting to `false` for environments without a rotation Lambda.

---

## 6. Application API Endpoints

**Directory:** `src/main/kotlin/com/persons/finder/`

### Original Prompt / Intent

Implement the core REST API endpoints for the Persons Finder application: POST `/api/v1/persons` (create person), PUT `/api/v1/persons/{id}/location` (update location), GET `/api/v1/persons/{id}/nearby?radius={km}` (find nearby people using Haversine formula), and GET `/api/v1/persons?ids={ids}` (get person details). Implement the three-layer architecture with Person/Location entities, PersonsService/LocationsService, and PersonController.

### What Was Generated

- **Data Layer:** `Person` and `Location` JPA entities with `PersonRepository` and `LocationRepository` extending `JpaRepository`.
- **Domain Layer:** `PersonsService` and `LocationsService` interfaces with implementations. `LocationsServiceImpl` includes the Haversine formula for distance calculation and nearby person search.
- **Presentation Layer:** `PersonController` with four REST endpoints handling request/response mapping, input validation, and error handling.

### Identified Flaws / Issues

1. **Haversine formula edge case:** The initial implementation did not handle the antipodal point case (points exactly opposite on the globe), which can produce NaN due to floating-point precision issues in `acos()`.
2. **Location entity composite key:** The initial `Location` entity used `referenceId` as both a foreign key and primary key, which prevents storing location history.
3. **Missing input validation:** The PUT location endpoint did not validate latitude (-90 to 90) and longitude (-180 to 180) ranges, allowing invalid coordinates.
4. **N+1 query problem:** The nearby search loaded all locations into memory and filtered in-application code, which does not scale for large datasets.
5. **Missing error responses:** The GET persons endpoint returned an empty list for non-existent IDs instead of distinguishing between "no results" and "invalid IDs".

### Fixes Applied

1. Replaced `acos()` with `atan2()` in the Haversine formula for numerical stability at all distances.
2. Kept `referenceId` as the primary key since the current requirement is for latest-location-only semantics; documented the trade-off for future location history support.
3. Added `@Valid` annotation and range validation (`@Min`/`@Max` or manual checks) for latitude and longitude in the controller.
4. Documented the scalability limitation and noted that a spatial database index (PostGIS) should be used for production workloads with large datasets.
5. The GET endpoint returns only found persons, with the response naturally being an empty list when no IDs match. This is consistent with standard REST API patterns.

---

## 7. Spring Boot Actuator Configuration

**Files:** `build.gradle.kts`, `src/main/resources/application.properties`

### Original Prompt / Intent

Configure Spring Boot Actuator for production health monitoring. Expose `/actuator/health` endpoint that returns HTTP 200 when the application is ready and HTTP 503 when not ready. Configure readiness and liveness health indicators for Kubernetes probe integration. Implement environment variable injection for `OPENAI_API_KEY` with graceful failure when the key is missing.

### What Was Generated

- Added `spring-boot-starter-actuator` dependency to `build.gradle.kts`.
- Configured `application.properties` to expose the health endpoint with `management.endpoints.web.exposure.include=health`.
- Configured health endpoint to show component details: `management.endpoint.health.show-details=always`.
- Implemented environment variable reading for `OPENAI_API_KEY` with a startup validation check.

### Identified Flaws / Issues

1. **Health endpoint exposure:** The initial configuration used `management.endpoints.web.exposure.include=*` which exposes all actuator endpoints (env, beans, metrics, etc.), creating a security risk in production.
2. **Show details setting:** `show-details=always` exposes internal component health details to unauthenticated users, which could leak infrastructure information.
3. **Missing Kubernetes probes configuration:** The initial setup did not enable the Kubernetes-specific probe endpoints (`/actuator/health/readiness` and `/actuator/health/liveness`).
4. **API key validation timing:** The startup check for `OPENAI_API_KEY` threw an exception during bean initialization, preventing the health endpoint from reporting the failure gracefully.
5. **Actuator base path:** The default `/actuator` base path was not explicitly configured, which could conflict with application routes if the API path structure changes.

### Fixes Applied

1. Restricted endpoint exposure to only `health`: `management.endpoints.web.exposure.include=health`.
2. Changed to `show-details=when-authorized` for production, keeping `always` only in dev profile for debugging convenience.
3. Added Kubernetes probe group configuration: `management.endpoint.health.probes.enabled=true` to enable `/actuator/health/readiness` and `/actuator/health/liveness`.
4. Changed API key validation to a `@PostConstruct` check that logs a warning instead of throwing, allowing the health endpoint to report `DOWN` status with a descriptive message.
5. Explicitly set `management.endpoints.web.base-path=/actuator` for clarity and documented it in the deployment guide.

---

## 8. Go PII Redaction Sidecar

**File:** `sidecar/main.go` + `devops/docker/Dockerfile.sidecar`

### Original Prompt / Intent

Implement an independent Layer 2 PII redaction proxy in Go that mirrors the Kotlin `PiiProxyService` logic. The sidecar runs as a separate container in the same pod, intercepts LLM traffic on port 8081, applies the same name and coordinate regex patterns, performs reversible tokenization, and emits `PII_AUDIT` JSON audit log entries to stdout. Build with a multi-stage Dockerfile producing a static binary on an Alpine runtime image.

### What Was Generated

- **`sidecar/main.go`** — HTTP server with `proxyHandler()`, `redact()`, `restore()`, `logAudit()` functions. Two-pass tokenization (names first, then coordinates). Reads `LISTEN_PORT` and `UPSTREAM_URL` from environment variables.
- **`devops/docker/Dockerfile.sidecar`** — Two-stage build: `golang:1.25-alpine3.21` → `alpine:3.21`, non-root user, static binary.

### Identified Flaws / Issues

1. **Regex pattern mismatch:** The initial Go `coordPattern` used `\d+\.\d+` without the leading `-?` and range bounds, diverging from the Kotlin implementation and failing to match negative coordinates.
2. **No coordinate range validation:** Unlike `PiiDetector.kt` which rejects values outside `[-180, 180]`, the initial Go version tokenized any decimal number, producing false positives on version strings like `1.25` or `3.21`.
3. **Token numbers inside existing tokens:** After replacing a name with `<NAME_xxxx>`, the hex digits inside the token could be matched again by the coordinate pattern, corrupting the token.
4. **No `/health` endpoint:** The initial handler only had `proxyHandler` on `/`, causing Kubernetes liveness/readiness probes to fail with non-200 responses on `GET /health`.
5. **`log.Println` vs `fmt.Println`:** Audit JSON was emitted via `log.Println`, which prepends a timestamp prefix, breaking the clean JSON-per-line format that Fluent Bit's `pii_audit_json` parser expects.

### Fixes Applied

1. Updated `coordPattern` to `-?\d{1,3}\.\d{1,15}` matching the Kotlin regex exactly (`RedactionConfig.kt` line 22).
2. Added coordinate range validation: skip matches where `strconv.ParseFloat` produces a value outside `[-180.0, 180.0]`.
3. Added `strings.ContainsAny(match, "<>")` guard in the coordinate pass to skip anything already inside an angle-bracket token.
4. Added explicit `/health` route returning `{"status":"ok","layer":"pii-sidecar"}` (line 222–224).
5. Switched audit output to `log.Println(string(b))` after `json.Marshal` — Go's `log` package writes to stderr by default; changed to `fmt.Fprintln(os.Stdout, ...)` so Fluent Bit reads from the correct stream.

---

## 9. Kyverno Image Signature Enforcement

**File:** `devops/kyverno/verify-image-signatures.yaml`

### Original Prompt / Intent

Create a Kyverno `ClusterPolicy` that enforces cosign image signature verification for all pods in the `default` namespace. The policy should cover both the main `persons-finder` image and the `pii-redaction-sidecar` image, use the project's AWS KMS public key (ECC_NIST_P256), start in `Audit` mode, and document the prerequisites for switching to `Enforce` mode (IRSA configuration, webhook timeout).

### What Was Generated

A `ClusterPolicy` in `Audit` mode with a single `verifyImages` rule targeting `persons-finder:*`, using an inline `publicKeys` PEM block. `mutateDigest: true` and `verifyDigest: true` were both set (defaults).

### Identified Flaws / Issues

1. **Missing sidecar image reference:** The initial policy only listed the main application image. The sidecar `pii-redaction-sidecar` image was not covered, leaving a bypass vector.
2. **`mutateDigest: true` (default):** With ECR IMMUTABLE tags, rewriting the pod spec tag reference to `@sha256:...` is unnecessary and adds operational complexity (ArgoCD diff noise, rollback confusion).
3. **`verifyDigest: true` (default):** Requiring digest-pinned image references in pod specs is overly strict when the ECR repo uses IMMUTABLE tags — a tag always maps to the same digest.
4. **Webhook timeout:** Kyverno's default 10s webhook timeout was too short for ECR signature API calls without IRSA (~11s via NAT). The policy would intermittently block valid images.
5. **No IRSA prerequisite documentation:** The generated file had no comments explaining the IRSA requirement, causing the first deployment to fail with timeout errors.

### Fixes Applied

1. Added `pii-redaction-sidecar:*` to `imageReferences` (line 51 of final file).
2. Set `mutateDigest: false` — tags are immutable; no rewrite needed.
3. Set `verifyDigest: false` — tag references are sufficient given ECR IMMUTABLE policy.
4. Configured IRSA for the `kyverno-admission-controller` ServiceAccount; ECR calls now complete in ~1s. Documented the prerequisite in file header comments.
5. Added detailed prerequisite block at top of file explaining OIDC, IRSA, and webhook timeout options before activation.

---

## 10. Layer 3 NetworkPolicy (VPC CNI eBPF)

**Files:** `devops/helm/persons-finder/templates/networkpolicy.yaml` · `values.yaml` egress rules

### Original Prompt / Intent

Implement Kubernetes NetworkPolicy for the persons-finder pod covering both ingress and egress. Egress must allow: DNS (UDP/TCP 53), same-namespace pod traffic (sidecar communication), and external HTTPS (TCP 443) to reach api.openai.com, ECR, AWS STS, and the EKS control plane. Block all RFC1918 private ranges on port 443 to prevent lateral movement. Enable AWS VPC CNI `enableNetworkPolicy` for eBPF kernel-level enforcement.

### What Was Generated

A `NetworkPolicy` Helm template with `Ingress` and `Egress` policy types. Egress allowed `0.0.0.0/0` on TCP 443 with an `except` block for private ranges, and a DNS rule.

### Identified Flaws / Issues

1. **Missing internal pod egress:** The initial egress rules blocked same-namespace pod traffic (main app → sidecar on localhost:8081). Without an explicit `podSelector: {}` rule, inter-container communication through the pod network was restricted.
2. **No DNS TCP rule:** Only `UDP 53` was listed for DNS. Some DNS resolvers (large responses, zone transfers) fall back to TCP 53; omitting it caused intermittent DNS failures.
3. **`networkpolicies` missing from deployer ClusterRole:** The CI `deployer` service account's `ClusterRole` did not include `networkpolicies` in its resource list, causing `helm upgrade` to fail with a RBAC forbidden error when NetworkPolicy was enabled.
4. **VPC CNI addon not enabled:** `enableNetworkPolicy: true` was not set in the EKS VPC CNI addon config, so the NetworkPolicy was parsed by the API server but not enforced at the kernel level.
5. **`enabled: false` default not toggled in CI:** The CI deploy command did not pass `--set networkPolicy.enabled=true`, so prod deployments ran without the policy despite it being defined.

### Fixes Applied

1. Added `- to: [{podSelector: {}}]` egress rule to allow all intra-namespace pod traffic.
2. Added `protocol: TCP, port: 53` alongside the UDP rule in the DNS egress block.
3. Added `networkpolicies` to the deployer `ClusterRole` resources list (commit `b416dbd`).
4. Set `enableNetworkPolicy: "true"` in the EKS VPC CNI managed addon config via Terraform `aws_eks_addon`.
5. Added `--set networkPolicy.enabled=true` to the CI `helm upgrade` command in `ci-cd.yml`.

---

## 11. Layer 4 Fluent Bit DaemonSet + CloudWatch

**Files:** `devops/k8s/fluent-bit.yaml` · `devops/terraform/environments/prod/cloudwatch.tf`

### Original Prompt / Intent

Deploy a Fluent Bit DaemonSet in `kube-system` to collect `PII_AUDIT` JSON log entries from persons-finder pod stdout, ship them to CloudWatch Logs (`/eks/persons-finder/pii-audit`), and create CloudWatch metric filters and an alarm for zero-redaction events. Provision the log group, IAM permissions, and alarm via Terraform.

### What Was Generated

- **`fluent-bit.yaml`** — DaemonSet with ServiceAccount, ClusterRole, ClusterRoleBinding, ConfigMap (SERVICE/INPUT/FILTER/OUTPUT blocks), and DaemonSet spec. SQLite position DB path set to `/var/log/flb_pii_audit.db`.
- **`cloudwatch.tf`** — `aws_cloudwatch_log_group`, `aws_iam_role_policy`, two `aws_cloudwatch_log_metric_filter` resources, and `aws_cloudwatch_metric_alarm`.

### Identified Flaws / Issues

1. **SQLite DB in read-only mount:** `/var/log` hostPath was mounted `readOnly: true`, but the `DB /var/log/flb_pii_audit.db` config required write access. Pod entered `CrashLoopBackOff` immediately with `[error] [sqldb] cannot open database`.
2. **`auto_create_group true`:** The initial ConfigMap set `auto_create_group true`, requiring Fluent Bit to have `logs:CreateLogGroup` permission. The IAM policy only granted stream-level actions, causing permission errors.
3. **Missing `logs:DescribeLogGroups`:** Fluent Bit's health check calls `DescribeLogGroups` before writing. The IAM policy omitted this action, producing a silent connection failure on startup.
4. **No tolerations:** DaemonSet lacked `tolerations` for `NoSchedule` taints, so it would not run on nodes with custom taints (e.g., system node pools).
5. **Metric filter pattern used single quotes:** CloudWatch metric filter pattern `{ $.type = 'PII_AUDIT' }` used single quotes, which CloudWatch does not accept. The correct syntax requires double quotes.

### Fixes Applied

1. Added `emptyDir` volume `fluent-bit-state`, changed DB path to `/fluent-bit/state/flb_pii_audit.db`, added volumeMount (documented in `run6.md` Section 3.1).
2. Changed `auto_create_group false` — log group is pre-created by Terraform; Fluent Bit only needs stream-level permissions.
3. Added `logs:DescribeLogGroups` to the IAM policy `Action` list (`cloudwatch.tf` lines 41).
4. Added broad `tolerations` block (NoSchedule + NoExecute + Exists) so Fluent Bit runs on all node types.
5. Fixed metric filter patterns to use escaped double quotes: `{ $.type = \"PII_AUDIT\" }`.

---

## 12. Supply Chain Security (cosign + SBOM + Periodic Re-scan)

**Files:** `.github/workflows/ci-cd.yml` (lines 122–183) · `.github/workflows/security-rescan.yml` · `.trivyignore`

### Original Prompt / Intent

Extend the CI pipeline with supply chain security: generate a CycloneDX SBOM after each Trivy-clean build, sign the image and attest the SBOM using cosign with an AWS KMS key (ECC_NIST_P256), and add a separate scheduled workflow to re-scan the 5 most recently deployed images every Monday against the latest CVE database.

### What Was Generated

- **SBOM step:** `trivy image --format cyclonedx` writing to `sbom.cdx.json`, uploaded as a GitHub artifact.
- **cosign steps:** `cosign sign --key awskms:///` and `cosign attest --predicate sbom.cdx.json`.
- **`security-rescan.yml`:** Scheduled workflow with `aws ecr list-images`, a loop over recent tags, and `trivy image --exit-code 1` for each.

### Identified Flaws / Issues

1. **`cosign sign` before image digest available:** The initial pipeline called `cosign sign` using the image tag reference immediately after `docker push`, before ECR had processed the manifest. On slow connections this produced `MANIFEST_UNKNOWN` errors.
2. **Missing `--yes` flag on `cosign sign`:** cosign 2.x requires `--yes` to confirm signing non-interactively; without it the step would hang waiting for terminal input in CI.
3. **`security-rescan.yml` fetched all tags:** `aws ecr list-images` with no filter returned every tag including `pr-*` branches and semver releases, causing the re-scan loop to scan hundreds of images and hit IAM rate limits.
4. **`.trivyignore` entries undocumented:** The initial suppression file listed CVE IDs with no rationale, no severity, and no removal conditions — failing the project's documentation standard.
5. **SBOM artifact retention default (0 days):** GitHub Actions `upload-artifact` defaults to 0 days retention unless explicitly set; the SBOM would be deleted immediately.

### Fixes Applied

1. Added `sleep 5` after `docker push` to allow ECR manifest propagation; switched to image digest reference (`IMAGE_REF=$(docker inspect --format='{{index .RepoDigests 0}}' "$IMAGE")`) for cosign operations.
2. Added `--yes` to both `cosign sign` and `cosign attest` commands.
3. Filtered re-scan to only `git-*` prefixed tags using `--filter tagStatus=TAGGED` and `jq` post-processing; limited to 5 most recent by `sort | tail -5`.
4. Rewrote `.trivyignore` (55 lines): each entry now includes CVE ID, severity, affected component, exploit condition, and an explicit removal condition (e.g., "Remove when `golang:1.25.7+` appears on Docker Hub").
5. Set `retention-days: 90` on the `upload-artifact` step for the SBOM artifact.

---

## Summary

All AI-generated artifacts were reviewed for correctness, security, and production readiness. Key themes across the review:

- **Security hardening:** Tightened permissions, restricted endpoint exposure, validated inputs, and ensured non-root execution.
- **Configuration correctness:** Fixed API versions, added required fields, and corrected parameterization patterns.
- **Production readiness:** Addressed scalability concerns, cost optimization, and operational documentation gaps.
- **Testing coverage:** Property-based tests and unit tests were written for all major components to validate correctness across diverse inputs.
- **Supply chain integrity:** Image signing, SBOM attestation, Kyverno admission enforcement, and periodic re-scanning added layered trust verification beyond what the initial AI output provided.

Each fix was verified through automated tests (JUnit 5 unit tests and jqwik property-based tests) and live EKS deployments before being committed.
