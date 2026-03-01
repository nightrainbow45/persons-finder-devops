# ECR Module Outputs

output "repository_url" {
  description = "URL of the ECR repository"
  value       = aws_ecr_repository.main.repository_url
}

output "repository_arn" {
  description = "ARN of the ECR repository"
  value       = aws_ecr_repository.main.arn
}

output "repository_name" {
  description = "Name of the ECR repository"
  value       = aws_ecr_repository.main.name
}

output "registry_id" {
  description = "Registry ID of the ECR repository"
  value       = aws_ecr_repository.main.registry_id
}

output "kms_key_arn" {
  description = "ARN of the KMS key used for ECR encryption"
  value       = aws_kms_key.ecr.arn
}

output "kms_key_alias" {
  description = "Alias of the KMS key used for ECR encryption"
  value       = aws_kms_alias.ecr.name
}

output "cosign_key_arn" {
  description = "ARN of the asymmetric KMS key used for cosign image signing"
  value       = aws_kms_key.cosign.arn
}

output "cosign_key_alias" {
  description = "Alias of the cosign KMS key"
  value       = aws_kms_alias.cosign.name
}
