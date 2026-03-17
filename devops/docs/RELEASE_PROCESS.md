# Release Process Guide / 发布流程指南

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-focused deep rewrite for production communication.
- 中文：这是面向生产沟通的中英双语、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, verifiable evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、可验证证据和可辩护取舍。
- EN: Define versioning, immutable tagging, and promotion workflow with operational safeguards.
- 中文：定义带运维护栏的版本策略、不可变标签与发布晋级流程。

## Executive Summary / 执行摘要

- EN: The process codifies semver decisions and artifact promotion boundaries.
- 中文：流程固化语义版本决策与制品晋级边界。
- EN: Immutable tag constraints are embedded in release commands.
- 中文：不可变标签约束被嵌入发布命令体系。
- EN: The guide aligns code, image, and deployment references to prevent drift.
- 中文：文档对齐代码、镜像与部署引用，防止漂移。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the "Release Process Guide" workstream and converted fragmented operations into a policy-backed, evidence-driven delivery narrative.
- 中文：我主导了“发布流程指南”工作流，把分散操作升级为“策略约束 + 证据驱动”的交付叙述。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | Delivery moved fast with AI support, but baseline artifacts could not be trusted by default. | 在 AI 辅助下交付速度很快，但基础产物默认并不可信。 |
| Task | Build a reproducible and defensible implementation with explicit controls. | 构建具备显式控制、可复现且可辩护的实现。 |
| Action | Standardized docs, encoded trade-offs, and added interview-ready evidence and Q&A. | 统一文档结构、固化关键取舍，并补充面试可复述证据与问答。 |
| Result | Reduced ambiguity, improved operational consistency, and strengthened interview communication quality. | 降低歧义、提升运维一致性，并增强面试表达质量。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Use explicit semver policy for predictable release semantics.
1. 中文：使用显式语义版本策略，保证发布语义可预测。
2. EN: Adopt immutable image tags (e.g., `git-sha`) for deployment determinism.
2. 中文：采用不可变镜像标签（如 `git-sha`）保证部署确定性。
3. EN: Require changelog and rollback notes for each release candidate.
3. 中文：每个候选发布都要求变更说明与回滚说明。
4. EN: Validate tag strategy against registry policies before publish.
4. 中文：发布前先校验标签策略与仓库策略的一致性。

## Evidence & Metrics / 证据与指标

- EN: Release failures from tag collisions are reduced with immutable strategy.
- 中文：不可变策略降低标签冲突导致的发布失败。
- EN: Version semantics become easier to communicate across teams.
- 中文：版本语义更容易跨团队沟通。
- EN: Audit traceability improves from commit to deployment artifact.
- 中文：从提交到部署制品的审计追溯能力增强。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
Why is immutable tagging critical in this project?

**Answer:**
Because registry policy enforces immutability, and floating tags cause release breakage.

### 问题 1（中文）
为什么不可变标签在本项目里是关键？

**回答：**
因为仓库策略强制不可变，浮动标签会直接引发发布失败。

### Q2 (EN)
How do you explain release discipline in interviews?

**Answer:**
As a contract linking version intent, artifact identity, and deployment behavior.

### 问题 2（中文）
你在面试中如何解释发布纪律？

**回答：**
它是连接版本意图、制品身份与部署行为的契约。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes, then explain controls, then cite runtime evidence.
- 中文：先讲结果，再讲控制，再给运行证据。
- EN: Mention one trade-off and one mitigation in each answer.
- 中文：每个回答都要包含一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
