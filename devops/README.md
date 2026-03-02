# DevOps Infrastructure — Persons Finder

All DevOps-related configuration for the Persons Finder application.

## Directory Structure

```
devops/
├── ci/                         # CI/CD pipeline source
│   ├── ci-cd.yml               #   source of .github/workflows/ci-cd.yml
│   └── README.md
├── docker/                     # Container build files
│   ├── Dockerfile              #   multi-stage: gradle → eclipse-temurin:11-jre-alpine
│   ├── Dockerfile.sidecar      #   multi-stage: golang:1.25 → alpine:3.21 (pii-redaction-sidecar)
│   ├── .dockerignore
│   └── README.md
├── docs/                       # Operational guides
│   ├── README.md               #   document index
│   ├── DEPLOYMENT.md           #   Helm deploy reference
│   ├── QUICKSTART.md           #   step-by-step first deploy
│   ├── RELEASE_PROCESS.md      #   tag strategy + release workflow
│   ├── HELM_DEPLOYMENT.md      #   Helm values deep-dive
│   ├── DNS_CONFIGURATION.md    #   domain + Ingress setup
│   ├── NETWORK_SECURITY_VERIFICATION.md  # NetworkPolicy + Kyverno checks
│   ├── SECURITY_REVIEW.md      #   security posture overview
│   └── SWAGGER_TROUBLESHOOTING.md  # Swagger/OpenAPI debugging
├── helm/                       # Helm chart
│   └── persons-finder/
│       ├── Chart.yaml
│       ├── values.yaml         #   defaults (sidecar off, networkpolicy off)
│       ├── values-dev.yaml     #   dev overrides
│       ├── values-prod.yaml    #   prod overrides (sidecar on, networkpolicy on, HPA on)
│       └── templates/          #   deployment, service, ingress, hpa, secret,
│                               #   networkpolicy, rbac, serviceaccount, basic-auth-secret
├── k8s/                        # Standalone K8s manifests (applied once)
│   ├── cluster-secret-store.yaml  # ESO ClusterSecretStore → AWS Secrets Manager
│   ├── external-secret.yaml       # ESO ExternalSecret → syncs OPENAI_API_KEY
│   └── fluent-bit.yaml            # Fluent Bit DaemonSet → CloudWatch log shipping
├── kyverno/                    # Admission policy
│   └── verify-image-signatures.yaml  # ClusterPolicy: block unsigned images (Enforce)
├── scripts/                    # Automation scripts
│   ├── deploy.sh               #   wrapper around helm upgrade
│   ├── setup-eks.sh            #   post-Terraform cluster bootstrap
│   ├── teardown-eks.sh         #   ordered resource teardown
│   ├── local-test.sh           #   run tests locally
│   ├── verify.sh               #   17-check end-to-end health verification
│   └── README.md
└── terraform/                  # Infrastructure as Code
    ├── README.md
    ├── modules/                #   reusable: iam/ vpc/ eks/ ecr/ secrets-manager/
    └── environments/
        ├── dev/                #   dev environment (not currently active)
        └── prod/               #   prod environment (active, ap-southeast-2)
```

## Active Production Environment

| Resource | Value |
|---|---|
| AWS Account | `190239490233` |
| Region | `ap-southeast-2` |
| EKS Cluster | `persons-finder-prod` (K8s 1.32) |
| ECR (main) | `190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder` |
| ECR (sidecar) | `190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/pii-redaction-sidecar` |
| Domain | `aifindy.digico.cloud` |
| TLS | cert-manager + Let's Encrypt (`letsencrypt-prod`) |
| App replicas | 3 (HPA: min=3, max=10, CPU 70%) |
| Nodes | 1× t3.small SPOT (managed node group) |
| Image tags | `git-<sha>` (CI) · `X.Y.Z` semver (releases) — ECR IMMUTABLE |

## Quick Links

| Guide | Description |
|---|---|
| [QUICKSTART.md](docs/QUICKSTART.md) | First deploy — step by step |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | Helm values reference + rollout strategy |
| [RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md) | Tag strategy, semantic versioning, ECR lifecycle |
| [HELM_DEPLOYMENT.md](docs/HELM_DEPLOYMENT.md) | Helm chart deep-dive (values, templates, sidecar) |
| [DNS_CONFIGURATION.md](docs/DNS_CONFIGURATION.md) | Domain, Ingress, TLS certificate setup |
| [SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) | Security posture: Kyverno, NetworkPolicy, Basic Auth |
| [SWAGGER_TROUBLESHOOTING.md](docs/SWAGGER_TROUBLESHOOTING.md) | Swagger UI / OpenAPI debugging |
| [terraform/README.md](terraform/README.md) | Terraform modules, environments, variable reference |

## Getting Started

```bash
# 1. Configure kubectl (after Terraform apply)
aws eks update-kubeconfig --region ap-southeast-2 --name persons-finder-prod

# 2. Verify cluster health
./scripts/verify.sh

# 3. Deploy (requires IMAGE_TAG)
helm upgrade --install persons-finder ./helm/persons-finder \
  --namespace default \
  -f helm/persons-finder/values-prod.yaml \
  --set image.tag=git-<sha>
```

See [QUICKSTART.md](docs/QUICKSTART.md) for the complete first-deploy walkthrough.
