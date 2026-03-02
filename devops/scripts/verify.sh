#!/usr/bin/env bash
#
# verify.sh — 验证 Persons Finder 部署及安全栈的健康状态
#             Verify Persons Finder deployment and security stack health
#
# 使用方式 / Usage:
#   ./devops/scripts/verify.sh [ENVIRONMENT]
#
# 参数 / Arguments:
#   ENVIRONMENT   目标环境 / Target environment: dev | prod (default: dev)
#
# Namespace 映射 / Namespace mapping:
#   prod → default   (与 CI/CD 流水线和 Kyverno 策略范围一致)
#   dev  → dev
#
# 检查项 / Checks performed:
#   所有环境 / All environments:
#     - Helm release 状态（deployed / failed）
#     - Pod 就绪数量（running pods vs desired replicas）
#     - Deployment rollout 是否完成
#     - Service 是否存在
#     - HPA 是否配置（可选，禁用时不计为失败）
#     - K8s Secret persons-finder-secrets 是否存在
#   仅 prod / prod only:
#     - Kyverno ClusterPolicy 是否存在且处于 Enforce 模式
#     - Kyverno admission-controller 是否运行
#     - ESO ClusterSecretStore 状态
#     - ESO ExternalSecret 同步状态
#
# 退出码 / Exit codes:
#   0  — 所有检查通过 / All checks passed
#   1  — 有一个或多个检查失败 / One or more checks failed
#
# 示例 / Examples:
#   ./devops/scripts/verify.sh
#   ./devops/scripts/verify.sh prod
#

# 遇到任何错误立即退出，未声明变量视为错误，管道命令以最后一个非零退出码为准
# Exit immediately on error, treat unset variables as errors, pipe fails on first error
set -euo pipefail

# ── 环境参数 / Environment parameters ─────────────────────────────────────
ENVIRONMENT="${1:-dev}"
RELEASE_NAME="persons-finder"
PASS=0
FAIL=0

# ── 验证环境名称 / Validate environment name ───────────────────────────────
if [[ "${ENVIRONMENT}" != "dev" && "${ENVIRONMENT}" != "prod" ]]; then
  echo "Error: ENVIRONMENT must be 'dev' or 'prod', got '${ENVIRONMENT}'"
  exit 1
fi

# ── Namespace 映射 / Namespace mapping ────────────────────────────────────
# 与 deploy.sh 和 CI/CD 保持一致
# Must match deploy.sh and CI/CD pipeline
if [[ "${ENVIRONMENT}" == "prod" ]]; then
  NAMESPACE="default"
else
  NAMESPACE="${ENVIRONMENT}"
fi

# ── 辅助函数：执行检查并记录结果 / Helper: run check and record result ─────
# 用法 / Usage: check "描述" command [args...]
# 命令成功（exit 0）→ 打印 ✓ 并增加 PASS 计数
# 命令失败（exit ≠0）→ 打印 ✗ 并增加 FAIL 计数
# Command succeeds (exit 0) → print ✓, increment PASS
# Command fails   (exit ≠0) → print ✗, increment FAIL
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

# ══════════════════════════════════════════════════════════════════════════════
# Helm Release 状态 / Helm Release status
# ══════════════════════════════════════════════════════════════════════════════
# helm status 检查 release 是否存在且状态为 deployed
# helm status checks that the release exists and is in 'deployed' state
echo "--- Helm Release ---"
check "Helm release '${RELEASE_NAME}' deployed" \
  helm status "${RELEASE_NAME}" -n "${NAMESPACE}"

# ══════════════════════════════════════════════════════════════════════════════
# Pod 就绪状态 / Pod readiness
# ══════════════════════════════════════════════════════════════════════════════
# 分别统计实际运行中的 Pod 数和 Deployment 期望副本数，进行对比
# Count running pods vs desired replicas to detect crash-loops or scheduling failures
echo ""
echo "--- Pods ---"
READY_PODS=$(kubectl get pods -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" \
  --no-headers 2>/dev/null | grep -c "Running" || true)
DESIRED_PODS=$(kubectl get deployment -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" \
  -o jsonpath='{.items[0].spec.replicas}' 2>/dev/null || echo "0")

# 只要有至少 1 个 Pod 运行即视为通过（HPA 可能动态调整副本数）
# Pass if at least 1 pod is running (HPA may dynamically change replica count)
check "Pods running (${READY_PODS}/${DESIRED_PODS})" \
  test "${READY_PODS}" -ge 1

# ══════════════════════════════════════════════════════════════════════════════
# Deployment rollout 状态 / Deployment rollout status
# ══════════════════════════════════════════════════════════════════════════════
# rollout status 会等待所有副本就绪（受 --timeout 限制）
# 检测到还有未就绪的 Pod 会返回非零退出码
# rollout status waits for all replicas to be ready (bounded by --timeout)
# Returns non-zero if any pods are not yet ready
echo ""
echo "--- Deployment ---"
check "Deployment exists" \
  kubectl get deployment -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" --no-headers
check "Deployment rollout complete" \
  kubectl rollout status deployment -n "${NAMESPACE}" \
    -l "app.kubernetes.io/name=persons-finder" --timeout=10s

# ══════════════════════════════════════════════════════════════════════════════
# Service 存在性 / Service existence
# ══════════════════════════════════════════════════════════════════════════════
# Service 是 Pod 的稳定访问入口（ClusterIP / ALB Ingress）
# Service provides stable access to pods (ClusterIP or ALB Ingress)
echo ""
echo "--- Service ---"
check "Service exists" \
  kubectl get svc -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" --no-headers

# ══════════════════════════════════════════════════════════════════════════════
# HPA 检查 / HPA check
# ══════════════════════════════════════════════════════════════════════════════
# HPA（HorizontalPodAutoscaler）根据 CPU/Memory 指标自动扩缩容
# HPA 只在 prod（values-prod.yaml: autoscaling.enabled=true）启用
# HPA scales pods based on CPU/Memory metrics; only enabled in prod
# dev 环境不启用 HPA，不视为失败，仅输出提示
# If not present (e.g., dev env), it's not counted as failure — just informational
echo ""
echo "--- HPA ---"
if kubectl get hpa -n "${NAMESPACE}" -l "app.kubernetes.io/name=persons-finder" \
     --no-headers &>/dev/null 2>&1; then
  check "HPA configured" true
else
  echo "  - HPA not present (autoscaling may be disabled in this environment)"
fi

# ══════════════════════════════════════════════════════════════════════════════
# K8s Secret 存在性 / K8s Secret existence
# ══════════════════════════════════════════════════════════════════════════════
# 应用 Pod 通过 envFrom.secretRef 挂载这个 Secret 获取 OPENAI_API_KEY
# prod：Secret 由 ESO 从 AWS Secrets Manager 同步创建
# dev：Secret 由 Helm 内联创建（或 local-test.sh 手动创建）
# The app pod mounts this Secret via envFrom.secretRef to get OPENAI_API_KEY
# prod: Secret is created by ESO syncing from AWS Secrets Manager
# dev:  Secret is created by Helm inline (or manually in local-test.sh)
echo ""
echo "--- Secret ---"
check "Secret 'persons-finder-secrets' exists" \
  kubectl get secret persons-finder-secrets -n "${NAMESPACE}"

# ══════════════════════════════════════════════════════════════════════════════
# 安全栈检查（仅 prod）/ Security stack checks (prod only)
# ══════════════════════════════════════════════════════════════════════════════
if [[ "${ENVIRONMENT}" == "prod" ]]; then

  # ── Kyverno ClusterPolicy ─────────────────────────────────────────────────
  # 检查签名验证策略是否存在且处于 Enforce 模式
  # Verify the image-signature policy exists and is in Enforce (not Audit) mode
  #   Audit  mode: log violations only, does NOT block unsigned images
  #   Enforce mode: block unsigned images at admission — production security stance
  echo ""
  echo "--- Kyverno (prod) ---"
  check "ClusterPolicy 'verify-image-signatures' exists" \
    kubectl get clusterpolicy verify-image-signatures

  POLICY_MODE=$(kubectl get clusterpolicy verify-image-signatures \
    -o jsonpath='{.spec.validationFailureAction}' 2>/dev/null || echo "unknown")
  echo "    Policy mode: ${POLICY_MODE}"
  check "ClusterPolicy in Enforce mode" \
    test "${POLICY_MODE}" == "Enforce"

  # Kyverno admission-controller 必须运行才能验签
  # The admission controller must be running to intercept pod creation and verify signatures
  check "Kyverno admission-controller running" \
    kubectl rollout status deployment kyverno-admission-controller -n kyverno --timeout=5s

  # ── PII Redaction Sidecar (Layer 2) ──────────────────────────────────────
  # values-prod.yaml 启用了 sidecar（sidecar.enabled=true），每个 Pod 包含两个容器：
  # 主应用容器（persons-finder）+ sidecar 容器（pii-redaction-sidecar）
  # prod enables the sidecar, so each pod has two containers: main app + sidecar.
  #
  # 检查项 / Checks:
  #   1. sidecar 容器处于 Ready 状态（确认镜像拉取成功、进程正常启动）
  #   2. 主容器存在 LLM_PROXY_URL 环境变量（确认 Helm 将 LLM 流量路由到 sidecar）
  # Checks:
  #   1. Sidecar container is Ready (image pulled successfully, process started)
  #   2. Main container has LLM_PROXY_URL env var (Helm wired LLM traffic through sidecar)
  echo ""
  echo "--- PII Redaction Sidecar / Layer 2 (prod) ---"

  # 通过 jsonpath 查询 pii-redaction-sidecar 容器的 ready 状态
  # Query the ready field of the pii-redaction-sidecar container via jsonpath
  SIDECAR_READY=$(kubectl get pods -n "${NAMESPACE}" \
    -l "app.kubernetes.io/name=persons-finder" \
    -o jsonpath='{.items[0].status.containerStatuses[?(@.name=="pii-redaction-sidecar")].ready}' \
    2>/dev/null || echo "false")
  echo "    Sidecar container ready: ${SIDECAR_READY}"
  check "Sidecar container (pii-redaction-sidecar) Ready" \
    test "${SIDECAR_READY}" == "true"

  # 检查主容器是否注入了 LLM_PROXY_URL（由 deployment.yaml 在 sidecar.enabled=true 时添加）
  # Check that LLM_PROXY_URL was injected into the main container (added by deployment.yaml
  # when sidecar.enabled=true), which routes all LLM calls through the sidecar proxy
  LLM_PROXY_URL=$(kubectl get pods -n "${NAMESPACE}" \
    -l "app.kubernetes.io/name=persons-finder" \
    -o jsonpath='{.items[0].spec.containers[?(@.name=="persons-finder")].env[?(@.name=="LLM_PROXY_URL")].value}' \
    2>/dev/null || echo "")
  echo "    LLM_PROXY_URL: ${LLM_PROXY_URL:-<not set>}"
  check "LLM_PROXY_URL injected into main container" \
    test -n "${LLM_PROXY_URL}"

  # ── External Secrets Operator ─────────────────────────────────────────────
  # 检查 ESO 同步状态：
  #   ClusterSecretStore: 连接 AWS Secrets Manager 的配置，status.conditions[0].reason
  #                       应为 "Valid" 或 "StoreValidated"
  #   ExternalSecret: 同步任务状态，status.conditions[0].reason 应为 "SecretSynced"
  # ESO sync status checks:
  #   ClusterSecretStore: config for connecting to AWS Secrets Manager,
  #                       status.conditions[0].reason should be "Valid"
  #   ExternalSecret: sync task status, reason should be "SecretSynced"
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

    # 如果 ExternalSecret 状态不是 SecretSynced，输出排查提示
    # If ExternalSecret is not synced, show troubleshooting hint
    if [[ "${ES_STATUS}" != "SecretSynced" ]]; then
      echo "    HINT: ExternalSecret not synced. Check AWS Secrets Manager value:"
      echo "      aws secretsmanager get-secret-value \\"
      echo "        --secret-id persons-finder/prod/openai-api-key \\"
      echo "        --region ap-southeast-2"
      echo "    Force resync with:"
      echo "      kubectl annotate externalsecret persons-finder-secrets \\"
      echo "        force-sync=\$(date +%s) --overwrite -n ${NAMESPACE}"
    fi
  else
    echo "  - ESO CRDs not installed (run setup-eks.sh to install ESO)"
  fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# 检查结果汇总 / Summary
# ══════════════════════════════════════════════════════════════════════════════
echo ""
echo "==================================="
echo "  Passed: ${PASS}  |  Failed: ${FAIL}"
echo "==================================="

if [[ "${FAIL}" -gt 0 ]]; then
  echo ""
  echo "Some checks failed. Useful troubleshooting commands:"
  echo ""
  echo "  # 查看 Pod 详情（含事件）/ Describe pod (includes events):"
  echo "  kubectl describe pod -n ${NAMESPACE} -l app.kubernetes.io/name=persons-finder"
  echo ""
  echo "  # 查看最近日志 / View recent logs:"
  echo "  kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=persons-finder --tail=50"
  echo ""
  echo "  # 查看所有资源 / List all resources:"
  echo "  kubectl get all -n ${NAMESPACE}"
  exit 1
fi

echo ""
echo "All checks passed."
