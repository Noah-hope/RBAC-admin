# BE-08：当前单项任命、范围读模型与覆盖策略

## 当前准备状态

2026-10-19，D-066。A 要求拉取远程、同步本地并准备开发。已将本地 main 从 `5966834` 快进到远程 `db1be16cd04f527cca990a0b0d40b463afc1f12e`，无冲突，并建立 `codex/be-08-management-scope` 分支。本轮仅准备设计与接入，不修改业务代码、不执行 Java 或数据库测试；任务仍为 PLANNED，不能据此标记已实现或已验收。

前置 BE-06 已确认并交付；BE-10 来源 `6bfae88` 已合入 main，内部事实端口和六个 HTTP 查询可供接入。BE-10 的负责人人工验收仍开放，不代替 D 确认。其真实验证证据见 [BE-10 交接](BE-10-PEOPLE.md)。

## 需求与接口范围

沿用 UC-07、UC-09、UC-11、[管理范围规则](../01-requirements/usecases-v1/管理范围与人员权限规则.md)、[API 公共约定](../03-api/CONVENTIONS.md)及 [OpenAPI](../03-api/openapi.yaml)，实现四个既有读取操作：

| 操作 | GET 路径（统一前缀 `/api/v1/admin`） |
| --- | --- |
| myAppointments | `/my-appointments` |
| listScopes | `/management-scopes` |
| getScope | `/management-scopes/{scopeId}` |
| listScopeAppointments | `/management-scopes/{scopeId}/appointments` |

范围创建、名单和范围替换分别留给 BE-09、BE-27；不新增公开写接口，不改 JWT、数据库迁移或业务权限模型。范围及任命管理接口按 UC-07 的总管理员职责实现；我的任命接口返回当前账号有效任命，不要求先选择任命。

## 实现安排

- `web/scope/controller`、`web/scope/dto`：四个 HTTP 查询及安全响应；不返回持久化实体。
- `scope/service`：任命和范围读取、统一当前上下文解析、`ScopeCoveragePolicy` 单项覆盖判断；供 B/C/D 后续调用。
- `scope/repository` 与实体映射：拟以 Spring Data JPA 的只读实体/DTO 投影实现本任务常规读取，落实既定技术栈；特殊 SQL 仅在实际需要时采用。不在本轮顺带重写已交付的身份或人员仓储。
- 复用 `CurrentIdentityReader.admin(token, false)` 复核真实账号和会话；使用 D 的 `OrganizationReader` 获取员工、公司/部门及准确部门全部有效任职事实，不调用人员命令服务。
- 对外读取在 `ReadBoundary` 的同一主库 RR 快照内完成身份、任命、数据和 total 查询；供写操作使用的上下文解析必须在调用方 `CommandBoundary` 锁 guard 后的 RC 事务内重新执行，不另开事务、不缓存授权事实。JPA 和既有 JDBC 端口必须使用同一 DataSource/事务。

## 必须验证的边界

1. 权限管理员选择一项 ACTIVE 且属于本人的任命；同时检查账号/员工当前资格和指定公司、指定部门有效任职。其他任命不得补足本次缺失的系统或部门覆盖。
2. 范围请求解析 `X-Admin-Appointment-Id`、`X-Admin-Appointment-Version`、`X-Admin-Scope-Version`。缺失或非法头为 422；上下文已变为 409 CONTEXT_CHANGED；总管理员全局请求省略三头。错误顺序与既有身份保护及 API 约定一致。
3. 任命版本和范围版本分别读取、分别比较；不能用实体技术 `row_version` 替代任何业务版本。只读不推进版本、不恢复历史任命。
4. 覆盖必须同时满足指定公司、指定部门及全部目标系统，不能合并任命，也不因公司相同扩大到其他部门。不引入业务数据行权限。
5. 列表使用可见条件后分页和计数；详情越权与不存在按契约统一 404。分页默认 1/20，每页仅 10/20/50/100；显式非法或空参数不能悄悄忽略。任命历史过滤仅接受契约状态。
6. 我的任命分页返回当前公司、部门、系统集合和独立版本，排除撤销、失效和不具备当前任职资格的任命；同部门兼岗按全部事实判断，不能只取第一项。

## 后续 TDD 顺序

先按项目 TDD skill 编写并运行失败测试，再实现最小行为：

1. 覆盖策略：完整单项通过、跨任命拼接拒绝、跨公司/部门拒绝、空/部分系统集合边界。
2. 真实数据库中的当前任命解析：本人/他人任命、ACTIVE/历史状态、双版本独立变化、兼岗与任职结束、账号停用及受限会话。
3. 四个 HTTP 读取操作：认证、角色边界、分页/过滤/排序、安全字段和 no-store；计数与数据使用同一快照。
4. 集成 D 端口及 JPA/JDBC 同事务：事务外调用拒绝，新的请求看见已提交变更，旧上下文拒绝；随后完整构建和既有 MySQL 回归。

本轮没有运行上述测试，不宣称 RED/GREEN。真实数据库验证仅使用新建专属临时 MySQL 8.4、非 3306 端口及已核验 datadir；不操作成员现有 dev/test 库。完成后记录实际结果、清理临时资源，再由 A 确认本任务；本轮不推送、不创建 PR、不合并任务分支。
