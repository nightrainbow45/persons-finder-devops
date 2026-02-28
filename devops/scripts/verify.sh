#!/usr/bin/env bash
#
# verify.sh — Verify Persons Finder deployment health
#
# Usage:
#   ./devops/scripts/verify.sh [NAMESPACE]
#
# Arguments:
#   NAMESPACE   Kubernetes namespace to check (default: dev)
#
# Examples:
#   ./devops/scripts/verify.sh
#   ./devops/scripts/verify.sh prod
#

set -euo pipefail

NAMESPACE="${1:-dev}"
RELEASE_NAME="persons-finder"
PASS=0
FAIL=0

check() {
  local description="$1"
  shift
  if "$@" &>/dev/null; then
    echo "  ✓ ${description}"
    ((PASS++))
  else
    echo "  ✗ ${description}"
    ((FAIL++))
  fi
}

echo "==> Verifying deployment in namespace: ${NAMESPACE}"
echo ""

# Helm release
echo "--- Helm Release ---"
check "Helm release exists" helm status "${RELEASE_NAME}" -n "${NAMESPACE}"

# Pods
echo ""
echo "--- Pods ---"
READY_PODS=$(kubectl get pods -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" \
  --no-headers 2>/dev/null | grep -c "Running" || true)
DESIRED_PODS=$(kubectl get deployment -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" \
  -o jsonpath='{.items[0].spec.replicas}' 2>/dev/null || echo "0")

check "Pods are running (${READY_PODS}/${DESIRED_PODS})" \
  test "${READY_PODS}" -ge 1

# Deployment
echo ""
echo "--- Deployment ---"
check "Deployment exists" \
  kubectl get deployment -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" --no-headers
check "Deployment is available" \
  kubectl rollout status deployment -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" --timeout=10s

# Service
echo ""
echo "--- Service ---"
check "Service exists" \
  kubectl get svc -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" --no-headers

# HPA (optional)
echo ""
echo "--- HPA ---"
if kubectl get hpa -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" --no-headers &>/dev/null; then
  check "HPA exists" true
else
  echo "  - HPA not configured (autoscaling may be disabled)"
fi

# Secret
echo ""
echo "--- Secret ---"
check "Secret 'persons-finder-secrets' exists" \
  kubectl get secret persons-finder-secrets -n "${NAMESPACE}"

# Summary
echo ""
echo "==================================="
echo "  Passed: ${PASS}  |  Failed: ${FAIL}"
echo "==================================="

if [[ "${FAIL}" -gt 0 ]]; then
  echo ""
  echo "Some checks failed. Run 'kubectl describe pod -n ${NAMESPACE} -l app.kubernetes.io/name=persons-finder' for details."
  exit 1
fi

echo ""
echo "All checks passed."
