# 2. Persons Finder — Architecture Diagram

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Internet / Users                               │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │ HTTPS
                                 │
┌────────────────────────────────▼────────────────────────────────────────┐
│                          AWS Cloud (ap-southeast-2)                     │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Application Load Balancer (ALB)                                  │  │
│  │  • TLS Termination (Let's Encrypt)                                │  │
│  │  • Health Checks                                                  │  │
│  └─────────────────────────────┬─────────────────────────────────────┘  │
│                                │                                        │
│  ┌─────────────────────────────▼─────────────────────────────────────┐  │
│  │  EKS Cluster (persons-finder-prod)                                │  │
│  │                                                                   │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │  NGINX Ingress Controller                                   │  │  │
│  │  │  • Rate Limiting: 100 req/s                                 │  │  │
│  │  │  • Path-based routing                                       │  │  │
│  │  └───────────────────────┬─────────────────────────────────────┘  │  │
│  │                          │                                         │  │
│  │  ┌───────────────────────▼─────────────────────────────────────┐  │  │
│  │  │  Service (ClusterIP)                                        │  │  │
│  │  │  Port: 8080                                                 │  │  │
│  │  └───────────────────────┬─────────────────────────────────────┘  │  │
│  │                          │                                         │  │
│  │  ┌───────────────────────▼─────────────────────────────────────┐  │  │
│  │  │  Deployment (3-20 replicas, HPA enabled)                    │  │  │
│  │  │                                                             │  │  │
│  │  │  ┌─────────────────────────────────────────────────────┐    │  │  │
│  │  │  │  Pod 1                                              │    │  │  │
│  │  │  │  ┌──────────────────┐  ┌──────────────────────┐    │    │  │  │
│  │  │  │  │  Main Container  │  │  Sidecar Container   │    │    │  │  │
│  │  │  │  │  (Spring Boot)   │  │  (PII Proxy)         │    │    │  │  │
│  │  │  │  │                  │  │                      │    │    │  │  │
│  │  │  │  │  • REST API      │◄─┤  • PII Detection    │    │    │  │  │
│  │  │  │  │  • Business      │  │  • Tokenization      │    │    │  │  │
│  │  │  │  │    Logic         │  │  • Audit Logging     │    │    │  │  │
│  │  │  │  │  • H2 Database   │  │                      │    │    │  │  │
│  │  │  │  │                  │  │  Port: 8081          │    │    │  │  │
│  │  │  │  │  Port: 8080      │  │                      │    │    │  │  │
│  │  │  │  └──────────────────┘  └──────────┬───────────┘    │    │  │  │
│  │  │  │                                   │                │    │  │  │
│  │  │  │  Network Policy (Zero-Trust)      │                │    │  │  │
│  │  │  │  • Ingress: Only from NGINX       │                │    │  │  │
│  │  │  │  • Egress: DNS + Internal + HTTPS │                │    │  │  │
│  │  │  └───────────────────────────────────┼────────────────┘    │  │  │
│  │  │                                      │                     │  │  │
│  │  │  ┌─────────────────────────────────┐│                     │  │  │
│  │  │  │  Pod 2 (same structure)         ││                     │  │  │
│  │  │  └─────────────────────────────────┘│                     │  │  │
│  │  │                                      │                     │  │  │
│  │  │  ┌─────────────────────────────────┐│                     │  │  │
│  │  │  │  Pod 3 (same structure)         ││                     │  │  │
│  │  │  └─────────────────────────────────┘│                     │  │  │
│  │  │                                      │                     │  │  │
│  │  │  ... (up to 20 pods based on CPU)   │                     │  │  │
│  │  └──────────────────────────────────────┼─────────────────────┘  │  │
│  │                                         │                        │  │
│  └─────────────────────────────────────────┼────────────────────────┘  │
│                                            │                           │
│  ┌─────────────────────────────────────────▼────────────────────────┐  │
│  │  Supporting AWS Services                                         │  │
│  │                                                                  │  │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │  │
│  │  │  CloudWatch      │  │  ECR             │  │  Secrets      │  │  │
│  │  │  • Logs          │  │  • App Image     │  │  Manager      │  │  │
│  │  │  • Metrics       │  │  • Sidecar Image │  │  • API Keys   │  │  │
│  │  │  • Alarms        │  │  • Signed        │  │  • Encrypted  │  │  │
│  │  └──────────────────┘  └──────────────────┘  └───────────────┘  │  │
│  │                                                                  │  │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │  │
│  │  │  VPC             │  │  IAM (IRSA)      │  │  KMS          │  │  │
│  │  │  • Private       │  │  • Pod Roles     │  │  • Cosign Key │  │  │
│  │  │    Subnets       │  │  • No Node Creds │  │  • Secrets    │  │  │
│  │  │  • 3 AZs         │  │                  │  │    Encryption │  │  │
│  │  └──────────────────┘  └──────────────────┘  └───────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                            │                           │
└────────────────────────────────────────────┼───────────────────────────┘
                                             │ (Redacted PII only)
                                             ▼
                                    ┌─────────────────┐
                                    │  api.openai.com │
                                    │  (External LLM) │
                                    └─────────────────┘
```

## Data Flow: API Request

```
1. User Request
   │
   ▼
2. ALB (TLS Termination)
   │
   ▼
3. NGINX Ingress (Rate Limiting)
   │
   ▼
4. Service (Load Balancing)
   │
   ▼
5. Pod Selected (Round-robin)
   │
   ├─► Main Container (Spring Boot)
   │   │
   │   ├─► PersonController (REST API)
   │   │
   │   ├─► PersonsService (Business Logic)
   │   │
   │   └─► H2 Database (In-memory)
   │
   └─► Sidecar Container (PII Protection)
       │
       └─► If LLM call needed:
           │
           ├─► Layer 1: PiiProxyService (In-process)
           │   • Detect PII (names, coordinates)
           │   • Tokenize: "John" → "<NAME_a3f2>"
           │
           ├─► Layer 2: Sidecar Proxy (Go service)
           │   • Validate no raw PII
           │   • Forward to OpenAI
           │
           ├─► Layer 3: Network Policy (Kernel)
           │   • Check egress rules
           │   • Allow only approved destinations
           │
           └─► Layer 4: Audit Log (CloudWatch)
               • Log PII detection events
               • Alert on anomalies
```

## PII Protection Architecture (4 Layers)

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: In-Process Redaction (PiiProxyService)               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  • PiiDetector: Regex-based detection                    │  │
│  │  • PiiRedactor: Reversible tokenization                  │  │
│  │  • AuditLogger: Structured JSON logs                     │  │
│  │  • Language: Kotlin (Spring Boot)                        │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 2: Sidecar Proxy (Language-Agnostic)                    │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  • HTTP intercept on localhost:8081                      │  │
│  │  • Secondary PII scan (defense in depth)                 │  │
│  │  • Forwards to real LLM provider                         │  │
│  │  • Language: Go (stdlib only, ~150 lines)                │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 3: Network Policy (Kernel-Level)                        │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  • Default-deny egress                                    │  │
│  │  • Allow: DNS (53), Internal, HTTPS (443)                │  │
│  │  • Enforced by: AWS VPC CNI + eBPF                       │  │
│  │  • Cannot be bypassed by application code                │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 4: Observability & Alerting                             │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  • CloudWatch: PII audit logs                            │  │
│  │  • Prometheus: Redaction metrics                         │  │
│  │  • Alerts: Zero redactions = potential leak              │  │
│  │  • Kyverno: Image signature verification                 │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Auto-Scaling Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Horizontal Pod Autoscaler (HPA)                            │
│                                                             │
│  Trigger: CPU > 70%                                         │
│  Min Replicas: 3                                            │
│  Max Replicas: 20                                           │
│  Scale Up: +1 pod every 30s                                 │
│  Scale Down: -1 pod every 5m (gradual)                      │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Current Load: 45% CPU                                      │
│  ┌─────┐ ┌─────┐ ┌─────┐                                   │
│  │ Pod │ │ Pod │ │ Pod │  ← 3 replicas (baseline)          │
│  │  1  │ │  2  │ │  3  │                                   │
│  └─────┘ └─────┘ └─────┘                                   │
└─────────────────────────────────────────────────────────────┘
                         │
                         │ Traffic increases
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Current Load: 75% CPU (above threshold)                    │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐                  │
│  │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │  ← Scaled to 5    │
│  │  1  │ │  2  │ │  3  │ │  4  │ │  5  │                  │
│  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘                  │
└─────────────────────────────────────────────────────────────┘
                         │
                         │ Traffic spike
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Current Load: 85% CPU (sustained)                          │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐                  │
│  │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │                  │
│  │  1  │ │  2  │ │  3  │ │  4  │ │  5  │                  │
│  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘                  │
│  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐                  │
│  │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │  ← Up to 20 max   │
│  │  6  │ │  7  │ │  8  │ │  9  │ │ 10  │                  │
│  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘                  │
│  ... (continues to 20 if needed)                            │
└─────────────────────────────────────────────────────────────┘
```

## Security Layers

```
┌─────────────────────────────────────────────────────────────┐
│  1. Network Security                                        │
│  ├─ VPC: Private subnets only                              │
│  ├─ Security Groups: Minimal ports                         │
│  ├─ Network Policies: Zero-trust egress                    │
│  └─ TLS: All traffic encrypted                             │
└─────────────────────────────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  2. Identity & Access                                       │
│  ├─ IRSA: Pod-level IAM roles                              │
│  ├─ No node-level credentials                              │
│  ├─ Secrets Manager: Encrypted at rest                     │
│  └─ External Secrets Operator: Auto-sync                   │
└─────────────────────────────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  3. Container Security                                      │
│  ├─ Cosign: Image signing (AWS KMS)                        │
│  ├─ Kyverno: Signature verification (enforce mode)         │
│  ├─ Trivy: CVE scanning in CI/CD                           │
│  ├─ Non-root: All containers run as UID 1000              │
│  └─ Read-only root filesystem                              │
└─────────────────────────────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  4. Data Protection                                         │
│  ├─ PII Redaction: 4-layer architecture                    │
│  ├─ Audit Logging: All PII events logged                   │
│  ├─ Encryption: At rest (KMS) and in transit (TLS)        │
│  └─ No PII in logs or external calls                       │
└─────────────────────────────────────────────────────────────┘
```

## CI/CD Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│  GitHub Actions Workflow                                    │
│                                                             │
│  1. Code Push                                               │
│     ├─ Checkout code                                        │
│     └─ Set up build environment                             │
│                                                             │
│  2. Build & Test                                            │
│     ├─ Gradle build                                         │
│     ├─ Run unit tests                                       │
│     └─ Generate test reports                                │
│                                                             │
│  3. Security Scanning                                       │
│     ├─ Trivy: Scan for CVEs                                │
│     ├─ Fail if CRITICAL vulnerabilities                     │
│     └─ Generate SBOM (Software Bill of Materials)          │
│                                                             │
│  4. Container Build                                         │
│     ├─ Build main app image                                │
│     ├─ Build PII sidecar image                             │
│     └─ Multi-stage Dockerfiles (optimized)                 │
│                                                             │
│  5. Image Signing                                           │
│     ├─ Cosign sign with AWS KMS key                        │
│     ├─ Push to ECR                                          │
│     └─ Tag: git SHA + semantic version                     │
│                                                             │
│  6. Deploy to EKS                                           │
│     ├─ Helm upgrade (rolling update)                        │
│     ├─ Wait for rollout completion                          │
│     └─ Run smoke tests                                      │
│                                                             │
│  7. Verify                                                  │
│     ├─ Kyverno validates signatures                         │
│     ├─ Health checks pass                                   │
│     └─ Notify on success/failure                            │
└─────────────────────────────────────────────────────────────┘
```

## Monitoring & Observability

```
┌─────────────────────────────────────────────────────────────┐
│  Application Metrics (Actuator / Prometheus-ready)          │
│  ├─ /actuator/health (liveness + readiness probes)         │
│  ├─ /actuator/prometheus (scrape endpoint, port 8080)      │
│  └─ Annotations: prometheus.io/scrape=true in prod         │
└─────────────────────────────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Log Pipeline: Fluent Bit → CloudWatch                      │
│                                                             │
│  Fluent Bit DaemonSet (kube-system)                         │
│  ├─ Input: tail /var/log/containers/persons-finder-*.log   │
│  ├─ Filter: grep type = "PII_AUDIT"                        │
│  ├─ Parser: pii_audit_json                                  │
│  └─ Output: CloudWatch Logs                                 │
│                                                             │
│  CloudWatch Log Group: /eks/persons-finder/pii-audit        │
│  ├─ Retention: 90 days                                      │
│  └─ Structured JSON with requestId, piiDetected, etc.      │
└─────────────────────────────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  CloudWatch Metric Filters & Alarms                         │
│  ├─ pii-audit-total: { $.type = "PII_AUDIT" }              │
│  │    → Metric: PersonsFinder/PII / PiiAuditTotal          │
│  ├─ pii-zero-redactions:                                    │
│  │    { $.type = "PII_AUDIT" && $.redactionsApplied = 0 }  │
│  │    → Metric: PersonsFinder/PII / PiiZeroRedactions      │
│  └─ Alarm: persons-finder-pii-leak-risk                     │
│       Trigger: PiiZeroRedactions Sum ≥ 1 in 5 min          │
│       Action: SNS → Email / PagerDuty (configurable)       │
└─────────────────────────────────────────────────────────────┘
```

**Verified:** `verify.sh prod` — 17/17 checks passing ✅ (Fluent Bit ready, log group exists, alarm active)

---

**Legend:**
- `┌─┐ └─┘` = Component boundaries
- `│ ▼` = Data flow direction
- `◄─►` = Bidirectional communication
- `├─` = Sub-component or feature

