# Persons Finder — Deployment Guide

This guide covers the full deployment lifecycle for the Persons Finder Spring Boot application, from local testing to production on AWS EKS.

## Prerequisites

Install the following tools before proceeding:

| Tool | Version | Purpose |
|------|---------|---------|
| [Docker](https://docs.docker.com/get-docker/) | 20.10+ | Container builds |
| [kubectl](https://kubernetes.io/docs/tasks/tools/) | 1.24+ | Kubernetes CLI |
| [Helm](https://helm.sh/docs/intro/install/) | 3.10+ | Kubernetes package manager |
| [Terraform](https://developer.hashicorp.com/terraform/downloads) | 1.3+ | Infrastructure provisioning |
| [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) | 2.x | AWS operations |
| [Kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) | 0.17+ | Local Kubernetes (optional) |

Verify installations:

```bash
docker --version
kubectl version --client
helm version
terraform --version
aws --version
```

## Project Structure

```
devops/
├── docker/                    # Dockerfile and .dockerignore
├── helm/persons-finder/       # Helm Chart
│   ├── Chart.yaml
│   ├── values.yaml            # Default values
│   ├── values-dev.yaml        # Dev overrides
│   ├── values-prod.yaml       # Prod overrides
│   └── templates/             # K8s resource templates
├── terraform/                 # AWS infrastructure (EKS, VPC, ECR, IAM)
│   ├── modules/               # Reusable Terraform modules
│   └── environments/          # dev/ and prod/ configurations
├── scripts/                   # Deployment helper scripts
├── ci/                        # GitHub Actions workflow
└── docs/                      # This documentation
```

## Helm Chart Overview

The Helm chart is located at `devops/helm/persons-finder/` and packages all Kubernetes resources.

### Chart Metadata

- **Name:** persons-finder
- **Version:** 0.1.0
- **App Version:** 1.0.0

### Key Values (values.yaml)

| Parameter | Default | Description |
|-----------|---------|-------------|
| `replicaCount` | `2` | Number of pod replicas |
| `image.repository` | `190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder` | Container image |
| `image.tag` | `latest` | Image tag |
| `service.type` | `ClusterIP` | Kubernetes service type |
| `service.port` | `80` | Service port |
| `service.targetPort` | `8080` | Container port |
| `autoscaling.enabled` | `true` | Enable HPA |
| `autoscaling.minReplicas` | `2` | Minimum replicas |
| `autoscaling.maxReplicas` | `10` | Maximum replicas |
| `autoscaling.targetCPUUtilizationPercentage` | `70` | CPU scale-up threshold |
| `resources.requests.cpu` | `250m` | CPU request |
| `resources.requests.memory` | `512Mi` | Memory request |
| `resources.limits.cpu` | `1000m` | CPU limit |
| `resources.limits.memory` | `1Gi` | Memory limit |
| `ingress.enabled` | `false` | Enable Ingress |
| `sidecar.enabled` | `false` | Enable PII redaction sidecar |
| `networkPolicy.enabled` | `false` | Enable NetworkPolicy |
| `secrets.create` | `true` | Create Secret resource |

### Environment-Specific Values

- **values-dev.yaml** — Lower resource limits, single replica, debug-friendly settings
- **values-prod.yaml** — Production-grade resources, HPA enabled, Ingress configured

## Secret Management

The application requires an `OPENAI_API_KEY` environment variable. Create the Kubernetes Secret **before** deploying the Helm chart.

### Option 1: Manual Secret Creation (Recommended for initial setup)

```bash
# Create the secret in your target namespace
kubectl create namespace prod  # if not exists
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=<your-api-key> \
  -n prod
```

Then disable chart-managed secrets:

```bash
helm upgrade --install persons-finder ./devops/helm/persons-finder \
  --set secrets.create=false \
  -n prod
```

### Option 2: Helm-Managed Secret

Set the API key in your values file or via `--set`:

```bash
helm upgrade --install persons-finder ./devops/helm/persons-finder \
  --set secrets.OPENAI_API_KEY=<your-api-key> \
  -n prod
```

> **Warning:** Avoid committing secrets to version control. Use `--set` on the command line or a values file excluded from Git.

## Local Testing with Kind

Use Kind to test the full Helm deployment locally.

### 1. Create a Kind Cluster

```bash
kind create cluster --name persons-finder
```

### 2. Build the Docker Image

```bash
docker build -t persons-finder:latest -f devops/docker/Dockerfile .
```

### 3. Load Image into Kind

```bash
kind load docker-image persons-finder:latest --name persons-finder
```

### 4. Create Secret

```bash
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=test-key
```

### 5. Deploy with Helm

```bash
helm upgrade --install persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/values-dev.yaml \
  --set image.repository=persons-finder \
  --set image.tag=latest \
  --set image.pullPolicy=Never
```

### 6. Verify and Test

```bash
kubectl get pods
kubectl port-forward svc/persons-finder 8080:80
# In another terminal:
curl http://localhost:8080/actuator/health
```

### 7. Cleanup

```bash
helm uninstall persons-finder
kind delete cluster --name persons-finder
```

Or use the helper script:

```bash
./devops/scripts/local-test.sh
```

## Deploying to AWS EKS

### 1. Provision Infrastructure with Terraform

```bash
# Initialize and apply for your target environment
cd devops/terraform/environments/dev  # or prod
terraform init
terraform plan
terraform apply
```

### 2. Configure kubectl

```bash
aws eks update-kubeconfig \
  --region ap-southeast-2 \
  --name persons-finder-dev  # or persons-finder-prod
```

### 3. Create Namespace and Secret

```bash
kubectl create namespace dev
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=<your-api-key> \
  -n dev
```

### 4. Deploy with Helm

**Development:**

```bash
helm upgrade --install persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/values-dev.yaml \
  --namespace dev --create-namespace \
  --set secrets.create=false
```

**Production:**

```bash
helm upgrade --install persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/values-prod.yaml \
  --namespace prod --create-namespace \
  --set secrets.create=false
```

## Verification

After deploying, verify the release and resources:

### Helm Status

```bash
helm status persons-finder -n prod
helm list -n prod
```

### Kubernetes Resources

```bash
kubectl get all -n prod
kubectl get pods -n prod
kubectl get svc -n prod
kubectl get ingress -n prod   # if ingress enabled
kubectl get hpa -n prod       # if autoscaling enabled
```

### Health Check

```bash
# Port-forward if no Ingress
kubectl port-forward svc/persons-finder 8080:80 -n prod

# Check health endpoint
curl http://localhost:8080/actuator/health
```

### Logs

```bash
kubectl logs -l app.kubernetes.io/name=persons-finder -n prod --tail=50
```

Or use the helper script:

```bash
./devops/scripts/verify.sh prod
```

## Rollback

Helm tracks release history, making rollbacks straightforward.

### View Release History

```bash
helm history persons-finder -n prod
```

### Rollback to Previous Revision

```bash
helm rollback persons-finder -n prod
```

### Rollback to a Specific Revision

```bash
helm rollback persons-finder 2 -n prod
```

### Verify Rollback

```bash
helm status persons-finder -n prod
kubectl get pods -n prod
```

## Upgrading

To deploy a new image version:

```bash
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/values-prod.yaml \
  --set image.tag=<new-tag> \
  --namespace prod
```

The rolling update strategy (`maxSurge=1, maxUnavailable=0`) ensures zero-downtime deployments.

## Troubleshooting

### Pods Not Starting

```bash
kubectl describe pod <pod-name> -n prod
kubectl logs <pod-name> -n prod
```

Common causes:
- Missing secret (`persons-finder-secrets`)
- Image pull errors (check ECR authentication)
- Insufficient resources on nodes

### ImagePullBackOff

```bash
# Verify ECR login
aws ecr get-login-password --region ap-southeast-2 | \
  docker login --username AWS --password-stdin \
  190239490233.dkr.ecr.ap-southeast-2.amazonaws.com

# Check image exists
aws ecr describe-images --repository-name persons-finder --region ap-southeast-2
```

### Health Check Failures

- Increase `probes.readiness.initialDelaySeconds` if the app needs more startup time
- Check application logs for startup errors
- Verify the `/actuator/health` endpoint is accessible on port 8080

### HPA Not Scaling

```bash
kubectl describe hpa persons-finder -n prod
kubectl top pods -n prod
```

Ensure `metrics-server` is installed in the cluster.
