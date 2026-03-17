# Swagger Troubleshooting Guide / Swagger 故障排查指南

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-focused deep rewrite for production communication.
- 中文：这是面向生产沟通的中英双语、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, verifiable evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、可验证证据和可辩护取舍。
- EN: Provide fast diagnostics and reliable fixes for Swagger UI access and integration issues.
- 中文：为 Swagger UI 访问与集成问题提供快速诊断与可靠修复。

## Executive Summary / 执行摘要

- EN: The guide classifies failures by layer: app, ingress, DNS, TLS, and auth.
- 中文：文档按层次分类故障：应用、Ingress、DNS、TLS、认证。
- EN: Diagnostic commands are sequenced from low-cost checks to deep inspection.
- 中文：诊断命令按“低成本先行、深度检查后置”排序。
- EN: Fixes include rollback-safe changes for production environments.
- 中文：修复方案包含适用于生产环境的可回退变更。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the "Swagger Troubleshooting Guide" workstream and converted fragmented operations into a policy-backed, evidence-driven delivery narrative.
- 中文：我主导了“Swagger 故障排查指南”工作流，把分散操作升级为“策略约束 + 证据驱动”的交付叙述。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | Delivery moved fast with AI support, but baseline artifacts could not be trusted by default. | 在 AI 辅助下交付速度很快，但基础产物默认并不可信。 |
| Task | Build a reproducible and defensible implementation with explicit controls. | 构建具备显式控制、可复现且可辩护的实现。 |
| Action | Standardized docs, encoded trade-offs, and added interview-ready evidence and Q&A. | 统一文档结构、固化关键取舍，并补充面试可复述证据与问答。 |
| Result | Reduced ambiguity, improved operational consistency, and strengthened interview communication quality. | 降低歧义、提升运维一致性，并增强面试表达质量。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Start with symptom fingerprinting before changing configs.
1. 中文：修改配置前先做症状指纹定位。
2. EN: Correlate app logs with ingress and certificate state.
2. 中文：关联应用日志、Ingress 状态与证书状态。
3. EN: Keep CORS/auth guidance explicit for environment differences.
3. 中文：针对环境差异显式说明 CORS 与认证策略。
4. EN: Document known production-safe disable patterns for Swagger.
4. 中文：记录 Swagger 在生产环境的安全关闭模式。

## Evidence & Metrics / 证据与指标

- EN: MTTR decreases with ordered diagnostics and known-fix mapping.
- 中文：有序诊断与已知修复映射可降低 MTTR。
- EN: False fixes decrease when root-cause layers are isolated first.
- 中文：先隔离根因层级可减少无效修复。
- EN: Operator confidence improves with reproducible command workflow.
- 中文：可复现命令流程提升运维信心。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
What is the first rule in Swagger incident response?

**Answer:**
Identify failing layer first; do not jump directly to config edits.

### 问题 1（中文）
Swagger 故障响应第一原则是什么？

**回答：**
先定位故障层级，不要直接改配置。

### Q2 (EN)
How do you keep troubleshooting interview-relevant?

**Answer:**
By linking each symptom to verification commands and rollback-safe fixes.

### 问题 2（中文）
你如何让排障经验在面试中更有说服力？

**回答：**
把每个症状绑定验证命令和可回退修复动作。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes, then explain controls, then cite runtime evidence.
- 中文：先讲结果，再讲控制，再给运行证据。
- EN: Mention one trade-off and one mitigation in each answer.
- 中文：每个回答都要包含一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
