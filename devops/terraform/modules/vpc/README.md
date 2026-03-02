# VPC Module

This module creates a VPC with public and private subnets for EKS deployment.

## Resources Created

- VPC with CIDR block
- Public subnets (for load balancers)
- Private subnets (for application pods)
- Internet Gateway
- NAT Gateways
- Route tables
- Security groups

## Usage

```hcl
module "vpc" {
  source = "../../modules/vpc"
  
  vpc_name         = "persons-finder-vpc"
  vpc_cidr         = "10.0.0.0/16"
  availability_zones = ["ap-southeast-2a", "ap-southeast-2b"]
}
```

## Outputs

- `vpc_id` - VPC ID
- `private_subnet_ids` - List of private subnet IDs
- `public_subnet_ids` - List of public subnet IDs
