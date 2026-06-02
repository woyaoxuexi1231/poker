pipeline {
    agent any

    tools {
        jdk 'JDK8'
        maven 'Maven3'
    }

    environment {
        // 容器参数（按需修改）
        APP_NAME = 'poker-tracker'
        APP_PORT = '8084'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '=== 拉取代码 ==='
                checkout scm
            }
        }

        stage('Maven Build') {
            steps {
                echo '=== Maven 编译打包 ==='
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                echo '=== 构建 Docker 镜像 ==='
                sh "docker build -t ${APP_NAME}:latest ."
            }
        }

        stage('Deploy') {
            steps {
                echo '=== 停止旧容器 ==='
                sh "docker rm -f ${APP_NAME} || true"
                echo '=== 启动新容器 ==='
                sh """
                    docker run -d \
                      --name ${APP_NAME} \
                      --restart=unless-stopped \
                      -p ${APP_PORT}:8084 \
                      -e TZ=Asia/Shanghai \
                      ${APP_NAME}:latest
                """
            }
        }

        stage('Verify') {
            steps {
                echo '=== 验证部署 ==='
                sh "sleep 5 && curl -sf -o /dev/null http://localhost:${APP_PORT}/actuator/health || echo 'Health check not available, check logs'"
                sh "docker ps --filter name=${APP_NAME} --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"
            }
        }
    }

    post {
        success {
            echo "✅ ${APP_NAME} 部署成功 -> http://localhost:${APP_PORT}"
        }
        failure {
            echo "❌ 部署失败，检查日志"
        }
    }
}