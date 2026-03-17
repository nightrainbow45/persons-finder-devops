# Docker Build & Runtime Guide / Docker 构建与运行指南

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-focused deep rewrite for production communication.
- 中文：这是面向生产沟通的中英双语、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, verifiable evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、可验证证据和可辩护取舍。
- EN: Standardize container build hardening and runtime behavior.
- 中文：标准化容器构建加固与运行行为。

## Executive Summary / 执行摘要

- EN: Multi-stage builds minimize runtime surface while preserving build speed.
- 中文：多阶段构建在保留构建效率的同时缩小运行攻击面。
- EN: Image pinning and non-root runtime form the baseline hardening set.
- 中文：镜像版本固定与非 root 运行构成基础加固组合。
- EN: Local run examples align with production environment variables and probes.
- 中文：本地运行示例与生产环境变量和探针语义保持一致。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the "Docker Build & Runtime Guide" workstream and converted fragmented operations into a policy-backed, evidence-driven delivery narrative.
- 中文：我主导了“Docker 构建与运行指南”工作流，把分散操作升级为“策略约束 + 证据驱动”的交付叙述。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | Delivery moved fast with AI support, but baseline artifacts could not be trusted by default. | 在 AI 辅助下交付速度很快，但基础产物默认并不可信。 |
| Task | Build a reproducible and defensible implementation with explicit controls. | 构建具备显式控制、可复现且可辩护的实现。 |
| Action | Standardized docs, encoded trade-offs, and added interview-ready evidence and Q&A. | 统一文档结构、固化关键取舍，并补充面试可复述证据与问答。 |
| Result | Reduced ambiguity, improved operational consistency, and strengthened interview communication quality. | 降低歧义、提升运维一致性，并增强面试表达质量。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Separate app and sidecar Dockerfiles for independent patch management.
1. 中文：主应用与 sidecar 分离 Dockerfile，便于独立补丁治理。
2. EN: Use `.dockerignore` aggressively to reduce context and secret exposure.
2. 中文：强化 `.dockerignore` 以缩小上下文并减少机密暴露。
3. EN: Favor minimal runtime images and explicit health checks.
3. 中文：优先最小运行镜像并显式配置健康检查。
4. EN: Align local tags with immutable release strategy guidance.
4. 中文：本地标签策略与不可变发布策略保持对齐。

## Evidence & Metrics / 证据与指标

- EN: Build reproducibility improves through pinned image references.
- 中文：固定镜像引用提升构建可复现性。
- EN: Runtime risk decreases with reduced privileges and hardened base images.
- 中文：降权运行与加固基础镜像降低运行时风险。
- EN: Developer usability remains high with concise local commands.
- 中文：精简本地命令保持开发可用性。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
Why not just use one simple Dockerfile?

**Answer:**
Because separation improves security patch velocity and operational clarity.

### 问题 1（中文）
为什么不使用一个简单 Dockerfile 就结束？

**回答：**
因为分离策略能提升安全补丁速度并增强运维可读性。

### Q2 (EN)
What is the interview highlight in this doc?

**Answer:**
I converted container best practices into enforceable operational defaults.

### 问题 2（中文）
这份文档的面试亮点是什么？

**回答：**
我把容器最佳实践落成了可执行的默认运维标准。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes, then explain controls, then cite runtime evidence.
- 中文：先讲结果，再讲控制，再给运行证据。
- EN: Mention one trade-off and one mitigation in each answer.
- 中文：每个回答都要包含一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
