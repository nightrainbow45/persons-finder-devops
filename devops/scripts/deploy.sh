#!/usr/bin/env bash
#
# deploy.sh — 使用 Helm 将 Persons Finder 部署到 EKS
#             Automated Helm deployment for Persons Finder to EKS
#
# 使用方式 / Usage:
#   ./devops/scripts/deploy.sh [ENVIRONMENT] [OPTIONS]
#
# 参数 / Arguments:
#   ENVIRONMENT   目标环境 / Target environment: dev | prod (default: dev)
#
# Namespace 映射 / Namespace mapping:
#   prod → default   (与 CI/CD 流水线和 Kyverno 策略范围一致)
#   dev  → dev
#
# 选项 / Options:
#   --tag TAG           Docker 镜像 tag / Docker image tag to deploy (default: latest)
#   --namespace NS      覆盖 K8s namespace / Override Kubernetes namespace
#   --dry-run           渲染模板但不安装 / Render templates without installing
#   --set KEY=VALUE     透传额外 Helm --set 参数 / Pass additional Helm --set flags
#   -h, --help          显示帮助 / Show this help message
#
# 镜像 tag 说明 / Image tag conventions:
#   git-<sha>     CI 自动推送到 main 分支时使用（如 git-abc1234）
#                 Used by CI on pushes to main branch (e.g., git-abc1234)
#   1.4.2         semver 发版 tag（从 v1.4.2 去掉 'v' 前缀）
#                 Semver release tag (v1.4.2 → 1.4.2, 'v' prefix stripped)
#   pr-<N>        PR 构建（不推送到 ECR，仅本地 scan）
#                 PR builds (scanned locally, not pushed to ECR)
#
# 密钥管理 / Secret management:
#   prod：K8s Secret 由 External Secrets Operator（ESO）自动从 AWS Secrets Manager 同步
#         prod: K8s Secret is auto-synced from AWS Secrets Manager by ESO
#         → 使用 --set secrets.create=false，让 ESO 负责创建 Secret
#           → Use --set secrets.create=false so ESO owns the Secret
#   dev：Secret 由 Helm 内联创建（secrets.create=true），适合本地开发
#        dev: Secret is created inline by Helm (secrets.create=true), suitable for local dev
#
# 示例 / Examples:
#   ./devops/scripts/deploy.sh prod --tag git-abc1234
#   ./devops/scripts/deploy.sh prod --tag 1.4.2
#   ./devops/scripts/deploy.sh dev
#   ./devops/scripts/deploy.sh dev --dry-run
#   ./devops/scripts/deploy.sh prod --tag git-abc1234 --set resources.requests.cpu=200m
#

# 遇到任何错误立即退出，未声明变量视为错误，管道命令以最后一个非零退出码为准
# Exit immediately on error, treat unset variables as errors, pipe fails on first error
set -euo pipefail

# ── 路径常量 / Path constants ──────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CHART_DIR="${REPO_ROOT}/devops/helm/persons-finder"
RELEASE_NAME="persons-finder"

# ── 默认值 / Defaults ──────────────────────────────────────────────────────
ENVIRONMENT="${1:-dev}"
IMAGE_TAG="latest"
NAMESPACE=""
DRY_RUN=""
EXTRA_SETS=()

# ── 解析命令行参数 / Parse CLI arguments ──────────────────────────────────
# 先 shift 掉第一个位置参数（ENVIRONMENT）
# Shift away the first positional arg (ENVIRONMENT) before parsing options
shift || true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      # 镜像 tag 决定从 ECR 拉取哪个版本
      # Image tag determines which version to pull from ECR
      IMAGE_TAG="$2"
      shift 2
      ;;
    --namespace)
      # 允许覆盖默认 namespace 映射（高级用法）
      # Allows overriding the default namespace mapping (advanced use)
      NAMESPACE="$2"
      shift 2
      ;;
    --dry-run)
      # --dry-run --debug 会渲染并打印所有模板但不实际安装
      # Renders and prints all templates without actually installing anything
      DRY_RUN="--dry-run --debug"
      shift
      ;;
    --set)
      # 收集额外的 Helm --set 参数（如覆盖资源 limits、副本数等）
      # Collect additional --set args (e.g., resource limits, replica count overrides)
      EXTRA_SETS+=("--set" "$2")
      shift 2
      ;;
    -h|--help)
      # 从文件头部的注释块提取帮助信息
      # Extract help text from the header comment block at the top of this file
      head -50 "$0" | tail -48
      exit 0
      ;;
    *)
      echo "Error: Unknown option '$1'"
      exit 1
      ;;
  esac
done

# ── 验证环境名称 / Validate environment name ───────────────────────────────
if [[ "${ENVIRONMENT}" != "dev" && "${ENVIRONMENT}" != "prod" ]]; then
  echo "Error: ENVIRONMENT must be 'dev' or 'prod', got '${ENVIRONMENT}'"
  exit 1
fi

# ── Namespace 映射 / Namespace mapping ────────────────────────────────────
# prod 的 default namespace：
#   1. CI/CD pipeline（ci-cd.yml）也部署到 default
#   2. Kyverno ClusterPolicy 的 match.namespaces 仅包含 default
# prod uses 'default' because:
#   1. CI/CD pipeline (ci-cd.yml) also deploys to default
#   2. Kyverno ClusterPolicy match.namespaces only includes 'default'
if [[ -z "${NAMESPACE}" ]]; then
  if [[ "${ENVIRONMENT}" == "prod" ]]; then
    NAMESPACE="default"
  else
    NAMESPACE="${ENVIRONMENT}"
  fi
fi

# ── Values 文件选择 / Values file selection ────────────────────────────────
# values-prod.yaml 启用了 sidecar、NetworkPolicy、HPA、IRSA 等 prod 特性
# values-dev.yaml  使用更低的资源 limits，适合 dev/本地开发
# values-prod.yaml enables sidecar, NetworkPolicy, HPA, IRSA, etc. for prod
# values-dev.yaml  uses lower resource limits, suitable for dev/local
VALUES_FILE="${CHART_DIR}/values-${ENVIRONMENT}.yaml"

# ── 前置命令检查 / Check required CLI tools ────────────────────────────────
for cmd in kubectl helm; do
  if ! command -v "${cmd}" &>/dev/null; then
    echo "Error: '${cmd}' is not installed."
    exit 1
  fi
done

# ── 验证 values 文件存在 / Verify values file exists ──────────────────────
if [[ ! -f "${VALUES_FILE}" ]]; then
  echo "Error: Values file not found: ${VALUES_FILE}"
  exit 1
fi

# ── 打印部署概要 / Print deployment summary ───────────────────────────────
echo "==> Current kubectl context: $(kubectl config current-context)"
echo "==> Environment:  ${ENVIRONMENT}"
echo "==> Namespace:    ${NAMESPACE}"
echo "==> Image tag:    ${IMAGE_TAG}"
echo "==> Values file:  ${VALUES_FILE}"
echo ""

# ── 密钥创建标志 / Secret creation flag ──────────────────────────────────
# prod：secrets.create=false — 由 ESO 负责创建和维护 Secret，Helm 不介入
#       如果 Helm 创建了 Secret，ESO 会因"already exists"冲突而失败
# prod: secrets.create=false — ESO owns the Secret lifecycle; if Helm also
#       creates it, ESO will fail with "already exists" conflict
# dev： secrets.create=true  — Helm 直接创建 Secret（值来自 values-dev.yaml）
#       dev: secrets.create=true  — Helm creates Secret inline (value from values-dev.yaml)
SECRETS_FLAG="--set secrets.create=false"
if [[ "${ENVIRONMENT}" == "dev" ]]; then
  SECRETS_FLAG="--set secrets.create=true"
fi

# ── 执行 Helm 部署 / Execute Helm deployment ──────────────────────────────
# helm upgrade --install：
#   - 如果 release 不存在 → 执行 install
#   - 如果 release 已存在 → 执行 upgrade（滚动更新）
# helm upgrade --install:
#   - If release doesn't exist → install
#   - If release already exists → upgrade (rolling update)
#
# --wait --timeout 5m：等待所有 Pod 就绪再返回，失败时回滚
# --wait --timeout 5m: wait for all pods to be ready before returning, roll back on failure
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

# ── 部署成功后输出状态 / Show status after successful deploy ───────────────
if [[ -z "${DRY_RUN}" ]]; then
  echo ""
  echo "==> Deployment complete."
  helm status "${RELEASE_NAME}" -n "${NAMESPACE}"
  echo ""
  echo "Run './devops/scripts/verify.sh ${ENVIRONMENT}' for full verification."
fi
