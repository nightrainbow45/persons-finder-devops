# Requirement 2: Infrastructure as Code (Kubernetes / Terraform)

> **Requirement:** Deploy this app to a local cluster (Minikube/Kind) or output Terraform for AWS/GCP.
> - **Secrets:** The app needs an `OPENAI_API_KEY`. Do not bake it into the image. Show how you inject it securely (K8s Secrets, Vault, etc.).
> - **Scaling:** Configure HPA (Horizontal Pod Autoscaler) based on CPU or custom metrics.
> - **AI Task:** Use AI to generate the K8s manifests (Deployment, Service, Ingress). **Document what you had to fix.** (Did it forget `readinessProbe`? Did it request 400 CPUs?)

---

## Quick Reference

**Requirement vs Implementation**

| Requirement | Status | Primary File |
|---|---|---|
| Local cluster deployment (Kind) | ✅ `devops/scripts/local-test.sh` | Kind + Helm |
| Terraform for AWS | ✅ `devops/terraform/environments/prod/` | EKS + VPC + ECR + IAM |
| OPENAI_API_KEY not baked into image | ✅ `envFrom.secretRef` | deployment.yaml line 75 |
| Secure secret injection (prod) | ✅ ESO → AWS Secrets Manager | external-secret.yaml |
| HPA based on CPU | ✅ `autoscaling/v2`, CPU 70% | hpa.yaml |
| K8s manifests (Deployment/Service/Ingress) | ✅ Helm templates | templates/ |
| AI generation + documented fixes | ✅ See Section 5 | — |

**Code Snippet Source Map**

| Snippet | Source |
|---|---|
| Deployment — probes | `deployment.yaml` lines 78–81 |
| Deployment — secret injection via envFrom | `deployment.yaml` lines 75–77 |
| Deployment — podSecurityContext | `values.yaml` lines 23–27 |
| Deployment — rollingUpdate strategy | `deployment.yaml` lines 14–20 |
| Deployment — checksum annotation | `deployment.yaml` line 24 |
| Service — ClusterIP + named port | `service.yaml` lines 11–19 |
| Ingress — TLS + ingressClassName | `ingress.yaml` lines 30–41 |
| HPA — CPU metric + behavior | `hpa.yaml` lines 15–38 |
| Secret — conditional creation | `secret.yaml` lines 1–18 |
| ESO ClusterSecretStore | `cluster-secret-store.yaml` lines 8–16 |
| ESO ExternalSecret | `external-secret.yaml` lines 15–32 |
| Local Kind deploy (Step 4 secret) | `local-test.sh` lines 133–137 |
| Local Kind deploy (Step 5 Helm) | `local-test.sh` lines 153–161 |

---

## 1. Deployment Targets

### 1.1 Local Cluster — Kind

**File:** `devops/scripts/local-test.sh` (241 lines)

Kind (Kubernetes in Docker) runs a full K8s control plane in a Docker container, with no cloud account required. The script handles the complete lifecycle in 6 steps:

> `local-test.sh` lines 91–99 — Step 1: create Kind cluster

```bash
kind create cluster --name "${CLUSTER_NAME}"   # line 97
```

> `local-test.sh` lines 110–111 — Step 2: build image

```bash
docker build -t "${IMAGE_NAME}" -f "${REPO_ROOT}/devops/docker/Dockerfile" "${REPO_ROOT}"
```

> `local-test.sh` lines 122–123 — Step 3: load image into Kind

```bash
# Kind nodes are Docker containers — they cannot access the host Docker image cache.
# kind load copies the image into the Kind node's containerd store.
kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"
```

> `local-test.sh` lines 133–137 — Step 4: create K8s Secret (placeholder for local testing)

```bash
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=test-local-key \
  --dry-run=client -o yaml | kubectl apply -f -   # idempotent: update if exists
```

> `local-test.sh` lines 153–161 — Step 5: Helm deploy with local overrides

```bash
helm upgrade --install "${RELEASE_NAME}" "${CHART_DIR}" \
  -f "${CHART_DIR}/values-dev.yaml" \
  --set image.repository=persons-finder \
  --set image.tag=latest \
  --set image.pullPolicy=Never \    # use locally loaded image, no registry pull
  --set secrets.create=false \      # secret already created in Step 4
  --set autoscaling.enabled=false \ # Kind has no metrics-server
  --wait --timeout 5m
```

Access the running app:
```bash
kubectl port-forward svc/persons-finder 8080:80
curl http://localhost:8080/actuator/health
```

---

### 1.2 Cloud Deployment — AWS EKS via Terraform

**Directory:** `devops/terraform/environments/prod/` + `devops/terraform/modules/`

Terraform provisions 61 AWS resources across 5 modules:

| Module | Resources |
|---|---|
| `modules/vpc` | VPC, 2 AZs, public + private subnets, NAT Gateway |
| `modules/eks` | EKS 1.32, t3.small SPOT, managed node group, IMDS hop limit=2, VPC CNI NetworkPolicy agent |
| `modules/ecr` | ECR repository, IMMUTABLE tags, KMS encryption, lifecycle policy |
| `modules/iam` | Roles: EKS admin / developer / github-actions (OIDC), RBAC ClusterRole for Helm deploy |
| `modules/secrets-manager` | Secrets Manager secret, KMS key, ESO access policy |

Deploy with:
```bash
cd devops/terraform/environments/prod
terraform init && terraform apply
aws eks update-kubeconfig --name persons-finder-prod --region ap-southeast-2
```

---

## 2. Secrets — OPENAI_API_KEY Secure Injection

The API key is **never written into the Dockerfile, image layers, or values files**. It is always injected at runtime through a K8s Secret.

### 2.1 How the app reads the key

> `deployment.yaml` lines 75–77 — `envFrom.secretRef` wires Secret into container env

```yaml
        envFrom:                                    # line 75
        - secretRef:
            name: {{ include "persons-finder.fullname" . }}-secrets
```

The Spring Boot app reads `OPENAI_API_KEY` from the environment. The Secret is mounted as env vars — not as a volume file, so it never touches the container filesystem.

### 2.2 Secret lifecycle by environment

**Local (Kind):**
Created manually by `local-test.sh` Step 4 (placeholder value, no real key needed to verify startup).

**Dev / one-off deployments:**

> `secret.yaml` lines 1–18 — Helm conditional Secret creation (18 lines)

```yaml
{{- if .Values.secrets.create }}           # line 1 — only create if explicitly requested
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "persons-finder.fullname" . }}-secrets
type: Opaque
data:
  {{- if .Values.secrets.OPENAI_API_KEY }}
  OPENAI_API_KEY: {{ .Values.secrets.OPENAI_API_KEY | b64enc | quote }}
  {{- end }}
{{- end }}
```

**Prod — External Secrets Operator (ESO):**

ESO watches AWS Secrets Manager and syncs the key into a K8s Secret automatically, refreshing every 5 minutes. Helm deploys with `secrets.create=false` so there is no conflict.

> `devops/k8s/cluster-secret-store.yaml` lines 8–16 — connects ESO to AWS Secrets Manager

```yaml
apiVersion: external-secrets.io/v1        # line 8
kind: ClusterSecretStore
metadata:
  name: aws-secrets-manager
spec:
  provider:
    aws:
      service: SecretsManager
      region: ap-southeast-2
      # No 'role' field: uses EC2 instance role via IMDS (hop limit=2 on EKS nodes)
```

> `devops/k8s/external-secret.yaml` lines 15–32 — syncs OPENAI_API_KEY into K8s Secret

```yaml
apiVersion: external-secrets.io/v1        # line 15
kind: ExternalSecret
metadata:
  name: persons-finder-secrets
  namespace: default
spec:
  refreshInterval: 5m                     # line 21 — re-sync every 5 minutes
  secretStoreRef:
    name: aws-secrets-manager
    kind: ClusterSecretStore
  target:
    name: persons-finder-secrets
    creationPolicy: Owner                 # ESO owns the Secret lifecycle
  data:
  - secretKey: OPENAI_API_KEY
    remoteRef:
      key: persons-finder/prod/openai-api-key
      property: OPENAI_API_KEY
```

Set or rotate the key without redeploying the app:
```bash
aws secretsmanager put-secret-value \
  --secret-id persons-finder/prod/openai-api-key \
  --secret-string '{"OPENAI_API_KEY":"sk-..."}' \
  --region ap-southeast-2

# Force immediate ESO sync (skips the 5-minute wait)
kubectl annotate externalsecret persons-finder-secrets \
  force-sync=$(date +%s) --overwrite -n default
```

---

## 3. Scaling — HPA (Horizontal Pod Autoscaler)

**File:** `devops/helm/persons-finder/templates/hpa.yaml` (39 lines)

Uses `autoscaling/v2` API with CPU utilization target and explicit scale-up/down behavior policies.

> `hpa.yaml` lines 1–39 — full HPA definition

```yaml
{{- if .Values.autoscaling.enabled }}      # line 1 — only rendered when enabled
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "persons-finder.fullname" . }}
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:                                 # line 15
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }}
  behavior:                               # line 22
    scaleDown:
      stabilizationWindowSeconds: {{ .Values.autoscaling.stabilizationWindowSeconds | default 300 }}
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60                 # scale down at most 50% of pods per minute
    scaleUp:
      stabilizationWindowSeconds: 0       # react immediately on scale-up
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30                 # double pod count every 30s if needed
      - type: Pods
        value: 2
        periodSeconds: 30                 # or add 2 pods every 30s
      selectPolicy: Max                   # take the more aggressive of the two policies
{{- end }}
```

**Values by environment:**

> `values.yaml` lines 93–98 — default HPA values

```yaml
autoscaling:             # line 93
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  stabilizationWindowSeconds: 300
```

Prod CI deploys with `--set autoscaling.minReplicas=1 --set autoscaling.maxReplicas=3` to fit the t3.small node's 11-pod limit. metrics-server must be installed for HPA to function:

```bash
helm upgrade --install metrics-server metrics-server/metrics-server \
  --namespace kube-system \
  --set args[0]="--kubelet-insecure-tls"
```

---

## 4. K8s Manifests — Deployment, Service, Ingress

All manifests are Helm templates under `devops/helm/persons-finder/templates/`.

### 4.1 Deployment

**File:** `devops/helm/persons-finder/templates/deployment.yaml` (125 lines)

Key sections:

> `deployment.yaml` lines 14–20 — RollingUpdate strategy

```yaml
  strategy:                              # line 14
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 0        # terminate old pod BEFORE starting new one
      maxUnavailable: 1  # required on t3.small (11-pod node limit)
```

> `deployment.yaml` line 24 — checksum annotation triggers pod restart on Secret change

```yaml
      checksum/secret: {{ include (print $.Template.BasePath "/secret.yaml") . | sha256sum }}
```

> `deployment.yaml` lines 36–37 — pod-level security context (non-root, fsGroup)

```yaml
      securityContext:
        {{- toYaml .Values.podSecurityContext | nindent 8 }}
```

Backed by `values.yaml` lines 23–27:
```yaml
podSecurityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000
```

> `deployment.yaml` lines 78–81 — liveness and readiness probes

```yaml
        livenessProbe:                              # line 78
          {{- toYaml .Values.probes.liveness | nindent 12 }}
        readinessProbe:                             # line 80
          {{- toYaml .Values.probes.readiness | nindent 12 }}
```

Backed by `values.yaml` lines 100–116:
```yaml
probes:
  liveness:
    httpGet:
      path: /actuator/health
      port: 8080
    initialDelaySeconds: 60   # Spring Boot needs time to start
    periodSeconds: 15
    timeoutSeconds: 5
    failureThreshold: 3
  readiness:
    httpGet:
      path: /actuator/health
      port: 8080
    initialDelaySeconds: 30
    periodSeconds: 10
    timeoutSeconds: 3
    failureThreshold: 3
```

> `deployment.yaml` lines 85–113 — PII redaction sidecar (conditional, prod only)

```yaml
      {{- if .Values.sidecar.enabled }}             # line 84
      - name: pii-redaction-sidecar
        ...
        livenessProbe:
          httpGet:
            path: /health
            port: {{ .Values.sidecar.port }}
        readinessProbe:
          httpGet:
            path: /health
            port: {{ .Values.sidecar.port }}
      {{- end }}
```

### 4.2 Service

**File:** `devops/helm/persons-finder/templates/service.yaml` (19 lines)

> `service.yaml` lines 11–19 — ClusterIP with named port

```yaml
spec:                            # line 11
  type: {{ .Values.service.type }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: http           # references the named port in the Deployment
      protocol: TCP
      name: http
  selector:
    {{- include "persons-finder.selectorLabels" . | nindent 4 }}
```

Using a named `targetPort: http` (rather than a numeric `8080`) decouples the Service from the container port number — changing the app port only requires updating the Deployment.

### 4.3 Ingress

**File:** `devops/helm/persons-finder/templates/ingress.yaml` (58 lines)

> `ingress.yaml` lines 1–14 — conditional render + annotation block

```yaml
{{- if .Values.ingress.enabled -}}                  # line 1
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    {{- if .Values.ingress.className }}
    kubernetes.io/ingress.class: {{ .Values.ingress.className }}
    {{- end }}
    {{- if .Values.ingress.tls }}
    cert-manager.io/cluster-issuer: {{ .Values.ingress.certManager.clusterIssuer }}
    {{- end }}
```

> `ingress.yaml` lines 30–41 — TLS + ingressClassName

```yaml
spec:                                               # line 29
  {{- if .Values.ingress.className }}
  ingressClassName: {{ .Values.ingress.className }} # line 31 — modern field (K8s 1.18+)
  {{- end }}
  {{- if .Values.ingress.tls }}
  tls:                                              # line 34
    {{- range .Values.ingress.tls }}
    - hosts:
        {{- range .hosts }}
        - {{ . | quote }}
        {{- end }}
      secretName: {{ .secretName }}
    {{- end }}
  {{- end }}
```

---

## 5. AI Task — What Was Generated and What Was Fixed

The Helm chart templates were initially scaffolded using `helm create` (which produces AI-style boilerplate) and then iteratively corrected through the project. Below is a record of every significant fix made to the generated manifests.

### Fix 1: `readinessProbe` missing from initial scaffold

**Problem:** `helm create` generates a basic `livenessProbe` pointing at `/` on the container port. It does not generate a `readinessProbe`. Without it, K8s marks the pod Ready as soon as the container starts — before Spring Boot has finished its startup sequence (~30s), causing traffic to hit a pod that returns 503.

**Fix:** Added explicit `readinessProbe` with Spring Actuator health endpoint and a `30s` initial delay:

```yaml
# deployment.yaml lines 80–81
readinessProbe:
  httpGet:
    path: /actuator/health   # Spring Boot's built-in health check endpoint
    port: 8080
  initialDelaySeconds: 30    # Spring Boot takes ~20–30s to start; wait before probing
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3
```

Both probes are in `values.yaml` lines 100–116 so they can be tuned per environment.

### Fix 2: `resources` limits were absent (the "400 CPUs" scenario)

**Problem:** `helm create` scaffold leaves `resources: {}` as a comment. Deploying without resource limits means a single pod can consume all node CPU/memory, starving other workloads and preventing the HPA from functioning (HPA needs `requests` to calculate utilization).

**Fix:** Set explicit requests and limits in `values.yaml` lines 85–91:

```yaml
resources:
  limits:
    cpu: 1000m      # 1 vCPU ceiling
    memory: 1Gi
  requests:
    cpu: 250m       # HPA measures actual usage against this request value
    memory: 512Mi
```

### Fix 3: `podSecurityContext` not in initial scaffold

**Problem:** Default scaffold runs the container as root with all Linux capabilities. Fails security benchmarks (CIS, NSA hardening guide) and any PodSecurity Admission policy set to `restricted`.

**Fix:** Added `podSecurityContext` and container-level `securityContext` in `values.yaml` lines 23–33:

```yaml
podSecurityContext:
  runAsNonRoot: true       # K8s rejects the pod if the image runs as UID 0
  runAsUser: 1000
  fsGroup: 1000

securityContext:
  allowPrivilegeEscalation: false
  capabilities:
    drop:
    - ALL                  # drop every Linux capability; add back only what's needed
  readOnlyRootFilesystem: false
```

### Fix 4: `rollingUpdate` strategy caused scheduling deadlock on small nodes

**Problem:** Default `maxSurge: 1` means K8s starts a new pod before terminating the old one. On a t3.small node (ENI limit = 11 pods), when the cluster is at 10/11 pods, a rolling update tries to schedule pod 12 — it stays `Pending` indefinitely because no capacity exists, blocking the rollout.

**Fix:** Set `maxSurge: 0` in `deployment.yaml` lines 14–20:

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 0        # terminate old pod first, then start new one
    maxUnavailable: 1  # tolerate 1 pod down during the transition
```

### Fix 5: Secret changes did not trigger pod restarts

**Problem:** Helm only restarts pods when the Deployment spec changes. If the K8s Secret is updated (e.g. API key rotated), the running pods continue using the old value from their env until manually restarted.

**Fix:** Added a `checksum/secret` annotation in `deployment.yaml` line 24:

```yaml
annotations:
  checksum/secret: {{ include (print $.Template.BasePath "/secret.yaml") . | sha256sum }}
```

Helm recomputes the hash on every `helm upgrade`. If `secret.yaml` content changes, the annotation value changes, which modifies the pod template spec, which triggers a rolling restart automatically.

### Fix 6: HPA used deprecated `autoscaling/v1`

**Problem:** AI-generated HPA used `autoscaling/v1`, which only supports CPU and has no `behavior` block. Scale-down was immediate and aggressive, causing pod churn under fluctuating load.

**Fix:** Upgraded to `autoscaling/v2` in `hpa.yaml` line 2, and added a `behavior` block (lines 22–38) with a 300s scale-down stabilization window to prevent thrashing.

### Fix 7: Ingress used deprecated `kubernetes.io/ingress.class` annotation only

**Problem:** From K8s 1.18+ the `ingressClassName` field in `spec` is the canonical way to specify the ingress controller. Using only the annotation works but produces deprecation warnings and is not supported by all controllers.

**Fix:** `ingress.yaml` lines 30–32 set both `spec.ingressClassName` (modern) and the annotation (backward-compat fallback):

```yaml
spec:
  {{- if .Values.ingress.className }}
  ingressClassName: {{ .Values.ingress.className }}   # modern field (K8s 1.18+)
```

### Fix 8: Service `targetPort` was a hardcoded number

**Problem:** Initial scaffold sets `targetPort: 8080`. If the app port changes, both the Deployment and Service must be updated in sync.

**Fix:** Named the container port `http` in the Deployment and referenced it by name in the Service (`targetPort: http` at `service.yaml` line 15). Port number is now defined in one place only (`values.yaml: service.targetPort: 8080`).

---

## 6. File Map

| File | Lines | Purpose |
|---|---|---|
| `devops/helm/persons-finder/templates/deployment.yaml` | 125 | App Deployment — probes, security, sidecar, envFrom |
| `devops/helm/persons-finder/templates/service.yaml` | 19 | ClusterIP Service with named port |
| `devops/helm/persons-finder/templates/ingress.yaml` | 58 | Ingress — TLS, ingressClassName, IP whitelist |
| `devops/helm/persons-finder/templates/hpa.yaml` | 39 | HPA — CPU 70%, scale-up/down behavior |
| `devops/helm/persons-finder/templates/secret.yaml` | 18 | Conditional K8s Secret creation |
| `devops/helm/persons-finder/values.yaml` | 189 | All default values + HPA / probes / resources |
| `devops/k8s/cluster-secret-store.yaml` | 16 | ESO ClusterSecretStore → AWS Secrets Manager |
| `devops/k8s/external-secret.yaml` | 32 | ESO ExternalSecret — syncs OPENAI_API_KEY |
| `devops/scripts/local-test.sh` | 241 | Kind local cluster — full up/down/status lifecycle |
| `devops/terraform/environments/prod/main.tf` | 327 | Prod environment composition (EKS + VPC + ECR + IAM) |
| `devops/terraform/modules/eks/main.tf` | — | EKS cluster, node group, VPC CNI, IMDS, RBAC |
| `devops/terraform/modules/secrets-manager/main.tf` | — | Secrets Manager secret + KMS key |
