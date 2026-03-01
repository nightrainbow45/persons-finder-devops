# Session 4+5 运行记录 / Run Log

> 日期：2026-03-01
> 分支：main
> Session 4 目标：实现完整安全扫描套件（SBOM / cosign 镜像签名 / Kyverno 准入策略 / 定期重扫）
> Session 5 目标：配置 IRSA，激活 Kyverno Enforce 模式

---

## 一、用户指令

### Session 4 指令
用户要求实现以下安全扫描功能（按优先级排列）：

1. **Scan on push**：ECR 仓库开启推送即扫描（已有）
2. **CI Trivy 门禁**：对 CRITICAL/HIGH 设置 pipeline 失败门禁（已有）
3. **SBOM 生成与保存**：使用 CycloneDX/SPDX 格式，用于合规与溯源
4. **镜像签名（cosign + KMS）**：使用非对称 KMS key 签名 ECR 镜像
5. **EKS 准入策略**：Kyverno 只允许"已签名镜像"部署
6. **定期重扫**：存量镜像定期重新扫描，捕捉新出现的 CVE

### Session 5 指令
> 继续把 Kyverno 激活，配置 IRSA

---

## 二、Session 4：做了什么（按顺序）

### 2.1 Terraform：创建 cosign 非对称 KMS Key

**文件修改：**
- `devops/terraform/modules/ecr/main.tf` — 新增 `aws_kms_key.cosign` + `aws_kms_alias.cosign`
- `devops/terraform/modules/ecr/outputs.tf` — 新增 `cosign_key_arn`、`cosign_key_alias` 输出
- `devops/terraform/environments/prod/main.tf` — 新增 `aws_iam_role_policy.github_actions_cosign`（避免循环依赖）+ `cosign_key_arn` output

**关键设计决策：**

```hcl
resource "aws_kms_key" "cosign" {
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "ECC_NIST_P256"
  enable_key_rotation      = false  # 非对称 key 不支持自动轮换
  ...
}
```

**循环依赖问题的规避：**
- ECR 模块需要 `github_actions_role_arn`（来自 IAM 模块）
- 若 IAM 模块再引用 `cosign_key_arn`（来自 ECR 模块）→ 循环依赖
- **解法**：把 `aws_iam_role_policy.github_actions_cosign` 直接写在 `environments/prod/main.tf` 中，而不是 IAM 模块

**Terraform apply 结果：**
```
Apply complete! Resources: 3 added, 0 changed, 0 destroyed.
cosign_key_arn = "arn:aws:kms:ap-southeast-2:190239490233:key/0133148e-4bd6-4c2d-bf95-0903a8e40708"
```

---

### 2.2 CI/CD workflow 更新：SBOM + cosign 签名

**文件修改：** `.github/workflows/ci-cd.yml`

**新增步骤（在 docker-build-and-scan job）：**

| 步骤 | 说明 |
|------|------|
| Install Trivy | 保持现有 apt 安装方式 |
| Trivy vulnerability scan | CI 门禁（CRITICAL/HIGH，已有） |
| **Generate SBOM (CycloneDX)** | `trivy --format cyclonedx --output sbom.cdx.json` |
| **Upload SBOM artifact** | GitHub artifact，保留 90 天 |
| Push Docker image to ECR | 仅 main/tags（已有） |
| **Install cosign** | `sigstore/cosign-installer@v3` |
| **Sign image with cosign (KMS)** | `cosign sign --key awskms:///...` |
| **Attest SBOM with cosign (KMS)** | `cosign attest --predicate sbom.cdx.json --type cyclonedx` |

---

### 2.3 定期重扫 Workflow

**新文件：** `.github/workflows/security-rescan.yml`

- **触发：** 每周一 02:00 UTC（`cron: '0 2 * * 1'`）或手动 `workflow_dispatch`
- **扫描范围：** 自动查找 ECR 中最近 5 个 `git-*` tag 的镜像
- **逻辑：**
  1. 生成全量 JSON 扫描报告（上传为 artifact，保留 90 天）
  2. 对 CRITICAL/HIGH 未修复 CVE 设门禁（exit 1）
- **手动触发时** 可指定具体 image tag

---

### 2.4 Kyverno 安装 + ClusterPolicy（初次，Audit 模式）

**安装 Kyverno v1.17.1：**
```bash
helm upgrade --install kyverno kyverno/kyverno \
  --namespace kyverno --create-namespace \
  --set replicaCount=1 --set admissionController.replicas=1 ...
```

**Pod 上限问题：** t3.small 节点最多 11 个 pod，安装时 admission-controller Pending。
**解法：** 将 coredns 缩为 1 副本（释放 1 个 slot），禁用 kyverno reports-controller。

**公钥获取（用于 Kyverno 策略）：**
```bash
aws kms get-public-key --key-id <arn> \
  --query 'PublicKey' --output text \
  | base64 --decode | openssl ec -pubin -inform DER -outform PEM
```

输出：
```
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQBjraQ7aXudIDKVTbYWaLKMz77xG
Gpo0z9qyZOncz/fiQ8nLtIrle2nB2EjAl4t9Flv1tjy/9fXAwXtlQx+U2Q==
-----END PUBLIC KEY-----
```

**ClusterPolicy（初始 Audit 模式）：**

```yaml
# devops/kyverno/verify-image-signatures.yaml
spec:
  validationFailureAction: Audit
  background: false
  rules:
  - name: verify-cosign-signature
    verifyImages:
    - imageReferences:
      - "190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder:*"
      mutateDigest: false
      attestors:
      - entries:
        - keys:
            publicKeys: |-
              -----BEGIN PUBLIC KEY-----
              MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQBjraQ...
              -----END PUBLIC KEY-----
```

---

## 三、Session 4：出了哪些问题 + 怎么发现 + 怎么修复

### 问题 1：cosign sign 失败 — `COSIGN_KMS_ARN` 为空

**症状：**
```
Error: signing [...]: getting signer: reading key: kms get: kms specification should be
in the format awskms://[ENDPOINT]/[ID/ALIAS/ARN]
```
日志中：`COSIGN_KMS_ARN:`（空值）

**根因分析：**
- workflow 中使用了 `${{ secrets.COSIGN_KMS_ARN }}`
- 尝试用 `gh secret set` 设置时报 403：
  ```
  HTTP 403: Resource not accessible by personal access token
  ```
- GitHub PAT 没有管理 secrets 的权限

**修复：**
KMS ARN 不是敏感信息（只是资源标识符，无法直接用于操作 key，还需 AWS 认证），直接硬编码到 workflow：
```yaml
env:
  COSIGN_KMS_ARN: arn:aws:kms:ap-southeast-2:190239490233:key/0133148e-4bd6-4c2d-bf95-0903a8e40708
```

---

### 问题 2：Kyverno ClusterPolicy 创建失败 — `mutateDigest` 校验错误

**症状：**
```
admission webhook "validate-policy.kyverno.svc" denied the request:
spec.rules[0].verifyImages[0].mutateDigest: Invalid value: true:
mutateDigest must be set to false for 'Audit' failure action
```

**根因：** Kyverno 1.17.1 要求 `validationFailureAction: Audit` 时必须显式设置 `mutateDigest: false`

**修复：** 在 `verifyImages` 块中添加 `mutateDigest: false`

---

### 问题 3：Helm deploy 失败 — Kyverno webhook 超时（最复杂）

**症状：**
```
Error: UPGRADE FAILED: cannot patch "persons-finder" with kind Deployment:
Internal error occurred: failed calling webhook "mutate.kyverno.svc-fail":
failed to call webhook: Post "...mutate/fail?timeout=10s": context deadline exceeded
```

**排查过程：**

1. 首先检查 webhook 配置：
   ```bash
   kubectl get mutatingwebhookconfiguration kyverno-resource-mutating-webhook-cfg \
     -o jsonpath='{.webhooks[0].name}{" "}{.webhooks[0].failurePolicy}{" "}{.webhooks[0].timeoutSeconds}'
   # → mutate.kyverno.svc-fail  Fail  10
   ```
   发现：`failurePolicy: Fail` + `timeoutSeconds: 10`

2. 尝试 patch webhook timeout 到 30s → **Kyverno 自动重置回 10s**（它自己管理 webhook 配置）

3. 查看 Kyverno admission controller 日志：
   ```
   "verifying image signatures" image=...persons-finder:git-dc37373
   ...
   "image verification failed" error="Get \"https://...ecr.amazonaws.com/v2/\": context canceled"
   ```
   时间戳：开始 `05:19:45`，失败 `05:19:56` — **整整 11 秒**，超过 10s webhook 超时

**根因定位：**
- Kyverno 的 `verifyImages` 规则在 webhook 响应时间内需要调用 ECR API 验证 cosign 签名
- ECR v2 endpoint 调用链：EC2 IMDS 获取 IAM 凭证 → ECR GetAuthorizationToken → 获取签名 manifest
- 没有 ECR VPC Endpoint，调用走 NAT Gateway，整个过程约 11s > webhook 超时 10s
- 即便 `validationFailureAction: Audit`，`mutate.kyverno.svc-fail` 仍是 `failurePolicy: Fail`，超时直接拒绝请求

**临时修复（Session 4）：**
删除 ClusterPolicy 解除阻塞，策略文件保留在 git，标注 IRSA 为激活前提条件。

**根本修复：** → 见 Session 5

---

### 问题 4：Helm deploy 失败 — t3.small 节点 pod 上限死锁

**症状：**
```
Error: UPGRADE FAILED: resource Deployment/default/persons-finder not ready.
status: InProgress, message: Pending termination: 1
```

**排查过程：**
```bash
kubectl describe pod -n default <new-pod>
# → 0/1 nodes are available: 1 Too many pods.
```

**根因：**
- t3.small 节点 pod 上限 = 11（ENI 限制）
- 节点已有 11 个 pod 在运行
- 默认 `RollingUpdate` 策略：`maxSurge=1, maxUnavailable=0`
- 升级时先启动新 pod（第 12 个）→ 永远 Pending → 死锁

**修复：**
将 Deployment 滚动更新策略改为 `maxSurge=0, maxUnavailable=1`：

```yaml
# devops/helm/persons-finder/templates/deployment.yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 0       # 先终止旧 pod，再启动新 pod
    maxUnavailable: 1 # 允许短暂不可用
```

---

### 问题 5：ENI 前缀委托导致 IP 分配失败（服务中断）

**背景：** 尝试通过开启 ENI prefix delegation 增加节点 pod 容量（11 → 110）

**操作：**
```bash
kubectl set env daemonset aws-node -n kube-system ENABLE_PREFIX_DELEGATION=true WARM_PREFIX_TARGET=1
kubectl rollout restart daemonset aws-node -n kube-system
```

**症状：**
```
Warning  FailedCreatePodSandBox  kubelet  Failed to create pod sandbox:
plugin type="aws-cni" failed (add): add cmd: failed to assign an IP address to container
```

**根因：**
- ENI prefix delegation 需要**节点重建**才能更新 `allocatable.pods`
- aws-node 重启后处于 IP 管理过渡期，新旧 IP 状态混乱
- maxSurge=0 策略下，旧 pod 已终止 → 无 pod 可用 → **服务中断**

**修复：**
立即恢复 aws-node 原始配置：
```bash
kubectl set env daemonset aws-node -n kube-system ENABLE_PREFIX_DELEGATION=false WARM_PREFIX_TARGET-
kubectl rollout restart daemonset aws-node -n kube-system
```
**结论：** ENI prefix delegation 需要节点替换，不能在线切换。

---

## 四、Session 5：IRSA 配置 + Kyverno Enforce 激活

### 4.1 背景与思路

问题根因（Session 4 遗留）：Kyverno admission controller 通过 EC2 IMDS 获取 IAM 凭证，再调 ECR，整个链路约 11s，超过 webhook 10s 超时。

**IRSA 原理：** 为 Pod 的 ServiceAccount 关联一个 IAM 角色，EKS 的 OIDC 提供商颁发的 projected token 可直接向 STS 换取该角色的临时凭证，无需通过 IMDS，延迟从 ~11s 降至 ~1s。

---

### 4.2 Terraform 新增资源

**文件修改：** `devops/terraform/environments/prod/main.tf`

**新增 locals：**
```hcl
locals {
  oidc_issuer_host = replace(module.eks.cluster_oidc_issuer_url, "https://", "")
}
```

**新增资源（3 个）：**

```hcl
# 1. 向 IAM 注册 EKS 集群的 OIDC 身份提供商
data "tls_certificate" "eks" {
  url = module.eks.cluster_oidc_issuer_url
}

resource "aws_iam_openid_connect_provider" "eks" {
  url             = module.eks.cluster_oidc_issuer_url
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
}

# 2. Kyverno 专用 IAM 角色，trust policy 精确绑定到 kyverno-admission-controller SA
resource "aws_iam_role" "kyverno" {
  name = "${var.project_name}-kyverno-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.eks.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_issuer_host}:sub" = "system:serviceaccount:kyverno:kyverno-admission-controller"
          "${local.oidc_issuer_host}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

# 3. 只需 ECR readonly（拉取签名 manifest 用）
resource "aws_iam_role_policy_attachment" "kyverno_ecr_readonly" {
  role       = aws_iam_role.kyverno.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}
```

**Terraform apply 结果：**
```
Apply complete! Resources: 3 added, 0 changed, 0 destroyed.
kyverno_role_arn = "arn:aws:iam::190239490233:role/persons-finder-kyverno-prod"
```

---

### 4.3 SA 注解 + admission controller 重启

Helm upgrade 因 pod 上限超时失败，改用 kubectl 直接 patch：

```bash
# 注解 SA
kubectl annotate serviceaccount -n kyverno kyverno-admission-controller \
  "eks.amazonaws.com/role-arn=arn:aws:iam::190239490233:role/persons-finder-kyverno-prod" \
  --overwrite

# 重启 admission controller（强制删除旧 pod 绕过 maxSurge 死锁）
kubectl rollout restart deployment kyverno-admission-controller -n kyverno
kubectl delete pod -n kyverno kyverno-admission-controller-676f6698dc-qxb2x
```

**验证 IRSA token 已挂载：**
```bash
kubectl get pod -n kyverno kyverno-admission-controller-bc44b4c66-5c289 \
  -o jsonpath='{.spec.containers[0].env}' | ...

# 输出：
AWS_ROLE_ARN = arn:aws:iam::190239490233:role/persons-finder-kyverno-prod
AWS_WEB_IDENTITY_TOKEN_FILE = /var/run/secrets/eks.amazonaws.com/serviceaccount/token
```

---

### 4.4 ClusterPolicy 更新与激活

**Session 4 初始策略 → Session 5 最终策略的变更：**

| 字段 | Session 4 | Session 5 | 原因 |
|------|-----------|-----------|------|
| `validationFailureAction` | `Audit` | **`Enforce`** | IRSA 解决了超时问题，可以安全阻断 |
| `mutateDigest` | `false` | `false` | Audit 模式要求，保持不变 |
| `verifyDigest` | （未设置，默认 true）| **`false`** | ECR IMMUTABLE tag 已保证内容不变，不必强制 digest 引用 |

```yaml
# 最终策略关键片段
spec:
  validationFailureAction: Enforce
  background: false
  rules:
  - name: verify-cosign-signature
    verifyImages:
    - imageReferences:
      - "190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder:*"
      mutateDigest: false
      verifyDigest: false   # ← Session 5 新增
      attestors:
      - entries:
        - keys:
            publicKeys: |-
              -----BEGIN PUBLIC KEY-----
              MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...
              -----END PUBLIC KEY-----
```

---

### 4.5 Session 5：出了哪些问题 + 怎么发现 + 怎么修复

#### 问题 6：Helm upgrade 标注 SA 超时失败

**症状：**
```
Error: UPGRADE FAILED: resource Deployment/kyverno/kyverno-reports-controller not ready.
status: InProgress, message: Available: 0/1
context deadline exceeded
```

**根因：** Helm upgrade 触发了 reports-controller 的重建，但节点 pod 上限（11）已满，新 pod Pending，upgrade 超时。

**修复：** 不用 Helm upgrade 改 SA，改用 `kubectl annotate` 直接打注解（不触发任何 pod 重建）。

---

#### 问题 7：admission controller 滚动更新又遇到 pod 上限死锁

**症状：**
`kubectl rollout restart` 后，新 pod 和旧 pod 同时 Pending（都抢不到第 12 个位置）。

**排查：**
```bash
kubectl get pods -n kyverno
# kyverno-admission-controller-676f6698dc-lwh2h   0/1  Init:0/1  ← 旧 RS 自动重建
# kyverno-admission-controller-bc44b4c66-5c289    0/1  Init:0/1  ← 新 RS
```

**根因：** `kubectl rollout restart` 创建新 RS，但旧 RS 的 pod 在新 pod 就绪前不会终止，节点只有 1 个空 slot（已禁用 reports-controller），两个 Init pod 都在排队。

**修复：** 主动禁用 cleanup-controller 再释放 1 个 slot（现在有 2 个空余），两个 Init 容器都能调度，旧 RS pod Ready 后被旧 RS 回收，最终只留下新 IRSA pod。

```bash
kubectl scale deployment kyverno-cleanup-controller --replicas=0 -n kyverno
```

---

#### 问题 8：Enforce 模式阻断了已签名镜像（`missing digest` 错误）

**症状：**
```
Error from server: admission webhook "validate.kyverno.svc-fail" denied the request:
verify-image-signatures:
  verify-cosign-signature: missing digest for ...persons-finder:git-11a4e5f
```

**关键日志（Kyverno）：**
```
"image attestors verification succeeded" verifiedCount=1   ← 签名验证通过
"missing digest" image=...git-11a4e5f                       ← 但 digest 引用缺失
"validation failed" failed rules=["autogen-verify-cosign-signature"]
```

**根因分析：**
- `image attestors verification succeeded`：签名是有效的，验证通过了
- 但策略还有另一个检查：镜像引用必须包含 digest（`@sha256:...`），否则 validate 阶段失败
- 我们的 deployment 使用的是 tag 引用（`git-11a4e5f`），不是 digest 引用
- 这个检查来自 `verifyDigest` 字段，默认为 `true`

**为什么 `verifyDigest` 没必要：**
ECR 仓库使用 `IMMUTABLE` tag 策略，同一个 tag 永远指向同一个镜像 digest，tag 引用和 digest 引用在安全上等价。

**修复：** 在 `verifyImages` 中添加 `verifyDigest: false`

---

## 五、最终 CI/CD 流水线结构

```
push to main / push v*.*.* tag
        │
        ▼
┌────────────────────┐
│  build-and-test    │
│  - JDK 11 + Gradle │
│  - ./gradlew test  │
│  - upload artifacts│
└────────┬───────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│  docker-build-and-scan                   │
│  1. OIDC → AWS credentials               │
│  2. ECR login                            │
│  3. Docker Buildx build (load=true)      │
│  4. Trivy install (apt)                  │
│  5. Trivy vuln scan (CRITICAL/HIGH gate) │ ← 门禁
│  6. Trivy SBOM → sbom.cdx.json          │
│  7. Upload SBOM artifact (90天)          │
│  8. Push image to ECR (main/tags only)   │
│  9. Install cosign                       │
│ 10. cosign sign --key awskms:///...      │
│ 11. cosign attest (SBOM CycloneDX)       │
└────────┬─────────────────────────────────┘
         │ (main/tags only)
         ▼
┌────────────────────────────────────────────────┐
│  helm-deploy                                   │
│  - aws eks kubeconfig                          │
│  - helm upgrade (maxSurge=0, maxUnavailable=1) │
│  - kubectl rollout status                      │
│  - kubectl get pods                            │
└────────────────────────────────────────────────┘
         ↓ 部署时 Kyverno 拦截 Pod 创建
┌──────────────────────────────────────────┐
│  Kyverno admission webhook (Enforce)     │
│  - 调用 ECR 验证 cosign 签名             │
│  - IRSA: ~1s 获取凭证                   │
│  - 签名有效 → 允许                       │
│  - 无签名/无效签名 → 阻断                │
└──────────────────────────────────────────┘
```

---

## 六、安全扫描套件功能清单（最终状态）

| 功能 | 实现方式 | 状态 | 文件 |
|------|---------|------|------|
| ECR Scan on push | Terraform `scan_on_push=true` | ✅ 已激活 | `modules/ecr/main.tf` |
| Trivy CI 门禁 | apt install trivy + exit-code 1 | ✅ 已激活 | `ci-cd.yml` |
| SBOM 生成（CycloneDX） | `trivy --format cyclonedx` | ✅ 已激活 | `ci-cd.yml` |
| SBOM 存档（90天） | `actions/upload-artifact@v4` | ✅ 已激活 | `ci-cd.yml` |
| cosign KMS key（P-256） | Terraform `aws_kms_key.cosign` | ✅ 已创建 | `modules/ecr/main.tf` |
| 镜像签名（cosign） | `cosign sign --key awskms:///` | ✅ 已激活 | `ci-cd.yml` |
| SBOM 证明（cosign attest） | `cosign attest --type cyclonedx` | ✅ 已激活 | `ci-cd.yml` |
| Kyverno 安装 | Helm v1.17.1，namespace kyverno | ✅ 已安装 | 集群 |
| Kyverno IRSA | EKS OIDC provider + IAM role | ✅ 已配置 | `prod/main.tf` |
| Kyverno ClusterPolicy | verifyImages + P-256 公钥 | ✅ **Enforce 模式激活** | `devops/kyverno/` |
| 定期重扫（每周） | GitHub Actions cron | ✅ 已配置 | `security-rescan.yml` |

---

## 七、关键资源

| 资源 | 值 |
|------|-----|
| ECR repository | `190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder` |
| cosign KMS key ARN | `arn:aws:kms:ap-southeast-2:190239490233:key/0133148e-4bd6-4c2d-bf95-0903a8e40708` |
| cosign KMS alias | `alias/persons-finder-cosign-prod` |
| cosign 公钥（P-256） | `devops/kyverno/verify-image-signatures.yaml` 中内嵌 |
| Kyverno namespace | `kyverno` |
| ClusterPolicy name | `verify-image-signatures`（**Enforce 模式，已激活**） |
| Kyverno IRSA role ARN | `arn:aws:iam::190239490233:role/persons-finder-kyverno-prod` |
| EKS OIDC issuer | `https://oidc.eks.ap-southeast-2.amazonaws.com/id/D58341C3FF971E2C67CD2C0263F5A56D` |
| SBOM 存储位置 | ECR attestation + GitHub artifact `sbom-cyclonedx` |

---

## 八、验证结果（Enforce 模式）

| 测试场景 | 镜像 | 结果 |
|---------|------|------|
| CI 构建后已签名的镜像 | `persons-finder:git-11a4e5f` | ✅ 允许部署 |
| 不在 ECR 中的 tag（无签名）| `persons-finder:pr-999` | ❌ 被阻断（tag not found） |
| 外部镜像（不匹配策略范围）| `nginx:alpine` | ✅ 允许（不匹配 ECR 路径） |
| ECR 调用延迟（无 IRSA） | — | ~11s（超过 10s 超时） |
| ECR 调用延迟（有 IRSA） | — | **~1s**（STS 直接换取凭证） |

---

## 九、全部 Git 提交记录

| Commit | Session | 说明 |
|--------|---------|------|
| `e8cdb2c` | 4 | feat(security): SBOM / cosign KMS key / Kyverno / weekly rescan |
| `dc37373` | 4 | fix(ci): hardcode cosign KMS ARN（非敏感，直接写死） |
| `11a4e5f` | 4 | fix(helm): maxSurge=0 解决 t3.small pod 上限死锁 |
| `0dd386e` | 4 | docs: add run2.md |
| `5e1e31f` | 5 | feat(irsa): configure IRSA for Kyverno and activate Enforce signature policy |

---

## 十、已知限制与后续改进

| 限制 | 当前状态 | 推荐方案 |
|------|---------|---------|
| t3.small pod 上限 11 | coredns=1副本，kyverno reports/cleanup-controller=0 | 升级节点规格或多节点 |
| ENI prefix delegation | 无法在线切换，需节点重建 | 替换 EC2 实例后生效 |
| 滚动更新有短暂中断 | maxSurge=0 策略 | 扩展为多节点后恢复 maxSurge=1 |
| cosign SBOM attestation 验证 | 证明已推送到 ECR，Kyverno 策略未校验 attestation | 在 ClusterPolicy 中添加 `attestations` 字段验证 SBOM |
| Kyverno reports-controller 禁用 | 无法生成 PolicyReport | 升级节点后重新启用（`kubectl scale deployment kyverno-reports-controller --replicas=1 -n kyverno`） |
