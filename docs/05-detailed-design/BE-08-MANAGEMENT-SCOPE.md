# BE-08：当前单项任命、范围读模型与覆盖策略

## D-068 负责人确认

2026-10-19，A明确“验证通过，接下来做什么”，接受实现提交 `1c0eb3c055fe267fd0ff69c37e5120de3951eca1`。任务为 OWNER_ACCEPTED，待共同 main 交付。沿用本文件85离线＋136真实MySQL及原自检证据；本轮未新增Java/数据库运行，不推断本人逐项重跑全部场景。下方待A确认是实施阶段历史。

已准备 [PR正文](BE-08-PR.md)，尚未推送、创建PR或合并。远程main核对仍为db1be16；先交付本任务供B/BE-17等使用，A/BE-09再等BE-17交付。本轮不开始BE-09或代替其他负责人确认。

## D-067 实施范围

2026-10-19，A 明确“开始开发BE-08”。在 `codex/be-08-management-scope` 实现四个读取接口、`ManagementContextResolver` 和纯 `ScopeCoveragePolicy`。采用 Spring Data JPA 的只读实体及仓储；范围列表使用 JPQL，跨账号/任职的任命读取通过 JPA 原生 SQL 投影完成，不复制其他领域的 JPA 实体。仅暴露需要的读取方法，不提供仓储写入口，DTO 不序列化实体。

`people.PeopleQueries` 已接入统一 resolver，保留 D 原有人员可见性规则及同事务要求，删除重复任命资格 SQL。身份、人员写端口、已有迁移不变；BE-09 创建及 BE-27 持久失效仍未实现。BE-08 为 IN_PROGRESS（已实现待 A 确认），尚未合并或推送。

## D-066 准备记录（历史）

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

## 模块及事务约定

- `web/scope/controller`、`web/scope/dto`：四个 HTTP 查询及安全响应；不返回持久化实体。
- `scope/service`：任命和范围读取、统一当前上下文解析、`ScopeCoveragePolicy` 单项覆盖判断；供 B/C/D 后续调用。
- `scope/repository` 与实体映射：Spring Data JPA 的只读实体/DTO 投影；跨域事实使用原生查询投影，实体不关联其他领域实体。不顺带重写已交付的身份或人员仓储。
- 复用 `CurrentIdentityReader.admin(token, false)` 复核真实账号和会话；使用 D 的 `OrganizationReader` 获取员工、公司/部门及准确部门全部有效任职事实，不调用人员命令服务。
- 对外读取在 `ReadBoundary` 的同一主库 RR 快照内完成身份、任命、数据和 total 查询；供写操作使用的上下文解析必须在调用方 `CommandBoundary` 锁 guard 后的 RC 事务内重新执行，不另开事务、不缓存授权事实。JPA 和既有 JDBC 端口必须使用同一 DataSource/事务。

## 必须验证的边界

1. 权限管理员选择一项 ACTIVE 且属于本人的任命；同时检查账号/员工当前资格和指定公司、指定部门有效任职。其他任命不得补足本次缺失的系统或部门覆盖。
2. 范围请求解析 `X-Admin-Appointment-Id`、`X-Admin-Appointment-Version`、`X-Admin-Scope-Version`。缺失或非法头为 422；上下文已变为 409 CONTEXT_CHANGED；总管理员全局请求省略三头。错误顺序与既有身份保护及 API 约定一致。
3. 任命版本和范围版本分别读取、分别比较；不能用实体技术 `row_version` 替代任何业务版本。只读不推进版本、不恢复历史任命。
4. 覆盖必须同时满足指定公司、指定部门及全部目标系统，不能合并任命，也不因公司相同扩大到其他部门。不引入业务数据行权限。
5. 列表使用可见条件后分页和计数；详情越权与不存在按契约统一 404。分页默认 1/20，每页仅 10/20/50/100；显式非法或空参数不能悄悄忽略。任命历史过滤仅接受契约状态。
6. 我的任命分页返回当前公司、部门、系统集合和独立版本，排除撤销、失效和不具备当前任职资格的任命；同部门兼岗按全部事实判断，不能只取第一项。

## TDD 与验证记录

先按项目 TDD skill 编写并运行失败测试，再实现最小行为：

1. 覆盖策略：完整单项通过、跨任命拼接拒绝、跨公司/部门拒绝、空/部分系统集合边界。
2. 真实数据库中的当前任命解析：本人/他人任命、ACTIVE/历史状态、双版本独立变化、兼岗与任职结束、账号停用及受限会话。
3. 四个 HTTP 读取操作：认证、角色边界、分页/过滤/排序、安全字段和 no-store；计数与数据使用同一快照。
4. 集成 D 端口及 JPA/JDBC 同事务：事务外调用拒绝，新的请求看见已提交变更，旧上下文拒绝；随后完整构建和既有 MySQL 回归。

实际 RED：四个读取接口的 6 个 HTTP/MySQL 测试全部因路由未注册返回 404 而断言失败，0 错误；纯覆盖策略 4 项中 3 项断言失败、0 错误；统一 resolver 占位时 11 项引擎测试中 5 项上下文断言失败、0 错误，其余 6 项既有新接口测试通过。修正过一次测试 ActorKind 参数编译笔误，不将编译错误计为 RED。新增双连接快照、JPA 已加载实体后 JDBC 更新及重复头场景为上述 RED 后的回归补充，不宣称每项都单独观察过 RED。

阶段 GREEN：首组四接口 6 项通过；统一 resolver、纯策略与 D 人员查询的 23 项引擎回归通过。

首次完整回归：85 离线通过，BE-08 的 14 项引擎检查（含双连接 RR 快照和同事务 JDBC 更新后 JPA 投影复核）通过；JpaMappingMySqlCheck、TransactionBoundaryMySqlCheck 两组旧夹具因只扫描测试实体而启动失败，故该次完整构建失败，不记为全绿。修正 PersistenceSamples.MappingConfiguration 为同时扫描正式后端实体及测试实体，保留原有业务断言，随后重跑完整构建。失败日志为本机忽略目录中的 `be08-full-before-fixture-fix.log`。

最终完整 `./mvnw.cmd -Pmysql-check clean verify` 于 2026-10-19 21:47:56（Asia/Shanghai）BUILD SUCCESS，用时 6分58秒：**85 项离线＋136 项真实 MySQL 检查，失败/错误/跳过均为 0**。包括既有 81＋122、4 项新纯策略及 14 项新范围/上下文引擎检查；旧 5 项 JPA 映射和 16 项事务检查均恢复通过，不排除失败用例。并发以双线程/连接和 CountDownLatch 明确提交顺序，无 sleep 猜测。

最终日志 `backend/.tmp/be08-final-complete-verify.log`，JUnit 同次完整报告已归档至 `backend/.tmp/be08-full-reports/`。正式 jar 包含 ScopeController、ScopeRepository 和 ManagementContextResolver，检查的测试夹具/本机密钥目录未进入 jar；该次 jar SHA256 为 `79b327e9eff4840b8642120ba8e3b3bd17ef4d5b1fe2a550a7a9676dc5b769e0`，不据此声称已做第二次重复构建。

收尾核验本任务专属非 3306 MySQL 的 PID、命令行和实际 datadir 一致，自建 schema/账号均为 0；mysqladmin 正常 shutdown 后确认进程已退出，临时 be08 密钥目录为 0。原 MySQL84 服务仍 Running；用户现有项目库、IDE 密钥及运行配置未操作。Flyway 兼容及 Hibernate 既有提示保留，不声称零警告。静态追踪/生成器一致性及 Git 空白校验通过；AT-08/19 仅本任务读取、当前上下文和覆盖部分，不含创建任命、范围替换或申请持久失效。

所有引擎测试使用本任务新建 MySQL 8.4.8、非 3306 端口及 `backend/.tmp` 独立 datadir，写入前验证实际实例身份；不连接或操作成员现有 dev/test 库。不部署服务器、不推送、不创建 PR、不合并任务分支。

## B/C/D 如何接入

在自己的 ReadBoundary 或锁 guard 后的 CommandBoundary 内，调用 `ManagementContextResolver.resolve(jwt, selection)`；selection 包含三上下文头。返回当前账号、一项任命、独立双版本及完整公司/部门/系统事实，不能缓存到下次请求或接受客户端 JSON 直接构造的 ManagementContext。缺头/非法值 422、他人任命或无指定部门任职 403、任命失效或版本陈旧 409；身份失效先按既有认证返回 401。

随后以 `ScopeCoveragePolicy.covers(context, companyId, departmentId, systemIds)` 校验该次操作的完整目标；目标标识及目标事实由调用方验证和读取。空系统目标仅表示组织层面的覆盖，不代表任意系统权限；空系统范围不能覆盖任何具体系统。公司级共享角色维护等全局专属操作仍显式要求 context.global，不能仅靠普通覆盖检查放行。

多目标操作必须在同一已选 context 下逐项覆盖全部目标，不能每个目标换用一项任命；任意一项不满足就整体拒绝。覆盖策略本身不读取数据库、不取代身份与业务状态校验，也不扩展为外部系统的业务数据权限。

总管理员省略三头得到全局上下文；显式提供三头则按同一项任命资格检查，不能借用别人的任命。四个管理查询路由本身不新增上下文头；权限管理员通过我的任命接口取得可供范围请求使用的最新版本。D 的人员查询现已通过同一 resolver 解析三头，原控制器继续拒绝重复头。

## A 本机验证步骤

1. IDEA 重新加载 Maven，重启正常 BackendApplication；沿用已有 RSA、身份开关及数据库运行账号配置。无需生成新密钥、重建表或改用迁移账号。普通应用与初始化账号须使用同一目标库；可继续使用独立 Dev/Test 运行配置。
2. Apifox 用已完成首次改密的总管理员登录，登录 Auth 设为无认证。把新 accessToken 配置到后续请求的 Bearer Token；仅在本机环境保存，不提交共享示例。
3. GET `/api/v1/admin/my-appointments?page=1&pageSize=20` 应为 200、分页结构和 no-store。未给总管理员任命时 items 为空属于正常情况。
4. GET `/api/v1/admin/management-scopes?page=1&pageSize=20` 应为 200。无管理范围时 items 为空；BE-09 尚未实现，不能通过本任务创建范围。
5. 如果目标库已有合法范围，使用返回的 id 读取 `/management-scopes/{scopeId}` 和 `/management-scopes/{scopeId}/appointments`，均应为 200；不存在的 id 返回 404。没有范围时，这两项成功路径及权限管理员场景由专属隔离夹具验证，不要求向本机库手工插入测试账号/任命。
6. 可补验 pageSize=3 为 422、未带 token 为 401。禁用空值的可选 X-Request-Id 和未使用的 companyId/departmentId/status，或填写合法值；不要发送空参数。四个查询为 GET，不带 Body。

A 核对代码、自检证据和适用的本机结果后再确认 BE-08；健康 UP 或空列表 200 只证明相应路径，不代替全部权限、上下文和事务行为验证。完成确认后的主线交付另行安排。
