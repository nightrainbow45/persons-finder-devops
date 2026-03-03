# Release Process - 发布流程

本文档说明如何使用语义化版本（Semantic Versioning）发布 Persons Finder 应用。

## 语义化版本规则

版本号格式：`MAJOR.MINOR.PATCH` (例如：`1.2.3`)

- **MAJOR（主版本）**：不兼容的 API 变更
- **MINOR（次版本）**：向后兼容的功能新增
- **PATCH（补丁版本）**：向后兼容的问题修复

## 发布步骤

### 1. 确定版本号

根据变更类型确定新版本号：

```bash
# 查看当前版本
git describe --tags --abbrev=0

# 示例：当前版本是 v1.0.0
# - 修复 bug → v1.0.1
# - 新增功能 → v1.1.0
# - 破坏性变更 → v2.0.0
```

### 2. 创建并推送标签

```bash
# 创建标签（替换 X.Y.Z 为实际版本号）
git tag -a vX.Y.Z -m "Release version X.Y.Z - 简短描述"

# 推送标签到远程仓库
git push origin vX.Y.Z
```

**示例：**
```bash
# 发布 v1.0.1 补丁版本
git tag -a v1.0.1 -m "Release version 1.0.1 - Fix authentication bug"
git push origin v1.0.1

# 发布 v1.1.0 功能版本
git tag -a v1.1.0 -m "Release version 1.1.0 - Add user management feature"
git push origin v1.1.0
```

### 3. CI/CD 自动构建

推送标签后，GitHub Actions 会自动：

1. ✅ 运行测试
2. ✅ 构建 Docker 镜像
3. ✅ 安全扫描（Trivy）
4. ✅ 推送到 ECR，标签为：
   - `X.Y.Z` - 完整版本（例如：`1.0.1`）
   - `git-abc1234` - Git commit hash（唯一溯源）
5. ✅ 使用 Cosign 签名镜像
6. ✅ 生成 SBOM（软件物料清单）

### 4. 更新 Helm Values

编辑 `devops/helm/persons-finder/production-values.yaml`：

```yaml
image:
  repository: 190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder
  pullPolicy: IfNotPresent
  tag: "X.Y.Z"  # 使用新版本号
```

### 5. 部署到生产环境

```bash
# 部署到生产环境
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/production-values.yaml \
  --namespace persons-finder

# 验证部署
kubectl get pods -n persons-finder
kubectl rollout status deployment/persons-finder -n persons-finder
```

### 6. 验证发布

```bash
# 检查镜像版本
kubectl get deployment persons-finder -n persons-finder -o jsonpath='{.spec.template.spec.containers[0].image}'

# 检查应用健康状态
curl https://aifindy.digico.cloud/actuator/health

# 访问 Swagger UI
open https://aifindy.digico.cloud/swagger-ui/index.html
```

## 镜像标签策略

### 生产环境（推荐）

使用精确的语义化版本：

```yaml
image:
  tag: "1.0.1"  # ✅ 推荐：精确版本，可追溯
```

### 开发/测试环境

使用 Git SHA 标签：

```yaml
image:
  tag: "git-abc1234"  # ✅ 可追溯到具体 commit
```

## 回滚流程

如果新版本有问题，可以快速回滚到上一个版本：

```bash
# 方法 1：使用 Helm 回滚
helm rollback persons-finder -n persons-finder

# 方法 2：部署指定版本
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/production-values.yaml \
  --set image.tag="1.0.0" \
  --namespace persons-finder
```

## 版本历史

查看所有发布的版本：

```bash
# 查看 Git 标签
git tag -l "v*" --sort=-v:refname

# 查看 ECR 镜像
aws ecr describe-images \
  --repository-name persons-finder \
  --query 'sort_by(imageDetails,&imagePushedAt)[-10:].imageTags[0]' \
  --output table
```

## 最佳实践

### ✅ 推荐做法

1. **使用精确版本号**：生产环境始终使用 `X.Y.Z` 格式
2. **遵循语义化版本**：正确区分 MAJOR、MINOR、PATCH
3. **编写发布说明**：在 Git 标签中包含变更描述
4. **先测试后发布**：在 staging 环境验证后再发布到生产
5. **保留版本历史**：不要删除旧的镜像标签

### ❌ 避免做法

1. **不要使用 `latest`**：生产环境不可追溯
2. **不要跳过版本号**：保持版本连续性
3. **不要重用标签**：一旦发布就不要修改
4. **不要直接修改生产**：始终通过 CI/CD 发布

## 示例发布场景

### 场景 1：修复紧急 Bug

```bash
# 当前版本：v1.0.0
# 修复了认证 bug

# 1. 修复代码并提交
git add .
git commit -m "fix: authentication bug in login endpoint"
git push origin main

# 2. 创建补丁版本
git tag -a v1.0.1 -m "Release version 1.0.1 - Fix authentication bug"
git push origin v1.0.1

# 3. 等待 CI/CD 完成（3-5 分钟）

# 4. 更新 production-values.yaml
# image.tag: "1.0.1"

# 5. 部署
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/production-values.yaml \
  --namespace persons-finder
```

### 场景 2：发布新功能

```bash
# 当前版本：v1.0.1
# 新增了用户管理功能

# 1. 开发完成并提交
git add .
git commit -m "feat: add user management feature"
git push origin main

# 2. 创建次版本
git tag -a v1.1.0 -m "Release version 1.1.0 - Add user management"
git push origin v1.1.0

# 3. 等待 CI/CD 完成

# 4. 更新 production-values.yaml
# image.tag: "1.1.0"

# 5. 部署
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/production-values.yaml \
  --namespace persons-finder
```

### 场景 3：破坏性变更

```bash
# 当前版本：v1.1.0
# API 接口不兼容变更

# 1. 完成重大变更并提交
git add .
git commit -m "feat!: redesign API endpoints (BREAKING CHANGE)"
git push origin main

# 2. 创建主版本
git tag -a v2.0.0 -m "Release version 2.0.0 - API redesign (BREAKING CHANGE)"
git push origin v2.0.0

# 3. 等待 CI/CD 完成

# 4. 更新 production-values.yaml
# image.tag: "2.0.0"

# 5. 部署（注意：可能需要数据库迁移或其他准备工作）
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/production-values.yaml \
  --namespace persons-finder
```

## 故障排查

### 问题：CI/CD 构建失败

```bash
# 检查 GitHub Actions 日志
# https://github.com/YOUR_ORG/persons-finder-devops/actions

# 常见原因：
# - 测试失败
# - 安全扫描发现漏洞
# - ECR 权限问题
```

### 问题：镜像未推送到 ECR

```bash
# 检查 ECR 中的镜像
aws ecr describe-images \
  --repository-name persons-finder \
  --image-ids imageTag=1.0.1

# 如果不存在，检查 CI/CD 日志
```

### 问题：部署后 Pod 无法启动

```bash
# 检查 Pod 状态
kubectl get pods -n persons-finder
kubectl describe pod <pod-name> -n persons-finder

# 常见原因：
# - ImagePullBackOff：镜像标签不存在
# - CrashLoopBackOff：应用启动失败
# - Pending：资源不足
```

## 相关文档

- [Semantic Versioning 2.0.0](https://semver.org/)
- [Helm Deployment Guide](./HELM_DEPLOYMENT.md)
- [DNS Configuration](./DNS_CONFIGURATION.md)
- [Security Review](./SECURITY_REVIEW.md)

---

**最后更新：** 2026-03-03  
**版本：** 1.0.0
