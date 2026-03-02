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

# ClusterRole for GitHub Actions deployer group
# Helm stores release state as Secrets, so deployers need full secret access.
resource "kubernetes_cluster_role" "deployer" {
  metadata {
    name = "deployer"
  }

  rule {
    api_groups = [""]
    resources  = ["secrets", "configmaps", "services", "serviceaccounts", "pods", "persistentvolumeclaims"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments", "replicasets", "statefulsets", "daemonsets"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }

  rule {
    api_groups = ["networking.k8s.io"]
    resources  = ["ingresses", "networkpolicies"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }

  rule {
    api_groups = ["autoscaling"]
    resources  = ["horizontalpodautoscalers"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }

  rule {
    api_groups = ["rbac.authorization.k8s.io"]
    resources  = ["roles", "rolebindings"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }

  depends_on = [aws_eks_cluster.main]
}

resource "kubernetes_cluster_role_binding" "deployer" {
  metadata {
    name = "deployer"
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = kubernetes_cluster_role.deployer.metadata[0].name
  }

  subject {
    kind      = "Group"
    name      = "deployers"
    api_group = "rbac.authorization.k8s.io"
  }

  depends_on = [aws_eks_cluster.main]
}
