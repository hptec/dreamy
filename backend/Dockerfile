# backend 运行镜像:JAR 已由 scripts/release.sh 在本地编译(JVM 字节码跨架构,无需在镜像内编译)。
# 服务器架构 x86_64:release.sh 以 docker buildx --platform linux/amd64 构建本文件。
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY build/libs/identity-app.jar /app/identity-app.jar

# application.yml 固定 server.port=18081;TZ 可被运行时环境变量覆盖
ENV TZ=UTC
EXPOSE 18081

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:18081/actuator/health | grep -q '"status":"UP"' || exit 1

# JAVA_OPTS 运行时注入(如 -Xmx1g);sh -c 形式保证变量展开后 exec 替换为 1 号进程(信号直达)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/identity-app.jar"]
