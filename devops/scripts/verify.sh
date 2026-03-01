#!/usr/bin/env bash
#
# verify.sh — Verify Persons Finder deployment and security stack health
#
# Usage:
#   ./devops/scripts/verify.sh [ENVIRONMENT]
#
# Arguments:
#   ENVIRONMENT   Target environment: dev | prod (default: dev)
#
# Namespace mapping:
#   prod → default   (app deployed in default namespace)
#   dev  → dev
#
# Checks:
#   - Helm release status
#   - Pod readiness
#   - Deployment rollout
#   - Service
#   - HPA (if enabled)
#   - K8s Secret (persons-finder-secrets)
#   - Kyverno ClusterPolicy (prod only)
#   - ESO ClusterSecretStore + ExternalSecret (prod only)
#
# Examples:
#   ./devops/scripts/verify.sh
#   ./devops/scripts/verify.sh prod
#

set -euo pipefail

ENVIRONMENT="${1:-dev}"
RELEASE_NAME="persons-finder"
PASS=0
FAIL=0

# Validate environment
if [[ "${ENVIRONMENT}" != "dev" && "${ENVIRONMENT}" != "prod" ]]; then
  echo "Error: ENVIRONMENT must be 'dev' or 'prod', got '${ENVIRONMENT}'"
  exit 1
fi

# Namespace mapping
if [[ "${ENVIRONMENT}" == "prod" ]]; then
  NAMESPACE="default"
else
  NAMESPACE="${ENVIRONMENT}"
fi

check() {
  local description="$1"
  shift
  if "$@" &>/dev/null; then
    echo "  ✓ ${description}"
    ((PASS++)) || true
  else
    echo "  ✗ ${description}"
    ((FAIL++)) || true
  fi
}

echo "==> Verifying '${ENVIRONMENT}' deployment (namespace: ${NAMESPACE})"
echo ""

# ── Helm ──────────────────────────────────────────────────────────────────
echo "--- Helm Release ---"
check "Helm release '${RELEASE_NAME}' deployed" \
  helm status "${RELEASE_NAME}" -n "${NAMESPACE}"

# ── Pods ──────────────────────────────────────────────────────────────────
echo ""
echo "--- Pods ---"
READY_PODS=$(kubectl get pods -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" \
  --no-headers 2>/dev/null | grep -c "Running" || true)
DESIRED_PODS=$(kubectl get deployment -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" \
  -o jsonpath='{.items[0].spec.replicas}' 2>/dev/null || echo "0")

check "Pods running (${READY_PODS}/${DESIRED_PODS})" \
  test "${READY_PODS}" -ge 1

# ── Deployment ────────────────────────────────────────────────────────────
echo ""
echo "--- Deployment ---"
check "Deployment exists" \
  kubectl get deployment -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" --no-headers
check "Deployment rollout complete" \
  kubectl rollout status deployment -n "${NAMESPACE}" \
    -l "app.kubernetes.io/name=persons-finder" --timeout=10s

# ── Service ───────────────────────────────────────────────────────────────
echo ""
echo "--- Service ---"
check "Service exists" \
  kubectl get svc -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" --no-headers

# ── HPA ───────────────────────────────────────────────────────────────────
echo ""
echo "--- HPA ---"
if kubectl get hpa -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" --no-headers &>/dev/null 2>&1; then
  check "HPA configured" true
else
  echo "  - HPA not present (autoscaling may be disabled in this environment)"
fi

# ── Secret ────────────────────────────────────────────────────────────────
echo ""
echo "--- Secret ---"
check "Secret 'persons-finder-secrets' exists" \
  kubectl get secret persons-finder-secrets -n "${NAMESPACE}"

# ── Security stack (prod only) ────────────────────────────────────────────
if [[ "${ENVIRONMENT}" == "prod" ]]; then
  echo ""
  echo "--- Kyverno (prod) ---"
  check "ClusterPolicy 'verify-image-signatures' exists" \
    kubectl get clusterpolicy verify-image-signatures

  POLICY_MODE=$(kubectl get clusterpolicy verify-image-signatures \
    -o jsonpath='{.spec.validationFailureAction}' 2>/dev/null || echo "unknown")
  echo "    Policy mode: ${POLICY_MODE}"
  check "ClusterPolicy in Enforce mode" \
    test "${POLICY_MODE}" == "Enforce"

  check "Kyverno admission-controller running" \
    kubectl rollout status deployment kyverno-admission-controller -n kyverno --timeout=5s

  echo ""
  echo "--- External Secrets Operator (prod) ---"
  if kubectl api-resources --api-group=external-secrets.io &>/dev/null 2>&1; then
    CSS_STATUS=$(kubectl get clustersecretstore aws-secrets-manager \
      -o jsonpath='{.status.conditions[0].reason}' 2>/dev/null || echo "NotFound")
    echo "    ClusterSecretStore status: ${CSS_STATUS}"
    check "ClusterSecretStore exists" \
      kubectl get clustersecretstore aws-secrets-manager

    ES_STATUS=$(kubectl get externalsecret persons-finder-secrets -n "${NAMESPACE}" \
      -o jsonpath='{.status.conditions[0].reason}' 2>/dev/null || echo "NotFound")
    echo "    ExternalSecret status: ${ES_STATUS}"
    check "ExternalSecret exists" \
      kubectl get externalsecret persons-finder-secrets -n "${NAMESPACE}"
  else
    echo "  - ESO CRDs not installed"
  fi
fi

# ── Summary ───────────────────────────────────────────────────────────────
echo ""
echo "==================================="
echo "  Passed: ${PASS}  |  Failed: ${FAIL}"
echo "==================================="

if [[ "${FAIL}" -gt 0 ]]; then
  echo ""
  echo "Some checks failed. Useful commands:"
  echo "  kubectl describe pod -n ${NAMESPACE} -l app.kubernetes.io/name=persons-finder"
  echo "  kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=persons-finder --tail=50"
  exit 1
fi

echo ""
echo "All checks passed."
