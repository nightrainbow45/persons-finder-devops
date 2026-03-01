LOCAL_IMAGE := persons-finder-local
LOCAL_TAG   := dev

.PHONY: build scan clean

## 删除旧的本地测试镜像（幂等，不存在时不报错）
clean:
	docker rmi $(LOCAL_IMAGE):$(LOCAL_TAG) 2>/dev/null || true

## 全新构建：先清理，再 build（不带 --platform，让 Docker 用宿主机原生架构做本地测试）
build: clean
	docker build -f devops/docker/Dockerfile -t $(LOCAL_IMAGE):$(LOCAL_TAG) .

## 全新扫描：先清理，再构建 linux/amd64 镜像（匹配 EKS t3.small），再 trivy 扫描，扫描后清理
scan: clean
	docker build --platform linux/amd64 -f devops/docker/Dockerfile -t $(LOCAL_IMAGE):$(LOCAL_TAG) .
	trivy image \
		--severity CRITICAL,HIGH \
		--ignore-unfixed \
		--ignorefile .trivyignore \
		--no-progress \
		$(LOCAL_IMAGE):$(LOCAL_TAG)
	$(MAKE) clean
