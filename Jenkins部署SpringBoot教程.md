# Jenkins + Docker Registry 部署 Spring Boot 教程

> 架构：Jenkins 编译打包 → 推送镜像到 Registry → Kubernetes 拉取部署

---

## 〇、前置条件

### 开启 Docker Desktop TCP API

1. Docker Desktop → **Settings** → **General**
2. 勾选 **Expose daemon on tcp://localhost:2375 without TLS**
3. 点 **Apply & Restart**

### 预拉取基础镜像（只需一次）

```powershell
docker pull eclipse-temurin:8-jre
```

Jenkins 通过 TCP 调用的是宿主机 Docker 引擎，镜像缓存都在宿主机上。提前拉一次，后续 `docker build` 就不会再下载了。

### 安装 Docker Registry

```powershell
.\install_registry.ps1
```

启动后 Registry 运行在 `localhost:5000`。

### 安装 Registry UI（可选，Web 管理界面）

```powershell
.\install_registry_ui.ps1
```

打开 `http://localhost:8085`，可以浏览镜像列表、查看 tag、删除旧版本。

### 启动 Jenkins

```powershell
.\install_jenkins.ps1
```

初始化后配置 Tools：**Manage Jenkins → Tools**：

| 工具 | Name | 配置 |
|------|------|------|
| JDK | `JDK8` | JAVA_HOME: `/usr/lib/jvm/java-8-openjdk-amd64`，取消 "Install automatically" |
| Maven | `Maven3` | 勾选 "Install automatically"，版本选最新 |

---

## 一、整体架构

```
git push
  ↓
Jenkins (8080)
  ├── git clone
  ├── mvn clean package
  ├── docker build -t localhost:5000/poker-tracker:latest .
  ├── docker push localhost:5000/poker-tracker:latest
  │
  └──→ Docker Registry (5000)     ← 镜像仓库，存储所有版本
           ↑
           │ kubectl apply / helm install
           │
       Kubernetes 集群
           ├── Node 1 → poker-tracker pod
           ├── Node 2 → poker-tracker pod
           └── ...
```

Jenkins 只负责 **代码 → 镜像 → 推送**，不再启动容器。部署交给 Kubernetes。

---

## 二、项目准备

```
poker-tracker/
├── src/
├── pom.xml
├── Dockerfile
├── Jenkinsfile
├── poker-tracker.yaml
```

### Dockerfile

```dockerfile
FROM eclipse-temurin:8-jre

ENV TZ=Asia/Shanghai
# 小内存应用优化：限制 JVM 堆内存和元空间
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

ARG JAR_FILE=poker-tracker.jar
WORKDIR /app
COPY target/${JAR_FILE} app.jar
ENTRYPOINT ["sh","-c","java ${JAVA_OPTS:-} -jar app.jar"]
```

---

## 三、Jenkinsfile

```groovy
pipeline {
    agent any

    tools {
        jdk 'JDK8'
        maven 'Maven3'
    }

    environment {
        APP_NAME  = 'poker-tracker'
        REGISTRY  = 'localhost:5000'
        IMAGE_TAG = "${REGISTRY}/${APP_NAME}:latest"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/woyaoxuexi1231/poker.git'
            }
        }

        stage('Build Jar') {
            steps {
                echo '=== Maven 编译打包 ==='
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Build & Push Image') {
            steps {
                echo '=== 构建 Docker 镜像 ==='
                sh "docker build -t ${IMAGE_TAG} ."
                echo '=== 推送镜像到 Registry ==='
                sh "docker push ${IMAGE_TAG}"
            }
        }
    }

    post {
        success {
            echo "✅ 镜像已推送: ${IMAGE_TAG}"
        }
        failure {
            echo "❌ 构建失败，查看 Console Output"
        }
    }
}
```

---

## 四、Jenkins 创建任务

1. **新建任务** → 名称 `poker-tracker` → 选 **流水线** → OK
2. 选 **Pipeline script**，把上面的 Jenkinsfile 粘贴进去
3. 保存 → **立即构建**

---

## 五、首次构建

点 **立即构建** → **控制台输出**：

```
Checkout        → git clone
Build Jar       → mvn clean package
Build & Push    → docker build + docker push
```

最后看到 `✅ 镜像已推送: localhost:5000/poker-tracker:latest` 即成功。

验证镜像已推送：

```bash
curl http://localhost:5000/v2/poker-tracker/tags/list
```

---

## 六、Kubernetes 拉取部署

镜像已经在 Registry 里了，K8s 通过 Deployment 拉取：

```yaml
# poker-tracker.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: poker-tracker
  labels:
    app: poker-tracker
spec:
  replicas: 2
  selector:
    matchLabels:
      app: poker-tracker
  template:
    metadata:
      labels:
        app: poker-tracker
    spec:
      containers:
        - name: poker-tracker
          image: localhost:5000/poker-tracker:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8084
          env:
            - name: TZ
              value: Asia/Shanghai
          resources:
            requests:
              memory: "256Mi"
              cpu: "100m"
            limits:
              memory: "512Mi"
              cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: poker-tracker
  labels:
    app: poker-tracker
spec:
  type: NodePort
  selector:
    app: poker-tracker
  ports:
    - port: 8084
      targetPort: 8084
      nodePort: 30080
```

部署：

```bash
kubectl apply -f poker-tracker.yaml
```

---

## 七、完整流程

```
你写代码 → git push
  ↓
Jenkins 拉代码
  ↓
Maven 打包
  ↓
Docker 构建镜像
  ↓
推送: localhost:5000/poker-tracker:latest
  ↓
Kubernetes: kubectl apply / kubectl rollout restart
  ↓
应用上线
```

---

## 八、后续更新

```bash
# 改完代码 → git push
# Jenkins → poker-tracker → 立即构建（镜像自动推送到 Registry）
# K8s 侧重启 Pod 拉最新镜像：
kubectl rollout restart deployment poker-tracker
```
