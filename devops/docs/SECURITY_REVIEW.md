# Security Review Guide / 安全评审指南

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-focused deep rewrite for production communication.
- 中文：这是面向生产沟通的中英双语、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, verifiable evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、可验证证据和可辩护取舍。
- EN: Provide a repeatable review framework for API docs, runtime posture, and network exposure.
- 中文：提供可复用的评审框架，覆盖 API 文档、运行姿态与网络暴露面。

## Executive Summary / 执行摘要

- EN: The review evaluates data exposure, auth boundaries, and operational controls.
- 中文：评审覆盖数据暴露、认证边界与运维控制。
- EN: Findings are categorized by severity and remediation urgency.
- 中文：评审结果按严重级别与修复优先级分类。
- EN: The process emphasizes evidence-backed conclusions over assumptions.
- 中文：流程强调基于证据得出结论，而非主观假设。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the "Security Review Guide" workstream and converted fragmented operations into a policy-backed, evidence-driven delivery narrative.
- 中文：我主导了“安全评审指南”工作流，把分散操作升级为“策略约束 + 证据驱动”的交付叙述。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | Delivery moved fast with AI support, but baseline artifacts could not be trusted by default. | 在 AI 辅助下交付速度很快，但基础产物默认并不可信。 |
| Task | Build a reproducible and defensible implementation with explicit controls. | 构建具备显式控制、可复现且可辩护的实现。 |
| Action | Standardized docs, encoded trade-offs, and added interview-ready evidence and Q&A. | 统一文档结构、固化关键取舍，并补充面试可复述证据与问答。 |
| Result | Reduced ambiguity, improved operational consistency, and strengthened interview communication quality. | 降低歧义、提升运维一致性，并增强面试表达质量。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Review documentation and runtime behavior together.
1. 中文：文档与运行行为必须联合评审。
2. EN: Separate informational findings from release-blocking risks.
2. 中文：区分信息性发现与阻断发布风险。
3. EN: Require remediation ownership and verification closure.
3. 中文：要求修复责任归属与闭环验证。
4. EN: Track recurring patterns to improve upstream design standards.
4. 中文：追踪重复问题模式，反向优化设计标准。

## Evidence & Metrics / 证据与指标

- EN: Review quality improves through structured findings taxonomy.
- 中文：结构化发现分类提升评审质量。
- EN: Security drift reduces when closure evidence is mandatory.
- 中文：强制闭环证据可减少安全漂移。
- EN: Cross-team clarity improves with severity-based communication.
- 中文：基于严重性沟通提升跨团队清晰度。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
What makes your security review actionable?

**Answer:**
Every finding includes impact, owner, fix path, and verification evidence.

### 问题 1（中文）
你的安全评审为什么具有可执行性？

**回答：**
每条发现都给出影响、责任人、修复路径和验证证据。

### Q2 (EN)
How do you avoid checkbox-style security reviews?

**Answer:**
By validating runtime behavior and enforcement paths, not only static config.

### 问题 2（中文）
你如何避免“打勾式”安全评审？

**回答：**
通过验证运行行为与执行链路，而不仅看静态配置。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes, then explain controls, then cite runtime evidence.
- 中文：先讲结果，再讲控制，再给运行证据。
- EN: Mention one trade-off and one mitigation in each answer.
- 中文：每个回答都要包含一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
