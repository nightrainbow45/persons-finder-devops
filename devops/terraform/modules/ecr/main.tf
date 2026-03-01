# ECR Module - Container Registry
# Creates ECR repository with scanning, lifecycle policies, and access control

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

# --- KMS Key for ECR Encryption ---

resource "aws_kms_key" "ecr" {
  description             = "KMS key for ECR repository ${var.repository_name} (${var.environment})"
  deletion_window_in_days = var.kms_deletion_window_days
  enable_key_rotation     = true

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "EnableRootManagement"
        Effect = "Allow"
        Principal = {
          AWS = "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      },
      {
        Sid    = "AllowEKSNodeDecrypt"
        Effect = "Allow"
        Principal = {
          AWS = var.eks_node_role_arn
        }
        Action   = ["kms:Decrypt", "kms:DescribeKey"]
        Resource = "*"
      },
      {
        Sid    = "AllowCICDPush"
        Effect = "Allow"
        Principal = {
          AWS = var.github_actions_role_arn
        }
        Action   = ["kms:GenerateDataKey", "kms:Decrypt", "kms:DescribeKey"]
        Resource = "*"
      }
    ]
  })

  tags = merge(var.tags, {
    Name = "${var.project_name}-ecr-kms-${var.environment}"
  })
}

resource "aws_kms_alias" "ecr" {
  name          = "alias/${var.project_name}-ecr-${var.environment}"
  target_key_id = aws_kms_key.ecr.key_id
}

# --- ECR Repository ---

resource "aws_ecr_repository" "main" {
  name                 = var.repository_name
  image_tag_mutability = var.image_tag_mutability
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.ecr.arn
  }

  tags = merge(var.tags, {
    Name = "${var.project_name}-ecr-${var.environment}"
  })
}

# --- Lifecycle Policy ---
# Tag strategy:
#   git-<sha>  → CI build artifacts (main branch) — keep last N, auto-expire old ones
#   <semver>   → Release tags (1.4.2) — NO rule = kept forever by default
#   pr-<num>   → Pull request builds — expire after 7 days
#   untagged   → Manifest list layers / intermediates — expire after 1 day

resource "aws_ecr_lifecycle_policy" "main" {
  repository = aws_ecr_repository.main.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Expire PR images after 7 days"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["pr-"]
          countType     = "sinceImagePushed"
          countUnit     = "days"
          countNumber   = 7
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 3
        description  = "Keep last ${var.max_image_count} CI build images (git-*)"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["git-"]
          countType     = "imageCountMoreThan"
          countNumber   = var.max_image_count
        }
        action = { type = "expire" }
      }
      # Semver release tags (e.g. 1.4.2) have no rule → kept forever
    ]
  })
}

# --- KMS Key for Image Signing (cosign) ---
# Asymmetric key (ECC_NIST_P256) used by cosign to sign container images.
# Note: Asymmetric keys do NOT support automatic key rotation.

resource "aws_kms_key" "cosign" {
  description              = "Asymmetric KMS key for cosign image signing (${var.project_name}-${var.environment})"
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "ECC_NIST_P256"
  deletion_window_in_days  = var.kms_deletion_window_days
  enable_key_rotation      = false # Asymmetric keys do not support auto-rotation

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "EnableRootManagement"
        Effect = "Allow"
        Principal = {
          AWS = "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      },
      {
        Sid    = "AllowCICDSign"
        Effect = "Allow"
        Principal = {
          AWS = var.github_actions_role_arn
        }
        Action   = ["kms:Sign", "kms:GetPublicKey", "kms:DescribeKey"]
        Resource = "*"
      }
    ]
  })

  tags = merge(var.tags, {
    Name = "${var.project_name}-cosign-kms-${var.environment}"
  })
}

resource "aws_kms_alias" "cosign" {
  name          = "alias/${var.project_name}-cosign-${var.environment}"
  target_key_id = aws_kms_key.cosign.key_id
}

# --- Repository Policy ---
# Grants pull access to the EKS node role and push access to CI/CD role

resource "aws_ecr_repository_policy" "main" {
  count      = var.enable_access_policy ? 1 : 0
  repository = aws_ecr_repository.main.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowPull"
        Effect = "Allow"
        Principal = {
          AWS = var.eks_node_role_arn
        }
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability"
        ]
      },
      {
        Sid    = "AllowPushFromCI"
        Effect = "Allow"
        Principal = {
          AWS = var.github_actions_role_arn
        }
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload"
        ]
      }
    ]
  })
}
