# 8. CI/CD & AI Usage / 8. CI/CD 与 AI 使用

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Describe how AI-assisted delivery is controlled by deterministic security and release gates.
- 中文：说明 AI 辅助交付如何被确定性的安全与发布门禁约束。

## Executive Summary / 执行摘要

- EN: GitHub Actions pipeline covers build, scan, sign, attest, and deploy stages.
- 中文：GitHub Actions 覆盖构建、扫描、签名、证明、部署全链路。
- EN: Trivy acts as a blocking security gate for high-severity findings.
- 中文：Trivy 对高危漏洞执行阻断门禁。
- EN: SBOM and cosign provide supply-chain traceability and integrity signals.
- 中文：SBOM 与 cosign 提供供应链可追溯与完整性信号。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 8. CI/CD & AI Usage workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“8. CI/CD 与 AI 使用”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Pin tool versions to avoid behavior drift from floating actions.
1. 中文：固定工具版本，避免浮动 action 引发行为漂移。
2. EN: Use OIDC federation instead of static long-lived cloud credentials.
2. 中文：采用 OIDC 联邦而非长期静态云凭据。
3. EN: Treat AI output as draft; release only after policy checks pass.
3. 中文：AI 输出视为草稿，策略门禁通过后才允许发布。
4. EN: Schedule periodic re-scan to detect newly disclosed CVEs.
4. 中文：定期重扫以捕获新披露 CVE。

## Evidence & Metrics / 证据与指标

- EN: Gate quality: vulnerability and signature checks are enforceable, not advisory.
- 中文：门禁质量：漏洞与签名检查是强制而非建议。
- EN: Artifact traceability: SBOM retained for audit timeline.
- 中文：制品追溯：SBOM 保留满足审计时间线。
- EN: Credential security: short-lived OIDC token path replaces static secrets.
- 中文：凭据安全：短时 OIDC 令牌替代静态密钥。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
How do you prevent AI from introducing unsafe pipeline logic?

**Answer:**
By enforcing deterministic gates and failing fast on policy violations.

### 问题 1（中文）
你如何防止 AI 把不安全逻辑引入流水线？

**回答：**
通过确定性门禁与策略违规快速失败机制，阻断不安全变更。

### Q2 (EN)
What is your CI/CD maturity signal in this project?

**Answer:**
The pipeline validates code, image, identity, and runtime admission before production.

### 问题 2（中文）
这个项目中你认为最能体现 CI/CD 成熟度的信号是什么？

**回答：**
流水线在上线前同时校验代码、镜像、身份与运行时准入。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
