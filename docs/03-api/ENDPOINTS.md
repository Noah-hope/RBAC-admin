# 接口清单

所有路径均为正式设计，尚未实现。公共约定见 [CONVENTIONS.md](CONVENTIONS.md)；精确模型见 [openapi.yaml](openapi.yaml)。

“范围”指总管理员全局资格或权限管理员逐请求选择一项任命覆盖全部目标；共享角色维护仅总管理员。

| 模块 | 方法 | 路径 | operationId | 入口/资格 | 用例 | 用途 |
| --- | --- | --- | --- | --- | --- | --- |
| 会话 | POST | /api/v1/admin/sessions | login | 公开 | UC-09 | 管理员登录 |
| 会话 | GET | /api/v1/admin/sessions/current | currentSession | 管理员 | UC-09 | 读取当前正常会话 |
| 会话 | DELETE | /api/v1/admin/sessions/current | logout | 管理员 | UC-09 | 退出当前会话 |
| 会话 | PUT | /api/v1/admin/password | changeOwnPassword | 管理员 | UC-09 | 修改本人密码并撤销全部会话 |
| 会话 | GET | /api/v1/admin/my-appointments | myAppointments | 管理员 | UC-09 | 分页读取当前有效任命 |
| 账号 | GET | /api/v1/admin/accounts | listAccounts | 总管理员 | UC-09 | 后台账号列表 |
| 账号 | POST | /api/v1/admin/accounts | createAccount | 总管理员 | UC-09 | 创建权限管理员后台账号 |
| 账号 | GET | /api/v1/admin/accounts/{accountId} | getAccount | 总管理员 | UC-09 | 读取后台账号 |
| 账号 | PUT | /api/v1/admin/accounts/{accountId}/state | setAccountState | 总管理员 | UC-09 | 启停后台账号 |
| 账号 | PUT | /api/v1/admin/accounts/{accountId}/password | resetAccountPassword | 总管理员 | UC-09 | 重置权限管理员临时密码 |
| 目录 | GET | /api/v1/admin/systems | listSystems | 范围 | UC-01 | 可见业务系统列表 |
| 目录 | POST | /api/v1/admin/systems | createSystem | 总管理员 | UC-01 | 登记系统及唯一进入权限 |
| 目录 | GET | /api/v1/admin/systems/{systemId} | getSystem | 范围 | UC-01 | 系统详情 |
| 目录 | POST | /api/v1/admin/systems/{systemId}/state-previews | systemStatePreview | 总管理员 | UC-01 | 系统资料与状态变更预览 |
| 目录 | PUT | /api/v1/admin/systems/{systemId} | updateSystem | 总管理员 | UC-01 | 保存系统资料及启停状态 |
| 目录 | GET | /api/v1/admin/systems/{systemId}/directory-nodes | listDirectoryNodes | 范围 | UC-01 | 分页读取目录节点 |
| 目录 | GET | /api/v1/admin/systems/{systemId}/permissions | listPermissions | 范围 | UC-01 | 分页读取权限目录 |
| 目录 | POST | /api/v1/admin/systems/{systemId}/catalog-previews | catalogPreview | 总管理员 | UC-01 | 原子目录变更预览 |
| 目录 | POST | /api/v1/admin/systems/{systemId}/catalog-changes | saveCatalog | 总管理员 | UC-01 | 原子保存目录变更 |
| 角色 | GET | /api/v1/admin/roles | listRoles | 范围 | UC-02 | 分页读取可见角色 |
| 角色 | POST | /api/v1/admin/roles | createRole | 范围 | UC-02 | 创建角色与初始配置草稿 |
| 角色 | GET | /api/v1/admin/roles/{roleId} | getRole | 范围 | UC-02 | 读取角色有效配置 |
| 角色 | PUT | /api/v1/admin/roles/{roleId}/metadata | updateRoleMetadata | 范围 | UC-02 | 修改角色名称与说明 |
| 员工 | GET | /api/v1/admin/employees | listEmployees | 范围 | UC-03 | 分页读取可管理员工 |
| 员工 | GET | /api/v1/admin/employees/{employeeId} | getEmployee | 范围 | UC-03 | 员工安全详情 |
| 员工 | GET | /api/v1/admin/employees/{employeeId}/employments | listEmployments | 范围 | UC-03 | 分页读取可见任职 |
| 员工 | GET | /api/v1/admin/grants | listGrants | 范围 | UC-03,UC-04 | 分页读取独立来源授权关系 |
| 员工 | POST | /api/v1/admin/grant-baselines | grantBaselines | 范围 | UC-03 | 读取分配所需关系与来源基线 |
| 申请 | GET | /api/v1/admin/change-requests | listChangeRequests | 范围 | UC-02,UC-03,UC-04,UC-11 | 分页读取可见申请 |
| 申请 | POST | /api/v1/admin/change-requests | createChangeDraft | 范围 | UC-02,UC-03,UC-04 | 保存配置/分配/撤销草稿 |
| 申请 | GET | /api/v1/admin/change-requests/{requestId} | getChangeRequest | 范围 | UC-02,UC-03,UC-04,UC-11 | 固定申请详情 |
| 申请 | PUT | /api/v1/admin/change-requests/{requestId} | updateChangeDraft | 范围 | UC-02,UC-03,UC-04 | 协作编辑未提交草稿 |
| 申请 | DELETE | /api/v1/admin/change-requests/{requestId} | deleteChangeDraft | 范围 | UC-02,UC-03,UC-04 | 删除未提交草稿 |
| 申请 | POST | /api/v1/admin/change-requests/{requestId}/previews | changePreview | 范围 | UC-02,UC-03,UC-04 | 预览差异与员工影响 |
| 申请 | POST | /api/v1/admin/change-requests/{requestId}/submissions | submitChange | 范围 | UC-02,UC-03,UC-04 | 冻结并提交 OA 申请 |
| 申请 | POST | /api/v1/admin/change-requests/{requestId}/delivery-retries | retryOaDelivery | 范围 | UC-11 | 重试原固定申请投递 |
| 申请 | GET | /api/v1/admin/change-requests/{requestId}/events | listChangeEvents | 范围 | UC-11 | 分页读取申请事件 |
| 申请 | GET | /api/v1/admin/previews/{previewId}/employees | listPreviewEmployees | 范围 | UC-01,UC-02,UC-03,UC-04,UC-07,UC-10 | 分页读取影响员工 |
| 申请 | GET | /api/v1/admin/previews/{previewId}/targets | listPreviewTargets | 范围 | UC-01,UC-02,UC-03,UC-04,UC-07,UC-10 | 分页读取有权查看的目标影响 |
| 来源 | POST | /api/v1/admin/source-baselines | sourceBaselines | 范围 | UC-04 | 读取来源周期基线 |
| 来源 | POST | /api/v1/admin/source-closure-previews | sourceClosePreview | 范围 | UC-04 | 预览关闭原部门权限 |
| 来源 | POST | /api/v1/admin/source-closures | closeSource | 范围 | UC-04 | 直接关闭原部门授权来源 |
| 组织导入 | GET | /api/v1/admin/companies | listCompanies | 范围 | UC-07,UC-10 | 分页读取公司 |
| 组织导入 | GET | /api/v1/admin/departments | listDepartments | 范围 | UC-07,UC-10 | 分页读取平级部门 |
| 组织导入 | GET | /api/v1/admin/positions | listPositions | 范围 | UC-07,UC-10 | 分页读取岗位 |
| 组织导入 | POST | /api/v1/admin/employees/{employeeId}/state-previews | employeeStatePreview | 总管理员 | UC-10 | 预览员工全局停用/恢复 |
| 组织导入 | PUT | /api/v1/admin/employees/{employeeId}/state | setEmployeeState | 总管理员 | UC-10 | 员工全局停用或恢复 |
| 范围 | GET | /api/v1/admin/management-scopes | listScopes | 总管理员 | UC-07 | 分页读取管理范围 |
| 范围 | POST | /api/v1/admin/management-scope-previews | scopeCreatePreview | 总管理员 | UC-07 | 预览新增范围和任命 |
| 范围 | POST | /api/v1/admin/management-scopes | createScope | 总管理员 | UC-07 | 原子新增范围与任命 |
| 范围 | GET | /api/v1/admin/management-scopes/{scopeId} | getScope | 总管理员 | UC-07 | 管理范围详情 |
| 范围 | GET | /api/v1/admin/management-scopes/{scopeId}/appointments | listScopeAppointments | 总管理员 | UC-07 | 分页读取任命及历史 |
| 范围 | POST | /api/v1/admin/management-scopes/{scopeId}/previews | scopeEditPreview | 总管理员 | UC-07 | 预览范围与名单替换 |
| 范围 | PUT | /api/v1/admin/management-scopes/{scopeId} | replaceScope | 总管理员 | UC-07 | 原子更新范围及任命 |
| 审计 | GET | /api/v1/admin/audit-events | listAudit | 范围 | UC-08 | 分页筛选授权审计 |
| 审计 | GET | /api/v1/admin/audit-events/{auditEventId} | getAudit | 范围 | UC-08 | 读取审计差异详情 |
| 组织导入 | POST | /api/v1/admin/import-files | uploadImportFile | 总管理员 | UC-10 | 上传导入文件 |
| 组织导入 | GET | /api/v1/admin/import-files/{fileId} | getImportFile | 总管理员 | UC-10 | 读取暂存文件元信息 |
| 组织导入 | POST | /api/v1/admin/import-batches | precheckImport | 总管理员 | UC-10 | 建立冻结预检批次 |
| 组织导入 | GET | /api/v1/admin/import-batches | listImportBatches | 总管理员 | UC-10 | 分页读取导入批次 |
| 组织导入 | GET | /api/v1/admin/import-batches/{batchId} | getImportBatch | 总管理员 | UC-10 | 导入批次及执行报告 |
| 组织导入 | GET | /api/v1/admin/import-batches/{batchId}/issues | listImportIssues | 总管理员 | UC-10 | 分页读取逐行预检问题 |
| 组织导入 | POST | /api/v1/admin/import-batches/{batchId}/commits | commitImport | 总管理员 | UC-10 | 原子执行冻结导入批次 |
| 权限调用 | POST | /api/v1/integration/authorization-queries | authorizationQuery | 业务系统 | UC-05 | 查询员工当前有效功能权限 |
| 权限调用 | POST | /api/v1/integration/permission-checks | permissionCheck | 业务系统 | UC-06 | 检查员工指定功能权限 |
| OA结果 | POST | /api/v1/oa/final-results | receiveOaResult | OA | UC-11 | 接收可信OA最终结果 |
| 组织导入 | GET | /api/v1/admin/import-batches/{batchId}/changes | listImportPlanChanges | 总管理员 | UC-10 | 冻结计划明细及生成代码预览 |

出站 OA 适配器契约：`webhooks.oaSubmission`，operationId=`deliverOaSubmission`。URL 由服务端配置，不是权限中心管理员 API。
