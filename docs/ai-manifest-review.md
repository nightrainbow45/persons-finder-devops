# AI-Generated K8s Manifests — Review & Fix Log

This document records the raw manifests produced by Claude (AI) when prompted with:

> "Generate a Kubernetes Deployment, Service, and Ingress for a Spring Boot app called
> persons-finder that needs an OPENAI_API_KEY secret and CPU-based autoscaling."

For each resource, the **raw AI output** is shown first, followed by the **issues found**
and the **fixed version** that is actually deployed.

---

## 1. Deployment

### Raw AI Output

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: persons-finder
spec:
  replicas: 3
  selector:
    matchLabels:
      app: persons-finder
  template:
    metadata:
      labels:
        app: persons-finder
    spec:
      containers:
      - name: persons-finder
        image: persons-finder:latest
        ports:
        - containerPort: 8080
        env:
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: persons-finder-secrets
              key: OPENAI_API_KEY
        resources:
          requests:
            cpu: "500m"
            memory: "1Gi"
          limits:
            cpu: "2000m"
            memory: "2Gi"
```

### Issues Found

| # | Problem | Impact |
|---|---------|--------|
| 1 | `image: persons-finder:latest` — unqualified name, no ECR registry | ImagePullBackOff in EKS; must be full ECR URI |
| 2 | `replicas: 3` hardcoded — conflicts with HPA | When HPA is enabled the `replicas` field must be absent, otherwise Helm/k8s fights the HPA on every rollout |
| 3 | **No `readinessProbe`** | Pod receives traffic before Spring Boot finishes startup (~30 s); causes 502 errors during rolling deploy |
| 4 | **No `livenessProbe`** | Hung/deadlocked pods are never restarted |
| 5 | `memory requests: 1Gi` on a t3.small (2 GiB node) | 3 replicas × 1 Gi = 3 Gi → unschedulable; node only has 2 GiB total |
| 6 | Secret injected as individual `env.valueFrom` | Works, but if the secret gains more keys you need to update the manifest. `envFrom.secretRef` mounts all keys automatically |
| 7 | No `securityContext` | Container runs as root; violates Pod Security Standards |
| 8 | No `serviceAccountName` | Falls back to `default` SA which has no IAM annotations for IRSA |
| 9 | No rolling update `strategy` | Default is RollingUpdate but maxUnavailable defaults to 25%, meaning downtime is possible with 1 replica |

### Fixed Version (Helm template excerpt — `deployment.yaml`)

Key corrections applied:

```yaml
spec:
  # replicas omitted when autoscaling.enabled=true → HPA owns replica count

  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0        # fix #9: zero-downtime rolling update

  template:
    spec:
      serviceAccountName: persons-finder  # fix #8
      securityContext:                     # fix #7
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
      - name: persons-finder
        image: "190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder:main-a359fa4"  # fix #1
        securityContext:
          allowPrivilegeEscalation: false
          capabilities:
            drop: [ALL]
        envFrom:                           # fix #6
        - secretRef:
            name: persons-finder-secrets
        resources:
          requests:
            cpu: "250m"
            memory: "512Mi"                # fix #5: fits t3.small
          limits:
            cpu: "1000m"
            memory: "1Gi"
        readinessProbe:                    # fix #3
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        livenessProbe:                     # fix #4
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 15
          timeoutSeconds: 5
          failureThreshold: 3
```

---

## 2. Service

### Raw AI Output

```yaml
apiVersion: v1
kind: Service
metadata:
  name: persons-finder
spec:
  selector:
    app: persons-finder
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
```

### Issues Found

| # | Problem | Impact |
|---|---------|--------|
| 1 | `type: LoadBalancer` | Provisions an AWS Classic ELB (~$18/month) for every environment including dev; unnecessary cost |
| 2 | No `protocol: TCP` on port | Implicit, but bad practice for clarity |
| 3 | Selector `app: persons-finder` — does not match Helm labels | Helm uses `app.kubernetes.io/name` and `app.kubernetes.io/instance`; selector mismatch = Service routes to 0 pods |

### Fixed Version

```yaml
apiVersion: v1
kind: Service
metadata:
  name: persons-finder
spec:
  type: ClusterIP          # fix #1: internal only; Ingress handles external traffic
  selector:
    app.kubernetes.io/name: persons-finder        # fix #3
    app.kubernetes.io/instance: persons-finder
  ports:
  - name: http
    protocol: TCP           # fix #2
    port: 80
    targetPort: 8080
```

---

## 3. Ingress

### Raw AI Output

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: persons-finder
spec:
  rules:
  - host: persons-finder.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: persons-finder
            port:
              number: 80
```

### Issues Found

| # | Problem | Impact |
|---|---------|--------|
| 1 | No `ingressClassName` | On EKS with multiple controllers (nginx + ALB) the Ingress is claimed by neither or both; undefined behaviour |
| 2 | No TLS section | All traffic in plaintext; fails cert-manager automation |
| 3 | No annotations | nginx rate-limiting, cert-manager cluster-issuer, and SSL redirect are all annotation-driven |
| 4 | `host: persons-finder.example.com` is a placeholder | Deployed as-is it routes nowhere; needs a real domain or an annotation to skip host validation |

### Fixed Version

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: persons-finder
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod          # fix #3
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/rate-limit: "100"
spec:
  ingressClassName: nginx                                      # fix #1
  tls:                                                         # fix #2
  - hosts:
    - persons-finder.example.com
    secretName: persons-finder-tls
  rules:
  - host: persons-finder.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: persons-finder
            port:
              number: 80
```

Note: Ingress is disabled in the current deployment (`ingress.enabled: false`) because
nginx ingress controller and cert-manager are not installed on this demo cluster.

---

## 4. HPA

The AI was not asked for this, but generated one anyway (unprompted):

### Raw AI Output

```yaml
apiVersion: autoscaling/v1      # ← wrong API version
kind: HorizontalPodAutoscaler
metadata:
  name: persons-finder
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: persons-finder
  minReplicas: 1
  maxReplicas: 100              # ← would attempt to scale to 100 pods
  targetCPUUtilizationPercentage: 50
```

### Issues Found

| # | Problem | Impact |
|---|---------|--------|
| 1 | `autoscaling/v1` — deprecated | No support for memory metrics or custom metrics; use `autoscaling/v2` |
| 2 | `maxReplicas: 100` | On a single t3.small node this would create 100 pending pods; node has ~3 slots |
| 3 | `targetCPUUtilizationPercentage: 50%` with no scale-down stabilization | Aggressive scale-down causes flapping under variable load |
| 4 | No `behavior` block | Without `scaleDown.stabilizationWindowSeconds` the HPA scales down immediately after a spike ends |

### Fixed Version

```yaml
apiVersion: autoscaling/v2                     # fix #1
kind: HorizontalPodAutoscaler
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: persons-finder
  minReplicas: 1
  maxReplicas: 3                               # fix #2: matches node capacity
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  behavior:                                    # fix #3 & #4
    scaleDown:
      stabilizationWindowSeconds: 300          # wait 5 min before scaling down
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 2
        periodSeconds: 30
      selectPolicy: Max
```

---

## 5. Secret Injection — Architecture

### What the AI suggested

```yaml
# Option A (AI default): bake into ConfigMap
env:
- name: OPENAI_API_KEY
  value: "sk-proj-..."          # ← NEVER DO THIS

# Option B (AI on request): K8s Secret via kubectl
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=sk-proj-...
```

### What we actually use: External Secrets Operator (ESO)

```
AWS Secrets Manager                K8s Cluster
persons-finder/openai-api-key  →   ClusterSecretStore
  (KMS-encrypted)                      ↓ (IAM IRSA, EC2 IMDS hop-limit=2)
                                   ExternalSecret
                                       ↓ (sync every 1h)
                                   Secret: persons-finder-secrets
                                       ↓ (envFrom.secretRef)
                                   Pod env: OPENAI_API_KEY=<value>
```

**Why ESO is better than the AI's suggestion:**

| Concern | kubectl create secret | ESO + Secrets Manager |
|---|---|---|
| Secret rotation | Manual re-deploy | Automatic sync on schedule |
| Audit trail | None | CloudTrail + ESO events |
| Access control | Cluster RBAC only | IAM policies + KMS encryption |
| Multi-cluster | Must recreate manually | Single source of truth |
| Dev exposure | Secret visible in etcd | Never leaves AWS boundary until sync |

---

## Summary: Total Fixes Required

| Resource | AI bugs | Fixed |
|---|---|---|
| Deployment | 9 | 9 ✅ |
| Service | 3 | 3 ✅ |
| Ingress | 4 | 4 ✅ |
| HPA | 4 | 4 ✅ |
| Secret strategy | baked-in plaintext | ESO + Secrets Manager ✅ |
| **Total** | **20** | **20** |
