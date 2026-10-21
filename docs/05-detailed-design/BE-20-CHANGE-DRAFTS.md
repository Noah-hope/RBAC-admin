# BE-20 角色创建与变更草稿

D-088（2026-10-21）：C在BE-20负责人确认与逐项合并建议后明确“可以”，接受既有137离线＋265真实MySQL/HTTP验证并授权交付。来源310313a07e0321f58a5b1b03d3d59231fe2a17a6已保留历史合入远程main/a9b214cce38d4396120b3902ce19f2b7440bf4f1，合并树等于验证来源；main和中文任务分支已原子推送，远程完整SHA回查一致。BE-20为OWNER_ACCEPTED、mergedToMain=true，人工演示未进行；本轮代码未变，仅复核报告/jar摘要与静态追踪，无新Java/SQL或部署。D/BE-26技术依赖已交付，其他C任务不自动开展，既有其他负责人验收保持。详细证据见docs/05-detailed-design/BE-20-CHANGE-DRAFTS.md。

## 范围与依据

2026-10-20：本轮用户明确负责 C，并要求先列计划再执行。基于 main/86089d2，当前任务分支 BE-20-角色与变更草稿。依据 UC-02/03/04、OpenAPI 1.2.0 的 createRole/createChangeDraft/updateChangeDraft/deleteChangeDraft、数据库 T0/T2/T3/T11、AT-11/13/17（AT-04/16 仅本任务局部）。不实施提交/OA/执行器及审计查询，不代记负责人验收或主线交付。

## 结构与调用

HTTP controller/dto → ChangeDraftService → DraftRepository/ClaimService；角色插入通过 role 的受控 RoleDraftWriter，来源与永久关系锚点通过 D 已交付 BaselineWriter，事实读取使用 RoleRepository/GrantReader。沿用 ManagementAccess、主库 ReadBoundary、IdempotencyCoordinator 和 WriteContext 及 AuditAppender，不新增依赖/表/迁移。

动态 content 使用严格显式解析：按 kind 拒绝未知/缺失/错误类型字段、重复业务键、非法版本及集合；规范集合顺序用于幂等摘要，明确配对顺序保留。该摘要不是 BE-21 的 RFC8785 固定申请摘要。

## 行为与事务

- 新角色由服务端生成稳定 ROLE_ 编码，固定归属；同 guard 事务写 UNCONFIGURED/配置版本0、初始空配置草稿、目标投影、角色占用与审计，不产生有效权限。
- 三种草稿都先校验完整当前单项管理范围，再校验当前角色/来源/历史事实。授权来源与角色所属部门分别校验；共享角色配置仅总管理员，来源操作可使用范围内共享角色。
- 授予保存完整当前任职依据；同源 ACTIVE 与准确 grant 已结束为无变化，不占用、不重比无意义旧版本。仍需项检查当前版本/资格。未物化来源和 baseline 通过已有端口在同一事务物化，不授予角色。
- 草稿投影包含所有明细，包括无变化项。占用以角色或永久 baseline 为唯一键，冲突仅返回安全通用码，不输出隐藏申请ID。
- 编辑只允许 DRAFT，kind/company/配置角色不可变。先授权旧整份目标和新整份目标，再比 draftVersion；原子替换明细与占用，任一冲突回滚原稿与原占用。相同规范内容和原因不推进版本。
- 删除只允许 DRAFT；保存删除事件的完整安全快照，按 claim→target→request 顺序删除；仅从未提交/生效且无其他引用的新角色可清理。审计不使用已删除申请的FK。幂等授权依据与204同事务保存，删除后重放复核当前身份/完整覆盖，不依赖已删除行。
- copiedFromRequestId 只接受当前整份可见 REJECTED 原申请，产生新ID并重校当前事实，不修改原记录。
- 单部门/系统安全事件按 SCOPED；不能安全分割的混合事件为 GLOBAL，完整目标作为审计主体保留。

## 验证计划

真实 HTTP/MySQL RED：先覆盖新角色原子创建/无有效授权、草稿编辑/删除与删除后重放；观察缺路由的业务断言失败后实现。补授权边界、完整任职、占用竞争/编辑回滚、版本、输入/批量限制、无变化、历史保护和审计故障注入。离线测试覆盖纯输入规范化边界。只使用 backend/.tmp 专属非3306 MySQL 8.4，随机库与运行账号自行清理，最后关闭专属进程。完整执行 Maven verify/mysql-check 和设计追踪校验，实际结果完成后补充。

## C → D 的内部状态交接

按架构共用接口及 BE-26 对 BE-20 的直接依赖，随本任务提供最小 WorkflowStateWriter：仅在已有 WriteContext 中读取状态，并对 PENDING/APPROVED/EXECUTION_FAILED 的已提交申请整份转 INVALIDATED、推进状态版本、释放整份占用并停止 READY/LEASED outbox。DRAFT 和终态保持原状；CAS陈旧或版本耗尽拒绝并回滚。D 的 RequestInvalidator 仍负责选择受影响申请、确定实际失效原因、可见统计及外层审计，不在此实现来源关闭或公开失效入口。审批/成功/失败恢复等转换留相应 C 任务，不先实现一套通用任意状态修改API。

## HTTP 与内部端口交接

| 操作 | 地址 | 成功行为 |
| --- | --- | --- |
| createRole | POST /api/v1/admin/roles | 201，role/draftId/draftVersion 与角色 Location；角色仍未生效 |
| createChangeDraft | POST /api/v1/admin/change-requests | 201，完整 DRAFT 模型与申请 Location |
| updateChangeDraft | PUT /api/v1/admin/change-requests/{requestId} | 200，仅草稿；同内容/原因不推进版本 |
| deleteChangeDraft | DELETE /api/v1/admin/change-requests/{requestId}?expectedDraftVersion=… | 204，无正文；删除后同键可重放 |

四操作均复用后台 JWT、Idempotency-Key、三上下文头和 no-store 响应；总管理员按现有规则不传任命头，权限管理员必须选择当前单项任命及双版本。完整请求形状见权威 OpenAPI，不新增草稿列表/详情入口（归 BE-22）。配置通道冲突 CHANNEL_OCCUPIED，关系占用 RELATION_OCCUPIED；不在错误正文披露持有者ID。陈旧版本 VERSION_CONFLICT，非法输入 VALIDATION_ERROR，非草稿 STATE_CONFLICT；详情目标隐藏统一 NOT_FOUND。

删除后重放的摘要只固定业务输入、主体、方法、路径与 key；当前任命头每次独立复核。允许同一账号换用另一项当前完整覆盖原目标的任命，失去任意目标覆盖则403，不能用缓存绕过。角色创建和草稿编辑/创建的安全结果也使用完整固定授权依据，重放不重新比较已推进的目标版本。

D 的后续编排可在已有 WriteContext 中调用：

```java
var state = workflowStateWriter.read(write, requestId);
boolean invalidated = workflowStateWriter.invalidate(
    write, requestId, state.statusVersion(), "SOURCE_EPOCH_CHANGED");
```

失效原因必须由调用方从CONVENTIONS中的公开失效码选择；直接关闭来源对应SOURCE_EPOCH_CHANGED，不把内部事件名SOURCE_CLOSED作为公开failureCode。

该方法不选取受影响申请、不自行开事务、不处理授权、审计或统计、不公开成 HTTP。返回 true 才表示本次完成整份状态转换；DRAFT/已终态返回 false，不伪造 submittedVersion。状态版本陈旧409，版本耗尽409；发生异常会标记调用方事务回滚，调用方即便捕获也不能提交部分状态。READY/LEASED outbox 改 STOPPED 并清空租约，已 DONE 的外部关联事实保留。审批结果和固定内容不变。外层来源/任职/授权变更、整份失效、审计及幂等结果仍必须原子提交；该端口不代表 BE-26 已实现或真实 OA 已接通。

持久化沿用现有 JDBC 端口和主库事务；事务连接参与方式核对 [Spring 官方说明](https://docs.spring.io/spring-framework/reference/data-access/jdbc/connections.html)。本任务没有修改公共事务、身份装配、pom、迁移或数据库设计。

## 本机验收步骤

1. 按现有 FOUNDATION-USAGE / BE-06 配置启动应用，用测试用后台账号登录；不向开发库写入本任务测试夹具。
2. 以实际公司/部门/系统ID调用 createRole，确认返回 UNCONFIGURED、configurationVersion=0、空 permissionIds 和初始 draftId；角色列表可见，但没有员工授权。
3. 用另一名同范围管理员对初始草稿调用 updateChangeDraft；检查版本推进。再用旧 draftVersion 编辑应409，跨范围应404/403，不能借共享角色获得维护权。
4. 在测试用实际任职上通过已有 grantBaselines 获取完整 employmentIds 和版本，保存一条明确授予草稿；确认没有实际授权，第二个不同 key 对同需变更关系保存应409。
5. 删除未提交草稿后重发相同 key，仍204且 Idempotent-Replayed=true；新请求不重复删除或追加审计。失去当前范围后重放应拒绝。
6. 已提交申请禁止编辑/删除；需要验证驳回复制时使用后续真实 BE-21/24 链路，不将本任务内的持久夹具当成外部审批完成。

## 实际验证与交付边界

2026-10-20，本任务在 macOS 使用 Corretto JDK21 与官方 MySQL8.4.8 ARM 便携包，专属127.0.0.1:23320、backend/.tmp/be20-mysql-data，禁用 binlog，测试夹具核对实际 datadir/version/binlog 后才建随机库与最小DML账号。没有连接已有开发/测试库，也没有安装系统数据库服务或改动用户的应用配置。

TDD 证据：

- be20-red.log：3项真实HTTP断言失败，0错误；POST roles尚未实现，405。
- be20-red-expanded.log：扩展9项全部断言失败，0错误；新角色405、草稿404，夹具/数据库正常。
- be20-channel-red.log：配置占用错误码与数据库不变量约定不符，期望CHANNEL_OCCUPIED，随后修正。
- be20-baseline-red.log：19项中1项断言失败；不同历史grantID指向同baseline原返回500，补业务键校验后422。
- be20-workflow-replay-red.log：22项中2项断言失败；新任命完整覆盖时删除重放原409、失效端口占位返回false。分别分离当前授权与业务摘要、实现受控状态写端口后GREEN。
- 首轮扩展夹具曾违反不可变角色归属和可靠OA关联约束；已修正为按最终归属创建角色、建立真实持久关联/结果事实，没有修改迁移或绕过触发器。夹具仅证明本地行为，不代表外部OA审批已接通。

最终命令：backend工作目录设置专属 RBAC_ISOLATED_MYSQL_PORT / RBAC_ISOLATED_MYSQL_DATADIR 与显式 JDK21 后运行 `sh ./mvnw -q clean verify -Pmysql-check`。完整 **137项离线/20组＋265项真实MySQL/HTTP/20组**，失败/错误/跳过均0。其中本任务 **25项** 覆盖原子新角色、三类草稿、完整任职、当前范围、共享角色维护、无变化、版本/输入/批量上限、不同历史ID同关系、两连接占用竞争、编辑冲突回滚、幂等删除/换任命重放、混合占用安全提示、已提交保护/驳回复制、审计故障回滚及内部整份失效/外层回滚。日志均位于忽略的 backend/.tmp，最终日志 be20-full-verify.log；Maven XML报告位于backend/target。

最终jar SHA-256：`adc7ed8684eadf9b6a4cfa7172b81e2a1f37acb3c9d5187fa8caae8f622b0059`。设计追踪校验 PASS。测试随机schema/账号回查0/0，BE20测试密钥目录0；专属PID6076已正常关闭，临时datadir、下载归档及便携二进制已移除。

BE-20登记IMPLEMENTED、ownerAccepted=false、mergedToMain=false；仅本地任务分支交付。负责人本机人工验收未进行，不自动等同OWNER_ACCEPTED。BE-19、BE-21～25、BE-31未在本轮实施；D/BE-26仍须本任务交付共同主线。没有合并/推送、PR或服务器部署。
