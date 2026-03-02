# 🛠️ Persons Finder – DevOps & SRE Challenge (AI-Augmented)

Welcome to the **Persons Finder** DevOps challenge.

**Scenario:**
The development team has finished the `persons-finder` API (a Java/Kotlin Spring Boot app that talks to an external LLM). It works on their machine. Now, **you** need to take it to production.

**Our Philosophy:** We want engineers who use AI to move fast, but who have the wisdom to verify every line.

---

## 🎯 The Mission

Your task is to Containerize, Infrastructure-as-Code (IaC), and secure this application.

### 1. 🐳 Containerization
*   Create a `Dockerfile` for the application.
*   **AI Challenge:** Ask an AI (ChatGPT/Claude) to write the Dockerfile.
*   **Audit:** The AI likely missed best practices (e.g., non-root user, multi-stage build, pinning versions). **Fix them.**
*   *Output:* An optimized `Dockerfile`.

### 2. ☁️ Infrastructure as Code (Kubernetes/Terraform)
*   Deploy this app to a local cluster (Minikube/Kind) or output Terraform for AWS/GCP.
*   **Requirements:**
    *   **Secrets:** The app needs an `OPENAI_API_KEY`. Do not bake it into the image. Show how you inject it securely (K8s Secrets, Vault, etc.).
    *   **Scaling:** Configure HPA (Horizontal Pod Autoscaler) based on CPU or custom metrics.
*   **AI Task:** Use AI to generate the K8s manifests (Deployment, Service, Ingress). **Document what you had to fix.** (Did it forget `readinessProbe`? Did it request 400 CPUs?)

### 3. 🛡️ The "AI Firewall" (Architecture)
The app sends user PII (names, bios) to an external LLM provider.
*   **Design Challenge:** Create a short architectural diagram or description (`ARCHITECTURE.md`) showing how you would secure this egress traffic.
*   **Question:** How would you implement a "PII Redaction Sidecar" or Gateway logic to prevent real names from leaving our cluster? You don't have to build it, just design the infrastructure for it.

### 4. 🤖 CI/CD & AI Usage
*   Create a CI pipeline (GitHub Actions preferred).
*   **The AI Twist:** We want to fail the build if the code "looks unsafe".
    *   Add a step in the pipeline that runs a security scanner (Trivy/Snyk) OR a mocked "AI Code Reviewer" step.

---

## 🚀 Live Deployment

| Item | Value |
|---|---|
| **URL** | https://aifindy.digico.cloud |
| **Swagger UI** | https://aifindy.digico.cloud/swagger-ui/index.html |
| **OpenAPI spec** | https://aifindy.digico.cloud/v3/api-docs |
| **Cluster** | AWS EKS `persons-finder-prod`, `ap-southeast-2` |
| **Nodes** | 3 × t3.small — 1 ON_DEMAND (system) + 2 SPOT (app, `persons-finder-nodes-prod`) |
| **TLS** | Let's Encrypt via cert-manager, auto-renewed |
| **Image tag** | `git-<sha>` (main builds) / `X.Y.Z` semver (releases) — ECR IMMUTABLE |
| **Replicas** | 3 (HPA min=3, max=10, CPU 70%) |

---

## 📚 Documentation

Full implementation details for each requirement are in the [`docs/`](docs/) directory.

| Document | Covers |
|---|---|
| [requirement-overview.md](docs/requirement-overview.md) | Single-page summary of all requirements, implementation status, test coverage |
| [ARCHITECTURE_DIAGRAM.md](docs/ARCHITECTURE_DIAGRAM.md) | System architecture diagrams: high-level AWS, PII layers, HPA, security, CI/CD, observability |
| [ApplicationDesignAndAPI.md](docs/ApplicationDesignAndAPI.md) | REST endpoints, 3-layer architecture, Haversine, OpenAPI/Swagger UI, CORS config, Swagger Basic Auth, 6 integration test classes |
| [ContainerizationAndDockerfile.md](docs/ContainerizationAndDockerfile.md) | AI-generated Dockerfile, identified flaws, applied fixes |
| [InfrastructureAsCode.md](docs/InfrastructureAsCode.md) | Terraform (AWS), Helm chart, ESO secret injection, HPA, AI manifest fixes |
| [SecurityAndSecrets.md](docs/SecurityAndSecrets.md) | Secrets management, RBAC, NetworkPolicy, pod security context, TLS/cert-manager, Ingress Basic Auth, cosign, Kyverno |
| [AIfirewall.md](docs/AIfirewall.md) | AI Firewall 4-layer PII egress protection: threat model, design rationale, end-to-end lifecycle |
| [CICDandAIUsage.md](docs/CICDandAIUsage.md) | GitHub Actions pipeline, Trivy scan gate, SBOM, image signing, ECR tag strategy (git-sha + semver, IMMUTABLE), periodic re-scan |
| [ObservabilityAndMonitoring.md](docs/ObservabilityAndMonitoring.md) | Structured logging, Fluent Bit, CloudWatch metrics & alarm, Actuator health probes |
| [AILLMIntegration.md](docs/AILLMIntegration.md) | PII proxy pipeline (Kotlin + Go sidecar), reversible tokenization, audit log |
| [devops-sre-architecture.md](docs/devops-sre-architecture.md) | Complete DevOps/SRE architecture diagrams: overall AWS topology, CI/CD, PII 4-layer, HPA, logging, security, Terraform, Helm, data flow, DR/HA, cost, SLI/SLO, tech stack |

Operational guides are in [`devops/docs/`](devops/docs/):

| Document | Covers |
|---|---|
| [QUICKSTART.md](devops/docs/QUICKSTART.md) | Fast-path: local Kind or EKS deployment |
| [DEPLOYMENT.md](devops/docs/DEPLOYMENT.md) | Full deployment lifecycle, Helm values, rollback |
| [RELEASE_PROCESS.md](devops/docs/RELEASE_PROCESS.md) | Semantic versioning, ECR tag strategy, release steps |
| [DNS_CONFIGURATION.md](devops/docs/DNS_CONFIGURATION.md) | Custom domain → NGINX Ingress LoadBalancer (CNAME/ALIAS) |
| [SWAGGER_TROUBLESHOOTING.md](devops/docs/SWAGGER_TROUBLESHOOTING.md) | Swagger UI access, Basic Auth, CORS, disable in prod |
| [SECURITY_REVIEW.md](devops/docs/SECURITY_REVIEW.md) | Security controls checklist |
| [HELM_DEPLOYMENT.md](devops/docs/HELM_DEPLOYMENT.md) | Helm chart deep-dive: templates, values, environment overrides |
| [NETWORK_SECURITY_VERIFICATION.md](devops/docs/NETWORK_SECURITY_VERIFICATION.md) | NetworkPolicy and VPC CNI enforcement verification |

---

## 📝 The AI Log

[`AI_LOG.md`](AI_LOG.md) documents AI collaboration across all sessions: what was generated, what was wrong, and what was fixed. The pre-commit checklist covers Container, Kubernetes, NetworkPolicy, CI/CD, Swagger/OpenAPI, Terraform, Kyverno, and Secrets & Supply Chain.

---

## ✅ Getting Started

```bash
# Build and test locally
./gradlew clean build

# Local K8s deployment (Kind)
# See devops/docs/QUICKSTART.md

# Deploy to EKS
# See devops/docs/DEPLOYMENT.md
```

## 📬 Submission

Submit your repository link. We care about:
*   **Security:** How you handle the API Key.
*   **Reliability:** Probes, Limits, Scaling.
*   **AI Maturity:** Your AI logs — did you blindly trust the bot, or did you engineer it?
