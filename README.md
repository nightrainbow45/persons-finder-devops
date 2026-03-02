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

## 📚 Documentation

Full implementation details for each requirement are in the [`docs/`](docs/) directory.

| Document | Covers |
|---|---|
| [requirement-overview.md](docs/requirement-overview.md) | Single-page summary of all requirements, architecture diagram, test coverage |
| [ApplicationDesignAndAPI.md](docs/ApplicationDesignAndAPI.md) | REST endpoints, 3-layer architecture, Haversine, OpenAPI/Swagger |
| [ContainerizationAndDockerfile.md](docs/ContainerizationAndDockerfile.md) | AI-generated Dockerfile, identified flaws, applied fixes |
| [InfrastructureAsCode.md](docs/InfrastructureAsCode.md) | Terraform (AWS), Helm chart, ESO secret injection, HPA, AI manifest fixes |
| [SecurityAndSecrets.md](docs/SecurityAndSecrets.md) | Secrets management, RBAC, NetworkPolicy, pod security context, cosign, Kyverno |
| [CICDandAIUsage.md](docs/CICDandAIUsage.md) | GitHub Actions pipeline, Trivy scan gate, SBOM, image signing, periodic re-scan |
| [ObservabilityAndMonitoring.md](docs/ObservabilityAndMonitoring.md) | Structured logging, Fluent Bit, CloudWatch metrics & alarm, Actuator health probes |
| [AILLMIntegration.md](docs/AILLMIntegration.md) | PII proxy pipeline (Kotlin + Go sidecar), reversible tokenization, audit log |

---

## 📝 Mandatory: The AI Log (`AI_LOG.md`)

We hire engineers who know how to collaborate with machines.
Please verify your work by documenting:

1.  **The Prompt:** "I asked ChatGPT: *'Write a K8s deployment for a Spring Boot app'*."
2.  **The Flaw:** "It gave me a deployment running as `root` and with no resource limits."
3.  **The Fix:** "I modified lines 12-15 to add `securityContext`."

**If you do not include this log, we will not review your submission.**

---

## ✅ Getting Started

1.  Clone this repo.
2.  Assume the code inside is a buildable Spring Boot app (or build it with `./gradlew build`).
3.  Push your solution (Dockerfile, K8s manifests/Terraform, CI configs) to your own public repository.

## 📬 Submission

Submit your repository link. We care about:
*   **Security:** How you handle the API Key.
*   **Reliability:** Probes, Limits, Scaling.
*   **AI Maturity:** Your `AI_LOG.md` (Did you blindly trust the bot, or did you engineer it?).
