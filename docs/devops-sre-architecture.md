# Persons Finder — DevOps/SRE Full Architecture / Persons Finder — DevOps/SRE 全景架构

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Provide a board-level and interviewer-friendly narrative for the full DevOps/SRE system.
- 中文：提供董事会级和面试友好的 DevOps/SRE 全景叙述。

## Executive Summary / 执行摘要

- EN: Covers platform, delivery, security, resilience, cost, and SLO dimensions end-to-end.
- 中文：端到端覆盖平台、交付、安全、韧性、成本与 SLO 维度。
- EN: Connects architecture choices to day-2 operations and incident handling.
- 中文：将架构选择与 Day-2 运维、故障处置直接关联。
- EN: Balances production rigor with practical cost constraints.
- 中文：在生产严谨性与成本约束之间给出务实平衡。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the Persons Finder — DevOps/SRE Full Architecture workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“Persons Finder — DevOps/SRE 全景架构”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Design by failure domain: compute, identity, supply chain, and network boundaries.
1. 中文：按故障域设计：计算、身份、供应链、网络边界。
2. EN: Operationalize controls with measurable signals and ownership.
2. 中文：通过可度量信号与责任归属实现控制落地。
3. EN: Use layered rollback/recovery paths for high-confidence releases.
3. 中文：通过分层回滚与恢复路径提升发布确定性。
4. EN: Keep architecture explainable in interview narratives with STAR framing.
4. 中文：用 STAR 框架保证架构在面试中可讲清。

## Evidence & Metrics / 证据与指标

- EN: Reliability narrative: SLI/SLO, scaling, and failure handling are linked.
- 中文：可靠性叙述：SLI/SLO、扩缩容与故障处理形成闭环。
- EN: Security narrative: build-time and runtime trust controls are integrated.
- 中文：安全叙述：构建时与运行时信任控制一体化。
- EN: Cost narrative: node mix and workload placement are optimization levers.
- 中文：成本叙述：节点组合与工作负载放置是优化杠杆。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
What makes this architecture interview-grade?

**Answer:**
It explains not just components, but decisions, trade-offs, and evidence under real constraints.

### 问题 1（中文）
是什么让这套架构具备“面试级”说服力？

**回答：**
它不仅解释组件，还解释真实约束下的决策、取舍与证据。

### Q2 (EN)
How do you defend complexity?

**Answer:**
By mapping each complexity element to a specific risk it mitigates.

### 问题 2（中文）
你如何为架构复杂度做合理辩护？

**回答：**
把每一项复杂度都对应到其所缓解的具体风险，就能形成可辩护复杂度。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
