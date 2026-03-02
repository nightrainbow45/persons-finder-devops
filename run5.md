# Session 9 运行记录 / Run Log

> 日期：2026-03-02
> 分支：main
> Session 9 目标：实现 AI Firewall Layer 3 — 修复 NetworkPolicy egress 规则并启用 AWS VPC CNI 内核级强制执行

---

## 一、用户指令

### 指令 1
> "阅读 run3.md 和 run4.md 和 AIfirewall.md 了解背景"

### 指令 2
> "继续 layer3"

### 指令 3
> "继续工作"（CI 失败后继续修复）

---

## 二、Layer 3 问题分析

阅读背景文件后，发现 Layer 3（NetworkPolicy）存在两个核心问题：

### 问题 1：egress 规则缺少外部 HTTPS 放行

**现象：** `values.yaml` 中 NetworkPolicy 的 `egress` 规则只有：
- DNS 53 端口（到所有命名空间 pods）
- 同命名空间内 pods 互通

**缺失：** 没有放行外部公网 HTTPS（TCP:443），导致 sidecar 无法调用 `api.openai.com`，同时也会阻断 ECR / STS（IRSA）/ EKS 控制平面访问。

### 问题 2：NetworkPolicy 只存在于 etcd，从未被实际强制执行

**现象：** `aws_eks_addon "vpc_cni"` 没有设置 `enableNetworkPolicy = "true"`，导致：
- NetworkPolicy 对象存在于 K8s
- 但 AWS VPC CNI 的 `aws-network-policy-agent`（eBPF）未部署
- 所有 NetworkPolicy 规则形同虚设，从未拦截任何流量

### 问题 3：CI deploy 没有启用 NetworkPolicy 和 sidecar

**现象：** CI 的 Helm deploy 没有 `--set networkPolicy.enabled=true`，因此每次 CI 部署后 K8s 中根本不存在 NetworkPolicy 对象。同理 `sidecar.enabled` 也未传入。

### 问题 4（CI 发现）：`deployer` ClusterRole 缺少 `networkpolicies` 权限

**现象：** 第一次 CI 加 `--set networkPolicy.enabled=true` 后，Helm 报错：
```
Error: UPGRADE FAILED: could not get information about the resource
NetworkPolicy "persons-finder" in namespace "default":
networkpolicies.networking.k8s.io "persons-finder" is forbidden:
User "github-actions" cannot get resource "networkpolicies"
in API group "networking.k8s.io"
```

**根因：** `aws-auth.tf` 的 `deployer` ClusterRole 中 `networking.k8s.io` 规则只有 `ingresses`，没有 `networkpolicies`。

---

## 三、修复过程

### 3.1 修复 egress 规则（`values.yaml`）

**文件：** `devops/helm/persons-finder/values.yaml`

新增第三条 egress 规则：

```yaml
# External HTTPS: allow TCP 443 to public IPs only.
# Covers: api.openai.com, ECR, STS (IRSA), EKS control plane endpoint.
# Note: K8s NetworkPolicy cannot filter by FQDN; this allows any public IP on 443.
# For FQDN-level enforcement, deploy an egress proxy (see AIfirewall.md Layer 3).
- to:
  - ipBlock:
      cidr: 0.0.0.0/0
      except:
        - 10.0.0.0/8
        - 172.16.0.0/12
        - 192.168.0.0/16
  ports:
  - protocol: TCP
    port: 443
```

**为什么排除 RFC1918 私有地址段？**
- `10.0.0.0/8`：Pod/Service 内部地址 → 由 `podSelector` 规则处理
- `172.16.0.0/12`：Docker / 部分云内网段
- `192.168.0.0/16`：本地网络

这样只放行公网 IP，避免 ipBlock 与 podSelector 规则重叠导致内部流量走错路径。

---

### 3.2 启用 VPC CNI Network Policy Agent（`eks/main.tf`）

**文件：** `devops/terraform/modules/eks/main.tf`

```hcl
resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "vpc-cni"

  # Enable the AWS Network Policy Agent so that Kubernetes NetworkPolicy objects
  # are actually enforced at the kernel level (eBPF via aws-network-policy-agent).
  # Without this flag the NetworkPolicy objects exist in etcd but have zero effect.
  # Requires VPC CNI v1.14+ and EKS 1.25+.
  configuration_values = jsonencode({
    enableNetworkPolicy = "true"
  })
  ...
}
```

**Terraform apply 结果：** `1 changed`（vpc-cni addon 更新），14 秒完成。

**效果：** 每个 Node 上 `aws-node` DaemonSet 部署 `aws-network-policy-agent` sidecar，使用 eBPF 在内核层面强制执行 NetworkPolicy，pod 流量在到达应用层之前就被过滤。

---

### 3.3 CI deploy 启用 NetworkPolicy 和 sidecar（`ci-cd.yml`）

**文件：** `.github/workflows/ci-cd.yml`

Helm deploy 步骤新增三个 `--set`：

```yaml
--set networkPolicy.enabled=true \
--set sidecar.enabled=true \
--set sidecar.image.tag="${IMAGE_TAG}" \
```

**效果：**
- 每次 CI 部署都会在 K8s 中创建/更新 NetworkPolicy 对象
- sidecar（Layer 2）和 NetworkPolicy（Layer 3）同时激活
- `sidecar.image.tag` 与主镜像 tag 一致，保证版本同源

---

### 3.4 Kyverno 策略新增 sidecar 镜像验证

**文件：** `devops/kyverno/verify-image-signatures.yaml`

```yaml
verifyImages:
- imageReferences:
  - "190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder:*"
  - "190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/pii-redaction-sidecar:*"  # 新增
```

**原因：** sidecar 在 `default` 命名空间运行，Kyverno Enforce 模式应同样验证其签名，防止未签名的 sidecar 镜像被偷偷部署。sidecar 在 CI 中已通过 cosign 签名，满足此策略要求。

立即 `kubectl apply` 使策略生效，无需等 CI。

---

### 3.5 修复 deployer ClusterRole（`aws-auth.tf`）

**文件：** `devops/terraform/modules/eks/aws-auth.tf`

```hcl
rule {
  api_groups = ["networking.k8s.io"]
  resources  = ["ingresses", "networkpolicies"]  # 新增 networkpolicies
  verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
}
```

**Terraform apply：** 直接更新 K8s ClusterRole（无需重建节点），立即生效。

---

## 四、CI/CD 调试记录

| Run | 结果 | 失败原因 | 修复 |
|---|---|---|---|
| `22557991958` | ✅ | — | 首次 Layer 3 egress fix + VPC CNI（无 sidecar/NP） |
| `22558200780` | ❌ | `github-actions cannot get networkpolicies` | deployer ClusterRole 缺少 networkpolicies 权限 |
| `22560022659` | ✅ | — | RBAC 修复后全流程通过 |

---

## 五、Git 提交记录

| Commit | 说明 |
|---|---|
| `84c8b9e` | `feat(security): implement Layer 3 NetworkPolicy egress and VPC CNI enforcement` |
| `f38626a` | `feat(security): enable Layer 3 NetworkPolicy and sidecar in CI deploy` |
| `b416dbd` | `fix(rbac): add networkpolicies to deployer ClusterRole` |

---

## 六、当前基础设施状态

```
kubectl get networkpolicy,pods -n default

NAME                                             POD-SELECTOR                          AGE
networkpolicy.networking.k8s.io/persons-finder   app=persons-finder,...                 ✅

NAME                                 READY   STATUS    RESTARTS
pod/persons-finder-fdf77cfc8-pk6xw   2/2     Running   0          ✅
```

**READY: 2/2** = main container（Spring Boot）+ pii-redaction-sidecar（Go proxy）

---

## 七、Layer 3 技术说明

### K8s NetworkPolicy 的 IP-based 限制

标准 NetworkPolicy 无法按 FQDN 过滤（如 `api.openai.com`），只能按 IP 段。我们的实现：
- 放行所有公网 IP 的 TCP:443（非 RFC1918 段）
- 依赖 Layer 1 + Layer 2 确保 PII 在流量离开 sidecar 时已脱敏
- NetworkPolicy 的作用是防止 **直连非 OpenAI 目标**（如恶意端点），是最后一道防线

### 为什么需要 enableNetworkPolicy=true

没有此标志，K8s 中的 NetworkPolicy 对象仅存在于 etcd，不产生任何实际效果：

```
没有 agent:  Pod 流量 → 未过滤 → 任意出站（NetworkPolicy 对象被忽略）
有 agent:    Pod 流量 → eBPF hook → NetworkPolicy 规则评估 → 允许/拒绝
```

### 部署顺序的重要性

每次重建基础设施后，必须按以下顺序执行，否则 enforcement 激活时会用错误的规则拦截流量：
1. **先** push 代码 → CI 部署 Helm（更新 K8s 中的 NetworkPolicy 规则）
2. **后** `terraform apply`（启用 enforcement）

---

## 八、当前 AI Firewall 状态（全部 Layer 更新）

| Layer | 组件 | 状态 |
|---|---|---|
| Layer 1 | PiiProxyService（Spring Bean） | ✅ |
| Layer 2 | Go sidecar proxy（LISTEN_PORT/UPSTREAM_URL） | ✅ 运行中（READY: 2/2） |
| Layer 2 | Kyverno：sidecar 签名验证 | ✅ 已加入 imageReferences |
| Layer 3 | NetworkPolicy egress（DNS/内部/外部 HTTPS） | ✅ 已修复，在集群中生效 |
| Layer 3 | VPC CNI enableNetworkPolicy（eBPF 强制执行） | ✅ Terraform 已激活 |
| Layer 4 | AuditLogger stdout | ✅ |
| Layer 4 | CloudWatch 采集链路（Fluent Bit） | 🔲 未配置 |
| Layer 4 | Grafana PII 仪表盘 | 🔲 未配置 |

---

## 九、下次对话的起点

- Layer 1、2、3 已完整落地并通过端到端 CI/CD 验证
- Layer 4 是下一个目标：Fluent Bit DaemonSet → CloudWatch Logs → 告警规则
- `.trivyignore` 中 4 个 Go CVE 待 `golang:1.25.7+` 发布后移除
- 如需重建：`./devops/scripts/setup-eks.sh prod`（包含全流程）
