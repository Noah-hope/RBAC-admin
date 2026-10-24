# 页面与 API 对应

依据：`docs/03-api/openapi.yaml` 1.2.0。`npm run generate:api` 生成 64 个管理员操作的路径、类型和上下文要求；其中 62 个在页面、选择器或名称解析中使用。下表是实现映射，不代表所有操作均已与真实后端联调，也不代表每种异常都有浏览器用例。

| 页面 / 能力                | operationId                                                                                                                                                   |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 登录、改密、退出、单项任命 | login、changeOwnPassword、logout、myAppointments                                                                                                              |
| 后台账号                   | listAccounts、getAccount、createAccount、setAccountState、resetAccountPassword                                                                                |
| 管理范围及任命             | listScopes、getScope、listScopeAppointments、scopeCreatePreview、createScope、scopeEditPreview、replaceScope                                                  |
| 系统及目录                 | listSystems、getSystem、createSystem、systemStatePreview、updateSystem、listDirectoryNodes、listPermissions、catalogPreview、saveCatalog                      |
| 角色                       | listRoles、getRole、createRole、updateRoleMetadata                                                                                                            |
| 员工、任职、授权           | listEmployees、getEmployee、listEmployments、listGrants、grantBaselines                                                                                       |
| 来源关闭、员工全局状态     | sourceBaselines、sourceClosePreview、closeSource、employeeStatePreview、setEmployeeState                                                                      |
| 申请草稿、预览、投递和查询 | listChangeRequests、getChangeRequest、createChangeDraft、updateChangeDraft、deleteChangeDraft、changePreview、submitChange、retryOaDelivery、listChangeEvents |
| 预览明细                   | listPreviewEmployees、listPreviewTargets                                                                                                                      |
| 组织、岗位及候选选择       | listCompanies、listDepartments、listPositions                                                                                                                 |
| 审计                       | listAudit、getAudit                                                                                                                                           |
| 文件及整批导入             | uploadImportFile、precheckImport、listImportBatches、getImportBatch、listImportIssues、listImportPlanChanges、commitImport                                    |

生成但未单独调用的两个读操作：`currentSession`（当前会话来自登录响应，刷新后要求重新登录）；`getImportFile`（上传响应已提供本次映射所需文件信息）。不为覆盖数量增加无意义请求。

浏览器不生成 `/integration`、`/oa` 操作，不持有 BusinessJwt/OaJwt。普通员工不登录权限中心；原型“权限调用演示”不进入正式前端。需要查看业务侧有效菜单时，应使用受信任业务服务调用后端业务查询接口，不能在管理员页面粘贴服务端凭据。

统一客户端负责：登录无旧 Authorization、其他请求内存 Bearer、权限管理员三个任命/范围头、可选空查询项过滤、写操作幂等键、结构化错误、上下文切换后丢弃旧响应。`X-Request-Id` 可选且未发送空值。预览确认发送原输入和原版本；错误或冲突不显示成功。

历史审计与申请详情直接显示固定服务端数据及标识；当前列表中的组织/角色名称通过受同一权限上下文约束的读接口解析，无法读取时保留标识。

## 当前需要真实环境补验的内容

启动既有 BackendApplication 后，按 [使用指南](README.md) 完成真实登录与强制改密、角色配置申请、OA 执行、授予/撤销、来源关闭、组织导入、权限管理员范围切换。确认响应与界面一致，并观察后端日志中的请求号。

本轮本机 8080 健康检查连接被拒绝，尚未验证真实后端响应与这些页面的全部组合。发现契约、路由、权限或业务错误时先保存操作、HTTP 状态与请求号，报告后再处理后端；不以修改数据库或放宽授权绕过问题。
