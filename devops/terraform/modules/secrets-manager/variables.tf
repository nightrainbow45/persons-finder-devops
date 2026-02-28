# Secrets Manager Module Variables

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev, prod)"
  type        = string
}

variable "secrets" {
  description = "Map of secrets to create"
  type = map(object({
    description         = string
    value               = string
    rotation_lambda_arn = string
    rotation_days       = number
  }))
  default = {}
}

variable "kms_deletion_window_days" {
  description = "Number of days before KMS key deletion"
  type        = number
  default     = 30
}

variable "recovery_window_days" {
  description = "Number of days for secret recovery window"
  type        = number
  default     = 30
}

variable "eks_node_role_arn" {
  description = "ARN of the EKS node IAM role for secret access"
  type        = string
  default     = ""
}

variable "enable_secret_policy" {
  description = "Whether to create secret access policies (requires eks_node_role_arn)"
  type        = bool
  default     = false
}

variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}
