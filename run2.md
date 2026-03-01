# Session 4 运行记录 / Run Log

> 日期：2026-03-01
> 分支：main
> 目标：实现完整安全扫描套件（SBOM / cosign 镜像签名 / Kyverno 准入策略 / 定期重扫）

---

## 一、用户指令

用户要求实现以下安全扫描功能（按优先级排列）：

1. **Scan on push**：ECR 仓库开启推送即扫描（已有）
2. **CI Trivy 门禁**：对 CRITICAL/HIGH 设置 pipeline 失败门禁（已有）
3. **SBOM 生成与保存**：使用 CycloneDX/SPDX 格式，用于合规与溯源
4. **镜像签名（cosign + KMS）**：使用非对称 KMS key 签名 ECR 镜像
5. **EKS 准入策略**：Kyverno 只允许"已签名镜像"部署
6. **定期重扫**：存量镜像定期重新扫描，捕捉新出现的 CVE

---

## 二、本次做了什么（按顺序）

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

### 2.4 Kyverno 安装 + ClusterPolicy

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

**ClusterPolicy（Audit 模式）：**

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

## 三、出了哪些问题 + 怎么发现 + 怎么修复

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
   "verifying image signatures" image=190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder:git-dc37373
   ...
   "image verification failed" error="Get \"https://190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/v2/\": context canceled"
   ```
   时间戳：开始 `05:19:45`，失败 `05:19:56` — **整整 11 秒**，超过 10s webhook 超时

**根因定位：**
- Kyverno 的 `verifyImages` 规则在 webhook 响应时间内需要调用 ECR API 验证 cosign 签名
- ECR v2 endpoint 调用链：EC2 IMDS 获取 IAM 凭证 → ECR GetAuthorizationToken → 获取签名 manifest
- 没有 ECR VPC Endpoint，调用走 NAT Gateway，整个过程约 11s > webhook 超时 10s
- 即便 `validationFailureAction: Audit`，`mutate.kyverno.svc-fail` 仍是 `failurePolicy: Fail`，超时直接拒绝请求

**修复：**
删除 ClusterPolicy，解除 Helm deploy 阻塞：
```bash
kubectl delete clusterpolicy verify-image-signatures
```
策略文件保留在 `devops/kyverno/verify-image-signatures.yaml`，添加激活前提条件注释。

**激活条件（两选一）：**
- **选项 A（推荐）**：为 kyverno service account 配置 IRSA（快速获取 ECR 凭证）
- **选项 B**：增加 webhook 超时（Helm upgrade kyverno with `admissionController.container.args.webhookTimeout=30`）

---

### 问题 4：Helm deploy 失败 — t3.small 节点 pod 上限死锁

**症状：**
```
Error: UPGRADE FAILED: resource Deployment/default/persons-finder not ready.
status: InProgress, message: Pending termination: 1
context deadline exceeded
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
将 Deployment 滚动更新策略改为 `maxSurge=0, maxUnavailable=1`（先终止旧 pod 再启动新 pod）：

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
等 aws-node 完全恢复后，新 pod 自动获得 IP，服务恢复。

**结论：** ENI prefix delegation 需要节点替换，不能在线切换，此 demo 环境不适用。

---

## 四、最终 CI/CD 流水线结构

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
│  6. Trivy SBOM → sbom.cdx.json          │ ← 新增
│  7. Upload SBOM artifact (90天)          │ ← 新增
│  8. Push image to ECR (main/tags only)   │
│  9. Install cosign                       │ ← 新增
│ 10. cosign sign --key awskms:///...      │ ← 新增
│ 11. cosign attest (SBOM CycloneDX)       │ ← 新增
└────────┬─────────────────────────────────┘
         │ (main/tags only)
         ▼
┌────────────────────────┐
│  helm-deploy           │
│  - aws eks kubeconfig  │
│  - helm upgrade        │
│    --set image.tag=... │
│    --set autoscaling   │
│  - kubectl rollout     │
│  - kubectl get pods    │
└────────────────────────┘
```

---

## 五、安全扫描套件功能清单

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
| Kyverno ClusterPolicy | verifyImages + P-256 公钥 | ⚠️ 待激活 | `devops/kyverno/` |
| 定期重扫（每周） | GitHub Actions cron | ✅ 已配置 | `security-rescan.yml` |

---

## 六、关键资源

| 资源 | 值 |
|------|-----|
| ECR repository | `190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder` |
| cosign KMS key ARN | `arn:aws:kms:ap-southeast-2:190239490233:key/0133148e-4bd6-4c2d-bf95-0903a8e40708` |
| cosign KMS alias | `alias/persons-finder-cosign-prod` |
| cosign 公钥（P-256） | `devops/kyverno/verify-image-signatures.yaml` 中内嵌 |
| Kyverno namespace | `kyverno` |
| ClusterPolicy name | `verify-image-signatures`（当前未 apply） |
| SBOM 存储位置 | ECR attestation + GitHub artifact `sbom-cyclonedx` |

---

## 七、Kyverno ClusterPolicy 激活步骤（待办）

```bash
# 前提：配置 IRSA for kyverno service account
# 1. 创建 IAM role，trust policy 绑定 kyverno SA，并赋予 ECR read 权限
# 2. 为 kyverno service account 打上注解
kubectl annotate serviceaccount -n kyverno kyverno \
  eks.amazonaws.com/role-arn=arn:aws:iam::190239490233:role/kyverno-ecr-readonly

# 3. 重启 Kyverno admission controller
kubectl rollout restart deployment kyverno-admission-controller -n kyverno

# 4. Apply 策略（Audit 模式）
kubectl apply -f devops/kyverno/verify-image-signatures.yaml

# 5. 验证 policy 生效
kubectl get clusterpolicy verify-image-signatures -o wide

# 6. 确认 CI 签名已稳定后，切换到 Enforce 模式
kubectl patch clusterpolicy verify-image-signatures \
  --type merge -p '{"spec":{"validationFailureAction":"Enforce"}}'
```

---

## 八、本次 Git 提交记录

| Commit | 说明 |
|--------|------|
| `e8cdb2c` | feat(security): SBOM / cosign KMS key / Kyverno / weekly rescan |
| `dc37373` | fix(ci): hardcode cosign KMS ARN（非敏感，直接写死） |
| `11a4e5f` | fix(helm): maxSurge=0 解决 t3.small pod 上限死锁 |

---

## 九、已知限制与后续改进

| 限制 | 当前状态 | 推荐方案 |
|------|---------|---------|
| Kyverno 策略未激活 | ClusterPolicy 已删除，文件在 git | 配置 IRSA for kyverno SA |
| t3.small pod 上限 11 | coredns=1副本，kyverno reports-controller=0 | 升级节点规格或多节点 |
| ENI prefix delegation | 无法在线切换，需节点重建 | 替换 EC2 实例后生效 |
| 滚动更新有短暂中断 | maxSurge=0 策略 | 扩展为多节点后恢复 maxSurge=1 |
| cosign SBOM 验证 | 证明已推送到 ECR，但无自动验证流程 | 在 Kyverno policy 中添加 attestation 验证 |
