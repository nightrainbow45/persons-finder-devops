# 4. Containerization & Dockerfile / 4. 容器化与 Dockerfile

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英双语 + 面试版），更新日期：2026-03-08。

---

## Document Positioning / 文档定位

- EN: This is a bilingual, interview-ready deep rewrite of the original document.
- 中文：这是原文档的中英对照、面试导向深度重写版。
- EN: The structure prioritizes decision rationale, production evidence, and defendable trade-offs.
- 中文：结构优先呈现决策依据、生产证据与可辩护的取舍逻辑。
- EN: Show how the image build became secure, reproducible, and production-operable.
- 中文：说明镜像构建如何达到安全、可复现、可运维。

## Executive Summary / 执行摘要

- EN: Multi-stage build separates compilation from runtime attack surface.
- 中文：多阶段构建将编译链与运行面分离，降低攻击面。
- EN: Base image versions are pinned to avoid hidden drift from floating tags.
- 中文：基础镜像版本固定，避免浮动标签带来的隐性漂移。
- EN: Runtime is non-root with health checks for orchestration safety.
- 中文：运行时使用非 root，并配置健康检查保障编排可靠性。

## Interview Pitch / 面试速讲

### 30-Second Pitch / 30 秒电梯陈述

- EN: I led the 4. Containerization & Dockerfile workstream and turned it from implementation notes into production-ready decisions with verifiable evidence.
- 中文：我主导了“4. 容器化与 Dockerfile”工作流，将实现说明升级为可验证证据支撑的生产级决策体系。

### STAR (90s) / STAR（90 秒）

| STAR | EN | 中文 |
|---|---|---|
| Situation | AI accelerated delivery, but baseline output was not production-safe by default. | AI 提升了交付速度，但默认输出并不天然满足生产要求。 |
| Task | Build a defendable implementation with clear controls and operational proof. | 构建可辩护实现，具备明确控制与运行证据。 |
| Action | Audited artifacts, fixed high-risk gaps, aligned docs/code/runtime behavior, and verified outcomes in deployment workflows. | 审计制品、修复高风险缺口、对齐文档/代码/运行态行为，并在部署流程中验证结果。 |
| Result | Reduced hidden failure risk and produced interview-ready, evidence-backed engineering narrative. | 降低隐性故障风险，形成可面试复述、证据充分的工程叙述。 |

## Deep Rewrite — Decisions & Trade-offs / 深度重写：关键决策与取舍

1. EN: Use dependency layer caching to accelerate CI without sacrificing determinism.
1. 中文：使用依赖层缓存加速 CI，同时保持可确定性。
2. EN: Patch Alpine packages in build path to reduce OS-level CVE exposure.
2. 中文：在构建流程中升级 Alpine 包，降低 OS 层 CVE 风险。
3. EN: Enforce `.dockerignore` to keep context minimal and secret-safe.
3. 中文：强化 `.dockerignore`，缩小构建上下文并防止机密泄露。
4. EN: Separate app and sidecar Dockerfiles for independent vulnerability posture.
4. 中文：主应用与 sidecar 镜像分离，便于独立漏洞治理。

## Evidence & Metrics / 证据与指标

- EN: Supply chain: image scanning integrated with CI gate policy.
- 中文：供应链：镜像扫描已接入 CI 门禁策略。
- EN: Runtime hardening: non-root, healthcheck, and minimal runtime image are enforced.
- 中文：运行时加固：非 root、健康探针、最小运行镜像均落地。
- EN: Build reliability: deterministic base versions and repeatable build stages.
- 中文：构建可靠性：固定版本与可复用阶段提升可重复性。

## High-Frequency Interview Q&A / 面试高频问答

### Q1 (EN)
What is your strongest Docker hardening decision here?

**Answer:**
Pinning + non-root + scanning gate; together they turn best practice into enforceable policy.

### 问题 1（中文）
这个容器化方案里你最关键的加固决策是什么？

**回答：**
版本固定 + 非 root 运行 + 扫描门禁三者组合，把最佳实践变成可执行策略。

### Q2 (EN)
Why keep sidecar as a separate image pipeline?

**Answer:**
Independent patch velocity and risk isolation for security-sensitive middleware.

### 问题 2（中文）
为什么把 sidecar 保持为独立镜像流水线？

**回答：**
可实现独立补丁节奏与风险隔离，尤其适用于安全敏感中间层。

## Interview Checklist / 面试使用清单

- EN: Lead with outcomes first, then show controls, and finish with runtime evidence.
- 中文：先讲结果，再讲控制措施，最后用运行态证据收尾。
- EN: Name one trade-off and one mitigation in every answer.
- 中文：每个回答至少说出一个取舍和一个补偿措施。
- EN: Use concrete artifacts (`Terraform`, `Helm`, `GitHub Actions`, `Kyverno`, `CloudWatch`) as proof points.
- 中文：用具体制品（`Terraform`、`Helm`、`GitHub Actions`、`Kyverno`、`CloudWatch`）作为证据。
