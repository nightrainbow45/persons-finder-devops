# Persons Finder – DevOps & SRE Challenge (AI-Augmented) / DevOps 与 SRE 挑战（AI 辅助）

Welcome to the **Persons Finder** DevOps challenge.
欢迎来到 **Persons Finder** DevOps 挑战项目。

**Scenario:**
**场景：**
The development team has finished the `persons-finder` API (a Java/Kotlin Spring Boot app that talks to an external LLM). It works on their machine. Now, **you** need to take it to production.
开发团队已完成 `persons-finder` API（一个调用外部 LLM 的 Java/Kotlin Spring Boot 应用），它在本地运行正常。现在，**你**需要将它推上生产环境。

**Our Philosophy:** We want engineers who use AI to move fast, but who have the wisdom to verify every line.
**我们的理念：** 我们希望工程师能借助 AI 快速推进，同时具备逐行验证的智慧。

---

## The Mission / 任务目标

Your task is to Containerize, Infrastructure-as-Code (IaC), and secure this application.
你的任务是对该应用进行容器化、基础设施即代码（IaC）化，并完成安全加固。

### 1. Containerization / 容器化

*   Create a `Dockerfile` for the application.
    为应用创建 `Dockerfile`。
*   **AI Challenge:** Ask an AI (ChatGPT/Claude) to write the Dockerfile.
    **AI 挑战：** 让 AI（ChatGPT/Claude）生成 Dockerfile。
*   **Audit:** The AI likely missed best practices (e.g., non-root user, multi-stage build, pinning versions). **Fix them.**
    **审查：** AI 可能遗漏了最佳实践（如非 root 用户、多阶段构建、版本固定），**请修复它们**。
*   *Output:* An optimized `Dockerfile`.
    *产出：* 经过优化的 `Dockerfile`。

### 2. Infrastructure as Code (Kubernetes/Terraform) / 基础设施即代码（Kubernetes/Terraform）

*   Deploy this app to a local cluster (Minikube/Kind) or output Terraform for AWS/GCP.
    将应用部署到本地集群（Minikube/Kind），或输出面向 AWS/GCP 的 Terraform 配置。
*   **Requirements:**
    **要求：**
    *   **Secrets:** The app needs an `OPENAI_API_KEY`. Do not bake it into the image. Show how you inject it securely (K8s Secrets, Vault, etc.).
        **密钥：** 应用需要 `OPENAI_API_KEY`，不得将其写入镜像，展示如何安全注入（K8s Secrets、Vault 等）。
    *   **Scaling:** Configure HPA (Horizontal Pod Autoscaler) based on CPU or custom metrics.
        **扩缩容：** 基于 CPU 或自定义指标配置 HPA（水平 Pod 自动扩缩器）。
*   **AI Task:** Use AI to generate the K8s manifests (Deployment, Service, Ingress). **Document what you had to fix.** (Did it forget `readinessProbe`? Did it request 400 CPUs?)
    **AI 任务：** 用 AI 生成 K8s 清单（Deployment、Service、Ingress），**记录你必须修复的内容**（它是否遗漏了 `readinessProbe`？是否申请了 400 个 CPU？）

### 3. The "AI Firewall" (Architecture) / "AI 防火墙"（架构）

The app sends user PII (names, bios) to an external LLM provider.
该应用会将用户 PII（姓名、简介）发送给外部 LLM 服务提供商。
*   **Design Challenge:** Create a short architectural diagram or description (`ARCHITECTURE.md`) showing how you would secure this egress traffic.
    **设计挑战：** 创建一份简短的架构图或说明（`ARCHITECTURE.md`），展示如何保护这条出站流量。
*   **Question:** How would you implement a "PII Redaction Sidecar" or Gateway logic to prevent real names from leaving our cluster? You don't have to build it, just design the infrastructure for it.
    **问题：** 你会如何实现"PII 脱敏 Sidecar"或网关逻辑，阻止真实姓名离开集群？不需要实现，只需设计基础设施方案。

### 4. CI/CD & AI Usage / CI/CD 与 AI 使用

*   Create a CI pipeline (GitHub Actions preferred).
    创建 CI 流水线（推荐使用 GitHub Actions）。
*   **The AI Twist:** We want to fail the build if the code "looks unsafe".
    **AI 特色：** 我们希望在代码"看起来不安全"时让构建失败。
    *   Add a step in the pipeline that runs a security scanner (Trivy/Snyk) OR a mocked "AI Code Reviewer" step.
        在流水线中添加一个运行安全扫描器（Trivy/Snyk）或模拟"AI 代码审查"的步骤。

---

## Live Deployment / 线上部署

| Item / 条目 | Value / 值 |
|---|---|
| **URL** | `[redacted]` |
| **Swagger UI** | `[redacted]/swagger-ui/index.html` |
| **OpenAPI spec / OpenAPI 规范** | `[redacted]/v3/api-docs` |
| **Cluster / 集群** | AWS EKS `persons-finder-prod`, `ap-southeast-2` |
| **Nodes / 节点** | 3 × t3.small ON_DEMAND — 1 system node + 2 app nodes (`persons-finder-nodes-prod`) |
| **TLS** | Let's Encrypt via cert-manager, auto-renewed<br>通过 cert-manager 使用 Let's Encrypt，自动续签 |
| **Image tag / 镜像标签** | `git-<sha>` (main builds) / `X.Y.Z` semver (releases) — ECR IMMUTABLE<br>`git-<sha>`（main 分支构建）/ `X.Y.Z` 语义化版本（发布）— ECR 不可变策略 |
| **Replicas / 副本数** | 1 (HPA min=1, max=3, CPU 70%) |

---

## Cluster Status / 集群状态

### Nodes (3 × t3.small ON_DEMAND — 2 vCPU / ~1.4 GB allocatable / max 11 pods each) / 节点（3 × t3.small 按需实例 — 2 vCPU / 约 1.4 GB 可分配 / 每节点最多 11 个 Pod）

| Node / 节点 | Node Group / 节点组 | AZ | Taint / 污点 | Pods / Pod 数 | CPU Used / CPU 使用 | Mem Used / 内存使用 |
|------|------------|----|-------|------|----------|----------|
| `ip-10-1-2-119` | `persons-finder-nodes-prod` | ap-southeast-2a | none | 8/11 | 400m (20%) | 292Mi (20%) |
| `ip-10-1-3-167` | `system-nodes-prod` | ap-southeast-2b | `system=true:NoSchedule` | 4/11 | 300m (15%) | 190Mi (13%) |
| `ip-10-1-3-25` | `persons-finder-nodes-prod` | ap-southeast-2b | none | 8/11 | 650m (33%) | 810Mi (56%) |

> `system-nodes-prod` carries a `NoSchedule` taint — only pods with a matching toleration are admitted, keeping system components isolated from application workloads.
> `system-nodes-prod` 带有 `NoSchedule` 污点——只有具备对应容忍的 Pod 才能调度到该节点，使系统组件与业务负载相互隔离。

### Namespaces (9) / 命名空间（9 个）

| Namespace / 命名空间 | Purpose / 用途 |
|-----------|---------|
| `kube-system` | Core K8s system components: VPC CNI, kube-proxy, CoreDNS, Fluent Bit<br>K8s 核心系统组件：VPC CNI、kube-proxy、CoreDNS、Fluent Bit |
| `kube-public` | Cluster-wide public info (read-only, mostly empty)<br>集群范围公共信息（只读，基本为空） |
| `kube-node-lease` | Node heartbeat leases used by the control plane to detect node failures<br>控制平面用于检测节点故障的心跳租约 |
| `default` | Default namespace — no business workloads deployed here<br>默认命名空间——未部署任何业务负载 |
| `cert-manager` | Automatic TLS certificate provisioning and renewal via Let's Encrypt<br>通过 Let's Encrypt 自动申请和续签 TLS 证书 |
| `external-secrets` | External Secrets Operator — syncs `OPENAI_API_KEY` from AWS Secrets Manager into K8s<br>External Secrets Operator——将 `OPENAI_API_KEY` 从 AWS Secrets Manager 同步到 K8s |
| `ingress-nginx` | NGINX Ingress controller — external HTTP/HTTPS entry point, TLS termination, Basic Auth<br>NGINX Ingress 控制器——外部 HTTP/HTTPS 入口、TLS 终止、Basic Auth |
| `kyverno` | Image signature policy engine (Enforce mode) — blocks pods with unsigned images<br>镜像签名策略引擎（强制模式）——阻止使用未签名镜像的 Pod |
| `persons-finder` | **Business application**: Spring Boot API + PII Redaction Sidecar<br>**业务应用**：Spring Boot API + PII 脱敏 Sidecar |

### Deployments (11 — all 1/1 Ready) / 部署（11 个——全部 1/1 就绪）

| Namespace / 命名空间 | Deployment / 部署 | Image / 镜像 | Purpose / 用途 |
|-----------|-----------|-------|---------|
| `cert-manager` | `cert-manager` | jetstack/cert-manager-controller:v1.13.0 | Certificate lifecycle controller<br>证书生命周期控制器 |
| `cert-manager` | `cert-manager-cainjector` | jetstack/cert-manager-cainjector:v1.13.0 | Injects CA certs into webhook configs<br>将 CA 证书注入 webhook 配置 |
| `cert-manager` | `cert-manager-webhook` | jetstack/cert-manager-webhook:v1.13.0 | Validates Certificate/Issuer resources<br>验证 Certificate/Issuer 资源 |
| `external-secrets` | `external-secrets` | external-secrets:v2.0.1 | Pulls secrets from AWS Secrets Manager<br>从 AWS Secrets Manager 拉取密钥 |
| `external-secrets` | `external-secrets-cert-controller` | external-secrets:v2.0.1 | Manages ESO's own webhook TLS cert<br>管理 ESO 自身的 webhook TLS 证书 |
| `external-secrets` | `external-secrets-webhook` | external-secrets:v2.0.1 | Validates ExternalSecret resource format<br>验证 ExternalSecret 资源格式 |
| `ingress-nginx` | `ingress-nginx-controller` | ingress-nginx/controller:v1.14.3 | NGINX reverse proxy and Ingress controller<br>NGINX 反向代理与 Ingress 控制器 |
| `kube-system` | `coredns` | eks/coredns:v1.11.4 | In-cluster DNS (`svc.cluster.local`)<br>集群内 DNS 解析 |
| `kyverno` | `kyverno-admission-controller` | kyverno/kyverno:v1.17.1 | Admission webhook — enforces image signing<br>准入 webhook——强制镜像签名验证 |
| `kyverno` | `kyverno-background-controller` | kyverno/background-controller:v1.17.1 | Async background compliance scanning<br>异步后台合规扫描 |
| `persons-finder` | `persons-finder` | ECR `persons-finder:git-0e2835b` + `pii-redaction-sidecar:git-0e2835b` | **Business app (2 containers: Spring Boot API + Go PII sidecar)**<br>**业务应用（2 个容器：Spring Boot API + Go PII Sidecar）** |

### Pods by Node / 按节点划分的 Pod

**Node 1 — `ip-10-1-2-119` (persons-finder-nodes-prod) — 8/11**

| Pod | Namespace / 命名空间 | Ready / 就绪 | Purpose / 用途 |
|-----|-----------|-------|---------|
| `aws-node-f8wmn` | kube-system | 2/2 | AWS VPC CNI — assigns VPC IPs to pods<br>为 Pod 分配 VPC IP |
| `kube-proxy-8xgpf` | kube-system | 1/1 | Maintains iptables rules for Service routing<br>维护 Service 路由的 iptables 规则 |
| `fluent-bit-ddfj5` | kube-system | 1/1 | Collects container stdout logs → CloudWatch<br>采集容器 stdout 日志发往 CloudWatch |
| `kyverno-admission-controller-*` | kyverno | 1/1 | Admission webhook — blocks unsigned images<br>准入 webhook——阻止未签名镜像 |
| `kyverno-background-controller-*` | kyverno | 1/1 | Background async policy compliance<br>后台异步策略合规扫描 |
| `cert-manager-*` | cert-manager | 1/1 | TLS certificate lifecycle (Let's Encrypt)<br>TLS 证书生命周期管理 |
| `cert-manager-cainjector-*` | cert-manager | 1/1 | CA injection into webhook configurations<br>CA 注入至 webhook 配置 |
| `cert-manager-webhook-*` | cert-manager | 1/1 | Certificate resource validation<br>证书资源验证 |

**Node 2 — `ip-10-1-3-167` (system-nodes-prod, tainted) — 4/11**

| Pod | Namespace / 命名空间 | Ready / 就绪 | Purpose / 用途 |
|-----|-----------|-------|---------|
| `aws-node-2frlf` | kube-system | 2/2 | AWS VPC CNI |
| `kube-proxy-9khl8` | kube-system | 1/1 | Service routing rules<br>Service 路由规则 |
| `fluent-bit-v9862` | kube-system | 1/1 | Log collection → CloudWatch<br>日志采集发往 CloudWatch |
| `ingress-nginx-controller-*` | ingress-nginx | 1/1 | External HTTPS entry point, TLS termination, Swagger Basic Auth<br>外部 HTTPS 入口、TLS 终止、Swagger Basic Auth |

**Node 3 — `ip-10-1-3-25` (persons-finder-nodes-prod) — 8/11**

| Pod | Namespace / 命名空间 | Ready / 就绪 | Purpose / 用途 |
|-----|-----------|-------|---------|
| `aws-node-2hcf6` | kube-system | 2/2 | AWS VPC CNI |
| `kube-proxy-hscph` | kube-system | 1/1 | Service routing rules<br>Service 路由规则 |
| `fluent-bit-swvvb` | kube-system | 1/1 | Log collection → CloudWatch (incl. PII audit logs)<br>日志采集发往 CloudWatch（含 PII 审计日志） |
| `coredns-*` | kube-system | 1/1 | In-cluster DNS resolution<br>集群内 DNS 解析 |
| `external-secrets-*` | external-secrets | 1/1 | Syncs `OPENAI_API_KEY` from AWS Secrets Manager<br>从 AWS Secrets Manager 同步 `OPENAI_API_KEY` |
| `external-secrets-cert-controller-*` | external-secrets | 1/1 | ESO internal TLS cert management<br>ESO 内部 TLS 证书管理 |
| `external-secrets-webhook-*` | external-secrets | 1/1 | ESO resource format validation<br>ESO 资源格式验证 |
| `persons-finder-*` | persons-finder | **2/2** | **① Spring Boot REST API  ② Go PII Redaction Sidecar (port 8081)**<br>**① Spring Boot REST API  ② Go PII 脱敏 Sidecar（端口 8081）** |

---

## Documentation / 文档

Full implementation details for each requirement are in the [`docs/`](docs/) directory.
每项需求的完整实现细节位于 [`docs/`](docs/) 目录。

| Document / 文档 | Covers / 内容 |
|---|---|
| [requirement-overview.md](docs/requirement-overview.md) | Single-page summary of all requirements, implementation status, test coverage<br>所有需求的单页摘要、实现状态、测试覆盖 |
| [ARCHITECTURE_DIAGRAM.md](docs/ARCHITECTURE_DIAGRAM.md) | System architecture diagrams: high-level AWS, PII layers, HPA, security, CI/CD, observability<br>系统架构图：AWS 全局视图、PII 分层、HPA、安全、CI/CD、可观测性 |
| [ApplicationDesignAndAPI.md](docs/ApplicationDesignAndAPI.md) | REST endpoints, 3-layer architecture, Haversine, OpenAPI/Swagger UI, CORS config, Swagger Basic Auth, 6 integration test classes<br>REST 端点、三层架构、Haversine 公式、OpenAPI/Swagger UI、CORS 配置、Swagger Basic Auth、6 个集成测试类 |
| [ContainerizationAndDockerfile.md](docs/ContainerizationAndDockerfile.md) | AI-generated Dockerfile, identified flaws, applied fixes<br>AI 生成的 Dockerfile、已识别缺陷及修复内容 |
| [InfrastructureAsCode.md](docs/InfrastructureAsCode.md) | Terraform (AWS), Helm chart, ESO secret injection, HPA, AI manifest fixes<br>Terraform（AWS）、Helm Chart、ESO 密钥注入、HPA、AI 清单修复 |
| [SecurityAndSecrets.md](docs/SecurityAndSecrets.md) | Secrets management, RBAC, NetworkPolicy, pod security context, TLS/cert-manager, Ingress Basic Auth, cosign, Kyverno<br>密钥管理、RBAC、NetworkPolicy、Pod 安全上下文、TLS/cert-manager、Ingress Basic Auth、cosign、Kyverno |
| [AIfirewall.md](docs/AIfirewall.md) | AI Firewall 4-layer PII egress protection: threat model, design rationale, end-to-end lifecycle<br>AI 防火墙四层 PII 出站保护：威胁模型、设计原理、端到端生命周期 |
| [CICDandAIUsage.md](docs/CICDandAIUsage.md) | GitHub Actions pipeline, Trivy scan gate, SBOM, image signing, ECR tag strategy (git-sha + semver, IMMUTABLE), periodic re-scan<br>GitHub Actions 流水线、Trivy 扫描卡点、SBOM、镜像签名、ECR 标签策略（git-sha + semver，IMMUTABLE）、定期重扫 |
| [ObservabilityAndMonitoring.md](docs/ObservabilityAndMonitoring.md) | Structured logging, Fluent Bit, CloudWatch metrics & alarm, Actuator health probes<br>结构化日志、Fluent Bit、CloudWatch 指标与告警、Actuator 健康探针 |
| [AILLMIntegration.md](docs/AILLMIntegration.md) | PII proxy pipeline (Kotlin + Go sidecar), reversible tokenization, audit log<br>PII 代理管道（Kotlin + Go Sidecar）、可逆令牌化、审计日志 |
| [devops-sre-architecture.md](docs/devops-sre-architecture.md) | Complete DevOps/SRE architecture diagrams: overall AWS topology, CI/CD, PII 4-layer, HPA, logging, security, Terraform, Helm, data flow, DR/HA, cost, SLI/SLO, tech stack<br>完整 DevOps/SRE 架构图：AWS 拓扑全览、CI/CD、PII 四层、HPA、日志、安全、Terraform、Helm、数据流、DR/HA、成本、SLI/SLO、技术栈 |

Operational guides are in [`devops/docs/`](devops/docs/):
运维指南位于 [`devops/docs/`](devops/docs/)：

| Document / 文档 | Covers / 内容 |
|---|---|
| [QUICKSTART.md](devops/docs/QUICKSTART.md) | Fast-path: local Kind or EKS deployment<br>快速通道：本地 Kind 或 EKS 部署 |
| [DEPLOYMENT.md](devops/docs/DEPLOYMENT.md) | Full deployment lifecycle, Helm values, rollback<br>完整部署生命周期、Helm 参数、回滚 |
| [RELEASE_PROCESS.md](devops/docs/RELEASE_PROCESS.md) | Semantic versioning, ECR tag strategy, release steps<br>语义化版本、ECR 标签策略、发布步骤 |
| [DNS_CONFIGURATION.md](devops/docs/DNS_CONFIGURATION.md) | Custom domain → NGINX Ingress LoadBalancer (CNAME/ALIAS)<br>自定义域名指向 NGINX Ingress LoadBalancer（CNAME/ALIAS） |
| [SWAGGER_TROUBLESHOOTING.md](devops/docs/SWAGGER_TROUBLESHOOTING.md) | Swagger UI access, Basic Auth, CORS, disable in prod<br>Swagger UI 访问、Basic Auth、CORS、生产环境禁用 |
| [SECURITY_REVIEW.md](devops/docs/SECURITY_REVIEW.md) | Security controls checklist<br>安全控制检查清单 |
| [HELM_DEPLOYMENT.md](devops/docs/HELM_DEPLOYMENT.md) | Helm chart deep-dive: templates, values, environment overrides<br>Helm Chart 深度解析：模板、参数、环境覆盖 |
| [NETWORK_SECURITY_VERIFICATION.md](devops/docs/NETWORK_SECURITY_VERIFICATION.md) | NetworkPolicy and VPC CNI enforcement verification<br>NetworkPolicy 与 VPC CNI 强制执行验证 |

---

## The AI Log / AI 日志

[`AI_LOG.md`](AI_LOG.md) documents AI collaboration across all sessions: what was generated, what was wrong, and what was fixed. The pre-commit checklist covers Container, Kubernetes, NetworkPolicy, CI/CD, Swagger/OpenAPI, Terraform, Kyverno, and Secrets & Supply Chain.
[`AI_LOG.md`](AI_LOG.md) 记录了所有会话中的 AI 协作过程：生成了什么、哪里出错、如何修复。提交前检查清单涵盖容器、Kubernetes、NetworkPolicy、CI/CD、Swagger/OpenAPI、Terraform、Kyverno 以及密钥与供应链安全。

---

## Getting Started / 快速开始

```bash
# Build and test locally / 本地构建与测试
./gradlew clean build

# Local K8s deployment (Kind)
# See devops/docs/QUICKSTART.md
# 本地 K8s 部署（Kind）
# 参见 devops/docs/QUICKSTART.md

# Deploy to EKS
# See devops/docs/DEPLOYMENT.md
# 部署到 EKS
# 参见 devops/docs/DEPLOYMENT.md
```

## Submission / 提交说明

Submit your repository link. We care about:
提交你的代码仓库链接，我们关注以下几点：
*   **Security:** How you handle the API Key.
    **安全性：** 你如何处理 API Key。
*   **Reliability:** Probes, Limits, Scaling.
    **可靠性：** 健康探针、资源限制、扩缩容。
*   **AI Maturity:** Your AI logs — did you blindly trust the bot, or did you engineer it?
    **AI 成熟度：** 你的 AI 日志——你是盲目信任 AI，还是经过工程化验证？
