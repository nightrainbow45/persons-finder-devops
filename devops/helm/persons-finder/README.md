# Persons Finder Helm Chart / Persons Finder Helm Chart

This Helm chart deploys the Persons Finder Spring Boot application to Kubernetes.
本 Helm Chart 用于将 Persons Finder Spring Boot 应用部署到 Kubernetes。

## Prerequisites / 前置条件

- Kubernetes 1.19+
- Helm 3.0+
- kubectl configured to access your cluster
  kubectl 已配置并可访问目标集群

## Installation / 安装

### Install with default values / 使用默认 values 安装

```bash
helm install persons-finder ./devops/helm/persons-finder
```

### Install with development values / 使用开发环境 values 安装

```bash
helm install persons-finder ./devops/helm/persons-finder -f ./devops/helm/persons-finder/values-dev.yaml
```

### Install with production values / 使用生产环境 values 安装

```bash
helm install persons-finder ./devops/helm/persons-finder -f ./devops/helm/persons-finder/values-prod.yaml
```

### Install with custom values / 使用自定义参数安装

```bash
helm install persons-finder ./devops/helm/persons-finder \
  --set image.tag=1.0.0 \
  --set replicaCount=3 \
  --set ingress.enabled=true
```

## Configuration / 配置参数

The following table lists the configurable parameters and their default values.
下表列出所有可配置参数及其默认值。

| Parameter / 参数 | Description / 说明 | Default / 默认值 |
|-----------|-------------|---------|
| `replicaCount` | Number of replicas / 副本数 | `2` |
| `image.repository` | Image repository / 镜像仓库 | `190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder` |
| `image.tag` | Image tag / 镜像标签 | `latest` |
| `image.pullPolicy` | Image pull policy / 镜像拉取策略 | `IfNotPresent` |
| `service.type` | Service type / Service 类型 | `ClusterIP` |
| `service.port` | Service port / Service 端口 | `80` |
| `ingress.enabled` | Enable ingress / 启用 Ingress | `false` |
| `resources.limits.cpu` | CPU limit / CPU 上限 | `1000m` |
| `resources.limits.memory` | Memory limit / 内存上限 | `1Gi` |
| `autoscaling.enabled` | Enable HPA / 启用 HPA | `true` |
| `autoscaling.minReplicas` | Minimum replicas / 最小副本数 | `2` |
| `autoscaling.maxReplicas` | Maximum replicas / 最大副本数 | `10` |
| `sidecar.enabled` | Enable PII redaction sidecar / 启用 PII 脱敏 Sidecar | `false` |

## Secrets Management / 密钥管理

Create a Kubernetes secret for the OpenAI API key:
创建存储 OpenAI API Key 的 Kubernetes Secret：

```bash
kubectl create secret generic persons-finder-secrets \
  --from-literal=OPENAI_API_KEY=your-api-key-here
```

Or set it in values.yaml:
或在 values.yaml 中配置：

```yaml
secrets:
  create: true
  OPENAI_API_KEY: "your-api-key-here"
```

## Upgrading / 升级

```bash
helm upgrade persons-finder ./devops/helm/persons-finder
```

## Uninstalling / 卸载

```bash
helm uninstall persons-finder
```

## Testing / 测试

Lint the chart:
对 Chart 进行 lint 检查：

```bash
helm lint ./devops/helm/persons-finder
```

Render templates:
渲染模板：

```bash
helm template persons-finder ./devops/helm/persons-finder
```

Dry run installation:
模拟安装（dry-run）：

```bash
helm install persons-finder ./devops/helm/persons-finder --dry-run --debug
```
