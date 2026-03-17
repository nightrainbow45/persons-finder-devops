# 7. AI Firewall: PII Egress Protection Architecture / 7. AI 防火墙：PII 出站保护架构

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Explain multi-layer controls that prevent sensitive data leakage to external LLMs.
- 中文：说明多层控制如何防止敏感数据泄露到外部 LLM。

## Executive Summary / 执行摘要

- EN: Layer 1 redacts data in-process before outbound calls.
- 中文：第 1 层在进程内进行脱敏后再发起外呼。
- EN: Layer 2 sidecar provides independent outbound proxy enforcement.
- 中文：第 2 层 sidecar 提供独立的出站代理强制。
- EN: Layer 3 NetworkPolicy constrains pod-level egress paths.
- 中文：第 3 层 NetworkPolicy 约束 Pod 出站路径。
- EN: Layer 4 observability captures PII audit signals and alerts.
- 中文：第 4 层可观测性采集 PII 审计信号与告警。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 7. AI Firewall: PII Egress Protection Architecture workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“7. AI 防火墙：PII 出站保护架构”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Prefer defense-in-depth over a single redaction hook.
1. 中文：采用纵深防御，而非单一脱敏钩子。
2. EN: Use explicit proxy URL injection to avoid silent bypass.
2. 中文：通过显式代理 URL 注入，避免无感绕过。
3. EN: Treat telemetry as a security control, not optional logging.
3. 中文：将遥测视为安全控制而不是“可选日志”。
4. EN: Document known limits (IP-based policy vs FQDN intent).
4. 中文：明确记录已知边界（IP 级策略与 FQDN 诉求差异）。

## Evidence & Metrics / 证据与指标

- EN: Control layering: 4 independent layers reduce single-point failure.
- 中文：控制分层：4 层独立控制降低单点失效风险。
- EN: Auditability: redaction events are structured and queryable.
- 中文：可审计性：脱敏事件结构化且可检索。
- EN: Containment: network controls limit unapproved outbound destinations.
- 中文：约束性：网络策略限制未授权外部访问。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
Why not rely only on app-layer redaction?

**Answer:**
Because process-level bypass, config drift, or code regression can nullify a single layer.

### 问题 1（中文）
为什么不能只依赖应用层脱敏？

**回答：**
因为进程级绕过、配置漂移或代码回归都可能让单层防护失效。

### Q2 (EN)
What is the strongest interview takeaway?

**Answer:**
Security posture improved by layering controls with runtime evidence, not by one feature toggle.

### 问题 2（中文）
这部分最值得在面试中强调的结论是什么？

**回答：**
真正提升安全姿态的是“分层控制 + 运行态证据”，而不是单一功能开关。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
