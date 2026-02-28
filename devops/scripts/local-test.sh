#!/usr/bin/env bash
#
# local-test.sh — Local Kind-based deployment test for Persons Finder
#
# Usage:
#   ./devops/scripts/local-test.sh [ACTION]
#
# Actions:
#   up        Create cluster, build image, deploy with Helm (default)
#   down      Tear down the Kind cluster
#   status    Show current deployment status
#
# Examples:
#   ./devops/scripts/local-test.sh
#   ./devops/scripts/local-test.sh up
#   ./devops/scripts/local-test.sh down
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CHART_DIR="${REPO_ROOT}/devops/helm/persons-finder"
CLUSTER_NAME="persons-finder"
IMAGE_NAME="persons-finder:latest"
RELEASE_NAME="persons-finder"

ACTION="${1:-up}"

# Check prerequisites
check_prereqs() {
  for cmd in docker kind kubectl helm; do
    if ! command -v "${cmd}" &>/dev/null; then
      echo "Error: '${cmd}' is required but not installed."
      exit 1
    fi
  done
}

cluster_exists() {
  kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"
}

do_up() {
  check_prereqs

  echo "==> Step 1: Create Kind cluster"
  if cluster_exists; then
    echo "    Cluster '${CLUSTER_NAME}' already exists, reusing."
  else
    kind create cluster --name "${CLUSTER_NAME}"
  fi

  echo ""
  echo "==> Step 2: Build Docker image"
  docker build -t "${IMAGE_NAME}" -f "${REPO_ROOT}/devops/docker/Dockerfile" "${REPO_ROOT}"

  echo ""
  echo "==> Step 3: Load image into Kind"
  kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"

  echo ""
  echo "==> Step 4: Create secret"
  kubectl create secret generic persons-finder-secrets \
    --from-literal=OPENAI_API_KEY=test-local-key \
    --dry-run=client -o yaml | kubectl apply -f -

  echo ""
  echo "==> Step 5: Deploy with Helm"
  helm upgrade --install "${RELEASE_NAME}" "${CHART_DIR}" \
    -f "${CHART_DIR}/values-dev.yaml" \
    --set image.repository=persons-finder \
    --set image.tag=latest \
    --set image.pullPolicy=Never \
    --set secrets.create=false \
    --set autoscaling.enabled=false \
    --wait \
    --timeout 5m

  echo ""
  echo "==> Step 6: Wait for pods"
  kubectl rollout status deployment -l "app.kubernetes.io/name=persons-finder" --timeout=120s

  echo ""
  echo "==> Deployment complete!"
  echo ""
  kubectl get pods
  echo ""
  echo "To access the application:"
  echo "  kubectl port-forward svc/persons-finder 8080:80"
  echo "  curl http://localhost:8080/actuator/health"
  echo ""
  echo "To tear down:"
  echo "  ./devops/scripts/local-test.sh down"
}

do_down() {
  echo "==> Tearing down Kind cluster '${CLUSTER_NAME}'"
  if cluster_exists; then
    kind delete cluster --name "${CLUSTER_NAME}"
    echo "    Cluster deleted."
  else
    echo "    Cluster '${CLUSTER_NAME}' does not exist."
  fi
}

do_status() {
  if ! cluster_exists; then
    echo "Kind cluster '${CLUSTER_NAME}' is not running."
    exit 0
  fi

  echo "==> Helm Release"
  helm status "${RELEASE_NAME}" 2>/dev/null || echo "  Not installed."

  echo ""
  echo "==> Pods"
  kubectl get pods -l "app.kubernetes.io/name=persons-finder" 2>/dev/null || echo "  None found."

  echo ""
  echo "==> Services"
  kubectl get svc -l "app.kubernetes.io/name=persons-finder" 2>/dev/null || echo "  None found."
}

case "${ACTION}" in
  up)     do_up ;;
  down)   do_down ;;
  status) do_status ;;
  *)
    echo "Error: Unknown action '${ACTION}'. Use: up, down, status"
    exit 1
    ;;
esac
