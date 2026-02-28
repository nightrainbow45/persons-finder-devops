# Terraform Infrastructure - Persons Finder

Terraform configurations for provisioning AWS infrastructure for the Persons Finder application, including VPC, EKS, ECR, IAM, and Secrets Manager.

## Prerequisites

- [Terraform](https://www.terraform.io/downloads) >= 1.5.0
- [AWS CLI](https://aws.amazon.com/cli/) v2, configured with appropriate credentials
- [kubectl](https://kubernetes.io/docs/tasks/tools/) for Kubernetes cluster management
- An AWS account with permissions to create the required resources
- An S3 bucket and DynamoDB table for Terraform state (see Bootstrap section)

## Architecture

```
┌─────────────────────────────────────────────────┐
│                    VPC                          │
│  ┌──────────────┐    ┌──────────────────────┐   │
│  │ Public Subnet│    │   Private Subnet      │   │
│  │  (ALB/NLB)  │    │  ┌─────────────────┐  │   │
│  │              │    │  │   EKS Cluster    │  │   │
│  │  Internet GW │    │  │  ┌───────────┐  │  │   │
│  │              │    │  │  │ Node Group│  │  │   │
│  └──────────────┘    │  │  └───────────┘  │  │   │
│         │            │  └─────────────────┘  │   │
│    NAT Gateway       │                       │   │
│         │            └──────────────────────┘   │
└─────────────────────────────────────────────────┘
         │
    ┌────┴────┐     ┌──────────────┐
    │   ECR   │     │   Secrets    │
    │  Repo   │     │   Manager    │
    └─────────┘     └──────────────┘
```

## Directory Structure

```
terraform/
├── backend.tf              # S3 backend configuration
├── versions.tf             # Provider version constraints
├── variables.tf            # Global variable definitions
├── outputs.tf              # Global outputs
├── README.md               # This file
├── modules/
│   ├── iam/                # IAM roles, policies, OIDC
│   ├── vpc/                # VPC, subnets, NAT, security groups
│   ├── eks/                # EKS cluster, node groups, add-ons
│   ├── ecr/                # ECR repository, lifecycle policies
│   └── secrets-manager/    # Secrets with KMS encryption
└── environments/
    ├── dev/                # Development environment
    │   ├── main.tf
    │   └── terraform.tfvars
    └── prod/               # Production environment
        ├── main.tf
        └── terraform.tfvars
```

## Bootstrap - State Backend

Before deploying any environment, create the S3 bucket and DynamoDB table for Terraform state:

```bash
# Create S3 bucket for state
aws s3api create-bucket \
  --bucket persons-finder-terraform-state \
  --region ap-southeast-2 \
  --create-bucket-configuration LocationConstraint=ap-southeast-2

# Enable versioning
aws s3api put-bucket-versioning \
  --bucket persons-finder-terraform-state \
  --versioning-configuration Status=Enabled

# Enable encryption
aws s3api put-bucket-encryption \
  --bucket persons-finder-terraform-state \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"aws:kms"}}]}'

# Create DynamoDB table for state locking
aws dynamodb create-table \
  --table-name persons-finder-terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region ap-southeast-2
```

## Deployment

### Dev Environment

```bash
cd devops/terraform/environments/dev

# Edit terraform.tfvars with your values
# Required: aws_account_id, github_org

# Initialize Terraform
terraform init

# Review the plan
terraform plan

# Apply changes
terraform apply
```

### Prod Environment

```bash
cd devops/terraform/environments/prod

# Edit terraform.tfvars with your values
terraform init
terraform plan
terraform apply
```

### Configure kubectl

After deploying, configure kubectl to access the cluster:

```bash
# The command is provided as a Terraform output
aws eks update-kubeconfig \
  --region ap-southeast-2 \
  --name persons-finder-dev   # or persons-finder-prod
```

## Variable Configuration

### Environment-Specific Settings

| Setting | Dev | Prod |
|---------|-----|------|
| VPC CIDR | 10.0.0.0/16 | 10.1.0.0/16 |
| Availability Zones | 2 | 3 |
| NAT Gateway | Single (cost saving) | Per-AZ (HA) |
| Node Instance Type | t3.medium | t3.large |
| Node Capacity | SPOT | ON_DEMAND |
| Desired Nodes | 2 | 3 |
| Max Nodes | 3 | 10 |
| ECR Image Retention | 10 | 50 |
| ECR Tag Mutability | MUTABLE | IMMUTABLE |
| KMS Deletion Window | 7 days | 30 days |
| Secret Recovery Window | 7 days | 30 days |

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `aws_account_id` | AWS account ID | `190239490233` |
| `github_org` | GitHub organization/user | `your-org` |
| `github_repo` | GitHub repository name | `persons-finder` |

### Optional Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `project_name` | `persons-finder` | Project name prefix |
| `aws_region` | `ap-southeast-2` | AWS region |

## Outputs

After applying Terraform, the following outputs are available:

| Output | Description |
|--------|-------------|
| `vpc_id` | ID of the created VPC |
| `eks_cluster_name` | Name of the EKS cluster |
| `eks_cluster_endpoint` | API endpoint URL for the EKS cluster |
| `ecr_repository_url` | URL for pushing Docker images |
| `github_actions_role_arn` | IAM role ARN for GitHub Actions OIDC |
| `kubeconfig_command` | Command to configure kubectl |

Access outputs:

```bash
terraform output                    # Show all outputs
terraform output ecr_repository_url # Show specific output
```

## Modules

### IAM Module (`modules/iam/`)
Creates GitHub OIDC provider, IAM roles (github-actions, eks-admin, eks-developer), and least-privilege policies for ECR push, EKS access, and deployment.

### VPC Module (`modules/vpc/`)
Creates VPC with public and private subnets across multiple AZs, internet gateway, NAT gateways, route tables, and security groups for EKS cluster and nodes.

### EKS Module (`modules/eks/`)
Creates EKS cluster with managed node groups, cluster add-ons (VPC CNI, CoreDNS, kube-proxy), IAM roles for nodes, and aws-auth ConfigMap for RBAC mapping.

### ECR Module (`modules/ecr/`)
Creates ECR repository with image scanning on push, lifecycle policies for image retention, and repository access policies for CI/CD and EKS nodes.

### Secrets Manager Module (`modules/secrets-manager/`)
Creates AWS Secrets Manager secrets with KMS encryption, optional rotation policies, and access policies for EKS nodes.

## Teardown

To destroy all resources in an environment:

```bash
cd devops/terraform/environments/dev  # or prod
terraform destroy
```

**Warning:** This will delete all infrastructure including the EKS cluster and all workloads running on it. Ensure you have backed up any important data before proceeding.

## Troubleshooting

### Common Issues

1. **State lock error**: If a previous apply was interrupted, release the lock:
   ```bash
   terraform force-unlock <LOCK_ID>
   ```

2. **EKS node join failure**: Check the aws-auth ConfigMap:
   ```bash
   kubectl get configmap aws-auth -n kube-system -o yaml
   ```

3. **ECR push denied**: Verify the GitHub Actions role has the ecr-push policy attached and OIDC is configured correctly.

4. **NAT Gateway costs**: Dev uses a single NAT gateway to reduce costs. If private subnet connectivity issues occur, verify the NAT gateway is healthy.
