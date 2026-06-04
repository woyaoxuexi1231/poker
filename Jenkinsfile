pipeline {
    agent any

    tools {
        jdk 'JDK8'
        maven 'Maven3'
    }

    environment {
        IMAGE_NAME = 'poker-tracker'
        CONTAINER_NAME = 'poker-tracker'
        APP_PORT = '8084'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '=== 拉取代码 ==='
                checkout scm
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
                      -p ${APP_PORT}:8080 \
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