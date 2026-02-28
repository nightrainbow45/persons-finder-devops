#!/usr/bin/env bash
#
# teardown-eks.sh — Destroy EKS cluster and associated AWS resources
#
# Usage:
#   ./devops/scripts/teardown-eks.sh [ENVIRONMENT]
#
# Arguments:
#   ENVIRONMENT   Target environment: dev | prod (default: dev)
#
# This script will:
#   1. Uninstall the Helm release (if present)
#   2. Destroy Terraform-managed infrastructure
#
# Examples:
#   ./devops/scripts/teardown-eks.sh dev
#   ./devops/scripts/teardown-eks.sh prod
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

ENVIRONMENT="${1:-dev}"
CLUSTER_NAME="persons-finder-${ENVIRONMENT}"
TF_DIR="${REPO_ROOT}/devops/terraform/environments/${ENVIRONMENT}"
RELEASE_NAME="persons-finder"

# Validate environment
if [[ "${ENVIRONMENT}" != "dev" && "${ENVIRONMENT}" != "prod" ]]; then
  echo "Error: ENVIRONMENT must be 'dev' or 'prod', got '${ENVIRONMENT}'"
  exit 1
fi

# Check prerequisites
for cmd in terraform kubectl helm; do
  if ! command -v "${cmd}" &>/dev/null; then
    echo "Error: '${cmd}' is required but not installed."
    exit 1
  fi
done

echo "WARNING: This will destroy the '${CLUSTER_NAME}' EKS cluster and all associated resources."
echo ""
read -rp "Are you sure? Type the environment name to confirm: " CONFIRM
if [[ "${CONFIRM}" != "${ENVIRONMENT}" ]]; then
  echo "Aborted. You must type '${ENVIRONMENT}' to confirm."
  exit 0
fi

# Step 1: Uninstall Helm release
echo ""
echo "==> Step 1: Uninstall Helm release"
if helm status "${RELEASE_NAME}" -n "${ENVIRONMENT}" &>/dev/null; then
  helm uninstall "${RELEASE_NAME}" -n "${ENVIRONMENT}"
  echo "    Helm release uninstalled."
else
  echo "    No Helm release found, skipping."
fi

# Step 2: Destroy Terraform resources
echo ""
echo "==> Step 2: Destroy Terraform-managed infrastructure"
if [[ ! -d "${TF_DIR}" ]]; then
  echo "Error: Terraform directory not found: ${TF_DIR}"
  exit 1
fi

terraform -chdir="${TF_DIR}" init
terraform -chdir="${TF_DIR}" destroy -auto-approve

echo ""
echo "==> Teardown complete. All resources for '${CLUSTER_NAME}' have been destroyed."
