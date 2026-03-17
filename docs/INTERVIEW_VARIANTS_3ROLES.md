# Interview Variants (3 Roles) / 面试定制稿（三版）

> Role-specific bilingual scripts for the Persons Finder project.  
> 面向 Persons Finder 项目的岗位定制中英双语话术。

---

## Version A: SRE Role / A 版：SRE 岗

### 30-Second Pitch / 30 秒开场

**EN**  
I focused on reliability under real production constraints: policy-enforced releases, failure-mode validation, and observable recovery paths. In this project, I turned AI-assisted delivery into an SRE-grade system by validating behavior in fresh-cluster rebuilds, tightening runtime controls, and improving incident diagnosability.

**中文**  
我重点做的是“真实生产约束下的可靠性”：策略强制发布、故障模式验证、可观测恢复路径。这个项目里，我把 AI 辅助交付升级为 SRE 级系统：通过全新集群重建验证行为、收紧运行时控制、提升故障可诊断性。

### 2-Minute Narrative / 2 分钟讲述

- EN: I built end-to-end reliability loops: deploy -> verify -> observe -> recover.  
- 中文：我建立了端到端可靠性闭环：部署 -> 验证 -> 观测 -> 恢复。  
- EN: Key SRE decisions included immutable artifact strategy, signed-image admission, and fresh-state validation (`destroy + apply`).  
- 中文：关键 SRE 决策包括不可变制品策略、签名镜像准入、全新状态验证（`destroy + apply`）。  
- EN: I also improved operational feedback with structured logs, health endpoints, and CloudWatch-oriented verification flow.  
- 中文：我还通过结构化日志、健康探针和面向 CloudWatch 的验证流程增强了运维反馈能力。  
- EN: Result: fewer hidden failures and higher confidence in rollout + rollback under pressure.  
- 中文：结果是隐性故障更少，在高压场景下发布与回滚更有把握。

### SRE High-Frequency Q&A / SRE 高频追问

**Q (EN): What is your strongest SRE habit?**  
**A (EN):** I never trust happy-path deploys alone; I always validate failure modes and recovery behavior.

**问（中文）：你最关键的 SRE 习惯是什么？**  
**答（中文）：** 我从不只看成功路径部署，必须验证故障模式和恢复行为。

---

## Version B: Platform Engineer Role / B 版：Platform 岗

### 30-Second Pitch / 30 秒开场

**EN**  
I treated this project as a platform design problem, not just an app deployment task. I standardized Terraform, Helm, CI/CD, and runbooks into reusable contracts so multiple teams can deliver consistently with policy and operational guardrails.

**中文**  
我把这个项目当成“平台设计问题”，而不是单纯应用部署任务。我把 Terraform、Helm、CI/CD、Runbook 标准化为可复用契约，让多团队在策略和运维护栏下稳定交付。

### 2-Minute Narrative / 2 分钟讲述

- EN: I built a platform-style delivery model: clear source-of-truth files, script-driven operations, and values-driven Helm promotion.  
- 中文：我搭建了平台化交付模型：明确真源文件、脚本驱动运维、values 驱动 Helm 晋级。  
- EN: I reduced drift by enforcing consistent artifact identity, configuration boundaries, and runtime policy checks.  
- 中文：通过统一制品身份、配置边界和运行策略检查，我显著降低了环境漂移。  
- EN: Documentation was rewritten as operational interfaces, not passive wiki pages.  
- 中文：文档被重写为“运维接口”，而不是被动 Wiki。  
- EN: Result: better onboarding speed, clearer ownership, and higher release consistency.  
- 中文：结果是上手更快、责任更清晰、发布一致性更高。

### Platform High-Frequency Q&A / Platform 高频追问

**Q (EN): How do you scale this beyond one team?**  
**A (EN):** By codifying standards into templates, scripts, and policy gates so delivery behavior is repeatable.

**问（中文）：这套东西如何扩展到多团队？**  
**答（中文）：** 把标准固化进模板、脚本和策略门禁，让交付行为天然可重复。

---

## Version C: DevSecOps Role / C 版：DevSecOps 岗

### 30-Second Pitch / 30 秒开场

**EN**  
My focus was secure-by-default delivery. I connected vulnerability scanning, SBOM, image signing, and Kyverno admission into an enforceable chain, then validated that controls worked in real runtime and rebuild scenarios, not just in static config.

**中文**  
我的重点是“默认安全交付”。我把漏洞扫描、SBOM、镜像签名和 Kyverno 准入串成可强制闭环，并验证这些控制在真实运行态和重建场景下都有效，而不只是静态配置看起来正确。

### 2-Minute Narrative / 2 分钟讲述

- EN: I moved security from recommendation to enforcement: Trivy blocking gates, cosign signatures, runtime admission checks.  
- 中文：我把安全从“建议项”升级为“强制项”：Trivy 阻断门禁、cosign 签名、运行时准入校验。  
- EN: I addressed identity and access boundaries with OIDC federation and least-privilege IAM/RBAC.  
- 中文：我用 OIDC 联邦和最小权限 IAM/RBAC 修复身份与访问边界。  
- EN: I verified network and secret controls through executable checks and operational evidence.  
- 中文：我通过可执行检查和运行证据验证网络与机密控制。  
- EN: Result: lower supply-chain risk, clearer audit traceability, and safer production promotion.  
- 中文：结果是供应链风险更低、审计追溯更清晰、生产晋级更安全。

### DevSecOps High-Frequency Q&A / DevSecOps 高频追问

**Q (EN): How do you avoid “security theater”?**  
**A (EN):** I require enforcement plus runtime evidence; if it cannot block risk or be verified in production, it is not done.

**问（中文）：你如何避免“安全表演化”？**  
**答（中文）：** 我要求“可强制 + 可验证”双条件；不能在生产阻断风险或无法验证的控制，不算完成。

---

## Fast Selection Guide / 快速选用建议

1. EN: If interviewer probes incident response and reliability, start with Version A (SRE).  
1. 中文：如果面试官重点问故障响应和可靠性，优先用 A 版（SRE）。  
2. EN: If interviewer probes standards and reuse, start with Version B (Platform).  
2. 中文：如果面试官重点问标准化和复用，优先用 B 版（Platform）。  
3. EN: If interviewer probes security chain and compliance, start with Version C (DevSecOps).  
3. 中文：如果面试官重点问安全链路和合规，优先用 C 版（DevSecOps）。
