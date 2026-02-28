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
# Options:
#   --tag TAG           Docker image tag to deploy (default: latest)
#   --namespace NS      Kubernetes namespace (default: same as environment)
#   --dry-run           Render templates without installing
#   --set KEY=VALUE     Pass additional Helm --set flags
#   -h, --help          Show this help message
#
# Examples:
#   ./devops/scripts/deploy.sh dev
#   ./devops/scripts/deploy.sh prod --tag v1.2.3
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
      head -25 "$0" | tail -22
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

NAMESPACE="${NAMESPACE:-${ENVIRONMENT}}"
VALUES_FILE="${CHART_DIR}/values-${ENVIRONMENT}.yaml"

# Check prerequisites
for cmd in kubectl helm; do
  if ! command -v "${cmd}" &>/dev/null; then
    echo "Error: '${cmd}' is not installed. See devops/docs/DEPLOYMENT.md for prerequisites."
    exit 1
  fi
done

# Verify values file exists
if [[ ! -f "${VALUES_FILE}" ]]; then
  echo "Error: Values file not found: ${VALUES_FILE}"
  exit 1
fi

# Verify kubectl context
echo "==> Current kubectl context: $(kubectl config current-context)"
echo "==> Deploying to namespace: ${NAMESPACE}"
echo "==> Environment: ${ENVIRONMENT}"
echo "==> Image tag: ${IMAGE_TAG}"
echo ""

# Deploy with Helm
echo "==> Running helm upgrade --install ..."
helm upgrade --install "${RELEASE_NAME}" "${CHART_DIR}" \
  -f "${VALUES_FILE}" \
  --namespace "${NAMESPACE}" \
  --create-namespace \
  --set image.tag="${IMAGE_TAG}" \
  "${EXTRA_SETS[@]+"${EXTRA_SETS[@]}"}" \
  ${DRY_RUN} \
  --wait \
  --timeout 5m

if [[ -z "${DRY_RUN}" ]]; then
  echo ""
  echo "==> Deployment complete. Verifying..."
  helm status "${RELEASE_NAME}" -n "${NAMESPACE}"
  echo ""
  echo "Run './devops/scripts/verify.sh ${NAMESPACE}' for full verification."
fi
