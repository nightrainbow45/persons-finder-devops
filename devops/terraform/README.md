# Terraform Infrastructure — Persons Finder / Terraform 基础设施 — Persons Finder

Terraform configurations for provisioning AWS infrastructure: VPC, EKS, ECR (with KMS encryption and cosign signing key), IAM (GitHub OIDC + Kyverno IRSA), Secrets Manager, and CloudWatch observability.
用于供给 AWS 基础设施的 Terraform 配置：VPC、EKS、ECR（含 KMS 加密与 cosign 签名密钥）、IAM（GitHub OIDC + Kyverno IRSA）、Secrets Manager 以及 CloudWatch 可观测性。

## Prerequisites / 前置条件

- Terraform >= 1.5.0 (tested with 1.14.6)
  Terraform >= 1.5.0（已在 1.14.6 上测试）
- AWS CLI v2, configured with credentials for account `190239490233`
  AWS CLI v2，已配置账户 `190239490233` 的凭证
- `kubectl` for post-deploy cluster access
  `kubectl`，用于部署后访问集群
- An S3 bucket and DynamoDB table for remote state (pre-created — see Bootstrap section)
  用于远程状态的 S3 存储桶和 DynamoDB 表（已预先创建，见 Bootstrap 章节）

## Architecture / 架构

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

## Directory Structure / 目录结构

```
terraform/
├── backend.tf               # S3 backend (shared across envs) / S3 远程状态后端（环境共享）
├── versions.tf              # Provider version constraints / Provider 版本约束
├── variables.tf             # Global variable definitions / 全局变量定义
├── outputs.tf               # Global outputs / 全局输出
├── README.md                # This file / 本文件
├── modules/
│   ├── iam/                 # GitHub OIDC provider, IAM roles + policies / GitHub OIDC 提供商、IAM 角色与策略
│   │   ├── main.tf
│   │   ├── oidc.tf
│   │   ├── roles.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   ├── policies/        # JSON policy documents / JSON 策略文档
│   │   └── trust-policies/  # JSON trust policy documents / JSON 信任策略文档
│   ├── vpc/                 # VPC, subnets, NAT, IGW, route tables, security groups / VPC、子网、NAT、IGW、路由表、安全组
│   ├── eks/                 # EKS cluster, node group, addons (vpc-cni, coredns, kube-proxy) / EKS 集群、节点组、插件
│   │   └── aws-auth.tf      # aws-auth ConfigMap for RBAC / RBAC 所需的 aws-auth ConfigMap
│   ├── ecr/                 # ECR repo, KMS keys (ECR + cosign), lifecycle policy / ECR 仓库、KMS 密钥、生命周期策略
│   └── secrets-manager/     # AWS Secrets Manager secret, KMS, access policy / Secrets Manager 密钥、KMS、访问策略
└── environments/
    ├── dev/                 # Development environment (inactive) / 开发环境（当前未激活）
    │   ├── main.tf
    │   └── terraform.tfvars
    └── prod/                # Production environment (active) / 生产环境（运行中）
        ├── main.tf          # Module composition + prod-specific resources / 模块组合 + 生产专属资源
        ├── cloudwatch.tf    # Layer 4: log group, metric filters, PII leak alarm / 日志组、指标过滤器、PII 泄漏告警
        └── terraform.tfvars
```

**Prod-specific resources** (in `environments/prod/main.tf`, not in modules):
**生产专属资源**（位于 `environments/prod/main.tf`，不在模块中）：

| Resource / 资源 | Purpose / 用途 |
|---|---|
| `aws_iam_openid_connect_provider.eks` | OIDC provider — enables IRSA for pods<br>OIDC 提供商，为 Pod 启用 IRSA |
| `aws_iam_role.kyverno` | IRSA role for Kyverno admission controller (ECR readonly)<br>Kyverno 准入控制器的 IRSA 角色（ECR 只读） |
| `aws_iam_role_policy.github_actions_sidecar_ecr` | Allows CI to push `pii-redaction-sidecar` to ECR<br>允许 CI 推送 `pii-redaction-sidecar` 到 ECR |
| `aws_iam_role_policy.github_actions_cosign` | Allows CI to sign images with cosign KMS key<br>允许 CI 使用 cosign KMS 密钥对镜像签名 |

## Bootstrap — State Backend / 初始化 — 状态后端

The S3 bucket and DynamoDB table are already provisioned. To recreate from scratch:
S3 存储桶和 DynamoDB 表已预先创建。如需从零重建：

```bash
# S3 bucket for state / 用于存储状态的 S3 存储桶
aws s3api create-bucket \
  --bucket persons-finder-terraform-state \
  --region ap-southeast-2 \
  --create-bucket-configuration LocationConstraint=ap-southeast-2

aws s3api put-bucket-versioning \
  --bucket persons-finder-terraform-state \
  --versioning-configuration Status=Enabled

# DynamoDB for state locking / 用于状态锁定的 DynamoDB 表
aws dynamodb create-table \
  --table-name persons-finder-terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region ap-southeast-2
```

## Deployment / 部署

### Prod Environment (active) / 生产环境（运行中）

```bash
cd devops/terraform/environments/prod

terraform init
terraform plan
terraform apply
```

### Dev Environment / 开发环境

```bash
cd devops/terraform/environments/dev

# Edit terraform.tfvars if needed (aws_account_id, github_org already set) / 如有需要编辑 terraform.tfvars（aws_account_id、github_org 已预填）
terraform init
terraform plan
terraform apply
```

### Configure kubectl After Apply / Apply 后配置 kubectl

```bash
# Output value of kubeconfig_command / 使用 kubeconfig_command 输出值
aws eks update-kubeconfig --region ap-southeast-2 --name persons-finder-prod
```

## Environment Configuration / 环境配置对比

| Setting / 配置项 | Dev / 开发 | Prod / 生产 |
|---|---|---|
| K8s version / K8s 版本 | 1.28 | 1.32 |
| VPC CIDR | 10.0.0.0/16 | 10.1.0.0/16 |
| Availability Zones / 可用区数 | 2 | 2 |
| NAT Gateway / NAT 网关 | Single / 单个 | Single (cost-saving) / 单个（节省成本） |
| Node Instance Type / 节点实例类型 | t3.medium | t3.small |
| Node Capacity / 节点容量类型 | SPOT | SPOT |
| Node desired / min / max / 节点期望/最小/最大 | 2 / 1 / 3 | 1 / 1 / 3 |
| Cluster Log Types / 集群日志类型 | api, audit, authenticator | audit |
| ECR Tag Mutability / ECR 标签可变性 | MUTABLE | IMMUTABLE |
| ECR Image Retention (git-*) / ECR 镜像保留数（git-*） | 10 | 50 |
| KMS Deletion Window / KMS 删除窗口期 | 7 days / 7 天 | 30 days / 30 天 |
| Secret Recovery Window / 密钥恢复窗口期 | 7 days / 7 天 | 30 days / 30 天 |
| Kyverno IRSA | — | ✅ |
| cosign KMS key / cosign KMS 密钥 | — | ✅ ECC_NIST_P256 |
| CloudWatch observability / CloudWatch 可观测性 | — | ✅ |

## Required Variables / 必填变量

| Variable / 变量 | Description / 说明 | Value / 值 |
|---|---|---|
| `aws_account_id` | AWS account ID / AWS 账户 ID | `190239490233` |
| `github_org` | GitHub organization/user / GitHub 组织或用户名 | `nightrainbow45` |
| `github_repo` | GitHub repository name / GitHub 仓库名称 | `persons-finder` |

## Outputs / 输出值

| Output / 输出 | Description / 说明 |
|---|---|
| `vpc_id` | VPC ID |
| `eks_cluster_name` | EKS cluster name / EKS 集群名称 |
| `eks_cluster_endpoint` | EKS API server endpoint / EKS API 服务端点 |
| `ecr_repository_url` | ECR URL for pushing images / 用于推送镜像的 ECR 地址 |
| `github_actions_role_arn` | IAM role ARN for GitHub Actions OIDC / GitHub Actions OIDC IAM 角色 ARN |
| `kubeconfig_command` | `aws eks update-kubeconfig` command / 配置 kubectl 的命令 |
| `cosign_key_arn` | KMS key ARN for cosign image signing (prod only) / cosign 镜像签名用 KMS 密钥 ARN（仅生产） |
| `kyverno_role_arn` | IRSA role ARN for Kyverno admission controller (prod only) / Kyverno 准入控制器 IRSA 角色 ARN（仅生产） |
| `cloudwatch_log_group` | CloudWatch log group for PII audit logs (prod only) / PII 审计日志 CloudWatch 日志组（仅生产） |
| `cloudwatch_alarm_arn` | ARN of the PII leak risk alarm (prod only) / PII 泄漏风险告警 ARN（仅生产） |

```bash
terraform output                        # all outputs / 所有输出
terraform output ecr_repository_url     # specific output / 指定输出
```

## Modules / 模块说明

### IAM Module (`modules/iam/`) / IAM 模块

Creates the GitHub OIDC provider and three IAM roles: `github-actions-role` (OIDC — ECR push + EKS deploy), `eks-admin`, and `eks-developer`. Least-privilege policies are in `policies/` as JSON documents.
创建 GitHub OIDC 提供商和三个 IAM 角色：`github-actions-role`（OIDC，ECR 推送 + EKS 部署）、`eks-admin` 和 `eks-developer`。最小权限策略以 JSON 文档形式存放于 `policies/`。

### VPC Module (`modules/vpc/`) / VPC 模块

Creates VPC with public and private subnets across `az_count` AZs, internet gateway, NAT gateways (single or per-AZ), route tables, and security groups for EKS cluster and nodes. Both environments use `single_nat_gateway = true` for cost savings.
创建跨 `az_count` 个可用区的 VPC（含公有和私有子网）、互联网网关、NAT 网关（单个或按 AZ 部署）、路由表以及 EKS 集群和节点的安全组。两套环境均使用 `single_nat_gateway = true` 以节省成本。

### EKS Module (`modules/eks/`) / EKS 模块

Creates EKS cluster, managed node group with a launch template (IMDS hop limit = 2 for IRSA), and three managed add-ons: `vpc-cni` (with `enableNetworkPolicy=true` for eBPF NetworkPolicy enforcement), `coredns`, `kube-proxy`. Also generates the `aws-auth` ConfigMap for RBAC.
创建 EKS 集群、带启动模板的托管节点组（IMDS hop limit = 2 以支持 IRSA）以及三个托管插件：`vpc-cni`（启用 `enableNetworkPolicy=true` 实现 eBPF 网络策略强制）、`coredns`、`kube-proxy`。同时生成用于 RBAC 的 `aws-auth` ConfigMap。

### ECR Module (`modules/ecr/`) / ECR 模块

Creates the ECR repository with `scan_on_push`, KMS encryption (`alias/persons-finder-ecr-prod`), a lifecycle policy (untagged→1d, pr-*→7d, git-*→keep last N, semver→forever), and an asymmetric KMS key for cosign image signing (`alias/persons-finder-cosign-prod`, ECC_NIST_P256). Dev uses MUTABLE tags; prod uses IMMUTABLE.
创建启用 `scan_on_push` 和 KMS 加密（`alias/persons-finder-ecr-prod`）的 ECR 仓库，配置生命周期策略（untagged→1 天，pr-*→7 天，git-*→保留最近 N 个，semver→永久保留），并创建用于 cosign 镜像签名的非对称 KMS 密钥（`alias/persons-finder-cosign-prod`，ECC_NIST_P256）。开发环境使用 MUTABLE 标签，生产环境使用 IMMUTABLE。

### Secrets Manager Module (`modules/secrets-manager/`) / Secrets Manager 模块

Creates the `persons-finder/prod/secrets` secret with KMS encryption and an access policy scoped to the EKS node role. The secret value (`OPENAI_API_KEY`) is set manually post-apply via AWS Console or CLI, then synced to Kubernetes by External Secrets Operator.
创建使用 KMS 加密的 `persons-finder/prod/secrets` 密钥，访问策略仅限 EKS 节点角色。密钥值（`OPENAI_API_KEY`）在 apply 后通过 AWS Console 或 CLI 手动设置，随后由 External Secrets Operator 同步到 Kubernetes。

## Teardown / 销毁环境

```bash
cd devops/terraform/environments/prod  # or dev / 或 dev
terraform destroy
```

**Warning:** This deletes the EKS cluster and all workloads. Drain and backup first.
**警告：** 此操作将删除 EKS 集群及所有工作负载，请先排空节点并备份数据。

## Troubleshooting / 故障排查

**State lock stuck / 状态锁卡住：**
```bash
terraform force-unlock <LOCK_ID>
```

**`aws-auth` ConfigMap missing a role / `aws-auth` ConfigMap 缺少角色：**
```bash
kubectl get configmap aws-auth -n kube-system -o yaml
```

**ECR push denied from CI / CI 推送 ECR 被拒绝：**
Verify the `github-actions-role` has `ecr-push` policy and the OIDC provider thumbprint matches.
验证 `github-actions-role` 是否已附加 `ecr-push` 策略，并确认 OIDC 提供商指纹匹配。

**Kyverno admission latency (>10s) / Kyverno 准入延迟（>10s）：**
Ensure `kyverno-admission-controller` ServiceAccount is annotated with `eks.amazonaws.com/role-arn: <kyverno_role_arn output>`.
确认 `kyverno-admission-controller` ServiceAccount 已标注 `eks.amazonaws.com/role-arn: <kyverno_role_arn 输出值>`。

**Secret not syncing to K8s / 密钥未同步到 K8s：**
Check ESO ClusterSecretStore and ExternalSecret status:
检查 ESO ClusterSecretStore 和 ExternalSecret 状态：
```bash
kubectl get clustersecretstore,externalsecret -n default
```
