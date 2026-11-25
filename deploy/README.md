# Docker Swarm 部署与后端切换

使用 `stack.yml` 统一部署 RBAC1 的 MySQL、结构化后端、面向对象（DDD）后端与管理前端。两套后端共用同一个 `rbac1` 数据库、同一对 JWT 密钥与 `sys_session` 会话表，在独立节点上同时常驻运行。

## 服务与节点分工

| 服务名 | 调度约束（节点） | 端口与网络 | 宿主机挂载目录 |
| --- | --- | --- | --- |
| `rbac_mysql` | `rbac.role=database`（`Course-Node1`） | 内部 `3306`（`rbac-net`） | `/root/rbac/mysql/data` → `/var/lib/mysql` |
| `rbac_structured` | `rbac.role=structured`（`Course-Node2`） | 内部 `8080`（`rbac-net`） | `/root/rbac/logs/structured` → `/app/logs` |
| `rbac_ddd` | `rbac.role=ddd`（`Course-Node3`） | 内部 `8080`（`rbac-net`） | `/root/rbac/logs/ddd` → `/app/logs` |
| `rbac_frontend` | `rbac.role=frontend`（`Course-Node4`） | 公网 `8088 -> 80`（`rbac-net`） | — |

## 发布与更新 Stack

在管理节点 `Course-Manager`（`/root/rbac` 目录）执行：

```bash
docker stack config -c deploy/stack.yml > /dev/null
docker stack deploy --with-registry-auth -c deploy/stack.yml rbac
```

## 结构化与面向对象（DDD）后端一键切换

在 `Course-Manager` 执行切换脚本（已在 `/root/rbac/switch.sh` 建立快捷软链接）：

```bash
# 查看当前生效后端与服务副本状态
/root/rbac/deploy/switch-backend.sh status

# 切换到面向对象（DDD）后端
/root/rbac/deploy/switch-backend.sh ddd

# 切回结构化后端
/root/rbac/deploy/switch-backend.sh structured
```

两套后端常驻热备且共享会话，切换前端网关上游 `RBAC_API_UPSTREAM` 无需浏览器重新登录；如需连同课程系统后端内部鉴权地址（`APP_IDENTITY_BASE_URL`）一并切换，在命令末尾追加 `--with-course`。切换后实时查看各层日志见 [Logback 日志查看操作指南](../docs/04-development/Logback日志查看指南.md)。
