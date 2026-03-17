# 2. Persons Finder — Architecture Diagram / 2. Persons Finder — 架构图

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Turn architecture diagrams into explainable operational decisions for interviews.
- 中文：把架构图转化为面试可讲述的运维决策逻辑。

## Executive Summary / 执行摘要

- EN: Diagram covers request path, node groups, scaling, security, CI/CD, and observability.
- 中文：图谱覆盖请求链路、节点组、扩缩容、安全、CI/CD 与可观测性。
- EN: System and app workloads are separated to reduce blast radius.
- 中文：系统负载与业务负载分离，降低爆炸半径。
- EN: Security controls are layered across build, deploy, network, and runtime.
- 中文：安全控制覆盖构建、部署、网络、运行时多个层级。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 2. Persons Finder — Architecture Diagram workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“2. Persons Finder — 架构图”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Use architecture views by concern: compute, data flow, security, delivery, and operations.
1. 中文：按关注点拆分架构视图：计算、数据流、安全、交付、运维。
2. EN: Keep diagram symbols aligned with real Terraform/Helm resources.
2. 中文：图中元素与 Terraform/Helm 实体保持一致。
3. EN: Document why each control exists, not only where it sits.
3. 中文：不仅说明控制放在哪，还说明为何存在。
4. EN: Prioritize interview readability: from business request to control evidence.
4. 中文：优先面试可读性：从业务请求讲到控制证据。

## Evidence & Metrics / 证据与指标

- EN: Comprehension: architecture can be explained in 2, 5, or 15-minute versions.
- 中文：可讲述性：架构可支持 2/5/15 分钟多时长讲解。
- EN: Traceability: each major component maps to code and deployment artifacts.
- 中文：可追溯性：主要组件均可映射到代码与部署制品。
- EN: Risk clarity: trust boundaries and failure domains are explicit.
- 中文：风险清晰度：信任边界与故障域明确。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
How do you avoid architecture docs becoming decorative?

**Answer:**
By mapping each diagram element to deployment behavior and incident response actions.

### 问题 1（中文）
你如何避免架构文档沦为“好看但无用”？

**回答：**
将图中每个元素映射到部署行为与故障响应动作，避免装饰化。

### Q2 (EN)
What is your strongest architecture narrative?

**Answer:**
Layered controls plus operational evidence, not just component count.

### 问题 2（中文）
你最强的架构叙事主线是什么？

**回答：**
核心叙事是“分层控制 + 运维证据”，而不是组件堆叠。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
