# Deployment Scripts

This directory contains all operational scripts for Persons Finder, from local dev testing to production deployment and teardown.

---

## Scripts overview

| Script | Purpose |
|---|---|
| `setup-eks.sh` | Full EKS cluster + platform components setup (Kyverno, ESO, metrics-server) |
| `teardown-eks.sh` | Destroy cluster and all AWS resources in safe order |
| `deploy.sh` | Helm deploy or upgrade app to a target environment |
| `verify.sh` | Verify deployment health + security stack status |
| `local-test.sh` | Local Helm Chart test using Kind (Kubernetes in Docker) |

---

## Quick start

### 1. Make scripts executable

```bash
chmod +x devops/scripts/*.sh
```

### 2. Full environment setup (first time)

```bash
# Deploy prod environment (Terraform + Kyverno + ESO + metrics-server)
./devops/scripts/setup-eks.sh prod
```

After setup-eks.sh completes, follow the printed instructions to set the OpenAI key, then push to trigger CI/CD.

### 3. Application deploy

```bash
# Manually deploy a specific image tag (normally done by CI/CD)
./devops/scripts/deploy.sh prod --tag git-abc1234
./devops/scripts/deploy.sh prod --tag 1.4.2        # semver release
```

### 4. Verify deployment

```bash
./devops/scripts/verify.sh prod
```

### 5. Local testing

```bash
# Prerequisites: Docker running, kind installed
./devops/scripts/local-test.sh up      # Create Kind cluster + build image + deploy
./devops/scripts/local-test.sh status  # Check status
./devops/scripts/local-test.sh down    # Destroy
```

### 6. Tear down environment

```bash
./devops/scripts/teardown-eks.sh prod
```

---

## Environments

| Env | K8s Namespace | Image source | Secret management |
|---|---|---|---|
| `prod` | `default` | ECR (cosign verified) | ESO syncs from Secrets Manager |
| `dev` | `dev` | ECR | Helm inline |
| local (Kind) | `default` | local build | script creates manually |

---

## Image tag conventions

| Tag format | Trigger | Description |
|---|---|---|
| `git-<sha>` | push to `main` | CI auto-build (e.g. `git-abc1234`) |
| `1.4.2` | push tag `v1.4.2` | semver release (strips `v` prefix) |
| `pr-<N>` | pull request | PR build (scan only, not pushed to ECR) |

---

## Security architecture notes

### cosign image signing
- CI signs images after ECR push using KMS key (ECC_NIST_P256)
- Kyverno ClusterPolicy (Enforce mode) blocks unsigned images in `default` namespace

### IRSA (IAM Roles for Service Accounts)
- Kyverno admission-controller uses IRSA to get IAM credentials directly (~1s vs ~11s via IMDS)
- Keeps ECR API calls within the 10s webhook timeout limit

### KMS key rebuild warning
After each `terraform destroy`, KMS keys enter 30-day PendingDeletion. The next rebuild creates NEW keys.
You must update the cosign public key in the Kyverno policy and the `COSIGN_KMS_ARN` in the CI workflow.

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| AWS CLI | ≥ 2.x | `brew install awscli` |
| Terraform | ≥ 1.5 | `brew install terraform` |
| kubectl | any | `brew install kubectl` |
| Helm | ≥ 3.x | `brew install helm` |
| Kind | any | `brew install kind` (local test only) |
| Docker | any | Docker Desktop |

Each script validates required tools at startup before taking any action.
