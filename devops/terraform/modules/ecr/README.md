# ECR Module

This module creates an Amazon ECR repository for storing Docker images.

## Resources Created

- ECR repository
- Lifecycle policies for image retention
- Repository policies for access control
- Image scanning configuration

## Usage

```hcl
module "ecr" {
  source = "../../modules/ecr"
  
  repository_name = "persons-finder"
  image_tag_mutability = "MUTABLE"
  scan_on_push = true
}
```

## Outputs

- `repository_url` - ECR repository URL
- `repository_arn` - ECR repository ARN
