# Deployment Scripts / 部署脚本说明

本目录包含 Persons Finder 的完整运维脚本，覆盖从本地开发测试到生产环境部署和销毁的全流程。
This directory contains all operational scripts for Persons Finder, from local dev testing to production deployment and teardown.

---

## 脚本一览 / Scripts overview

| 脚本 / Script | 用途 / Purpose |
|---|---|
| `setup-eks.sh` | 完整部署 EKS 集群 + 平台组件（Kyverno、ESO、metrics-server）<br>Full EKS cluster + platform components setup |
| `teardown-eks.sh` | 按安全顺序销毁 EKS 集群和所有 AWS 资源<br>Destroy cluster and all AWS resources in safe order |
| `deploy.sh` | Helm 部署或升级应用到指定环境<br>Helm deploy or upgrade app to a target environment |
| `verify.sh` | 验证部署健康状态 + 安全栈状态<br>Verify deployment health + security stack status |
| `local-test.sh` | 使用 Kind 在本地进行 Helm Chart 部署测试<br>Local Helm Chart test using Kind (Kubernetes in Docker) |

---

## 快速开始 / Quick start

### 1. 使脚本可执行 / Make scripts executable

```bash
chmod +x devops/scripts/*.sh
```

### 2. 完整环境搭建（首次）/ Full environment setup (first time)

```bash
# 部署 prod 环境（Terraform + Kyverno + ESO + metrics-server）
# Deploy prod environment (Terraform + Kyverno + ESO + metrics-server)
./devops/scripts/setup-eks.sh prod
```

setup-eks.sh 完成后，按提示设置 OpenAI API key，然后触发 CI/CD 部署应用。
After setup-eks.sh completes, follow the printed instructions to set the OpenAI key, then push to trigger CI/CD.

### 3. 应用部署 / Application deploy

```bash
# 手动部署指定镜像 tag（通常由 CI/CD 自动执行）
# Manually deploy a specific image tag (normally done by CI/CD)
./devops/scripts/deploy.sh prod --tag git-abc1234
./devops/scripts/deploy.sh prod --tag 1.4.2        # semver release
```

### 4. 验证部署 / Verify deployment

```bash
./devops/scripts/verify.sh prod
```

### 5. 本地测试 / Local testing

```bash
# 前置条件：Docker running, kind installed
# Prerequisites: Docker running, kind installed
./devops/scripts/local-test.sh up      # 创建 Kind 集群 + 构建镜像 + 部署
./devops/scripts/local-test.sh status  # 查看状态
./devops/scripts/local-test.sh down    # 销毁
```

### 6. 销毁环境 / Tear down environment

```bash
./devops/scripts/teardown-eks.sh prod
```

---

## 环境说明 / Environments

| 环境 / Env | K8s Namespace | 镜像来源 / Image source | Secret 管理 / Secret management |
|---|---|---|---|
| `prod` | `default` | ECR（cosign 验签）/ ECR (cosign verified) | ESO 从 Secrets Manager 同步 / ESO syncs from Secrets Manager |
| `dev` | `dev` | ECR | Helm 内联创建 / Helm inline |
| local (Kind) | `default` | 本地构建 / local build | 脚本手动创建 / script creates manually |

---

## 镜像 Tag 规范 / Image tag conventions

| Tag 格式 / Format | 触发条件 / Trigger | 说明 / Description |
|---|---|---|
| `git-<sha>` | push to `main` | CI 自动构建（如 `git-abc1234`）/ CI auto-build |
| `1.4.2` | push tag `v1.4.2` | semver 发版（去掉 `v` 前缀）/ semver release |
| `pr-<N>` | pull request | PR 构建（仅 scan，不推送 ECR）/ PR build (scan only, not pushed) |

---

## 安全架构说明 / Security architecture notes

### cosign 镜像签名 / cosign image signing
- CI/CD 在 push 到 main 后用 KMS key（ECC_NIST_P256）对镜像签名
- Kyverno ClusterPolicy（Enforce 模式）阻断未签名镜像进入 `default` namespace
- CI signs images after ECR push using KMS key (ECC_NIST_P256)
- Kyverno ClusterPolicy (Enforce mode) blocks unsigned images in `default` namespace

### IRSA（IAM Roles for Service Accounts）
- Kyverno admission-controller 通过 IRSA 直接获取 IAM 凭证（跳过 IMDS 链路）
- 使 ECR API 调用延迟从 ~11s 降至 ~1s，满足 webhook 10s 超时限制
- Kyverno admission-controller uses IRSA to get IAM credentials directly (~1s vs ~11s via IMDS)
- Keeps ECR API calls within the 10s webhook timeout limit

### KMS key 重建注意 / KMS key rebuild warning
每次 `terraform destroy` 后，KMS key 进入 30 天 PendingDeletion 状态，下次重建会生成新 key。
需同步更新：
- `devops/kyverno/verify-image-signatures.yaml`（新公钥）
- `.github/workflows/ci-cd.yml`（新 `COSIGN_KMS_ARN`）

After each `terraform destroy`, KMS keys enter 30-day PendingDeletion. The next rebuild creates NEW keys.
You must update the cosign public key in the Kyverno policy and the `COSIGN_KMS_ARN` in the CI workflow.

---

## 前置条件 / Prerequisites

| 工具 / Tool | 版本 / Version | 安装 / Install |
|---|---|---|
| AWS CLI | ≥ 2.x | `brew install awscli` |
| Terraform | ≥ 1.5 | `brew install terraform` |
| kubectl | any | `brew install kubectl` |
| Helm | ≥ 3.x | `brew install helm` |
| Kind | any | `brew install kind`（本地测试用 / local test only）|
| Docker | any | Docker Desktop |

各脚本会在启动时检查所需工具是否已安装。
Each script validates required tools at startup before taking any action.
