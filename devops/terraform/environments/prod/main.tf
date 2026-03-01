# Prod Environment - Persons Finder Infrastructure
# Composes all modules with production-grade settings

terraform {
  required_version = ">= 1.5.0"

  backend "s3" {
    bucket         = "persons-finder-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "ap-southeast-2"
    encrypt        = true
    dynamodb_table = "persons-finder-terraform-locks"
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", local.cluster_name]
  }
}

# --- Local Variables ---

locals {
  cluster_name = "${var.project_name}-${var.environment}"
}

# --- Variables ---

variable "project_name" {
  type    = string
  default = "persons-finder"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "aws_region" {
  type    = string
  default = "ap-southeast-2"
}

variable "aws_account_id" {
  type = string
}

variable "github_org" {
  type = string
}

variable "github_repo" {
  type    = string
  default = "persons-finder"
}

# --- IAM Module ---

module "iam" {
  source = "../../modules/iam"

  project_name        = var.project_name
  environment         = var.environment
  aws_region          = var.aws_region
  github_org          = var.github_org
  github_repo         = var.github_repo
  cluster_name        = local.cluster_name
  ecr_repository_name = var.project_name
}

# --- VPC Module ---

module "vpc" {
  source = "../../modules/vpc"

  project_name       = var.project_name
  environment        = var.environment
  vpc_cidr           = "10.1.0.0/16"
  az_count           = 2
  cluster_name       = local.cluster_name
  enable_nat_gateway = true
  single_nat_gateway = true  # Single NAT gateway to reduce cost
}

# --- EKS Module ---

module "eks" {
  source = "../../modules/eks"

  project_name              = var.project_name
  environment               = var.environment
  cluster_name              = local.cluster_name
  kubernetes_version        = "1.32"
  public_subnet_ids         = module.vpc.public_subnet_ids
  private_subnet_ids        = module.vpc.private_subnet_ids
  cluster_security_group_id = module.vpc.eks_cluster_security_group_id
  node_security_group_id    = module.vpc.eks_nodes_security_group_id

  # Demo: smallest viable EKS node (t3.small = 2GiB, Free Tier Eligible) with SPOT for cost savings
  node_instance_type = "t3.small"
  capacity_type      = "SPOT"
  node_desired_count = 1
  node_min_count     = 1
  node_max_count     = 3

  cluster_log_types = ["audit"]

  eks_admin_role_arn      = module.iam.eks_admin_role_arn
  eks_developer_role_arn  = module.iam.eks_developer_role_arn
  github_actions_role_arn = module.iam.github_actions_role_arn
}

# --- ECR Module ---

module "ecr" {
  source = "../../modules/ecr"

  project_name            = var.project_name
  environment             = var.environment
  repository_name         = var.project_name
  image_tag_mutability    = "IMMUTABLE"  # Immutable tags in prod
  max_image_count         = 50
  github_actions_role_arn = module.iam.github_actions_role_arn
  eks_node_role_arn       = module.eks.node_role_arn
  enable_access_policy    = true
}

# --- Secrets Manager Module ---

module "secrets" {
  source = "../../modules/secrets-manager"

  project_name = var.project_name
  environment  = var.environment

  secrets = {
    openai-api-key = {
      description         = "OpenAI API key for Persons Finder"
      value               = ""  # Set via AWS Console or CLI
      rotation_lambda_arn = ""  # Configure rotation Lambda when available
      rotation_days       = 90
    }
  }

  kms_deletion_window_days = 30
  recovery_window_days     = 30
  eks_node_role_arn        = module.eks.node_role_arn
  enable_secret_policy     = true
}

# --- cosign KMS signing policy for GitHub Actions ---
# Added here (not in IAM module) to avoid circular dependency:
#   ECR module uses github_actions_role_arn → depends on IAM
#   IAM module would need cosign_key_arn → depends on ECR → circular
resource "aws_iam_role_policy" "github_actions_cosign" {
  name = "cosign-kms"
  role = module.iam.github_actions_role_name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "AllowCosignSign"
        Effect   = "Allow"
        Action   = ["kms:Sign", "kms:GetPublicKey", "kms:DescribeKey"]
        Resource = module.ecr.cosign_key_arn
      }
    ]
  })
}

# --- Outputs ---

output "vpc_id" {
  value = module.vpc.vpc_id
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "ecr_repository_url" {
  value = module.ecr.repository_url
}

output "github_actions_role_arn" {
  value = module.iam.github_actions_role_arn
}

output "kubeconfig_command" {
  value = "aws eks update-kubeconfig --region ${var.aws_region} --name ${local.cluster_name}"
}

output "cosign_key_arn" {
  value = module.ecr.cosign_key_arn
}
