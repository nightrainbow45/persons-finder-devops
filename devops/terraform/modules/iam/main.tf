# IAM Module - Main Configuration
# Manages IAM roles, policies, and OIDC provider for Persons Finder

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  partition  = data.aws_partition.current.partition
}
