# Build context is the repository root. Build on node 3, not the runtime node.
FROM m.daocloud.io/docker.io/library/node:22-bookworm-slim AS build
WORKDIR /source/frontend
RUN node --version && npm --version
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM m.daocloud.io/docker.io/library/nginx:stable-alpine
RUN nginx -v && command -v wget
COPY --from=build /source/frontend/dist/ /usr/share/nginx/html/
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80 443
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8081/healthz || exit 1
