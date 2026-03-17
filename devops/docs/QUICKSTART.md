# Quick Start Guide / 快速入门指南

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-focused deep rewrite for production communication.
- 中文：这是面向生产沟通的中英双语、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, verifiable evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、可验证证据和可辩护取舍。
- EN: Deliver the fastest safe onboarding path for local and cloud deployment.
- 中文：提供本地与云端部署的最快安全上手路径。

## Executive Summary / 执行摘要

- EN: The guide offers two tracks: local Kind and AWS EKS.
- 中文：文档提供两条路径：本地 Kind 与 AWS EKS。
- EN: Commands are optimized for first success with minimal cognitive load.
- 中文：命令设计追求首次成功并降低认知负担。
- EN: Follow-up links guide users to deeper operational and security docs.
- 中文：后续链接引导用户进入更深层运维与安全文档。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the "Quick Start Guide" workstream and converted fragmented operations into a policy-backed, evidence-driven delivery narrative.
- 中文：我主导了“快速入门指南”工作流，把分散操作升级为“策略约束 + 证据驱动”的交付叙述。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | Delivery moved fast with AI support, but baseline artifacts could not be trusted by default. | 在 AI 辅助下交付速度很快，但基础产物默认并不可信。 |
| Task | Build a reproducible and defensible implementation with explicit controls. | 构建具备显式控制、可复现且可辩护的实现。 |
| Action | Standardized docs, encoded trade-offs, and added interview-ready evidence and Q&A. | 统一文档结构、固化关键取舍，并补充面试可复述证据与问答。 |
| Result | Reduced ambiguity, improved operational consistency, and strengthened interview communication quality. | 降低歧义、提升运维一致性，并增强面试表达质量。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Start with local reproducibility before cloud spend.
1. 中文：先保证本地可复现，再进入云端成本域。
2. EN: Keep quickstart short but explicit about prerequisites.
2. 中文：快速入门保持简洁，但前置条件必须明确。
3. EN: Separate happy-path commands from troubleshooting paths.
3. 中文：区分主路径命令与故障排查路径。
4. EN: Use stable command aliases and environment variables.
4. 中文：使用稳定命令别名与环境变量约定。

## Evidence & Metrics / 证据与指标

- EN: Time-to-first-success is reduced via minimal command path.
- 中文：最小命令路径缩短首次成功时间。
- EN: Drop-off risk decreases with explicit next-step guidance.
- 中文：明确下一步指引可降低中途流失。
- EN: Operational safety remains intact through prerequisite checks.
- 中文：前置检查确保快速路径不牺牲安全性。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
What makes a quickstart interview-grade?

**Answer:**
It balances speed with guardrails, not just short command lists.

### 问题 1（中文）
什么样的快速入门文档算“面试级”？

**回答：**
它能在追求速度的同时保留护栏，而不只是命令更短。

### Q2 (EN)
How do you avoid oversimplifying production realities?

**Answer:**
By linking quickstart to deployment, security, and verification runbooks.

### 问题 2（中文）
你如何避免过度简化生产现实？

**回答：**
通过把快速入门与部署、安全、验证手册串成闭环。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes, then explain controls, then cite runtime evidence.
- 中文：先讲结果，再讲控制，再给运行证据。
- EN: Mention one trade-off and one mitigation in each answer.
- 中文：每个回答都要包含一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
