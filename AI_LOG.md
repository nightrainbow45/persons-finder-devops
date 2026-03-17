# AI War Stories — Persons Finder DevOps / AI 实战记录 — Persons Finder DevOps

> Deep Human Rewrite (Bilingual + Interview Edition), updated on 2026-03-08.
> 深度人工重写（中英对照 + 面试版），更新日期：2026-03-08。

---

## Table of Contents

1. [Document Positioning](#document-positioning)
2. [Executive Summary](#executive-summary)
3. [Artifact Coverage: Dockerfile](#1-dockerfile)
4. [Artifact Coverage: Kubernetes](#2-kubernetes)
5. [Artifact Coverage: CI/CD Pipeline](#3-cicd-pipeline)
6. [Artifact Coverage: PII Protection](#4-pii-protection)
7. [Artifact Coverage: Terraform](#5-terraform)
8. [Artifact Coverage: API Endpoints](#6-api-endpoints)
9. [Artifact Coverage: Actuator & Health Checks](#7-actuator--health-checks)
10. [Interview Pitch](#interview-pitch)

---

## 1. Dockerfile

**Original Prompt / Intent:** Generate a multi-stage Dockerfile for a Spring Boot + JDK 11 app with minimal final image size.

**What Was Generated:** Two-stage build: `gradle:7.6.4-jdk11-focal` builder → `eclipse-temurin:11.0.26_4-jre-alpine` runtime.

**Flaws Identified:**
- Missing non-root user — container ran as root (security issue).
- No explicit `HEALTHCHECK` instruction — Kubernetes liveness probe relied solely on HTTP endpoint.
- `COPY` used `--chown` incorrectly, causing permission errors on some builds.

**Fixes Applied:**
- Add `RUN adduser -D appuser && USER appuser` to restrict privileges.
- Add `HEALTHCHECK CMD wget -qO- http://localhost:8080/actuator/health || exit 1`.
- Correct `--chown=appuser:appuser` flag on the COPY instruction.

---

## 2. Kubernetes

**Original Prompt / Intent:** Generate Kubernetes Deployment, Service, Ingress, HPA, NetworkPolicy, and RBAC manifests for a Java microservice.

**What Was Generated:** Helm chart with `values.yaml`, `values-dev.yaml`, `values-prod.yaml` and full template set.

**Flaws Identified:**
- NetworkPolicy egress rules were missing DNS (port 53) — pods could not resolve hostnames.
- HPA missing `behavior` stanza — rapid scale-down caused request drops.
- Ingress TLS secret name was hardcoded and incorrect for the actual cert.

**Fixes Applied:**
- Add egress rule allowing UDP/TCP port 53 to kube-dns CIDR.
- Add `behavior.scaleDown.stabilizationWindowSeconds: 300` to HPA spec.
- Replace hardcoded TLS secret name with Helm value `{{ .Values.ingress.tls.secretName }}`.

---

## 3. CI/CD Pipeline

**Original Prompt / Intent:** Generate a GitHub Actions workflow for build, Trivy scan, SBOM, ECR push, cosign signing, and Helm deploy to EKS.

**What Was Generated:** Three-job pipeline: Build and Test → Docker Build and Security Scan → Deploy to EKS.

**Flaws Identified:**
- ECR IMMUTABLE tag strategy was incorrect — used `latest` which cannot overwrite existing tags.
- cosign attest step missing `--type cyclonedx` flag, causing attestation format errors.
- OIDC permissions block missing `id-token: write`, breaking AWS authentication.

**Fixes Applied:**
- Replace `latest` with `git-<sha>` tag strategy; add semver tags for release events.
- Add `--type cyclonedx` to the cosign attest command.
- Add `permissions: id-token: write` to the deploy job block.

---

## 4. PII Protection

**Original Prompt / Intent:** Generate a PII proxy pipeline that intercepts outbound LLM calls, redacts names and coordinates, and audit-logs all transactions.

**What Was Generated:** `PiiDetector` → `PiiRedactor` → `PiiProxyService` → `AuditLogger` chain in Kotlin, plus a Go sidecar.

**Flaws Identified:**
- Regex patterns missed multi-word names with hyphens (e.g., "Mary-Jane Smith").
- AuditLogger wrote multi-line JSON, breaking Fluent Bit single-line parsing.
- Go sidecar Dockerfile ran as root — inconsistent with security policy.

**Fixes Applied:**
- Update name regex to include hyphenated patterns: `[A-Z][a-z]+-[A-Z][a-z]+`.
- Change AuditLogger to emit single-line JSON using `JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM`.
- Add `RUN adduser -D appuser && USER appuser` to sidecar Dockerfile.

---

## 5. Terraform

**Original Prompt / Intent:** Generate reusable Terraform modules for VPC, EKS, ECR, IAM, and Secrets Manager targeting AWS ap-southeast-2.

**What Was Generated:** Five modules under `devops/terraform/modules/` with prod and dev environment compositions.

**Flaws Identified:**
- KMS key policy missing `kms:Decrypt` for EKS node role — pods could not read secrets.
- IMDS hop limit set to 1 — pods inside containers could not reach instance metadata.
- ECR lifecycle policy had incorrect filter syntax for `pr-*` tags.

**Fixes Applied:**
- Add `kms:Decrypt` and `kms:DescribeKey` to the EKS node role KMS policy statement.
- Change `http_put_response_hop_limit` from 1 to 2 in the launch template.
- Correct lifecycle policy filter from `tagPrefixList` to `tagPatternList` with `pr-*`.

---

## 6. API Endpoints

**Original Prompt / Intent:** Generate REST API endpoints for person CRUD and location-based proximity search using the Haversine formula.

**What Was Generated:** `PersonController` under `/api/v1/persons` with GET, POST, PUT, DELETE and a `/nearby` endpoint.

**Flaws Identified:**
- `/nearby` endpoint missing input validation — negative radius values caused incorrect results.
- API response missing standard error body format — clients received empty 400 responses.
- OpenAPI spec missing `@Parameter` annotations — Swagger UI showed undocumented query params.

**Fixes Applied:**
- Add `@Min(0)` constraint to the radius parameter and `@Validated` to the controller.
- Add `@ControllerAdvice` handler returning standardized `{"error": "...", "status": N}` body.
- Add `@Parameter(description = "...", required = true)` to all endpoint query parameters.

---

## 7. Actuator & Health Checks

**Original Prompt / Intent:** Expose Spring Boot Actuator health endpoints for Kubernetes liveness and readiness probes.

**What Was Generated:** `management.endpoints.web.exposure.include=health,info` configuration with basic probe setup.

**Flaws Identified:**
- Actuator health endpoint exposed all component details publicly — information leakage risk.
- Readiness probe interval too aggressive (3s) — caused false pod restarts during GC pauses.
- Missing `livenessState` and `readinessState` groups — Kubernetes could not distinguish probe types.

**Fixes Applied:**
- Set `management.endpoint.health.show-details=when-authorized` to restrict detail visibility.
- Change readiness probe `periodSeconds` from 3 to 10 and add `failureThreshold: 3`.
- Add explicit `health.group.liveness.include=livenessState` and `health.group.readiness.include=readinessState,db`.

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
