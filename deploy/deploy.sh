#!/usr/bin/env bash
# 在 Swarm Manager 执行。配置文件是受信任的 Bash 赋值文件，不包含密码。
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
check_only=false
database=false
while [[ "${1:-}" == --* ]]; do
    case "$1" in
        --check) check_only=true ;;
        --database) database=true ;;
        *) printf '用法：bash deploy/deploy.sh [--check] [--database] [配置文件]\n' >&2; exit 1 ;;
    esac
    shift
done
env_file=${1:-"$script_dir/.env"}

set -a
source "$env_file"
STACK_NAME=${STACK_NAME:-rbac}
if [[ "$database" == true ]]; then
    STACK_NAME=${DATABASE_STACK_NAME:-rbac-db}
    compose_file="$script_dir/mysql-stack.yml"
else
    compose_file="$script_dir/stack.yml"
    FRONTEND_BACKEND=${FRONTEND_BACKEND:-structured}
    case "$FRONTEND_BACKEND" in
        structured|ddd) RBAC_API_UPSTREAM="${STACK_NAME}_${FRONTEND_BACKEND}:8080" ;;
        *) printf 'FRONTEND_BACKEND 必须是 structured 或 ddd。\n' >&2; exit 1 ;;
    esac
    if [[ "$FRONTEND_BACKEND" == ddd && "${DDD_REPLICAS:-0}" == 0 ]]; then
        printf '选择 DDD 前端目标前，须将 DDD_REPLICAS 设为 1。\n' >&2
        exit 1
    fi
fi
set +a

# Docker CLI 校验必填变量和 Stack 格式；配置未通过时不会执行部署。
docker stack config --compose-file "$compose_file" > /dev/null
if [[ "$check_only" == true ]]; then
    printf 'Stack 配置检查通过：%s。未执行部署。\n' "$compose_file"
    exit 0
fi

docker stack deploy --with-registry-auth --compose-file "$compose_file" "$STACK_NAME"
printf '部署请求已提交；使用 docker stack services %s 检查服务状态。\n' "$STACK_NAME"
