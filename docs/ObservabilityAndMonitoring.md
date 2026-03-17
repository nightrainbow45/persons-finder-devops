# 9. Observability & Monitoring / 9. 可观测性与监控

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Explain how logs, metrics, health probes, and alerts support secure operations.
- 中文：说明日志、指标、探针与告警如何支撑安全运维。

## Executive Summary / 执行摘要

- EN: Structured JSON logs capture PII redaction decisions and context.
- 中文：结构化 JSON 日志记录 PII 脱敏决策与上下文。
- EN: Actuator health endpoints provide orchestration-level readiness signals.
- 中文：Actuator 健康端点提供编排层可用性信号。
- EN: Fluent Bit ships runtime logs to CloudWatch for centralized search and alerting.
- 中文：Fluent Bit 将运行日志汇聚到 CloudWatch 便于检索与告警。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 9. Observability & Monitoring workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“9. 可观测性与监控”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Define log schema around incident response needs, not developer convenience.
1. 中文：日志字段围绕故障响应需求设计，而非开发便利。
2. EN: Use metric filters and alarms for PII leak-risk indicators.
2. 中文：通过 metric filter 与告警跟踪 PII 泄露风险信号。
3. EN: Keep health, telemetry, and alerting correlated by deployment metadata.
3. 中文：通过部署元数据关联健康、遥测与告警。
4. EN: Document end-to-end telemetry data flow for debug reproducibility.
4. 中文：文档化端到端遥测路径以提升排障复现性。

## Evidence & Metrics / 证据与指标

- EN: Signal quality: logs are machine-parseable and human-readable.
- 中文：信号质量：日志既可机器解析也可人工阅读。
- EN: Alert relevance: alarms target security/PII risk events, not noise.
- 中文：告警相关性：聚焦安全/PII 风险而非噪音。
- EN: Operational speed: health endpoints support faster rollout diagnostics.
- 中文：运维效率：健康端点加快发布期诊断速度。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
How do you avoid observability becoming expensive noise?

**Answer:**
By designing event schema and alarms around concrete response actions.

### 问题 1（中文）
你如何避免可观测性变成高成本噪音？

**回答：**
围绕“可执行响应动作”设计事件模型和告警，而不是堆叠指标。

### Q2 (EN)
What is interview-proof evidence here?

**Answer:**
CloudWatch filters/alarms, health checks, and end-to-end log flow validation.

### 问题 2（中文）
这里有哪些“面试可举证”的证据？

**回答：**
CloudWatch 过滤器与告警、健康探针、端到端日志链路验证都是可举证证据。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
