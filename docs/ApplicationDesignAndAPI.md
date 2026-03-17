# 3. Application Design & API / 3. 应用设计与 API

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Explain API behavior, service boundaries, and correctness guarantees for location-based queries.
- 中文：说明 API 行为、服务边界与位置查询正确性保证。

## Executive Summary / 执行摘要

- EN: Core endpoints cover create person, update location, nearby search, and batch read.
- 中文：核心端点覆盖创建人员、更新位置、附近搜索与批量读取。
- EN: Distance calculation is deterministic via Haversine formula with testable radius semantics.
- 中文：距离计算基于 Haversine，半径语义可测试且可复现。
- EN: Controller-service-repository layering enforces maintainability and test isolation.
- 中文：控制器-服务-仓储分层提升可维护性与测试隔离。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 3. Application Design & API workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“3. 应用设计与 API”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Use versioned API base path (`/api/v1`) to preserve contract stability.
1. 中文：采用版本化路径（`/api/v1`）保障接口兼容。
2. EN: Prefer DTO boundaries to avoid leaking persistence details.
2. 中文：使用 DTO 边界，避免持久化细节泄漏到外部契约。
3. EN: Enforce input validation before distance computation.
3. 中文：在距离计算前执行输入校验。
4. EN: Generate OpenAPI docs for reproducible API review.
4. 中文：生成 OpenAPI 文档以支持可复核接口评审。

## Evidence & Metrics / 证据与指标

- EN: API contract: four primary endpoints with explicit request/response examples.
- 中文：接口契约：四个主端点，包含明确请求/响应示例。
- EN: Correctness: unit and integration tests for location update and nearby search.
- 中文：正确性：位置更新与附近搜索具备单测/集成测试。
- EN: Maintainability: clear service interface boundaries for test doubles and mocking.
- 中文：可维护性：清晰服务接口边界，便于测试替身与 mock。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
Why Haversine instead of straight Euclidean distance?

**Answer:**
Because Earth curvature matters for realistic geospatial distance at city scale and beyond.

### 问题 1（中文）
为什么使用 Haversine 而不是欧氏距离？

**回答：**
因为在城市级及更大范围下，地球曲率会影响实际地理距离，欧氏距离误差不可忽略。

### Q2 (EN)
How do you avoid API design drift?

**Answer:**
Versioned routes, OpenAPI spec, and CI checks against contract assumptions.

### 问题 2（中文）
你如何避免 API 设计与实现逐步偏离？

**回答：**
通过版本化路由、OpenAPI 规范与 CI 契约检查共同防止漂移。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
