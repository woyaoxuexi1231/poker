# Poker 项目 Docker 部署指南

## 📦 项目信息

- **项目名称**: Poker Tracker (德扑计分器)
- **默认端口**: 8080
- **Context-Path**: `/poker`
- **JAR文件**: `poker-tracker-1.0.0-SNAPSHOT.jar`

## 🚀 快速开始

### 1. 打包项目

```bash
mvn clean package -DskipTests
```

### 2. 复制JAR文件（可选）

```bash
# 将target目录下的jar文件复制到项目根目录（run.sh会自动查找）
cp target/poker-tracker-1.0.0-SNAPSHOT.jar .
```

### 3. 运行Docker容器

```bash
# 使用默认配置启动
bash run.sh

# 或自定义配置
HOST_PORT=9090 IMAGE_NAME=poker:v1 bash run.sh
```

## ⚙️ 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `HOST_PORT` | 8080 | 宿主机映射端口 |
| `CONTAINER_PORT` | 8080 | 容器内部端口 |
| `IMAGE_NAME` | poker-tracker:latest | Docker镜像名称 |
| `CONTAINER_NAME` | poker-tracker | 容器名称 |
| `JAR_FILE` | 自动查找 | JAR文件路径 |
| `RESTART_POLICY` | unless-stopped | 重启策略 |
| `TZ` | Asia/Shanghai | 时区设置 |

## 📝 使用示例

### 示例1: 默认启动

```bash
bash run.sh
```

访问地址: `http://localhost:8080/poker/`

### 示例2: 自定义端口

```bash
HOST_PORT=9090 bash run.sh
```

访问地址: `http://localhost:9090/poker/`

### 示例3: 自定义镜像名

```bash
IMAGE_NAME=poker-tracker:v1.0 CONTAINER_NAME=poker-prod bash run.sh
```

## 🔧 常用Docker命令

```bash
# 查看容器日志
docker logs -f poker-tracker

# 停止容器
docker stop poker-tracker

# 启动容器
docker start poker-tracker

# 删除容器
docker rm -f poker-tracker

# 查看容器状态
docker ps | grep poker-tracker
```

## 🌐 Nginx 配置

如果使用Nginx反向代理，配置如下：

```nginx
location /poker {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    
    # WebSocket 支持
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

## 📊 数据库配置

项目需要MySQL数据库，请确保：
1. MySQL服务已启动
2. 在 `application.yml` 中配置正确的数据库连接信息
3. 数据库 `poker` 已创建

## ⚠️ 注意事项

- 容器使用 `unless-stopped` 重启策略
- 手动停止容器后不会自动重启，直到Docker服务重启或手动启动
- 日志会输出到Docker日志系统，使用 `docker logs` 查看
