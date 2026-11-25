# Logback 日志查看操作指南

助教第一阶段现场检查使用。两套 RBAC1 后端均使用 Logback 配合 AOP 分层切面（`DEBUG` 级别），同时向**容器控制台**和**宿主机滚动日志文件**输出分层调用记录。

前端当前连接哪套后端，切换菜单或调用接口时就会在哪套后端产生日志；任选以下一种方式即可实时查看。

> 现场如需切换后端实现，先在 `Course-Manager` 执行 `/root/rbac/deploy/switch-backend.sh ddd`（或 `structured`，详见 [Swarm 部署与后端切换](../../deploy/README.md)），再按对应服务查看实时日志。

## 方法一：在 Swarm 管理节点统一查看（无需切换服务器）

SSH 登录管理节点 `Course-Manager`，按当前启用的后端服务名实时跟踪输出（`--since 2m` 仅展示最近 2 分钟及后续新增日志，过滤历史旧容器记录）：

```bash
# 当前使用结构化后端时
docker service logs -f --since 2m rbac_structured

# 切换到面向对象（DDD）后端后
docker service logs -f --since 2m rbac_ddd
```

## 方法二：在对应工作节点查看宿主机 `.log` 文件

容器内的 `/app/logs` 已挂载至对应工作节点的宿主机目录（按天滚动归档，保留 7 天）：

| 当前启用后端 | 所在节点 | 宿主机实时查看命令 |
| --- | --- | --- |
| 结构化后端（`rbac_structured`） | `Course-Node2` | `tail -f /root/rbac/logs/structured/rbac1-structured.log` |
| 面向对象后端（`rbac_ddd`） | `Course-Node3` | `tail -f /root/rbac/logs/ddd/rbac1-ddd.log` |

## 日志输出对照说明

| 后端实现 | 切面位置 | 日志分层标识 | 类名包路径前缀 |
| --- | --- | --- | --- |
| 结构化（`rbac1-structured`） | `config/LayerLoggingAspect` | `[接口层]`、`[业务层]`、`[数据访问层]` | `com.rbac.rbac1` |
| 面向对象（`rbac1-ddd`） | `infrastructure/logging/LayerLoggingAspect` | `[接口层]`、`[应用层]`、`[基础设施层]` | `com.rbac.rbac1ddd` |
