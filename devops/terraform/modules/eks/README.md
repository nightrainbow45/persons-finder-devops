# EKS Module

This module creates an Amazon EKS cluster with managed node groups.

## Resources Created

- EKS cluster
- Managed node groups
- IAM roles for cluster and nodes
- aws-auth ConfigMap for IAM to RBAC mapping
- Cluster add-ons (VPC CNI, CoreDNS, kube-proxy)

## Usage

```hcl
module "eks" {
  source = "../../modules/eks"
  
  cluster_name       = "persons-finder-eks"
  cluster_version    = "1.27"
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
}
```

## Outputs

- `cluster_id` - EKS cluster ID
- `cluster_endpoint` - EKS cluster endpoint
- `cluster_certificate_authority_data` - Cluster CA certificate
