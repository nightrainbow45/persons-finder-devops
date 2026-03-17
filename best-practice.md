# Best Practice Explanations

This document follows the exact item order from `.docs/AI_LOG.md` for:
- Part 1: Containerization
- Part 2: Kubernetes Manifests (Helm)
- Part 3: NetworkPolicy
- Part 4: CI/CD & Supply Chain Security
- Part 5: Project Runtime Flow Mapping
- Part 6: Terraform Access, Secrets Lifecycle, and Recovery Validation
- Appendix A: Full Conversation Q&A (complete coverage)

---

## Part 1: Containerization / 第一部分：容器化

### 1. Base image versions are fully pinned
- Checklist (EN): `Base image versions are fully pinned — no :latest, no floating minor tags`
- 清单原文（CN）：`基础镜像版本完全固定 - 没有 :latest，没有浮动次要标签`
- Explanation (EN): Always pin exact image versions so builds are reproducible and security scans are stable. Floating tags can silently change behavior between builds.
- 解释（中文）：基础镜像必须固定到明确版本，确保每次构建结果可复现，安全扫描也稳定。浮动标签会在你不知情时变化，导致“同一份代码、不同结果”。

### 2. Multi-stage build: build with full SDK, run on minimal runtime
- Checklist (EN): `Multi-stage build: build stage uses full SDK; runtime stage uses minimal JRE/distroless/alpine`
- 清单原文（CN）：`多阶段构建：构建阶段使用完整的SDK；运行时阶段使用最小化的 JRE/distroless/alpine`
- Explanation (EN): Use a full toolchain image only to compile/package, then copy artifacts into a minimal runtime image. This reduces image size, startup time, and attack surface.
- 解释（中文）：编译阶段使用完整工具链，运行阶段只保留运行必需内容。这样可以显著减小镜像体积、降低攻击面并加快部署。

### 3. Run as non-root before ENTRYPOINT
- Checklist (EN): `Non-root user created and set with USER instruction before ENTRYPOINT`
- 清单原文（CN）：`在 ENTRYPOINT 之前通过 USER 指令切换为非 root 用户`
- Explanation (EN): The application process should not run as root by default. If the app is compromised, non-root execution limits privilege escalation and host impact.
- 解释（中文）：应用进程默认不应以 root 身份运行。即使被攻击，非 root 权限也能显著降低提权和横向破坏风险。

### 4. `.dockerignore` must exclude unnecessary/sensitive files
- Checklist (EN): `.dockerignore present and excludes .git/, build/, src/test/, Terraform state, secrets`
- 清单原文（CN）：`.dockerignore 已存在，并排除了 .git/、build/、src/test/、Terraform state 和机密文件`
- Explanation (EN): Excluding irrelevant files keeps build context small, speeds up builds, and avoids leaking sensitive files into image layers.
- 解释（中文）：排除无关文件能缩小构建上下文、提升构建速度，并防止敏感文件被意外打进镜像层。

### 5. Patch Alpine runtime packages for OS CVEs
- Checklist (EN): `Alpine runtime stage runs apk update && apk upgrade --no-cache to patch OS-level CVEs`
- 清单原文（CN）：`Alpine 运行时阶段运行 apk update && apk upgrade --no-cache 来修复 OS 层 CVE`
- Explanation (EN): Application security is not enough; base OS packages also need patching. Updating packages in runtime stage reduces known OS-level vulnerabilities.
- 解释（中文）：不仅业务代码要安全，基础系统包也要打补丁。运行时阶段升级 Alpine 包能降低已知系统层漏洞风险。

### 6. Add HEALTHCHECK pointing to app health endpoint
- Checklist (EN): `HEALTHCHECK instruction present and points to the application health endpoint`
- 清单原文（CN）：`HEALTHCHECK 指令存在并指向应用健康检查端点`
- Explanation (EN): HEALTHCHECK lets the runtime detect unhealthy containers beyond process liveness. It should target a lightweight endpoint that reflects real service health.
- 解释（中文）：HEALTHCHECK 能让平台判断容器是否真正健康，而不只是进程“还活着”。应指向轻量且能反映服务状态的健康端点。

---

## Concept Notes / 概念补充（位于两部分之间）

### What is a sidecar? / Sidecar 是什么？
- EN: A sidecar is an additional container running in the same Pod as the main app container. They share the same Pod network, so the main app can call the sidecar through `localhost`.
- 中文：Sidecar 是和主应用容器一起运行在同一个 Pod 里的辅助容器。它们共享同一个 Pod 网络，因此主应用可以通过 `localhost` 访问 sidecar。
- EN: Typical purpose is to provide cross-cutting capabilities (proxying, logging, telemetry, security controls) without deeply coupling that logic into business code.
- 中文：它常用于承载横切能力（代理、日志、监控、安全控制），避免把这些逻辑深度耦合进业务代码。

### Readiness, Liveness, HEALTHCHECK (simple view) / 简单理解：Readiness、Liveness、HEALTHCHECK
- EN: In simple terms, the platform periodically sends probe requests (usually HTTP API requests) to health endpoints and checks whether responses are correct and timely.
- 中文：简单说就是平台会定期向健康端点发探测请求（通常是 HTTP API 请求），看是否能及时得到正确响应。
- EN: `readinessProbe` answers: "Can this container receive production traffic now?" If probe fails, traffic is stopped.
- 中文：`readinessProbe` 回答的是“现在能不能接业务流量”。失败时会先摘流，不一定重启。
- EN: `livenessProbe` answers: "Is this container still alive or stuck?" If probe keeps failing, Kubernetes restarts it.
- 中文：`livenessProbe` 回答的是“容器是否还活着、是否卡死”。连续失败会触发 Kubernetes 重启容器。
- EN: Docker `HEALTHCHECK` is similar in spirit at image/runtime level: it periodically checks app health endpoint so unhealthy containers can be detected quickly.
- 中文：Docker 的 `HEALTHCHECK` 也是同类机制，只是发生在镜像/运行时层面：定期检查应用健康端点，尽快发现不健康容器。

### Pod DNS resolution in practice / Pod DNS 解析的实际路径
- EN: `UDP/53` and `TCP/53` are DNS transport rules on port `53`. Most lookups use UDP first, but large/truncated DNS responses can fall back to TCP.
- 中文：`UDP/53` 和 `TCP/53` 是 DNS 在 `53` 端口上的两种传输方式。大多数查询先走 UDP，但响应过大或被截断时会回退到 TCP。
- EN: In this project, this maps to NetworkPolicy egress for application/sidecar pods: they must resolve domains (for example `api.openai.com`) before making outbound HTTPS calls.
- 中文：在本项目里，这对应 NetworkPolicy 的 DNS 出站规则：主应用和 sidecar 在访问外部 HTTPS（例如 `api.openai.com`）前必须先做域名解析。
- EN: External client access usually follows `domain -> DNS -> Ingress/LoadBalancer -> Service -> Pod`; users are not directly targeting Pod IPs.
- 中文：外部访问通常是 `域名 -> DNS -> Ingress/LoadBalancer -> Service -> Pod`，用户一般不会直接访问 Pod IP。
- EN: Ingress forwarding to backend pods typically uses Kubernetes internal service discovery/endpoints, not external DNS resolution at that hop.
- 中文：Ingress 转发到后端 Pod 这一步通常走 Kubernetes 内部服务发现/Endpoints，而不是额外的外部 DNS 解析。
- EN: Pods still often need DNS even for internal communication (Service names like `*.svc.cluster.local`); only direct IP-based calls avoid DNS.
- 中文：Pod 即使做内部通信也常依赖 DNS（如 `*.svc.cluster.local` 服务名）；只有直接使用 IP 通信时才不需要 DNS。

---

## Part 2: Kubernetes Manifests (Helm) / 第二部分：K8s 清单（Helm）

### 1. HPA API version must be `autoscaling/v2`
- Checklist (EN): `HPA uses autoscaling/v2, not deprecated v2beta2 or v2beta1`
- 清单原文（CN）：`HPA 使用 autoscaling/v2，而不是弃用的 v2beta2 或 v2beta1`
- Explanation (EN): Use supported API versions to avoid apply failures and future incompatibility. Deprecated HPA APIs are removed in newer clusters.
- 解释（中文）：应使用受支持的 API 版本，避免升级集群后清单失效。已弃用版本在新集群中可能直接不可用。

### 2. Every container must have both readiness and liveness probes
- Checklist (EN): `Both readinessProbe and livenessProbe defined on every container (including sidecars)`
- 清单原文（CN）：`每个容器（包括 sidecar）上都定义了 readinessProbe 和 livenessProbe`
- Explanation (EN): Readiness controls traffic routing; liveness controls restart behavior. Defining both on all containers prevents silent partial failures.
- 解释（中文）：readiness 决定是否接流量，liveness 决定是否需要重启。主容器和 sidecar 都要配齐，避免“看起来正常但功能已失效”。

### 3. Set resources requests/limits on every container
- Checklist (EN): `resources.requests and resources.limits set on every container`
- 清单原文（CN）：`每个容器都设置 resources.requests 和 resources.limits`
- Explanation (EN): Requests guarantee scheduling baseline; limits cap resource usage. Without them, pods can be evicted, starved, or destabilize node workloads.
- 解释（中文）：requests 用于调度保底，limits 用于限制上限。缺失会导致调度不稳定、资源争抢和节点抖动。

### 4. Ingress must include `pathType` in `networking.k8s.io/v1`
- Checklist (EN): `Ingress pathType field present (Prefix or Exact) — required in networking.k8s.io/v1`
- 清单原文（CN）：`Ingress pathType 字段存在（Prefix 或 Exact）——networking.k8s.io/v1 中需要`
- Explanation (EN): `pathType` makes path matching semantics explicit and is required by the v1 API. Missing it can cause validation errors or ambiguous routing behavior.
- 解释（中文）：`pathType` 明确路径匹配语义，也是 v1 API 的必填项。缺失会导致校验失败或路由行为不确定。

### 5. Tune rolling update values to real node capacity
- Checklist (EN): `Rolling update maxSurge sized against actual node pod capacity — on single small nodes use maxSurge: 0, maxUnavailable: 1 to avoid scheduling deadlock`
- 清单原文（CN）：`滚动更新参数需结合节点真实 Pod 容量设置；单个小节点建议 maxSurge: 0, maxUnavailable: 1，避免调度死锁`
- Explanation (EN): Rolling update strategy must match cluster capacity. On small single-node setups, `maxSurge > 0` can block updates because extra pods cannot be scheduled.
- 解释（中文）：滚动发布参数必须和集群容量匹配。单小节点场景若 `maxSurge > 0`，常会因“起不出额外 Pod”而卡住更新。

### 6. Enforce strict pod/container security context
- Checklist (EN): `podSecurityContext: runAsNonRoot: true, allowPrivilegeEscalation: false, capabilities: drop: [ALL]`
- 清单原文（CN）：`podSecurityContext：runAsNonRoot: true，allowPrivilegeEscalation: false，capabilities: drop: [ALL]`
- Explanation (EN): These settings enforce least privilege: no root execution, no privilege escalation, and no extra Linux capabilities. They reduce blast radius when vulnerabilities are exploited.
- 解释（中文）：这组配置落实最小权限原则：非 root、禁止提权、移除额外 Linux 能力。即使出现漏洞，也能显著降低影响范围。

### 7. Verify sidecar proxy env direction is correct
- Checklist (EN): `Sidecar proxy env direction verified — outbound forward proxy uses LISTEN_PORT + UPSTREAM_URL, not TARGET_HOST + TARGET_PORT`
- 清单原文（CN）：`已验证 Sidecar 代理 env 方向：出站 forward proxy 使用 LISTEN_PORT + UPSTREAM_URL，而不是 TARGET_HOST + TARGET_PORT`
- Explanation (EN): Outbound proxy design means app -> sidecar -> upstream service. Wrong env direction can turn it into reverse-proxy wiring and bypass intended protection.
- 解释（中文）：出站代理的正确链路是“主应用 -> sidecar -> 上游服务”。方向配反会变成反向代理接线，导致预期防护失效。

### 8. Inject `LLM_PROXY_URL` into main container when sidecar is enabled
- Checklist (EN): `Proxy URL env variable (LLM_PROXY_URL) injected into the main container when sidecar is enabled`
- 清单原文（CN）：`启用 sidecar 时将代理 URL 环境变量 (LLM_PROXY_URL) 注入到 main 容器中`
- Explanation (EN): Sidecar running alone is not enough; main app must actively send LLM traffic to it via proxy URL configuration. Otherwise all calls may bypass the sidecar.
- 解释（中文）：仅启动 sidecar 不代表流量会经过它。必须把代理地址注入主容器并由应用读取，否则请求会直接绕过 sidecar。

---

## Part 3: NetworkPolicy / 第三部分：网络策略

### 1. Allow both DNS UDP 53 and TCP 53 for egress
- Checklist (EN): `DNS egress allows both UDP 53 and TCP 53 (large DNS responses fall back to TCP)`
- 清单原文（CN）：`DNS 出站需同时放行 UDP 53 和 TCP 53（大响应会回退到 TCP）`
- Explanation (EN): DNS over UDP is the default, but TCP fallback is required for larger responses or truncation cases. Allowing both avoids intermittent name-resolution failures.
- 解释（中文）：DNS 默认走 UDP，但大响应或截断场景必须回退 TCP。两者都放行才能避免间歇性解析失败。

### 2. Keep internal pod egress path with `podSelector: {}`
- Checklist (EN): `Internal pod egress rule present (podSelector: {}) so same-namespace containers can communicate`
- 清单原文（CN）：`存在内部 Pod 出站规则 (podSelector: {})，以便相同命名空间的容器可以进行通信`
- Explanation (EN): With default-deny egress, missing this rule can break in-namespace traffic paths needed by app components. Explicit internal egress keeps required pod-to-pod communication available.
- 解释（中文）：在默认拒绝出站策略下，缺少该规则会切断命名空间内必要通信。显式放行内部出站可保障应用组件之间的连通性。

### 3. Use HTTPS egress `ipBlock` excluding RFC1918 ranges
- Checklist (EN): `External HTTPS egress uses ipBlock with RFC1918 ranges excluded, not an open 0.0.0.0/0`
- 清单原文（CN）：`外部 HTTPS 出站应使用排除 RFC1918 段的 ipBlock，而非开放式 0.0.0.0/0`
- Explanation (EN): Broad HTTPS egress should avoid allowing private address ranges. Excluding RFC1918 blocks reduces lateral movement risk while preserving outbound internet access.
- 解释（中文）：HTTPS 出站不应无限放开到私网地址。排除 RFC1918 私有网段可以在保留外网访问能力的同时降低横向移动风险。

### 4. Enable VPC CNI policy enforcement switch
- Checklist (EN): `enableNetworkPolicy: "true" set on the VPC CNI addon — without this, NetworkPolicy objects exist but are never enforced`
- 清单原文（CN）：`在 VPC CNI 插件上设置 enableNetworkPolicy: "true" — 否则 NetworkPolicy 对象虽存在，但不会被真正执行`
- Explanation (EN): NetworkPolicy YAML alone is not enough on EKS; VPC CNI enforcement must be enabled. Without this switch, policies are stored in the API but not applied to actual traffic.
- 解释（中文）：在 EKS 上仅有 NetworkPolicy 清单还不够，必须开启 VPC CNI 的策略执行开关。不开启时策略只会“存在于 API 中”，不会真正作用到流量。

---

## Part 4: CI/CD & Supply Chain Security / 第四部分：CI/CD 与供应链安全

### 1. Trivy gate must include `--exit-code 1`
- Checklist (EN): `Trivy scan step includes --exit-code 1 — omitting it means HIGH/CRITICAL CVEs never fail the build`
- 清单原文（CN）：`Trivy 扫描步骤包括 --exit-code 1 — 省略它意味着高/关键 CVE 永远不会使构建失败`
- Explanation (EN): Trivy can print vulnerabilities but still return success unless failure code is explicitly configured. `--exit-code 1` turns scan findings into an enforceable CI gate.
- 解释（中文）：Trivy 默认可能只输出漏洞报告但不让流水线失败。加上 `--exit-code 1` 才能把“扫描结果”变成“发布门禁”。
- Project mapping (EN): Main image and sidecar scan steps both use `--exit-code 1` in `ci-cd.yml`.
- 项目映射（中文）：当前主镜像与 sidecar 的 Trivy 步骤都已经使用 `--exit-code 1`。

### 2. OIDC auth job permissions must include token + repo read
- Checklist (EN): `OIDC authentication job has permissions: { id-token: write, contents: read }`
- 清单原文（CN）：`OIDC 认证作业应声明 permissions: { id-token: write, contents: read }`
- Explanation (EN): `id-token: write` is required to request a short-lived OIDC JWT from GitHub. `contents: read` is required for checkout/repo read operations in the same job.
- 解释（中文）：`id-token: write` 用于向 GitHub 申请 OIDC 临时身份令牌；`contents: read` 用于读取仓库内容（如 checkout）。
- Why it matters (EN): Without `id-token: write`, AWS role assumption via OIDC fails and deployment credentials cannot be issued.
- 为什么重要（中文）：缺少 `id-token: write` 时，OIDC 换取 AWS 临时凭证会失败，后续推镜像/部署都无法进行。

### 3. OIDC trust policy should use `StringLike` and cover all repos
- Checklist (EN): `OIDC trust policy uses StringLike (not StringEquals) and covers all relevant repositories (e.g. both app and app-devops repos)`
- 清单原文（CN）：`OIDC 信任策略使用 StringLike（不是 StringEquals）并涵盖所有相关仓库（例如 app 和 app-devops）`
- Explanation (EN): OIDC trust matching often needs wildcard patterns for branches/workflows/repositories. `StringLike` prevents brittle exact-match failures when multiple repos or refs are valid deployment sources.
- 解释（中文）：OIDC 信任策略经常需要对仓库、分支、ref 做模式匹配。使用 `StringLike` 能避免“精确匹配过窄”导致的 AssumeRole 失败。
- Typical failure mode (EN): One repo can deploy while another valid repo fails with access denied due to missing claim pattern.
- 常见故障（中文）：一个仓库能部署，另一个同属合法来源的仓库却因信任策略不匹配被拒绝。

### 4. `cosign sign` and `cosign attest` must run non-interactively
- Checklist (EN): `cosign sign and cosign attest include --yes flag for non-interactive CI execution`
- 清单原文（CN）：`cosign sign 和 cosign attest 包含用于非交互式 CI 执行的 --yes 标志`
- Explanation (EN): CI has no terminal interaction. Cosign 2.x may ask for confirmation; `--yes` (or environment equivalent) avoids hanging pipelines.
- 解释（中文）：CI 是无人值守环境，不能等待人工确认。cosign 可能出现确认提示，必须用 `--yes`（或等价环境变量）确保流水线不中断。
- Project mapping (EN): Current pipeline uses `COSIGN_YES: "true"` before both sign/attest steps.
- 项目映射（中文）：当前流水线在 sign/attest 步骤均设置了 `COSIGN_YES: "true"`。

### 5. SBOM upload must keep artifacts for audit window
- Checklist (EN): `SBOM upload-artifact step sets retention-days: 90 (default is 0 — artifact deleted immediately)`
- 清单原文（CN）：`SBOM upload-artifact 步骤设置 retention-days: 90（默认值为 0 — 立即删除制品）`
- Explanation (EN): SBOM is valuable only if retained for audit, incident response, and compliance checks. Setting retention avoids losing traceability right after the run.
- 解释（中文）：SBOM 的价值在于可追溯与审计留档。若立即清理，事后就无法做合规核查或漏洞追踪。
- Project mapping (EN): CI and periodic re-scan reports both keep artifacts for 90 days.
- 项目映射（中文）：当前 CI 的 SBOM 与定期重扫报告都设置了 `retention-days: 90`。

### 6. Periodic re-scan should filter by stable tag class
- Checklist (EN): `Periodic re-scan workflow filters to specific tag prefix (e.g. git-*) to avoid scanning all images`
- 清单原文（CN）：`定期重扫工作流应按特定标签前缀（例如 git-*）过滤，避免扫描全部镜像`
- Explanation (EN): Re-scanning every historical image is expensive and noisy. Filtering to recent, policy-relevant tags gives actionable signal with controlled runtime/cost.
- 解释（中文）：全量重扫所有历史镜像成本高且噪音大。按 `git-*` 等发布策略标签筛选，可在成本可控下保持有效风险发现。
- Project mapping (EN): The scheduled workflow fetches recent tags from ECR and filters `^git-` before scanning.
- 项目映射（中文）：当前定时工作流从 ECR 取最近标签并用 `^git-` 过滤后再扫描。

### 7. `verifyImages.imageReferences` must include main + all sidecars
- Checklist (EN): `Both the main image and all sidecar images listed in imageReferences — a missing sidecar is a bypass vector`
- 清单原文（CN）：`imageReferences 必须同时覆盖主镜像和所有 sidecar 镜像；遗漏 sidecar 会形成绕过路径`
- Explanation (EN): Kyverno only verifies images that match `imageReferences`. If a sidecar image is not listed, unsigned/unauthorized sidecar images can still be admitted.
- 解释（中文）：Kyverno 只会校验 `imageReferences` 命中的镜像。若 sidecar 未列入，未签名或未授权的 sidecar 镜像可能绕过策略进入集群。
- Project mapping (EN): Current policy includes both `persons-finder:*` and `pii-redaction-sidecar:*`.
- 项目映射（中文）：当前策略已包含 `persons-finder:*` 与 `pii-redaction-sidecar:*`。

### 8. Document an explicit Audit -> Enforce promotion checklist
- Checklist (EN): `Audit -> Enforce promotion checklist documented in the policy file header`
- 清单原文（CN）：`在策略文件头中记录 Audit -> Enforce 的晋级检查清单`
- Explanation (EN): Promotion from observation mode to blocking mode should follow a written checklist (signing coverage, IRSA/webhook latency, namespace scope, rollback steps). This prevents accidental cluster-wide deploy blocking.
- 解释（中文）：从观察模式升级到强制拦截必须有书面清单（签名覆盖、IRSA/延迟、命名空间范围、回滚步骤）。这样可避免误切 Enforce 导致大面积发布阻断。

### 9. Configure IRSA for Kyverno admission controller before Enforce
- Checklist (EN): `IRSA configured for kyverno-admission-controller ServiceAccount before switching to Enforce mode`
- 清单原文（CN）：`在切换 Enforce 前，必须先为 kyverno-admission-controller ServiceAccount 配置 IRSA`
- Explanation (EN): Kyverno verify webhooks call ECR during admission. Without IRSA, credential fallback paths can be slow enough to exceed webhook timeout and block deployments.
- 解释（中文）：Kyverno 验签 webhook 在准入时要访问 ECR。若缺少 IRSA，凭证回退链路可能变慢并超过 webhook 超时，导致部署被拦截。
- Practical note (EN): `validationFailureAction: Audit` does not protect you from webhook timeout failures; timeout can still deny admission.
- 实践说明（中文）：即使 `validationFailureAction: Audit`，若 webhook 超时仍可能拒绝准入，不是“只记录不阻断”。

### 10. `mutateDigest: false` is reasonable for IMMUTABLE-tag ECR
- Checklist (EN): `mutateDigest: false for ECR repositories with IMMUTABLE tags (rewriting tag refs to digest adds noise, no security benefit)`
- 清单原文（CN）：`对使用 IMMUTABLE tag 的 ECR 仓库建议 mutateDigest: false（将 tag 改写为 digest 仅增加噪音，无额外安全收益）`
- Explanation (EN): In immutable-tag registries, tag-to-image mapping is stable. Auto-rewriting manifests from tag to digest often adds GitOps diff noise without meaningful risk reduction.
- 解释（中文）：在不可变标签仓库中，tag 到镜像的映射是稳定的。自动把 tag 改写成 digest 通常只增加 GitOps 变更噪音，安全收益有限。

### 11. `verifyDigest: false` can be acceptable with IMMUTABLE tags
- Checklist (EN): `verifyDigest: false for ECR IMMUTABLE tags (tag references are as trustworthy as digest references)`
- 清单原文（CN）：`对 ECR IMMUTABLE tag 可设 verifyDigest: false（tag 引用与 digest 引用同等可信）`
- Explanation (EN): If repository tags are strictly immutable, requiring digest-only pod specs is optional. Signature verification still protects image integrity and trust chain.
- 解释（中文）：当仓库标签严格不可变时，不强制 Pod 只能写 digest 也是可行的。签名验证链路仍可保障镜像完整性与来源可信。
- Caveat (EN): If tag mutability is enabled in an environment, revisit this setting and prefer digest enforcement.
- 注意（中文）：若某环境启用了可变 tag，应重新评估并优先启用 digest 强制。

### 12. `.trivyignore` entries must be auditable and removable
- Checklist (EN): `.trivyignore entries include: CVE ID, severity, affected component, exploit condition, and explicit removal condition`
- 清单原文（CN）：`.trivyignore 条目包括：CVE ID、严重性、受影响的组件、利用条件和显式删除条件`
- Explanation (EN): Ignore rules are temporary risk acceptances, not permanent exceptions. Each entry should contain enough context to justify, audit, and safely remove it later.
- 解释（中文）：忽略规则本质是“临时风险接受”，不是永久豁免。每条都应有充分上下文，便于审计、复核和按条件移除。
- Recommended template (EN): `CVE/GHSA` + severity + component/version + why current exploitability is limited + exact remove trigger (date/version/event).
- 推荐模板（中文）：`CVE/GHSA` + 严重性 + 组件/版本 + 当前利用受限原因 + 明确移除触发条件（日期/版本/事件）。

### 13. `cosign sign` should sign digest references, not tags
- Checklist (EN): `cosign sign uses image digest reference (not tag) to avoid MANIFEST_UNKNOWN race condition after docker push`
- 清单原文（CN）：`cosign sign 应使用镜像 digest 引用（非 tag），避免 docker push 后出现 MANIFEST_UNKNOWN 竞态`
- Explanation (EN): Tag resolution can race registry propagation right after push. Signing by digest is deterministic and avoids post-push manifest lookup races.
- 解释（中文）：`docker push` 后按 tag 查询 manifest 可能遇到短暂传播竞态。改用 digest 签名更稳定、可重复，能规避 `MANIFEST_UNKNOWN`。
- Project mapping (EN): Current workflow signs main and sidecar images by digest (`@sha256:...`).
- 项目映射（中文）：当前流水线对主镜像和 sidecar 都使用 digest（`@sha256:...`）签名。

### 14. KMS ARN for cosign can live in workflow env (not Secret)
- Checklist (EN): `KMS ARN for cosign is not stored as a GitHub secret if the PAT lacks secrets: write scope — use a workflow env variable instead (KMS ARNs are not sensitive)`
- 清单原文（CN）：`若 PAT 缺少 secrets: write，不要把 cosign 的 KMS ARN 存为 GitHub Secret；改用 workflow env 变量（KMS ARN 非敏感）`
- Explanation (EN): A KMS ARN is an identifier, not a credential. Access is enforced by IAM permissions/OIDC role assumption, so ARN can be declared in workflow env when secret-write permissions are unavailable.
- 解释（中文）：KMS ARN 是资源标识，不是凭证。真正的访问控制由 IAM/OIDC 权限决定，因此在无法写 GitHub Secret 时可安全放在 workflow `env`。
- Project mapping (EN): `COSIGN_KMS_ARN` is currently declared as workflow `env`.
- 项目映射（中文）：当前 `COSIGN_KMS_ARN` 已在 workflow 的 `env` 中声明。

### 15. Secrets must be injected via `envFrom.secretRef`, never baked in images/values
- Checklist (EN): `OPENAI_API_KEY (and other secrets) injected via envFrom.secretRef; never stored in images or Helm values`
- 清单原文（CN）：`通过 envFrom.secretRef 注入 OPENAI_API_KEY（及其他机密）——绝不写入镜像或 Helm values`
- Explanation (EN): Keep secrets outside container layers and source-controlled values files. Runtime injection from K8s Secret supports safer rotation and reduces leakage risk.
- 解释（中文）：机密应与镜像层和版本库解耦。通过 K8s Secret 运行时注入可降低泄露风险，并支持更安全的轮换。
- Project mapping (EN): Deployment uses `envFrom.secretRef`; production deploy overrides with `secrets.create=false` to avoid inline Helm secret creation.
- 项目映射（中文）：Deployment 使用 `envFrom.secretRef`；生产部署通过 `secrets.create=false` 避免在 Helm values 内联创建机密。

---

## Part 5: Project Runtime Flow Mapping / 第五部分：本项目实链路映射

### 5.1 CI "packaging machine" = GitHub Runner
- EN: In this project, CI jobs run on GitHub-hosted ephemeral Ubuntu machines (`runs-on: ubuntu-latest`). The runner is effectively the build/packaging host.
- 中文：本项目 CI 运行在 GitHub 提供的临时 Ubuntu 机器上（`runs-on: ubuntu-latest`），这就是你说的“打包台/构建机”。

### 5.2 Where Trivy runs and what it scans
- EN: Trivy is installed on the runner via apt repository, then used to scan locally built images (main and sidecar) in CI.
- 中文：Trivy 在 runner 中通过 apt 安装，然后扫描本次 CI 构建出来的主镜像与 sidecar 镜像。
- EN: A separate periodic workflow re-scans ECR images (recent `git-*` tags) to catch newly disclosed CVEs without new code pushes.
- 中文：另有定时工作流会重扫 ECR 中最近 `git-*` 标签镜像，用于发现“代码没变但漏洞库更新”的新风险。

### 5.3 Where `cosign sign`, `cosign attest`, and verification happen
- EN: Signing (`cosign sign`) occurs in CI after image push using digest references. Attestation (`cosign attest`) is done for the main image SBOM.
- 中文：镜像签名（`cosign sign`）发生在 CI 推送后并使用 digest 引用；主镜像 SBOM 通过 `cosign attest` 绑定。
- EN: Signature verification is not done by `cosign verify` in CI; it is enforced at cluster admission by Kyverno `verifyImages`.
- 中文：验签不是在 CI 用 `cosign verify` 执行，而是在集群准入阶段由 Kyverno 的 `verifyImages` 规则执行。

### 5.4 KMS in this chain
- EN: AWS KMS holds the signing key material and performs cryptographic signing operations without exposing private keys in CI filesystems.
- 中文：AWS KMS 负责托管签名密钥并执行签名运算，避免私钥落地到 CI 文件系统。
- EN: Cosign references KMS key ARN (`awskms:///...`) for sign/attest operations.
- 中文：cosign 通过 `awskms:///...` 形式引用 KMS Key ARN 完成签名/证明。

### 5.5 What Kyverno is, and relation to cosign
- EN: Kyverno is a Kubernetes admission policy engine. Cosign signs images; Kyverno verifies signatures before allowing pods.
- 中文：Kyverno 是 Kubernetes 的准入策略引擎。cosign 负责签名，Kyverno 负责在 Pod 创建前验签并决定放行或拒绝。
- EN: Together they form the supply-chain enforcement loop: sign in CI -> verify in cluster.
- 中文：两者组成供应链闭环：CI 签名，集群准入验证。

### 5.6 SBOM in this project
- EN: Trivy generates CycloneDX SBOM (`sbom.cdx.json`), uploads it as artifact, then cosign attests it to the pushed image digest.
- 中文：Trivy 生成 CycloneDX SBOM（`sbom.cdx.json`），上传为制品，再由 cosign 把 SBOM 作为 attestation 绑定到镜像 digest。
- EN: This supports vulnerability response, license/compliance review, and provenance tracking.
- 中文：这为漏洞响应、许可证合规、供应链追溯提供基础数据。

### 5.7 Important current-state observations (from code inspection)
- EN: Kyverno policy currently matches namespace `default`, while Helm deployment targets namespace `persons-finder`; signature policy may not apply to the app namespace unless adjusted.
- 中文：当前 Kyverno 策略匹配的是 `default` 命名空间，但 Helm 部署在 `persons-finder`，若不调整匹配范围，应用命名空间可能不会被验签策略覆盖。
- EN: Sidecar is signed but not attested in current workflow; main image has both sign + SBOM attest.
- 中文：当前 sidecar 有签名但未做 attestation；主镜像则有签名和 SBOM attestation。
- EN: Periodic re-scan workflow currently targets `persons-finder` repository tags; sidecar repository is not included in that scheduled scan job.
- 中文：当前定期重扫只覆盖 `persons-finder` 仓库标签，未纳入 sidecar 仓库。
- EN: Release-tag deployment sets `sidecar.image.tag` from semver, while sidecar build step tags image as `git-<sha>`; release-tag consistency should be checked.
- 中文：发布场景中 Helm 用 semver 作为 `sidecar.image.tag`，但 sidecar 构建默认打 `git-<sha>`，两者一致性需重点校验。

### 5.8 Sidecar operational best-practice (monitoring and health)
- EN: Monitor both main and sidecar independently (metrics, logs, probes, resource usage). Do not assume sidecar health from main-container readiness.
- 中文：主容器和 sidecar 必须独立监控（指标、日志、探针、资源），不能用主容器健康替代 sidecar 健康。
- EN: Prefer cluster-level log/metrics collection by default; use sidecar only when per-pod processing/isolation is required.
- 中文：默认优先集群级采集（如 DaemonSet/Collector），只有确需 Pod 级隔离处理时再引入日志/代理 sidecar。

### 5.9 ECR tag lifecycle in plain language (current project)
- EN: Current practical tag classes are three: `git-<sha>` (mainline CI builds), `pr-<id>` (PR artifacts), and semver release tags (e.g. `1.4.2`).
- 中文：当前实际标签主要有三类：`git-<sha>`（主干构建）、`pr-<id>`（PR 工件）、semver 发布标签（如 `1.4.2`）。
- EN: `git-*` is your day-to-day deploy/debug tag; semver is your formal release contract; `pr-*` is temporary validation output.
- 中文：`git-*` 是日常部署/排障标签；semver 是正式发布契约；`pr-*` 主要用于临时验证。
- EN: Main repository has Terraform lifecycle rules for `pr-*` and `git-*`; semver tags are kept long-term by default. Sidecar repo is CI-created and should be checked for equivalent lifecycle governance.
- 中文：主仓库通过 Terraform 对 `pr-*` 与 `git-*` 有回收策略，semver 默认长期保留；sidecar 仓库由 CI 创建，需确认是否有等价生命周期治理。

---

## Part 6: Terraform Access, Secrets Lifecycle, and Recovery Validation / 第六部分：Terraform 权限、密钥生命周期与重建验证

### 1. Remote state backend must be bootstrapped before `terraform init`
- Checklist (EN): `S3 backend bucket and DynamoDB lock table documented as bootstrap prerequisites before terraform init`
- 清单原文（CN）：`S3 backend bucket 和 DynamoDB lock table 应文档化为 terraform init 之前的引导前置条件`
- Explanation (EN): Terraform cannot initialize an S3 backend if the bucket/table do not exist. S3 stores the state file; DynamoDB stores lock records to prevent concurrent state writes.
- 解释（中文）：若 S3 bucket 和 DynamoDB lock table 未提前创建，`terraform init` 无法成功。S3 保存 state 文件，DynamoDB 保存锁记录，防止并发写坏 state。
- Project mapping (EN): This repo uses bucket `persons-finder-terraform-state` and table `persons-finder-terraform-locks`; prod/dev keys are separated by path.
- 项目映射（中文）：本仓库使用 `persons-finder-terraform-state` 与 `persons-finder-terraform-locks`，并通过 `prod/terraform.tfstate`、`dev/terraform.tfstate` 分环境隔离。

### 2. EKS identity mapping must be paired with Kubernetes RBAC
- Checklist (EN): `EKS aws-auth group mappings always paired with a kubernetes_cluster_role + kubernetes_cluster_role_binding — identity without RBAC grants zero permissions`
- 清单原文（CN）：`EKS aws-auth 组映射始终与 kubernetes_cluster_role + kubernetes_cluster_role_binding 配对 — 仅有身份映射而无 RBAC 绑定时权限为 0`
- Explanation (EN): `aws-auth` only maps IAM identities to Kubernetes users/groups (authentication context). Actual permissions come from RBAC roles/bindings (authorization context).
- 解释（中文）：`aws-auth` 只做 IAM 身份到 Kubernetes 用户/组的映射（认证），真正的操作权限由 RBAC 的角色与绑定决定（授权）。
- Common failure (EN): Identity is recognized but all operations return `forbidden` because no role binding exists.
- 常见故障（中文）：身份能识别，但所有操作都 `forbidden`，原因是未绑定 RBAC 权限。

### 3. Helm deployer requires `secrets` CRUD
- Checklist (EN): `ClusterRole for Helm deployer includes secrets CRUD (Helm stores release state as K8s Secrets)`
- 清单原文（CN）：`Helm deployer 的 ClusterRole 需包含 secrets CRUD（Helm 将发布状态存储为 K8s Secrets）`
- Explanation (EN): Helm v3 stores release records as Secrets (`sh.helm.release.v1.*`). Without create/read/update/delete on `secrets`, Helm install/upgrade/rollback can fail.
- 解释（中文）：Helm v3 用 K8s Secret 保存发布状态（`sh.helm.release.v1.*`）。若无 `secrets` 的增删改查权限，安装/升级/回滚都会失败。
- Project mapping (EN): The deployer ClusterRole in this repo explicitly includes `secrets` permissions.
- 项目映射（中文）：本仓库 deployer 的 ClusterRole 已显式包含 `secrets` 权限。

### 4. Permission model layering: OIDC, aws-auth, RBAC, IRSA, IMDS
- EN: OIDC (CI side) grants short-lived AWS credentials; `aws-auth` maps IAM identities into Kubernetes groups; RBAC grants Kubernetes API permissions.
- 中文：OIDC（CI 侧）用于换取 AWS 临时凭证；`aws-auth` 把 IAM 身份映射到 K8s 组；RBAC 负责授予 K8s API 操作权限。
- EN: For pod-to-AWS access, IRSA is preferred (pod-scoped role). IMDS is node metadata/credential service and often a fallback path if IRSA is missing.
- 中文：Pod 访问 AWS 时优先使用 IRSA（Pod 级最小权限）；IMDS 是节点元数据/凭证服务，常作为未配置 IRSA 时的回退路径。
- EN: In short: OIDC = federated authentication, RBAC = Kubernetes authorization, IRSA = pod AWS authorization, IMDS = node credential source.
- 中文：简化记忆：OIDC 管身份联邦，RBAC 管 K8s 授权，IRSA 管 Pod 的 AWS 权限，IMDS 是节点凭证来源。

### 5. Secrets flow in this project: AWS -> ESO -> K8s Secret -> Pod env
- EN: AWS Secrets Manager is the source of truth for `OPENAI_API_KEY`; External Secrets Operator (ESO) syncs it to K8s Secret `persons-finder-secrets`; Deployment injects it via `envFrom.secretRef`.
- 中文：`OPENAI_API_KEY` 的真源在 AWS Secrets Manager；ESO 同步到 K8s Secret `persons-finder-secrets`；Deployment 通过 `envFrom.secretRef` 注入 Pod 环境变量。
- EN: Application reads it via `openai.api-key=${OPENAI_API_KEY:}`.
- 中文：应用通过 `openai.api-key=${OPENAI_API_KEY:}` 读取该环境变量。
- Why ESO exists (EN): Kubernetes does not natively auto-sync AWS Secrets Manager values into Secret objects; ESO provides this reconciliation controller.
- 为什么需要 ESO（中文）：Kubernetes 本身不会自动把 AWS Secrets Manager 同步成 K8s Secret，ESO 提供了这个同步控制器能力。
- Best-practice note (EN): For production, central secret source + automatic sync + IAM audit trail is generally preferable to manual inline secrets in Helm values.
- 最佳实践说明（中文）：生产环境通常推荐“集中密钥源 + 自动同步 + IAM 审计链”，优于在 Helm values 中手工维护明文密钥。

### 6. IMDS hop limit setting on EKS node launch template
- Checklist (EN): `EKS Launch Template sets http_put_response_hop_limit = 2 so pods can reach EC2 IMDS (ESO, IRSA)`
- 清单原文（CN）：`EKS 启动模板设置 http_put_response_hop_limit = 2，以便 Pod 可以到达 EC2 IMDS（ESO、IRSA）`
- Explanation (EN): `http_put_response_hop_limit` controls IMDSv2 response reachability. `1` generally restricts to node-only; `2` allows pod network namespace hops to IMDS.
- 解释（中文）：`http_put_response_hop_limit` 控制 IMDSv2 响应可达跳数。`1` 通常只允许节点本机，`2` 可覆盖 Pod 网络命名空间跳转。
- Security note (EN): This is often needed for fallback compatibility, but pod access should still prefer IRSA over broad IMDS dependence.
- 安全说明（中文）：该配置常用于兼容回退链路，但长期仍应优先 IRSA，避免广泛依赖 IMDS。

### 7. After `destroy + apply`, asymmetric KMS keys require pipeline/policy refresh
- Checklist (EN): `After terraform destroy + apply, KMS asymmetric keys get new ARNs and new public keys — Kyverno policy and CI workflow must be updated before deploying, or Enforce mode blocks all pods`
- 清单原文（CN）：`在 terraform destroy + apply 之后，KMS 非对称密钥将获得新的 ARN 和新的公钥；Kyverno 策略和 CI 工作流必须在部署前更新，否则 Enforce 模式会阻止 Pod`
- Explanation (EN): Recreated KMS signing keys produce new ARN/public-key pairs. CI signing must reference the new ARN, and Kyverno verify policy must use the matching new public key.
- 解释（中文）：基础设施重建后，KMS 签名密钥会变成新的 ARN/公钥对。CI 签名引用和 Kyverno 验签公钥必须同步更新并保持匹配。
- Failure mode (EN): old ARN -> CI sign fails; old public key -> Kyverno verify fails; in Enforce mode, pod admission is denied.
- 失败模式（中文）：旧 ARN 会导致 CI 签名失败；旧公钥会导致 Kyverno 验签失败；Enforce 模式下 Pod 准入会被拒绝。

### 8. Idempotency must be proven with at least one full rebuild cycle
- Checklist (EN): `Infrastructure idempotency tested with at least one full destroy + apply cycle, not just incremental applies`
- 清单原文（CN）：`基础设施幂等性至少要通过一次完整 destroy + apply 验证，而不只是增量 apply`
- Explanation (EN): Incremental applies can hide missing bootstrap assumptions and residual-state dependencies. A full teardown/rebuild validates true recoverability and deterministic provisioning.
- 解释（中文）：仅做增量 apply 可能掩盖“依赖历史残留”的问题。完整销毁再重建才能验证从零恢复能力与真正幂等性。
- Operational value (EN): This test catches backend/bootstrap gaps, stale key references, and ordering assumptions before real incidents.
- 运维价值（中文）：该验证能在真实故障前暴露后端引导缺失、密钥引用陈旧、资源创建顺序依赖等问题。

### 9. Why audit matters for privacy/security governance
- EN: Auditability answers who accessed what, when, and why. It supports incident response, compliance evidence, and accountability for sensitive data handling.
- 中文：审计能力回答“谁在何时因何访问了什么数据”，用于事故响应、合规举证和敏感数据问责。
- EN: In NZ context, legal wording is typically "personal information" rather than "PII"; financial-sector controls commonly require traceability and timely breach handling.
- 中文：在新西兰语境中，法律术语更常用“personal information”（而非 PII）；金融场景通常要求可追溯与及时处理隐私事件。
- Note (EN): This document is technical guidance, not legal advice; verify obligations with your compliance/legal team for production policy decisions.
- 说明（中文）：本文件仅提供技术实践说明，不构成法律意见；生产合规要求请由法务/合规团队最终确认。

---

## Appendix A: Full Conversation Q&A / 附录 A：完整问答（不省略版）

> Note / 说明  
> EN: This appendix captures all technical topics discussed in the conversation in chronological order, with normalized phrasing for readability.  
> 中文：本附录按对话时间顺序覆盖所有技术主题，做了语言规范化但不删除信息点。

### A1. "Multi-stage build" means what?
- Q (CN): 多阶段构建是什么意思？
- A (CN): 一个 Dockerfile 可有多个 `FROM`。前一阶段用完整 SDK/JDK 编译，后一阶段只放运行时最小依赖（JRE/distroless/alpine），从而缩小镜像、降低攻击面。
- A (EN): Multi-stage means separating build and runtime images: heavy SDK for compile, minimal runtime for execution.

### A2. What is SDK?
- Q (CN): SDK 是什么？
- A (CN): SDK（Software Development Kit）是开发工具包，包含编译器、库、调试工具、文档等。Java 场景里构建常用 JDK，运行常用 JRE。
- A (EN): SDK is the full development toolkit; runtime usually requires a smaller subset.

### A3. Why run as non-root before ENTRYPOINT?
- Q (CN): 为什么要非 root 用户运行？
- A (CN): 最小权限原则。应用被攻破时，非 root 能降低提权和横向破坏风险，也更符合安全基线与合规要求。
- A (EN): Non-root execution reduces blast radius and aligns with least-privilege policy.

### A4. What does Alpine `apk update && apk upgrade --no-cache` mean?
- Q (CN): 这句的作用是什么？
- A (CN): 在运行时镜像阶段升级系统包，修复 OS 层 CVE。不是只扫应用代码，基础镜像组件也需要补丁。
- A (EN): Update runtime OS packages to patch known vulnerabilities.

### A5. What is "OS-level CVE"?
- Q (CN): OS 层漏洞 CVE 是什么？
- A (CN): 指操作系统和系统库的公开漏洞编号（CVE），例如 openssl、libc、busybox 等组件漏洞。
- A (EN): CVEs can exist in base OS packages, not only app dependencies.

### A6. Is Alpine runtime image always the first base image?
- Q (CN): Alpine 是最开始的基础镜像吗？
- A (CN): 不一定。多阶段构建里第一个通常是 build image，runtime image 常在后面的 `FROM`。
- A (EN): Runtime base image is often the last stage, not the first.

### A7. Why can Dockerfile have multiple base images?
- Q (CN): 为什么会有多个 `FROM`？
- A (CN): 为了分离构建与运行环境、减小镜像体积、提升安全和部署效率。
- A (EN): Multiple `FROM` stages optimize build/runtime responsibilities.

### A8. What does HEALTHCHECK point-to-endpoint mean?
- Q (CN): HEALTHCHECK 指向健康端点是什么意思？
- A (CN): 容器平台会定期调用健康端点判断容器是否健康，不只是判断进程是否存在。
- A (EN): HEALTHCHECK actively verifies service health endpoint behavior.

### A9. How is health endpoint usually implemented?
- Q (CN): 一般怎样做端点 healthcheck？
- A (CN): 提供轻量无副作用端点（如 `/health`），Docker/K8s 定期探测。Readiness 与 Liveness 语义分离。
- A (EN): Use lightweight endpoints and separate readiness from liveness concerns.

### A10. Liveness vs Readiness?
- Q (CN): 它们是什么，是不是 API 探测？
- A (CN): 是。`readiness` 决定是否接流量；`liveness` 决定是否重启。两者都通过探针定期请求端点来判断。
- A (EN): Both are periodic probes; readiness controls routing, liveness controls restart.

### A11. Ingress `pathType` means what and why required?
- Q (CN): `pathType` 是什么，为什么必须写？
- A (CN): 路径匹配语义（`Prefix`/`Exact`）。在 `networking.k8s.io/v1` 中是必填，缺失会导致校验失败或匹配歧义。
- A (EN): `pathType` is required and defines exact path matching behavior.

### A12. Rolling update capacity settings mean what?
- Q (CN): `maxSurge` / `maxUnavailable` 那句什么意思？
- A (CN): 参数需按节点真实容量设置。单小节点常用 `maxSurge: 0, maxUnavailable: 1`，避免因无额外资源导致更新卡死。
- A (EN): Tune rollout parameters to avoid scheduling deadlocks.

### A13. What is rolling update and when triggered?
- Q (CN): 滚动更新是什么，什么时候发生？
- A (CN): Pod 分批替换旧版本为新版本；常见触发是更新镜像 tag/digest 或 `spec.template` 变化。
- A (EN): Rolling updates occur on pod-template changes, especially new image deployments.

### A14. `podSecurityContext` + sidecar env + `LLM_PROXY_URL` line means what?
- Q (CN): 这组条目是什么意思？
- A (CN): 前者是最小权限安全加固；后两者是 sidecar 代理接线方向与主容器流量注入是否正确。
- A (EN): Security hardening + correct sidecar forward-proxy wiring + main-container proxy injection.

### A15. What is sidecar and how does it work?
- Q (CN): sidecar 干什么、原理是什么？
- A (CN): sidecar 与主容器同 Pod 运行，共享网络，主容器通过 localhost 调用 sidecar，再由 sidecar 转发/治理流量。
- A (EN): Sidecar provides cross-cutting capabilities with shared pod network locality.

### A16. Does one Pod run two apps/images?
- Q (CN): 是否一个 Pod 两个镜像一起跑？
- A (CN): 是。主容器 + sidecar 同时运行。是否“全部流量都经 sidecar”取决于配置，不是天然强制。
- A (EN): Multi-container pod is normal; traffic path depends on routing config.

### A17. Monitoring best practice with sidecar?
- Q (CN): 监控时是否连 sidecar，最佳实践是什么？
- A (CN): 主容器与 sidecar 分开监控；探针、指标、日志和资源都要分别观测；集群级采集优先，按需引入 sidecar。
- A (EN): Observe both containers independently; avoid blind spots from shared health assumptions.

### A18. DNS egress UDP/TCP 53 line means what?
- Q (CN): 为什么要同时放行 UDP/53 + TCP/53？
- A (CN): DNS 默认 UDP，响应大或截断会回退 TCP。只放 UDP 会导致间歇性解析失败。
- A (EN): TCP fallback is required for some DNS responses.

### A19. What are UDP/53 and TCP/53 in this project?
- Q (CN): 它们是什么、作用是什么、项目里是哪一块？
- A (CN): 是 DNS 端口 53 的两种传输协议；在本项目对应 NetworkPolicy 的 DNS 出站规则，服务于应用/sidecar 对外域名解析（如 `api.openai.com`）。
- A (EN): They are DNS transport rules used by pod egress resolution.

### A20. Is DNS rule only for `api.openai.com`?
- Q (CN): 是否只是为了 OpenAI？
- A (CN): 不是。凡是域名访问都依赖 DNS，包括 OpenAI、ECR、STS 及集群内 Service 名称解析。
- A (EN): It is a general DNS requirement, not OpenAI-only.

### A21. External access path and DNS dependency?
- Q (CN): 外部访问 API 是否直接访问 Pod，是否也需 DNS？
- A (CN): 外部通常访问域名 -> DNS -> Ingress/LB -> Service -> Pod，不直接打 Pod IP。
- A (EN): DNS is used at domain entry; ingress forwards internally to services/pods.

### A22. Does Ingress->Pod forwarding need DNS?
- Q (CN): ingress 发到 Pod 时还需要 DNS 吗？
- A (CN): 该跳通常走 K8s 内部 service/endpoints 路由，不依赖外部 DNS。
- A (EN): Internal routing usually avoids external DNS at that hop.

### A23. Do most Pods not need DNS?
- Q (CN): Pod 大多数不需要 DNS 吗？
- A (CN): 不准确。Pod 只要访问域名（外部域名或内部 Service 名）就需要 DNS；仅直连 IP 不需要。
- A (EN): Most pods still need DNS for service discovery.

### A24. External HTTPS egress with RFC1918 exclusion means what?
- Q (CN): 这句是什么意思？
- A (CN): 允许 HTTPS 出网但排除私网地址段，降低横向移动风险，避免 `0.0.0.0/0` 全放开。
- A (EN): Allow internet HTTPS while denying private-range lateral paths.

### A25. `enableNetworkPolicy: "true"` on VPC CNI means what?
- Q (CN): 这句什么意思？
- A (CN): 是 EKS VPC CNI 的策略执行开关。不开启时 NetworkPolicy 资源对象存在，但流量层面不执行。
- A (EN): Policy objects without enforcement switch are effectively non-operative.

### A26. What is VPC CNI?
- Q (CN): VPC CNI 全称和作用是什么？
- A (CN): Amazon VPC Container Network Interface。负责 Pod 网络接入、IP 分配与（启用后）网络策略执行路径。
- A (EN): EKS networking plugin bridging pods into VPC networking.

### A27. What does "run Trivy in CI" mean?
- Q (CN): 在 CI 跑 Trivy 是什么？
- A (CN): 在流水线自动构建后执行 Trivy，发现高危漏洞时直接失败，阻断发布。
- A (EN): Security scan is part of build gate, not a manual post-check.

### A28. Where is Trivy installed?
- Q (CN): Trivy 装在哪里？
- A (CN): 典型是在 CI runner 临时安装；也可本地装或使用 Trivy 容器执行。
- A (EN): Most commonly installed in ephemeral CI runner environments.

### A29. How to install/run Trivy in CI?
- Q (CN): 详细步骤，是否必须拉镜像安装？
- A (CN): 可选三种：二进制安装、官方 GitHub Action、Docker 容器方式。只有容器方式需要拉 Trivy 镜像。
- A (EN): Image pull is optional depending on installation mode.

### A30. How is this project currently handling Trivy in CI?
- Q (CN): 本项目 CICD 现在怎么处理？
- A (CN): 当前通过 apt 安装 Trivy，主镜像与 sidecar 都有 CRITICAL/HIGH gate 且 `--exit-code 1`，并有定时重扫 workflow。
- A (EN): Main and sidecar are both gated; periodic ECR re-scan is also configured.

### A31. Where does CI process run? Is runner the build machine?
- Q (CN): CI 的“打包台”在哪里？
- A (CN): 在 GitHub-hosted runner 上，runner 就是本次流水线执行环境和打包机。
- A (EN): Runner is the actual ephemeral execution host for CI.

### A32. What is OIDC and checklist meaning?
- Q (CN): OIDC 是什么，这两句是什么意思？
- A (CN): OIDC 让 CI 用短期身份令牌换云上临时凭证，避免长期 AK/SK。`id-token: write` 是取 token 必需；信任策略需 `StringLike` 覆盖真实仓库/分支模式。
- A (EN): OIDC enables short-lived federated auth; permissions and trust patterns must align.

### A33. What is cosign sign/attest and `--yes` requirement?
- Q (CN): 机制是什么，这句 checklist 什么意思？
- A (CN): `sign` 对镜像 digest 签名，`attest` 对 SBOM/声明签名绑定。CI 非交互必须 `--yes`（或 `COSIGN_YES=true`）。
- A (EN): Sign image integrity; attest metadata integrity; run non-interactively in CI.

### A34. More detailed cosign example?
- Q (CN): 还是没懂，要例子。
- A (CN): 用 `awskms:///arn...` 作为 key 对 `image@sha256:...` 签名；再对 `sbom.cdx.json` 做 `--type cyclonedx` attestation。
- A (EN): Digest-based signing + SBOM attestation is the recommended pattern.

### A35. In this repo, where sign/attest/verify happen? KMS and Kyverno relation?
- Q (CN): 具体在哪一步签名、证明、验签？KMS/Kyverno 是什么关系？
- A (CN): CI 中执行 sign/attest；集群准入由 Kyverno verifyImages 验签；KMS 托管私钥，Kyverno用对应公钥做验证。它们是“签名方 + 验签方”协作关系。
- A (EN): CI signs with KMS-backed key; Kyverno enforces verification at admission.

### A36. What is SBOM?
- Q (CN): SBOM 是什么？
- A (CN): Software Bill of Materials，记录镜像/软件包含的组件与版本，用于审计、漏洞追踪、合规。
- A (EN): A component inventory for security/compliance/provenance.

### A37. Does Trivy also generate SBOM?
- Q (CN): Trivy 除了漏洞扫描也能生成 SBOM 吗？
- A (CN): 能。项目里用 Trivy 生成 CycloneDX SBOM，再由 cosign attest 绑定镜像。
- A (EN): Yes, SBOM generation is part of the Trivy workflow.

### A38. What else can Trivy do and best practices?
- Q (CN): Trivy 还能做什么，最佳实践？
- A (CN): 除漏洞外还能做 misconfig/secret/SBOM；最佳实践包括版本固定、双阶段门禁、主镜像+sidecar都扫、定期重扫、谨慎管理 `.trivyignore`。
- A (EN): Use Trivy as a layered control, not only a one-time CVE scanner.

### A39. What does SBOM artifact retention mean?
- Q (CN): `retention-days: 90` 那句是什么意思？
- A (CN): 指 SBOM 产物至少保留 90 天用于追溯与审计；不保留会丢失合规证据链。
- A (EN): Artifact retention preserves auditability and incident response evidence.

### A40. What does periodic re-scan tag filtering mean?
- Q (CN): 这里扫描的是哪里、谁来扫、扫描范围是否 ECR？
- A (CN): 由 GitHub runner 定时执行，从 ECR 拉取目标仓库最近 `git-*` 标签镜像并用 Trivy 扫描。当前工作流主要覆盖 `persons-finder` 仓库。
- A (EN): Scheduled runner-driven ECR image scans are filtered to recent policy-relevant tags.

### A41. What does S3 backend + DynamoDB lock prerequisite mean?
- Q (CN): 这句话是什么意思？S3 和 DynamoDB 分别存什么、作用是什么？
- A (CN): S3 存 Terraform state 文件；DynamoDB 存 state 锁记录（`LockID`）。每次 `plan/apply` 会先加锁，再读写 S3 state，最后解锁，防止并发冲突。
- A (EN): S3 stores state; DynamoDB stores locks. Plan/apply coordinate through lock-then-read/write-unlock flow.

### A42. What does aws-auth + ClusterRole/Binding pairing mean?
- Q (CN): 这句话究竟在干什么？
- A (CN): 强制形成“认证 + 授权闭环”：`aws-auth` 只映射身份到组，`ClusterRole/Binding` 才授予操作权限。只做映射不做 RBAC 时权限为 0。
- A (EN): Identity mapping without RBAC authorization results in no effective permissions.

### A43. What are RBAC and OIDC, and how do they differ?
- Q (CN): 它们都像授权，分别是谁授权给谁？
- A (CN): OIDC 用于身份联邦（如 GitHub Actions 向 AWS 申请临时凭证）；RBAC 用于 Kubernetes 内部资源权限控制（谁能对哪些资源执行哪些动作）。
- A (EN): OIDC is federated authentication to cloud roles; RBAC is Kubernetes API authorization.

### A44. What is a "subject" operating in Kubernetes?
- Q (CN): 授权某个主体在 K8s 里操作是什么意思？主体是谁？
- A (CN): 主体可为人、CI 机器人、或 ServiceAccount。授权后才能部署、扩缩容、查看日志、运行控制器等。
- A (EN): Subjects are users, automation identities, or service accounts requiring scoped API permissions.

### A45. Is this like AWS user groups?
- Q (CN): 是否类似 AWS 用户组？
- A (CN): 可类比但不完全相同。EKS 中常见是 IAM 身份经 `aws-auth` 映射到 K8s group 字符串，再由 RBAC 赋权。
- A (EN): Similar conceptually, but implemented as IAM-to-K8s-group mapping plus RBAC.

### A46. Is OIDC for temporary permissions?
- Q (CN): OIDC 是给临时权限吗？
- A (CN): 是。OIDC + AssumeRole 产生短期 AWS 凭证，过期自动失效，避免长期密钥驻留。
- A (EN): Yes, it enables short-lived cloud credentials.

### A47. Is OIDC just a GitHub Actions feature?
- Q (CN): OIDC 是 GitHub Actions 独有吗？
- A (CN): 不是。OIDC 是通用协议；GitHub Actions 只是支持该协议并用于云身份联邦的一个实现方。
- A (EN): OIDC is a standard protocol, not GitHub-specific.

### A48. Why must Helm deployer include `secrets` CRUD?
- Q (CN): 这句是什么意思？
- A (CN): Helm v3 把 release 状态存在 K8s Secret。若 deployer 无 `secrets` CRUD，Helm 安装/升级会因 RBAC `forbidden` 失败。
- A (EN): Helm needs secret CRUD to persist and update release state.

### A49. EKS Secret vs AWS Secrets Manager in this project
- Q (CN): 两者分别存什么？如何工作？
- A (CN): AWS Secrets Manager 存源密钥（如 `persons-finder/prod/openai-api-key`）；ESO 同步到 K8s Secret（`persons-finder-secrets`）；Deployment 注入 Pod 环境变量供应用读取。
- A (EN): Source secret in AWS -> synchronized K8s secret -> pod environment injection.

### A50. Why use ESO; can AWS Secret go directly to K8s Secret?
- Q (CN): ExternalSecret 为什么存在？不能直接同步吗？
- A (CN): Kubernetes 原生不会自动把 AWS Secret 变成 K8s Secret；ESO 就是这个同步控制器。当前架构本质上已实现“直接同步”，但通过 ESO 完成。
- A (EN): ESO is the reconciliation bridge that performs the sync.

### A51. Is ESO best practice?
- Q (CN): 这是最佳实践吗？
- A (CN): 在 EKS + Helm 生产场景中是主流实践之一，优势是集中密钥源、自动同步、IAM 审计、轮换流程标准化。
- A (EN): Yes, it is a common production-grade pattern in Kubernetes on AWS.

### A52. Why not keep everything only in K8s Secret?
- Q (CN): 为什么不直接只放 EKS Secret？
- A (CN): 可以，但在生产治理上通常不如“Secrets Manager 真源 + ESO 同步”稳健。后者在审计、轮换、权限隔离和一致性方面更好。
- A (EN): K8s-only secrets are possible, but centralized secret governance is stronger for production.

### A53. Why audit and what is NZ "PII" context?
- Q (CN): 审计为了什么？NZ 银行 PII 指什么？
- A (CN): 审计用于追踪访问行为、支撑事件响应与合规举证。NZ 法律语境更常用“personal information”而非 PII，金融机构通常要求可追溯与隐私事件处置能力。
- A (EN): Auditability supports accountability and incident response; NZ legal framing centers on personal information.

### A54. What is KMS in this architecture?
- Q (CN): KMS 是什么？
- A (CN): AWS KMS 是密钥管理服务。项目中用于 cosign 签名密钥托管与 Secrets Manager 加密密钥，不直接存业务数据。
- A (EN): KMS manages cryptographic keys for signing and encryption operations.

### A55. What does `http_put_response_hop_limit = 2` mean?
- Q (CN): 这句是什么意思？
- A (CN): 这是 EC2 IMDSv2 响应跳数设置。设为 2 可让 Pod 网络命名空间访问 IMDS（常用于兼容回退链路）。
- A (EN): Hop limit 2 allows metadata responses to reach pod-level network hops.

### A56. What are IRSA and IMDS?
- Q (CN): IRSA 和 IMDS 分别是什么？
- A (CN): IRSA（IAM Roles for Service Accounts）给 Pod 分配专属 IAM 角色；IMDS（Instance Metadata Service）提供节点元数据和实例角色凭证。实践上优先 IRSA，减少 IMDS 依赖。
- A (EN): IRSA is pod-scoped role federation; IMDS is node metadata/credential service.

### A57. Summary of permissions/OIDC/RBAC/IMDS/IRSA
- Q (CN): 总结一下这些概念？
- A (CN): OIDC 解决云侧身份联邦；`aws-auth` 做 IAM 到 K8s 组映射；RBAC 授权 K8s 资源操作；IRSA 授权 Pod 访问 AWS；IMDS 是节点凭证来源与回退路径。
- A (EN): They are layered controls across cloud identity, cluster authorization, and pod credentialing.

### A58. KMS key changes after `destroy + apply`
- Q (CN): 这句是什么意思？
- A (CN): 重建后 KMS 非对称签名 key 会变更 ARN 与公钥。必须同时更新 CI 签名引用和 Kyverno 验签公钥，否则 Enforce 下会阻断 Pod。
- A (EN): Rebuilt keys require synchronized CI signing ARN and Kyverno public key updates.

### A59. Why test idempotency with full `destroy + apply`?
- Q (CN): 这句是什么意思？
- A (CN): 仅增量 apply 不能证明可重建。至少一次完整销毁/重建能验证 bootstrap 依赖、顺序约束和灾备恢复能力。
- A (EN): Full rebuild validates true reproducibility beyond incremental drift management.

### A60. Why must IRSA be ready before switching Kyverno to Enforce?
- Q (CN): 这句是什么意思？
- A (CN): Kyverno 准入时要访问 ECR 做验签；没有 IRSA 时回退链路可能过慢，超过 webhook 超时并阻断部署。即便 Audit 模式，也可能因为超时拒绝准入。
- A (EN): Admission-time ECR calls need fast/consistent auth path; missing IRSA can cause timeout-based deployment failures.

### A61. Is Enforce equal to signature verification?
- Q (CN): Enforce 就是验签吗？
- A (CN): 不是。验签是技术动作；Enforce 是策略模式，表示“验签失败时是否强制拦截”。Enforce=拦截，Audit=记录（但超时仍可能阻断）。
- A (EN): Verification is the check; Enforce is the blocking decision mode.

### A62. What exactly is being verified?
- Q (CN): 是不是比对 Pod 里和 CI 里的签名一致？
- A (CN): 不是比对两份签名文件，而是验证“Pod 引用的镜像”是否具有可被受信任公钥验证通过的 cosign 签名（且签名绑定该镜像 digest）。
- A (EN): It verifies trust and integrity of the referenced image digest via signature validation.

### A63. What is KMS private key?
- Q (CN): KMS 私钥是什么？
- A (CN): 指 AWS KMS 非对称密钥对中的私钥部分。私钥不导出，CI 通过 KMS API 调用签名；Kyverno/验证端使用对应公钥验签。
- A (EN): Private key stays in KMS; signing is API-mediated; verification uses public key.

### A64. ECR tag lifecycle in plain language
- Q (CN): 现在项目 ECR 标签生命周期是怎样的？
- A (CN): 主要三类标签：`git-*`（主干常用）、`pr-*`（临时验证）、semver（正式发布）。主仓库有生命周期回收规则，semver默认长期保留；sidecar 由 CI 创建并需单独核查治理一致性。
- A (EN): Tag classes differ by purpose and retention policy; governance must stay aligned across main/sidecar repos.

### A65. PR tag vs semver tag difference
- Q (CN): PR 和 semver 看起来都像 release，有什么区别？
- A (CN): `pr-*` 是临时测试产物，不是正式发布承诺；semver 是可追溯、可回滚、用于正式部署的版本契约。
- A (EN): PR tags are ephemeral validation artifacts; semver tags represent formal releases.

### A66. Why `mutateDigest: false` / `verifyDigest: false` on IMMUTABLE tags?
- Q (CN): 这两条是什么意思？
- A (CN): 对不可变标签仓库，tag 与 digest 的映射稳定。可不强制改写/不强制仅 digest 引用，以减少噪音；但签名验证仍需保留。若环境改为可变 tag，应重新启用更严格策略。
- A (EN): With immutable tags, digest-forcing may be optional, but signature verification must remain enforced.

### A67. What should each `.trivyignore` entry include?
- Q (CN): `.trivyignore` 条目需要包含哪些信息？
- A (CN): 至少要写：CVE/GHSA 编号、严重性、受影响组件、当前利用条件评估、明确移除条件（如升级到某版本/某日期复核）。
- A (EN): Include ID, severity, affected component, exploitability condition, and explicit removal trigger.

### A68. Why must `cosign sign` use digest instead of tag?
- Q (CN): 为什么签 digest 而不是 tag？
- A (CN): push 后按 tag 查 manifest 可能有传播竞态导致 `MANIFEST_UNKNOWN`。digest 是不可变引用，签名更稳定可靠。
- A (EN): Digest signing avoids post-push tag-resolution race conditions.

### A69. Why can `COSIGN_KMS_ARN` be workflow env instead of secret?
- Q (CN): 为什么 KMS ARN 可以不用 GitHub Secret？
- A (CN): ARN 只是资源地址，不是敏感凭证。真正权限由 OIDC+IAM 决定。PAT 没 `secrets:write` 时可放 workflow `env`。
- A (EN): KMS ARN is non-secret metadata; authorization is enforced by IAM.

### A70. Why insist on `envFrom.secretRef` for `OPENAI_API_KEY`?
- Q (CN): 为什么强调机密只能通过 `envFrom.secretRef` 注入？
- A (CN): 避免机密落入镜像层、仓库和 values 文件；支持独立轮换与审计，降低泄露面。
- A (EN): Runtime secret injection reduces leak surface and improves rotation/auditability.

---

## Appendix B: Interview Quick Reference (1-2 pages) / 附录 B：面试速记（1-2 页）

### B1. 30-second system pitch / 30 秒系统介绍
- CN: 这是一个运行在 AWS EKS 的 Spring Boot API，采用多阶段容器化、Helm/K8s 编排、Terraform IaC、Trivy + cosign + Kyverno 供应链防护，以及 Secrets Manager + ESO 的密钥治理。
- EN: This is a Spring Boot API on AWS EKS with multi-stage containerization, Helm/K8s orchestration, Terraform IaC, Trivy+cosign+Kyverno supply-chain controls, and Secrets Manager+ESO secret governance.

### B2. Security chain (end-to-end) / 端到端安全链路
- CN: CI 构建镜像 -> Trivy 扫描（`--exit-code 1`）-> push ECR -> cosign 签名（KMS）-> Kyverno 准入验签 -> Helm 部署。
- EN: CI build -> Trivy gate (`--exit-code 1`) -> ECR push -> cosign KMS sign -> Kyverno admission verify -> Helm deploy.
- CN: 关键点：签名在 CI，验签在集群准入，不是同一环节。
- EN: Key point: signing happens in CI; verification happens at cluster admission.

### B3. Identity/authorization layers / 身份与授权分层
- CN: OIDC 负责 CI 获取 AWS 临时凭证；`aws-auth` 把 IAM 身份映射成 K8s 组；RBAC 决定 K8s 资源权限。
- EN: OIDC gets short-lived AWS creds; `aws-auth` maps IAM to K8s groups; RBAC grants K8s API permissions.
- CN: Pod 访问 AWS 优先 IRSA；IMDS 作为节点级元数据/凭证来源与回退路径。
- EN: Pod-to-AWS should prefer IRSA; IMDS is node-level metadata/credential source and fallback.

### B4. Secrets flow / 密钥流转
- CN: AWS Secrets Manager（真源）-> ESO 同步 -> K8s Secret -> Deployment `envFrom.secretRef` 注入 Pod。
- EN: AWS Secrets Manager (source of truth) -> ESO sync -> K8s Secret -> Pod env via `envFrom.secretRef`.
- CN: 应用通过 `OPENAI_API_KEY` 环境变量读取。
- EN: App reads `OPENAI_API_KEY` from environment variable.

### B5. Container/K8s high-signal best practices / 容器与 K8s 高频考点
- CN: 多阶段构建（build 用 SDK，runtime 用最小镜像）。
- EN: Multi-stage builds (fat build stage, lean runtime stage).
- CN: 非 root 运行 + `allowPrivilegeEscalation: false` + `capabilities: drop: [ALL]`。
- EN: Non-root + no privilege escalation + drop all capabilities.
- CN: 每个容器都要有 readiness + liveness（含 sidecar）。
- EN: Every container needs readiness + liveness probes, including sidecars.
- CN: Ingress `pathType` 必填（`networking.k8s.io/v1`）。
- EN: Ingress `pathType` is required in `networking.k8s.io/v1`.
- CN: 小集群滚动更新建议 `maxSurge: 0, maxUnavailable: 1` 防止调度卡死。
- EN: For small clusters, use `maxSurge: 0, maxUnavailable: 1` to avoid rollout deadlocks.

### B6. NetworkPolicy interview essentials / NetworkPolicy 面试要点
- CN: DNS 出站同时放行 UDP/TCP 53（大响应会回退 TCP）。
- EN: Allow both UDP/TCP 53 for DNS egress (TCP fallback for large responses).
- CN: 外部 HTTPS 放行要排除 RFC1918 私网段，避免横向移动。
- EN: Exclude RFC1918 ranges from broad HTTPS egress to reduce lateral movement.
- CN: EKS 上必须开启 VPC CNI `enableNetworkPolicy: "true"` 才会真正执行策略。
- EN: On EKS, policy objects are enforced only when VPC CNI `enableNetworkPolicy: "true"` is enabled.

### B7. Sidecar pattern (what to say) / Sidecar 面试表达
- CN: Sidecar 与主容器同 Pod 运行，共享网络；主容器可通过 `localhost` 调 sidecar。
- EN: Sidecar runs in the same pod and shared network namespace; main app talks via `localhost`.
- CN: 本项目 sidecar 是出站 forward proxy：`LISTEN_PORT + UPSTREAM_URL`，主容器注入 `LLM_PROXY_URL` 走代理链路。
- EN: In this project sidecar is an outbound forward proxy using `LISTEN_PORT + UPSTREAM_URL`, with main container using `LLM_PROXY_URL`.

### B8. ECR tag strategy in plain words / ECR 标签策略人话版
- CN: `git-*` = 主干构建标签；`pr-*` = 临时验证标签；`semver` = 正式发布标签。
- EN: `git-*` = mainline build tags; `pr-*` = temporary validation tags; `semver` = formal release tags.
- CN: 主仓库有生命周期策略清理 `pr-*`/旧 `git-*`，semver 默认长期保留。
- EN: Main repo lifecycle policy expires `pr-*` and old `git-*`; semver tags are retained by default.

### B9. Kyverno policy do/don't / Kyverno 策略要做与禁忌
- CN: `imageReferences` 必须覆盖主镜像 + 所有 sidecar，漏 sidecar 是绕过路径。
- EN: `imageReferences` must include main + all sidecars; missing sidecar creates a bypass path.
- CN: 先 Audit 再 Enforce，且在策略头部写清晋级检查清单。
- EN: Promote Audit -> Enforce only with a documented checklist in policy header.
- CN: Enforce 前先配好 `kyverno-admission-controller` 的 IRSA，避免 ECR 调用超时阻塞发布。
- EN: Configure IRSA for `kyverno-admission-controller` before Enforce to avoid timeout-based deploy blocking.
- CN: ECR IMMUTABLE tag 场景可用 `mutateDigest: false`、`verifyDigest: false` 减少噪音，但签名验证必须保留。
- EN: With immutable ECR tags, `mutateDigest: false` and `verifyDigest: false` can reduce noise, but signature verification must remain.

### B10. Terraform reliability checkpoints / Terraform 可靠性检查点
- CN: S3 backend + DynamoDB lock table 必须先 bootstrap，再 `terraform init`。
- EN: Bootstrap S3 backend + DynamoDB lock table before `terraform init`.
- CN: `destroy + apply` 后，KMS 非对称签名 key 的 ARN/公钥可能变化，CI 与 Kyverno 要同步更新。
- EN: After `destroy + apply`, asymmetric KMS signing key ARN/public key may change; update CI and Kyverno together.
- CN: 幂等性验证不能只看增量 apply，至少做一次完整重建验证。
- EN: Don’t rely on incremental apply only; validate with at least one full rebuild cycle.

### B11. Fast answers to common interviewer questions / 高频追问快答
- CN Q: 为什么要 OIDC？  
- CN A: 去掉长期 AK/SK，改用短期凭证，最小权限且可审计。
- EN Q: Why OIDC?  
- EN A: Replaces long-lived keys with short-lived auditable credentials.
- CN Q: Helm 为什么要 `secrets` CRUD？  
- CN A: Helm v3 release state 存在 K8s Secrets，没有权限会直接 `forbidden`。
- EN Q: Why Helm needs `secrets` CRUD?  
- EN A: Helm v3 stores release state in K8s Secrets; missing access causes RBAC failures.
- CN Q: Enforce 是不是就是验签？  
- CN A: 验签是动作，Enforce 是“失败是否拦截”的模式。
- EN Q: Is Enforce the same as verification?  
- EN A: Verification is the check; Enforce is the blocking mode.

### B12. Risk bullets you can mention / 可直接说的风险点
- CN: sidecar 仓库若无与主仓库一致的生命周期治理，成本和风险都会积累。
- EN: If sidecar repo lacks lifecycle governance parity with main repo, cost and risk accumulate.
- CN: 发布时主镜像/sidecar tag 需一致，避免“主版本升级但 sidecar tag 不存在”。
- EN: Keep main/sidecar tags aligned at release time to avoid missing sidecar image references.
- CN: Kyverno 策略命名空间匹配范围必须与实际部署命名空间一致，否则策略形同虚设。
- EN: Kyverno namespace match scope must align with deployment namespace or enforcement becomes ineffective.
