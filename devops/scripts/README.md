# Operations Scripts Playbook / 运维脚本作战手册

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-focused deep rewrite for production communication.
- 中文：这是面向生产沟通的中英双语、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, verifiable evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、可验证证据和可辩护取舍。
- EN: Codify day-0/day-1/day-2 operations into repeatable scripts with safety guardrails.
- 中文：将 day-0/day-1/day-2 运维流程固化为可重复、带安全护栏的脚本。

## Executive Summary / 执行摘要

- EN: Scripts cover environment bootstrap, deploy, verify, local test, and teardown.
- 中文：脚本覆盖环境初始化、部署、验证、本地测试和销毁。
- EN: Operational sequencing reduces manual mistakes during high-pressure tasks.
- 中文：明确执行顺序可降低高压场景下的人工失误。
- EN: The playbook links script usage to expected outcomes and rollback paths.
- 中文：手册将脚本使用与预期结果、回滚路径绑定。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the "Operations Scripts Playbook" workstream and converted fragmented operations into a policy-backed, evidence-driven delivery narrative.
- 中文：我主导了“运维脚本作战手册”工作流，把分散操作升级为“策略约束 + 证据驱动”的交付叙述。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | Delivery moved fast with AI support, but baseline artifacts could not be trusted by default. | 在 AI 辅助下交付速度很快，但基础产物默认并不可信。 |
| Task | Build a reproducible and defensible implementation with explicit controls. | 构建具备显式控制、可复现且可辩护的实现。 |
| Action | Standardized docs, encoded trade-offs, and added interview-ready evidence and Q&A. | 统一文档结构、固化关键取舍，并补充面试可复述证据与问答。 |
| Result | Reduced ambiguity, improved operational consistency, and strengthened interview communication quality. | 降低歧义、提升运维一致性，并增强面试表达质量。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Encode safety checks and preconditions directly in scripts.
1. 中文：将安全检查与前置条件直接写入脚本。
2. EN: Use environment variables for explicit environment targeting.
2. 中文：通过环境变量显式指定目标环境。
3. EN: Separate verification from deployment to improve incident triage.
3. 中文：将验证与部署分离，提升故障定位效率。
4. EN: Provide local Kind path to validate changes before cloud impact.
4. 中文：提供本地 Kind 路径，先本地验证再影响云端。

## Evidence & Metrics / 证据与指标

- EN: Execution reliability improves by reducing one-off manual commands.
- 中文：减少一次性手工命令可提升执行可靠性。
- EN: Recovery speed improves with scripted teardown and rebuild flows.
- 中文：脚本化销毁与重建提升恢复速度。
- EN: Team handoff quality improves via clear script intent and usage contracts.
- 中文：明确脚本意图与用法契约可提升交接质量。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
Why invest heavily in scripts instead of wiki steps?

**Answer:**
Scripts are executable truth; they reduce ambiguity and enforce ordering.

### 问题 1（中文）
为什么要投入脚本，而不是只写 Wiki 步骤？

**回答：**
脚本是可执行真相，能减少歧义并强制执行顺序。

### Q2 (EN)
How does this help in interviews?

**Answer:**
It demonstrates operational ownership beyond writing Kubernetes YAML.

### 问题 2（中文）
这在面试中能体现什么？

**回答：**
它体现的是端到端运维责任，而不只是会写 Kubernetes YAML。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes, then explain controls, then cite runtime evidence.
- 中文：先讲结果，再讲控制，再给运行证据。
- EN: Mention one trade-off and one mitigation in each answer.
- 中文：每个回答都要包含一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
