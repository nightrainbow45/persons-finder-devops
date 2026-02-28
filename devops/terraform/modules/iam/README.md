# IAM Module

This module creates IAM resources for GitHub Actions OIDC authentication and EKS access.

## Resources Created

- GitHub OIDC Identity Provider
- IAM roles (github-actions, eks-admin, eks-developer)
- IAM policies for ECR push, EKS access, and deployment

## Usage

```hcl
module "iam" {
  source = "../../modules/iam"
  
  github_org        = "your-org"
  github_repo       = "your-repo"
  aws_account_id    = "123456789012"
  cluster_name      = "persons-finder-eks"
}
```

## Outputs

- `github_actions_role_arn` - ARN of the GitHub Actions IAM role
- `oidc_provider_arn` - ARN of the OIDC Identity Provider
