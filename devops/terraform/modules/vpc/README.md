# VPC Module / VPC 模块

This module creates a VPC with public and private subnets for EKS deployment.
本模块为 EKS 部署创建包含公有子网和私有子网的 VPC。

## Resources Created / 创建的资源

- VPC with CIDR block
  带 CIDR 地址块的 VPC
- Public subnets (for load balancers)
  公有子网（用于负载均衡器）
- Private subnets (for application pods)
  私有子网（用于应用 Pod）
- Internet Gateway
  互联网网关
- NAT Gateways
  NAT 网关
- Route tables
  路由表
- Security groups
  安全组

## Usage / 使用方式

```hcl
module "vpc" {
  source = "../../modules/vpc"

  vpc_name           = "persons-finder-vpc"
  vpc_cidr           = "10.0.0.0/16"
  availability_zones = ["ap-southeast-2a", "ap-southeast-2b"]
}
```

## Outputs / 输出

- `vpc_id` — VPC ID
- `private_subnet_ids` — List of private subnet IDs / 私有子网 ID 列表
- `public_subnet_ids` — List of public subnet IDs / 公有子网 ID 列表
