#!/usr/bin/env bash
#
# setup-eks.sh — Provision EKS cluster and install all platform components
#
# Usage:
#   ./devops/scripts/setup-eks.sh [ENVIRONMENT]
#
# Arguments:
#   ENVIRONMENT   Target environment: dev | prod (default: dev)
#
# What this script does:
#   1. Terraform init + plan + apply  (VPC, EKS, ECR, IAM, KMS, IRSA roles)
#   2. Configure kubectl
#   3. Install Kyverno (admission controller, IRSA annotation)
#   4. Apply Kyverno ClusterPolicy (verify-image-signatures)
#   5. Install External Secrets Operator
#   6. Apply ClusterSecretStore + ExternalSecret
#
# Prerequisites:
#   - AWS CLI configured with appropriate credentials
#   - Terraform >= 1.5 installed
#   - kubectl, helm installed
#
# Examples:
#   ./devops/scripts/setup-eks.sh prod
#   ./devops/scripts/setup-eks.sh dev
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

ENVIRONMENT="${1:-dev}"
AWS_REGION="ap-southeast-2"
CLUSTER_NAME="persons-finder-${ENVIRONMENT}"
TF_DIR="${REPO_ROOT}/devops/terraform/environments/${ENVIRONMENT}"
TF_VARS="${TF_DIR}/terraform.tfvars"

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

# Verify AWS credentials
echo "==> Verifying AWS credentials..."
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
echo "    AWS Account: ${AWS_ACCOUNT}  Region: ${AWS_REGION}"

# Verify Terraform directory and var file
if [[ ! -d "${TF_DIR}" ]]; then
  echo "Error: Terraform environment directory not found: ${TF_DIR}"
  exit 1
fi

TF_VAR_ARGS=()
if [[ -f "${TF_VARS}" ]]; then
  TF_VAR_ARGS=(-var-file="${TF_VARS}")
  echo "    Using var file: ${TF_VARS}"
else
  echo "Warning: ${TF_VARS} not found. Terraform may prompt for required variables."
fi

# ── Step 1: Terraform ─────────────────────────────────────────────────────
echo ""
echo "==> Step 1: Initialize Terraform"
terraform -chdir="${TF_DIR}" init -input=false

echo ""
echo "==> Step 2: Plan infrastructure changes"
terraform -chdir="${TF_DIR}" plan -out=tfplan "${TF_VAR_ARGS[@]+"${TF_VAR_ARGS[@]}"}"

echo ""
read -rp "Apply the plan above? (y/N): " CONFIRM
if [[ "${CONFIRM}" != "y" && "${CONFIRM}" != "Y" ]]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "==> Step 3: Apply Terraform plan"
terraform -chdir="${TF_DIR}" apply tfplan

# Read outputs
KYVERNO_ROLE_ARN=$(terraform -chdir="${TF_DIR}" output -raw kyverno_role_arn 2>/dev/null || echo "")

# ── Step 4: Configure kubectl ─────────────────────────────────────────────
echo ""
echo "==> Step 4: Configure kubectl"
aws eks update-kubeconfig \
  --region "${AWS_REGION}" \
  --name "${CLUSTER_NAME}"
kubectl cluster-info
kubectl get nodes

# ── Step 5: Install Kyverno ───────────────────────────────────────────────
echo ""
echo "==> Step 5: Install Kyverno"
helm repo add kyverno https://kyverno.github.io/kyverno/ 2>/dev/null || true
helm repo update kyverno

# Install with reports-controller and cleanup-controller disabled:
# t3.small nodes have an 11-pod ENI limit; reducing Kyverno to 1 pod avoids capacity issues.
helm upgrade --install kyverno kyverno/kyverno \
  --namespace kyverno \
  --create-namespace \
  --version 3.7.1 \
  --set reportsController.enabled=false \
  --set cleanupController.enabled=false \
  --wait \
  --timeout 5m
echo "    Kyverno installed."

# ── Step 6: Annotate Kyverno SA with IRSA ────────────────────────────────
echo ""
echo "==> Step 6: Annotate kyverno-admission-controller SA with IRSA"
if [[ -n "${KYVERNO_ROLE_ARN}" ]]; then
  kubectl annotate serviceaccount kyverno-admission-controller \
    -n kyverno \
    eks.amazonaws.com/role-arn="${KYVERNO_ROLE_ARN}" \
    --overwrite
  kubectl rollout restart deployment kyverno-admission-controller -n kyverno
  kubectl rollout status deployment kyverno-admission-controller -n kyverno --timeout=3m
  echo "    IRSA annotation applied and rollout complete."
else
  echo "    WARNING: kyverno_role_arn output not found. Skipping IRSA annotation."
  echo "    Run manually: kubectl annotate sa kyverno-admission-controller -n kyverno \\"
  echo "      eks.amazonaws.com/role-arn=<role-arn>"
fi

# ── Step 7: Apply Kyverno ClusterPolicy ──────────────────────────────────
echo ""
echo "==> Step 7: Apply Kyverno ClusterPolicy (verify-image-signatures)"
if [[ -f "${REPO_ROOT}/devops/kyverno/verify-image-signatures.yaml" ]]; then
  kubectl apply -f "${REPO_ROOT}/devops/kyverno/verify-image-signatures.yaml"
  echo "    ClusterPolicy applied."
else
  echo "    WARNING: devops/kyverno/verify-image-signatures.yaml not found. Skipping."
fi

# ── Step 8: Install External Secrets Operator ─────────────────────────────
echo ""
echo "==> Step 8: Install External Secrets Operator"
helm repo add external-secrets https://charts.external-secrets.io 2>/dev/null || true
helm repo update external-secrets

helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace external-secrets \
  --create-namespace \
  --wait \
  --timeout 5m
echo "    External Secrets Operator installed."

echo ""
echo "==> Setup complete for '${CLUSTER_NAME}'."
echo ""
echo "Next steps:"
echo "  1. Apply ESO resources:  kubectl apply -f devops/k8s/cluster-secret-store.yaml"
echo "  2. Create secret:        kubectl create secret generic persons-finder-secrets \\"
echo "                             --from-literal=OPENAI_API_KEY=<key> -n default"
echo "  3. Deploy app:           ./devops/scripts/deploy.sh ${ENVIRONMENT}"
echo "  4. Verify:               ./devops/scripts/verify.sh ${ENVIRONMENT}"
