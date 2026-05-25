# ============================
# poker-tracker Dockerfile (K8s 版)
# 多阶段构建，一个 docker build 搞定全部
# ============================

# 阶段1: 构建（Maven + JDK 17）
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# 先下载依赖，利用 Docker 缓存层
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# 阶段2: 运行（只带 JRE，镜像小）
FROM eclipse-temurin:17-jre-jammy
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

WORKDIR /app
COPY --from=builder /app/target/poker.jar app.jar

EXPOSE 8084
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]