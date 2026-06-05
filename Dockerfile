FROM ubuntu:22.04

# 安装 JRE 运行依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    libfreetype6 fontconfig ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 复制本地下载的 JDK 8
COPY jdk-8-linux-x64.tar.gz /opt/
RUN mkdir -p /opt/jdk8 && \
    tar -xzf /opt/jdk-8-linux-x64.tar.gz -C /opt/jdk8 --strip-components=1 && \
    rm /opt/jdk-8-linux-x64.tar.gz

ENV JAVA_HOME=/opt/jdk8
ENV PATH=$JAVA_HOME/bin:$PATH
ENV TZ=Asia/Shanghai
# 小内存应用优化：限制 JVM 堆内存和元空间
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

ARG JAR_FILE=poker-tracker.jar
WORKDIR /app
COPY target/${JAR_FILE} app.jar
ENTRYPOINT ["sh","-c","java ${JAVA_OPTS:-} -jar app.jar"]

