# Requirement 4: CI/CD & AI Usage

> **Requirement:** Create a CI pipeline (GitHub Actions preferred). Add a step that runs a security scanner (Trivy/Snyk) OR a mocked "AI Code Reviewer" step that fails the build if the code "looks unsafe".

---

## 1. What Was Asked

| Requirement item | Description |
|---|---|
| CI pipeline | GitHub Actions preferred |
| Security gate | Trivy/Snyk OR mocked AI Code Reviewer |
| Fail condition | Build fails if code "looks unsafe" |

---

## 2. What Was Implemented

The requirement is **fully satisfied** — using real Trivy container scanning (not a mock), with additional supply-chain security layers that go beyond the minimum ask.

### 2.1 CI Pipeline: GitHub Actions

**File:** `.github/workflows/ci-cd.yml`

Three-job pipeline triggered on every push to `main`, `develop`, and `v*.*.*` tags, and on every pull request to `main`:

```
push / pull_request
        │
        ▼
┌─────────────────────┐
│  1. Build and Test  │  Gradle build + unit tests (317 tests)
└──────────┬──────────┘
           │ needs:
           ▼
┌──────────────────────────────────┐
│  2. Docker Build and Scan        │  Build → Trivy (gate) → SBOM → ECR push → cosign
└──────────────────┬───────────────┘
                   │ needs: (push to main/tags only)
                   ▼
        ┌──────────────────┐
        │  3. Deploy to EKS│  Helm upgrade → kubectl verify
        └──────────────────┘
```

### 2.2 Security Gate: Trivy (Container Image Scanner)

**Location in pipeline:** `docker-build-and-scan` job, step "Trivy vulnerability scan (CI gate — CRITICAL/HIGH)"

Trivy scans the built Docker image for known CVEs in OS packages and application dependencies **before** the image is pushed to ECR.

```yaml
- name: Install Trivy
  run: |
    wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key \
      | gpg --dearmor | sudo tee /usr/share/keyrings/trivy.gpg > /dev/null
    echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] \
      https://aquasecurity.github.io/trivy-repo/deb generic main" \
      | sudo tee /etc/apt/sources.list.d/trivy.list
    sudo apt-get update -qq && sudo apt-get install -y trivy

- name: Trivy vulnerability scan (CI gate — CRITICAL/HIGH)
  run: |
    trivy image \
      --severity CRITICAL,HIGH \   # only gate on high-impact CVEs
      --ignore-unfixed \           # skip CVEs with no available fix
      --ignorefile .trivyignore \  # project-specific suppressions (documented)
      --exit-code 1 \              # non-zero exit = build FAILS
      --no-progress \
      "$IMAGE"
```

**Fail condition:** Any unfixed CRITICAL or HIGH CVE causes `exit-code 1`, which fails the job and blocks the push to ECR and deployment to EKS.

**Current result:** 0 CVEs (main image and sidecar image both pass clean on every CI run).

### 2.3 Second Trivy Scan: PII Redaction Sidecar

The sidecar container (`pii-redaction-sidecar`, Go binary) is built and scanned independently with the same gate:

```yaml
- name: Trivy scan sidecar image (CI gate — CRITICAL/HIGH)
  run: |
    trivy image \
      --severity CRITICAL,HIGH \
      --ignore-unfixed \
      --exit-code 1 \
      --no-progress \
      "$SIDECAR_IMAGE"
```

Both images must pass before either is pushed to ECR.

### 2.4 Why Trivy Over a Mocked AI Reviewer

The requirement explicitly offers "OR a mocked AI Code Reviewer step" as an alternative. Trivy was chosen because:

| | Trivy (implemented) | Mocked AI Reviewer |
|---|---|---|
| Detection accuracy | CVE database (NVD, GitHub Advisory) — real findings | Simulated/hardcoded output |
| Build signal quality | Actual vulnerabilities in actual image layers | No actionable signal |
| Supply chain value | Feeds into SBOM + cosign attestation chain | No downstream value |
| Maintenance | Self-updating CVE DB | Needs manual upkeep of mock rules |

---

## 3. Beyond the Minimum: Full Security Pipeline

The pipeline implements a complete supply-chain security chain, not just the gating step:

```
Docker image built
        │
        ▼
[Trivy scan — CRITICAL/HIGH gate]  ← requirement satisfied here
        │ pass (exit 0)
        ▼
[SBOM generated — CycloneDX format]
        │
        ▼
[Image pushed to ECR (IMMUTABLE tags)]
        │
        ▼
[cosign sign — KMS key ECC_NIST_P256]
        │
        ▼
[cosign attest SBOM — provenance on ECR]
        │
        ▼
[Helm deploy to EKS]
        │
        ▼
[Kyverno Enforce — blocks any unsigned image at admission]
```

### 3.1 SBOM Generation

Every build produces a Software Bill of Materials in CycloneDX format, stored as a GitHub Actions artifact (90-day retention):

```yaml
- name: Generate SBOM (CycloneDX)
  run: |
    trivy image \
      --format cyclonedx \
      --output sbom.cdx.json \
      "$IMAGE"

- name: Upload SBOM artifact
  uses: actions/upload-artifact@v4
  with:
    name: sbom-cyclonedx
    path: sbom.cdx.json
    retention-days: 90
```

### 3.2 Image Signing (cosign + AWS KMS)

Images are signed after passing the Trivy gate. The cosign KMS key uses ECC_NIST_P256 (FIPS-compliant elliptic curve):

```yaml
- name: Sign image with cosign (KMS)
  run: |
    cosign sign \
      --key "awskms:///arn:aws:kms:ap-southeast-2:190239490233:key/6e0f596a-..." \
      "$IMAGE_REF"

- name: Attest SBOM with cosign (KMS)
  run: |
    cosign attest \
      --key "awskms:///${{ env.COSIGN_KMS_ARN }}" \
      --predicate sbom.cdx.json \
      --type cyclonedx \
      "$IMAGE_REF"
```

### 3.3 Runtime Enforcement: Kyverno

Kyverno ClusterPolicy in `Enforce` mode blocks any pod creation in the `default` namespace if the image is not cosign-signed with the project's KMS key:

```yaml
# devops/kyverno/verify-image-signatures.yaml
spec:
  validationFailureAction: Enforce   # block (not just audit)
  rules:
  - verifyImages:
    - imageReferences:
      - "190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder:*"
      - "190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/pii-redaction-sidecar:*"
```

Even if someone bypasses CI entirely and pushes a tampered image directly to ECR, Kyverno will block its deployment.

### 3.4 Periodic Re-scan

**File:** `.github/workflows/security-rescan.yml`

Runs every Monday at 02:00 UTC, scanning the 5 most recent `git-*` tagged images against the latest CVE database — catching newly disclosed vulnerabilities in already-deployed images:

```yaml
on:
  schedule:
    - cron: '0 2 * * 1'   # every Monday 02:00 UTC
```

### 3.5 CVE Suppression Policy (`.trivyignore`)

Suppressions are documented with rationale and removal conditions:

```
# CVE-2025-68121 — CRITICAL — Go crypto/tls
# Suppressed: no Docker Hub image with golang:1.25.7+ available at time of writing.
# Remove this entry when golang:1.25.7+ appears on Docker Hub.
CVE-2025-68121
```

No suppressions are applied to the main Spring Boot application image — only to Go stdlib CVEs in the sidecar where the fix version is not yet published.

---

## 4. Pipeline Execution Results

Latest CI run (`22561483122`, triggered by push to `main`):

| Job | Duration | Result |
|---|---|---|
| Build and Test | 58s | ✅ 317 tests, 0 failures |
| Docker Build and Security Scan | 2m4s | ✅ Trivy 0 CVE · SBOM · ECR push · cosign sign+attest |
| Deploy to EKS | 1m1s | ✅ Helm upgrade · rollout verified |

Trivy scan output (main image):
```
Total: 0 (CRITICAL: 0, HIGH: 0)
```

Trivy scan output (sidecar image):
```
Total: 0 (CRITICAL: 0, HIGH: 0)
```

---

## 5. File Map

| File | Purpose |
|---|---|
| `.github/workflows/ci-cd.yml` | Main pipeline: build → scan → sign → deploy |
| `.github/workflows/security-rescan.yml` | Weekly re-scan of deployed images |
| `.trivyignore` | Documented CVE suppressions with removal conditions |
| `devops/kyverno/verify-image-signatures.yaml` | Runtime enforcement: block unsigned images |
| `devops/docker/Dockerfile` | Multi-stage main image (gradle → eclipse-temurin:11-jre-alpine) |
| `devops/docker/Dockerfile.sidecar` | Multi-stage sidecar (golang:1.25-alpine3.21 → alpine:3.21, non-root) |
