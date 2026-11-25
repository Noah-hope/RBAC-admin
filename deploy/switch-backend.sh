#!/usr/bin/env bash
# 一键切换 RBAC1 前端网关所连后端（structured / ddd）

set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]}"
if command -v readlink >/dev/null 2>&1; then
  RESOLVED_PATH="$(readlink -f "$SCRIPT_PATH" 2>/dev/null || printf '%s' "$SCRIPT_PATH")"
else
  RESOLVED_PATH="$SCRIPT_PATH"
fi
SCRIPT_DIR="$(cd "$(dirname "$RESOLVED_PATH")" && pwd)"
STACK_FILE="${SCRIPT_DIR}/stack.yml"
COURSE_STACK_FILE="/root/course/deploy/stack.local.yml"

usage() {
  cat <<'EOF'
用法：
  switch-backend.sh [status]
  switch-backend.sh structured [--with-course]
  switch-backend.sh ddd [--with-course]

说明：
  status        查看当前前端生效的后端及 rbac 栈服务状态（默认）
  structured    将 rbac_frontend 上游切换为结构化后端（rbac_structured:8080）
  ddd           将 rbac_frontend 上游切换为面向对象 DDD 后端（rbac_ddd:8080）
  --with-course 同步切换课程系统后端内部鉴权地址（APP_IDENTITY_BASE_URL）
EOF
}

ensure_manager() {
  if ! docker node ls >/dev/null 2>&1; then
    echo "错误：请在 Docker Swarm 管理节点（Course-Manager）上执行此脚本。" >&2
    exit 1
  fi
}

service_exists() {
  docker service inspect "$1" >/dev/null 2>&1
}

read_service_env() {
  local service_name="$1"
  local env_key="$2"
  docker service inspect "$service_name" \
    --format '{{range .Spec.TaskTemplate.ContainerSpec.Env}}{{println .}}{{end}}' \
    | grep -E "^${env_key}=" | head -n 1 | cut -d= -f2- || true
}

describe_upstream() {
  case "$1" in
    rbac_structured:8080|http://structured:8080) echo "结构化后端 (structured)" ;;
    rbac_ddd:8080|http://ddd:8080) echo "面向对象 DDD 后端 (ddd)" ;;
    "") echo "未知" ;;
    *) echo "$1" ;;
  esac
}

print_status() {
  local current_upstream course_identity
  current_upstream="$(read_service_env rbac_frontend RBAC_API_UPSTREAM)"
  echo "当前 RBAC 前端网关上游：${current_upstream:-未设置} -> $(describe_upstream "$current_upstream")"
  if service_exists course_course-mgt-backend; then
    course_identity="$(read_service_env course_course-mgt-backend APP_IDENTITY_BASE_URL)"
    echo "当前课程后端鉴权地址：${course_identity:-未设置} -> $(describe_upstream "$course_identity")"
  fi
  echo ""
  docker stack services rbac
}

update_yaml_env() {
  local file_path="$1"
  local env_key="$2"
  local env_value="$3"
  if [[ -f "$file_path" ]]; then
    sed -i -E "s|^([[:space:]]*${env_key}:[[:space:]]*).*$|\1${env_value}|" "$file_path"
  fi
}

print_log_hints() {
  local mode="$1"
  echo ""
  echo "实时查看分层 Logback 日志："
  if [[ "$mode" == "structured" ]]; then
    echo "  方法一（管理节点）：docker service logs -f --since 2m rbac_structured"
    echo "  方法二（Course-Node2）：tail -f /root/rbac/logs/structured/rbac1-structured.log"
  else
    echo "  方法一（管理节点）：docker service logs -f --since 2m rbac_ddd"
    echo "  方法二（Course-Node3）：tail -f /root/rbac/logs/ddd/rbac1-ddd.log"
  fi
}

switch_mode() {
  local mode="$1"
  local with_course="$2"
  local target_service="rbac_${mode}"
  local target_upstream="rbac_${mode}:8080"
  local course_identity_url="http://${mode}:8080"

  if ! service_exists "$target_service" || ! service_exists rbac_frontend; then
    echo "错误：未找到服务 $target_service 或 rbac_frontend，请先部署 rbac 栈。" >&2
    exit 1
  fi

  local replicas
  replicas="$(docker service inspect "$target_service" --format '{{.Spec.Mode.Replicated.Replicas}}')"
  if [[ "${replicas:-0}" -eq 0 ]]; then
    echo "检测到 $target_service 当前未启动，正在扩容为 1 副本..."
    docker service scale "${target_service}=1"
  fi

  update_yaml_env "$STACK_FILE" "RBAC_API_UPSTREAM" "$target_upstream"

  local current_upstream
  current_upstream="$(read_service_env rbac_frontend RBAC_API_UPSTREAM)"
  if [[ "$current_upstream" == "$target_upstream" ]]; then
    echo "rbac_frontend 已连接 ${target_upstream}，无需重启前端网关。"
  else
    echo "正在将 rbac_frontend 上游切换为 ${target_upstream}..."
    docker service update --env-add "RBAC_API_UPSTREAM=${target_upstream}" rbac_frontend
  fi

  if [[ "$with_course" == "true" ]]; then
    if service_exists course_course-mgt-backend; then
      update_yaml_env "$COURSE_STACK_FILE" "APP_IDENTITY_BASE_URL" "$course_identity_url"
      local current_course_url
      current_course_url="$(read_service_env course_course-mgt-backend APP_IDENTITY_BASE_URL)"
      if [[ "$current_course_url" == "$course_identity_url" ]]; then
        echo "course_course-mgt-backend 已连接 ${course_identity_url}，无需重启。"
      else
        echo "正在同步将课程后端鉴权地址切换为 ${course_identity_url}..."
        docker service update --env-add "APP_IDENTITY_BASE_URL=${course_identity_url}" course_course-mgt-backend
      fi
    else
      echo "提示：未检测到 course_course-mgt-backend 服务，跳过课程后端联动。"
    fi
  fi

  echo ""
  echo "切换完成！当前服务状态："
  print_status
  print_log_hints "$mode"
}

main() {
  local action="${1:-status}"
  if [[ "$action" == "-h" || "$action" == "--help" ]]; then
    usage
    exit 0
  fi

  ensure_manager

  case "$action" in
    status)
      if [[ $# -gt 1 ]]; then
        echo "错误：status 命令不接受额外参数：$2" >&2
        usage
        exit 1
      fi
      print_status
      ;;
    structured|ddd)
      shift || true
      local with_course="false"
      while [[ $# -gt 0 ]]; do
        case "$1" in
          --with-course) with_course="true" ;;
          *)
            echo "错误：未知参数 $1" >&2
            usage
            exit 1
            ;;
        esac
        shift
      done
      switch_mode "$action" "$with_course"
      ;;
    *)
      echo "错误：不支持的操作 $action" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
