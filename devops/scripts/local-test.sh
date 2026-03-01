#!/usr/bin/env bash
#
# local-test.sh — 使用 Kind 在本地进行 Helm Chart 部署测试
#                 Local Helm Chart deployment test using Kind (Kubernetes in Docker)
#
# 使用方式 / Usage:
#   ./devops/scripts/local-test.sh [ACTION]
#
# 操作 / Actions:
#   up        创建集群、构建镜像、Helm 部署（默认）
#             Create cluster, build image, deploy with Helm (default)
#   down      销毁 Kind 集群 / Tear down the Kind cluster
#   status    查看当前部署状态 / Show current deployment status
#
# 本地测试说明 / Local test notes:
#   Kind（Kubernetes in Docker）在 Docker 容器中运行完整的 K8s 控制平面和节点
#   Kind (Kubernetes in Docker) runs a full K8s control plane and node in Docker containers
#
#   本地测试使用简化配置（与 prod/dev 环境的区别）：
#   Local test uses simplified config (differences from prod/dev):
#     - 镜像从 Kind 本地加载，不走 ECR / Image loaded locally into Kind, no ECR pull
#     - imagePullPolicy=Never（使用本地构建的镜像）/ imagePullPolicy=Never (use local image)
#     - autoscaling=false（无 metrics-server）/ autoscaling=false (no metrics-server)
#     - secrets.create=false（Secret 由脚本手动创建）/ secrets.create=false (created manually)
#     - OPENAI_API_KEY 使用占位符值，无需真实 key 即可测试应用启动
#       OPENAI_API_KEY uses placeholder — tests app startup without a real key
#
# 前置条件 / Prerequisites:
#   - Docker（运行中）/ Docker (must be running)
#   - Kind: brew install kind
#   - kubectl: brew install kubectl
#   - Helm: brew install helm
#
# 访问应用 / Accessing the app after 'up':
#   kubectl port-forward svc/persons-finder 8080:80
#   curl http://localhost:8080/actuator/health
#   curl http://localhost:8080/api/v1/persons
#
# 示例 / Examples:
#   ./devops/scripts/local-test.sh
#   ./devops/scripts/local-test.sh up
#   ./devops/scripts/local-test.sh down
#   ./devops/scripts/local-test.sh status
#

# 遇到任何错误立即退出，未声明变量视为错误，管道命令以最后一个非零退出码为准
# Exit immediately on error, treat unset variables as errors, pipe fails on first error
set -euo pipefail

# ── 路径常量 / Path constants ──────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CHART_DIR="${REPO_ROOT}/devops/helm/persons-finder"
CLUSTER_NAME="persons-finder"
IMAGE_NAME="persons-finder:latest"
RELEASE_NAME="persons-finder"

ACTION="${1:-up}"

# ── 前置命令检查 / Check required CLI tools ────────────────────────────────
check_prereqs() {
  for cmd in docker kind kubectl helm; do
    if ! command -v "${cmd}" &>/dev/null; then
      echo "Error: '${cmd}' is required but not installed."
      echo "Install with: brew install ${cmd}"
      exit 1
    fi
  done
  # 检查 Docker daemon 是否运行
  # Check that Docker daemon is actually running (not just installed)
  if ! docker info &>/dev/null; then
    echo "Error: Docker daemon is not running. Start Docker and retry."
    exit 1
  fi
}

# ── 检查 Kind 集群是否存在 / Check if Kind cluster exists ─────────────────
cluster_exists() {
  kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"
}

# ══════════════════════════════════════════════════════════════════════════════
# do_up — 完整的本地测试部署流程 / Full local test deployment flow
# ══════════════════════════════════════════════════════════════════════════════
do_up() {
  check_prereqs

  # ── Step 1: 创建 Kind 集群 / Create Kind cluster ──────────────────────────
  # Kind 在 Docker 中启动一个 K8s 控制平面 + 工作节点，无需任何云资源
  # Kind spins up a K8s control plane + worker node in Docker, no cloud required
  echo "==> Step 1: Create Kind cluster"
  if cluster_exists; then
    # 集群已存在时复用，避免重复创建（幂等）
    # Reuse existing cluster to be idempotent (avoids recreation on rerun)
    echo "    Cluster '${CLUSTER_NAME}' already exists, reusing."
  else
    kind create cluster --name "${CLUSTER_NAME}"
    echo "    Cluster '${CLUSTER_NAME}' created."
  fi

  # ── Step 2: 构建 Docker 镜像 / Build Docker image ─────────────────────────
  # 使用 devops/docker/Dockerfile 的多阶段构建：
  #   Stage 1（build）: gradle:7.6-jdk11 — 编译 Spring Boot app，生成 JAR
  #   Stage 2（runtime）: eclipse-temurin:11-jre-alpine — 轻量运行时镜像
  # Multi-stage Dockerfile:
  #   Stage 1 (build): gradle:7.6-jdk11 — compile Spring Boot app, produce JAR
  #   Stage 2 (runtime): eclipse-temurin:11-jre-alpine — lightweight runtime image
  echo ""
  echo "==> Step 2: Build Docker image '${IMAGE_NAME}'"
  docker build -t "${IMAGE_NAME}" -f "${REPO_ROOT}/devops/docker/Dockerfile" "${REPO_ROOT}"
  echo "    Image built: ${IMAGE_NAME}"

  # ── Step 3: 加载镜像到 Kind / Load image into Kind ────────────────────────
  # Kind 的节点是 Docker 容器，无法直接访问宿主机的 Docker image cache
  # Kind nodes are Docker containers and cannot access the host's Docker image cache
  # kind load docker-image 将镜像从宿主机 Docker 复制到 Kind 节点的 containerd
  # kind load copies the image from host Docker into the Kind node's containerd
  # 这样 Pod 才能使用 imagePullPolicy=Never 而不走 registry 拉取
  # This allows pods to use imagePullPolicy=Never without pulling from a registry
  echo ""
  echo "==> Step 3: Load image into Kind cluster"
  kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"
  echo "    Image loaded into Kind."

  # ── Step 4: 创建 K8s Secret / Create K8s Secret ───────────────────────────
  # 应用通过 envFrom.secretRef 读取 OPENAI_API_KEY
  # The app reads OPENAI_API_KEY via envFrom.secretRef
  # 本地测试使用占位符值——验证应用能正常启动，PII 代理逻辑需真实 key 才能调用 OpenAI
  # Use placeholder value for local testing — verifies app starts; PII proxy needs a real key for OpenAI calls
  # --dry-run=client -o yaml | kubectl apply -f - 实现幂等创建（已存在则更新）
  # --dry-run=client -o yaml | kubectl apply -f - achieves idempotent create (update if exists)
  echo ""
  echo "==> Step 4: Create K8s secret (placeholder value for local testing)"
  kubectl create secret generic persons-finder-secrets \
    --from-literal=OPENAI_API_KEY=test-local-key \
    --dry-run=client -o yaml | kubectl apply -f -
  echo "    Secret 'persons-finder-secrets' created/updated."

  # ── Step 5: Helm 部署 / Helm deploy ──────────────────────────────────────
  # 本地测试的关键差异 / Key differences for local testing:
  #   --set image.repository=persons-finder  — 使用本地镜像名（无 ECR registry 前缀）
  #                                            Use local image name (no ECR registry prefix)
  #   --set image.tag=latest                 — 匹配 Step 2 构建的 tag
  #                                            Match the tag built in Step 2
  #   --set image.pullPolicy=Never           — K8s 不从 registry 拉取，使用 Kind 加载的镜像
  #                                            K8s uses the locally loaded image, never pulls
  #   --set secrets.create=false             — Secret 已在 Step 4 手动创建
  #                                            Secret already created manually in Step 4
  #   --set autoscaling.enabled=false        — Kind 无 metrics-server，无法使用 HPA
  #                                            Kind has no metrics-server, HPA won't work
  echo ""
  echo "==> Step 5: Deploy with Helm (local config)"
  helm upgrade --install "${RELEASE_NAME}" "${CHART_DIR}" \
    -f "${CHART_DIR}/values-dev.yaml" \
    --set image.repository=persons-finder \
    --set image.tag=latest \
    --set image.pullPolicy=Never \
    --set secrets.create=false \
    --set autoscaling.enabled=false \
    --wait \
    --timeout 5m
  echo "    Helm release '${RELEASE_NAME}' deployed."

  # ── Step 6: 等待 Pod 就绪 / Wait for pods to be ready ────────────────────
  # rollout status 轮询 Deployment 直到所有副本就绪，或超时退出
  # rollout status polls the Deployment until all replicas are Ready, or times out
  echo ""
  echo "==> Step 6: Wait for pods to be ready"
  kubectl rollout status deployment -l "app.kubernetes.io/name=persons-finder" --timeout=120s
  echo "    All pods ready."

  # ── 输出使用说明 / Print usage instructions ───────────────────────────────
  echo ""
  echo "==> Local deployment complete!"
  echo ""
  kubectl get pods
  echo ""
  echo "To access the application:"
  echo "  kubectl port-forward svc/persons-finder 8080:80"
  echo "  curl http://localhost:8080/actuator/health"
  echo "  curl http://localhost:8080/api/v1/persons"
  echo ""
  echo "To run the devops test suite against this deployment:"
  echo "  ./gradlew test --tests 'com.persons.finder.devops.*'"
  echo ""
  echo "To tear down:"
  echo "  ./devops/scripts/local-test.sh down"
}

# ══════════════════════════════════════════════════════════════════════════════
# do_down — 销毁 Kind 集群 / Destroy Kind cluster
# ══════════════════════════════════════════════════════════════════════════════
do_down() {
  echo "==> Tearing down Kind cluster '${CLUSTER_NAME}'"
  if cluster_exists; then
    kind delete cluster --name "${CLUSTER_NAME}"
    echo "    Cluster '${CLUSTER_NAME}' deleted."
    echo "    Note: The Docker image '${IMAGE_NAME}' still exists on your host."
    echo "    To remove it: docker rmi ${IMAGE_NAME}"
  else
    echo "    Cluster '${CLUSTER_NAME}' does not exist, nothing to delete."
  fi
}

# ══════════════════════════════════════════════════════════════════════════════
# do_status — 显示当前部署状态 / Show current deployment status
# ══════════════════════════════════════════════════════════════════════════════
do_status() {
  if ! cluster_exists; then
    echo "Kind cluster '${CLUSTER_NAME}' is not running."
    echo "Run './devops/scripts/local-test.sh up' to start."
    exit 0
  fi

  echo "==> Kind cluster '${CLUSTER_NAME}' is running."
  echo ""
  echo "==> Helm Release"
  helm status "${RELEASE_NAME}" 2>/dev/null || echo "  Not installed."

  echo ""
  echo "==> Pods"
  kubectl get pods -l "app.kubernetes.io/name=persons-finder" 2>/dev/null || echo "  None found."

  echo ""
  echo "==> Services"
  kubectl get svc -l "app.kubernetes.io/name=persons-finder" 2>/dev/null || echo "  None found."

  echo ""
  echo "To access: kubectl port-forward svc/persons-finder 8080:80"
}

# ── 主入口 / Main entry point ──────────────────────────────────────────────
case "${ACTION}" in
  up)     do_up ;;
  down)   do_down ;;
  status) do_status ;;
  *)
    echo "Error: Unknown action '${ACTION}'. Valid actions: up, down, status"
    exit 1
    ;;
esac
