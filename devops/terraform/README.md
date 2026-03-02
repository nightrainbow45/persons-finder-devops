# Terraform Infrastructure — Persons Finder

Terraform configurations for provisioning AWS infrastructure: VPC, EKS, ECR (with KMS encryption and cosign signing key), IAM (GitHub OIDC + Kyverno IRSA), Secrets Manager, and CloudWatch observability.

## Prerequisites

- Terraform >= 1.5.0 (tested with 1.14.6)
- AWS CLI v2, configured with credentials for account `190239490233`
- `kubectl` for post-deploy cluster access
- An S3 bucket and DynamoDB table for remote state (pre-created — see Bootstrap section)

## Architecture

```
                           ap-southeast-2
┌──────────────────────────────────────────────────────────────────┐
│  VPC (10.1.0.0/16)  ─ 2 AZs                                      │
│                                                                    │
│  Public Subnets (10.1.0.x, 10.1.1.x)                             │
│    └── Internet Gateway → NAT Gateway (single, cost-saving)       │
│                                                                    │
│  Private Subnets (10.1.2.x, 10.1.3.x)                            │
│    └── EKS Managed Node Group                                      │
│          persons-finder-nodes-prod                                 │
│          t3.small · SPOT · desired=1 · max=3                       │
│          VPC CNI (enableNetworkPolicy=true, eBPF enforcement)      │
│                                                                    │
│  EKS Control Plane                                                 │
│    persons-finder-prod · K8s 1.32 · audit logs → CloudWatch       │
└──────────────────────────────────────────────────────────────────┘
       │
       ├── ECR (persons-finder)   IMMUTABLE · KMS-encrypted · scan-on-push
       ├── ECR (pii-redaction-sidecar)   CI-created · not Terraform-managed
       ├── Secrets Manager   persons-finder/prod/secrets (KMS, 30-day recovery)
       ├── KMS (ECR encryption)   alias/persons-finder-ecr-prod · key rotation on
       ├── KMS (cosign signing)   alias/persons-finder-cosign-prod · ECC_NIST_P256
       ├── IAM OIDC Provider   GitHub Actions → sts:AssumeRoleWithWebIdentity
       ├── IAM: kyverno IRSA   persons-finder-kyverno-prod → ECR readonly
       └── CloudWatch   /eks/persons-finder/pii-audit · 2 metric filters · 1 alarm
```

## Directory Structure

```
terraform/
├── backend.tf               # S3 backend (shared across envs)
├── versions.tf              # Provider version constraints
├── variables.tf             # Global variable definitions
├── outputs.tf               # Global outputs
├── README.md                # This file
├── modules/
│   ├── iam/                 # GitHub OIDC provider, IAM roles + policies
│   │   ├── main.tf
│   │   ├── oidc.tf
│   │   ├── roles.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   ├── policies/        # JSON policy documents
│   │   └── trust-policies/  # JSON trust policy documents
│   ├── vpc/                 # VPC, subnets, NAT, IGW, route tables, security groups
│   ├── eks/                 # EKS cluster, node group, addons (vpc-cni, coredns, kube-proxy)
│   │   └── aws-auth.tf      # aws-auth ConfigMap for RBAC
│   ├── ecr/                 # ECR repo, KMS keys (ECR + cosign), lifecycle policy
│   └── secrets-manager/     # AWS Secrets Manager secret, KMS, access policy
└── environments/
    ├── dev/                 # Development environment (inactive)
    │   ├── main.tf
    │   └── terraform.tfvars
    └── prod/                # Production environment (active)
        ├── main.tf          # Module composition + prod-specific resources
        ├── cloudwatch.tf    # Layer 4: log group, metric filters, PII leak alarm
        └── terraform.tfvars
```

**Prod-specific resources** (in `environments/prod/main.tf`, not in modules):

| Resource | Purpose |
|---|---|
| `aws_iam_openid_connect_provider.eks` | OIDC provider — enables IRSA for pods |
| `aws_iam_role.kyverno` | IRSA role for Kyverno admission controller (ECR readonly) |
| `aws_iam_role_policy.github_actions_sidecar_ecr` | Allows CI to push `pii-redaction-sidecar` to ECR |
| `aws_iam_role_policy.github_actions_cosign` | Allows CI to sign images with cosign KMS key |

## Bootstrap — State Backend

The S3 bucket and DynamoDB table are already provisioned. To recreate from scratch:

```bash
# S3 bucket for state
aws s3api create-bucket \
  --bucket persons-finder-terraform-state \
  --region ap-southeast-2 \
  --create-bucket-configuration LocationConstraint=ap-southeast-2

aws s3api put-bucket-versioning \
  --bucket persons-finder-terraform-state \
  --versioning-configuration Status=Enabled

# DynamoDB for state locking
aws dynamodb create-table \
  --table-name persons-finder-terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region ap-southeast-2
```

## Deployment

### Prod Environment (active)

```bash
cd devops/terraform/environments/prod

terraform init
terraform plan
terraform apply
```

### Dev Environment

```bash
cd devops/terraform/environments/dev

# Edit terraform.tfvars if needed (aws_account_id, github_org already set)
terraform init
terraform plan
terraform apply
```

### Configure kubectl After Apply

```bash
# Output value of kubeconfig_command
aws eks update-kubeconfig --region ap-southeast-2 --name persons-finder-prod
```

## Environment Configuration

| Setting | Dev | Prod |
|---|---|---|
| K8s version | 1.28 | 1.32 |
| VPC CIDR | 10.0.0.0/16 | 10.1.0.0/16 |
| Availability Zones | 2 | 2 |
| NAT Gateway | Single | Single (cost-saving) |
| Node Instance Type | t3.medium | t3.small |
| Node Capacity | SPOT | SPOT |
| Node desired / min / max | 2 / 1 / 3 | 1 / 1 / 3 |
| Cluster Log Types | api, audit, authenticator | audit |
| ECR Tag Mutability | MUTABLE | IMMUTABLE |
| ECR Image Retention (git-*) | 10 | 50 |
| KMS Deletion Window | 7 days | 30 days |
| Secret Recovery Window | 7 days | 30 days |
| Kyverno IRSA | — | ✅ |
| cosign KMS key | — | ✅ ECC_NIST_P256 |
| CloudWatch observability | — | ✅ |

## Required Variables

| Variable | Description | Value |
|---|---|---|
| `aws_account_id` | AWS account ID | `190239490233` |
| `github_org` | GitHub organization/user | `nightrainbow45` |
| `github_repo` | GitHub repository name | `persons-finder` |

## Outputs

| Output | Description |
|---|---|
| `vpc_id` | VPC ID |
| `eks_cluster_name` | EKS cluster name |
| `eks_cluster_endpoint` | EKS API server endpoint |
| `ecr_repository_url` | ECR URL for pushing images |
| `github_actions_role_arn` | IAM role ARN for GitHub Actions OIDC |
| `kubeconfig_command` | `aws eks update-kubeconfig` command |
| `cosign_key_arn` | KMS key ARN for cosign image signing (prod only) |
| `kyverno_role_arn` | IRSA role ARN for Kyverno admission controller (prod only) |
| `cloudwatch_log_group` | CloudWatch log group for PII audit logs (prod only) |
| `cloudwatch_alarm_arn` | ARN of the PII leak risk alarm (prod only) |

```bash
terraform output                        # all outputs
terraform output ecr_repository_url     # specific output
```

## Modules

### IAM Module (`modules/iam/`)
Creates the GitHub OIDC provider and three IAM roles: `github-actions-role` (OIDC — ECR push + EKS deploy), `eks-admin`, and `eks-developer`. Least-privilege policies are in `policies/` as JSON documents.

### VPC Module (`modules/vpc/`)
Creates VPC with public and private subnets across `az_count` AZs, internet gateway, NAT gateways (single or per-AZ), route tables, and security groups for EKS cluster and nodes. Both environments use `single_nat_gateway = true` for cost savings.

### EKS Module (`modules/eks/`)
Creates EKS cluster, managed node group with a launch template (IMDS hop limit = 2 for IRSA), and three managed add-ons: `vpc-cni` (with `enableNetworkPolicy=true` for eBPF NetworkPolicy enforcement), `coredns`, `kube-proxy`. Also generates the `aws-auth` ConfigMap for RBAC.

### ECR Module (`modules/ecr/`)
Creates the ECR repository with `scan_on_push`, KMS encryption (`alias/persons-finder-ecr-prod`), a lifecycle policy (untagged→1d, pr-*→7d, git-*→keep last N, semver→forever), and an asymmetric KMS key for cosign image signing (`alias/persons-finder-cosign-prod`, ECC_NIST_P256). Dev uses MUTABLE tags; prod uses IMMUTABLE.

### Secrets Manager Module (`modules/secrets-manager/`)
Creates the `persons-finder/prod/secrets` secret with KMS encryption and an access policy scoped to the EKS node role. The secret value (`OPENAI_API_KEY`) is set manually post-apply via AWS Console or CLI, then synced to Kubernetes by External Secrets Operator.

## Teardown

```bash
cd devops/terraform/environments/prod  # or dev
terraform destroy
```

**Warning:** This deletes the EKS cluster and all workloads. Drain and backup first.

## Troubleshooting

**State lock stuck:**
```bash
terraform force-unlock <LOCK_ID>
```

**`aws-auth` ConfigMap missing a role:**
```bash
kubectl get configmap aws-auth -n kube-system -o yaml
```

**ECR push denied from CI:**
Verify the `github-actions-role` has `ecr-push` policy and the OIDC provider thumbprint matches.

**Kyverno admission latency (>10s):**
Ensure `kyverno-admission-controller` ServiceAccount is annotated with `eks.amazonaws.com/role-arn: <kyverno_role_arn output>`.

**Secret not syncing to K8s:**
Check ESO ClusterSecretStore and ExternalSecret status:
```bash
kubectl get clustersecretstore,externalsecret -n default
```
