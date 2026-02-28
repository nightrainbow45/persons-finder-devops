# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
本文件为 Claude Code 在此代码仓库中工作时提供操作指导。

## Build & Test Commands / 构建与测试命令

```bash
# Build (skip tests) / 构建（跳过测试）
./gradlew clean build -x test

# Run all tests / 运行全部测试
./gradlew test

# Run a single test class / 运行单个测试类
./gradlew test --tests "com.persons.finder.devops.DockerfileBestPracticesTest"

# Run a single test method / 运行单个测试方法
./gradlew test --tests "com.persons.finder.devops.HelmChartStructureTest.helmLintPassesValidation"

# Run the application locally / 本地启动应用
./gradlew bootRun
```

## Architecture Overview / 架构概述

### Application Layer Structure / 应用层结构

The Spring Boot app follows a strict 3-layer architecture at `src/main/kotlin/com/persons/finder/`:
Spring Boot 应用遵循严格三层架构，代码位于 `src/main/kotlin/com/persons/finder/`：

- **`presentation/`** — REST controllers (`PersonController`), base path `/api/v1/persons` / REST 控制器，基础路径为 `/api/v1/persons`
- **`domain/services/`** — Business logic interfaces and implementations (`PersonsServiceImpl`, `LocationsServiceImpl`). Location distance uses the Haversine formula. / 业务逻辑接口及实现类（`PersonsServiceImpl`、`LocationsServiceImpl`），位置距离计算使用 Haversine 公式
- **`data/`** — JPA entities (`Person`, `Location`) backed by H2 in-memory database / JPA 实体（`Person`、`Location`），使用 H2 内存数据库

### PII Protection System / PII 保护系统 (`pii/` package)

A privacy-by-default proxy pipeline that intercepts all outbound LLM calls:
默认隐私保护的代理管道，拦截所有发往 LLM 的出站请求：

1. **`PiiDetector`** — Regex patterns identify names, coordinates, and other PII / 正则表达式识别人名、坐标等个人身份信息
2. **`PiiRedactor`** — Replaces PII with reversible UUID tokens (`<NAME_xxxxx>`, `<COORD_xxxxx>`) / 将 PII 替换为可逆 UUID 令牌
3. **`PiiProxyService`** — HTTP proxy that redacts requests before forwarding to external LLM APIs, then de-tokenizes responses / HTTP 代理，转发前脱敏请求，收到响应后还原令牌
4. **`AuditLogger`** — Writes JSON-structured audit log entries to stdout (for CloudWatch/ELK collection) / 将 JSON 结构化审计日志写入 stdout，供 CloudWatch/ELK 采集

The tokenization map lives in memory per-request, making redaction reversible within a single proxy transaction.
令牌映射表存储于每次请求的内存中，使脱敏在单次代理事务内可逆。

### DevOps Infrastructure / DevOps 基础设施 (`devops/`)

| Directory / 目录 | Contents / 内容 |
|---|---|
| `devops/docker/` | Multi-stage Dockerfile: `gradle:7.6-jdk11` (build) → `eclipse-temurin:11-jre-alpine` (runtime) / 多阶段 Dockerfile：构建阶段 → 轻量运行时镜像 |
| `devops/helm/persons-finder/` | Helm Chart with `values.yaml`, `values-dev.yaml`, `values-prod.yaml` / Helm Chart 及三套环境参数文件 |
| `devops/helm/persons-finder/templates/` | All K8s resource templates (Deployment, Service, Ingress, HPA, Secret, RBAC) / 所有 K8s 资源模板 |
| `devops/terraform/modules/` | Reusable modules: `iam/`, `vpc/`, `eks/`, `ecr/`, `secrets-manager/` / 可复用 Terraform 模块 |
| `devops/terraform/environments/` | `dev/` and `prod/` environment compositions / 开发与生产环境组合配置 |
| `devops/ci/ci-cd.yml` | Source for `.github/workflows/ci-cd.yml` / CI/CD 流水线源文件 |

### Test Categories / 测试类别

Tests live in `src/test/kotlin/com/persons/finder/` and split into two distinct concerns:
测试位于 `src/test/kotlin/com/persons/finder/`，分为两个互相独立的类别：

**`devops/` — Infrastructure validation tests / 基础设施验证测试** (no running app required / 无需启动应用)
- Parse and assert on raw text files: `Dockerfile`, `helm/` YAML, folder structure / 解析并断言原始文件内容：Dockerfile、Helm YAML、目录结构
- `HelmChartStructureTest` invokes `helm lint` and `helm template` as shell commands; requires `helm` on PATH / 调用 `helm lint`/`helm template` shell 命令，需要 PATH 中存在 `helm`
- `ContainerImageDeterminismPropertyTest` builds the Docker image twice and compares content hashes; requires Docker daemon / 两次构建镜像并比较内容哈希，需要 Docker 守护进程运行中

**`pii/` — PII functionality tests / PII 功能测试**
- `PiiRedactionCompletenessPropertyTest` and `AuditLogCompletenessPropertyTest` use **jqwik** property-based testing with 100+ iterations / 使用 jqwik 属性测试框架，每项属性至少执行 100 次随机迭代
- These are unit tests with no external dependencies / 纯单元测试，无任何外部依赖

### Key Helm Chart Defaults / Helm Chart 关键默认值

The sidecar and network policy are **disabled by default** in `values.yaml` and only enabled in `values-prod.yaml`. The main application container always reads `OPENAI_API_KEY` from a K8s Secret referenced via `envFrom.secretRef`. The ECR image repository targets `ap-southeast-2`.

Sidecar 容器和 NetworkPolicy 在 `values.yaml` 中**默认禁用**，仅在 `values-prod.yaml` 中启用。主应用容器始终通过 `envFrom.secretRef` 从 K8s Secret 读取 `OPENAI_API_KEY`。ECR 镜像仓库指向 `ap-southeast-2` 区域。

### CI/CD Pipeline Logic / CI/CD 流水线逻辑

The GitHub Actions workflow (`.github/workflows/ci-cd.yml`) only pushes to ECR when both conditions are true: event is `push` AND ref is `refs/heads/main`. Security scanning with Trivy runs on every build and fails the pipeline on `CRITICAL` or `HIGH` severity findings. AWS authentication uses OIDC (no stored credentials) with role `github-actions-role`.

GitHub Actions 工作流（`.github/workflows/ci-cd.yml`）仅在同时满足两个条件时才推送镜像到 ECR：事件为 `push` 且 ref 为 `refs/heads/main`。每次构建都运行 Trivy 安全扫描，发现 `CRITICAL` 或 `HIGH` 级别漏洞时流水线失败。AWS 认证使用 OIDC（无需存储凭证），IAM 角色名为 `github-actions-role`。

### Secret Management / 密钥管理

The `OPENAI_API_KEY` must be created manually before deploying:
`OPENAI_API_KEY` 必须在部署前手动创建：

```bash
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=<your-key>
```

The Helm `secret.yaml` template conditionally creates this Secret only when `secret.create: true` in values.
Helm 的 `secret.yaml` 模板仅当 values 中 `secret.create: true` 时才创建该 Secret。
