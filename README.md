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
| **Nodes** | 3 × t3.small ON_DEMAND — 1 system node + 2 app nodes (`persons-finder-nodes-prod`) |
| **TLS** | Let's Encrypt via cert-manager, auto-renewed |
| **Image tag** | `git-<sha>` (main builds) / `X.Y.Z` semver (releases) — ECR IMMUTABLE |
| **Replicas** | 1 (HPA min=1, max=3, CPU 70%) |

---

## 🖥️ Cluster Status

### Nodes (3 × t3.small ON_DEMAND — 2 vCPU / ~1.4 GB allocatable / max 11 pods each)

| Node | Node Group | AZ | Taint | Pods | CPU Used | Mem Used |
|------|------------|----|-------|------|----------|----------|
| `ip-10-1-2-119` | `persons-finder-nodes-prod` | ap-southeast-2a | none | 8/11 | 400m (20%) | 292Mi (20%) |
| `ip-10-1-3-167` | `system-nodes-prod` | ap-southeast-2b | `system=true:NoSchedule` | 4/11 | 300m (15%) | 190Mi (13%) |
| `ip-10-1-3-25` | `persons-finder-nodes-prod` | ap-southeast-2b | none | 8/11 | 650m (33%) | 810Mi (56%) |

> `system-nodes-prod` carries a `NoSchedule` taint — only pods with a matching toleration are admitted, keeping system components isolated from application workloads.

### Namespaces (9)

| Namespace | Purpose |
|-----------|---------|
| `kube-system` | Core K8s system components: VPC CNI, kube-proxy, CoreDNS, Fluent Bit |
| `kube-public` | Cluster-wide public info (read-only, mostly empty) |
| `kube-node-lease` | Node heartbeat leases used by the control plane to detect node failures |
| `default` | Default namespace — no business workloads deployed here |
| `cert-manager` | Automatic TLS certificate provisioning and renewal via Let's Encrypt |
| `external-secrets` | External Secrets Operator — syncs `OPENAI_API_KEY` from AWS Secrets Manager into K8s |
| `ingress-nginx` | NGINX Ingress controller — external HTTP/HTTPS entry point, TLS termination, Basic Auth |
| `kyverno` | Image signature policy engine (Enforce mode) — blocks pods with unsigned images |
| `persons-finder` | **Business application**: Spring Boot API + PII Redaction Sidecar |

### Deployments (11 — all 1/1 Ready)

| Namespace | Deployment | Image | Purpose |
|-----------|-----------|-------|---------|
| `cert-manager` | `cert-manager` | jetstack/cert-manager-controller:v1.13.0 | Certificate lifecycle controller |
| `cert-manager` | `cert-manager-cainjector` | jetstack/cert-manager-cainjector:v1.13.0 | Injects CA certs into webhook configs |
| `cert-manager` | `cert-manager-webhook` | jetstack/cert-manager-webhook:v1.13.0 | Validates Certificate/Issuer resources |
| `external-secrets` | `external-secrets` | external-secrets:v2.0.1 | Pulls secrets from AWS Secrets Manager |
| `external-secrets` | `external-secrets-cert-controller` | external-secrets:v2.0.1 | Manages ESO's own webhook TLS cert |
| `external-secrets` | `external-secrets-webhook` | external-secrets:v2.0.1 | Validates ExternalSecret resource format |
| `ingress-nginx` | `ingress-nginx-controller` | ingress-nginx/controller:v1.14.3 | NGINX reverse proxy and Ingress controller |
| `kube-system` | `coredns` | eks/coredns:v1.11.4 | In-cluster DNS (`svc.cluster.local`) |
| `kyverno` | `kyverno-admission-controller` | kyverno/kyverno:v1.17.1 | Admission webhook — enforces image signing |
| `kyverno` | `kyverno-background-controller` | kyverno/background-controller:v1.17.1 | Async background compliance scanning |
| `persons-finder` | `persons-finder` | ECR `persons-finder:git-0e2835b` + `pii-redaction-sidecar:git-0e2835b` | **Business app (2 containers: Spring Boot API + Go PII sidecar)** |

### Pods by Node

**Node 1 — `ip-10-1-2-119` (persons-finder-nodes-prod) — 8/11**

| Pod | Namespace | Ready | Purpose |
|-----|-----------|-------|---------|
| `aws-node-f8wmn` | kube-system | 2/2 | AWS VPC CNI — assigns VPC IPs to pods |
| `kube-proxy-8xgpf` | kube-system | 1/1 | Maintains iptables rules for Service routing |
| `fluent-bit-ddfj5` | kube-system | 1/1 | Collects container stdout logs → CloudWatch |
| `kyverno-admission-controller-*` | kyverno | 1/1 | Admission webhook — blocks unsigned images |
| `kyverno-background-controller-*` | kyverno | 1/1 | Background async policy compliance |
| `cert-manager-*` | cert-manager | 1/1 | TLS certificate lifecycle (Let's Encrypt) |
| `cert-manager-cainjector-*` | cert-manager | 1/1 | CA injection into webhook configurations |
| `cert-manager-webhook-*` | cert-manager | 1/1 | Certificate resource validation |

**Node 2 — `ip-10-1-3-167` (system-nodes-prod, tainted) — 4/11**

| Pod | Namespace | Ready | Purpose |
|-----|-----------|-------|---------|
| `aws-node-2frlf` | kube-system | 2/2 | AWS VPC CNI |
| `kube-proxy-9khl8` | kube-system | 1/1 | Service routing rules |
| `fluent-bit-v9862` | kube-system | 1/1 | Log collection → CloudWatch |
| `ingress-nginx-controller-*` | ingress-nginx | 1/1 | External HTTPS entry point, TLS termination, Swagger Basic Auth |

**Node 3 — `ip-10-1-3-25` (persons-finder-nodes-prod) — 8/11**

| Pod | Namespace | Ready | Purpose |
|-----|-----------|-------|---------|
| `aws-node-2hcf6` | kube-system | 2/2 | AWS VPC CNI |
| `kube-proxy-hscph` | kube-system | 1/1 | Service routing rules |
| `fluent-bit-swvvb` | kube-system | 1/1 | Log collection → CloudWatch (incl. PII audit logs) |
| `coredns-*` | kube-system | 1/1 | In-cluster DNS resolution |
| `external-secrets-*` | external-secrets | 1/1 | Syncs `OPENAI_API_KEY` from AWS Secrets Manager |
| `external-secrets-cert-controller-*` | external-secrets | 1/1 | ESO internal TLS cert management |
| `external-secrets-webhook-*` | external-secrets | 1/1 | ESO resource format validation |
| `persons-finder-*` | persons-finder | **2/2** | **① Spring Boot REST API  ② Go PII Redaction Sidecar (port 8081)** |

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
