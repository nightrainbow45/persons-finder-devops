# Terraform Infrastructure

This directory contains Terraform configurations for provisioning AWS infrastructure.

## Structure

- `modules/` - Reusable Terraform modules (IAM, VPC, EKS, ECR, Secrets Manager)
- `environments/` - Environment-specific configurations (dev, prod)
- `backend.tf` - Terraform backend configuration
- `versions.tf` - Terraform and provider version constraints
- `variables.tf` - Global variables
- `outputs.tf` - Global outputs

## Prerequisites

- Terraform >= 1.0
- AWS CLI configured with appropriate credentials
- kubectl for EKS cluster access

## Usage

```bash
# Initialize Terraform
cd devops/terraform/environments/dev
terraform init

# Plan changes
terraform plan

# Apply changes
terraform apply

# Destroy infrastructure
terraform destroy
```

## Modules

- **iam** - IAM roles, policies, and GitHub OIDC Provider
- **vpc** - VPC, subnets, security groups
- **eks** - EKS cluster and node groups
- **ecr** - Container registry
- **secrets-manager** - Secrets management

See individual module READMEs for detailed documentation.
