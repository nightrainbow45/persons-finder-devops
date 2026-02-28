# Terraform Backend Configuration
# Uses S3 for state storage and DynamoDB for state locking
# This file is a template - actual bucket/table names are set per environment

terraform {
  backend "s3" {
    bucket         = "persons-finder-terraform-state"
    key            = "terraform.tfstate"
    region         = "ap-southeast-2"
    encrypt        = true
    dynamodb_table = "persons-finder-terraform-locks"
  }
}
