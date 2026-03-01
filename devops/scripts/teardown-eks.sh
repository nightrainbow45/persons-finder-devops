#!/usr/bin/env bash
#
# teardown-eks.sh — Destroy EKS cluster and all associated AWS resources
#
# Usage:
#   ./devops/scripts/teardown-eks.sh [ENVIRONMENT]
#
# Arguments:
#   ENVIRONMENT   Target environment: dev | prod (default: dev)
#
# Namespace mapping (matches deploy.sh):
#   prod → default   (app deployed in default namespace)
#   dev  → dev
#
# Destruction order:
#   1. Update kubeconfig
#   2. Delete Kyverno ClusterPolicy  (prevents webhook blocking pod terminations)
#   3. Helm uninstall app            (default / dev namespace)
#   4. Delete ESO resources          (ExternalSecret, ClusterSecretStore)
#   5. Helm uninstall external-secrets
#   6. Helm uninstall kyverno
#   7. terraform destroy
#
# Examples:
#   ./devops/scripts/teardown-eks.sh prod
#   ./devops/scripts/teardown-eks.sh dev
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

ENVIRONMENT="${1:-dev}"
CLUSTER_NAME="persons-finder-${ENVIRONMENT}"
AWS_REGION="ap-southeast-2"
TF_DIR="${REPO_ROOT}/devops/terraform/environments/${ENVIRONMENT}"
TF_VARS="${TF_DIR}/terraform.tfvars"
RELEASE_NAME="persons-finder"

# Namespace mapping: prod uses 'default'; dev uses 'dev'
if [[ "${ENVIRONMENT}" == "prod" ]]; then
  APP_NAMESPACE="default"
else
  APP_NAMESPACE="${ENVIRONMENT}"
fi

# Validate environment
if [[ "${ENVIRONMENT}" != "dev" && "${ENVIRONMENT}" != "prod" ]]; then
  echo "Error: ENVIRONMENT must be 'dev' or 'prod', got '${ENVIRONMENT}'"
  exit 1
fi

# Check prerequisites
for cmd in aws terraform kubectl helm; do
  if ! command -v "${cmd}" &>/dev/null; then
    echo "Error: '${cmd}' is required but not installed."
    exit 1
  fi
done

echo "=========================================================="
echo "  WARNING: This will DESTROY the '${CLUSTER_NAME}' cluster"
echo "  and ALL associated AWS resources (VPC, ECR, IAM, KMS...)."
echo "  App namespace: ${APP_NAMESPACE}"
echo "=========================================================="
echo ""
read -rp "Type the environment name to confirm ('${ENVIRONMENT}'): " CONFIRM
if [[ "${CONFIRM}" != "${ENVIRONMENT}" ]]; then
  echo "Aborted."
  exit 0
fi

# ── Step 1: Update kubeconfig ──────────────────────────────────────────────
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
SKIP_K8S="${SKIP_K8S:-false}"

# ── Step 2: Delete Kyverno ClusterPolicy ───────────────────────────────────
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

# ── Step 3: Uninstall app Helm release ─────────────────────────────────────
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

# ── Step 4: Delete ESO resources ───────────────────────────────────────────
echo ""
echo "==> Step 4: Delete External Secrets Operator resources"
if [[ "${SKIP_K8S}" == "false" ]]; then
  # Delete ExternalSecrets in all namespaces
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

# ── Step 5: Helm uninstall external-secrets ────────────────────────────────
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

# ── Step 6: Helm uninstall kyverno ─────────────────────────────────────────
echo ""
echo "==> Step 6: Helm uninstall kyverno"
if [[ "${SKIP_K8S}" == "false" ]]; then
  if helm status kyverno -n kyverno &>/dev/null; then
    # Remove webhook configurations first to prevent timeout if admission controller is gone
    kubectl delete validatingwebhookconfiguration --all -l "app.kubernetes.io/instance=kyverno" --ignore-not-found
    kubectl delete mutatingwebhookconfiguration   --all -l "app.kubernetes.io/instance=kyverno" --ignore-not-found
    helm uninstall kyverno -n kyverno --wait --timeout 3m || true
    echo "    kyverno uninstalled."
  else
    echo "    No kyverno release found, skipping."
  fi
else
  echo "    Skipped (no cluster access)."
fi

# ── Step 7: Terraform destroy ──────────────────────────────────────────────
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

terraform -chdir="${TF_DIR}" init -input=false
terraform -chdir="${TF_DIR}" destroy -auto-approve "${TF_VAR_ARGS[@]+"${TF_VAR_ARGS[@]}"}"

echo ""
echo "=========================================================="
echo "  Teardown complete. All resources for '${CLUSTER_NAME}'"
echo "  have been destroyed."
echo "=========================================================="
