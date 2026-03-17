# 6. Security & Secrets Management / 6. 安全与机密管理

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Present how secrets, runtime hardening, and admission controls work as a single chain.
- 中文：呈现机密管理、运行时加固与准入控制如何形成闭环。

## Executive Summary / 执行摘要

- EN: Secrets are injected at runtime, never baked into images or static values files.
- 中文：机密在运行时注入，绝不固化到镜像或静态 values。
- EN: Pod security context and RBAC implement least-privilege defaults.
- 中文：Pod 安全上下文与 RBAC 实现最小权限默认策略。
- EN: Image trust is enforced with cosign signing and Kyverno admission verification.
- 中文：通过 cosign 签名与 Kyverno 准入验证强制镜像可信。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 6. Security & Secrets Management workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“6. 安全与机密管理”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Use External Secrets Operator for cloud-to-cluster secret synchronization.
1. 中文：使用 ESO 实现云端机密到集群的同步。
2. EN: Restrict network egress with allowlist-style NetworkPolicy.
2. 中文：通过白名单式 NetworkPolicy 收敛出站流量。
3. EN: Keep TLS termination and ingress hardening centralized.
3. 中文：统一管理 TLS 终止与 Ingress 加固配置。
4. EN: Separate detection gates (Trivy) from enforcement gates (Kyverno).
4. 中文：将检测门禁（Trivy）与强制门禁（Kyverno）分层。

## Evidence & Metrics / 证据与指标

- EN: Secret hygiene: no hardcoded OpenAI keys in repo or image layers.
- 中文：机密卫生：仓库与镜像层均无硬编码 OpenAI key。
- EN: Runtime policy: signed-image-only enforcement path is active.
- 中文：运行时策略：仅签名镜像可部署路径已生效。
- EN: Least privilege: RBAC scope reduced to required operational surface.
- 中文：最小权限：RBAC 范围收敛到必需运维面。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
What is the most defensible security design choice here?

**Answer:**
Runtime secret injection + signed image admission; both are enforceable and auditable.

### 问题 1（中文）
这里最可辩护的安全设计选择是什么？

**回答：**
运行时机密注入 + 签名镜像准入，二者都可强制执行且可审计。

### Q2 (EN)
How do you handle false positives in vulnerability scans?

**Answer:**
Documented suppression with expiry/removal conditions and explicit exploit context.

### 问题 2（中文）
你如何处理漏洞扫描中的误报？

**回答：**
对误报采用文档化抑制，并写明到期/移除条件与可利用前提。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
