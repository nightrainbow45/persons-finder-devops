#!/usr/bin/env bash
#
# teardown-eks.sh — 按安全顺序销毁 EKS 集群及所有关联 AWS 资源
#                   Destroy EKS cluster and all associated AWS resources in safe order
#
# 使用方式 / Usage:
#   ./devops/scripts/teardown-eks.sh [ENVIRONMENT]
#
# 参数 / Arguments:
#   ENVIRONMENT   目标环境 / Target environment: dev | prod (default: dev)
#
# Namespace 映射 / Namespace mapping:
#   prod → default   (与 CI/CD 和 Kyverno 策略范围一致)
#   dev  → dev
#
# 销毁顺序及原因 / Destruction order and rationale:
#   1. 配置 kubectl                    — 后续 K8s 操作的前提
#   2. 删除 Kyverno ClusterPolicy      — 防止 webhook 在 Pod 终止时阻塞销毁流程
#   3. Helm 卸载应用                    — 清理 Deployment/Service/HPA 等对象
#   4. 删除 ESO 资源                    — 先删 ExternalSecret/ClusterSecretStore 再卸载 ESO
#   5. Helm 卸载 external-secrets      — CRD 由 Helm 管理，卸载前须先删实例
#   6. Helm 卸载 kyverno               — 先删 webhook 配置，再卸载控制器
#   7. Terraform destroy               — 最后销毁所有 AWS 资源（VPC/EKS/ECR/IAM/KMS）
#
# ⚠️  注意 / WARNING:
#   KMS key 销毁有 30 天等待期。Terraform destroy 后，key 进入 PendingDeletion 状态。
#   KMS keys enter a 30-day PendingDeletion window after terraform destroy.
#   下次重建基础设施时，Terraform 会创建新 key，ARN 会变更。
#   On next rebuild, Terraform creates NEW keys with NEW ARNs.
#   届时需同步更新：/ You must then update:
#     - devops/kyverno/verify-image-signatures.yaml（新公钥 / new public key）
#     - .github/workflows/ci-cd.yml（新 COSIGN_KMS_ARN / new COSIGN_KMS_ARN）
#   参见 setup-eks.sh Step 8 的注释 / See setup-eks.sh Step 8 comments for details.
#
# 示例 / Examples:
#   ./devops/scripts/teardown-eks.sh prod
#   ./devops/scripts/teardown-eks.sh dev
#

# 遇到任何错误立即退出，未声明变量视为错误，管道命令以最后一个非零退出码为准
# Exit immediately on error, treat unset variables as errors, pipe fails on first error
set -euo pipefail

# ── 路径常量 / Path constants ──────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# ── 环境参数 / Environment parameters ─────────────────────────────────────
ENVIRONMENT="${1:-dev}"
CLUSTER_NAME="persons-finder-${ENVIRONMENT}"
AWS_REGION="ap-southeast-2"
TF_DIR="${REPO_ROOT}/devops/terraform/environments/${ENVIRONMENT}"
TF_VARS="${TF_DIR}/terraform.tfvars"
RELEASE_NAME="persons-finder"

# ── Namespace 映射 / Namespace mapping ────────────────────────────────────
# prod 应用部署在 default namespace（与 CI/CD 和 Kyverno 策略匹配）
# prod app lives in 'default' to match CI/CD pipeline and Kyverno policy scope
if [[ "${ENVIRONMENT}" == "prod" ]]; then
  APP_NAMESPACE="default"
else
  APP_NAMESPACE="${ENVIRONMENT}"
fi

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

# ── 确认提示 / Confirmation prompt ────────────────────────────────────────
# 要求用户手动输入环境名称以确认，防止误操作
# Require manual typing of environment name to confirm — prevents accidental destruction
echo "=========================================================="
echo "  WARNING: This will DESTROY the '${CLUSTER_NAME}' cluster"
echo "  and ALL associated AWS resources (VPC, ECR, IAM, KMS...)."
echo "  App namespace: ${APP_NAMESPACE}"
echo ""
echo "  ⚠️  KMS keys will enter 30-day PendingDeletion state."
echo "      Rebuilding infra will create new KMS keys with new ARNs."
echo "      You will need to update cosign public key and CI ARN."
echo "=========================================================="
echo ""
read -rp "Type the environment name to confirm ('${ENVIRONMENT}'): " CONFIRM
if [[ "${CONFIRM}" != "${ENVIRONMENT}" ]]; then
  echo "Aborted."
  exit 0
fi

# ── 集群可达性标志 / Cluster reachability flag ─────────────────────────────
# 如果集群已不可达（已被销毁），跳过所有 K8s 操作，直接进行 terraform destroy
# If the cluster is unreachable (already destroyed), skip K8s cleanup and go straight to destroy
SKIP_K8S=false

# ══════════════════════════════════════════════════════════════════════════════
# Step 1: 配置 kubectl / Configure kubectl
# ══════════════════════════════════════════════════════════════════════════════
# 如果集群已销毁，这一步会失败；我们捕获失败并设置 SKIP_K8S=true
# If the cluster is already destroyed, this step fails; we catch that and skip K8s steps
echo ""
echo "==> Step 1: Update kubeconfig for ${CLUSTER_NAME}"
if aws eks update-kubeconfig \
     --region "${AWS_REGION}" \
     --name "${CLUSTER_NAME}" 2>/dev/null; then
  echo "    kubeconfig updated."
else
  echo "    Could not reach cluster (already destroyed?). Skipping K8s cleanup steps."
  SKIP_K8S=true
fi

# ══════════════════════════════════════════════════════════════════════════════
# Step 2: 删除 Kyverno ClusterPolicy / Delete Kyverno ClusterPolicy
# ══════════════════════════════════════════════════════════════════════════════
# 为什么最先删 ClusterPolicy？
# Why delete ClusterPolicy first?
#   Kyverno ClusterPolicy 处于 Enforce 模式，所有创建/删除 Pod 的操作都经过
#   Kyverno admission webhook 验证。如果在 webhook 还活跃时开始销毁流程，
#   webhook 可能拦截 Pod 终止请求，导致资源卡在 Terminating 状态。
#   The Kyverno policy is in Enforce mode: all pod create/delete operations pass
#   through the Kyverno admission webhook. If we start destroying resources while
#   the webhook is still active, it may intercept and block pod termination,
#   leaving resources stuck in Terminating state.
echo ""
echo "==> Step 2: Delete Kyverno ClusterPolicy (prevents webhook blocking teardown)"
if [[ "${SKIP_K8S}" == "false" ]]; then
  if kubectl get clusterpolicy verify-image-signatures &>/dev/null; then
    kubectl delete clusterpolicy verify-image-signatures
    echo "    ClusterPolicy deleted."
  else
    echo "    No ClusterPolicy found, skipping."
  fi
else
  echo "    Skipped (no cluster access)."
fi

# ══════════════════════════════════════════════════════════════════════════════
# Step 3: Helm 卸载应用 / Helm uninstall application
# ══════════════════════════════════════════════════════════════════════════════
# helm uninstall 会删除所有由 Helm 管理的 K8s 资源：
#   Deployment, Service, Ingress, HPA, ServiceAccount, RBAC（如果 Helm 创建的）
# helm uninstall removes all Helm-managed K8s resources:
#   Deployment, Service, Ingress, HPA, ServiceAccount, RBAC (if created by Helm)
# 注意：ESO 管理的 Secret（persons-finder-secrets）不由 Helm 管理，在 Step 4 处理
# Note: ESO-managed Secret (persons-finder-secrets) is NOT managed by Helm, handled in Step 4
echo ""
echo "==> Step 3: Helm uninstall '${RELEASE_NAME}' from namespace '${APP_NAMESPACE}'"
if [[ "${SKIP_K8S}" == "false" ]]; then
  if helm status "${RELEASE_NAME}" -n "${APP_NAMESPACE}" &>/dev/null; then
    helm uninstall "${RELEASE_NAME}" -n "${APP_NAMESPACE}" --wait --timeout 3m
    echo "    Helm release '${RELEASE_NAME}' uninstalled."
  else
    echo "    No release '${RELEASE_NAME}' in '${APP_NAMESPACE}', skipping."
  fi
else
  echo "    Skipped (no cluster access)."
fi

# ══════════════════════════════════════════════════════════════════════════════
# Step 4: 删除 ESO 资源 / Delete ESO resources
# ══════════════════════════════════════════════════════════════════════════════
# 为什么在卸载 ESO Helm release 前先删除这些资源？
# Why delete these resources before uninstalling the ESO Helm release?
#   ExternalSecret 和 ClusterSecretStore 是 ESO 定义的 CRD 实例
#   ExternalSecret and ClusterSecretStore are CRD instances defined by ESO
#   如果先卸载 ESO（含 CRD），再删这些资源，kubectl 会因 CRD 不存在而报错
#   If we uninstall ESO (which removes CRDs) first, kubectl delete would fail
#   because the resource type no longer exists
echo ""
echo "==> Step 4: Delete External Secrets Operator resources (before uninstalling ESO)"
if [[ "${SKIP_K8S}" == "false" ]]; then
  # 先检查 ESO CRD 是否存在，再执行删除
  # Check if ESO CRDs exist before attempting deletion
  if kubectl api-resources --api-group=external-secrets.io &>/dev/null 2>&1; then
    kubectl delete externalsecret --all -A --ignore-not-found
    kubectl delete clustersecretstore --all --ignore-not-found
    echo "    ESO resources deleted."
  else
    echo "    ESO CRDs not found, skipping."
  fi
else
  echo "    Skipped (no cluster access)."
fi

# ══════════════════════════════════════════════════════════════════════════════
# Step 5: Helm 卸载 external-secrets / Helm uninstall external-secrets
# ══════════════════════════════════════════════════════════════════════════════
# ESO Helm release 包含 CRD 定义，卸载后 CRD 实例（ExternalSecret 等）
# 和 Controller 都会被删除
# The ESO Helm release includes CRD definitions. Uninstalling removes the
# controller and all associated CRD definitions
echo ""
echo "==> Step 5: Helm uninstall external-secrets"
if [[ "${SKIP_K8S}" == "false" ]]; then
  if helm status external-secrets -n external-secrets &>/dev/null; then
    helm uninstall external-secrets -n external-secrets --wait --timeout 3m
    echo "    external-secrets uninstalled."
  else
    echo "    No external-secrets release found, skipping."
  fi
else
  echo "    Skipped (no cluster access)."
fi

# ══════════════════════════════════════════════════════════════════════════════
# Step 6: Helm 卸载 kyverno / Helm uninstall kyverno
# ══════════════════════════════════════════════════════════════════════════════
# 为什么先删 webhook 配置再卸载？
# Why delete webhook configurations before uninstalling?
#   Kyverno 注册了 ValidatingWebhookConfiguration 和 MutatingWebhookConfiguration
#   如果先卸载 kyverno（删除 admission-controller Pod），K8s API server 仍然
#   会向已消失的 webhook endpoint 发送请求，导致所有后续操作超时（约 10-30s 每次）
#   Kyverno registers ValidatingWebhookConfiguration and MutatingWebhookConfiguration.
#   If we delete the controller pod first, the K8s API server still tries to call
#   the (now dead) webhook endpoint, causing all subsequent operations to time out.
#   先删 webhook 配置可以立即解除 API server 对 Kyverno 的依赖
#   Pre-deleting webhook configs immediately breaks the API server's dependency on Kyverno.
echo ""
echo "==> Step 6: Helm uninstall kyverno (pre-delete webhook configs to avoid timeout)"
if [[ "${SKIP_K8S}" == "false" ]]; then
  if helm status kyverno -n kyverno &>/dev/null; then
    # 先删除 webhook 配置，解除 API server 对 Kyverno 的依赖
    # Remove webhook configs first to break API server dependency on Kyverno
    kubectl delete validatingwebhookconfiguration \
      --all -l "app.kubernetes.io/instance=kyverno" --ignore-not-found
    kubectl delete mutatingwebhookconfiguration \
      --all -l "app.kubernetes.io/instance=kyverno" --ignore-not-found
    # 即使 Helm 超时（|| true），我们也继续执行后续步骤
    # Even if Helm times out (|| true), we continue with the rest of teardown
    helm uninstall kyverno -n kyverno --wait --timeout 3m || true
    echo "    kyverno uninstalled."
  else
    echo "    No kyverno release found, skipping."
  fi
else
  echo "    Skipped (no cluster access)."
fi

# ══════════════════════════════════════════════════════════════════════════════
# Step 7: Terraform destroy / 销毁所有 AWS 资源
# ══════════════════════════════════════════════════════════════════════════════
# 最后执行 terraform destroy，销毁：
# Finally destroy all AWS resources including:
#   - EKS 集群（控制平面 + 节点组）
#   - VPC（子网、路由表、Internet Gateway、NAT Gateway、安全组）
#   - ECR 仓库（镜像数据不会自动删除，Terraform destroy 只删除 registry 配置）
#   - IAM 角色和策略（admin/developer/github-actions/kyverno-irsa）
#   - KMS keys（进入 30 天 PendingDeletion 等待期，而非立即删除）
#   - Secrets Manager secret（进入 30 天 PendingDeletion，或使用 force-delete 立即删除）
#   - EKS OIDC 身份提供商
#   - S3/DynamoDB Terraform state backend（不会被销毁，这是有意为之）
#
# ⚠️  ECR 镜像数据说明 / ECR image data note:
#   terraform destroy 会尝试删除 ECR 仓库，但如果仓库中还有镜像，会失败。
#   If the ECR repository contains images, terraform destroy may fail.
#   如果失败，手动清空镜像后重新执行：
#   If it fails, manually clear images and re-run:
#   aws ecr batch-delete-image --repository-name persons-finder \
#     --image-ids "$(aws ecr list-images --repository-name persons-finder \
#     --query 'imageIds[*]' --output json)" --region ap-southeast-2
echo ""
echo "==> Step 7: Destroy Terraform-managed infrastructure"
if [[ ! -d "${TF_DIR}" ]]; then
  echo "Error: Terraform directory not found: ${TF_DIR}"
  exit 1
fi

TF_VAR_ARGS=()
if [[ -f "${TF_VARS}" ]]; then
  TF_VAR_ARGS=(-var-file="${TF_VARS}")
  echo "    Using var file: ${TF_VARS}"
else
  echo "    WARNING: ${TF_VARS} not found. You may need to pass -var flags manually."
fi

# init 确保 backend 连接和 provider 插件已就绪
# init ensures backend connection and provider plugins are ready
terraform -chdir="${TF_DIR}" init -input=false
terraform -chdir="${TF_DIR}" destroy -auto-approve "${TF_VAR_ARGS[@]+"${TF_VAR_ARGS[@]}"}"

# ── 销毁完成 / Teardown complete ───────────────────────────────────────────
echo ""
echo "=========================================================="
echo "  Teardown complete. All resources for '${CLUSTER_NAME}'"
echo "  have been destroyed."
echo ""
echo "  REMINDER: KMS keys are in 30-day PendingDeletion state."
echo "  If you rebuild infra, new KMS keys will be created with"
echo "  new ARNs. You must update:"
echo "    - devops/kyverno/verify-image-signatures.yaml (public key)"
echo "    - .github/workflows/ci-cd.yml (COSIGN_KMS_ARN)"
echo "=========================================================="
