# HTTP处理入口与输入输出契约

M12负责严格DTO/参数与响应映射，下表为模块入口契约，不是已创建的Java类；精确字段/错误分支仍以OpenAPI为准。公共头逐请求校验，JSON快照不直接映射实体。机器映射另保存所有参数名称、位置及必填性。

| 入口 | 模块/本地任务 | body模型 | 成功状态/模型 | 参数 |
| --- | --- | --- | --- | --- |
| POST login | M01 / BE-06 | LoginInput | 201:Session | header:X-Request-Id |
| GET currentSession | M01 / BE-06 | 无body | 200:SessionView | header:X-Request-Id |
| DELETE logout | M01 / BE-06 | 无body | 204:无body/内联结果 | header:X-Request-Id |
| PUT changeOwnPassword | M01 / BE-06 | PasswordInput | 204:无body/内联结果 | header:X-Request-Id |
| GET myAppointments | M03 / BE-08 | 无body | 200:AppointmentPage | header:X-Request-Id, query:page, query:pageSize |
| GET listAccounts | M01 / BE-07 | 无body | 200:AccountPage | header:X-Request-Id, query:page, query:pageSize, query:employeeId, query:q, query:enabled |
| POST createAccount | M01 / BE-07 | AccountCreate | 201:Account | header:X-Request-Id, header:Idempotency-Key* |
| GET getAccount | M01 / BE-07 | 无body | 200:Account | header:X-Request-Id, path:accountId* |
| PUT setAccountState | M01 / BE-07 | AccountStateInput | 200:Account | header:X-Request-Id, path:accountId*, header:Idempotency-Key* |
| PUT resetAccountPassword | M01 / BE-07 | PasswordReset | 200:Account | header:X-Request-Id, path:accountId*, header:Idempotency-Key* |
| GET listSystems | M04 / BE-11 | 无body | 200:ApplicationPage | header:X-Request-Id, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:enabled, query:q, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST createSystem | M04 / BE-11 | ApplicationInput | 201:Application | header:X-Request-Id, header:Idempotency-Key* |
| GET getSystem | M04 / BE-11 | 无body | 200:Application | header:X-Request-Id, path:systemId*, header:X-Admin-Appointment-Id, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| PUT updateSystem | M04 / BE-12 | ApplicationSave | 200:Application | header:X-Request-Id, path:systemId*, header:Idempotency-Key* |
| POST systemStatePreview | M04 / BE-12 | ApplicationEdit | 200:Preview | header:X-Request-Id, path:systemId* |
| GET listDirectoryNodes | M04 / BE-12 | 无body | 200:DirectoryNodePage | header:X-Request-Id, path:systemId*, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:parentId, query:nodeType, query:enabled, query:rootOnly, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listPermissions | M04 / BE-12 | 无body | 200:PermissionPage | header:X-Request-Id, path:systemId*, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:functionId, query:permissionType, query:enabled, query:q, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST catalogPreview | M04 / BE-12 | CatalogInput | 200:Preview | header:X-Request-Id, path:systemId* |
| POST saveCatalog | M04 / BE-12 | CatalogSave | 200:CatalogResult | header:X-Request-Id, path:systemId*, header:Idempotency-Key* |
| GET listRoles | M05 / BE-13 | 无body | 200:RolePage | header:X-Request-Id, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:companyId, query:departmentId, query:systemId, query:state, query:q, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST createRole | M07 / BE-20 | RoleCreate | 201:RoleCreated | header:X-Request-Id, header:X-Admin-Appointment-Id, header:Idempotency-Key*, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET getRole | M05 / BE-13 | 无body | 200:Role | header:X-Request-Id, path:roleId*, header:X-Admin-Appointment-Id, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| PUT updateRoleMetadata | M05 / BE-13 | RoleMetadata | 200:Role | header:X-Request-Id, path:roleId*, header:X-Admin-Appointment-Id, header:Idempotency-Key*, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listEmployees | M02 / BE-10 | 无body | 200:EmployeePage | header:X-Request-Id, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:companyId, query:departmentId, query:status, query:q, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET getEmployee | M02 / BE-10 | 无body | 200:Employee | header:X-Request-Id, path:employeeId*, header:X-Admin-Appointment-Id, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listEmployments | M02 / BE-10 | 无body | 200:EmploymentPage | header:X-Request-Id, path:employeeId*, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:companyId, query:departmentId, query:status, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listGrants | M06 / BE-14 | 无body | 200:GrantPage | header:X-Request-Id, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:employeeId, query:roleId, query:companyId, query:sourceDepartmentId, query:systemId, query:status, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST grantBaselines | M06 / BE-14 | BaselineQuery | 200:BaselineResult | header:X-Request-Id, header:X-Admin-Appointment-Id, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listChangeRequests | M07 / BE-22 | 无body | 200:ChangeRequestPage | header:X-Request-Id, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:companyId, query:systemId, query:state, query:deliveryStatus, query:approvalResult, query:executionStatus, query:q, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST createChangeDraft | M07 / BE-20 | DraftInput | 201:ChangeRequest | header:X-Request-Id, header:X-Admin-Appointment-Id, header:Idempotency-Key*, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET getChangeRequest | M07 / BE-22 | 无body | 200:ChangeRequest | header:X-Request-Id, path:requestId*, header:X-Admin-Appointment-Id, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| PUT updateChangeDraft | M07 / BE-20 | DraftEdit | 200:ChangeRequest | header:X-Request-Id, path:requestId*, header:X-Admin-Appointment-Id, header:Idempotency-Key*, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| DELETE deleteChangeDraft | M07 / BE-20 | 无body | 204:无body/内联结果 | header:X-Request-Id, path:requestId*, header:X-Admin-Appointment-Id, query:expectedDraftVersion*, header:Idempotency-Key*, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST changePreview | M07 / BE-21 | ChangePreviewInput | 200:ChangePreview | header:X-Request-Id, path:requestId*, header:X-Admin-Appointment-Id, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST submitChange | M07 / BE-21 | SubmitInput | 202:SubmissionResult；200:SubmissionResult | header:X-Request-Id, path:requestId*, header:X-Admin-Appointment-Id, header:Idempotency-Key*, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST retryOaDelivery | M08 / BE-23 | RetryInput | 202:ChangeRequest | header:X-Request-Id, path:requestId*, header:X-Admin-Appointment-Id, header:Idempotency-Key*, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listChangeEvents | M07 / BE-22 | 无body | 200:ChangeEventPage | header:X-Request-Id, path:requestId*, header:X-Admin-Appointment-Id, query:page, query:pageSize, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listPreviewEmployees | M11 / BE-17 | 无body | 200:ImpactEmployeePage | header:X-Request-Id, path:previewId*, header:X-Admin-Appointment-Id, query:page, query:pageSize, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST sourceBaselines | M06 / BE-14 | SourceQuery | 200:SourceQueryResult | header:X-Request-Id, header:X-Admin-Appointment-Id, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST sourceClosePreview | M10 / BE-26 | SourceCloseInput | 200:Preview | header:X-Request-Id, header:X-Admin-Appointment-Id, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST closeSource | M10 / BE-26 | SourceCloseSave | 200:SourceCloseResult | header:X-Request-Id, header:X-Admin-Appointment-Id, header:Idempotency-Key*, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listCompanies | M02 / BE-10 | 无body | 200:CompanyPage | header:X-Request-Id, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:q, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listDepartments | M02 / BE-10 | 无body | 200:DepartmentPage | header:X-Request-Id, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:q, query:companyId, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET listPositions | M02 / BE-10 | 无body | 200:PositionPage | header:X-Request-Id, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:q, query:companyId, query:departmentId, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST employeeStatePreview | M10 / BE-27 | EmployeeStateInput | 200:Preview | header:X-Request-Id, path:employeeId* |
| PUT setEmployeeState | M10 / BE-27 | EmployeeStateSave | 200:EmployeeStateResult | header:X-Request-Id, path:employeeId*, header:Idempotency-Key* |
| GET listScopes | M03 / BE-08 | 无body | 200:ScopePage | header:X-Request-Id, query:page, query:pageSize, query:companyId, query:departmentId |
| POST createScope | M03 / BE-09 | ScopeCreateSave | 201:ScopeResult | header:X-Request-Id, header:Idempotency-Key* |
| POST scopeCreatePreview | M03 / BE-09 | ScopeInput | 200:Preview | header:X-Request-Id |
| GET getScope | M03 / BE-08 | 无body | 200:Scope | header:X-Request-Id, path:scopeId* |
| PUT replaceScope | M10 / BE-27 | ScopeSave | 200:ScopeResult | header:X-Request-Id, path:scopeId*, header:Idempotency-Key* |
| GET listScopeAppointments | M03 / BE-08 | 无body | 200:AppointmentPage | header:X-Request-Id, path:scopeId*, query:page, query:pageSize, query:status |
| POST scopeEditPreview | M10 / BE-27 | ScopeEdit | 200:Preview | header:X-Request-Id, path:scopeId* |
| GET listAudit | M11 / BE-19 | 无body | 200:AuditEventPage | header:X-Request-Id, header:X-Admin-Appointment-Id, query:page, query:pageSize, query:companyId, query:sourceDepartmentId, query:systemId, query:employeeId, query:roleId, query:actorId, query:changeRequestId, query:oaApprovalId, query:from, query:to, query:eventType, query:result, query:requestId, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| GET getAudit | M11 / BE-19 | 无body | 200:AuditDetail | header:X-Request-Id, path:auditEventId*, header:X-Admin-Appointment-Id, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST uploadImportFile | M10 / BE-28 | 内联multipart/JSON | 201:ImportFile | header:X-Request-Id, header:Idempotency-Key* |
| GET getImportFile | M10 / BE-28 | 无body | 200:ImportFile | header:X-Request-Id, path:fileId* |
| POST precheckImport | M10 / BE-29 | ImportInput | 201:ImportBatch | header:X-Request-Id, header:Idempotency-Key* |
| GET listImportBatches | M10 / BE-29 | 无body | 200:ImportBatchPage | header:X-Request-Id, query:page, query:pageSize, query:sourceBatchId, query:state |
| GET getImportBatch | M10 / BE-29 | 无body | 200:ImportBatch | header:X-Request-Id, path:batchId* |
| GET listImportIssues | M10 / BE-29 | 无body | 200:ImportIssuePage | header:X-Request-Id, path:batchId*, query:page, query:pageSize, query:severity |
| POST commitImport | M10 / BE-30 | ImportCommit | 200:ImportBatch | header:X-Request-Id, path:batchId*, header:Idempotency-Key* |
| POST authorizationQuery | M11 / BE-18 | AuthorizationInput | 200:AuthorizationResult | header:X-Request-Id |
| POST permissionCheck | M11 / BE-18 | CheckInput | 200:CheckResult | header:X-Request-Id |
| POST receiveOaResult | M08 / BE-24 | OaFinalResult | 202:OaReceipt；200:OaReceipt | header:X-Request-Id |
| GET listImportPlanChanges | M10 / BE-29 | 无body | 200:ImportPlanChangePage | header:X-Request-Id, path:batchId*, query:page, query:pageSize |
| GET listPreviewTargets | M11 / BE-17 | 无body | 200:ImpactTargetPage | header:X-Request-Id, path:previewId*, header:X-Admin-Appointment-Id, query:page, query:pageSize, header:X-Admin-Scope-Version, header:X-Admin-Appointment-Version |
| POST deliverOaSubmission | M08 / BE-23 | OaSubmission | 200:OaLink | header:X-Request-Id |

星号为参数必填；条件必填的管理员上下文仍需结合当前身份校验。204为空响应，幂等内部缓存保存安全空结果及授权事实。error Problem、fieldErrors和认证安全头由公共适配统一处理，不能从成功模型数量推断错误语义。
