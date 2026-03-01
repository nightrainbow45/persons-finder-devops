# Session 7 运行记录 / Run Log

> 日期：2026-03-02
> 分支：main
> Session 7 目标 1：从零重建 prod 环境（terraform destroy 后的完整恢复）
> Session 7 目标 2：更新 devops/scripts/ 目录下所有脚本，添加详细中英双语注释

---

## 一、用户指令

### 指令 1
> "阅读 run1.md run2.md 了解背景"

### 指令 2
> "重新部署 prod 环境"

### 指令 3（含 OpenAI key）
> "帮我设置然后，帮我强制同步 ESO"
> `sk-proj-LoSeAtvyE4jgzgCBp-42RITK...`（用户直接提供 OpenAI API key）

### 指令 4
> "更新 devops 文件夹的里面的 scripts 里面的各个脚本，并且加上详细的注释"

### 指令 5
> "创建 run3.md 文件，规则同 run1.md 和 run2.md，将更新内容放进 run3.md 中去"

---

## 二、重建 prod 环境：完整过程

### 2.1 背景：上次（Session 6）执行了 terraform destroy

Session 6 销毁了全部 60 个 AWS 资源，本次从零重建：
- EKS 集群 `persons-finder-prod`
- VPC / 子网 / NAT Gateway / 安全组
- ECR 仓库
- IAM 角色（admin / developer / github-actions / kyverno-irsa）
- KMS keys（ECR 加密 / Secrets 加密 / cosign ECC_NIST_P256）
- Secrets Manager secret
- EKS OIDC 身份提供商

---

### 2.2 Terraform apply 前的准备工作

#### 2.2.1 Secrets Manager secret 处于 PendingDeletion 状态

**问题：** 上次 terraform destroy 后，secret `persons-finder/prod/openai-api-key` 进入 30 天删除冷却期，Terraform 无法直接重建同名 secret。

**错误：**
```
Error: creating Secrets Manager Secret: ResourceExistsException:
  You can't create this secret because a secret with this name is already scheduled for deletion.
```

**修复：**
```bash
aws secretsmanager delete-secret \
  --secret-id "persons-finder/prod/openai-api-key" \
  --force-delete-without-recovery \
  --region ap-southeast-2
```
强制立即删除后，Terraform 可正常重建。

#### 2.2.2 发现 4 个 KMS Key 处于 PendingDeletion 状态

上次销毁遗留的 KMS key（30 天等待期内），不影响 Terraform 重建，因为 alias 已释放，Terraform 会创建**新 key、新 ARN**。

```
旧 cosign key: 0133148e-4bd6-4c2d-bf95-0903a8e40708 → PendingDeletion
旧 ECR key:    588a3e23-...                          → PendingDeletion
旧 SM key:     ae43f207-...                          → PendingDeletion
旧 SM key:     b94d4245-...                          → PendingDeletion
```

**影响：** 新基础设施使用全新 ARN，因此必须同步更新：
1. `devops/kyverno/verify-image-signatures.yaml` 中的 cosign 公钥
2. `.github/workflows/ci-cd.yml` 中的 `COSIGN_KMS_ARN`

#### 2.2.3 新增 IMDS hop limit=2（Terraform EKS module 改动）

**文件：** `devops/terraform/modules/eks/main.tf`

**问题背景：** ESO（External Secrets Operator）通过 EC2 IMDS 获取 IAM 凭证。默认 hop limit=1 时，Pod 的网络请求到达 node 的 veth 已消耗 1 跳，无法继续访问 IMDS（169.254.169.254），导致 ESO 无法获取凭证。

**修复：** 新增 Launch Template 并设置 hop limit=2：

```hcl
resource "aws_launch_template" "eks_nodes" {
  name_prefix = "${var.project_name}-${var.environment}-nodes-"
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "optional"
    http_put_response_hop_limit = 2   # 允许 Pod→veth→IMDS 共 2 跳
  }
  tags      = merge(var.tags, { Name = "${var.project_name}-nodes-lt-${var.environment}" })
  lifecycle { create_before_destroy = true }
}
```

并在 `aws_eks_node_group` 中引用：
```hcl
launch_template {
  id      = aws_launch_template.eks_nodes.id
  version = aws_launch_template.eks_nodes.latest_version
}
```

---

### 2.3 Terraform apply 结果

```
Apply complete! Resources: 61 added, 0 changed, 0 destroyed.

Outputs:
cluster_endpoint       = "https://7E4C...ap-southeast-2.eks.amazonaws.com"
ecr_repository_url     = "190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder"
cosign_key_arn         = "arn:aws:kms:ap-southeast-2:190239490233:key/6e0f596a-0608-45de-bf42-d9ddb8ea960f"
kyverno_role_arn       = "arn:aws:iam::190239490233:role/persons-finder-kyverno-prod"
```

新 cosign KMS ARN（ECC_NIST_P256）：`6e0f596a-0608-45de-bf42-d9ddb8ea960f`

---

### 2.4 配置 kubectl

```bash
aws eks update-kubeconfig \
  --name persons-finder-prod \
  --region ap-southeast-2
kubectl wait --for=condition=Ready nodes --all --timeout=5m
```

---

### 2.5 安装 metrics-server（HPA 依赖）

```bash
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/
helm upgrade --install metrics-server metrics-server/metrics-server \
  --namespace kube-system \
  --set args[0]="--kubelet-insecure-tls" \
  --wait --timeout 3m
```

metrics-server 为 HPA 提供 CPU/Memory 指标，不安装则 HPA 无法正常工作。

---

### 2.6 安装 Kyverno v1.17.1（chart 3.7.1）

```bash
helm upgrade --install kyverno kyverno/kyverno \
  --namespace kyverno --create-namespace \
  --version 3.7.1 \
  --set reportsController.enabled=false \
  --set cleanupController.enabled=false \
  --wait --timeout 5m
```

**为什么禁用 reports-controller 和 cleanup-controller？**
t3.small 节点 ENI 限制最多 11 个 Pod。禁用这两个可选组件节省 2 个 slot，确保应用 Pod 可调度。

---

### 2.7 配置 Kyverno IRSA

```bash
kubectl annotate serviceaccount kyverno-admission-controller \
  -n kyverno \
  "eks.amazonaws.com/role-arn=arn:aws:iam::190239490233:role/persons-finder-kyverno-prod" \
  --overwrite

kubectl rollout restart deployment kyverno-admission-controller -n kyverno
kubectl rollout status deployment kyverno-admission-controller -n kyverno --timeout=3m
```

**IRSA 的效果：**
- 无 IRSA：Pod → veth → node → IMDS → IAM Role → STS → ECR，约 11s（超出 webhook 10s 超时）
- 有 IRSA：EKS 控制平面直接向 Pod 注入 STS Web Identity Token，Pod 直接向 STS 换凭证，约 1s

---

### 2.8 提取新 cosign 公钥并更新策略文件

KMS key 重建后 ARN 变化，公钥也随之变化，必须重新提取：

```bash
aws kms get-public-key \
  --key-id "arn:aws:kms:ap-southeast-2:190239490233:key/6e0f596a-0608-45de-bf42-d9ddb8ea960f" \
  --query 'PublicKey' \
  --output text \
  | base64 --decode \
  | openssl ec -pubin -inform DER -outform PEM
```

新公钥（ECC_NIST_P256）：
```
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEV/I0wjO3J+vMUFzHgXvt5mYmZK3s
JoH79cL9fSUFQODmG1NhcU2GVUrs3mgQIJy/YhjL0zK4qL9sfTAoB7bLyA==
-----END PUBLIC KEY-----
```

**文件更新：**

| 文件 | 更新内容 |
|---|---|
| `devops/kyverno/verify-image-signatures.yaml` | `cosign-kms-arn` 注解 + `publicKeys` 字段 |
| `.github/workflows/ci-cd.yml` | `COSIGN_KMS_ARN` env 变量 |

**Kyverno 策略关键配置说明：**
```yaml
spec:
  validationFailureAction: Enforce  # 阻断未签名镜像（非 Audit）
  background: false                 # 仅在 Pod 创建时检查，不扫描存量
  rules:
  - verifyImages:
    - mutateDigest: false           # 不将 tag 改写为 digest 引用
      verifyDigest: false           # 不强制要求 digest 引用（ECR IMMUTABLE tags 保证同一 tag 对应同一镜像）
```

---

### 2.9 应用 Kyverno ClusterPolicy

```bash
kubectl apply -f devops/kyverno/verify-image-signatures.yaml
# 验证
kubectl get clusterpolicy verify-image-signatures \
  -o jsonpath='{.spec.validationFailureAction}'
# 输出: Enforce
```

---

### 2.10 安装 External Secrets Operator（ESO）

```bash
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace external-secrets --create-namespace \
  --wait --timeout 5m
```

---

### 2.11 新建 ESO Kubernetes 资源文件

本次重建时发现这两个文件在代码仓库中不存在，遂新建：

**`devops/k8s/cluster-secret-store.yaml`**
```yaml
apiVersion: external-secrets.io/v1
kind: ClusterSecretStore
metadata:
  name: aws-secrets-manager
spec:
  provider:
    aws:
      service: SecretsManager
      region: ap-southeast-2
      # 无 role 字段：使用节点 EC2 Instance Role（通过 IMDS）认证
      # No 'role' field: uses EC2 instance role via IMDS (hop limit=2 required)
```

**`devops/k8s/external-secret.yaml`**
```yaml
apiVersion: external-secrets.io/v1
kind: ExternalSecret
metadata:
  name: persons-finder-secrets
  namespace: default
spec:
  refreshInterval: 5m
  secretStoreRef:
    name: aws-secrets-manager
    kind: ClusterSecretStore
  target:
    name: persons-finder-secrets
    creationPolicy: Owner
  data:
  - secretKey: OPENAI_API_KEY
    remoteRef:
      key: persons-finder/prod/openai-api-key
      property: OPENAI_API_KEY
```

```bash
kubectl apply -f devops/k8s/cluster-secret-store.yaml
kubectl apply -f devops/k8s/external-secret.yaml
```

---

### 2.12 设置 OpenAI API Key + 强制同步 ESO

**设置 key（用户在对话中提供）：**
```bash
aws secretsmanager put-secret-value \
  --secret-id "persons-finder/prod/openai-api-key" \
  --secret-string '{"OPENAI_API_KEY":"sk-proj-LoSeAtvyE4jgzg..."}' \
  --region ap-southeast-2
```

**强制 ESO 立即同步（不等 5 分钟刷新间隔）：**
```bash
kubectl annotate externalsecret persons-finder-secrets \
  force-sync=$(date +%s) --overwrite -n default
```

**验证同步成功：**
```bash
kubectl get externalsecret persons-finder-secrets -n default
# READY: True   STATUS: SecretSynced
```

---

### 2.13 第一次 CI/CD run — Helm deploy 失败（RBAC bug）

推送代码触发 CI/CD，Build / Trivy / cosign 全部通过，Helm deploy 失败：

```
Error: UPGRADE FAILED: create: failed to create:
secrets is forbidden: User "github-actions" cannot list resource "secrets"
in API group "" in the namespace "default"
```

**根因分析：**

`aws-auth` ConfigMap 中 GitHub Actions IAM role 映射到了 `deployers` K8s group，但始终没有为这个 group 创建对应的 `ClusterRole` 和 `ClusterRoleBinding`。以前没暴露是因为之前会话的集群是从现有 state 操作的，这次从零重建后才首次触发此 bug。

**修复：** 在 `devops/terraform/modules/eks/aws-auth.tf` 中新增：

```hcl
resource "kubernetes_cluster_role" "deployer" {
  metadata { name = "deployer" }
  rule {
    api_groups = [""]
    resources  = ["secrets", "configmaps", "services", "serviceaccounts",
                  "pods", "persistentvolumeclaims"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }
  rule {
    api_groups = ["apps"]
    resources  = ["deployments", "replicasets", "statefulsets", "daemonsets"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }
  rule {
    api_groups = ["networking.k8s.io"]
    resources  = ["ingresses"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }
  rule {
    api_groups = ["autoscaling"]
    resources  = ["horizontalpodautoscalers"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }
  rule {
    api_groups = ["rbac.authorization.k8s.io"]
    resources  = ["roles", "rolebindings"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }
  depends_on = [aws_eks_cluster.main]
}

resource "kubernetes_cluster_role_binding" "deployer" {
  metadata { name = "deployer" }
  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = kubernetes_cluster_role.deployer.metadata[0].name
  }
  subject {
    kind      = "Group"
    name      = "deployers"
    api_group = "rbac.authorization.k8s.io"
  }
  depends_on = [aws_eks_cluster.main]
}
```

为什么 deployers role 需要 `secrets` CRUD？
Helm 将 release state 存储为 K8s Secrets（`sh.helm.release.v1.*`），deploy 时必须读写这些 secret。

**Terraform apply（仅 RBAC fix）：**
```
Apply complete! Resources: 2 added, 0 changed, 0 destroyed.
```

---

### 2.14 第二次 CI/CD run — 全流程通过

Run #22554799028：
```
Build and Test      ✅  53s
Docker Build+Scan   ✅  1m 32s
  - Trivy: 0 CVE
  - SBOM: generated (sbom.cdx.json)
  - ECR push: ✅
  - cosign sign: ✅
  - cosign attest SBOM: ✅
Deploy to EKS       ✅  1m 1s
  - Helm upgrade: ✅
  - kubectl rollout status: ✅
```

---

## 三、脚本注释更新

### 3.1 更新范围

对 `devops/scripts/` 目录下的所有脚本和文档添加详细的中英双语注释：

| 文件 | 更新内容 |
|---|---|
| `setup-eks.sh` | 完全重写，10 个步骤各有中英注释，新增 metrics-server 步骤，新增 ESO 资源应用步骤 |
| `teardown-eks.sh` | 每个销毁步骤注释销毁顺序原因，KMS 30 天 PendingDeletion 警告 |
| `deploy.sh` | 注释 namespace 映射逻辑、secrets.create 标志的原因、镜像 tag 规范 |
| `verify.sh` | 每个检查项说明其意义，ESO 状态异常时输出排查命令 |
| `local-test.sh` | 说明 Kind 镜像加载原理、每个 --set 参数的本地 vs prod 差异原因 |
| `README.md` | 删除不存在的 setup-github-oidc.sh 引用，新增环境对比表、安全架构说明、KMS 重建警告 |

### 3.2 注释内容举例

**teardown-eks.sh — 为什么先删 ClusterPolicy：**
```bash
# Kyverno ClusterPolicy 处于 Enforce 模式，所有创建/删除 Pod 的操作都经过
# Kyverno admission webhook 验证。如果在 webhook 还活跃时开始销毁流程，
# webhook 可能拦截 Pod 终止请求，导致资源卡在 Terminating 状态。
# The Kyverno policy is in Enforce mode: all pod create/delete operations pass
# through the admission webhook. Starting destroy while the webhook is active
# may intercept and block pod termination, leaving resources stuck in Terminating.
```

**deploy.sh — 为什么 prod 使用 secrets.create=false：**
```bash
# prod：secrets.create=false — 由 ESO 负责创建和维护 Secret，Helm 不介入
#       如果 Helm 创建了 Secret，ESO 会因"already exists"冲突而失败
# prod: secrets.create=false — ESO owns the Secret lifecycle; if Helm also
#       creates it, ESO will fail with "already exists" conflict
```

**local-test.sh — 为什么需要 kind load docker-image：**
```bash
# Kind 的节点是 Docker 容器，无法直接访问宿主机的 Docker image cache
# Kind nodes are Docker containers and cannot access the host's Docker image cache
# kind load docker-image 将镜像从宿主机 Docker 复制到 Kind 节点的 containerd
# kind load copies the image from host Docker into the Kind node's containerd
# 这样 Pod 才能使用 imagePullPolicy=Never 而不走 registry 拉取
# This allows pods to use imagePullPolicy=Never without pulling from a registry
```

**setup-eks.sh — IRSA 原理说明：**
```bash
# 无 IRSA 的情况：Pod → veth → node → EC2 IMDS → IAM Role → STS → ECR 调用约 11 秒
# Without IRSA: Pod → veth → node → IMDS → IAM Role → STS → ECR, ~11s
# 有 IRSA 的情况：EKS 控制平面直接为 Pod 注入 STS Web Identity Token（projected volume）
# With IRSA: EKS injects STS Web Identity Token directly via projected SA volume
# Pod 用此 token 直接向 STS 换取 IAM 临时凭证，跳过 IMDS 链路，约 1 秒
# Pod exchanges token for IAM credentials directly with STS, ~1s latency
```

---

## 四、Git 提交记录

| Commit | 消息 |
|---|---|
| `2af0589` | `feat(infra): redeploy prod - new cosign KMS key, IMDS fix, ESO manifests` |
| `63fd4e1` | `fix(rbac): add ClusterRole and ClusterRoleBinding for deployers group` |
| `2156107` | `docs(scripts): add detailed bilingual comments to all devops scripts` |

---

## 五、当前基础设施状态

| 资源 | 状态 |
|---|---|
| EKS cluster `persons-finder-prod` | Running ✅ |
| Node group（t3.small SPOT）| Ready ✅ |
| ECR repository `persons-finder` | Active ✅ |
| cosign KMS key | `6e0f596a-...`，Active ✅ |
| Kyverno v1.17.1 | Running，Enforce 模式 ✅ |
| ESO ClusterSecretStore | Valid ✅ |
| ESO ExternalSecret | SecretSynced ✅ |
| Persons Finder app | 1 Pod Running，HPA min=1 max=3 ✅ |
| CI/CD pipeline | 全绿（Build / Trivy / cosign / Helm） ✅ |

### Pod 分布（t3.small，11 pod 上限）

| Pod | Namespace |
|---|---|
| coredns（1 副本） | kube-system |
| aws-node（daemonset） | kube-system |
| kube-proxy（daemonset） | kube-system |
| metrics-server | kube-system |
| kyverno-admission-controller | kyverno |
| kyverno-background-controller | kyverno |
| eso-external-secrets（controller） | external-secrets |
| eso-cert-controller | external-secrets |
| persons-finder | default |

共约 9-10 个 Pod，保留足够余量（reports-controller 和 cleanup-controller 已禁用）。

---

## 六、重建后必须检查的事项（下次再执行 terraform destroy 后）

每次重建基础设施后，KMS cosign key 会变化，必须执行以下操作：

1. **提取新公钥：**
   ```bash
   aws kms get-public-key \
     --key-id "$(terraform -chdir=devops/terraform/environments/prod output -raw cosign_key_arn)" \
     --query 'PublicKey' --output text \
     | base64 --decode | openssl ec -pubin -inform DER -outform PEM
   ```

2. **更新 Kyverno 策略中的公钥：**
   `devops/kyverno/verify-image-signatures.yaml` → `publicKeys` 字段

3. **更新 CI workflow 中的 KMS ARN：**
   `.github/workflows/ci-cd.yml` → `COSIGN_KMS_ARN` env 变量

4. **重新 push 触发 CI/CD，验证签名和 Kyverno 策略联动正常。**

不执行以上步骤的后果：cosign 签名使用新 key，但 Kyverno 策略还验证旧公钥 → 所有 Pod 创建被 Enforce 策略阻断。

---

## 七、下次对话的起点

- prod 环境完整运行，CI/CD 全绿
- 所有脚本已更新详细注释并已提交
- 如需再次销毁：`./devops/scripts/teardown-eks.sh prod`
- 如需再次重建：`./devops/scripts/setup-eks.sh prod`（脚本已包含 IMDS / IRSA / ESO / Kyverno 全流程）
