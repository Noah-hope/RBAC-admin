# 用例—页面—接口追踪

依据需求V1.3和原型阶段评审；页面为交互输入，不把Mock按钮或样本限制变成正式需求。精确路径由 [接口清单](ENDPOINTS.md) 中 operationId 定位。

| 用例 | 页面/视角 | operationId | 契约覆盖及重点核对 |
| --- | --- | --- | --- |
| UC-01 | 系统与权限目录／总管理员维护，权限管理员只读使用 | listSystems, createSystem, getSystem, systemStatePreview, updateSystem, listDirectoryNodes, listPermissions, catalogPreview, saveCatalog, listPreviewEmployees | 新系统唯一进入权限且不授权；目录/权限编码稳定；同系统父级无环、可见权限同功能；原子批次、启停影响、稳定排序R-03 |
| UC-02 | 角色权限／总管理员共享角色及部门角色，权限管理员本部门角色 | listRoles, createRole, getRole, updateRoleMetadata, createChangeDraft, updateChangeDraft, deleteChangeDraft, changePreview, submitChange, listPreviewEmployees | 角色和草稿同事务、编码后端生成；单一占用，权限独立，空配置不提交；旧配置保留；员工影响及剩余进入来源R-01，范围外明细不泄露 |
| UC-03 | 员工授权／两后台身份 | listEmployees, getEmployee, listEmployments, listRoles, listGrants, grantBaselines, createChangeDraft, updateChangeDraft, changePreview, submitChange | 明确配对不笛卡尔积；本公司/部门任职及来源独立；逐条角色版本、来源周期、关系版本；无变化排除，整批执行；分配不替换其他授权 |
| UC-04 | 员工来源详情／一般撤销与关闭原部门权限 | listGrants, createChangeDraft, updateChangeDraft, changePreview, submitChange, sourceBaselines, sourceClosePreview, closeSource | 批量一般撤销R-02按准确关系/版本经OA；离岗直接关权推进周期，其他来源保留；历史只读；无关系待申请也关权；不全局停用 |
| UC-05 | 独立业务系统HTTP调用端；原型调用演示仅参考 | authorizationQuery | 业务身份绑定系统、公司上下文隔离，完整一致快照，进入资格与菜单/操作分开；无进入返回false/空集合，进入不自动授予功能 |
| UC-06 | 独立业务系统后端功能入口 | permissionCheck | 单项检查与UC-05共用规则；进入不递归，功能先查进入；编辑不推导查看；未知编码false/null，服务故障独立错误，不读取业务记录 |
| UC-07 | 管理员与范围／总管理员 | listScopes, scopeCreatePreview, createScope, getScope, listScopeAppointments, scopeEditPreview, replaceScope, myAppointments | 范围/任命分开，指定公司一个平级部门/系统集合；可空名单/系统，同范围协作；任命去重、版本及并发，原申请依据不随人离任删除 |
| UC-08 | 审计记录／当前资格范围 | listAudit, getAudit, listChangeEvents, getChangeRequest | 历史来源＋当前范围过滤列表/统计/详情/关联；全局记录仅总管理员；只读、无导出/修改/重放；原申请/批准/实际执行区分 |
| UC-09 | 登录、个人账号、管理员账号配套 | login, currentSession, logout, changeOwnPassword, myAppointments, listAccounts, createAccount, getAccount, setAccountState, resetAccountPassword | 后台账号≠员工≠任命；临时密码受限改密；JWT及服务端当前校验；退出当前会话、停用/改密/重置全部旧会话失效；员工每人最多一个后台账号 |
| UC-10 | 员工组织／总管理员维护、权限管理员只读 | listCompanies, listDepartments, listPositions, listEmployees, listEmployments, employeeStatePreview, setEmployeeState, uploadImportFile, getImportFile, precheckImport, listImportBatches, getImportBatch, listImportIssues, listImportPlanChanges, commitImport | 明确文件/映射/批次、稳定标识、并岗去重；不由缺行推离职；已引用部门跨公司迁移阻断；预检不生效，整批原子；结束任职/全局停用持久失效，恢复不复权，防自锁 |
| UC-11 | 变更申请只读状态与投递重试；OA受信调用方 | listChangeRequests, getChangeRequest, listChangeEvents, retryOaDelivery, receiveOaResult；出站deliverOaSubmission | 固定投递/可信最终结果/自动执行，三状态轴；原范围依据/周期/版本；重复冲突回调；批准可靠落地后服务端恢复；无管理员批准、模拟执行或强制覆盖接口 |

## 跨用例反例追踪

| 规则 | 覆盖点 |
| --- | --- |
| DR-01/02：指定部门精确边界与同范围协作 | 所有范围操作的任命ID/版本头、列表/详情过滤、Authority快照及CONSISTENCY原范围复核 |
| DR-03：共享角色维护边界 | createRole/updateRoleMetadata/ROLE_CONFIGURATION拒绝权限管理员；ROLE_GRANT可合法分配 |
| DR-04～06：独立来源/旧批准不复活 | GrantLine、SourceBaseline、SourceCloseInput、sourceEpoch/relationVersion与自动执行 |
| DR-07/08：全局员工与账号停用分开 | setEmployeeState和setAccountState、会话即时撤销及恢复规则 |
| DR-09：无公司/部门启停 | Company/Department没有status/enabled，只有组织读取及明确导入更名/迁移 |
| DR-10：固定申请投递失败重试 | retryOaDelivery、deliverOaSubmission固定requestId/摘要及最终关联 |
| R-01～03原型缺口 | API均覆盖；原型源码不改，继续在STAGE-REVIEW按D-029跟踪 |
| R-04同步方式 | Git权威→人工导入Apifox→MCP刷新/回读语义核对；APIFOX.md如实记录实际状态 |

全部接口均有 x-use-cases 和 x-access，维护脚本检查 operationId 唯一、UC-01～11覆盖及与本表关联。追踪覆盖是设计证据，不是后端验收测试通过证据。

API评审补充：`listPreviewTargets` 与上述 `listPreviewEmployees` 共用预览快照，覆盖UC-01目录/角色影响、UC-02配置影响、UC-03/04明确关系与待分配申请、UC-07任命/原申请影响、UC-10全局员工状态影响。它不授予额外读取资格；混合范围的整份申请不可见时，不返回其ID或计数。导入文件预检的冻结行明细仍由 `listImportPlanChanges` 返回，包含归属编码及字段前后差异。
