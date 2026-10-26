# First run `mvn clean verify` in backend on node 3.
# Build context is the repository root. Reuse the image already present on node 3.
ARG JAVA_RUNTIME_IMAGE=swr.cn-north-4.myhuaweicloud.com/oomall-javaee/openjdk:21-alpine
FROM ${JAVA_RUNTIME_IMAGE}
USER 0:0
RUN java -version && command -v wget \
    && mkdir -p /app /var/lib/rbac-admin/imports \
    && chown 10001:10001 /var/lib/rbac-admin/imports
WORKDIR /app
COPY backend/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
COPY deploy/docker/backend-entrypoint.sh /app/entrypoint.sh
RUN chmod 0555 /app/entrypoint.sh && chmod 0444 /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["server"]
