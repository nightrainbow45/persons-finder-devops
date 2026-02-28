# IAM Roles for Persons Finder

# --- GitHub Actions Role ---
# Used by CI/CD pipeline to push images and deploy to EKS

resource "aws_iam_role" "github_actions" {
  name = "${var.project_name}-github-actions-${var.environment}"

  assume_role_policy = templatefile("${path.module}/trust-policies/github-oidc.json", {
    oidc_provider_arn = aws_iam_openid_connect_provider.github.arn
    github_org        = var.github_org
    github_repo       = var.github_repo
  })

  tags = merge(var.tags, {
    Name = "${var.project_name}-github-actions-${var.environment}"
  })
}

resource "aws_iam_role_policy" "github_actions_ecr" {
  name   = "ecr-push"
  role   = aws_iam_role.github_actions.id
  policy = templatefile("${path.module}/policies/ecr-push.json", {
    aws_account_id = local.account_id
    aws_region     = var.aws_region
    ecr_repo_name  = var.ecr_repository_name
  })
}

resource "aws_iam_role_policy" "github_actions_eks" {
  name   = "eks-access"
  role   = aws_iam_role.github_actions.id
  policy = templatefile("${path.module}/policies/eks-access.json", {
    aws_account_id = local.account_id
    aws_region     = var.aws_region
    cluster_name   = var.cluster_name
  })
}

resource "aws_iam_role_policy" "github_actions_deployer" {
  name   = "deployer"
  role   = aws_iam_role.github_actions.id
  policy = file("${path.module}/policies/deployer.json")
}

# --- EKS Admin Role ---
# Full cluster management permissions for operations team

resource "aws_iam_role" "eks_admin" {
  name = "${var.project_name}-eks-admin-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          AWS = "arn:${local.partition}:iam::${local.account_id}:root"
        }
        Action = "sts:AssumeRole"
        Condition = {
          StringEquals = {
            "aws:PrincipalTag/Role" = "admin"
          }
        }
      }
    ]
  })

  tags = merge(var.tags, {
    Name = "${var.project_name}-eks-admin-${var.environment}"
  })
}

resource "aws_iam_role_policy_attachment" "eks_admin_eks_access" {
  role       = aws_iam_role.eks_admin.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/AmazonEKSClusterPolicy"
}

# --- EKS Developer Role ---
# Namespace-level read/write permissions for developers

resource "aws_iam_role" "eks_developer" {
  name = "${var.project_name}-eks-developer-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          AWS = "arn:${local.partition}:iam::${local.account_id}:root"
        }
        Action = "sts:AssumeRole"
        Condition = {
          StringEquals = {
            "aws:PrincipalTag/Role" = "developer"
          }
        }
      }
    ]
  })

  tags = merge(var.tags, {
    Name = "${var.project_name}-eks-developer-${var.environment}"
  })
}

resource "aws_iam_role_policy" "eks_developer_eks" {
  name   = "eks-read"
  role   = aws_iam_role.eks_developer.id
  policy = templatefile("${path.module}/policies/eks-access.json", {
    aws_account_id = local.account_id
    aws_region     = var.aws_region
    cluster_name   = var.cluster_name
  })
}
