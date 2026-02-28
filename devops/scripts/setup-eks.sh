#!/usr/bin/env bash
#
# setup-eks.sh — Provision EKS cluster using Terraform
#
# Usage:
#   ./devops/scripts/setup-eks.sh [ENVIRONMENT]
#
# Arguments:
#   ENVIRONMENT   Target environment: dev | prod (default: dev)
#
# Prerequisites:
#   - AWS CLI configured with appropriate credentials
#   - Terraform >= 1.3 installed
#   - kubectl installed
#
# Examples:
#   ./devops/scripts/setup-eks.sh dev
#   ./devops/scripts/setup-eks.sh prod
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

ENVIRONMENT="${1:-dev}"
AWS_REGION="ap-southeast-2"
CLUSTER_NAME="persons-finder-${ENVIRONMENT}"
TF_DIR="${REPO_ROOT}/devops/terraform/environments/${ENVIRONMENT}"

# Validate environment
if [[ "${ENVIRONMENT}" != "dev" && "${ENVIRONMENT}" != "prod" ]]; then
  echo "Error: ENVIRONMENT must be 'dev' or 'prod', got '${ENVIRONMENT}'"
  exit 1
fi

# Check prerequisites
for cmd in aws terraform kubectl; do
  if ! command -v "${cmd}" &>/dev/null; then
    echo "Error: '${cmd}' is required but not installed."
    exit 1
  fi
done

# Verify AWS credentials
echo "==> Verifying AWS credentials..."
if ! aws sts get-caller-identity &>/dev/null; then
  echo "Error: AWS credentials not configured. Run 'aws configure' first."
  exit 1
fi
echo "    AWS Account: $(aws sts get-caller-identity --query Account --output text)"

# Verify Terraform directory
if [[ ! -d "${TF_DIR}" ]]; then
  echo "Error: Terraform environment directory not found: ${TF_DIR}"
  exit 1
fi

echo ""
echo "==> Step 1: Initialize Terraform"
terraform -chdir="${TF_DIR}" init

echo ""
echo "==> Step 2: Plan infrastructure changes"
terraform -chdir="${TF_DIR}" plan -out=tfplan

echo ""
read -rp "Apply the plan above? (y/N): " CONFIRM
if [[ "${CONFIRM}" != "y" && "${CONFIRM}" != "Y" ]]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "==> Step 3: Apply Terraform plan"
terraform -chdir="${TF_DIR}" apply tfplan

echo ""
echo "==> Step 4: Configure kubectl"
aws eks update-kubeconfig \
  --region "${AWS_REGION}" \
  --name "${CLUSTER_NAME}"

echo ""
echo "==> Step 5: Verify cluster"
kubectl cluster-info
kubectl get nodes

echo ""
echo "==> EKS cluster '${CLUSTER_NAME}' is ready."
echo ""
echo "Next steps:"
echo "  1. Create secrets:  kubectl create secret generic persons-finder-secrets --from-literal=OPENAI_API_KEY=<key> -n ${ENVIRONMENT}"
echo "  2. Deploy app:      ./devops/scripts/deploy.sh ${ENVIRONMENT}"
echo "  3. Verify:          ./devops/scripts/verify.sh ${ENVIRONMENT}"
