# CI/CD Configuration Guide / CI/CD 配置指南

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-focused deep rewrite for production communication.
- 中文：这是面向生产沟通的中英双语、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, verifiable evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、可验证证据和可辩护取舍。
- EN: Define pipeline controls for secure and deterministic releases.
- 中文：定义安全且确定性的流水线控制策略。

## Executive Summary / 执行摘要

- EN: The workflow covers build, scan, sign, and deployment gates.
- 中文：工作流覆盖构建、扫描、签名与部署门禁。
- EN: OIDC-based cloud auth removes long-lived static credentials.
- 中文：基于 OIDC 的云认证消除长期静态凭据。
- EN: Policy checks convert security guidance into enforceable steps.
- 中文：策略检查将安全建议转化为可强制步骤。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the "CI/CD Configuration Guide" workstream and converted fragmented operations into a policy-backed, evidence-driven delivery narrative.
- 中文：我主导了“CI/CD 配置指南”工作流，把分散操作升级为“策略约束 + 证据驱动”的交付叙述。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | Delivery moved fast with AI support, but baseline artifacts could not be trusted by default. | 在 AI 辅助下交付速度很快，但基础产物默认并不可信。 |
| Task | Build a reproducible and defensible implementation with explicit controls. | 构建具备显式控制、可复现且可辩护的实现。 |
| Action | Standardized docs, encoded trade-offs, and added interview-ready evidence and Q&A. | 统一文档结构、固化关键取舍，并补充面试可复述证据与问答。 |
| Result | Reduced ambiguity, improved operational consistency, and strengthened interview communication quality. | 降低歧义、提升运维一致性，并增强面试表达质量。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Pin tool versions to avoid nondeterministic behavior from floating tags.
1. 中文：固定工具版本，避免浮动标签带来的非确定性行为。
2. EN: Use Trivy as a blocking gate for high-severity findings.
2. 中文：将 Trivy 作为高危漏洞阻断门禁。
3. EN: Enforce signed-image path before production promotion.
3. 中文：在进入生产前强制执行签名镜像路径。
4. EN: Keep secrets in GitHub/AWS managed stores, never in workflow source.
4. 中文：机密仅放在 GitHub/AWS 管理存储，不写入 workflow 源码。

## Evidence & Metrics / 证据与指标

- EN: Release confidence comes from pass/fail gates, not manual approvals alone.
- 中文：发布信心来源于可判定门禁，而非仅人工审批。
- EN: Credential risk is reduced by short-lived OIDC tokens.
- 中文：短时 OIDC 令牌显著降低凭据泄露风险。
- EN: Pipeline behavior remains reproducible across branches and environments.
- 中文：流水线行为在分支与环境间保持可复现。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
How do you prove this CI/CD is production-grade?

**Answer:**
It enforces vulnerability, identity, and artifact integrity checks before deploy.

### 问题 1（中文）
你如何证明这套 CI/CD 达到生产级？

**回答：**
它在部署前强制执行漏洞、身份与制品完整性检查。

### Q2 (EN)
What was the key improvement over a basic pipeline?

**Answer:**
Turning security from optional scanning into release-blocking policy gates.

### 问题 2（中文）
相比基础流水线，关键提升是什么？

**回答：**
把“可选扫描”升级为“可阻断发布”的策略门禁。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes, then explain controls, then cite runtime evidence.
- 中文：先讲结果，再讲控制，再给运行证据。
- EN: Mention one trade-off and one mitigation in each answer.
- 中文：每个回答都要包含一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
