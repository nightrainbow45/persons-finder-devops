# DevOps Infrastructure — Persons Finder / DevOps 基础设施 — Persons Finder

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-focused deep rewrite for production communication.
- 中文：这是面向生产沟通的中英双语、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, verifiable evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、可验证证据和可辩护取舍。
- EN: Present the full DevOps repository layout as an operational system, not a folder list.
- 中文：将 DevOps 仓库结构从“目录说明”升级为“可运维系统说明”。

## Executive Summary / 执行摘要

- EN: The repo is organized by delivery stages: build, deploy, verify, and recover.
- 中文：仓库按交付阶段组织：构建、部署、验证、恢复。
- EN: Infrastructure, charts, scripts, and docs are linked as one control plane.
- 中文：基础设施、Chart、脚本与文档形成统一控制面。
- EN: The structure supports both onboarding speed and production governance.
- 中文：该结构同时支持快速上手与生产治理。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the "DevOps Infrastructure — Persons Finder" workstream and converted fragmented operations into a policy-backed, evidence-driven delivery narrative.
- 中文：我主导了“DevOps 基础设施 — Persons Finder”工作流，把分散操作升级为“策略约束 + 证据驱动”的交付叙述。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | Delivery moved fast with AI support, but baseline artifacts could not be trusted by default. | 在 AI 辅助下交付速度很快，但基础产物默认并不可信。 |
| Task | Build a reproducible and defensible implementation with explicit controls. | 构建具备显式控制、可复现且可辩护的实现。 |
| Action | Standardized docs, encoded trade-offs, and added interview-ready evidence and Q&A. | 统一文档结构、固化关键取舍，并补充面试可复述证据与问答。 |
| Result | Reduced ambiguity, improved operational consistency, and strengthened interview communication quality. | 降低歧义、提升运维一致性，并增强面试表达质量。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Keep source-of-truth pipeline config in `devops/ci` and sync to GitHub workflows.
1. 中文：将流水线真源放在 `devops/ci`，再同步到 GitHub workflows。
2. EN: Separate build, deploy, and runbook docs for clean ownership boundaries.
2. 中文：拆分构建、部署与操作手册，明确职责边界。
3. EN: Use script-driven operations to minimize manual drift.
3. 中文：以脚本驱动运维动作，减少人工漂移。
4. EN: Maintain environment-agnostic conventions for local and cloud parity.
4. 中文：保持本地与云环境的一致约定。

## Evidence & Metrics / 证据与指标

- EN: Traceability: every operational path maps to a specific file group.
- 中文：可追溯性：每条运维路径都可映射到具体文件组。
- EN: Governance: ownership boundaries reduce change ambiguity.
- 中文：治理性：职责边界减少变更歧义。
- EN: Operability: scripts + docs reduce incident recovery latency.
- 中文：可运维性：脚本与文档协同降低故障恢复时延。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
How do you explain this DevOps tree in an interview?

**Answer:**
I map folders to lifecycle stages and show where controls are enforced.

### 问题 1（中文）
你在面试中如何讲清这个 DevOps 目录？

**回答：**
我会把目录映射到交付生命周期，并指出每个控制点在哪里执行。

### Q2 (EN)
What is the practical value of this structure?

**Answer:**
It shortens onboarding while preserving policy and release consistency.

### 问题 2（中文）
这种结构的实际价值是什么？

**回答：**
它既缩短上手时间，也保持策略与发布的一致性。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes, then explain controls, then cite runtime evidence.
- 中文：先讲结果，再讲控制，再给运行证据。
- EN: Mention one trade-off and one mitigation in each answer.
- 中文：每个回答都要包含一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
