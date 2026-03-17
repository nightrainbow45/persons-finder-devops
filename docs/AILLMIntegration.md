# 10. AI/LLM Integration / 10. AI/LLM 集成

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Describe safe LLM integration with PII controls, proxying, and secret hygiene.
- 中文：说明在 PII 控制、代理链路与机密治理下的安全 LLM 集成方案。

## Executive Summary / 执行摘要

- EN: PiiProxyService performs request-time redaction before upstream model calls.
- 中文：PiiProxyService 在模型调用前进行请求时脱敏。
- EN: Reversible tokenization preserves debugging traceability within request scope.
- 中文：可逆令牌在请求范围内兼顾调试追踪与脱敏需求。
- EN: Go sidecar acts as independent egress control for LLM traffic.
- 中文：Go sidecar 作为 LLM 流量的独立出站控制层。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 10. AI/LLM Integration workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“10. AI/LLM 集成”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Keep secret keys externalized and injected at runtime.
1. 中文：密钥外置并在运行时注入。
2. EN: Separate business logic from redaction mechanics for testability.
2. 中文：业务逻辑与脱敏机制解耦，提升可测性。
3. EN: Provide fallback and timeout strategy for model call resilience.
3. 中文：为模型调用提供超时与回退策略。
4. EN: Use structured audit events to make AI behavior reviewable.
4. 中文：使用结构化审计事件让 AI 行为可复核。

## Evidence & Metrics / 证据与指标

- EN: Safety: PII handling path is explicit and test-covered.
- 中文：安全性：PII 处理路径显式且有测试覆盖。
- EN: Isolation: sidecar allows policy enforcement independent of app code.
- 中文：隔离性：sidecar 让策略执行不依赖应用代码。
- EN: Auditability: redaction outcomes are observable at runtime.
- 中文：可审计性：脱敏结果可在运行态观察。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
How do you balance LLM utility and privacy?

**Answer:**
By layering in-process redaction, egress proxy control, and auditable telemetry.

### 问题 1（中文）
你如何平衡 LLM 可用性与隐私保护？

**回答：**
通过进程内脱敏、出站代理控制与可审计遥测三层联动来平衡可用性与隐私。

### Q2 (EN)
What failure mode did you design against?

**Answer:**
Silent bypass of redaction controls through direct outbound calls.

### 问题 2（中文）
你重点防范了哪类故障模式？

**回答：**
重点防范“直接外呼绕过脱敏链路”的静默失效模式。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
