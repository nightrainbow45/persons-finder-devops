# EKS Module / EKS 模块

This module creates an Amazon EKS cluster with managed node groups.
本模块创建带托管节点组的 Amazon EKS 集群。

## Resources Created / 创建的资源

- EKS cluster
  EKS 集群
- Managed node groups
  托管节点组
- IAM roles for cluster and nodes
  集群和节点所需的 IAM 角色
- aws-auth ConfigMap for IAM to RBAC mapping
  IAM 到 RBAC 映射的 aws-auth ConfigMap
- Cluster add-ons (VPC CNI, CoreDNS, kube-proxy)
  集群插件（VPC CNI、CoreDNS、kube-proxy）

## Usage / 使用方式

```hcl
module "eks" {
  source = "../../modules/eks"

  cluster_name       = "persons-finder-eks"
  cluster_version    = "1.27"
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
}
```

## Outputs / 输出

- `cluster_id` — EKS cluster ID / EKS 集群 ID
- `cluster_endpoint` — EKS cluster endpoint / EKS 集群端点
- `cluster_certificate_authority_data` — Cluster CA certificate / 集群 CA 证书
