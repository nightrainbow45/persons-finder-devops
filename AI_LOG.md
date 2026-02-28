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

## Summary

All AI-generated artifacts were reviewed for correctness, security, and production readiness. Key themes across the review:

- **Security hardening:** Tightened permissions, restricted endpoint exposure, validated inputs, and ensured non-root execution.
- **Configuration correctness:** Fixed API versions, added required fields, and corrected parameterization patterns.
- **Production readiness:** Addressed scalability concerns, cost optimization, and operational documentation gaps.
- **Testing coverage:** Property-based tests and unit tests were written for all major components to validate correctness across diverse inputs.

Each fix was verified through automated tests (JUnit 5 unit tests and jqwik property-based tests) before being committed.
