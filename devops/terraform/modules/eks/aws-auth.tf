# aws-auth ConfigMap - Maps IAM roles to Kubernetes RBAC groups

resource "kubernetes_config_map" "aws_auth" {
  metadata {
    name      = "aws-auth"
    namespace = "kube-system"
  }

  data = {
    mapRoles = yamlencode([
      # Node group role - required for nodes to join the cluster
      {
        rolearn  = aws_iam_role.eks_nodes.arn
        username = "system:node:{{EC2PrivateDNSName}}"
        groups   = ["system:bootstrappers", "system:nodes"]
      },
      # Admin role - full cluster access
      {
        rolearn  = var.eks_admin_role_arn
        username = "eks-admin"
        groups   = ["system:masters"]
      },
      # Developer role - namespace-level access
      {
        rolearn  = var.eks_developer_role_arn
        username = "eks-developer"
        groups   = ["developers"]
      },
      # GitHub Actions role - deployer access
      {
        rolearn  = var.github_actions_role_arn
        username = "github-actions"
        groups   = ["deployers"]
      },
    ])
  }

  depends_on = [aws_eks_cluster.main]
}
