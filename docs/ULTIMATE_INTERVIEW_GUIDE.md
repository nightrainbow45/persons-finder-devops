# Ultimate Interview Guide — Persons Finder DevOps/SRE Project / 终极面试指南 — Persons Finder DevOps/SRE 项目

> Bilingual, interviewer-facing narrative for technical + behavioral rounds.  
> 面向技术面与行为面的中英双语面试讲述总稿。

---

## 1) How to Use This Guide / 使用方式

- EN: Use this as your primary script in interviews, then pull evidence from the referenced docs and files.
- 中文：将本指南作为面试主讲稿，再从引用文档和代码文件中提取证据。
- EN: Pick one version based on time: 30s intro, 2-min overview, or 5-min deep dive.
- 中文：按面试时长选择版本：30 秒开场、2 分钟概览、5 分钟深讲。
- EN: For each answer, include one decision, one trade-off, and one evidence point.
- 中文：每个回答都包含一个决策、一个取舍、一个证据点。

---

## 2) Project One-Liner / 项目一句话

- EN: I led production DevOps hardening for a Kotlin/Spring Boot location API on AWS EKS, turning AI-generated drafts into a secure, reproducible, policy-enforced delivery system.
- 中文：我主导了一个 Kotlin/Spring Boot 地理位置 API 在 AWS EKS 上的生产级 DevOps 加固，把 AI 初稿工程化为“安全、可复现、策略可强制”的交付系统。

---

## 3) 30-Second Intro / 30 秒开场

**EN**
I delivered end-to-end DevOps/SRE implementation for Persons Finder on EKS. The core value was not just deployment speed, but deployment reliability under real constraints: immutable image tags, signed-image admission, least-privilege IAM/RBAC, and fresh-cluster rebuild validation. I used AI to accelerate drafts, then applied strict engineering gates to make the system production-defensible.

**中文**
我负责了 Persons Finder 在 EKS 上的端到端 DevOps/SRE 落地。核心价值不只是“部署得快”，而是“在真实约束下稳定可发布”：例如不可变镜像标签、签名镜像准入、最小权限 IAM/RBAC、全新集群重建验证。我用 AI 提速初稿，但用严格工程门禁确保系统达到生产可辩护水平。

---

## 4) 2-Minute Narrative / 2 分钟项目讲述

### Situation / 背景
- EN: We needed a production-ready API platform with security, observability, and repeatable delivery.
- 中文：我们需要一个具备安全、可观测、可重复交付能力的生产级 API 平台。
- EN: AI accelerated implementation but introduced hidden risks in configs and assumptions.
- 中文：AI 加速了实现，但在配置和假设层面引入了隐性风险。

### What I Built / 我做了什么
- EN: Platform: Terraform-provisioned AWS stack (VPC/EKS/IAM/ECR) + Helm-based Kubernetes delivery.
- 中文：平台层：Terraform 编排 AWS 资源（VPC/EKS/IAM/ECR）+ Helm 驱动 Kubernetes 交付。
- EN: Security chain: Trivy gate, SBOM, cosign signing, Kyverno runtime admission verification.
- 中文：安全链路：Trivy 漏洞门禁、SBOM、cosign 签名、Kyverno 运行时准入校验。
- EN: AI Firewall: 4-layer PII protection (in-process redaction, sidecar proxy, NetworkPolicy, observability/alerting).
- 中文：AI 防火墙：四层 PII 保护（进程内脱敏、sidecar 代理、NetworkPolicy、可观测告警）。
- EN: Operations: setup/deploy/verify/teardown scripts + runbook-driven documentation.
- 中文：运维层：setup/deploy/verify/teardown 脚本 + runbook 化文档体系。

### Why It Matters / 价值
- EN: We converted “works once” into “works reliably under policy and failure conditions.”
- 中文：我们把“能跑一次”升级为“在策略约束和故障场景下仍可稳定运行”。

---

## 5) 5-Minute Deep Dive / 5 分钟深讲版本

### A. Architecture / 架构
- EN: App: Kotlin + Spring Boot REST API.
- 中文：应用：Kotlin + Spring Boot REST API。
- EN: Runtime: EKS workloads deployed by Helm.
- 中文：运行层：EKS 集群中通过 Helm 部署工作负载。
- EN: Data/Security path: requests pass business logic + PII controls before external LLM egress.
- 中文：数据与安全路径：请求经过业务逻辑与 PII 控制后再出站访问外部 LLM。

### B. High-Impact Engineering Decisions / 高影响工程决策
1. EN: Immutable image strategy (`git-sha`/exact semver) aligned with ECR IMMUTABLE policy.
1. 中文：采用不可变镜像策略（`git-sha`/精确 semver）并与 ECR IMMUTABLE 对齐。
2. EN: OIDC federation + least-privilege IAM/RBAC instead of long-lived credentials.
2. 中文：使用 OIDC 联邦 + 最小权限 IAM/RBAC，替代长期静态凭据。
3. EN: Signed-image enforcement moved from recommendation to runtime admission control.
3. 中文：签名镜像从“建议项”升级为“运行时准入强制项”。
4. EN: Mandatory fresh-state (`destroy + apply`) validation to expose hidden lifecycle defects.
4. 中文：强制全新状态（`destroy + apply`）验证，暴露隐藏生命周期缺陷。

### C. Hard Problems I Solved / 我解决的难题
1. EN: Floating tag strategy conflicted with ECR IMMUTABLE.
1. 中文：浮动标签策略与 ECR IMMUTABLE 冲突。
2. EN: Missing RBAC bindings were hidden in incremental environments, exposed in fresh rebuilds.
2. 中文：RBAC 绑定缺口在增量环境被掩盖，在全新重建中暴露。
3. EN: Kyverno admission timeout sensitivity (10s webhook budget) required proper IRSA and latency discipline.
3. 中文：Kyverno 准入超时窗口敏感（10 秒 webhook 预算），必须正确配置 IRSA 与延迟控制。
4. EN: Sidecar proxy wiring initially reversed traffic direction; fixed to strict outbound path.
4. 中文：sidecar 代理早期方向接反，修复为严格出站路径。

### D. Results / 结果
- EN: Delivery became reproducible, audit-friendly, and safer under real production constraints.
- 中文：交付过程在真实生产约束下变得可复现、可审计且更安全。
- EN: Interview-ready value: I can explain not just tools, but decision quality under pressure.
- 中文：面试价值：我不仅能讲工具，更能讲高压场景下的决策质量。

---

## 6) STAR Story Bank (Top 6) / STAR 案例库（6 个高频）

### STAR 1: AI Draft to Production Quality / 从 AI 初稿到生产质量
- Situation / 背景:
EN: AI output looked fast but had policy and lifecycle blind spots.  
中文：AI 产出速度快，但存在策略与生命周期盲区。
- Task / 目标:
EN: Convert speed into safe, auditable delivery.  
中文：把速度转化为安全、可审计交付。
- Action / 动作:
EN: Added enforceable gates, rebuilt checklist, and verified runtime behavior.  
中文：补齐强制门禁、重构检查清单，并验证运行态行为。
- Result / 结果:
EN: Reduced hidden failures and standardized release quality.  
中文：降低隐性故障并标准化发布质量。

### STAR 2: ECR IMMUTABLE vs Floating Tags / ECR IMMUTABLE 与浮动标签冲突
- Situation:
EN: Existing release docs/pipeline allowed floating tags.  
中文：原发布文档/流水线允许浮动标签。
- Task:
EN: Align release strategy with immutable registry behavior.  
中文：让发布策略与仓库不可变策略一致。
- Action:
EN: Switched to immutable tags and synchronized docs + workflow logic.  
中文：改为不可变标签并同步文档与流水线逻辑。
- Result:
EN: Eliminated tag collision failures and improved deployment determinism.  
中文：消除标签冲突导致的失败，提升部署确定性。

### STAR 3: RBAC Latent Gap / RBAC 潜在缺口
- Situation:
EN: Incremental deploys passed; fresh rebuild failed with permission errors.  
中文：增量部署正常，但全新重建出现权限错误。
- Task:
EN: Find hidden identity/permission mismatch.  
中文：定位隐蔽的身份与权限不一致。
- Action:
EN: Paired aws-auth mapping with explicit ClusterRole/Binding and required verbs.  
中文：将 aws-auth 映射与明确的 ClusterRole/Binding、必要权限动词配对。
- Result:
EN: New and existing environments behaved consistently.  
中文：新旧环境行为统一，发布更稳定。

### STAR 4: Kyverno Admission Reliability / Kyverno 准入可靠性
- Situation:
EN: Admission reliability degraded under strict verification path.  
中文：严格校验路径下准入可靠性下降。
- Task:
EN: Keep signature enforcement without blocking valid deploys.  
中文：在保留签名强制的同时避免误阻断有效发布。
- Action:
EN: Tuned identity/auth path and validation flow, then verified admission behavior under load.  
中文：优化身份认证路径与校验流程，并在负载下验证准入行为。
- Result:
EN: Enforcement stayed active while delivery remained operational.  
中文：在保持强制策略生效的同时保证发布可用。

### STAR 5: Sidecar Egress Direction Fix / Sidecar 出站方向修复
- Situation:
EN: Sidecar existed but traffic path was not enforcing intended outbound redaction.  
中文：sidecar 已部署，但流量路径未真正强制出站脱敏。
- Task:
EN: Ensure app-to-sidecar-to-upstream path is mandatory.  
中文：确保“应用->sidecar->上游”成为必经路径。
- Action:
EN: Rewired proxy env model and main-container integration points.  
中文：重构代理环境变量模型并修复主容器接入点。
- Result:
EN: Layer-2 protection became effective and observable.  
中文：第二层防护真正生效且可观测。

### STAR 6: NetworkPolicy From YAML to Enforcement / NetworkPolicy 从“声明”到“执行”
- Situation:
EN: Policy objects existed, but effective enforcement could drift by environment setup.
中文：策略对象存在，但执行效果会受环境配置影响而漂移。
- Task:
EN: Prove actual deny/allow behavior, not just manifest presence.
中文：证明实际放行/拒绝行为，而不只是清单存在。
- Action:
EN: Added verification workflow with command-based pass/fail checks.
中文：建立命令化验证流程，明确通过/失败标准。
- Result:
EN: Security controls became testable and repeatable.
中文：安全控制变得可测试、可重复。

---

## 7) Evidence Kit You Can Quote / 可直接引用的证据包

### Core Artifacts / 核心制品
- EN: Terraform + Helm + CI pipeline + admission policy + observability stack.
- 中文：Terraform + Helm + CI 流水线 + 准入策略 + 可观测栈。
- EN: Paths: `devops/terraform/`, `devops/helm/`, `devops/ci/ci-cd.yml`, `devops/scripts/`, `devops/docs/`.
- 中文：路径：`devops/terraform/`、`devops/helm/`、`devops/ci/ci-cd.yml`、`devops/scripts/`、`devops/docs/`。

### Talking Numbers / 可讲数字
- EN: 4-layer AI Firewall model for PII egress protection.
- 中文：PII 出站保护采用 4 层 AI 防火墙模型。
- EN: Admission timeout budget discussed explicitly in reliability tuning.
- 中文：在准入可靠性调优中明确考虑了 webhook 超时预算。
- EN: Full rebuild (`destroy + apply`) used as a quality gate for hidden defects.
- 中文：将全量重建（`destroy + apply`）作为隐藏缺陷质量门禁。

### What to Say If Asked “How do you prove it?” / 被问“你怎么证明？”时的答法
- EN: “I show runtime evidence: deployment behavior, policy outcomes, and verification commands, not only document claims.”
- 中文：“我提供运行态证据：部署行为、策略结果和验证命令，而不只靠文档陈述。” 

---

## 8) High-Frequency Interview Q&A / 面试高频追问

### Q1
**EN Question:** What is the most important DevOps decision you made?  
**EN Answer:** Making policy-enforced reliability non-negotiable: immutable artifacts, signed-image admission, and fresh-state validation.

**中文问题：** 你做过最关键的 DevOps 决策是什么？  
**中文回答：** 把“策略强制下的可靠性”设为不可妥协项：不可变制品、签名镜像准入、全新状态验证。

### Q2
**EN Question:** How did you use AI without lowering quality?  
**EN Answer:** AI produced drafts; production acceptance required explicit gates, runtime checks, and evidence.

**中文问题：** 你如何在用 AI 的同时不降低质量？  
**中文回答：** AI 负责初稿，生产验收必须通过显式门禁、运行检查和证据验证。

### Q3
**EN Question:** What failure did you catch only after deeper validation?  
**EN Answer:** Permission and lifecycle defects that only surfaced during fresh-cluster rebuild, not incremental updates.

**中文问题：** 哪类问题是深度验证后才发现的？  
**中文回答：** 只有在全新集群重建时才暴露的权限与生命周期缺陷，增量更新不一定能发现。

### Q4
**EN Question:** How do you balance speed, cost, and security?  
**EN Answer:** Speed from automation, security from enforced controls, and cost from topology/operation discipline.

**中文问题：** 你如何平衡速度、成本和安全？  
**中文回答：** 用自动化拿速度，用强制策略保安全，用拓扑和运维纪律控成本。

### Q5
**EN Question:** What is your strongest SRE mindset in this project?  
**EN Answer:** Validate behavior in failure modes, not only in happy paths.

**中文问题：** 在这个项目里你最强的 SRE 思维是什么？  
**中文回答：** 不只验证成功路径，更要验证故障模式下的行为。

### Q6
**EN Question:** You said this project has a 4-layer security model. What are the four layers exactly?  
**EN Answer:**  
Layer 1 is in-process PII redaction inside the Kotlin application before outbound LLM calls.  
Layer 2 is the Go sidecar forward proxy that enforces a second independent redaction path.  
Layer 3 is Kubernetes NetworkPolicy egress control (only required outbound paths are allowed).  
Layer 4 is observability and alerting (audit logs + monitoring) to detect bypass attempts and abnormal behavior.

**中文问题：** 你提到做了四层安全，具体是哪四层？  
**中文回答：**  
第 1 层是在 Kotlin 应用进程内做 PII 脱敏，再发起 LLM 出站请求。  
第 2 层是 Go sidecar 正向代理，提供独立的二次脱敏与转发控制。  
第 3 层是 Kubernetes NetworkPolicy 出站约束，只允许必需的对外路径。  
第 4 层是可观测与告警（审计日志 + 监控），用于发现绕过与异常行为。

### Q7
**EN Question:** How do you manage sensitive secrets, and how are they injected into EKS at runtime?  
**EN Answer:**  
We keep sensitive values (for example `OPENAI_API_KEY`) in AWS Secrets Manager as the source of truth.  
External Secrets Operator (ESO) reads from Secrets Manager via `ClusterSecretStore` + `ExternalSecret` and syncs into a Kubernetes Secret (for example `persons-finder-secrets`).  
The Deployment injects that Secret into the app container with `envFrom.secretRef`.  
So secrets are not baked into images or hardcoded in Helm values; rotation happens at the source and is synced to the cluster.

**中文问题：** 敏感秘钥是怎么管理的？要用的时候怎么注入到 EKS Secret？  
**中文回答：**  
我们把敏感值（如 `OPENAI_API_KEY`）统一放在 AWS Secrets Manager 作为真源。  
然后由 External Secrets Operator（`ClusterSecretStore` + `ExternalSecret`）从 Secrets Manager 拉取并同步成 Kubernetes Secret（例如 `persons-finder-secrets`）。  
Deployment 再通过 `envFrom.secretRef` 把该 Secret 注入主容器环境变量。  
这样机密不会写进镜像或硬编码到 Helm values，轮换时只改真源并同步到集群。

---

## 9) Behavioral Round Mapping / 行为面映射

### “Tell me about a challenge” / “说一个困难案例”
- EN: Use STAR 3 (RBAC latent gap) or STAR 4 (admission reliability).
- 中文：优先用 STAR 3（RBAC 潜在缺口）或 STAR 4（准入可靠性）。

### “How do you handle disagreement?” / “你如何处理分歧？”
- EN: Frame it as policy vs speed trade-off; align on risk ownership and measurable gates.
- 中文：把分歧框定为“速度与风险”取舍，再对齐风险归属与可量化门禁。

### “How do you prioritize?” / “你如何排优先级？”
- EN: Prioritize by blast radius, release blocking risk, and reversibility.
- 中文：按影响半径、阻断发布风险、可逆性三维度排优先级。

---

## 10) Questions to Ask the Interviewer / 反问面试官

1. EN: How does your team validate infrastructure changes beyond happy-path deploys?  
   中文：贵团队如何验证基础设施变更，不仅是成功路径部署？
2. EN: Which controls are advisory vs release-blocking in your CI/CD?  
   中文：你们 CI/CD 里哪些控制是建议项，哪些是阻断发布项？
3. EN: How do you measure operational quality after go-live?  
   中文：上线后你们如何量化运维质量？
4. EN: What is the current biggest gap in platform reliability or security?  
   中文：当前平台在可靠性或安全上最大的缺口是什么？

---

## 11) Role-Tailored Variants / 岗位定制版本

### For DevOps Engineer / DevOps 工程师岗位
- EN: Emphasize release automation, IaC consistency, and deployment guardrails.
- 中文：重点讲自动化发布、IaC 一致性、部署护栏。

### For SRE / SRE 岗位
- EN: Emphasize failure-mode validation, observability evidence, and operational feedback loops.
- 中文：重点讲故障模式验证、可观测证据、运维反馈闭环。

### For Platform Engineer / 平台工程师岗位
- EN: Emphasize multi-team standards, reusable templates, and policy-as-platform.
- 中文：重点讲多团队标准化、可复用模板、平台化策略治理。

### For DevSecOps / DevSecOps 岗位
- EN: Emphasize signed supply chain, runtime admission enforcement, and least-privilege boundaries.
- 中文：重点讲签名供应链、运行时准入强制、最小权限边界。

---

## 12) Final Closing Script / 收尾话术

**EN**
If I summarize this project in one sentence: I turned fast AI-assisted delivery into a production-defensible system by enforcing policy, validating runtime behavior, and documenting decisions so the team can scale safely.

**中文**
如果用一句话总结这个项目：我把“AI 加速交付”升级成“生产可辩护系统”，关键是策略强制、运行验证和决策文档化，从而让团队可以安全扩展。

---

## 13) Next-Step Practice Plan (Optional) / 练习计划（可选）

1. EN: Record your 30s and 2-min versions and remove vague statements.
1. 中文：录制 30 秒和 2 分钟版本，删除空泛表达。
2. EN: Rehearse 3 STAR stories with “decision + trade-off + evidence” format.
2. 中文：用“决策 + 取舍 + 证据”格式演练 3 个 STAR。
3. EN: Do a mock panel round: architecture + security + incident questions.
3. 中文：做一次小组模拟面：架构、安全、故障三类问题联练。
