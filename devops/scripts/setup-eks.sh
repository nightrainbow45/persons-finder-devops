#!/usr/bin/env bash
#
# setup-eks.sh — 完整部署 EKS 集群及所有平台组件
#               Full EKS cluster provisioning with all platform components
#
# 使用方式 / Usage:
#   ./devops/scripts/setup-eks.sh [ENVIRONMENT]
#
# 参数 / Arguments:
#   ENVIRONMENT   目标环境 / Target environment: dev | prod (default: dev)
#
# 执行步骤 / Steps performed:
#   1.  Terraform init + plan + apply  (VPC, EKS, ECR, IAM, KMS, OIDC, IRSA, RBAC)
#   2.  配置 kubectl / Configure kubectl
#   3.  安装 metrics-server（HPA 依赖）/ Install metrics-server (required for HPA)
#   4.  安装 Kyverno（准入控制器）/ Install Kyverno admission controller
#   5.  注解 Kyverno SA 绑定 IRSA / Annotate Kyverno SA with IRSA role
#   6.  应用 Kyverno ClusterPolicy（镜像签名验证）/ Apply image-signature ClusterPolicy
#   7.  安装 External Secrets Operator / Install ESO
#   8.  应用 ESO 资源（ClusterSecretStore + ExternalSecret）/ Apply ESO manifests
#   9.  打印 OpenAI key 设置指令 / Print OpenAI key setup instructions
#   10. 打印应用部署指令 / Print app deployment instructions
#
# 前置条件 / Prerequisites:
#   - AWS CLI 已配置，具备 IAM 和 EKS 权限 / AWS CLI configured with IAM & EKS permissions
#   - Terraform >= 1.5、kubectl、helm 已安装 / Terraform >= 1.5, kubectl, helm installed
#   - devops/k8s/ 目录中存在 ESO 资源文件 / ESO manifests exist in devops/k8s/
#
# 示例 / Examples:
#   ./devops/scripts/setup-eks.sh prod
#   ./devops/scripts/setup-eks.sh dev
#

# 遇到任何错误立即退出，未声明变量视为错误，管道命令以最后一个非零退出码为准
# Exit immediately on error, treat unset variables as errors, pipe fails on first failure
set -euo pipefail

# ── 路径常量 / Path constants ──────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# ── 环境参数 / Environment parameters ─────────────────────────────────────
ENVIRONMENT="${1:-dev}"
AWS_REGION="ap-southeast-2"
CLUSTER_NAME="persons-finder-${ENVIRONMENT}"
TF_DIR="${REPO_ROOT}/devops/terraform/environments/${ENVIRONMENT}"
TF_VARS="${TF_DIR}/terraform.tfvars"

# ── 验证环境名称 / Validate environment name ───────────────────────────────
if [[ "${ENVIRONMENT}" != "dev" && "${ENVIRONMENT}" != "prod" ]]; then
  echo "Error: ENVIRONMENT must be 'dev' or 'prod', got '${ENVIRONMENT}'"
  exit 1
fi

# ── 前置命令检查 / Check required CLI tools ────────────────────────────────
for cmd in aws terraform kubectl helm; do
  if ! command -v "${cmd}" &>/dev/null; then
    echo "Error: '${cmd}' is required but not installed."
    exit 1
  fi
done

# ── 验证 AWS 凭证 / Verify AWS credentials ────────────────────────────────
echo "==> Verifying AWS credentials..."
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
echo "    Account: ${AWS_ACCOUNT}  Region: ${AWS_REGION}"

# ── 验证 Terraform 目录 / Validate Terraform directory ────────────────────
if [[ ! -d "${TF_DIR}" ]]; then
  echo "Error: Terraform environment directory not found: ${TF_DIR}"
  exit 1
fi

# terraform.tfvars 包含 project_name、environment、github_org 等变量
# Contains project_name, environment, github_org, and other input variables
TF_VAR_ARGS=()
if [[ -f "${TF_VARS}" ]]; then
  TF_VAR_ARGS=(-var-file="${TF_VARS}")
  echo "    Using var file: ${TF_VARS}"
else
  echo "Warning: ${TF_VARS} not found. Terraform may prompt for required variables."
fi

# ══════════════════════════════════════════════════════════════════════════════
# Step 1: Terraform — 创建所有 AWS 基础设施
#         Provision all AWS infrastructure
# ══════════════════════════════════════════════════════════════════════════════
# 创建的资源包括 / Resources created:
#   - VPC（2 个 AZ，公有 + 私有子网，NAT Gateway）
#   - EKS 集群（K8s 1.32，t3.small SPOT，IMDS hop limit=2）
#   - ECR 仓库（IMMUTABLE tags，KMS 加密，lifecycle policy）
#   - IAM 角色（EKS admin/developer/github-actions，K8s RBAC 绑定）
#   - KMS keys（ECR 加密、Secrets 加密、cosign 签名 ECC_NIST_P256）
#   - Secrets Manager（persons-finder/prod/openai-api-key，占位符值）
#   - EKS OIDC 身份提供商（IRSA 的前提条件）
#   - Kyverno IRSA 角色（允许 Kyverno 以低延迟调用 ECR 验签）
#   - K8s ClusterRole/ClusterRoleBinding（deployers 组，Helm 部署所需）
echo ""
echo "==> Step 1: Terraform init"
terraform -chdir="${TF_DIR}" init -input=false

echo ""
echo "==> Step 2: Terraform plan"
terraform -chdir="${TF_DIR}" plan -out=tfplan "${TF_VAR_ARGS[@]+"${TF_VAR_ARGS[@]}"}"

echo ""
# 展示 plan 后让用户确认，避免误操作
# Show plan first and ask for confirmation to prevent accidental changes
read -rp "Apply the plan above? (y/N): " CONFIRM
if [[ "${CONFIRM}" != "y" && "${CONFIRM}" != "Y" ]]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "==> Step 3: Terraform apply"
terraform -chdir="${TF_DIR}" apply tfplan

# 从 Terraform 输出中读取 Kyverno IRSA 角色 ARN，后续步骤使用
# Read Kyverno IRSA role ARN from Terraform output for use in later steps
KYVERNO_ROLE_ARN=$(terraform -chdir="${TF_DIR}" output -raw kyverno_role_arn 2>/dev/null || echo "")

# ══════════════════════════════════════════════════════════════════════════════
# Step 4: 配置 kubectl / Configure kubectl
# ══════════════════════════════════════════════════════════════════════════════
# aws eks update-kubeconfig 将集群的 endpoint、CA 证书和 OIDC 认证命令
# 写入 ~/.kube/config，之后所有 kubectl/helm 命令无需额外认证
# This writes the cluster endpoint, CA cert, and OIDC auth token command
# into ~/.kube/config so all subsequent kubectl/helm commands work automatically
echo ""
echo "==> Step 4: Configure kubectl"
aws eks update-kubeconfig \
  --region "${AWS_REGION}" \
  --name "${CLUSTER_NAME}"
echo "    Waiting for nodes to become Ready..."
# 等待节点就绪（EKS managed node group 通常在 apply 后 1-2 分钟内就绪）
# EKS managed node groups typically become Ready within 1-2 minutes after apply
kubectl wait --for=condition=Ready nodes --all --timeout=5m
kubectl get nodes

# ══════════════════════════════════════════════════════════════════════════════
# Step 5: 安装 metrics-server / Install metrics-server
# ══════════════════════════════════════════════════════════════════════════════
# HPA（HorizontalPodAutoscaler）依赖 metrics-server 提供 CPU/Memory 指标
# metrics-server collects resource metrics from Kubelets and exposes them
# via the Metrics API, which HPA uses to make scaling decisions
echo ""
echo "==> Step 5: Install metrics-server (required for HPA)"
# --set args.kubelet-insecure-tls=true 用于绕过 kubelet 证书验证（自签名证书环境）
# kubelet-insecure-tls skips kubelet certificate verification (self-signed certs in EKS)
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ 2>/dev/null || true
helm repo update metrics-server
helm upgrade --install metrics-server metrics-server/metrics-server \
  --namespace kube-system \
  --set args[0]="--kubelet-insecure-tls" \
  --wait \
  --timeout 3m
echo "    metrics-server installed."

# ══════════════════════════════════════════════════════════════════════════════
# Step 6: 安装 Kyverno / Install Kyverno
# ══════════════════════════════════════════════════════════════════════════════
# Kyverno 是 Kubernetes 原生准入控制器，用于在 Pod 创建时验证镜像 cosign 签名
# Kyverno is a Kubernetes-native admission controller that intercepts pod creation
# and verifies cosign image signatures before allowing deployment
#
# 为什么禁用 reports-controller 和 cleanup-controller？
# Why disable reports-controller and cleanup-controller?
#   t3.small 节点的 ENI 限制最多只能运行 11 个 Pod
#   t3.small nodes have an ENI-based 11-pod limit per node
#   禁用这两个可选组件节省 2 个 Pod slot，确保应用能正常调度
#   Disabling these optional components frees 2 pod slots for the app
echo ""
echo "==> Step 6: Install Kyverno v1.17.1 (chart 3.7.1)"
helm repo add kyverno https://kyverno.github.io/kyverno/ 2>/dev/null || true
helm repo update kyverno
helm upgrade --install kyverno kyverno/kyverno \
  --namespace kyverno \
  --create-namespace \
  --version 3.7.1 \
  --set reportsController.enabled=false \
  --set cleanupController.enabled=false \
  --wait \
  --timeout 5m
echo "    Kyverno installed."

# ══════════════════════════════════════════════════════════════════════════════
# Step 7: 配置 Kyverno IRSA / Configure Kyverno IRSA
# ══════════════════════════════════════════════════════════════════════════════
# 问题背景 / Problem context:
#   Kyverno 在准入 webhook 中调用 ECR API 验证 cosign 签名
#   Kyverno calls ECR API to verify cosign signatures inside the admission webhook
#
# 无 IRSA 的情况（旧方式）/ Without IRSA (old way):
#   Pod → veth → node → EC2 IMDS → IAM Role → STS → ECR 调用约 11 秒
#   This chain takes ~11s, exceeding the 10s webhook timeout → deployment blocked
#
# 有 IRSA 的情况（当前方式）/ With IRSA (current way):
#   EKS 控制平面直接为 Pod 注入 STS Web Identity Token（projected volume）
#   EKS injects STS Web Identity Token directly via projected service account volume
#   Pod 用此 token 直接向 STS 换取 IAM 临时凭证，跳过 IMDS 链路，约 1 秒
#   Pod exchanges this token for IAM credentials directly with STS, ~1s latency
echo ""
echo "==> Step 7: Configure IRSA for kyverno-admission-controller SA"
if [[ -n "${KYVERNO_ROLE_ARN}" ]]; then
  # 给 kyverno-admission-controller 的 ServiceAccount 打注解
  # 注解触发 EKS Pod Identity Webhook 自动注入 AWS_ROLE_ARN 和 token 文件
  # Annotation triggers EKS Pod Identity Webhook to inject AWS_ROLE_ARN env var
  # and the web identity token file into every new pod for this ServiceAccount
  kubectl annotate serviceaccount kyverno-admission-controller \
    -n kyverno \
    "eks.amazonaws.com/role-arn=${KYVERNO_ROLE_ARN}" \
    --overwrite

  # rollout restart 创建新的 Pod ReplicaSet，新 Pod 会携带 IRSA token
  # rollout restart creates a new ReplicaSet; the new pod picks up the IRSA token
  kubectl rollout restart deployment kyverno-admission-controller -n kyverno
  kubectl rollout status deployment kyverno-admission-controller -n kyverno --timeout=3m
  echo "    IRSA configured. ECR call latency: ~11s → ~1s"
else
  echo "    WARNING: kyverno_role_arn Terraform output not found."
  echo "    Run manually after confirming the role ARN:"
  echo "      kubectl annotate sa kyverno-admission-controller -n kyverno \\"
  echo "        eks.amazonaws.com/role-arn=<role-arn> --overwrite"
  echo "      kubectl rollout restart deployment kyverno-admission-controller -n kyverno"
fi

# ══════════════════════════════════════════════════════════════════════════════
# Step 8: 应用 Kyverno ClusterPolicy / Apply Kyverno ClusterPolicy
# ══════════════════════════════════════════════════════════════════════════════
# 策略作用 / Policy effect:
#   Enforce 模式下，只允许使用我们的 cosign KMS key（ECC_NIST_P256）签名的镜像
#   在 default namespace 创建 Pod；未签名或签名不匹配的镜像将被 webhook 拒绝
#   In Enforce mode, only images signed by our cosign KMS key are allowed in
#   the default namespace. Unsigned or wrongly-signed images are rejected at admission.
#
# 注意：策略中的公钥与 cosign KMS key 绑定，每次重建基础设施会创建新 KMS key，
#       需要同步更新策略文件中的公钥和 CI workflow 中的 COSIGN_KMS_ARN
# Note: The public key in the policy is tied to the cosign KMS key.
#       Rebuilding infra creates a new key → must update both policy and CI workflow.
echo ""
echo "==> Step 8: Apply Kyverno ClusterPolicy (verify-image-signatures)"
KYVERNO_POLICY="${REPO_ROOT}/devops/kyverno/verify-image-signatures.yaml"
if [[ -f "${KYVERNO_POLICY}" ]]; then
  kubectl apply -f "${KYVERNO_POLICY}"
  # 验证策略确实处于 Enforce 模式 / Verify policy is in Enforce mode
  POLICY_MODE=$(kubectl get clusterpolicy verify-image-signatures \
    -o jsonpath='{.spec.validationFailureAction}' 2>/dev/null || echo "unknown")
  echo "    ClusterPolicy applied. Mode: ${POLICY_MODE}"
else
  echo "    WARNING: ${KYVERNO_POLICY} not found. Skipping."
fi

# ══════════════════════════════════════════════════════════════════════════════
# Step 9: 安装 External Secrets Operator / Install External Secrets Operator
# ══════════════════════════════════════════════════════════════════════════════
# ESO 的作用 / ESO role:
#   将 AWS Secrets Manager 中的密钥（如 OPENAI_API_KEY）自动同步为 K8s Secret
#   Automatically syncs secrets from AWS Secrets Manager into K8s Secrets
#   应用 Pod 通过 envFrom.secretRef 引用这个 K8s Secret，无需直接访问 AWS
#   App pods reference the K8s Secret via envFrom.secretRef, with no direct AWS access
#
# ESO 使用 EC2 Instance Role（通过 IMDS）访问 Secrets Manager
# ESO uses the EC2 instance role (via IMDS) to access Secrets Manager
# 这就是为什么 Terraform EKS module 中设置 IMDS hop limit=2 的原因
# That's why the EKS Terraform module sets IMDS hop limit=2 (allows pod→IMDS→IAM)
echo ""
echo "==> Step 9: Install External Secrets Operator"
helm repo add external-secrets https://charts.external-secrets.io 2>/dev/null || true
helm repo update external-secrets
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace external-secrets \
  --create-namespace \
  --wait \
  --timeout 5m
echo "    ESO installed."

# ══════════════════════════════════════════════════════════════════════════════
# Step 10: 应用 ESO 资源 / Apply ESO Kubernetes resources
# ══════════════════════════════════════════════════════════════════════════════
# ClusterSecretStore 定义如何连接 AWS Secrets Manager（使用哪个 region、哪种认证方式）
# ClusterSecretStore defines HOW to connect to AWS Secrets Manager (region, auth method)
#
# ExternalSecret 定义要同步哪个 secret（secret 名称、JSON key、刷新间隔）
# ExternalSecret defines WHAT to sync (which AWS secret, which JSON key, refresh interval)
# 同步结果：在 default namespace 创建 K8s Secret persons-finder-secrets，
#           包含 OPENAI_API_KEY 字段
# Result: creates K8s Secret "persons-finder-secrets" in default namespace
#         with the OPENAI_API_KEY field populated from AWS Secrets Manager
echo ""
echo "==> Step 10: Apply ESO resources (ClusterSecretStore + ExternalSecret)"
ESO_DIR="${REPO_ROOT}/devops/k8s"
if [[ -d "${ESO_DIR}" ]]; then
  kubectl apply -f "${ESO_DIR}/cluster-secret-store.yaml"
  kubectl apply -f "${ESO_DIR}/external-secret.yaml"
  echo "    ESO resources applied."
  echo "    Note: ExternalSecret will sync after you set the OpenAI key (see Step 11)."
else
  echo "    WARNING: ${ESO_DIR} not found. Skipping ESO resource creation."
fi

# ══════════════════════════════════════════════════════════════════════════════
# 后续步骤 / Next steps
# ══════════════════════════════════════════════════════════════════════════════
echo ""
echo "============================================================"
echo "  Infrastructure setup complete for '${CLUSTER_NAME}'!"
echo "============================================================"
echo ""
echo "NEXT STEP 1 — Set OpenAI API key in Secrets Manager:"
echo "  Terraform created the secret with a placeholder value."
echo "  Replace 'sk-...' with the actual key:"
echo ""
echo "  aws secretsmanager put-secret-value \\"
echo "    --secret-id \"persons-finder/${ENVIRONMENT}/openai-api-key\" \\"
echo "    --secret-string '{\"OPENAI_API_KEY\":\"sk-...\"}' \\"
echo "    --region ${AWS_REGION}"
echo ""
echo "  Then force ESO to sync immediately (normally refreshes every 5 min):"
echo ""
echo "  kubectl annotate externalsecret persons-finder-secrets \\"
echo "    force-sync=\$(date +%s) --overwrite -n default"
echo ""
echo "  Verify sync succeeded:"
echo "  kubectl get externalsecret persons-finder-secrets -n default"
echo ""
echo "NEXT STEP 2 — Deploy the application:"
echo "  Push a commit to main to trigger CI/CD."
echo "  The pipeline now builds TWO images in the same job:"
echo "    - persons-finder:git-<sha>         (main application)"
echo "    - pii-redaction-sidecar:git-<sha>  (Layer 2 PII proxy, auto-creates ECR repo)"
echo "  Both images are Trivy-scanned and cosign-signed before deployment."
echo ""
echo "  OR deploy manually with signed ECR images:"
echo ""
echo "  ./devops/scripts/deploy.sh ${ENVIRONMENT} --tag git-<sha>"
echo "  (passes --set sidecar.image.tag=git-<sha> automatically for prod)"
echo ""
echo "NEXT STEP 3 — Verify the full stack:"
echo "  ./devops/scripts/verify.sh ${ENVIRONMENT}"
echo ""
