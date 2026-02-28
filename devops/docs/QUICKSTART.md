# Persons Finder — Quick Start Guide

Get the Persons Finder application running in minutes. Choose your path:

- [Local (Kind)](#local-deployment-with-kind) — No cloud account needed, great for development
- [AWS EKS](#aws-eks-deployment) — Production-grade Kubernetes on AWS

---

## Local Deployment with Kind

**Time:** ~5 minutes

### Prerequisites

```bash
# Install required tools (macOS example)
brew install docker kind kubectl helm
```

### Deploy

```bash
# One-command local deployment
./devops/scripts/local-test.sh up
```

This script will:
1. Create a Kind cluster named `persons-finder`
2. Build the Docker image from `devops/docker/Dockerfile`
3. Load the image into Kind
4. Create a test secret
5. Deploy with Helm using dev values

### Verify

```bash
# Check pods are running
kubectl get pods

# Port-forward and test
kubectl port-forward svc/persons-finder 8080:80 &
curl http://localhost:8080/actuator/health
```

### Tear Down

```bash
./devops/scripts/local-test.sh down
```

---

## AWS EKS Deployment

**Time:** ~20 minutes (cluster provisioning takes ~15 min)

### Prerequisites

```bash
# Install tools
brew install awscli terraform kubectl helm

# Configure AWS credentials
aws configure
# Region: ap-southeast-2
```

### Step 1: Provision EKS Cluster

```bash
./devops/scripts/setup-eks.sh dev
```

This runs Terraform to create the VPC, EKS cluster, and node groups.

### Step 2: Create Secret

```bash
kubectl create namespace dev
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=<your-key> \
  -n dev
```

### Step 3: Deploy

```bash
./devops/scripts/deploy.sh dev --tag latest
```

### Step 4: Verify

```bash
./devops/scripts/verify.sh dev
```

### Tear Down

```bash
./devops/scripts/teardown-eks.sh dev
```

---

## Troubleshooting

### Pods stuck in `Pending`

Nodes may not have enough resources. Check node status:

```bash
kubectl describe nodes
kubectl get events --sort-by='.lastTimestamp'
```

### Pods in `CrashLoopBackOff`

Usually a missing secret or application startup error:

```bash
kubectl logs <pod-name>
kubectl describe pod <pod-name>
```

Common fix — create the secret if missing:

```bash
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=<your-key> -n dev
```

### `ImagePullBackOff`

The cluster can't pull the container image.

**Local (Kind):** Make sure you loaded the image:
```bash
kind load docker-image persons-finder:latest --name persons-finder
```

**EKS:** Verify ECR authentication:
```bash
aws ecr get-login-password --region ap-southeast-2 | \
  docker login --username AWS --password-stdin \
  190239490233.dkr.ecr.ap-southeast-2.amazonaws.com
```

### Helm install fails with "namespace not found"

Add `--create-namespace` or create it first:

```bash
kubectl create namespace dev
```

### Health check probe failures

The app may need more startup time. Increase the initial delay:

```bash
helm upgrade persons-finder ./devops/helm/persons-finder \
  --set probes.readiness.initialDelaySeconds=60 \
  --set probes.liveness.initialDelaySeconds=90 \
  -n dev
```

### Terraform state lock error

Another Terraform process may be running. If you're sure it's stale:

```bash
terraform -chdir=devops/terraform/environments/dev force-unlock <LOCK_ID>
```

---

## What's Next?

- Full deployment guide: [DEPLOYMENT.md](./DEPLOYMENT.md)
- Terraform infrastructure details: [devops/terraform/README.md](../terraform/README.md)
- CI/CD pipeline: [devops/ci/ci-cd.yml](../ci/ci-cd.yml)
- Helm chart docs: [devops/helm/persons-finder/README.md](../helm/persons-finder/README.md)
