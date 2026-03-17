# AI War Stories — Persons Finder DevOps / AI 实战记录 — Persons Finder DevOps

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-focused deep rewrite for production communication.
- 中文：这是面向生产沟通的中英双语、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, verifiable evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、可验证证据和可辩护取舍。
- EN: Capture real AI-generated mistakes, fixes, and engineering lessons in a reusable interview narrative.
- 中文：沉淀 AI 真实失误、修复动作与工程经验，形成可复用面试叙述。

## Executive Summary / 执行摘要

- EN: The log tracks concrete failures across Docker, Helm, CI/CD, Terraform, and policy enforcement.
- 中文：日志覆盖 Docker、Helm、CI/CD、Terraform 与策略执行的真实故障。
- EN: Each issue is linked to root cause and operational fix, not just code diff.
- 中文：每个问题都关联根因与运维修复，而不仅是代码差异。
- EN: The document turns AI speed into controlled delivery by enforcing human review gates.
- 中文：文档通过人工门禁把 AI 速度转化为可控交付。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the "AI War Stories — Persons Finder DevOps" workstream and converted fragmented operations into a policy-backed, evidence-driven delivery narrative.
- 中文：我主导了“AI 实战记录 — Persons Finder DevOps”工作流，把分散操作升级为“策略约束 + 证据驱动”的交付叙述。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | Delivery moved fast with AI support, but baseline artifacts could not be trusted by default. | 在 AI 辅助下交付速度很快，但基础产物默认并不可信。 |
| Task | Build a reproducible and defensible implementation with explicit controls. | 构建具备显式控制、可复现且可辩护的实现。 |
| Action | Standardized docs, encoded trade-offs, and added interview-ready evidence and Q&A. | 统一文档结构、固化关键取舍，并补充面试可复述证据与问答。 |
| Result | Reduced ambiguity, improved operational consistency, and strengthened interview communication quality. | 降低歧义、提升运维一致性，并增强面试表达质量。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Treat AI output as draft artifacts until runtime behavior is verified.
1. 中文：将 AI 输出视为草稿，只有运行态验证后才可采纳。
2. EN: Require fresh-state validation (`destroy + apply`) to expose hidden lifecycle bugs.
2. 中文：要求新状态验证（`destroy + apply`）以暴露隐藏生命周期问题。
3. EN: Link every failure to a checklist item to prevent recurrence.
3. 中文：把每个故障沉淀为清单项，防止重复踩坑。
4. EN: Prioritize policy-enforced controls over advisory documentation.
4. 中文：优先采用可强制策略控制，而非仅靠建议性文档。

## Evidence & Metrics / 证据与指标

- EN: Cross-domain fault coverage includes container, platform, security, and pipeline layers.
- 中文：故障覆盖跨越容器、平台、安全与流水线多层面。
- EN: Controls are measurable through CI results, admission behavior, and runtime checks.
- 中文：控制效果可通过 CI 结果、准入行为和运行检查量化。
- EN: Risk handling moved from implicit tribal knowledge to explicit playbooks.
- 中文：风险处理从隐性经验迁移到显式操作手册。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
What makes this AI log interview-worthy?

**Answer:**
It proves engineering judgment under real constraints, not just tooling familiarity.

### 问题 1（中文）
这份 AI 日志为什么有面试价值？

**回答：**
它体现的不是工具熟练度，而是真实约束下的工程判断力。

### Q2 (EN)
How do you keep AI speed without losing quality?

**Answer:**
By forcing policy gates, fresh-state validation, and evidence-based acceptance criteria.

### 问题 2（中文）
你如何在保留 AI 速度的同时不牺牲质量？

**回答：**
通过策略门禁、新状态验证与证据驱动验收标准共同兜底。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes, then explain controls, then cite runtime evidence.
- 中文：先讲结果，再讲控制，再给运行证据。
- EN: Mention one trade-off and one mitigation in each answer.
- 中文：每个回答都要包含一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
