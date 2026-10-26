# Build context is the repository root.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /source/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
COPY backend/src/ src/
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress clean verify

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 rbac && useradd --uid 10001 --gid 10001 --no-create-home rbac \
    && mkdir -p /app /var/lib/rbac-admin/imports \
    && chown 10001:10001 /var/lib/rbac-admin/imports
WORKDIR /app
COPY --from=build /source/backend/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
COPY deploy/docker/backend-entrypoint.sh /app/entrypoint.sh
RUN chmod 0555 /app/entrypoint.sh && chmod 0444 /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["server"]
