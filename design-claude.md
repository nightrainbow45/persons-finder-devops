# Design Document (Claude-Updated) / 设计文档（Claude 更新版）

> **说明 / Note:**
> 本文档基于 Kiro 原始 `design.md` 改写，反映截至 2026-03-01 的**实际落地状态**。
> 原始文档描述的是设计意图；本文档描述的是已运行的系统。
> 与原文不同之处均以 `⚡ 实际 / Actual:` 标注。
>
> This document is rewritten from Kiro's original `design.md` to reflect the **actual deployed state** as of 2026-03-01.
> Where reality differs from the original design, differences are marked with `⚡ 实际 / Actual:`.

---

## Overview / 概述

Persons Finder 应用的生产部署架构，基于容器化、Kubernetes 编排、自动化 CI/CD 流水线和安全最佳实践。

Production deployment architecture for the Persons Finder application, built on containerization, Kubernetes orchestration, automated CI/CD pipelines, and security best practices.

### Application Overview / 应用概述

Persons Finder 是一个 Spring Boot REST API 应用，用于管理人员位置信息并查找附近的人。

**⚡ 实际技术栈 / Actual Technology Stack:**

| 组件 | 原始设计 | 实际版本 | 变更原因 |
|---|---|---|---|
| Kotlin | 1.6.21 | **1.9.25** | CVE 修复升级 |
| Spring Boot | 2.7.0 | **2.7.18** | CVE 修复（最后一个 2.x 版本）|
| Spring Framework | 5.3.x | **5.3.34** | CVE-2024-22243/22259/22262 |
| H2 Database | 2.1.212 | **2.2.220** | CVE-2022-45868 |
| Tomcat | 9.0.x | **9.0.109** | CRITICAL CVE-2025-24813 |
| Logback | 1.2.12 | **1.2.13** | CVE-2023-6378/6481 |
| Jackson | 2.13.x | **2.15.4** | CVE-2025-52999 |
| SnakeYAML | 1.30 | **1.33** | CVE-2022-25857 |
| JDK | 11 | **11**（不变）| |

**三层架构 / Three-Layer Architecture:** （未变）

1. **Presentation Layer** — `presentation/` — REST controllers, base path `/api/v1/persons`
2. **Domain Layer** — `domain/services/` — `PersonsServiceImpl`, `LocationsServiceImpl`，Haversine 公式
3. **Data Layer** — `data/` — JPA entities, H2 in-memory database

**API Endpoints:** （未变）
- `POST /api/v1/persons`
- `PUT /api/v1/persons/{id}/location`
- `GET /api/v1/persons/{id}/nearby?radius={km}`
- `GET /api/v1/persons?ids={ids}`

---

## DevOps Code Structure / DevOps 代码结构

```
devops/
├── docker/
│   └── Dockerfile                   # 多阶段构建，版本已锁定 / Multi-stage, pinned versions
├── helm/
│   └── persons-finder/
│       ├── Chart.yaml
│       ├── values.yaml              # 默认配置 / Default config
│       ├── values-dev.yaml
│       ├── values-prod.yaml         # ⚡ 部分参数被 CI 覆盖 / Some params overridden by CI
│       └── templates/
│           ├── deployment.yaml      # ⚡ envFrom 已修复（始终挂载）
│           ├── hpa.yaml             # ⚡ autoscaling/v2，含 behavior 配置
│           ├── service.yaml
│           ├── ingress.yaml         # ⚡ 当前禁用（无 nginx controller）
│           ├── secret.yaml
│           ├── serviceaccount.yaml
│           ├── rbac.yaml
│           ├── networkpolicy.yaml
│           └── imagepullsecret.yaml
├── terraform/
│   ├── modules/
│   │   ├── iam/                     # OIDC, Roles, Policies
│   │   ├── vpc/                     # VPC, Subnets, Security Groups
│   │   ├── eks/                     # EKS Cluster, Node Group, aws-auth
│   │   ├── ecr/                     # ⚡ 新增 KMS key + 更新 lifecycle policy
│   │   └── secrets-manager/         # ⚡ 修复 kms:Decrypt 授权
│   └── environments/
│       └── prod/                    # ⚡ 已 apply，prod 环境运行中
│           └── main.tf
├── ci/
│   └── ci-cd.yml                    # ⚡ 已全面更新（见 CI/CD 章节）
└── docs/
    └── ai-manifest-review.md        # ⚡ 新增：AI 生成 manifest 的问题记录

# 项目根目录新增 / New at project root:
Makefile                             # ⚡ 新增：本地构建/扫描/清理
.trivyignore                         # ⚡ 新增：7 个 Spring Boot 2.x wontfix CVE
```

---

## Architecture / 架构

### Authentication and Authorization / 认证与授权

#### 1. GitHub Actions → AWS (OIDC)

**⚡ 实际配置 / Actual:**
- IAM Role: `arn:aws:iam::190239490233:role/persons-finder-github-actions-prod`
- Trust policy 允许两个 repo: `persons-finder` 和 `persons-finder-devops`
- GitHub Secret: `AWS_ACCOUNT_ID = 190239490233`（唯一需要的 Secret）

```
GitHub Actions
    │ OIDC Token
    ▼
AWS IAM OIDC Provider
    │ AssumeRoleWithWebIdentity
    ▼
IAM Role: persons-finder-github-actions-prod
    ├── Policy: ecr-push       → ECR GetAuthToken, Push/Pull images
    ├── Policy: eks-access     → EKS DescribeCluster, AccessKubernetesApi
    └── Policy: deployer       → EKS DescribeCluster, STS GetCallerIdentity
```

#### 2. Kubernetes RBAC

**⚡ 实际状态 / Actual:**

| IAM Role | K8s Username | K8s Group | 权限 |
|---|---|---|---|
| eks-admin | eks-admin | system:masters | 完全管理 |
| eks-developer | eks-developer | developers | 命名空间读写 |
| github-actions-prod | github-actions | deployers | cluster-admin（CI/CD 部署需要）|
| EKS Node Role | system:node:{{EC2PrivateDNSName}} | system:bootstrappers, system:nodes | 节点加入 |

> **注意 / Note:** `deployers` 组绑定了 `cluster-admin` ClusterRole（`deployers-admin` ClusterRoleBinding），这是 Helm 部署 RBAC 资源所必需的。原始设计中的 "最小权限 deployer" 未在实践中落地。

#### 3. Secret Management / 密钥管理

**⚡ 实际方案：External Secrets Operator (ESO)**

原始设计提及 ESO 作为选项，**实际已全面采用**：

```
AWS Secrets Manager
  persons-finder/openai-api-key
  (KMS 加密，key: aws/secretsmanager)
         │
         │ IAM Role (EC2 Instance Profile, IMDS hop-limit=2)
         ▼
External Secrets Operator
  ClusterSecretStore (API v1, IAM 模式)
         │
         │ 每小时同步
         ▼
K8s Secret: persons-finder-secrets
  key: OPENAI_API_KEY
         │
         │ envFrom.secretRef（始终挂载，不受 secrets.create 条件控制）
         ▼
Pod 环境变量: OPENAI_API_KEY
```

**修复的 Bug:** 原始 Helm deployment.yaml 中 `envFrom` 被包在 `{{- if .Values.secrets.create }}` 条件内，导致使用 ESO 时（`secrets.create=false`）OPENAI_API_KEY 完全未注入 Pod。已修复为无条件挂载。

**GitHub Secrets（仅需一个）:**
- `AWS_ACCOUNT_ID`: `190239490233`

#### 4. ECR Authentication / ECR 认证

**⚡ 实际加密：SSE-KMS（已从 AES-256 升级）**

- KMS Key ARN: `arn:aws:kms:ap-southeast-2:190239490233:key/ae43f207-...`
- KMS Alias: `alias/persons-finder-ecr-prod`
- Key Rotation: 已启用
- EKS Node Role: `kms:Decrypt`, `kms:DescribeKey`
- GitHub Actions Role: `kms:GenerateDataKey`, `kms:Decrypt`, `kms:DescribeKey`

---

### Infrastructure / 基础设施

**⚡ 实际 AWS 资源（prod 环境，全部已 apply）:**

| 资源 | 配置 | 状态 |
|---|---|---|
| VPC | 10.1.0.0/16，2 个 AZ，单 NAT Gateway | ✅ 运行中 |
| EKS Cluster | `persons-finder-prod`，K8s 1.32 | ✅ 运行中 |
| Node Group | **t3.small SPOT**（1 节点，最多 3）| ✅ 运行中 |
| ECR | `persons-finder`，IMMUTABLE，SSE-KMS | ✅ 运行中 |
| Secrets Manager | `persons-finder/openai-api-key` | ✅ 运行中 |
| IAM | OIDC Provider + 3 Roles | ✅ 运行中 |
| Terraform Backend | S3 `persons-finder-terraform-state` + DynamoDB | ✅ 运行中 |

> **⚡ 节点规格变更 / Node spec change:** 原始设计未指定实例类型。实际选用 `t3.small SPOT`（2 GiB RAM），是悉尼区域 Free Tier Eligible 中能运行 EKS Pod 的最小规格。SPOT 节省约 60-70% 成本。

**⚡ 额外安装的集群组件（Terraform 之外手动安装）:**

| 组件 | 安装方式 | 用途 |
|---|---|---|
| External Secrets Operator | Helm | AWS Secrets Manager → K8s Secret 同步 |
| metrics-server | kubectl apply | 为 HPA 提供 CPU 指标 |

---

### Container Architecture / 容器架构

**⚡ 实际 Dockerfile（版本已锁定）:**

```dockerfile
# 构建阶段
FROM gradle:7.6.4-jdk11-focal AS builder   # 原设计: gradle:7.6-jdk11（未锁定）

# 运行阶段
FROM eclipse-temurin:11.0.26_4-jre-alpine  # 原设计: eclipse-temurin:11-jre-alpine（未锁定）

RUN apk update && apk upgrade --no-cache   # 修复 Alpine OS CVE（gnutls, libpng）

# 非 root 用户（uid=1000）
USER appuser
```

**Trivy 扫描结果:** 本地构建 `0 CVE`（CRITICAL/HIGH，ignore-unfixed，含 .trivyignore）

**⚡ .trivyignore（7 个 Spring Boot 2.x wontfix CVE）:**
- `CVE-2016-1000027` (spring-web, 5.x wontfix)
- `CVE-2025-22235` (spring-boot, 需 3.x)
- `CVE-2025-41249` (spring-core, 需 Spring 6)
- `CVE-2024-38816`, `CVE-2024-38819` (spring-webmvc, 需 6.x)
- `GHSA-72hv-8253-57qq` (jackson, 需 2.18.6+)
- `CVE-2022-1471` (snakeyaml, 需 2.0)

---

### CI/CD Pipeline / CI/CD 流水线

**⚡ 实际流水线（3 个 Job，全部已验证）:**

```
push to main / push tag v*.*.*
         │
         ▼
┌─────────────────────────────┐
│  Job 1: Build and Test      │ ~50s
│  - JDK 11 (temurin)         │
│  - ./gradlew build          │
│  - ./gradlew test (315 个)  │
└──────────────┬──────────────┘
               │ needs: build-and-test
               ▼
┌─────────────────────────────────────────────┐
│  Job 2: Docker Build and Security Scan      │ ~90s
│  - OIDC → AWS credentials                  │
│  - ECR Login                               │
│  - docker/setup-buildx-action              │
│  - docker/metadata-action → tag 生成       │
│  - docker/build-push-action (build only)   │
│  - Trivy scan (apt 安装，0 CVE)             │
│  - Push to ECR (main branch 或 v* tag)     │
└───────────────────┬─────────────────────────┘
                    │ needs: docker-build-and-scan
                    │ if: push && (main || v*)
                    ▼
┌─────────────────────────────────────────────┐
│  Job 3: Deploy to EKS                       │ ~65s
│  - OIDC → AWS credentials                  │
│  - aws eks update-kubeconfig               │
│  - helm upgrade --install                  │
│  - kubectl rollout status (验证)            │
└─────────────────────────────────────────────┘
```

**⚡ 触发条件 / Triggers:**

| 事件 | Build+Test | Docker+Scan | Deploy |
|---|---|---|---|
| push to main | ✅ | ✅（build+push）| ✅ |
| push to develop | ✅ | ✅（build only）| ❌ |
| pull_request to main | ✅ | ✅（build+push pr-tag）| ❌ |
| push tag `v*.*.*` | ✅ | ✅（build+push semver）| ✅ |

**⚡ Trivy 安装方式变更:** 原始设计使用 `aquasecurity/trivy-action@master`（Action 版本），实际因该 Action 二进制下载不稳定导致 CI 持续失败，改为 `apt-get install trivy`（官方 deb 仓库）。

---

### Image Tag Strategy / 镜像 Tag 策略

**⚡ 实际 Tag 策略（原始设计未详细规定）:**

| Tag 格式 | 触发方式 | ECR 保留策略 | 示例 |
|---|---|---|---|
| `git-<sha>` | push to main | 保留最近 50 个 | `git-4f986c1` |
| `<semver>` | push tag `v1.4.2` | **永久保留**（无 lifecycle rule）| `1.4.2` |
| `pr-<num>` | pull request | 7 天后自动删除 | `pr-42` |
| ~~`latest`~~ | ❌ 不使用 | — | ECR IMMUTABLE 无法覆盖 |
| ~~`main`~~ | ❌ 不使用 | — | ECR IMMUTABLE 无法覆盖 |

**发布流程 / Release flow:**
```bash
git tag v1.4.2
git push origin v1.4.2
# CI 自动: 构建 → Trivy → 推 1.4.2 到 ECR → Helm 部署
```

---

### ECR Lifecycle Policy / ECR 生命周期策略

**⚡ 实际 Lifecycle Policy（原始设计有 Bug，tagPrefixList 用 `sha-` 从未匹配任何 tag）:**

```json
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Expire untagged images after 1 day",
      "selection": { "tagStatus": "untagged", "countType": "sinceImagePushed", "countUnit": "days", "countNumber": 1 }
    },
    {
      "rulePriority": 2,
      "description": "Expire PR images after 7 days",
      "selection": { "tagStatus": "tagged", "tagPrefixList": ["pr-"], "countType": "sinceImagePushed", "countUnit": "days", "countNumber": 7 }
    },
    {
      "rulePriority": 3,
      "description": "Keep last 50 CI build images (git-*)",
      "selection": { "tagStatus": "tagged", "tagPrefixList": ["git-"], "countType": "imageCountMoreThan", "countNumber": 50 }
    }
  ]
}
```

Semver 镜像（如 `1.4.2`）无规则匹配 → ECR 默认永久保留。

---

### Kubernetes Deployment Architecture / Kubernetes 部署架构

**⚡ 实际部署参数（CI 传入，覆盖 values-prod.yaml）:**

```bash
helm upgrade --install persons-finder devops/helm/persons-finder/ \
  --set image.tag="git-<sha>"      # 或 semver
  --set autoscaling.enabled=true \
  --set autoscaling.minReplicas=1 \ # 原始: 2（t3.small 单节点放不下）
  --set autoscaling.maxReplicas=3 \ # 原始: 10
  --set secrets.create=false \      # ESO 已创建，Helm 不重建
  --namespace default \
  --wait --timeout 3m
```

**⚡ 实际运行状态 / Actual Running State:**

```
Cluster: persons-finder-prod (ap-southeast-2)
Node: ip-10-1-3-40 (t3.small SPOT, Amazon Linux 2023, x86_64)

default namespace:
  Deployment: persons-finder (REVISION 4)
  Pod: persons-finder-xxx  1/1 Running
  Service: persons-finder  ClusterIP 172.20.49.149:80
  HPA: persons-finder      cpu: 0%/70%  min=1 max=3

kube-system:
  metrics-server           ✅ (HPA 数据源)
  external-secrets         ✅ (ESO, ClusterSecretStore READY)
  coredns (×2)
  aws-node, kube-proxy
```

**⚡ HPA 配置（autoscaling/v2）:**

```yaml
minReplicas: 1         # 原始设计: 2
maxReplicas: 3         # 原始设计: 10（单节点容量限制）
targetCPU: 70%
behavior:
  scaleDown:
    stabilizationWindowSeconds: 300   # 防止流量波动抖动
    policies: [{type: Percent, value: 50, periodSeconds: 60}]
  scaleUp:
    stabilizationWindowSeconds: 0
    policies:
      - {type: Percent, value: 100, periodSeconds: 30}
      - {type: Pods, value: 2, periodSeconds: 30}
    selectPolicy: Max
```

**Probe 配置 / Probes:** （与原始设计一致）

```yaml
readinessProbe:
  httpGet: {path: /actuator/health, port: 8080}
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 3

livenessProbe:
  httpGet: {path: /actuator/health, port: 8080}
  initialDelaySeconds: 60
  periodSeconds: 15
  timeoutSeconds: 5
```

**⚡ Ingress 状态:** 当前禁用（`ingress.enabled: false`）。原始设计需要 nginx ingress controller 和 cert-manager，均未安装。访问方式：`kubectl port-forward svc/persons-finder 8080:80`。

---

### Security / 安全

**⚡ 容器安全（实际已实现）:**

```yaml
podSecurityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000

securityContext:
  allowPrivilegeEscalation: false
  capabilities:
    drop: [ALL]
  readOnlyRootFilesystem: false
```

**⚡ 安全扫描结果 / Security Scan Results:**
- Trivy CRITICAL: **0**
- Trivy HIGH: **0**（含 .trivyignore 中 7 个 Spring 2.x wontfix）

---

### PII Protection / PII 保护

**Sidecar 状态:** `sidecar.enabled: false`（默认禁用，仅 values-prod.yaml 启用）

实际 PII 保护逻辑在应用代码内（`src/main/kotlin/com/persons/finder/pii/`）：

```
PiiDetector → PiiRedactor → PiiProxyService → AuditLogger
```

- 令牌映射表存储于每次请求的内存中，脱敏在单次代理事务内可逆
- AuditLogger 输出 JSON 结构化日志到 stdout（CloudWatch/ELK 可采集）

---

### Local Development / 本地开发

**⚡ 新增 Makefile（原始设计无）:**

```bash
make build   # 清理旧镜像 → 本地构建（宿主机架构，快速测试）
make scan    # 清理 → 构建 linux/amd64 → trivy 扫描 → 清理
             # 每次运行后自动删除镜像，不积累
make clean   # 删除本地测试镜像
```

**架构对齐:** 本地 Mac（ARM）build 用宿主机架构；`make scan` 强制 `--platform linux/amd64` 与 EKS t3.small（x86_64）对齐。

---

### AI Manifest Review / AI 生成 Manifest 问题记录

完整记录见 `docs/ai-manifest-review.md`。摘要：

| 资源 | AI 生成的主要问题 | 修复数 |
|---|---|---|
| Deployment | 无 readinessProbe、无 livenessProbe、内存超节点容量、跑 root、无 ECR 路径 | 9 |
| Service | type=LoadBalancer（产生费用）、selector 标签不匹配 | 3 |
| Ingress | 无 ingressClassName、无 TLS、无注解 | 4 |
| HPA | autoscaling/v1（废弃）、maxReplicas=100、无 scaleDown 稳定窗口 | 4 |
| Secrets | 第一反应是明文写入 env | — |

---

## Known Limitations / 已知限制

| 限制 | 原因 | 计划 |
|---|---|---|
| Spring Boot 2.7.x EOL | 7 个 CVE 需 Spring Boot 3.x + JDK 17 才能彻底修复 | 后续迁移 |
| 单节点 t3.small | 演示成本控制，HPA 最多 3 副本 | 生产扩容 |
| Ingress 未启用 | 无 nginx controller | 按需安装 |
| ECR Pull Through Cache 未配置 | 暂缓 | 待实现 |
| ESO IMDS hop limit 手动设置 | Terraform 未管理 | 固化到 Terraform |

---

## Pending / 待办

| 项目 | 优先级 |
|---|---|
| ECR Pull Through Cache（缓存 Docker Hub 基础镜像）| 中 |
| Spring Boot 3.x 迁移（彻底消除 .trivyignore）| 中 |
| nginx ingress controller + cert-manager（公网访问）| 低 |
| IMDS hop limit 固化到 Terraform node group 配置 | 低 |
| deployers RBAC 细化（当前为 cluster-admin，权限过大）| 低 |
