# Secrets Manager Module
# Creates AWS Secrets Manager secrets with KMS encryption and rotation

# --- KMS Key for Secret Encryption ---

resource "aws_kms_key" "secrets" {
  description             = "KMS key for ${var.project_name} secrets encryption"
  deletion_window_in_days = var.kms_deletion_window_days
  enable_key_rotation     = true

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "Enable IAM User Permissions"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
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
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey"
        ]
        Resource = "*"
      }
    ]
  })

  tags = merge(var.tags, {
    Name = "${var.project_name}-secrets-kms-${var.environment}"
  })
}

data "aws_caller_identity" "current" {}

resource "aws_kms_alias" "secrets" {
  name          = "alias/${var.project_name}-secrets-${var.environment}"
  target_key_id = aws_kms_key.secrets.key_id
}

# --- Secrets ---

resource "aws_secretsmanager_secret" "secrets" {
  for_each = var.secrets

  name        = "${var.project_name}/${var.environment}/${each.key}"
  description = each.value.description
  kms_key_id  = aws_kms_key.secrets.arn

  recovery_window_in_days = var.recovery_window_days

  tags = merge(var.tags, {
    Name = "${var.project_name}-${each.key}-${var.environment}"
  })
}

# --- Secret Versions (initial placeholder values) ---

resource "aws_secretsmanager_secret_version" "secrets" {
  for_each = var.secrets

  secret_id     = aws_secretsmanager_secret.secrets[each.key].id
  secret_string = each.value.value != "" ? each.value.value : jsonencode({ "placeholder" = "CHANGE_ME" })

  lifecycle {
    ignore_changes = [secret_string]
  }
}

# --- Secret Rotation (optional) ---

resource "aws_secretsmanager_secret_rotation" "secrets" {
  for_each = { for k, v in var.secrets : k => v if v.rotation_lambda_arn != "" }

  secret_id           = aws_secretsmanager_secret.secrets[each.key].id
  rotation_lambda_arn = each.value.rotation_lambda_arn

  rotation_rules {
    automatically_after_days = each.value.rotation_days
  }
}

# --- Secret Access Policy ---

resource "aws_secretsmanager_secret_policy" "secrets" {
  for_each = var.enable_secret_policy ? var.secrets : {}

  secret_arn = aws_secretsmanager_secret.secrets[each.key].arn

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowEKSNodeAccess"
        Effect = "Allow"
        Principal = {
          AWS = var.eks_node_role_arn
        }
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = "*"
      }
    ]
  })
}
