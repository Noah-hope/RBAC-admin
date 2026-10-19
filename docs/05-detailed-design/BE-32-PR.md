# BE-32：独立管理员初始化、部署配置与本机验证指南

本文已用于[PR #1](https://github.com/Noah-hope/RBAC-admin/pull/1)正文，2026-10-19已合并至main/609ee4b。以下为该PR的交付说明；不是待发布草案。

后端已有登录能力，但尚无受控的初始总管理员创建入口。本次提供同一 jar 中显式执行的 BootstrapAdminApplication，创建后要求首次改密；普通应用启动不会创建或重置管理员。

## 变更

- 初始化要求专用环境变量和目标库确认，使用既有密码哈希、guard 及同事务部署审计；已有总管理员或占用的登录名拒绝覆盖。无需额外 RSA/HMAC 密钥，无新增公开 API 或迁移。
- server profile 校验身份、幂等开关和外部持久目录，提供运行/迁移/初始化分离的环境与 systemd、HTTPS 代理示例；同一业务代码/jar 用于开发机和服务器。
- 补齐 IDEA 初始化、Dev/Test 独立运行配置、Apifox 鉴权/可选参数排查、发布校验和及回退说明，同步共享记忆和任务追踪。

## 验证

- 已观察初始化入口与 server 配置的 RED/GREEN；完整 clean verify/mysql-check：80 离线＋88 真实隔离 MySQL，全无失败、错误或跳过。
- 覆盖双进程竞争、重复初始化拒绝、审计失败回滚、迁移/guard 缺失、配置错误、首次改密与账号列表访问。
- 两次独立干净构建的 jar SHA256 一致；专属 MySQL 随机 schema/账号清理归零、进程关闭，未操作已有开发/测试库。
- A 已在本机验证首次登录、改密后 NORMAL 会话及账号列表，并于2026-10-19明确确认 BE-32 通过（D-062）。完整证据见 BE-32-DEPLOYMENT.md。

## 组员使用

合入后更新 main，按 deploy/README.md 配置自己的数据库及一次性初始化入口。正常运行仍用 rbac_app，独立迁移用 rbac_migrator；密码/token/密钥和个人 IDEA 配置不随代码共享。已有管理员时跳过初始化。

本次提供通用 Linux 模板；尚未部署真实服务器，systemd/nginx/HTTPS、重启及备份恢复由 BE-34 验证。BE-08 仍等待 D 的 BE-10，其他业务依赖不变。本任务按 D-051 由 A 自检并确认，不设置独立评审人。
