# IAM Module / IAM 模块

This module creates IAM resources for GitHub Actions OIDC authentication and EKS access.
本模块创建 GitHub Actions OIDC 认证及 EKS 访问所需的 IAM 资源。

## Resources Created / 创建的资源

- GitHub OIDC Identity Provider
  GitHub OIDC 身份提供商
- IAM roles (github-actions, eks-admin, eks-developer)
  IAM 角色（github-actions、eks-admin、eks-developer）
- IAM policies for ECR push, EKS access, and deployment
  ECR 推送、EKS 访问和部署所需的 IAM 策略

## Usage / 使用方式

```hcl
module "iam" {
  source = "../../modules/iam"

  github_org        = "your-org"
  github_repo       = "your-repo"
  aws_account_id    = "123456789012"
  cluster_name      = "persons-finder-eks"
}
```

## Outputs / 输出

- `github_actions_role_arn` — ARN of the GitHub Actions IAM role / GitHub Actions IAM 角色的 ARN
- `oidc_provider_arn` — ARN of the OIDC Identity Provider / OIDC 身份提供商的 ARN
