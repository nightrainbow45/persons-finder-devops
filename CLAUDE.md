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
- **`config/`** — Cross-cutting Spring configuration: `OpenAPIConfig` (springdoc-openapi-ui 1.7.0, reads `swagger.*` properties), `CorsConfig` (reads `cors.allowed-origins`, default `*`) / 横切 Spring 配置：OpenAPI/Swagger 文档配置、CORS 跨域配置

### PII Protection System / PII 保护系统 (`pii/` package)

A privacy-by-default proxy pipeline that intercepts all outbound LLM calls:
默认隐私保护的代理管道，拦截所有发往 LLM 的出站请求：

1. **`PiiDetector`** — Regex patterns identify names and coordinates / 正则表达式识别人名、坐标
2. **`PiiRedactor`** — Replaces PII with reversible UUID tokens (`<NAME_xxxxx>`, `<COORD_xxxxx>`) / 将 PII 替换为可逆 UUID 令牌
3. **`PiiProxyService`** — HTTP proxy that redacts requests before forwarding to external LLM APIs, then de-tokenizes responses / HTTP 代理，转发前脱敏请求，收到响应后还原令牌
4. **`AuditLogger`** — Writes single-line JSON audit log entries to stdout (`{"type":"PII_AUDIT",...}`) collected by Fluent Bit → CloudWatch / 将单行 JSON 审计日志写入 stdout，由 Fluent Bit 采集发往 CloudWatch

The tokenization map lives in memory per-request, making redaction reversible within a single proxy transaction.
令牌映射表存储于每次请求的内存中，使脱敏在单次代理事务内可逆。

There is also a **Go sidecar** (`pii-redaction-sidecar`) running in the same pod on port 8081. It mirrors the Kotlin regex patterns and provides a second independent PII enforcement layer. Enabled only in prod via `sidecar.enabled: true` in `values-prod.yaml`.
Go sidecar（`pii-redaction-sidecar`）运行在同一 Pod 的 8081 端口，镜像 Kotlin 正则模式，提供第二道独立 PII 过滤层，仅在生产环境通过 `sidecar.enabled: true` 启用。

### DevOps Infrastructure / DevOps 基础设施 (`devops/`)

| Directory / 目录 | Contents / 内容 |
|---|---|
| `devops/docker/` | Two Dockerfiles: `Dockerfile` (gradle:7.6.4-jdk11-focal → eclipse-temurin:11.0.26_4-jre-alpine) · `Dockerfile.sidecar` (golang:1.25-alpine3.21 → alpine:3.21, non-root) / 两个多阶段镜像构建文件 |
| `devops/helm/persons-finder/` | Helm Chart: `values.yaml` (defaults) · `values-dev.yaml` · `values-prod.yaml` (sidecar on, networkpolicy on, HPA on) / Helm Chart 及三套环境参数文件 |
| `devops/helm/persons-finder/templates/` | K8s resource templates: Deployment, Service, Ingress (nginx, Basic Auth), HPA, Secret, NetworkPolicy, RBAC, ServiceAccount, basic-auth-secret / 所有 K8s 资源模板 |
| `devops/terraform/modules/` | Reusable modules: `iam/` · `vpc/` · `eks/` · `ecr/` (+ cosign KMS key) · `secrets-manager/` / 可复用 Terraform 模块 |
| `devops/terraform/environments/prod/` | Prod composition: modules + OIDC provider + Kyverno IRSA role + sidecar ECR policy + cosign KMS policy + `cloudwatch.tf` (log group, metric filters, alarm) / 生产环境组合配置及 Layer 4 可观测性 |
| `devops/terraform/environments/dev/` | Dev composition: modules only (no cloudwatch/Kyverno/cosign) / 开发环境组合配置 |
| `devops/k8s/` | One-time manifests: `cluster-secret-store.yaml` (ESO) · `external-secret.yaml` (syncs OPENAI_API_KEY) · `fluent-bit.yaml` (DaemonSet → CloudWatch) / 一次性应用的 K8s 清单 |
| `devops/kyverno/` | `verify-image-signatures.yaml` — ClusterPolicy Enforce: blocks pods with unsigned images / Kyverno 签名强制策略 |
| `devops/scripts/` | `verify.sh` (17-check health script) · `deploy.sh` · `setup-eks.sh` · `teardown-eks.sh` · `local-test.sh` / 运维脚本 |
| `devops/ci/ci-cd.yml` | Source for `.github/workflows/ci-cd.yml` / CI/CD 流水线源文件 |
| `devops/docs/` | Operational guides: DEPLOYMENT, QUICKSTART, RELEASE_PROCESS, HELM_DEPLOYMENT, DNS_CONFIGURATION, SECURITY_REVIEW, SWAGGER_TROUBLESHOOTING, NETWORK_SECURITY_VERIFICATION / 运维文档 |

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

**`config/` — OpenAPI/CORS Spring integration tests / OpenAPI 与 CORS 集成测试** (requires running app via `@SpringBootTest` / 需要启动完整 Spring 上下文)
- 7 test classes: `OpenAPISpecificationTest`, `OpenAPISpecificationPropertiesTest`, `CorsConfigurationTest`, `SwaggerUIAccessibilityTest`, `ModelExamplesTest`, `HealthCheckIsolationTest`, `EnvironmentConfigTest`
- `OpenAPISpecificationPropertiesTest` — 9 `@Test` methods (Property 1–8, Property 11) using jqwik `Arbitrary` generators for input variation; validates spec completeness, parameter docs, request body schemas, CORS headers, Swagger UI accessibility, health check isolation, model examples
- **Gotcha:** doc comments must not contain `/**` as a substring (Kotlin treats it as a nested comment opener) / Kotlin 文档注释中不能包含 `/**` 子序列（会被解析为嵌套注释导致编译错误）

### Key Helm Chart Defaults / Helm Chart 关键默认值

The sidecar and network policy are **disabled by default** in `values.yaml` and only enabled in `values-prod.yaml`. The main application container always reads `OPENAI_API_KEY` from a K8s Secret referenced via `envFrom.secretRef`. The ECR image repository targets `ap-southeast-2`.

Sidecar 容器和 NetworkPolicy 在 `values.yaml` 中**默认禁用**，仅在 `values-prod.yaml` 中启用。主应用容器始终通过 `envFrom.secretRef` 从 K8s Secret 读取 `OPENAI_API_KEY`。ECR 镜像仓库指向 `ap-southeast-2` 区域。

**Swagger/OpenAPI:** controlled by `swagger.enabled` (default `true`). Title/version/description read from `swagger.title`, `swagger.version`, `swagger.description` in `application.properties` or Helm `values.yaml`. Live UI at `/swagger-ui/index.html`, spec at `/v3/api-docs`. In prod, the Swagger UI is protected by Ingress Basic Auth (nginx annotation `auth-type: basic`, secret in `basic-auth-secret.yaml`).

**Swagger / OpenAPI：** 由 `swagger.enabled` 控制（默认 `true`）。标题/版本/描述从 `swagger.*` 属性读取。Swagger UI 地址 `/swagger-ui/index.html`，规范地址 `/v3/api-docs`。生产环境 Swagger UI 由 Ingress Basic Auth 保护（nginx `auth-type: basic`）。

**CORS:** `cors.allowed-origins` (default `*`). In prod, set to `https://aifindy.digico.cloud` — this enables `allowCredentials` and disables the wildcard. **Important:** `allowCredentials=true` and `allowedOrigins="*"` are mutually exclusive; Spring throws an exception if both are set.

**CORS：** `cors.allowed-origins` 控制跨域（默认 `*`）。生产环境设为 `https://aifindy.digico.cloud`，此时自动关闭通配符并开启 `allowCredentials`。注意两者互斥，同时设置会抛出异常。

### CI/CD Pipeline Logic / CI/CD 流水线逻辑

The GitHub Actions workflow (`.github/workflows/ci-cd.yml`) runs three jobs: **Build and Test** → **Docker Build and Security Scan** → **Deploy to EKS** (push-to-main only).
GitHub Actions 工作流三个 Job：构建测试 → Docker 构建+安全扫描 → 部署到 EKS（仅 main 分支推送触发）。

**Image tag strategy (ECR is IMMUTABLE — tags cannot be overwritten):**
- `git-<sha>` — every push to `main` (e.g. `git-a1b2c3d`)
- `X.Y.Z` semver — release tags `v*.*.*` (e.g. `1.4.2`)
- `pr-N` — pull request builds only
- No floating tags (`latest`, `X.Y`, `X`) — incompatible with ECR IMMUTABLE

**镜像标签策略（ECR IMMUTABLE — 标签不可覆盖）：**
- `git-<sha>`：main 分支每次推送
- `X.Y.Z` semver：release tag 触发
- `pr-N`：PR 构建
- 无浮动标签（latest、X.Y、X）——与 ECR IMMUTABLE 不兼容

**Security pipeline (runs on every build, gates before ECR push):**
1. Trivy scans main image — fails on unfixed CRITICAL/HIGH CVEs (`--exit-code 1`)
2. Trivy scans sidecar image — same gate
3. SBOM generated (CycloneDX format, uploaded as GitHub artifact, 90-day retention)
4. Image pushed to ECR (only if both Trivy gates pass, push to main/tags only)
5. cosign signs image with AWS KMS key (`alias/persons-finder-cosign-prod`, ECC_NIST_P256)
6. cosign attests SBOM provenance to ECR

**Kyverno ClusterPolicy** (`devops/kyverno/verify-image-signatures.yaml`) runs in `Enforce` mode — any pod in `default` namespace using an unsigned image is blocked at admission, even if it bypasses CI entirely.

AWS authentication uses OIDC (no stored credentials), role `github-actions-role`.

### Secret Management / 密钥管理

**Production (ESO — primary method):** External Secrets Operator syncs `OPENAI_API_KEY` automatically from AWS Secrets Manager (`persons-finder/prod/secrets`) into the `persons-finder-secrets` K8s Secret. The ESO `ClusterSecretStore` and `ExternalSecret` are in `devops/k8s/`.

**生产环境（ESO 主方式）：** External Secrets Operator 自动从 AWS Secrets Manager 同步 `OPENAI_API_KEY` 到 K8s Secret `persons-finder-secrets`，配置文件在 `devops/k8s/`。

**Fallback (manual):** If ESO is not installed, create the secret manually:
**回退（手动）：** ESO 未安装时手动创建：

```bash
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=<your-key>
```

The Helm `secret.yaml` template conditionally creates this Secret only when `secret.create: true` in values.
Helm 的 `secret.yaml` 模板仅当 values 中 `secret.create: true` 时才创建该 Secret。

### Local Working Docs / 本地工作文档

`.docs/` is **gitignored** — it holds session notes, design scratch pads, and run logs that are not part of the submission. Do not commit anything from `.docs/`.

`.docs/` 目录已加入 `.gitignore`，存放会话笔记、设计草稿和运行日志，不属于提交内容，不要提交。
