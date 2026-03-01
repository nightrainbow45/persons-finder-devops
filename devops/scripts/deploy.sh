#!/usr/bin/env bash
#
# deploy.sh — Automated Helm deployment for Persons Finder
#
# Usage:
#   ./devops/scripts/deploy.sh [ENVIRONMENT] [OPTIONS]
#
# Arguments:
#   ENVIRONMENT   Target environment: dev | prod (default: dev)
#
# Namespace mapping:
#   prod → default   (matches CI/CD pipeline and Kyverno policy scope)
#   dev  → dev
#
# Options:
#   --tag TAG           Docker image tag to deploy (default: latest)
#   --namespace NS      Override Kubernetes namespace
#   --dry-run           Render templates without installing
#   --set KEY=VALUE     Pass additional Helm --set flags
#   -h, --help          Show this help message
#
# Examples:
#   ./devops/scripts/deploy.sh prod --tag git-abc1234
#   ./devops/scripts/deploy.sh prod --tag 1.4.2
#   ./devops/scripts/deploy.sh dev
#   ./devops/scripts/deploy.sh dev --dry-run
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CHART_DIR="${REPO_ROOT}/devops/helm/persons-finder"
RELEASE_NAME="persons-finder"

# Defaults
ENVIRONMENT="${1:-dev}"
IMAGE_TAG="latest"
NAMESPACE=""
DRY_RUN=""
EXTRA_SETS=()

# Parse arguments
shift || true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      IMAGE_TAG="$2"
      shift 2
      ;;
    --namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="--dry-run --debug"
      shift
      ;;
    --set)
      EXTRA_SETS+=("--set" "$2")
      shift 2
      ;;
    -h|--help)
      head -30 "$0" | tail -28
      exit 0
      ;;
    *)
      echo "Error: Unknown option '$1'"
      exit 1
      ;;
  esac
done

# Validate environment
if [[ "${ENVIRONMENT}" != "dev" && "${ENVIRONMENT}" != "prod" ]]; then
  echo "Error: ENVIRONMENT must be 'dev' or 'prod', got '${ENVIRONMENT}'"
  exit 1
fi

# Namespace mapping: prod app lives in 'default'; dev in 'dev'
if [[ -z "${NAMESPACE}" ]]; then
  if [[ "${ENVIRONMENT}" == "prod" ]]; then
    NAMESPACE="default"
  else
    NAMESPACE="${ENVIRONMENT}"
  fi
fi

VALUES_FILE="${CHART_DIR}/values-${ENVIRONMENT}.yaml"

# Check prerequisites
for cmd in kubectl helm; do
  if ! command -v "${cmd}" &>/dev/null; then
    echo "Error: '${cmd}' is not installed."
    exit 1
  fi
done

# Verify values file exists
if [[ ! -f "${VALUES_FILE}" ]]; then
  echo "Error: Values file not found: ${VALUES_FILE}"
  exit 1
fi

echo "==> Current kubectl context: $(kubectl config current-context)"
echo "==> Environment:  ${ENVIRONMENT}"
echo "==> Namespace:    ${NAMESPACE}"
echo "==> Image tag:    ${IMAGE_TAG}"
echo ""

# For prod, the K8s secret is managed by ESO (not created by Helm)
SECRETS_FLAG="--set secrets.create=false"
if [[ "${ENVIRONMENT}" == "dev" ]]; then
  SECRETS_FLAG="--set secrets.create=true"
fi

echo "==> Running helm upgrade --install ..."
helm upgrade --install "${RELEASE_NAME}" "${CHART_DIR}" \
  -f "${VALUES_FILE}" \
  --namespace "${NAMESPACE}" \
  --create-namespace \
  --set image.tag="${IMAGE_TAG}" \
  ${SECRETS_FLAG} \
  "${EXTRA_SETS[@]+"${EXTRA_SETS[@]}"}" \
  ${DRY_RUN} \
  --wait \
  --timeout 5m

if [[ -z "${DRY_RUN}" ]]; then
  echo ""
  echo "==> Deployment complete."
  helm status "${RELEASE_NAME}" -n "${NAMESPACE}"
  echo ""
  echo "Run './devops/scripts/verify.sh ${ENVIRONMENT}' for full verification."
fi
