ARG JAVA_IMAGE=eclipse-temurin:21-jre-alpine
FROM ${JAVA_IMAGE}

RUN apk add --no-cache curl \
    && addgroup -S -g 10001 app \
    && adduser -S -u 10001 -G app app \
    && mkdir -p /app/logs && chown -R app:app /app
WORKDIR /app
COPY --chown=app:app target/rbac1-ddd-*.jar app.jar

ENV SERVER_PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
USER app
EXPOSE 8080
# 公钥接口仅用于确认进程可响应，不代替数据库就绪检查。
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl --fail --silent --max-time 4 http://127.0.0.1:8080/.well-known/jwks.json > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
