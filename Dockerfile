# XSS 微服务集群统一 Dockerfile
# 使用方式：docker build -t xss/{service-name}:latest --build-arg SERVICE_NAME={service-name} .

FROM eclipse-temurin:21-jre-alpine

ARG SERVICE_NAME

WORKDIR /app

# 设置时区并安装 curl（用于健康检查）
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 复制 jar 包（从 Maven target 目录）
COPY ${SERVICE_NAME}/target/${SERVICE_NAME}-*.jar app.jar

# 启动命令
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
