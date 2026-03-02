# Persons Finder Helm Chart

This Helm chart deploys the Persons Finder Spring Boot application to Kubernetes.

## Prerequisites

- Kubernetes 1.19+
- Helm 3.0+
- kubectl configured to access your cluster

## Installation

### Install with default values

```bash
helm install persons-finder ./devops/helm/persons-finder
```

### Install with development values

```bash
helm install persons-finder ./devops/helm/persons-finder -f ./devops/helm/persons-finder/values-dev.yaml
```

### Install with production values

```bash
helm install persons-finder ./devops/helm/persons-finder -f ./devops/helm/persons-finder/values-prod.yaml
```

### Install with custom values

```bash
helm install persons-finder ./devops/helm/persons-finder \
  --set image.tag=1.0.0 \
  --set replicaCount=3 \
  --set ingress.enabled=true
```

## Configuration

The following table lists the configurable parameters and their default values.

| Parameter | Description | Default |
|-----------|-------------|---------|
| `replicaCount` | Number of replicas | `2` |
| `image.repository` | Image repository | `190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder` |
| `image.tag` | Image tag | `latest` |
| `image.pullPolicy` | Image pull policy | `IfNotPresent` |
| `service.type` | Service type | `ClusterIP` |
| `service.port` | Service port | `80` |
| `ingress.enabled` | Enable ingress | `false` |
| `resources.limits.cpu` | CPU limit | `1000m` |
| `resources.limits.memory` | Memory limit | `1Gi` |
| `autoscaling.enabled` | Enable HPA | `true` |
| `autoscaling.minReplicas` | Minimum replicas | `2` |
| `autoscaling.maxReplicas` | Maximum replicas | `10` |
| `sidecar.enabled` | Enable PII redaction sidecar | `false` |

## Secrets Management

Create a Kubernetes secret for the OpenAI API key:

```bash
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=your-api-key-here
```

Or set it in values.yaml:

```yaml
secrets:
  create: true
  OPENAI_API_KEY: "your-api-key-here"
```

## Upgrading

```bash
helm upgrade persons-finder ./devops/helm/persons-finder
```

## Uninstalling

```bash
helm uninstall persons-finder
```

## Testing

Lint the chart:

```bash
helm lint ./devops/helm/persons-finder
```

Render templates:

```bash
helm template persons-finder ./devops/helm/persons-finder
```

Dry run installation:

```bash
helm install persons-finder ./devops/helm/persons-finder --dry-run --debug
```
