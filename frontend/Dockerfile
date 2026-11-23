ARG NGINX_IMAGE=nginx:stable-alpine
FROM ${NGINX_IMAGE}

COPY dist/ /usr/share/nginx/html/
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

ENV RBAC_API_UPSTREAM=rbac_structured:8080
ENV NGINX_ENVSUBST_FILTER=^RBAC_API_UPSTREAM$
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
# 保留官方 entrypoint，启动时把环境变量填入 Nginx 模板。
CMD ["nginx", "-g", "daemon off;"]
