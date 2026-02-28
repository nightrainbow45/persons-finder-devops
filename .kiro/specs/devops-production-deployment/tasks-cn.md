# Implementation Plan: DevOps Production Deployment / 实施计划：DevOps 生产环境部署

## Overview / 概述

This implementation plan covers the complete DevOps production deployment for the Persons Finder Spring Boot application. The tasks include containerization with Docker multi-stage builds, Kubernetes orchestration with auto-scaling, CI/CD pipeline setup with security scanning, and PII protection architecture using sidecar pattern. All tasks build incrementally to create a production-ready deployment infrastructure.

本实施计划涵盖了 Persons Finder Spring Boot 应用的完整 DevOps 生产环境部署流程。任务包括：使用 Docker 多阶段构建实现容器化、基于 Kubernetes 的编排与自动扩缩容、集成安全扫描的 CI/CD 流水线搭建，以及使用 Sidecar 边车模式实现 PII（个人身份信息）保护架构。所有任务以递进方式叠加，最终构建出一套生产就绪的部署基础设施。

---

## Tasks / 任务列表

### ✅ 1. Create DevOps infrastructure folder structure / 创建 DevOps 基础设施目录结构

**Purpose / 目的：** Establish a unified, standardized directory layout in the repository, separating all DevOps configurations from application source code for easier maintenance and collaboration.
在代码仓库中建立统一、规范的目录布局，将所有 DevOps 相关配置与应用源码分离，便于维护和协作。

- Create `devops/` directory in repository root / 在仓库根目录创建 `devops/` 主目录
- Create `devops/docker/` for Dockerfile and `.dockerignore` / 创建 `devops/docker/` — 存放 Dockerfile 和 .dockerignore（容器构建配置）
- Create `devops/helm/persons-finder/` for Helm Chart / 创建 `devops/helm/persons-finder/` — 存放 Helm Chart（Kubernetes 应用包）
- Create `devops/helm/persons-finder/templates/` for K8s resource templates / 创建 `devops/helm/persons-finder/templates/` — 存放 K8s 资源模板文件
- Create `devops/helm/persons-finder/charts/` for Chart dependencies / 创建 `devops/helm/persons-finder/charts/` — 存放 Chart 依赖子包
- Create `devops/terraform/` for infrastructure as code / 创建 `devops/terraform/` — 存放基础设施即代码（IaC）配置
- Create `devops/terraform/modules/` for reusable Terraform modules / 创建 `devops/terraform/modules/` — 存放可复用的 Terraform 模块
- Create `devops/terraform/modules/iam/` for IAM and OIDC configuration / 创建 `devops/terraform/modules/iam/` — IAM 身份与访问管理及 OIDC 配置
- Create `devops/terraform/modules/iam/policies/` for IAM policy files / 创建 `devops/terraform/modules/iam/policies/` — IAM 权限策略文件
- Create `devops/terraform/modules/iam/trust-policies/` for trust policy files / 创建 `devops/terraform/modules/iam/trust-policies/` — 信任策略文件
- Create `devops/terraform/modules/vpc/` for VPC module / 创建 `devops/terraform/modules/vpc/` — VPC 虚拟私有云模块
- Create `devops/terraform/modules/eks/` for EKS module / 创建 `devops/terraform/modules/eks/` — EKS Kubernetes 集群模块
- Create `devops/terraform/modules/ecr/` for ECR module / 创建 `devops/terraform/modules/ecr/` — ECR 容器镜像仓库模块
- Create `devops/terraform/modules/secrets-manager/` for Secrets Manager module / 创建 `devops/terraform/modules/secrets-manager/` — Secrets Manager 密钥管理模块
- Create `devops/terraform/environments/dev/` for dev environment / 创建 `devops/terraform/environments/dev/` — 开发环境配置
- Create `devops/terraform/environments/prod/` for prod environment / 创建 `devops/terraform/environments/prod/` — 生产环境配置
- Create `devops/scripts/` for deployment helper scripts / 创建 `devops/scripts/` — 部署辅助脚本
- Create `devops/ci/` for CI/CD pipeline configurations / 创建 `devops/ci/` — CI/CD 流水线配置
- Create `devops/docs/` for deployment documentation / 创建 `devops/docs/` — 部署文档
- Create placeholder `README.md` files in each directory explaining its purpose / 在每个目录下创建占位用的 `README.md`，说明该目录的用途
- _Requirements: 8.1, 8.2, 8.5, 8.6, 8.7_

> **Note / 说明：** A standardized directory structure is the foundation of any DevOps project. Separating Docker, Helm, and Terraform configurations into modules makes it easy for team members to locate files and aligns with GitOps best practices.
> 规范化的目录结构是 DevOps 项目的基础。将 Docker、Helm、Terraform 等配置分模块存放，既方便团队成员快速定位文件，也符合 GitOps 的最佳实践。

---

### ✅ 1.1 Write unit tests for DevOps folder structure / 为 DevOps 目录结构编写单元测试

**Purpose / 目的：** Use automated tests to ensure the directory structure meets expectations, preventing accidental deletion or omission of critical directories during development.
通过自动化测试确保目录结构符合预期，防止后续开发误删或遗漏关键目录。

- Test all required directories exist / 测试所有必需目录是否存在
- Test `README.md` files exist in key directories / 测试关键目录中是否存在 `README.md` 文件
- Verify folder structure follows best practices / 验证目录结构是否符合最佳实践
- _Requirements: 8.1, 8.7_

> **Note / 说明：** Testing the infrastructure structure itself ensures the CI/CD process can detect missing directories early.
> 对基础设施结构本身进行测试，确保 CI/CD 流程在目录缺失时能够及早发现问题。

---

### ✅ 2. Create Docker multi-stage build configuration / 创建 Docker 多阶段构建配置

**Purpose / 目的：** Use Docker multi-stage builds to separate the compile/build environment from the final runtime environment, producing small and secure production images.
使用 Docker 多阶段构建，将编译构建环境与最终运行环境分离，生成体积小、安全性高的生产镜像。

- Create `devops/docker/Dockerfile` with **build stage** using `gradle:7.6-jdk11` / 创建 `devops/docker/Dockerfile`，**构建阶段**使用 `gradle:7.6-jdk11` 镜像编译 Java 应用
- Create `devops/docker/.dockerignore` to exclude unnecessary files / 创建 `devops/docker/.dockerignore`，排除不必要文件（如 `.git`、`build/` 等），加速构建
- Configure **runtime stage** using lightweight `eclipse-temurin:11-jre-alpine` (JRE only) / **运行阶段**使用轻量级 `eclipse-temurin:11-jre-alpine` 镜像，只包含 JRE 运行时
- Configure non-root user for security / 配置非 root 用户运行容器（安全加固，防止容器逃逸风险）
- Pin all base image versions to specific tags / 所有基础镜像固定到具体版本标签（确保构建可重复、避免版本漂移）
- Expose port 8080 for Spring Boot application / 暴露 8080 端口供 Spring Boot 应用使用
- Add `HEALTHCHECK` instruction for container health monitoring / 添加 `HEALTHCHECK` 指令，供 Docker/Kubernetes 检测容器健康状态
- _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

> **Note / 说明：** The core advantage of multi-stage builds: JDK, Gradle and other build tools are not included in the final image, reducing image size from hundreds of MB to tens of MB while minimizing the attack surface. Running as a non-root user is a critical container security measure.
> 多阶段构建的核心优势：构建阶段的 JDK、Gradle 等工具不会进入最终镜像，使镜像体积从数百 MB 压缩到几十 MB，同时减少攻击面。非 root 用户运行是容器安全的重要措施。

---

### ✅ 2.1 Write property test for container image determinism / 编写容器镜像确定性属性测试

**Purpose / 目的：** Verify that building the same source code multiple times produces the same content hash, ensuring reproducible (idempotent) builds.
验证对同一源码多次构建产生相同内容哈希值，确保构建过程具有可重复性（幂等性）。

- **Property 3: Container Image Determinism / 属性 3：容器镜像确定性**
- **Validates: Requirements 1.1, 1.2 / 验证需求：1.1, 1.2**
- Build same source code multiple times and verify content hash consistency / 对同一源码多次构建，验证内容哈希的一致性
- _Requirements: 1.1, 1.2_

> **Note / 说明：** Deterministic builds are an important production guarantee — the same code should always produce the same image, eliminating the "works on my machine" problem.
> 确定性构建（Deterministic Build）是生产环境的重要保障——相同代码应始终产出相同镜像，避免"在我机器上能运行"的问题。

---

### ✅ 2.2 Write unit tests for Dockerfile best practices / 编写 Dockerfile 最佳实践单元测试

**Purpose / 目的：** Automate verification that the Dockerfile meets security and quality standards.
自动化验证 Dockerfile 符合安全和质量标准。

- Test multi-stage build structure exists / 测试多阶段构建结构是否存在
- Test non-root user configuration / 测试非 root 用户配置
- Test base image version pinning / 测试基础镜像是否固定版本
- Test port 8080 exposure / 测试 8080 端口是否已暴露
- _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

---

### ✅ 3. Create Helm Chart structure / 创建 Helm Chart 结构

**Purpose / 目的：** Helm is the package manager for Kubernetes. A Helm Chart templates all K8s resources for an application, enabling configuration reuse across multiple environments.
Helm 是 Kubernetes 的包管理工具，通过 Helm Chart 将应用的所有 K8s 资源模板化，实现多环境配置复用。

- Create `Chart.yaml` with metadata (name, version, appVersion, description) / 创建 `Chart.yaml` — 定义 Chart 元数据（名称、版本、应用版本、描述）
- Create `values.yaml` with default configuration parameters / 创建 `values.yaml` — 定义默认配置参数（镜像、副本数、资源限制等）
- Create `values-dev.yaml` for development environment (low resource config) / 创建 `values-dev.yaml` — 开发环境专用参数（低资源配置）
- Create `values-prod.yaml` for production environment (HA, strict resource limits) / 创建 `values-prod.yaml` — 生产环境专用参数（高可用、更严格的资源限制）
- Create `templates/_helpers.tpl` for template helper functions / 创建 `templates/_helpers.tpl` — 模板辅助函数（如统一标签生成、名称格式化）
- Create `templates/NOTES.txt` for post-installation instructions / 创建 `templates/NOTES.txt` — 安装后提示信息（显示访问地址、使用说明）
- Create `.helmignore` to exclude unnecessary files from Chart / 创建 `.helmignore` — 排除不需要打包到 Chart 中的文件
- Create `README.md` with Chart documentation / 创建 `README.md` — Chart 使用文档
- _Requirements: 8.1, 8.2, 8.3, 8.4, 8.7_

> **Note / 说明：** Helm Charts convert Kubernetes deployment configuration from "hard-coded" to "parameterized templates". The same template set can adapt to development, testing, and production environments through different values files — the standard approach for modern cloud-native application deployment.
> Helm Chart 将 Kubernetes 部署配置从"硬编码"变为"参数化模板"，同一套模板通过不同的 values 文件就能适配开发、测试、生产多个环境，是现代云原生应用部署的标准做法。

---

### ✅ 3.1 Write unit tests for Helm Chart structure / 编写 Helm Chart 结构单元测试

**Purpose / 目的：** Validate the completeness and correctness of the Helm Chart.
验证 Helm Chart 的完整性和正确性。

- Test `Chart.yaml` exists and has required fields / 测试 `Chart.yaml` 存在且包含必填字段
- Test values files exist for different environments / 测试不同环境的 values 文件均存在
- Test `templates/` directory structure / 测试 `templates/` 目录结构
- Validate Chart with `helm lint` / 使用 `helm lint` 命令验证 Chart 格式合规性
- _Requirements: 8.1, 8.2, 8.3, 8.4, 8.8_

> **Note / 说明：** `helm lint` is the official syntax checking tool that catches template errors early, preventing issues from only surfacing at deploy time.
> `helm lint` 是官方提供的语法检查工具，能提前发现模板错误，避免部署时才暴露问题。

---

### ✅ 4. Create Kubernetes Secret template for API key management / 创建 Kubernetes Secret 模板（API 密钥管理）

**Purpose / 目的：** Securely manage sensitive configuration (such as OpenAI API Key), avoiding hard-coding secrets in images or source code.
安全地管理敏感配置（如 OpenAI API Key），避免将密钥硬编码在镜像或代码中。

- Create `templates/secret.yaml` / 创建 `templates/secret.yaml`
- Use Helm templating for conditional secret creation / 使用 Helm 模板语法实现条件化 Secret 创建（可选择内联或外部引用）
- Configure `OPENAI_API_KEY` from values or external secret / 从 values 或外部 Secret 配置 `OPENAI_API_KEY`
- Add documentation for manual secret creation process / 添加手动创建 Secret 的操作文档
- Support both inline secrets and external secret references / 同时支持内联密钥和外部 Secret 引用两种模式
- _Requirements: 2.1, 2.2, 2.5_

> **Note / 说明：** Kubernetes Secrets are a resource type designed for storing sensitive data, encoded as Base64 (production environments should pair with AWS Secrets Manager or Vault). API Keys must never appear in image layers or the code repository.
> Kubernetes Secret 是专门存储敏感数据的资源类型，数据以 Base64 编码存储（生产环境建议配合 AWS Secrets Manager 或 Vault 等外部密钥管理系统）。API Key 绝对不能出现在镜像层或代码仓库中。

---

### ✅ 4.1 Write unit tests for Secret template / 编写 Secret 模板单元测试

**Purpose / 目的：** Verify the rendering correctness and security of the Secret template.
验证 Secret 模板的渲染正确性和安全性。

- Test Secret template renders correctly / 测试 Secret 模板能正确渲染
- Test `OPENAI_API_KEY` key exists in rendered output / 测试渲染输出中存在 `OPENAI_API_KEY` 键
- Verify Secret type is `Opaque` / 验证 Secret 类型为 `Opaque`
- Test conditional rendering logic (enabled/disabled behavior) / 测试条件渲染逻辑（启用/禁用时的行为）
- _Requirements: 2.1, 2.5_

---

### ✅ 5. Create Kubernetes Deployment template / 创建 Kubernetes Deployment 模板

**Purpose / 目的：** Deployment is the core Kubernetes resource for managing stateless applications, defining how to run, update, and maintain application Pods.
Deployment 是 Kubernetes 中管理无状态应用的核心资源，定义了如何运行、更新和维护应用 Pod。

- Create `templates/deployment.yaml` / 创建 `templates/deployment.yaml`
- Parameterize replica count from `values.yaml` / 从 `values.yaml` 参数化副本数（支持多副本高可用）
- Parameterize image repository and tag from `values.yaml` / 从 `values.yaml` 参数化镜像仓库和标签（方便发版更新）
- Configure resource requests and limits from `values.yaml` / 从 `values.yaml` 配置资源请求（requests）和限制（limits）— CPU/内存的最小保障和最大上限
- Configure environment variable injection from Secret / 从 Secret 注入环境变量（安全传入 API Key 等敏感配置）
- Parameterize readiness probe settings (determines if Pod can receive traffic) / 从 `values.yaml` 参数化就绪探针（readiness probe）配置 — Kubernetes 用此判断 Pod 是否可以接受流量
- Parameterize liveness probe settings (determines if Pod needs restart) / 从 `values.yaml` 参数化存活探针（liveness probe）配置 — Kubernetes 用此判断 Pod 是否需要重启
- Configure rolling update strategy (zero-downtime updates) / 从 `values.yaml` 配置滚动更新策略（Rolling Update）— 零停机更新
- Set security context to run as non-root user / 设置安全上下文（Security Context），以非 root 用户运行
- Use Helm template helpers for labels and selectors / 使用 Helm 模板辅助函数生成标准标签和选择器
- _Requirements: 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5, 8.2, 9.4, 9.5_

> **Note / 说明：** A Deployment is the "specification" for running an application on K8s. Resource limits prevent a single Pod from exhausting node resources; probe mechanisms allow K8s to automatically handle unhealthy Pods; rolling update strategy ensures healthy instances are always serving traffic during updates.
> Deployment 是应用在 K8s 上运行的"说明书"。资源限制防止单个 Pod 耗尽节点资源；探针机制让 K8s 能自动处理不健康的 Pod；滚动更新策略确保更新过程中始终有健康实例提供服务。

---

### ✅ 5.1 Write unit tests for Deployment template / 编写 Deployment 模板单元测试

**Purpose / 目的：** Verify that all parameterized configurations in the Deployment template work correctly.
验证 Deployment 模板的各项参数化配置正确工作。

- Test template renders with default values / 测试模板使用默认值能正常渲染
- Test replica count parameterization / 测试副本数参数化
- Test resource requests and limits parameterization / 测试资源请求和限制的参数化
- Test environment variable injection / 测试环境变量注入
- Test probe configuration parameterization / 测试探针配置参数化
- Test security context configuration / 测试安全上下文配置
- Use `helm template` command for validation / 使用 `helm template` 命令进行验证
- _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 8.2_

---

### ✅ 6. Create Kubernetes Service template / 创建 Kubernetes Service 模板

**Purpose / 目的：** A Service provides stable network access to Pods in Kubernetes, abstracting away Pod IP changes caused by scaling, updates, or failures.
Service 是 Kubernetes 中为 Pod 提供稳定网络访问入口的资源，屏蔽了 Pod IP 变化带来的影响。

- Create `templates/service.yaml` / 创建 `templates/service.yaml`
- Parameterize service type from `values.yaml`: / 从 `values.yaml` 参数化 Service 类型：
  - `ClusterIP` — internal cluster access only / 仅集群内部访问
  - `LoadBalancer` — exposed via cloud load balancer / 通过云厂商负载均衡器对外暴露
  - `NodePort` — exposed via node port / 通过节点端口对外暴露
- Parameterize port configuration (service port to container port mapping) / 从 `values.yaml` 参数化端口配置（服务端口与容器端口映射）
- Use Helm template helpers for selectors (matching Deployment labels) / 使用 Helm 模板辅助函数生成选择器（与 Deployment 标签对应）
- Support service annotations from `values.yaml` (for cloud-specific features) / 支持从 `values.yaml` 添加 Service 注解（用于配置云厂商特定功能）
- _Requirements: 3.6, 8.2_

> **Note / 说明：** Services provide service discovery and load balancing. When Pods are rebuilt due to scaling, updates, or failures, their IPs change — but the Service IP and DNS name remain stable, allowing callers to always access via the Service.
> Service 提供了服务发现和负载均衡能力。当 Pod 因扩缩容、更新或故障而重建时，其 IP 会变化，但 Service 的 IP 和 DNS 名称保持稳定，调用方只需访问 Service 即可。

---

### ✅ 6.1 Write unit tests for Service template / 编写 Service 模板单元测试

- Test Service template renders correctly / 测试 Service 模板能正确渲染
- Test service type parameterization / 测试 Service 类型参数化
- Test port mapping configuration / 测试端口映射配置
- Test selector matches Deployment labels / 测试选择器与 Deployment 标签匹配
- _Requirements: 3.6, 8.2_

---

### ✅ 7. Create Kubernetes Ingress template / 创建 Kubernetes Ingress 模板

**Purpose / 目的：** Ingress manages rules for external HTTP/HTTPS traffic entering the cluster, typically paired with Nginx or ALB Ingress controllers.
Ingress 是 Kubernetes 中管理外部 HTTP/HTTPS 流量进入集群的规则，通常配合 Nginx 或 ALB 等 Ingress 控制器使用。

- Create `templates/ingress.yaml` / 创建 `templates/ingress.yaml`
- Add conditional rendering based on `ingress.enabled` in `values.yaml` / 基于 `ingress.enabled` 值实现条件渲染（可按需启用/禁用）
- Parameterize host configuration (e.g. `api.example.com`) / 从 `values.yaml` 参数化主机名配置（如 `api.example.com`）
- Parameterize path routing rules / 从 `values.yaml` 参数化路径路由规则（如 `/api/v1/*` 路由到对应 Service）
- Support multiple ingress controllers via annotations / 通过注解（annotations）支持多种 Ingress 控制器
- Parameterize TLS configuration (HTTPS certificates) / 从 `values.yaml` 参数化 TLS 配置（HTTPS 证书）
- _Requirements: 3.7, 8.2_

> **Note / 说明：** Ingress acts as the cluster's "reverse proxy gateway", distributing traffic to different backend services based on domain and path, with unified SSL termination.
> Ingress 相当于集群的"反向代理网关"，可以根据域名和路径将流量分发到不同的后端服务，并统一处理 SSL 终止。

---

### ✅ 7.1 Write unit tests for Ingress template / 编写 Ingress 模板单元测试

- Test Ingress renders when enabled / 测试启用时 Ingress 能正确渲染
- Test Ingress is not rendered when disabled / 测试禁用时 Ingress 不渲染
- Test host and path parameterization / 测试主机名和路径参数化
- Test TLS configuration / 测试 TLS 配置
- _Requirements: 3.7, 8.2_

---

### ✅ 8. Create Kubernetes HorizontalPodAutoscaler template / 创建 Kubernetes HPA（水平 Pod 自动扩缩容）模板

**Purpose / 目的：** HPA automatically adjusts Pod count based on CPU/memory utilization — scaling out during peak load and scaling in during low load, saving resource costs.
HPA 根据 CPU/内存利用率自动调整 Pod 数量，实现负载高峰时自动扩容、低峰时自动缩容，节省资源成本。

- Create `templates/hpa.yaml` / 创建 `templates/hpa.yaml`
- Add conditional rendering based on `autoscaling.enabled` in `values.yaml` / 基于 `autoscaling.enabled` 值实现条件渲染
- Parameterize `minReplicas` and `maxReplicas` from `values.yaml` / 从 `values.yaml` 参数化最小副本数（`minReplicas`）和最大副本数（`maxReplicas`）
- Parameterize CPU target utilization (e.g. scale out at 70%) / 从 `values.yaml` 参数化 CPU 目标利用率（如 70% 时触发扩容）
- Configure stabilization windows to prevent thrashing / 从 `values.yaml` 配置稳定窗口（`stabilizationWindows`）— 防止频繁扩缩容抖动
- _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 8.2_

> **Note / 说明：** HPA is the core mechanism for cloud-native elastic scaling. Stabilization window configuration is important — set the scale-out window short (quick response to traffic peaks) and the scale-in window long (avoid triggering scale-in from brief traffic dips which destabilizes the service).
> HPA 是云原生弹性伸缩的核心机制。稳定窗口配置很重要——扩容窗口设置短（快速响应流量峰值），缩容窗口设置长（避免流量短暂下降就触发缩容导致服务不稳定）。

---

### ✅ 8.1 Write unit tests for HPA template / 编写 HPA 模板单元测试

- Test HPA renders when autoscaling is enabled / 测试启用自动扩缩容时 HPA 能正确渲染
- Test HPA is not rendered when disabled / 测试禁用时 HPA 不渲染
- Test `minReplicas` and `maxReplicas` parameterization / 测试最小/最大副本数参数化
- Test CPU target utilization configuration / 测试 CPU 目标利用率配置
- Test stabilization windows / 测试稳定窗口配置
- _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 8.2_

---

### ✅ 9. Checkpoint — Verify Helm Chart / 检查点 — 验证 Helm Chart

**Purpose / 目的：** Perform a comprehensive validation of the entire Helm Chart before proceeding with subsequent tasks, ensuring all templates are correct.
在继续后续任务前，对整个 Helm Chart 进行全面验证，确保所有模板正确。

- Run `helm lint` on the Chart / 运行 `helm lint` 检查 Chart 语法合规性
- Run `helm template` to verify all templates render correctly / 运行 `helm template` 渲染所有模板并检查输出
- Test with different values files (dev, prod) / 使用不同 values 文件（dev、prod）分别测试渲染结果
- Validate rendered YAML with `kubeval` / 使用 `kubeval` 验证渲染出的 YAML 是否符合 Kubernetes 规范
- Ensure all tests pass, ask the user if questions arise / 确保所有测试通过，如有问题及时与用户沟通

> **Note / 说明：** This is a quality gate checkpoint. `kubeval` validates YAML against the Kubernetes API spec, which is stricter than `helm lint` and can detect field type errors and missing required fields.
> 这是一个质量门控节点。`kubeval` 会对照 Kubernetes API 规范验证 YAML，比 `helm lint` 更严格，能发现字段类型错误、缺少必填字段等深层问题。

---

### 🔄 10. Implement PII redaction sidecar container / 实现 PII 脱敏 Sidecar 容器

**Purpose / 目的：** Use the sidecar pattern to automatically intercept and redact personally identifiable information (PII) before the application sends LLM requests, preventing privacy data from leaking to external AI services.
通过 Sidecar 边车模式，在应用发出 LLM 请求前自动拦截并脱敏个人身份信息（PII），防止隐私数据泄露给外部 AI 服务。

- Create new Kotlin module for PII redaction service / 创建新的 Kotlin 模块实现 PII 脱敏服务
- Implement HTTP proxy server that intercepts outbound LLM requests / 实现 HTTP 代理服务器，拦截应用发往 LLM 的出站请求
- Implement PII detection using regex patterns for names and coordinates / 使用正则表达式实现 PII 检测（人名、地理坐标等）
- Implement redaction/tokenization logic with reversible mapping / 实现可逆的脱敏/令牌化逻辑（将 PII 替换为占位符，并保存映射关系，以便还原响应）
- Add configuration for redaction rules and PII patterns / 添加脱敏规则和 PII 模式的配置项
- _Requirements: 5.1, 5.2, 5.3, 5.4_

> **Note / 说明：** The sidecar pattern deploys auxiliary functions (proxy, monitoring, logging) as separate containers in the same Pod as the main application. The main app sends LLM requests to localhost proxy port; the Sidecar intercepts, redacts, then forwards to the real LLM API; on response, tokens are restored. The main app detects no change — a non-intrusive privacy protection approach.
> Sidecar 模式是将辅助功能（如代理、监控、日志）部署为与主应用同 Pod 的独立容器。主应用将 LLM 请求发往 localhost 的代理端口，Sidecar 拦截后完成脱敏再转发给真实 LLM API，响应返回时再还原令牌，主应用感知不到任何变化。这是一种非侵入式的隐私保护方案。

---

### 🔄 10.1 Implement audit logging for PII redaction / 实现 PII 脱敏审计日志

**Purpose / 目的：** Record PII detection status for all external API calls to meet compliance audit requirements.
记录所有对外 API 调用的 PII 检测情况，满足合规审计要求。

- Create audit log data model (timestamp, request_id, pii_detected, redactions_applied, destination) / 创建审计日志数据模型（包含字段：时间戳、请求 ID、检测到的 PII 类型、应用的脱敏操作、目标地址）
- Implement audit log writer to stdout in JSON format / 实现将审计日志以 JSON 格式写入 stdout（符合云原生日志收集规范）
- Log all external API calls with PII detection results / 记录所有外部 API 调用及 PII 检测结果
- _Requirements: 5.5_

> **Note / 说明：** Data protection regulations such as GDPR and CCPA require complete audit records for personal data processing. JSON format output to stdout allows unified collection and analysis by ELK, CloudWatch, and other log systems.
> GDPR、CCPA 等数据保护法规要求对个人数据的处理保留完整审计记录。JSON 格式输出到 stdout 方便 ELK、CloudWatch 等日志系统统一收集分析。

---

### 🔄 10.2 Write property test for PII redaction completeness / 编写 PII 脱敏完整性属性测试

**Purpose / 目的：** Use property-based testing to verify the correctness and completeness of PII redaction.
通过属性测试验证 PII 脱敏的正确性和完整性。

- **Property 1: PII Redaction Completeness / 属性 1：PII 脱敏完整性**
- **Validates: Requirements 5.3 / 验证需求：5.3**
- Generate random requests with various PII types / 生成包含各类 PII 的随机请求
- Verify all PII is redacted before sending to external services / 验证所有 PII 在发往外部服务前均已完成脱敏
- Verify redaction is reversible / 验证脱敏操作可逆（令牌能正确还原为原始 PII）
- _Requirements: 5.3_

> **Note / 说明：** Property-based testing (jqwik) validates system invariants through large amounts of random input, covering far more ground than hand-written fixed test cases, and can find redaction omissions in edge cases.
> 属性测试（Property-based Testing）通过大量随机输入验证系统不变量，比手写固定用例覆盖范围更广，能发现边界情况下的脱敏遗漏。

---

### 🔄 10.3 Write property test for audit log completeness / 编写审计日志完整性属性测试

**Purpose / 目的：** Verify that every external API call has a corresponding audit log entry with no omissions.
验证每次外部 API 调用都有对应的审计日志条目，无遗漏。

- **Property 2: Audit Log Completeness / 属性 2：审计日志完整性**
- **Validates: Requirements 5.5 / 验证需求：5.5**
- Generate random sequences of external API calls / 生成随机的外部 API 调用序列
- Verify each call has corresponding audit log entry / 验证每次调用都有对应的审计日志条目
- Verify log entries contain all required fields / 验证日志条目包含所有必填字段
- Verify chronological ordering / 验证日志条目按时间顺序排列
- _Requirements: 5.5_

---

### 🔄 10.4 Write unit tests for PII redaction service / 编写 PII 脱敏服务单元测试

- Test regex pattern matching for person names / 测试人名正则模式匹配
- Test coordinate redaction / 测试地理坐标脱敏
- Test tokenization and de-tokenization / 测试令牌化和反令牌化（脱敏还原）
- Test error handling for redaction failures / 测试脱敏失败时的错误处理
- _Requirements: 5.2, 5.3_

---

### 🔄 11. Update Deployment template with PII sidecar / 更新 Deployment 模板以集成 PII Sidecar

**Purpose / 目的：** Add the PII redaction service as a sidecar container into the application Pod's Deployment configuration.
将 PII 脱敏服务作为 Sidecar 容器加入到应用 Pod 的 Deployment 配置中。

- Add sidecar container definition to `deployment.yaml` / 在 `deployment.yaml` 中添加 Sidecar 容器定义
- Parameterize sidecar image from `values.yaml` / 从 `values.yaml` 参数化 Sidecar 镜像
- Configure sidecar to listen on localhost proxy port / 配置 Sidecar 监听 localhost 的代理端口
- Update main application container to route LLM requests through sidecar / 更新主应用容器，将 LLM 请求路由到 Sidecar 代理
- Configure shared volume for audit logs if needed / 如需要，配置共享卷用于审计日志传输
- Add resource requests and limits for sidecar from `values.yaml` / 从 `values.yaml` 为 Sidecar 配置资源请求和限制
- Make sidecar optional via `values.yaml` flag / 通过 `values.yaml` 中的开关使 Sidecar 可选启用/禁用
- _Requirements: 5.4, 8.2_

> **Note / 说明：** Containers in the same Pod share a network namespace (communicate via localhost) and storage volumes — the basis that makes the sidecar pattern work. Using a values flag allows the sidecar to be disabled in dev environments (simpler debugging) and forced on in production to protect data security.
> 同一 Pod 内的容器共享网络命名空间（可通过 localhost 通信）和存储卷，这是 Sidecar 模式能够工作的基础。通过 values 开关，开发环境可以禁用 Sidecar 简化调试，生产环境强制启用保护数据安全。

---

### 🔄 11.1 Write unit tests for sidecar configuration / 编写 Sidecar 配置单元测试

- Test sidecar container renders when enabled / 测试启用时 Sidecar 容器能正确渲染
- Test sidecar is not rendered when disabled / 测试禁用时 Sidecar 不渲染
- Test sidecar resource configuration / 测试 Sidecar 资源配置
- Test localhost communication setup / 测试 localhost 通信配置
- _Requirements: 5.4, 8.2_

---

### 🔄 12. Create GitHub Actions CI/CD workflow / 创建 GitHub Actions CI/CD 工作流

**Purpose / 目的：** Establish an automated continuous integration/delivery pipeline that automatically builds, tests, scans, and deploys on code commit.
建立自动化的持续集成/持续交付流水线，实现代码提交后自动构建、测试、扫描和部署。

#### ✅ 12.1 Create workflow file structure / 创建工作流文件结构

**Purpose / 目的：** Establish the base configuration files for the CI/CD workflow.
建立 CI/CD 工作流的基础配置文件。

- Create `devops/ci/ci-cd.yml` (source file, to be copied to `.github/workflows/`) / 创建 `devops/ci/ci-cd.yml`（源文件，后续复制到 `.github/workflows/`）
- Create `.github/workflows/` directory if not exists / 创建 `.github/workflows/` 目录（GitHub Actions 标准目录）
- Copy `devops/ci/ci-cd.yml` to `.github/workflows/ci-cd.yml` / 将 `devops/ci/ci-cd.yml` 复制到 `.github/workflows/ci-cd.yml`
- Configure trigger on push to main branch / 配置触发条件：推送到 main 分支时触发
- Set up jobs for build, test, scan, and deploy stages / 设置构建、测试、扫描、部署各阶段的 Job
- _Requirements: 6.7_

---

#### ✅ 12.2 Implement build and test stage / 实现构建和测试阶段

**Purpose / 目的：** Automate Java code compilation and unit test execution to ensure code quality on every commit.
自动化编译 Java 代码并运行单元测试，确保每次提交的代码质量。

- Add checkout action / 添加 checkout action（拉取代码）
- Set up Java 11 environment / 配置 Java 11 运行环境
- Configure Gradle caching (speeds up subsequent builds) / 配置 Gradle 缓存（加速后续构建）
- Run Gradle build with tests / 运行 Gradle 构建和测试
- Upload test results as artifacts / 将测试结果作为 Artifacts 上传（便于查看测试报告）
- _Requirements: 6.1, 6.2_

---

#### ✅ 12.3 Implement AWS authentication stage / 实现 AWS 认证阶段

**Purpose / 目的：** Use OIDC (OpenID Connect) keyless authentication to securely authenticate to AWS, avoiding long-term AWS credentials in GitHub Secrets.
使用 OIDC（OpenID Connect）无密钥方式安全地向 AWS 认证，避免在 GitHub Secrets 中存储长期 AWS 凭证。

- Configure AWS credentials using OIDC (no Access Key/Secret Key storage) / 使用 OIDC 配置 AWS 凭证（无需存储 Access Key/Secret Key）
- Use `aws-actions/configure-aws-credentials` action / 使用 `aws-actions/configure-aws-credentials` 官方 Action
- Assume IAM role for GitHub Actions (least privilege) / 以 IAM 角色方式为 GitHub Actions 授权（最小权限原则）
- Verify AWS authentication / 验证 AWS 认证是否成功
- _Requirements: 6.3_

> **Note / 说明：** OIDC federated authentication is AWS's recommended CI/CD best practice. GitHub Actions obtains a short-lived JWT Token; AWS validates it and issues temporary credentials. No long-term keys need to be stored, greatly reducing credential leakage risk.
> OIDC 联合认证是 AWS 推荐的 CI/CD 认证最佳实践。GitHub Actions 获取一个短期 JWT Token，AWS 验证后颁发临时凭证，整个过程无需存储任何长期密钥，大幅降低凭证泄露风险。

---

#### ✅ 12.4 Implement Docker build stage / 实现 Docker 镜像构建阶段

**Purpose / 目的：** Automatically build the application's Docker image and tag it with version labels.
自动构建应用的 Docker 镜像并打上版本标签。

- Set up Docker Buildx (multi-platform build support) / 配置 Docker Buildx（支持多平台构建）
- Login to Amazon ECR using OIDC credentials / 使用 OIDC 凭证登录 Amazon ECR 镜像仓库
- Build Docker image using `devops/docker/Dockerfile` / 使用 `devops/docker/Dockerfile` 构建 Docker 镜像
- Tag image with commit SHA and `latest` (SHA ensures version traceability) / 用 commit SHA 和 `latest` 双标签标记镜像（SHA 标签保证版本可追溯）
- _Requirements: 6.3_

> **Note / 说明：** Using Git commit SHA as the image tag is best practice — it allows precise tracing of the code version corresponding to each image, making problem investigation and version rollback straightforward.
> 用 Git commit SHA 作为镜像标签是最佳实践，能精确追溯每个镜像对应的代码版本，方便问题排查和版本回滚。

---

#### ✅ 12.5 Implement security scanning stage / 实现安全扫描阶段

**Purpose / 目的：** Perform vulnerability scanning on built Docker images, blocking images with high-severity vulnerabilities from reaching production.
对构建好的 Docker 镜像进行漏洞扫描，拦截包含高危漏洞的镜像进入生产环境。

- Add Trivy security scanner action / 集成 Trivy 安全扫描器 Action
- Scan Docker image for vulnerabilities (CVE) / 扫描 Docker 镜像中的已知漏洞（CVE）
- Configure to fail on HIGH or CRITICAL severity / 配置在发现 HIGH（高）或 CRITICAL（严重）级别漏洞时流水线失败
- Generate and upload security scan report / 生成并上传安全扫描报告
- _Requirements: 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 7.5_

> **Note / 说明：** Trivy is the industry's mainstream open-source container security scanning tool, capable of detecting known vulnerabilities in OS packages and language dependencies (such as Java libraries). Setting a security gate in CI/CD is the key barrier preventing vulnerable images from reaching production.
> Trivy 是业界主流的开源容器安全扫描工具，能检测操作系统包、语言依赖（如 Java 库）中的已知漏洞。在 CI/CD 中设置安全门控，是防止漏洞镜像流入生产的关键屏障。

---

#### ✅ 12.6 Implement container registry push stage / 实现容器镜像推送阶段

**Purpose / 目的：** Push images that pass security scanning to Amazon ECR for subsequent Kubernetes deployment.
将通过安全扫描的镜像推送到 Amazon ECR，供后续 Kubernetes 部署使用。

- Configure container registry authentication using OIDC / 使用 OIDC 凭证配置容器仓库认证
- Push Docker image with version tags to Amazon ECR / 将带版本标签的 Docker 镜像推送到 Amazon ECR
- Push `latest` tag on successful build / 构建成功后同时推送 `latest` 标签
- _Requirements: 6.6_

---

#### 🔄 12.7 Write unit tests for CI/CD workflow / 编写 CI/CD 工作流单元测试

**Purpose / 目的：** Validate the YAML configuration correctness of the CI/CD workflow.
验证 CI/CD 工作流的 YAML 配置正确性。

- Test workflow file YAML structure / 测试工作流文件的 YAML 结构
- Test all required stages are present / 测试所有必需阶段均已配置
- Test trigger configuration / 测试触发条件配置
- Test security scanning is configured / 测试安全扫描已正确配置
- _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

---

### 🔄 13. Create Terraform configurations for AWS infrastructure / 创建 AWS 基础设施的 Terraform 配置

**Purpose / 目的：** Use Terraform (Infrastructure as Code) to manage AWS cloud resources, enabling versioned, auditable, and reproducible infrastructure.
使用 Terraform（基础设施即代码）管理 AWS 云资源，实现基础设施版本化、可审计、可重复创建。

#### 🔄 13.1 Create Terraform backend configuration / 创建 Terraform 后端配置

**Purpose / 目的：** Configure Terraform remote state storage to support team collaboration and state locking.
配置 Terraform 远程状态存储，支持团队协作和状态锁定。

- Create `backend.tf` — S3 backend (state file in S3, DynamoDB for state locking) / 创建 `backend.tf` — 配置 S3 远程后端（状态文件存储在 S3，DynamoDB 实现状态锁定）
- Create `versions.tf` — pin Terraform and AWS Provider versions / 创建 `versions.tf` — 固定 Terraform 和 AWS Provider 版本（确保一致性）
- Create `variables.tf` — global variables (env name, AWS Region, etc.) / 创建 `variables.tf` — 定义全局变量（环境名、AWS Region 等）
- Create `outputs.tf` — global outputs (VPC ID, EKS cluster name, etc.) / 创建 `outputs.tf` — 定义全局输出（VPC ID、EKS 集群名等）
- _Requirements: 8.6_

> **Note / 说明：** Terraform state files record actually created cloud resources. Storing in S3 prevents local file loss; DynamoDB locking prevents state conflicts when multiple people execute `terraform apply` simultaneously.
> Terraform 状态文件记录了实际创建的云资源。存储在 S3 可防止本地文件丢失；DynamoDB 锁定防止多人同时执行 `terraform apply` 导致状态冲突。

---

#### 🔄 13.2 Create IAM and OIDC module / 创建 IAM 和 OIDC 模块

**Purpose / 目的：** Manage AWS identity and access permissions, enabling GitHub Actions to access AWS resources via OIDC without storing credentials.
管理 AWS 身份与访问权限，实现 GitHub Actions 通过 OIDC 无密钥访问 AWS 资源。

- `main.tf` — main IAM resources / IAM 资源主配置
- `oidc.tf` — GitHub OIDC Provider configuration / 配置 GitHub OIDC Provider（允许 GitHub Actions 通过 OIDC 认证）
- `roles.tf` — IAM roles: `github-actions`, `eks-admin`, `eks-developer` / 创建 IAM 角色：CI/CD 流水线角色、EKS 管理员角色、开发者只读角色
- `policies/ecr-push.json` — ECR image push policy / ECR 镜像推送权限策略
- `policies/eks-access.json` — EKS cluster access policy / EKS 集群访问权限策略
- `policies/deployer.json` — deployer comprehensive policy / 部署者综合权限策略
- `trust-policies/github-oidc.json` — GitHub OIDC trust policy / 允许 GitHub Actions 的信任策略
- `trust-policies/eks-nodes.json` — EKS nodes trust policy / 允许 EKS 节点的信任策略
- _Requirements: 8.6_

---

#### 🔄 13.3 Create VPC module / 创建 VPC 模块

**Purpose / 目的：** Create an isolated AWS network environment following the principle of least privilege network access.
创建隔离的 AWS 网络环境，遵循最小权限的网络访问原则。

- `main.tf` — VPC, subnets, routing tables / VPC、子网、路由表配置
- `security-groups.tf` — security groups (inbound/outbound traffic rules) / 安全组配置（控制出入站流量规则）
- Configure public and private subnets across multiple AZs / 跨多个可用区（AZ）配置公有子网和私有子网
  - Public subnets: load balancers, bastion hosts / 公有子网：负载均衡器、跳板机
  - Private subnets: EKS worker nodes (not directly internet-exposed) / 私有子网：EKS 工作节点（不直接暴露在公网）
- Configure NAT Gateway (outbound internet for private subnet resources) and Internet Gateway / 配置 NAT Gateway（私有子网中的资源访问公网的出口）和 Internet Gateway（公有子网入口）
- _Requirements: 8.6_

> **Note / 说明：** Placing EKS nodes in private subnets is a production security best practice. Nodes access the internet (e.g. pulling images) via NAT Gateway, but the internet cannot directly access nodes, significantly reducing the attack surface.
> 将 EKS 节点放在私有子网是生产环境的安全最佳实践。节点通过 NAT Gateway 访问外网（如拉取镜像），但外网无法直接访问节点，显著降低攻击面。

---

#### 🔄 13.4 Create EKS module / 创建 EKS 模块

**Purpose / 目的：** Use Terraform to automate creation and configuration of Amazon EKS (managed Kubernetes) clusters.
使用 Terraform 自动化创建和配置 Amazon EKS（托管 Kubernetes）集群。

- `main.tf` — EKS cluster definition (managed control plane + managed node groups) / EKS 集群定义（托管控制平面 + 托管节点组）
- `aws-auth.tf` — configure `aws-auth` ConfigMap (map IAM roles to K8s RBAC groups) / 配置 `aws-auth` ConfigMap（将 IAM 角色映射到 K8s RBAC 组）
- Configure EKS managed node groups (auto-manage EC2 node lifecycle) / 配置 EKS 托管节点组（自动管理 EC2 节点的生命周期）
- Configure IAM roles and policies for EKS nodes / 配置 EKS 节点的 IAM 角色和策略
- Configure cluster add-ons: **VPC CNI** (Pod networking), **CoreDNS** (DNS), **kube-proxy** (node networking) / 配置集群插件：VPC CNI（Pod 网络）、CoreDNS（集群内 DNS）、kube-proxy（节点级网络代理）
- Map IAM roles to Kubernetes RBAC groups / 将 IAM 角色映射到 Kubernetes RBAC 组（实现统一的权限管理）
- _Requirements: 8.6_

> **Note / 说明：** EKS is AWS's managed Kubernetes service — AWS is responsible for control plane (Master node) operations, users only need to manage worker nodes. Via the `aws-auth` ConfigMap, users with specific IAM roles can gain K8s cluster operation permissions.
> EKS 是 AWS 托管的 Kubernetes 服务，AWS 负责控制平面（Master 节点）的运维，用户只需管理工作节点。通过 `aws-auth` ConfigMap，可以让拥有特定 IAM 角色的用户获得 K8s 集群的操作权限。

---

#### 🔄 13.5 Create ECR module / 创建 ECR 模块

**Purpose / 目的：** Create Amazon ECR private container image repository to securely store application Docker images.
创建 Amazon ECR 私有容器镜像仓库，安全存储应用 Docker 镜像。

- Create ECR repository resources / 创建 ECR 仓库资源
- Configure image scanning on push (alert on vulnerabilities) / 配置推送时自动镜像扫描（发现漏洞时告警）
- Configure lifecycle policies for image retention (auto-clean old images to control storage costs) / 配置镜像生命周期策略（自动清理旧镜像，控制存储成本）
- Configure repository access policies (restrict who can pull/push images) / 配置仓库访问策略（限制谁可以拉取/推送镜像）
- _Requirements: 8.6_

---

#### 🔄 13.6 Create Secrets Manager module / 创建 Secrets Manager 模块

**Purpose / 目的：** Use AWS Secrets Manager to securely store and rotate application secrets.
使用 AWS Secrets Manager 安全存储和轮换应用密钥。

- Create secret resources (e.g. OpenAI API Key, database passwords) / 创建密钥资源（如 OpenAI API Key、数据库密码）
- Configure secret auto-rotation policies (periodic key updates reduce leakage risk) / 配置密钥自动轮换策略（定期更新密钥降低泄露风险）
- Configure KMS encryption for secrets (encrypt secret content using AWS KMS) / 配置 KMS 密钥加密（使用 AWS KMS 对密钥内容加密存储）
- _Requirements: 8.6_

> **Note / 说明：** Secrets Manager is a more secure secret management solution than K8s Secrets. It supports auto-rotation, version management, and fine-grained access control, with secrets stored in encrypted form — even if the database is leaked, plaintext cannot be read directly.
> Secrets Manager 是比 K8s Secret 更安全的密钥管理方案。它支持自动轮换、版本管理和细粒度访问控制，且密钥以加密形式存储，即使数据库泄露也无法直接读取明文。

---

#### 🔄 13.7 Create environment-specific configurations / 创建环境专用配置

**Purpose / 目的：** Create separate Terraform configurations for development and production environments using the same modules but different parameters.
为开发和生产环境分别创建 Terraform 配置，使用相同模块但参数不同。

- `environments/dev/main.tf` — dev environment (calls modules with dev params) / 开发环境（调用各模块，传入 dev 参数）
- `environments/dev/terraform.tfvars` — dev variable values (small instance types, single node) / 开发环境变量值（小实例类型、单节点）
- `environments/prod/main.tf` — prod environment (calls modules with prod params) / 生产环境（调用各模块，传入 prod 参数）
- `environments/prod/terraform.tfvars` — prod variable values (large instance types, multi-node HA) / 生产环境变量值（大实例类型、多节点高可用）
- Configure environment-specific settings (instance types, node counts) / 配置环境专属设置（EC2 实例类型、节点数量等）
- Compose all modules (IAM, VPC, EKS, ECR, Secrets Manager) / 组合调用所有模块（IAM、VPC、EKS、ECR、Secrets Manager）
- _Requirements: 8.6_

---

#### 🔄 13.8 Create Terraform documentation / 创建 Terraform 文档

**Purpose / 目的：** Provide complete usage instructions for infrastructure configuration to reduce the team's onboarding cost.
为基础设施配置提供完整的使用说明，降低团队的上手成本。

- Document prerequisites (AWS CLI, Terraform, kubectl versions) / 文档化前置条件（AWS CLI、Terraform、kubectl 版本要求）
- Document deployment steps for each environment (init, plan, apply) / 文档化各环境的部署步骤（初始化、计划、应用）
- Document variable configuration and customization / 文档化变量配置和自定义说明
- Document outputs and how to use them (e.g. getting EKS kubeconfig) / 文档化输出结果及如何使用（如获取 EKS kubeconfig）
- _Requirements: 8.6_

---

### 🔄 14. Create Kubernetes RBAC and Security resources / 创建 Kubernetes RBAC 和安全资源

**Purpose / 目的：** Following the principle of least privilege, configure fine-grained Kubernetes access control and network isolation policies for the application.
按照最小权限原则，为应用配置精细化的 Kubernetes 访问控制和网络隔离策略。

#### 🔄 14.1 Create ServiceAccount template / 创建 ServiceAccount 模板

**Purpose / 目的：** Create a dedicated service account for application Pods, isolated from the default account, supporting IRSA (IAM Role for Service Account).
为应用 Pod 创建专属的服务账号，与默认账号隔离，支持 IRSA（IAM Role for Service Account）。

- Create `templates/serviceaccount.yaml` / 创建 `templates/serviceaccount.yaml`
- Configure ServiceAccount for application pods / 为应用 Pod 配置 ServiceAccount
- Add IRSA annotations (associate K8s ServiceAccount with AWS IAM role, allowing Pod to directly access AWS services) / 添加 IRSA 注解（将 K8s ServiceAccount 与 AWS IAM 角色关联，Pod 可直接访问 AWS 服务）
- _Requirements: 3.1_

> **Note / 说明：** IRSA is an EKS-specific feature that lets Pods access AWS services (e.g. Secrets Manager, S3) with the identity of a specified IAM role, without configuring AWS credentials in the Pod.
> IRSA 是 EKS 特有功能，让 Pod 能够以指定 IAM 角色的身份访问 AWS 服务（如 Secrets Manager、S3），无需在 Pod 中配置 AWS 凭证。

---

#### 🔄 14.2 Create RBAC templates / 创建 RBAC 模板

**Purpose / 目的：** Define the minimum permissions for the application within its Kubernetes namespace.
定义应用在 Kubernetes 命名空间内的最小权限。

- Create `templates/rbac.yaml` / 创建 `templates/rbac.yaml`
- Define Role (namespace permission rules, e.g. read ConfigMaps, Secrets) / 定义 Role（命名空间内的权限规则，如读取 ConfigMap、Secret）
- Define RoleBinding (bind ServiceAccount to Role) / 定义 RoleBinding（将 ServiceAccount 绑定到 Role）
- Follow least privilege principle (only grant K8s resource permissions the app actually needs) / 遵循最小权限原则（只授予应用实际需要的 K8s 资源访问权限）
- _Requirements: 3.1_

---

#### 🔄 14.3 Create NetworkPolicy template / 创建 NetworkPolicy 模板

**Purpose / 目的：** Implement network isolation between Pods via network policies, preventing lateral movement attacks.
通过网络策略实现 Pod 间的网络隔离，防止横向移动攻击。

- Create `templates/networkpolicy.yaml` / 创建 `templates/networkpolicy.yaml`
- Configure ingress rules: only allow traffic from Ingress controller / 配置入站规则（ingress）：只允许来自 Ingress 控制器的流量
- Configure egress rules: only allow access to external LLM API and DNS / 配置出站规则（egress）：只允许访问外部 LLM API 和 DNS
- Implement default deny policy (all non-explicitly-allowed traffic is rejected) / 实现默认拒绝策略（Default Deny）— 未明确允许的流量一律拒绝
- _Requirements: 3.1_

> **Note / 说明：** NetworkPolicy is K8s's "firewall". The default deny policy means that even if an attacker compromises a Pod, they cannot freely access other services in the cluster, significantly improving security defense depth.
> NetworkPolicy 是 K8s 的"防火墙"。默认拒绝策略意味着即使攻击者入侵了某个 Pod，也无法随意访问集群内的其他服务，显著提高了安全防御深度。

---

#### 🔄 14.4 Create ImagePullSecret template / 创建 ImagePullSecret 模板

**Purpose / 目的：** Configure authentication required to pull images from the private ECR repository.
配置从私有 ECR 仓库拉取镜像所需的认证信息。

- Create `templates/imagepullsecret.yaml` / 创建 `templates/imagepullsecret.yaml`
- Configure ECR authentication (Docker registry credentials) / 配置 ECR 认证（Docker registry 凭证）
- Support automatic credential refresh (ECR token validity is 12 hours) / 支持自动凭证刷新（ECR token 有效期为 12 小时）
- _Requirements: 1.1_

---

#### 🔄 14.5 Write unit tests for security resources / 编写安全资源单元测试

- Test ServiceAccount renders correctly / 测试 ServiceAccount 正确渲染
- Test RBAC permissions are minimal / 测试 RBAC 权限遵循最小权限原则
- Test NetworkPolicy rules / 测试 NetworkPolicy 规则
- Test ImagePullSecret configuration / 测试 ImagePullSecret 配置
- _Requirements: 3.1_

---

### 🔄 15. Create deployment documentation and scripts / 创建部署文档和脚本

**Purpose / 目的：** Provide complete operation guides and automation scripts to lower deployment barriers and support team-standardized operation processes.
提供完整的操作指南和自动化脚本，降低部署门槛，支持团队标准化操作流程。

#### 🔄 15.1 Create deployment README / 创建部署 README

- Create `devops/docs/DEPLOYMENT.md` comprehensive deployment guide / 创建 `devops/docs/DEPLOYMENT.md` 全面部署指南
- Document prerequisites (kubectl, helm, docker, terraform versions) / 文档化前置条件（kubectl、helm、docker、terraform 版本）
- Document Helm Chart structure and values configuration / 文档化 Helm Chart 结构和 values 配置说明
- Document local testing with Kind/Minikube using Helm / 文档化使用 Kind/Minikube 进行本地测试的步骤
- Document secret creation process / 文档化 Secret 手动创建流程
- Document `helm install`/`helm upgrade` commands for different environments / 文档化不同环境的 `helm install`/`helm upgrade` 命令
- Document verification steps (`helm status`, `kubectl get`) / 文档化验证步骤（`helm status`、`kubectl get`）
- Document rollback procedures (`helm rollback`) / 文档化回滚流程（`helm rollback`）
- _Requirements: 8.1, 8.3, 8.5_

---

#### 🔄 15.2 Create deployment helper scripts / 创建部署辅助脚本

**Purpose / 目的：** Automate common operations to reduce manual operation errors.
自动化常见的运维操作，减少人工操作错误。

- `deploy.sh` — automated Helm deployment (wraps `helm upgrade --install`) / 自动化 Helm 部署脚本（封装 `helm upgrade --install` 命令及参数）
- `verify.sh` — deployment verification (check Pod status, health endpoints, service accessibility) / 部署验证脚本（检查 Pod 状态、健康端点、服务可访问性）
- `local-test.sh` — Kind-based local testing (one-click local K8s cluster + app deployment) / 基于 Kind 的本地测试脚本（一键启动本地 K8s 集群并部署应用）
- `setup-eks.sh` — EKS cluster setup (calls Terraform to create AWS infrastructure) / EKS 集群初始化脚本（调用 Terraform 创建 AWS 基础设施）
- `teardown-eks.sh` — EKS cluster teardown (calls Terraform to destroy AWS resources) / EKS 集群销毁脚本（调用 Terraform 销毁 AWS 资源，节省费用）
- Make all scripts executable and add error handling (`set -e`) / 所有脚本设置可执行权限并添加错误处理（`set -e` 等）
- Add usage documentation in script headers / 在脚本头部添加使用说明注释
- _Requirements: 8.1, 8.3, 8.5, 8.6_

---

#### 🔄 15.3 Create quick start guide / 创建快速入门指南

- Create `devops/docs/QUICKSTART.md` / 创建 `devops/docs/QUICKSTART.md`
- Document fastest path to local deployment (up and running in 5 minutes) / 文档化本地部署最快路径（5 分钟内跑起来）
- Document fastest path to AWS EKS deployment / 文档化 AWS EKS 部署最快路径
- Include troubleshooting common issues / 包含常见问题故障排查（Troubleshooting）
- _Requirements: 8.1, 8.5_

---

### 🔄 16. Implement application API endpoints / 实现应用 API 端点

**Purpose / 目的：** Implement the core business features of Persons Finder, including person management and location query APIs.
实现 Persons Finder 的核心业务功能，包括人员管理和位置查询 API。

> **Note / 说明：** The following tasks (15.x numbered) implement the application's 3-layer architecture:
> 以下任务（15.x 编号）实现应用的三层架构：
> - **Data Layer / 数据层**: JPA entities and Repository / JPA 实体和 Repository
> - **Domain Layer / 领域层**: Business logic services / 业务逻辑服务
> - **Presentation Layer / 展示层**: REST API controllers / REST API 控制器

#### 🔄 15.1 Implement Person entity and repository / 实现 Person 实体和 Repository

- Create Person entity with JPA annotations (id, name) / 创建带 JPA 注解的 Person 实体（字段：id、name）
- Create `PersonRepository` interface extending `JpaRepository` / 创建继承 `JpaRepository` 的 `PersonRepository` 接口
- Configure H2 database schema generation / 配置 H2 数据库 Schema 自动生成
- _Requirements: Application data layer / 应用数据层_

---

#### 🔄 15.2 Implement Location entity and repository / 实现 Location 实体和 Repository

- Create Location entity with JPA annotations (referenceId, latitude, longitude) / 创建带 JPA 注解的 Location 实体（字段：referenceId、latitude 纬度、longitude 经度）
- Create `LocationRepository` interface extending `JpaRepository` / 创建 `LocationRepository` 接口
- Add indexes for efficient location queries / 为位置查询添加数据库索引（提升查询性能）
- _Requirements: Application data layer / 应用数据层_

---

#### 🔄 15.3 Implement PersonsService and LocationsService / 实现 PersonsService 和 LocationsService

- Create `PersonsService` interface and implementation / 创建 `PersonsService` 接口及其实现类
- Create `LocationsService` interface and implementation / 创建 `LocationsService` 接口及其实现类
- Implement location distance calculation algorithm (**Haversine formula** — spherical distance between two points on Earth) / 实现位置距离计算算法（**Haversine 公式**，基于地球球面计算两点之间的距离）
- Implement nearby person search logic (find persons within given radius) / 实现附近人员搜索逻辑（在给定半径范围内查找人员）
- _Requirements: Application domain layer / 应用领域层_

> **Note / 说明：** The Haversine formula is the standard algorithm for calculating spherical distances between two points on Earth's surface, suitable for latitude/longitude coordinate distance calculations.
> Haversine 公式是计算地球表面两点球面距离的标准算法，适用于经纬度坐标的距离计算。

---

#### 🔄 15.4 Implement `POST /api/v1/persons` endpoint / 实现 `POST /api/v1/persons` 端点

- Create `PersonController` with POST endpoint / 创建 PersonController，添加 POST 端点
- Accept person name in request body / 请求体接收人员姓名
- Return created person with generated ID / 返回创建的人员信息（含系统生成的 ID）
- Add input validation / 添加输入验证
- _Requirements: Application presentation layer / 应用展示层_

---

#### 🔄 15.5 Implement `PUT /api/v1/persons/{id}/location` endpoint / 实现 `PUT /api/v1/persons/{id}/location` 端点

- Add PUT endpoint to `PersonController` / 在 PersonController 添加 PUT 端点
- Accept latitude and longitude in request body / 请求体接收纬度（latitude）和经度（longitude）
- Update or create location for person / 更新或创建该人员的位置信息
- Validate coordinate ranges (latitude: -90 to 90, longitude: -180 to 180) / 验证坐标范围（纬度：-90 到 90，经度：-180 到 180）
- _Requirements: Application presentation layer / 应用展示层_

---

#### 🔄 15.6 Implement `GET /api/v1/persons/{id}/nearby` endpoint / 实现 `GET /api/v1/persons/{id}/nearby` 端点

- Add GET endpoint to `PersonController` / 在 PersonController 添加 GET 端点
- Accept `radius` query parameter (in kilometers) / 接收 `radius` 查询参数（单位：公里）
- Return list of person IDs within specified radius / 返回指定半径范围内的人员 ID 列表
- Use Haversine formula for distance calculation / 使用 Haversine 公式计算距离
- _Requirements: Application presentation layer / 应用展示层_

---

#### 🔄 15.7 Implement `GET /api/v1/persons` endpoint / 实现 `GET /api/v1/persons` 端点

- Add GET endpoint to `PersonController` / 在 PersonController 添加 GET 端点
- Accept comma-separated `ids` query parameter / 接收逗号分隔的 `ids` 查询参数
- Return list of person details / 返回指定人员的详细信息列表
- Handle missing persons gracefully (no exceptions thrown) / 对不存在的人员进行优雅处理（不抛异常）
- _Requirements: Application presentation layer / 应用展示层_

---

#### 🔄 15.8 ~ 15.10 Write unit tests for all layers / 编写各层单元测试

- **15.8** — Entity and Repository tests (JPA persistence, query methods) / 实体和 Repository 测试（JPA 持久化、查询方法）
- **15.9** — Service layer tests (CRUD operations, distance calculation, nearby search) / Service 层测试（CRUD 操作、距离计算算法、附近搜索逻辑）
- **15.10** — API endpoint tests (input validation and error handling for each HTTP endpoint) / API 端点测试（各 HTTP 端点的输入验证和错误处理）

---

### 🔄 16. Update Spring Boot application for production readiness / 更新 Spring Boot 应用至生产就绪状态

**Purpose / 目的：** Add health check and configuration management capabilities required for production environments to the Spring Boot application.
为 Spring Boot 应用添加生产环境必需的健康检查和配置管理能力。

#### 🔄 16.1 Configure Spring Boot Actuator / 配置 Spring Boot Actuator

- Add Spring Boot Actuator dependency to `build.gradle.kts` / 在 `build.gradle.kts` 添加 Spring Boot Actuator 依赖
- Configure `/actuator/health` endpoint / 配置 `/actuator/health` 健康检查端点
- Implement readiness and liveness health indicators / 实现就绪探针（Readiness）和存活探针（Liveness）健康指示器
- Return HTTP 200 when ready, 503 when not ready / 应用就绪时返回 HTTP 200，未就绪时返回 HTTP 503
- _Requirements: 9.1, 9.2, 9.3_

> **Note / 说明：** The `/actuator/health` endpoint provided by Spring Boot Actuator is the standard integration point for Kubernetes health probes. The readiness probe tells K8s when to send traffic to the Pod; the liveness probe tells K8s when to restart the Pod. Both together ensure service high availability.
> Spring Boot Actuator 提供的 `/actuator/health` 端点是 Kubernetes 健康探针的标准对接点。就绪探针告知 K8s 何时可以向 Pod 发送流量；存活探针告知 K8s 何时需要重启 Pod。两者共同确保服务的高可用性。

---

#### 🔄 16.2 Configure application for environment variable injection / 配置应用环境变量注入

- Update application configuration to read `OPENAI_API_KEY` from environment (not hard-coded) / 更新应用配置，从环境变量读取 `OPENAI_API_KEY`（而非硬编码）
- Add validation to fail gracefully with clear error if API key missing / 添加验证逻辑：API Key 缺失时提供清晰的错误信息并优雅退出
- Implement startup check for required environment variables / 实现启动时必要环境变量检查
- _Requirements: 2.3, 2.4_

> **Note / 说明：** Following the 12-Factor App methodology, configuration should be injected via environment variables rather than hard-coded, allowing the same image to run in different environments (dev/staging/prod) by simply changing environment variables.
> 遵循 12-Factor App 方法论，配置应通过环境变量注入而非写死在代码中，使同一镜像可以在不同环境（dev/staging/prod）运行，只需改变环境变量即可。

---

#### 🔄 16.3 & 16.4 Write unit tests for health endpoints and environment variables / 编写健康端点和环境变量单元测试

- Test `/actuator/health` returns 200 (when ready) and 503 (when not ready) / 测试 `/actuator/health` 返回 200（就绪时）和 503（未就绪时）
- Test health endpoint response format / 测试健康端点响应格式
- Test application reads API key from environment / 测试从环境读取 API Key
- Test error message clarity when API key is missing / 测试 API Key 缺失时的错误提示清晰性
- _Requirements: 9.1, 9.2, 9.3, 2.3, 2.4_

---

### 🔄 18. Create AI usage documentation / 创建 AI 使用文档

**Purpose / 目的：** Record detailed logs of all AI-assisted work in the project to meet transparency and auditability requirements.
记录项目中所有 AI 辅助工作的详细日志，满足透明度和可审计性要求。

- Create `AI_LOG.md` in repository root / 在仓库根目录创建 `AI_LOG.md`
- Document all AI-assisted work with original prompts / 记录所有 AI 辅助工作（含原始提示词）
- Document identified flaws or issues in AI-generated content / 记录 AI 生成内容中发现的缺陷或问题
- Document fixes applied to AI-generated content / 记录对 AI 生成内容所做的修正
- Include sections for Dockerfile, Kubernetes manifests, CI/CD workflow, and PII redaction / 包含以下各部分的记录：Dockerfile、Kubernetes 清单、CI/CD 工作流、PII 脱敏
- _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

---

### 🔄 18.1 Write unit tests for AI documentation / 编写 AI 文档单元测试

- Test `AI_LOG.md` file exists in repository root / 测试 `AI_LOG.md` 文件存在于仓库根目录
- Test file contains required sections / 测试文件包含所有必需的章节
- _Requirements: 10.1, 10.5_

---

### 🔄 19. Final checkpoint — Integration testing and verification / 最终检查点 — 集成测试与验证

**Purpose / 目的：** Perform end-to-end validation of the entire system to confirm all components work correctly together in a real environment.
对整个系统进行端到端验证，确认所有组件在真实环境中正确协作。

- Build Docker image locally and verify it runs / 在本地构建 Docker 镜像并验证能正常运行
- Deploy to local Kind/Minikube cluster using Helm / 使用 Helm 部署到本地 Kind/Minikube 集群
- Test `helm install` with different values files / 使用不同 values 文件测试 `helm install`
- Verify all Kubernetes resources are created successfully / 验证所有 Kubernetes 资源成功创建
- Test health endpoints are responding correctly / 测试健康端点是否正常响应
- Test HPA is monitoring and can scale / 测试 HPA 能否正确监控并触发扩缩容
- Verify PII redaction sidecar is intercepting requests / 验证 PII 脱敏 Sidecar 正常拦截请求
- Verify audit logs are being generated / 验证审计日志正常生成
- Test `helm upgrade` and `helm rollback` functionality / 测试 `helm upgrade` 和 `helm rollback` 功能
- Run all property-based tests and unit tests / 运行所有属性测试和单元测试
- Ensure all tests pass, ask the user if questions arise / 确保所有测试通过，如有问题及时与用户沟通

> **Note / 说明：** Integration testing is an end-to-end validation of the entire deployment process, covering container building, K8s deployment, auto-scaling, security protection, and all other core functions. `helm rollback` testing ensures a reliable fallback path when a release fails, which is an important safeguard for production deployment safety.
> 集成测试是对整个部署流程的端到端验证，涵盖了容器构建、K8s 部署、自动扩缩容、安全防护等所有核心功能。`helm rollback` 测试确保在发布失败时有可靠的回退路径，是生产部署安全性的重要保障。

---

## Notes / 附注

- Tasks marked with `*` are optional and can be skipped for faster MVP / 标记 `*` 的任务为可选项，在追求最小可行产品（MVP）时可跳过
- Each task references specific requirements for traceability / 每个任务都引用了具体的需求编号，便于需求追溯
- **Property tests** validate universal correctness properties with minimum 100 iterations / **属性测试**验证系统的普遍正确性，每个属性至少执行 100 次迭代
- **Unit tests** validate specific configuration examples and edge cases / **单元测试**验证特定配置示例和边界情况
- Implementation uses Kotlin 1.6.21 / Spring Boot 2.7.0 / 实现语言：Kotlin 1.6.21 / Spring Boot 2.7.0（用于主应用和 PII 脱敏服务）
- Application follows 3-layer architecture: Presentation (REST API) → Domain (business logic) → Data (entities) / 应用采用三层架构：展示层（REST API）→ 领域层（业务逻辑）→ 数据层（实体存储）
- Four API endpoints: create person, update location, find nearby, get person details / 计划实现四个 API 端点：创建人员、更新位置、查找附近人员、获取人员详情
- H2 in-memory database for development (production should use external database) / 开发环境使用 H2 内存数据库（生产环境应替换为外部持久化数据库）
- All Kubernetes resources defined as Helm templates following IaC principles / 所有 Kubernetes 资源均以 Helm 模板形式定义，遵循基础设施即代码（IaC）原则
- Helm provides parameterization for different environments via values files (dev / staging / prod) / Helm 通过不同的 values 文件实现多环境参数化（dev / staging / prod）
- CI/CD pipeline uses GitHub Actions with Trivy for security scanning / CI/CD 流水线使用 GitHub Actions + Trivy 安全扫描
- PII protection uses sidecar pattern for intercepting and redacting outbound LLM requests / PII 保护采用 Sidecar 边车模式，拦截并脱敏发往外部 LLM 的请求
- Terraform used for provisioning AWS EKS infrastructure / AWS EKS 基础设施通过 Terraform 自动化管理

---

## Task Status Legend / 任务状态图例

| Status / 状态 | Meaning / 说明 |
|---|---|
| ✅ `[x]` | Completed / 已完成 |
| 🔄 `[~]` | In progress / partial / 进行中 / 部分完成 |
| ⏳ `[-]` | Not started / 未开始 |
