# BE-33 整体系统集成验收

## 本轮计划（D-103）

A 明确要求同步远程并开始 BE-33；本地主线已快进到 `0ff0ad703d4697c10f7e7cd5800961ef1404115a`，分支 `codex/be-33-system-acceptance`。按此前后期任务由 A 收尾的安排，BE-33/35 分工转交 A（BE-34 原由 A 负责），本轮仅实施 BE-33；B 的既有集成准备保留，不代记任何成员本人验收。BE-34/35 本轮不实施，不部署、推送或合并远程。

直接依赖 BE-12/18/19/22/25/27/30/31 均已主线交付。旧 preparationProgress 等待 BE-30 为历史快照，不是当前阻塞。

1. 对现有真实 HTTP 测试加可选契约检查：使用 Git OpenAPI 1.2.0 验证真实响应的状态码、媒体类型、Schema、敏感缓存头；输出每个 operationId 的实际状态覆盖，不将路由存在或单个401算成功覆盖。校验器仅在测试源码内，不改生产链路；使用真实返回的错误字段/必需字段/类型/联合Schema反例验证其拒绝能力。
2. 新增两公司、各两部门、兼岗、两业务系统的正式 CSV 上传→预检→提交，配置共享/部门角色→真实 demo OA 接收/可信回调→持久执行→业务查询；组织/员工/任职/授权/批准/执行均经正式端口建立，只有初始管理员及服务身份是隔离夹具。
3. 串联来源关闭、迟到批准、员工停用恢复、范围缩小扩回、导入离岗/重新任职；验证旧申请/授权/任命/会话不能复活。补导入与已批准执行的双连接顺序、末项审计故障回滚/恢复，实际业务结果和下一次查询为断言。
4. 完整 `clean verify -Pmysql-check`、全部 Python 工具回归、设计静态追踪。只使用本轮专属随机非3306 MySQL8.4实例和随机schema/账号/测试密钥，结束确认资源清理、实例关闭，原 MySQL84 保持。
5. 汇总 AT-01～39/41 的本地后端证据及真实契约覆盖；目标服务器 AT-36/38 部分及 AT-39 发布保留 BE-34/35，不因绿色测试代记服务器、真实外部OA或本人验收。

新增回归针对既有业务，不伪造生产 RED；若发现缺陷，保留真实失败及最小修复。契约检查必须能拒绝缺必需属性、额外属性、错误类型和未声明状态，不能通过放宽契约或删除断言消除差异。

## 实际结果

完整 `clean verify -Pmysql-check` 于 **2026-10-21 22:44:25（Asia/Shanghai）** 通过，耗时 **15分23秒**：**143普通/22组＋436真实MySQL/HTTP/27组**，失败、错误、跳过均0。19项整体组含原8项及本轮新增11项；18项Python辅助工具已全量通过。日志 `backend/.tmp/be33-full-final.log`，49份JUnit XML合计579项。

**67/67入站操作**均有真实成功响应的Schema、状态码、媒体类型与敏感缓存头验证，差异0；新增一次遍历同时验证67项缺凭据边界（登录422、幂等退出204，其余401）。既有各模块请求字段、版本、分页及范围边界测试全部复跑。不把单个401当作操作成功覆盖，也不声称每个操作的所有可能错误状态均已穷举。

完整自动化故事使用正式CSV上传/预检/提交建立两公司、每公司两部门和兼岗，创建两个业务系统、共享/部门角色及独立查看/编辑操作；角色配置和授予均经demo受理、可信OA回调和实际持久执行，再验证跨来源并集、跨公司/系统隔离、来源关闭、迟到批准、全局停用恢复不复权与新申请恢复。其他集成场景验证范围缩小扩回、离岗再任职、旧会话/任命不复活、末审计失败原子回滚及新key重试、执行/导入两个guard顺序、失败后应用上下文启动扫描仅成功一次。

候选jar SHA-256：`cea7f4f4084ec8be245ea3c6494b8d955b0a37a87f8d33e7aadfd41f179498fb`。主代码、测试、构建工具及每份XML摘要绑定见[构建证据](BE-33-35-BUILD-EVIDENCE.json)，逐组数量与逐操作状态见[系统证据](BE-33-SYSTEM-EVIDENCE.json)。候选包/XML位于忽略目录 `backend/target`，未冒称远程二进制发布。

清理核对：随机schema/账号0/0，`delivered-modules-keys-*`、`be33-files-*`及本轮密钥目录0，端口63153本轮专属MySQL已正常关闭，原MySQL84仍Running；既有dev/test库和8080应用未操作。MySQL维持innodb_flush_log_at_trx_commit=1、doublewrite=ON，未通过放宽事务配置换取绿色结果。

BE-33为IMPLEMENTED、automatedSystemVerified=true，**ownerAccepted=false、mergedToMain=false、fullAcceptanceVerified=false**：自动化实施完成，本人确认、主线交付及最终发布分别记录。A按此前安排接手BE-33/35，BE-34原属A；B的原准备标为历史，其他成员验收不代确认。BE-34/35保持PLANNED，不部署或发布。demo OA不是实际外部OA，应用上下文重启不是目标服务器/数据库掉电或灾备验收。验收清单仍保留PARTIALLY_VERIFIED，并补本轮localSystemVerification，避免把本机测试代记所有后端/服务器/交接通过。

发布证据工具的server/endToEnd/ownerHandoff字段要求TARGET_SERVER匹配证据，因此本轮仍为false；本地完整故事通过单独记录，不伪造服务器证据。

## 验收证据索引与边界

以下测试来源已随最终完整回归运行；逐组数量见系统证据。此索引不代记负责人、目标服务器或发布验收。AT-36目标服务器及AT-39发布属于BE-34/35；AT-40为前端后置。AT-01/38的本地行为不能替代服务器证据。

| 验收 | 测试来源/边界 |
| --- | --- |
| AT-01 可重复构建与最小部署 | DatabaseProfileTest/StartupDatabaseMySqlCheck/BootstrapMySqlCheck |
| AT-02 MySQL迁移、即时约束与JPA映射 | MigrationMySqlCheck/JpaMappingMySqlCheck |
| AT-03 写锚点及原子回滚 | TransactionBoundaryMySqlCheck/ImportCommitMySqlCheck/DeliveredModulesMySqlCheck |
| AT-04 HTTP幂等栅栏与失败恢复 | IdempotencyMySqlCheck |
| AT-05 登录与普通员工边界 | IdentityMySqlCheck/AccountMySqlCheck |
| AT-06 退出、改密及账号停用即时撤销 | IdentityMySqlCheck/AccountMySqlCheck/CrossDomainLifecycleMySqlCheck |
| AT-07 三类JWT严格隔离 | IdentityMySqlCheck/AuthorizationMySqlCheck/DeliveredModulesMySqlCheck |
| AT-08 管理员任命与单项上下文 | ScopeHttpMySqlCheck/ScopeCreationMySqlCheck/CrossDomainLifecycleMySqlCheck |
| AT-09 系统进入槽与同批功能目录 | CatalogPreviewMySqlCheck/BModuleMySqlCheck |
| AT-10 独立操作与目录启停 | CatalogPreviewMySqlCheck/AuthorizationMySqlCheck |
| AT-11 角色元数据与归属 | BModuleMySqlCheck/ChangeDraftMySqlCheck |
| AT-12 角色配置新增/保留/移除与空配置 | SubmissionWorkflowMySqlCheck/CatalogPreviewMySqlCheck |
| AT-13 完整任职依据与岗位不自动授权 | PeopleHttpMySqlCheck/GrantQueriesMySqlCheck/GrantMutationsMySqlCheck |
| AT-14 同源去重与异源并存 | GrantMutationsMySqlCheck/SubmissionWorkflowMySqlCheck/DeliveredModulesMySqlCheck |
| AT-15 零基线关权与执行两个顺序 | SourceClosureMySqlCheck/GrantMutationsMySqlCheck/DeliveredModulesMySqlCheck |
| AT-16 无变化、权限与版本判定顺序 | ChangeDraftMySqlCheck/SubmissionWorkflowMySqlCheck/SourceClosureMySqlCheck |
| AT-17 草稿占用与删除 | ChangeDraftMySqlCheck/AuditRequestQueriesMySqlCheck |
| AT-18 固定申请与规范摘要 | SubmissionWorkflowMySqlCheck/AuditRequestQueriesMySqlCheck |
| AT-19 原申请人离任与原范围缩小 | SubmissionWorkflowMySqlCheck/CrossDomainLifecycleMySqlCheck/DeliveredModulesMySqlCheck |
| AT-20 OA投递超时与关联前回调 | SubmissionWorkflowMySqlCheck |
| AT-21 OA事件、最终结果三层防重 | SubmissionWorkflowMySqlCheck/DeliveredModulesMySqlCheck |
| AT-22 一次成功与全批执行 | SubmissionWorkflowMySqlCheck/GrantMutationsMySqlCheck/DeliveredModulesMySqlCheck |
| AT-23 执行失败恢复与进程重启 | SubmissionWorkflowMySqlCheck/DeliveredModulesMySqlCheck |
| AT-24 员工停用/恢复与会话 | CrossDomainLifecycleMySqlCheck/ImportCommitMySqlCheck/DeliveredModulesMySqlCheck |
| AT-25 离岗事件顺序Q-01 | ImportCommitMySqlCheck/DeliveredModulesMySqlCheck |
| AT-26 上传与文件解析边界 | ImportFileParserTest/ImportFileMySqlCheck |
| AT-27 预检冻结、映射与候选冲突 | ImportPlannerTest/ImportPrecheckMySqlCheck/ImportCommitMySqlCheck |
| AT-28 导入第N项失败与重试 | ImportCommitMySqlCheck/DeliveredModulesMySqlCheck |
| AT-29 部门迁移与历史保护 | PeopleHttpMySqlCheck/ImportPrecheckMySqlCheck/ImportCommitMySqlCheck |
| AT-30 业务权限完整并集与隔离 | AuthorizationMySqlCheck/DeliveredModulesMySqlCheck |
| AT-31 影响快照与新增目标 | CatalogPreviewMySqlCheck/CrossDomainLifecycleMySqlCheck/ImportPrecheckMySqlCheck |
| AT-32 敏感混合申请计数与审计 | SourceClosureMySqlCheck/AuditRequestQueriesMySqlCheck |
| AT-33 当前快照与撤权后的新查询 | AuthorizationMySqlCheck/DeliveredModulesMySqlCheck |
| AT-34 审计读取与历史删除 | AuditRequestQueriesMySqlCheck/ChangeDraftMySqlCheck |
| AT-35 演示OA与服务令牌边界 | SubmissionWorkflowMySqlCheck/DeliveredModulesMySqlCheck/test_demo_callback.py |
| AT-36 服务器部署和数据持久化 | 未执行，BE-34真实服务器 |
| AT-37 全接口契约与Problem | OpenApiResponsesTest/19组真实HTTP检查/67操作覆盖摘要 |
| AT-38 示例全链路演示 | DeliveredModulesMySqlCheck.importedMultiCompanyMultiDepartmentMultiSystemStoryKeepsIndependentSources |
| AT-39 PR与发布证据 | 未执行，BE-35发布收尾 |
| AT-40 前端后续衔接 | 前端后置，不计本轮后端 |
| AT-41 来源资格与角色部门资格分别失效 | ImportCommitMySqlCheck/GrantMutationsMySqlCheck/SourceClosureMySqlCheck |

## 已观察的失败与修正

- 第一轮完整回归 `backend/.tmp/be33-full-first.log`：142普通测试通过；432真实MySQL/HTTP中新增整体组6失败、1夹具错误，原有组未出现失败。新增夹具将业务JWT设置为600秒而项目上限300秒，范围创建响应误按平面id读取，导入存储故障预期应为503；已按现有契约修正，未以此修改生产语义。执行先于导入提交会使旧冻结依赖STALE，测试改为明确409/STALE、无部分提交、重新预检后成功关权，不将合理拒绝当作允许旧计划执行。
- 正式接口覆盖首次仅66/67，唯一缺成功覆盖为 `updateRoleMetadata`。新增 `roleMetadataUpdateIsAvailableAtTheFrozenOpenApiPath` 真实RED（`be33-role-route-red.log`）：1项、1失败、0错误，期望200实际404。生产控制器原 `PUT /api/v1/admin/roles/{roleId}` 改为原契约 `/metadata`；同一操作的幂等规范路径同步修正，并验证成功记录采用正式URI。B原元数据/授权/幂等测试全部改用正式路径；不改OpenAPI、不保留未声明别名。其他模块归属、权限语义与迁移不变。
- 契约检查器反例 `be33-contract-validator-red.log`：4项中1失败，证实oneOf同级约束被遗漏；校验器修正为联合分支和同级约束都检查，保留缺字段/额外字段/错误类型/枚举/日期/媒体类型/敏感缓存等反例。此为测试工具缺陷，不冒充生产业务RED。

以上均为过程证据，最终通过结果及未通过事项另记，不把中途失败删除或计作跳过。

定向修复回归 `backend/.tmp/be33-targeted-green.log` 于2026-10-21 22:28:09通过：143普通测试，B模块14项＋整体组19项真实MySQL/HTTP，失败/错误/跳过0。整体组含原B的8项与本轮新增11项，包括正式路径、67操作缺凭据边界、两种guard先后、应用上下文重启恢复；所有过程故障均保留。最终全量报告另记。

只读发布检查实际退出2、ready=false；已确认没有源码/包/报告摘要不符、报告缺失或测试失败类阻断。67操作实现清单与579项报告一致。剩余是本人确认、BE-33尚未主线交付、BE-34/35未实施、正式AT及TARGET_SERVER/交接证据，不能把发布门槛拒绝当作业务测试失败或反过来删除门槛。

验证实现已在本地任务分支提交：`3bad24c13586c93421f72c912955b2c5102cc4bb`。提交之后只补来源SHA和静态/文档追踪，不改变已验证的主代码、测试、构建或辅助工具，也不重复Java/SQL。任务分支未推送，main仍为同步基线0ff0ad7。
