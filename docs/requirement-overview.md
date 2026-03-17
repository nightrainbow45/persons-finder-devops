# 1. Requirement Overview / 1. 需求总览

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Map each assignment requirement to concrete implementation and verifiable outcomes.
- 中文：将每一项作业需求映射到可落地实现与可验证结果。

## Executive Summary / 执行摘要

- EN: Requirements were decomposed into API, IaC, security, CI/CD, observability, and AI/LLM tracks.
- 中文：需求被拆解为 API、IaC、安全、CI/CD、可观测性、AI/LLM 六条主线。
- EN: Every track was tied to code artifacts, deployment behavior, and test evidence.
- 中文：每条主线都绑定代码产物、部署行为和测试证据。
- EN: Risk items were converted into explicit controls instead of informal assumptions.
- 中文：风险项被转化为显式控制，而不是隐含假设。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 1. Requirement Overview workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“1. 需求总览”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Use requirement-to-evidence mapping, not requirement-to-claim mapping.
1. 中文：采用“需求到证据”映射，而非“需求到口头结论”映射。
2. EN: Treat fresh-cluster rebuild as mandatory acceptance criteria.
2. 中文：将全新集群重建纳入必选验收标准。
3. EN: Keep security controls testable in CI and enforceable in runtime admission.
3. 中文：确保安全控制在 CI 可测试、在运行时准入可强制。
4. EN: Document both delivered scope and known limitations to reduce interview ambiguity.
4. 中文：同时记录已交付范围与边界限制，降低面试表达歧义。

## Evidence & Metrics / 证据与指标

- EN: Coverage: all six requirement domains have implementation sections and file references.
- 中文：覆盖度：六大需求域均有实现章节与文件映射。
- EN: Validation: includes tests, deployment checks, and runtime control verification.
- 中文：验证方式：包含测试、部署检查与运行态控制验证。
- EN: Traceability: each requirement can be traced to Terraform/Helm/App/CI artifacts.
- 中文：可追溯性：每项需求可追踪到 Terraform/Helm/应用/CI 制品。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
How do you prove requirement completeness?

**Answer:**
By providing requirement-to-artifact-to-evidence traceability, not only architecture diagrams.

### 问题 1（中文）
你如何证明需求覆盖是完整的？

**回答：**
通过“需求-制品-证据”可追溯链路证明完整性，而不是只给架构图。

### Q2 (EN)
What changed after deep rewrite?

**Answer:**
The document now emphasizes measurable outcomes, controls, and interview storytelling.

### 问题 2（中文）
深度重写后，文档发生了哪些关键变化？

**回答：**
文档现在更强调可量化结果、控制闭环与面试可讲述性。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
