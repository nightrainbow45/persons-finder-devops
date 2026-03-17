# ECR Module / ECR 模块

This module creates an Amazon ECR repository for storing Docker images.
本模块创建用于存储 Docker 镜像的 Amazon ECR 仓库。

## Resources Created / 创建的资源

- ECR repository
  ECR 镜像仓库
- Lifecycle policies for image retention
  镜像保留生命周期策略
- Repository policies for access control
  访问控制的仓库策略
- Image scanning configuration
  镜像扫描配置

## Usage / 使用方式

```hcl
module "ecr" {
  source = "../../modules/ecr"

  repository_name      = "persons-finder"
  image_tag_mutability = "MUTABLE"
  scan_on_push         = true
}
```

## Outputs / 输出

- `repository_url` — ECR repository URL / ECR 仓库 URL
- `repository_arn` — ECR repository ARN / ECR 仓库 ARN
