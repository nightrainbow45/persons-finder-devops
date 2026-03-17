# 5. Infrastructure as Code (Kubernetes / Terraform) / 5. 基础设施即代码（Kubernetes / Terraform）

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Describe environment provisioning, deployment topology, and resilience controls from code.
- 中文：从代码层面说明环境创建、部署拓扑与韧性控制。

## Executive Summary / 执行摘要

- EN: Terraform provisions VPC, EKS, IAM, ECR, and supporting cloud services.
- 中文：Terraform 统一编排 VPC、EKS、IAM、ECR 及配套云资源。
- EN: Helm templates encode deployment strategy, probes, resources, and ingress behavior.
- 中文：Helm 模板编码部署策略、探针、资源配额与 Ingress 行为。
- EN: HPA and rollout configuration are tuned against real node capacity constraints.
- 中文：HPA 与滚动更新参数按真实节点容量进行调优。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 5. Infrastructure as Code (Kubernetes / Terraform) workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“5. 基础设施即代码（Kubernetes / Terraform）”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Treat Terraform state backend prerequisites as explicit bootstrap steps.
1. 中文：将 Terraform 后端前置条件作为显式引导步骤管理。
2. EN: Fix RBAC and aws-auth mapping together to avoid identity-without-permission gaps.
2. 中文：同步处理 aws-auth 映射与 RBAC 绑定，避免“有身份无权限”。
3. EN: Use secret-driven config rotation patterns to trigger controlled restarts.
3. 中文：采用机密驱动配置更新并触发可控重启。
4. EN: Validate idempotency with destroy+apply, not only incremental apply.
4. 中文：用 destroy+apply 验证幂等性，而非只做增量 apply。

## Evidence & Metrics / 证据与指标

- EN: Provisioning traceability: environment resources are fully declarative.
- 中文：可追溯性：环境资源声明化管理。
- EN: Recovery confidence: fresh-cluster rebuild path is documented and tested.
- 中文：恢复信心：全新集群重建路径已文档化并验证。
- EN: Deployment stability: probes/resources/rollout settings reduce update deadlocks.
- 中文：部署稳定性：探针/配额/滚动参数降低更新死锁风险。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
Why is destroy+apply a non-negotiable check?

**Answer:**
Because hidden state masks permission and lifecycle defects that only fresh state reveals.

### 问题 1（中文）
为什么 destroy+apply 是不可省略的检查？

**回答：**
因为历史状态会掩盖权限与生命周期缺陷，只有全新状态才能暴露真实问题。

### Q2 (EN)
How did you avoid over-permissioning deploy roles?

**Answer:**
Minimal ClusterRole verbs scoped to Helm-required resources with explicit bindings.

### 问题 2（中文）
你如何避免部署角色权限过大？

**回答：**
通过最小化 ClusterRole 动词并限定 Helm 所需资源，再配合显式绑定实现权限收敛。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
