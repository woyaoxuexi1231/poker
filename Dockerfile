FROM eclipse-temurin:17-jre-jammy

ARG JAR_FILE=poker-tracker.jar
ENV TZ=Asia/Shanghai
# 小内存应用优化：限制 JVM 堆内存和元空间
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
VOLUME /tmp
WORKDIR /
COPY ${JAR_FILE} /app.jar
ENTRYPOINT ["sh","-c","java ${JAVA_OPTS:-} -jar /app.jar"]
