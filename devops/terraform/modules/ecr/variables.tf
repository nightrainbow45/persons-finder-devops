# ECR Module Variables

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "environment" {
  description = "Deployment environment (dev, prod)"
  type        = string
}

variable "repository_name" {
  description = "Name of the ECR repository"
  type        = string
}

variable "image_tag_mutability" {
  description = "Image tag mutability setting (MUTABLE or IMMUTABLE)"
  type        = string
  default     = "MUTABLE"
}

variable "max_image_count" {
  description = "Maximum number of tagged images to retain"
  type        = number
  default     = 30
}

variable "github_actions_role_arn" {
  description = "ARN of the GitHub Actions IAM role for push access"
  type        = string
  default     = ""
}

variable "eks_node_role_arn" {
  description = "ARN of the EKS node IAM role for pull access"
  type        = string
  default     = ""
}

variable "enable_access_policy" {
  description = "Whether to create the ECR repository access policy (requires github_actions_role_arn and eks_node_role_arn)"
  type        = bool
  default     = false
}

variable "kms_deletion_window_days" {
  description = "KMS key deletion window in days (7-30)"
  type        = number
  default     = 30
}

variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}
