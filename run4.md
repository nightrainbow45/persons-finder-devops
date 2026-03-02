# Session 8 运行记录 / Run Log

> 日期：2026-03-02
> 分支：main
> Session 8 目标：设计并实现 AI Firewall（PII 出站保护）架构，落地 Layer 2 PII Redaction Sidecar

---

## 一、用户指令

### 指令 1
> "阅读 run1.md run2.md run3.md 和 README.md 来了解背景"

### 指令 2
> "帮我拆解一下这部分的需求，找出最佳答案并根据我现有架构到新的 AIfirewall.md 文档"
> （需求原文：The "AI Firewall" Architecture — PII Redaction Sidecar or Gateway logic）

### 指令 3
> "我现在目前的做到了 layer1 是吗，其他 layer 都没做？"

### 指令 4
> "怎么修，比如 lay2"

### 指令 5
> "就用 Go 推荐的那种方式做吧"

### 指令 6
> "要做"（App 侧 Spring Boot 接入 `LLM_PROXY_URL` 环境变量）

### 指令 7
> "commit 这些改动"

### 指令 8
> "更新 scripts 和 AIfirewall"

### 指令 9
> "写 run4.md"

---

## 二、AIfirewall.md 架构设计

### 2.1 需求拆解

README 要求设计"AI Firewall"，防止用户 PII（姓名、坐标）随 LLM 请求离开集群。问题核心：

```
persons-finder API → api.openai.com
                      ↑ 未脱敏时，原始 PII 在此越界
```

**设计决策：4 层纵深防御（Defence in Depth）**

| Layer | 方案 | 实施位置 |
|---|---|---|
| Layer 1 | In-process PiiProxyService（已有） | Spring Boot 应用内 |
| Layer 2 | PII Redaction Sidecar（本次实现） | Pod 内第二容器 |
| Layer 3 | NetworkPolicy 出口白名单 | Kubernetes 内核层 |
| Layer 4 | 审计日志 + 可观测性 | CloudWatch + Grafana |

### 2.2 为什么选 Sidecar 而不是共享 Gateway

| 对比项 | Sidecar per Pod | 共享 Gateway |
|---|---|---|
| token map 存储 | 每 Pod 内存，天然隔离 | 需要 Redis，增加复杂度 |
| 单点故障 | 只影响一个 Pod | Gateway 宕机 = 全服务中断 |
| 延迟 | ~0.1ms（localhost） | ~1-5ms（网络跳） |

### 2.3 各层现状核查结果

读完代码后发现并非"只做了 Layer 1"，而是每层都有骨架但有缺陷：

| Layer | 描述 | 发现的问题 |
|---|---|---|
| Layer 1 | In-process PiiProxyService | ✅ 完整，但未注册为 Spring Bean |
| Layer 2 | Sidecar 容器 | Helm 有骨架，但镜像不存在；ENV 方向配反了 |
| Layer 3 | NetworkPolicy | 模板有，但 egress 规则没有允许 OpenAI，会封锁 LLM 调用 |
| Layer 4 | 可观测性 | 只有 stdout log，无采集链路 |

**Sidecar ENV 方向错误的具体表现：**
```yaml
# 原始（错误）— 把流量代理到主应用（反向代理方向）
TARGET_HOST: localhost
TARGET_PORT: 8080

# 修复后（正确）— 把主应用的 LLM 调用转发到真实 provider（出站代理方向）
LISTEN_PORT: 8081          # sidecar 监听
UPSTREAM_URL: https://api.openai.com  # 转发目标
```

---

## 三、Layer 2 实现过程

### 3.1 Go Sidecar 服务（`sidecar/main.go`）

**技术选型：** Go stdlib only，无外部依赖，单二进制，约 150 行。

**核心逻辑：**

```
收到请求（来自主应用 → localhost:8081）
    │
    ▼
正则扫描 request body
  namePattern:  \b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)+\b  →  <NAME_xxxxxxxx>
  coordPattern: -?\d{1,3}\.\d{1,15}                  →  <COORD_xxxxxxxx>
    │
    ▼
UPSTREAM_URL + 原始 path 拼接目标 URL
转发所有 header（含 Authorization: Bearer sk-...）
    │
    ▼
收到响应，用 tokenMap 逐一还原 token → 原始 PII
    │
    ▼
写 JSON 审计日志到 stdout（含 "layer":"sidecar-layer2" 标识）
返回还原后的响应给主应用
```

**特殊情况处理：**
- Layer 1 已脱敏的请求：sidecar 扫描不到 PII（token 格式不匹配正则），直接透传，无副作用
- Layer 1 被绕过的请求：sidecar 捕获原始 PII，独立完成脱敏和还原

**健康检查端点：** `GET /health` → `{"status":"ok","layer":"pii-sidecar"}`，供 K8s liveness/readiness probe 使用。

### 3.2 Dockerfile.sidecar

多阶段构建，最终镜像 ~12 MB：

```dockerfile
# 构建阶段 / Build stage
FROM golang:1.22.4-alpine3.20 AS builder
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o pii-sidecar .

# 运行时阶段 / Runtime stage
FROM alpine:3.20.6
RUN addgroup -S sidecar && adduser -S sidecar -G sidecar
USER sidecar
EXPOSE 8081
```

### 3.3 Helm 配置修复

**`values.yaml` 新增：**
```yaml
sidecar:
  upstreamUrl: "https://api.openai.com"  # 新增
  port: 8081
```

**`deployment.yaml` 修复：**
```yaml
# sidecar 容器：修正 ENV 方向
- name: LISTEN_PORT
  value: "{{ .Values.sidecar.port }}"
- name: UPSTREAM_URL
  value: "{{ .Values.sidecar.upstreamUrl }}"

# 新增：主容器 ENV（当 sidecar 启用时路由 LLM 流量）
{{- if .Values.sidecar.enabled }}
- name: LLM_PROXY_URL
  value: "http://localhost:{{ .Values.sidecar.port }}"
{{- end }}

# 新增：sidecar 健康探针
livenessProbe:
  httpGet:
    path: /health
    port: 8081
```

### 3.4 Spring Boot 侧接入

**问题：** `PiiProxyService` 是孤立的 POJO，未被 Spring DI 管理，调用方写死 URL。

**三处 Kotlin 改动：**

#### `EnvironmentConfig.kt` — 新增 `llmProxyUrl` 属性
```kotlin
@Value("\${llm.proxy-url:https://api.openai.com}")
val llmProxyUrl: String = "https://api.openai.com"
// LLM_PROXY_URL env var → llm.proxy-url Spring property（Spring Boot 自动转换）
// sidecar 启用时 Helm 注入 "http://localhost:8081"，dev 不注入则直连 OpenAI
```

#### `PiiProxyService.kt` — 新增 `llmBaseUrl` 构造参数 + `callLlm()` 便捷方法
```kotlin
class PiiProxyService(
    val llmBaseUrl: String = "https://api.openai.com",  // 新增，向后兼容
    ...
) {
    fun callLlm(path: String, body: String, headers: Map<String, String> = emptyMap()): ProxyResponse =
        processRequest(ProxyRequest(url = llmBaseUrl.trimEnd('/') + path, body = body, headers = headers))
}
```

#### `config/LlmConfig.kt` — 新建 Spring `@Configuration`
```kotlin
@Configuration
class LlmConfig(private val environmentConfig: EnvironmentConfig) {
    @Bean
    fun piiProxyService(): PiiProxyService =
        PiiProxyService(llmBaseUrl = environmentConfig.llmProxyUrl)
}
// 注入后任何 Service 可用 @Autowired PiiProxyService 调 LLM，自动走正确 URL
```

**流量路由效果：**

| 环境 | sidecar | `LLM_PROXY_URL` | 路由 |
|---|---|---|---|
| dev | disabled | 未注入 | 主应用 → OpenAI（仅 Layer 1） |
| prod | enabled | `http://localhost:8081` | 主应用 → sidecar → OpenAI（Layer 1 + 2） |

### 3.5 CI/CD 新增 sidecar 构建流程

在 `docker-build-and-scan` job 的 cosign attest 步骤之后，追加 5 个 step：

| Step | 说明 |
|---|---|
| Ensure sidecar ECR repo exists | `aws ecr create-repository … || true`（幂等） |
| Build sidecar image | `docker build -f devops/docker/Dockerfile.sidecar` |
| Trivy scan sidecar | CRITICAL/HIGH 门禁，与主镜像一致 |
| Push sidecar to ECR | 仅 main/tags 时推送，tag 与主镜像相同（`git-<sha>`） |
| Sign sidecar with cosign | `cosign sign --key awskms:///...` |

ECR 仓库：`190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/pii-redaction-sidecar`

### 3.6 测试更新

**`SidecarConfigurationTest.kt`（3 个测试名称变更）：**

| 旧测试（错误架构）| 新测试（正确架构） |
|---|---|
| `PROXY_PORT environment variable` | `LISTEN_PORT environment variable` |
| `TARGET_HOST as localhost` | `UPSTREAM_URL pointing to real LLM provider` |
| `TARGET_PORT for main application` | `LLM_PROXY_URL injected into main container` |

**`EnvironmentConfigTest.kt`（新增 2 个 unit test）：**
```kotlin
fun `llmProxyUrl defaults to openai when not specified`()
fun `llmProxyUrl stores sidecar localhost url when provided`()
```

**最终测试结果：** 317 tests，0 failures ✅

---

## 四、Scripts + AIfirewall.md 更新

### 4.1 deploy.sh

新增 sidecar tag 同步逻辑：
```bash
# prod 部署时自动传 --set sidecar.image.tag=<same-tag>
# 确保主应用和 sidecar 来自同一次 CI 构建，tag 完全一致
SIDECAR_TAG_FLAG=""
if [[ "${ENVIRONMENT}" == "prod" ]]; then
  SIDECAR_TAG_FLAG="--set sidecar.image.tag=${IMAGE_TAG}"
fi
```

### 4.2 verify.sh

prod 安全检查区新增 PII Sidecar 检查（在 Kyverno 检查之后、ESO 检查之前）：

```bash
# 1. sidecar 容器 Ready 状态
SIDECAR_READY=$(kubectl get pods -n "${NAMESPACE}" \
  -l "app.kubernetes.io/name=persons-finder" \
  -o jsonpath='{.items[0].status.containerStatuses[?(@.name=="pii-redaction-sidecar")].ready}')
check "Sidecar container (pii-redaction-sidecar) Ready" test "${SIDECAR_READY}" == "true"

# 2. 主容器 LLM_PROXY_URL 注入确认
LLM_PROXY_URL=$(kubectl get pods ... -o jsonpath='{...env[?(@.name=="LLM_PROXY_URL")].value}')
check "LLM_PROXY_URL injected into main container" test -n "${LLM_PROXY_URL}"
```

### 4.3 setup-eks.sh

NEXT STEP 2 说明更新：现在 CI 构建两个镜像（`persons-finder` + `pii-redaction-sidecar`），ECR repo 由 CI 首次 push 时自动创建。

### 4.4 AIfirewall.md

| 更新项 | 变化 |
|---|---|
| Layer 2 状态 | 🔲 骨架 → ✅ 已实现 |
| Sidecar 实现描述 | 设计文字 → 实际 Go 代码逻辑（LISTEN_PORT/UPSTREAM_URL/audit log/health） |
| iptables Option B | 明确标注"未实现，设计中作为对比" |
| Implementation Status 表 | 新增 LlmConfig、Go sidecar、Dockerfile.sidecar、CI sign 行 |

---

## 五、出了哪些问题 + 怎么解决

### 问题 1：编译二进制被 git add 进暂存区

**现象：** `git diff --cached --stat` 显示 `sidecar/pii-sidecar` 二进制（9 MB）被 staged。

**修复：**
```bash
git rm --cached sidecar/pii-sidecar
# 同时在 .gitignore 新增：
sidecar/pii-sidecar
```

### 问题 2：`SidecarConfigurationTest` 3 个测试因 ENV 名变更而失败

**现象：** 改了 deployment.yaml ENV 名后，旧测试检查 `PROXY_PORT`/`TARGET_HOST`/`TARGET_PORT`，全部 false。

**分析：** 旧测试本身验证的是错误的架构（反向代理方向）。更新测试断言为正确的 ENV 名即可，不是回滚代码。

**修复：** 更新 3 个测试为 `LISTEN_PORT`、`UPSTREAM_URL`、`LLM_PROXY_URL`。

### 问题 3：`EnvironmentConfigTest` 单元测试用 `EnvironmentConfig("")` 单参数构造

**现象：** 给 `EnvironmentConfig` 加第二个参数后，已有单元测试 `EnvironmentConfig("")` 编译报错。

**修复：** 给新参数 `llmProxyUrl` 设置 Kotlin 默认值 `= "https://api.openai.com"`，兼容原有所有调用方式。

---

## 六、Git 提交记录

| Commit | 说明 |
|---|---|
| `ed47bb0` | `feat(security): implement Layer 2 PII redaction sidecar` |
| `7dafd1c` | `docs(scripts): update scripts and AIfirewall.md for Layer 2 sidecar` |

---

## 七、当前状态

| 组件 | 状态 |
|---|---|
| Layer 1：PiiProxyService（Spring Bean） | ✅ 已注册（LlmConfig.kt） |
| Layer 2：Go sidecar 服务 | ✅ 已实现（sidecar/main.go） |
| Layer 2：Dockerfile.sidecar | ✅ 已构建（多阶段，alpine，非 root） |
| Layer 2：Helm 配置 | ✅ 已修正（ENV 方向、LLM_PROXY_URL、健康探针） |
| Layer 2：CI/CD 构建 + 签名 | ✅ 已配置（5 个新 step） |
| Layer 3：NetworkPolicy | ⚠️ 模板存在，egress 规则未放行 OpenAI（待修） |
| Layer 4：AuditLogger stdout | ✅ 已有 |
| Layer 4：CloudWatch 采集链路 | 🔲 未配置 |
| AIfirewall.md | ✅ 完整设计文档（4 层架构 + 实现状态） |
| 测试 | ✅ 317 tests，0 failures |

---

## 八、下次对话的起点

- prod 环境运行中（上次 Session 7 重建）
- 本次实现了完整 Layer 2 sidecar，代码已 commit，待 push 触发 CI/CD 验证
- Layer 3 NetworkPolicy 有隐患：egress 规则应放行 `api.openai.com`，否则 prod 启用后 LLM 调用失败
- Layer 4 CloudWatch 采集链路未搭建（Fluent Bit DaemonSet）

**如需重新部署：**
```bash
./devops/scripts/setup-eks.sh prod
./devops/scripts/deploy.sh prod --tag git-<sha>
./devops/scripts/verify.sh prod
```
