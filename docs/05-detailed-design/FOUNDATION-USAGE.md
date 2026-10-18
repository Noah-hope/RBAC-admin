# 团队如何使用公共基座

适用范围：BE-01～05 的公共能力。后续业务从交付后的共同主线开始；当前交接状态见[共享记忆](../project/MEMORY.md)，完整环境操作见[后端运行说明](../../backend/README.md)。

## 先在自己的电脑运行

1. 在 IDEA 打开 `backend/pom.xml`，项目、Maven 导入/运行和应用 JDK 均选 21，Maven 使用项目 Wrapper（固定 3.9.11）。
2. 准备自己负责的本机数据库，分别建立普通运行和独立迁移配置。迁移主类为 `com.adgn.backend.migration.MigrationApplication`，设置 `RBAC_MIGRATION_URL/USER/PASSWORD`；按目标库执行迁移后退出。普通启动主类为 `com.adgn.backend.BackendApplication`，设置 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`，使用 DML 运行账号。
3. 启用幂等时，另配 `RBAC_IDEMPOTENCY_ENABLED=true`、`RBAC_IDEMPOTENCY_CURRENT_KEY_ID`、`RBAC_IDEMPOTENCY_KEYS_JSON`。密钥生成、格式及轮换按后端运行说明；密码和密钥只放本机配置，不提交运行配置中的秘密。
4. 构建并启动，访问 `/actuator/health`，确认 `status=UP`。这只说明服务健康，业务行为仍须测试。

每人使用自己的数据库时可生成自己的幂等密钥；多个应用连接同一数据库时，必须使用一致的密钥环。开发库、测试库由连接 URL 决定，与 IDEA 配置名称无关。迁移账号负责结构变更，普通运行账号负责读写数据；本机 binlog 的触发器限制按运行说明受控处理，普通应用不改用 root。

## 写业务时复用哪些能力

| 需要解决的事 | 使用入口 | 业务模块还要提供什么 |
| --- | --- | --- |
| HTTP 请求和错误 | 严格 JSON、DTO 校验、Problem、请求 ID | 对应 API 的 DTO、约束和业务错误，不另造公共错误结构 |
| 普通写操作 | `CommandBoundary.execute(qualify, work)` | 锁后读取的当前资格；业务写入和审计事实 |
| 契约要求幂等的写操作 | `IdempotencyCoordinator.execute(request, qualify, authorize, work)`，结果由 `IdempotencyHttp` 适配 | 规范请求字节、当前资格、重放授权回调及安全结果 |
| 权限等一致性读取 | `ReadBoundary.read(qualify, project)` | 同一快照内的身份读取及 DTO 投影 |
| 审计追加 | `AuditAppender.append(context, record)` | 当前操作者及安全事件内容；与业务同事务提交 |
| 基础持久化映射 | `core/persistence` | 各域实体和 Repository；按字段使用 UUID、UTC 和存储版本约定 |

业务应用服务负责选择外层边界。写边界先锁 `mutation_guard`，再检查资格；内部写端口接收 `WriteContext` 并检查有效性，复用同一事务，不逐条再开启边界或 `REQUIRES_NEW`。读边界中完成投影，返回 DTO，避免事务外加载实体。OA 网络调用、文件解析等耗时操作放在持锁事务外。

例如“登记系统”应用服务负责校验当前管理员、构造写入和审计，再交给幂等协调器完成预留、执行及结果保存；“系统列表”则通过读边界读取当前身份和列表。业务权限由真实资格读取实现，不能沿用测试夹具身份。

## 幂等接入时记住三点

1. 调用方提供规范字节，包含目标、预期版本及业务查询参数，排除 JWT、请求 ID、任命上下文头。当前只有规范化接口；固定申请的 RFC8785 实现归 BE-21。
2. 成功结果只保存可安全重放的状态、正文和 Location。每次重放重新检查当前资格与保存的授权事实；不重查原请求已经推进的业务版本，也不依赖已删除的目标行。
3. 同键异内容返回 `409 IDEMPOTENCY_CONFLICT`；有效租约返回 `409 REQUEST_IN_PROGRESS` 和 `Retry-After`。默认不装配幂等能力；需要幂等的模块必须显式启用并依赖协调器，不增加关闭时直接执行的备用路径。

## 每人如何配合开发

1. 基座交付共同主线后，领取实际分派的 BE 任务，登记唯一负责人，从最新主线建立 `codex/be-xx-主题` 分支，先读对应用例、API、数据库设计及模块说明。
2. 业务代码按领域放入 `com.adgn.backend` 下的模块包；公共边界需要变化时先协调使用方，减少多人同时改公共文件。
3. 按[项目 TDD 技能](../../.agents/skills/test-driven-development/SKILL.md)先运行失败测试，再最小实现。迁移新增后续版本，不能改已经执行的 V1～V3；结构变化同步权威设计并核验。
4. 在 `backend` 运行 `./mvnw.cmd clean verify`。事务、约束和并发测试使用专属临时 MySQL，再运行显式 `mysql-check`；不要将自动化检查指向个人开发库或共享测试库。
5. 提交业务代码、测试和受影响文档，PR 写清 RED/GREEN、真实数据库结果及限制；由负责人核对测试和自检证据、明确确认完成后集成。任务与状态统一更新 `docs/project/` 和任务清单。

当前已有 64 项离线、42 项真实 MySQL 检查的通过证据，未包括后续新增业务。JWT/会话、管理员范围、草稿/审批/执行及演示 OA 仍由各业务任务实现。接口细节见[事务接入说明](BE-04-TRANSACTIONS.md)和[幂等接入说明](BE-05-IDEMPOTENCY.md)。
