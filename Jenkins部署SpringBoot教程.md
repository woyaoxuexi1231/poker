# Jenkins 部署 Spring Boot 项目 — Pipeline 流水线教程

> 架构：Windows Server + Docker Desktop + Jenkins 容器 + TCP API 控制宿主机 Docker

---

## 〇、前置条件

### 开启 Docker Desktop TCP API

1. Docker Desktop → **Settings** → **General**
2. 勾选 **Expose daemon on tcp://localhost:2375 without TLS**
3. 点 **Apply & Restart**

验证：

```bash
curl http://localhost:2375/containers/json
```

能返回数据就说明 TCP API 已开启。

### 启动 Jenkins

```bash
bash component_install_jenkins_docker.sh
```

初始化后配置 Tools：**Manage Jenkins → Tools**：

| 工具 | Name | 配置 |
|------|------|------|
| JDK | `JDK8` | JAVA_HOME: `/usr/lib/jvm/java-8-openjdk-amd64`，取消 "Install automatically" |
| Maven | `Maven3` | 勾选 "Install automatically"，版本选最新 |

---

## 一、整体架构

```
Windows Server
├── Docker Desktop (引擎)
│   ├── Jenkins 容器 (8080)
│   │   ├── Git + Maven
│   │   ├── JDK8 ← 编译你的项目
│   │   ├── Docker CLI ←─TCP:2375─→ Docker Desktop 引擎
│   │   └── Pipeline 调度
│   │
│   ├── MySQL 容器 (3306)
│   ├── Redis 容器 (6379)
│   └── poker-tracker 容器 (9090) ← Jenkins 自动部署
│
└── 浏览器
    http://服务器IP:8080 → Jenkins
    http://服务器IP:9090 → poker-tracker
```

发布流程：

```
git push
  ↓
Jenkins: git clone
  ↓
Jenkins: mvn clean package
  ↓
Jenkins: docker build  ──TCP:2375→ Docker Desktop
  ↓
Jenkins: docker rm -f   ──TCP:2375→ 停止旧容器
  ↓
Jenkins: docker run     ──TCP:2375→ 启动新容器
  ↓
应用上线
```

---

## 二、项目准备

项目结构：

```
poker-tracker/
├── src/
├── pom.xml
├── Dockerfile
└── Jenkinsfile
```

### Dockerfile

```dockerfile
FROM openjdk:8-jdk-slim
WORKDIR /app
COPY target/poker-tracker.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
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
        IMAGE_NAME = 'poker-tracker'
        CONTAINER_NAME = 'poker-tracker'
        APP_PORT = '9090'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '=== 拉取代码 ==='
                sh 'git config --global http.sslVerify false'
                sh 'git clone https://github.com/woyaoxuexi1231/poker.git .'
            }
        }

        stage('Build Jar') {
            steps {
                echo '=== Maven 编译打包 ==='
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo '=== 构建 Docker 镜像 ==='
                sh "docker build -t ${IMAGE_NAME}:latest ."
            }
        }

        stage('Stop Old Container') {
            steps {
                echo '=== 停止旧容器 ==='
                sh "docker rm -f ${CONTAINER_NAME} || true"
            }
        }

        stage('Run New Container') {
            steps {
                echo '=== 启动新容器 ==='
                sh """
                    docker run -d \
                      --name ${CONTAINER_NAME} \
                      --restart=unless-stopped \
                      -p ${APP_PORT}:8084 \
                      -e TZ=Asia/Shanghai \
                      ${IMAGE_NAME}:latest
                """
            }
        }

        stage('Verify') {
            steps {
                echo '=== 验证 ==='
                sh "sleep 3 && docker ps --filter name=${CONTAINER_NAME} --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"
                sh "curl -sf -o /dev/null http://host.docker.internal:${APP_PORT} || echo '健康检查跳过'"
            }
        }
    }

    post {
        success {
            echo "✅ 部署成功 → http://服务器IP:${APP_PORT}"
        }
        failure {
            echo "❌ 部署失败，查看 Console Output"
        }
    }
}
```

---

## 四、推送代码

```bash
git add .
git commit -m "add pipeline"
git push
```

---

## 五、Jenkins 创建任务

> **Jenkinsfile 是通用的**：上面的内容既可以存到 Git 仓库用 "Pipeline script from SCM" 引用，也可以直接粘贴到 Jenkins 的 "Pipeline script" 文本框。语法完全一样，选哪个都行。

1. **新建任务** → 名称 `poker-tracker` → 选 **流水线** → OK
2. 拉到 **流水线** 配置区域，选 **Pipeline script**，把上面的 Jenkinsfile 粘贴进去

| 配置项 | 值 |
|--------|-----|
| Definition | `Pipeline script from SCM` |
| SCM | `Git` |
| Repository URL | 你的 Git 地址 |
| Branches to build | `*/master` |
| Script Path | `Jenkinsfile` |

3. 保存 → **立即构建**

---

## 六、首次构建

点 **立即构建** → 点进构建号 → **控制台输出**，看实时日志：

```
Checkout        → git clone
Build Jar       → mvn clean package
Build Docker    → docker build -t poker-tracker:latest
Stop Old        → docker rm -f poker-tracker
Run New         → docker run -d --name poker-tracker -p 9090:8080 poker-tracker:latest
Verify          → docker ps
```

最后 `Finished: SUCCESS` 就完成了。

浏览器打开 `http://服务器IP:9090` 看到 poker-tracker。

---

## 七、Pipeline 阶段视图

构建完成后，回到 `poker-tracker` 任务页面，你会看到 **Stage View** 表格：

```
┌────────────┬──────────┬──────────────┬──────────┬──────────┬────────┐
│ Checkout   │ Build Jar│ Build Docker │ Stop Old │ Run New  │ Verify │
│ 15s ✅     │ 45s ✅   │ 22s ✅       │ 1s ✅    │ 3s ✅    │ 2s ✅  │
└────────────┴──────────┴──────────────┴──────────┴──────────┴────────┘
```

每个阶段显示耗时和成功/失败状态，绿色 ✅ 成功，红色 ❌ 失败。点进去能看到该阶段的完整日志。

> 这也是 Jenkins 被企业广泛使用的原因之一——每个项目的构建进度、每个阶段的耗时、哪步挂了，一目了然。

## 八、后续更新代码

```bash
# 改完代码
git push

# Jenkins → poker-tracker → 立即构建
# 30秒~2分钟后完成
```

---

## 八、验证 Docker 连接（排障）

进 Jenkins 容器验证：

```bash
docker exec -it jenkins bash
docker ps         # 应该能看到所有容器，包括 jenkins 自己
docker images     # 能看到镜像列表
mvn -version      # Maven 可用
```

如果 `docker ps` 报错，检查：

1. Docker Desktop 是否开了 `tcp://localhost:2375`
2. Jenkins 启动时是否有 `-e DOCKER_HOST=tcp://host.docker.internal:2375`

---

## 九、Jenkins 容器里网络要点

| 地址 | 作用 |
|------|------|
| `host.docker.internal` | 指向 Windows 宿主机 |
| `host.docker.internal:2375` | Docker Desktop TCP API |
| `host.docker.internal:9090` | 访问宿主机上暴露的应用端口 |
| `localhost` | Jenkins 容器自己（别用它访问其他容器） |

---

## 十、推荐：自动清理旧镜像

在 Pipeline 最后加一个阶段：

```groovy
stage('Clean Images') {
    steps {
        sh 'docker image prune -f'
    }
}
```

---

## 十一、目录结构建议

```
C:\Users\15434\Desktop\
├── docker-data\
│   ├── jenkins-data\    ← Jenkins 所有数据（插件/Job/凭据）
│   ├── mysql-data\      ← MySQL 数据
│   └── ...
│
├── projects\
│   └── poker-tracker\     ← Spring Boot 项目源码
│
└── scripts\              ← 所有安装脚本
```

---

## 十二、完整流程总结

```
你写代码 → git push
  ↓
Jenkins 拉代码
  ↓
Maven 打包 jar
  ↓
Docker 构建镜像 ──TCP:2375→ Docker Desktop
  ↓
停止旧容器
  ↓
启动新容器
  ↓
http://服务器IP:9090  上线 poker-tracker 🎉
```
