# UC—API—数据库追踪

基线：需求V1.3；原输入Git2b78430/API1.1.0，正式跨阶段评审输出API1.2.0及数据库1.1。逐操作表覆盖67入站+1出站，字段映射见[FIELD-MAPPING.md](FIELD-MAPPING.md)。T编号见[TRANSACTIONS.md](TRANSACTIONS.md)，I编号见[INVARIANTS.md](INVARIANTS.md)。操作/路径来自权威契约，表和事务映射经过设计判断，不是按API模型建表。

| 用例 | 方法/操作 | 路径 | 读写/依赖表 | 事务 | 不变量 |
| --- | --- | --- | --- | --- | --- |
| UC-09 | POST login | /api/v1/admin/sessions | admin_account, admin_session, employee, admin_appointment, management_scope, scope_system | T0,T9,T12 | I-04,I-20,I-22 |
| UC-09 | GET currentSession | /api/v1/admin/sessions/current | admin_account, admin_session, employee, admin_appointment, management_scope, scope_system | T9,T11 | I-04,I-20,I-22 |
| UC-09 | DELETE logout | /api/v1/admin/sessions/current | admin_account, admin_session, employee, admin_appointment, management_scope, scope_system | T0,T9,T12 | I-04,I-20,I-22 |
| UC-09 | PUT changeOwnPassword | /api/v1/admin/password | admin_account, admin_session, employee, admin_appointment, management_scope, scope_system | T0,T9,T12 | I-04,I-20,I-22 |
| UC-09 | GET myAppointments | /api/v1/admin/my-appointments | management_scope, scope_system, admin_appointment, admin_account, employee, employment | T9,T11 | I-03,I-04,I-20 |
| UC-09 | GET listAccounts | /api/v1/admin/accounts | admin_account, admin_session, employee, audit_event | T9,T11 | I-01,I-04,I-20 |
| UC-09 | POST createAccount | /api/v1/admin/accounts | admin_account, admin_session, employee, audit_event, http_idempotency | T0,T9 | I-01,I-04,I-16,I-20 |
| UC-09 | GET getAccount | /api/v1/admin/accounts/{accountId} | admin_account, admin_session, employee, audit_event | T9,T11 | I-01,I-04,I-20 |
| UC-09 | PUT setAccountState | /api/v1/admin/accounts/{accountId}/state | admin_account, admin_session, employee, audit_event, http_idempotency | T0,T9 | I-01,I-04,I-16,I-20 |
| UC-09 | PUT resetAccountPassword | /api/v1/admin/accounts/{accountId}/password | admin_account, admin_session, employee, audit_event, http_idempotency | T0,T9 | I-01,I-04,I-16,I-20 |
| UC-01 | GET listSystems | /api/v1/admin/systems | business_system, directory_node, permission, management_scope, scope_system | T9,T11 | I-05,I-06 |
| UC-01 | POST createSystem | /api/v1/admin/systems | business_system, directory_node, permission, role_permission, business_role, impact_preview, preview_item, audit_event, http_idempotency | T0,T1,T11 | I-05,I-06,I-16,I-18 |
| UC-01 | GET getSystem | /api/v1/admin/systems/{systemId} | business_system, directory_node, permission, management_scope, scope_system | T9,T11 | I-05,I-06 |
| UC-01 | PUT updateSystem | /api/v1/admin/systems/{systemId} | business_system, directory_node, permission, role_permission, business_role, impact_preview, preview_item, audit_event, http_idempotency | T0,T1,T11 | I-05,I-06,I-16,I-18 |
| UC-01 | POST systemStatePreview | /api/v1/admin/systems/{systemId}/state-previews | business_system, directory_node, permission, role_permission, business_role, impact_preview, preview_item, audit_event | T0,T11 | I-05,I-06,I-18 |
| UC-01 | GET listDirectoryNodes | /api/v1/admin/systems/{systemId}/directory-nodes | business_system, directory_node, permission, management_scope, scope_system | T9,T11 | I-05,I-06 |
| UC-01 | GET listPermissions | /api/v1/admin/systems/{systemId}/permissions | business_system, directory_node, permission, management_scope, scope_system | T9,T11 | I-05,I-06 |
| UC-01 | POST catalogPreview | /api/v1/admin/systems/{systemId}/catalog-previews | business_system, directory_node, permission, role_permission, business_role, impact_preview, preview_item, audit_event | T0,T11 | I-05,I-06,I-18 |
| UC-01 | POST saveCatalog | /api/v1/admin/systems/{systemId}/catalog-changes | business_system, directory_node, permission, role_permission, business_role, impact_preview, preview_item, audit_event, http_idempotency | T0,T1,T11 | I-05,I-06,I-16,I-18 |
| UC-02 | GET listRoles | /api/v1/admin/roles | business_role, role_permission, role_change_claim | T9,T11 | I-07,I-10 |
| UC-02 | POST createRole | /api/v1/admin/roles | business_role, change_request, change_target, role_change_claim, audit_event, http_idempotency | T0,T2 | I-01,I-07,I-10,I-16 |
| UC-02 | GET getRole | /api/v1/admin/roles/{roleId} | business_role, role_permission, role_change_claim | T9,T11 | I-07,I-10 |
| UC-02 | PUT updateRoleMetadata | /api/v1/admin/roles/{roleId}/metadata | business_role, change_request, change_target, role_change_claim, audit_event, http_idempotency | T0,T2 | I-01,I-07,I-10,I-16 |
| UC-03 | GET listEmployees | /api/v1/admin/employees | employee, employment, authorization_source, grant_baseline, role_grant, grant_employment_evidence, business_role | T9,T11 | I-03,I-08,I-09 |
| UC-03 | GET getEmployee | /api/v1/admin/employees/{employeeId} | employee, employment, authorization_source, grant_baseline, role_grant, grant_employment_evidence, business_role | T9,T11 | I-03,I-08,I-09 |
| UC-03 | GET listEmployments | /api/v1/admin/employees/{employeeId}/employments | employee, employment, authorization_source, grant_baseline, role_grant, grant_employment_evidence, business_role | T9,T11 | I-03,I-08,I-09 |
| UC-03,UC-04 | GET listGrants | /api/v1/admin/grants | employee, employment, authorization_source, grant_baseline, role_grant, grant_employment_evidence, business_role | T9,T11 | I-03,I-08,I-09 |
| UC-03 | POST grantBaselines | /api/v1/admin/grant-baselines | employee, employment, authorization_source, grant_baseline, role_grant, grant_employment_evidence, business_role | T9,T11 | I-03,I-08,I-09 |
| UC-02,UC-03,UC-04,UC-11 | GET listChangeRequests | /api/v1/admin/change-requests | change_request, change_target, oa_link, oa_final_result, execution_success, audit_event | T9,T11 | I-11,I-12,I-19 |
| UC-02,UC-03,UC-04 | POST createChangeDraft | /api/v1/admin/change-requests | change_request, change_target, role_change_claim, relation_change_claim, authorization_source, grant_baseline, impact_preview, preview_item, oa_outbox, audit_event, http_idempotency | T0,T2,T3,T11 | I-08,I-10,I-11,I-16,I-18 |
| UC-02,UC-03,UC-04,UC-11 | GET getChangeRequest | /api/v1/admin/change-requests/{requestId} | change_request, change_target, oa_link, oa_final_result, execution_success, audit_event | T9,T11 | I-11,I-12,I-19 |
| UC-02,UC-03,UC-04 | PUT updateChangeDraft | /api/v1/admin/change-requests/{requestId} | change_request, change_target, role_change_claim, relation_change_claim, authorization_source, grant_baseline, impact_preview, preview_item, oa_outbox, audit_event, http_idempotency | T0,T2,T3,T11 | I-08,I-10,I-11,I-16,I-18 |
| UC-02,UC-03,UC-04 | DELETE deleteChangeDraft | /api/v1/admin/change-requests/{requestId} | change_request, change_target, role_change_claim, relation_change_claim, authorization_source, grant_baseline, impact_preview, preview_item, oa_outbox, audit_event, http_idempotency | T0,T2,T3,T11 | I-08,I-10,I-11,I-16,I-18 |
| UC-02,UC-03,UC-04 | POST changePreview | /api/v1/admin/change-requests/{requestId}/previews | change_request, change_target, role_change_claim, relation_change_claim, authorization_source, grant_baseline, impact_preview, preview_item, oa_outbox, audit_event | T0,T11 | I-08,I-10,I-11,I-18 |
| UC-02,UC-03,UC-04 | POST submitChange | /api/v1/admin/change-requests/{requestId}/submissions | change_request, change_target, role_change_claim, relation_change_claim, authorization_source, grant_baseline, impact_preview, preview_item, oa_outbox, audit_event, http_idempotency | T0,T2,T3,T11 | I-08,I-10,I-11,I-16,I-18 |
| UC-11 | POST retryOaDelivery | /api/v1/admin/change-requests/{requestId}/delivery-retries | change_request, oa_outbox, oa_link, service_identity, audit_event, http_idempotency | T0,T10 | I-11,I-12,I-14,I-16 |
| UC-11 | GET listChangeEvents | /api/v1/admin/change-requests/{requestId}/events | change_request, change_target, oa_link, oa_final_result, execution_success, audit_event | T9,T11 | I-11,I-12,I-19 |
| UC-01,UC-02,UC-03,UC-04,UC-07,UC-10 | GET listPreviewEmployees | /api/v1/admin/previews/{previewId}/employees | impact_preview, preview_item, admin_account, admin_appointment, management_scope, scope_system | T9,T11 | I-18,I-20 |
| UC-04 | POST sourceBaselines | /api/v1/admin/source-baselines | employee, employment, authorization_source, grant_baseline, role_grant, grant_employment_evidence, business_role | T9,T11 | I-03,I-08,I-09 |
| UC-04 | POST sourceClosePreview | /api/v1/admin/source-closure-previews | authorization_source, grant_baseline, role_grant, change_request, change_target, relation_change_claim, impact_preview, preview_item, audit_event | T0,T11 | I-08,I-09,I-13,I-18 |
| UC-04 | POST closeSource | /api/v1/admin/source-closures | authorization_source, grant_baseline, role_grant, change_request, change_target, relation_change_claim, impact_preview, preview_item, audit_event, http_idempotency | T0,T5,T11 | I-08,I-09,I-13,I-16,I-18 |
| UC-07,UC-10 | GET listCompanies | /api/v1/admin/companies | company, department, position | T9,T11 | I-01,I-21 |
| UC-07,UC-10 | GET listDepartments | /api/v1/admin/departments | company, department, position | T9,T11 | I-01,I-21 |
| UC-07,UC-10 | GET listPositions | /api/v1/admin/positions | company, department, position | T9,T11 | I-01,I-21 |
| UC-10 | POST employeeStatePreview | /api/v1/admin/employees/{employeeId}/state-previews | employee, employment, authorization_source, grant_baseline, role_grant, admin_appointment, admin_account, change_request, change_target, impact_preview, preview_item, audit_event | T0,T11 | I-03,I-13,I-18,I-20 |
| UC-10 | PUT setEmployeeState | /api/v1/admin/employees/{employeeId}/state | employee, employment, authorization_source, grant_baseline, role_grant, admin_appointment, admin_account, change_request, change_target, impact_preview, preview_item, audit_event, http_idempotency | T0,T7,T11 | I-03,I-13,I-16,I-18,I-20 |
| UC-07 | GET listScopes | /api/v1/admin/management-scopes | management_scope, scope_system, admin_appointment, admin_account, employee, employment | T9,T11 | I-03,I-04,I-20 |
| UC-07 | POST createScope | /api/v1/admin/management-scopes | management_scope, scope_system, admin_appointment, employee, employment, admin_account, change_request, change_target, impact_preview, preview_item, audit_event, http_idempotency | T0,T6,T11 | I-04,I-12,I-16,I-18,I-20 |
| UC-07 | POST scopeCreatePreview | /api/v1/admin/management-scope-previews | management_scope, scope_system, admin_appointment, employee, employment, admin_account, change_request, change_target, impact_preview, preview_item, audit_event | T0,T11 | I-04,I-12,I-18,I-20 |
| UC-07 | GET getScope | /api/v1/admin/management-scopes/{scopeId} | management_scope, scope_system, admin_appointment, admin_account, employee, employment | T9,T11 | I-03,I-04,I-20 |
| UC-07 | PUT replaceScope | /api/v1/admin/management-scopes/{scopeId} | management_scope, scope_system, admin_appointment, employee, employment, admin_account, change_request, change_target, impact_preview, preview_item, audit_event, http_idempotency | T0,T6,T11 | I-04,I-12,I-16,I-18,I-20 |
| UC-07 | GET listScopeAppointments | /api/v1/admin/management-scopes/{scopeId}/appointments | management_scope, scope_system, admin_appointment, admin_account, employee, employment | T9,T11 | I-03,I-04,I-20 |
| UC-07 | POST scopeEditPreview | /api/v1/admin/management-scopes/{scopeId}/previews | management_scope, scope_system, admin_appointment, employee, employment, admin_account, change_request, change_target, impact_preview, preview_item, audit_event | T0,T11 | I-04,I-12,I-18,I-20 |
| UC-08 | GET listAudit | /api/v1/admin/audit-events | audit_event, audit_subject, management_scope, scope_system, admin_appointment | T9,T11 | I-19,I-20 |
| UC-08 | GET getAudit | /api/v1/admin/audit-events/{auditEventId} | audit_event, audit_subject, management_scope, scope_system, admin_appointment | T9,T11 | I-19,I-20 |
| UC-10 | POST uploadImportFile | /api/v1/admin/import-files | import_file, admin_account, audit_event, http_idempotency | T0,T8 | I-16,I-17,I-21 |
| UC-10 | GET getImportFile | /api/v1/admin/import-files/{fileId} | import_file, admin_account | T9,T11 | I-01,I-02,I-03,I-13,I-17,I-21 |
| UC-10 | POST precheckImport | /api/v1/admin/import-batches | import_file, import_batch, import_plan_row, import_issue, organization_mapping, employee_source_identity, company, department, position, employee, employment, authorization_source, grant_baseline, role_grant, admin_account, admin_appointment, change_request, change_target, audit_event, http_idempotency | T0,T8 | I-01,I-02,I-03,I-13,I-16,I-17,I-21 |
| UC-10 | GET listImportBatches | /api/v1/admin/import-batches | import_batch, import_file, admin_account | T9,T11 | I-01,I-02,I-03,I-13,I-17,I-21 |
| UC-10 | GET getImportBatch | /api/v1/admin/import-batches/{batchId} | import_batch, import_file, admin_account | T9,T11 | I-01,I-02,I-03,I-13,I-17,I-21 |
| UC-10 | GET listImportIssues | /api/v1/admin/import-batches/{batchId}/issues | import_batch, import_issue, admin_account | T9,T11 | I-01,I-02,I-03,I-13,I-17,I-21 |
| UC-10 | POST commitImport | /api/v1/admin/import-batches/{batchId}/commits | import_file, import_batch, import_plan_row, import_issue, organization_mapping, employee_source_identity, company, department, position, employee, employment, authorization_source, grant_baseline, role_grant, admin_account, admin_appointment, change_request, change_target, audit_event, http_idempotency | T0,T7,T8 | I-01,I-02,I-03,I-13,I-16,I-17,I-21 |
| UC-05 | POST authorizationQuery | /api/v1/integration/authorization-queries | service_identity, business_system, employee, employment, authorization_source, grant_baseline, role_grant, business_role, role_permission, permission, directory_node | T11 | I-03,I-05,I-06,I-07,I-08 |
| UC-06 | POST permissionCheck | /api/v1/integration/permission-checks | service_identity, business_system, employee, employment, authorization_source, grant_baseline, role_grant, business_role, role_permission, permission, directory_node | T11 | I-03,I-05,I-06,I-07,I-08 |
| UC-11 | POST receiveOaResult | /api/v1/oa/final-results | service_identity, change_request, oa_link, oa_callback_event, oa_final_result, oa_outbox, execution_success, execution_attempt, audit_event | T0,T10 | I-11,I-12,I-14,I-15 |
| UC-10 | GET listImportPlanChanges | /api/v1/admin/import-batches/{batchId}/changes | import_batch, import_plan_row, admin_account | T9,T11 | I-01,I-02,I-03,I-13,I-17,I-21 |
| UC-01,UC-02,UC-03,UC-04,UC-07,UC-10 | GET listPreviewTargets | /api/v1/admin/previews/{previewId}/targets | impact_preview, preview_item, admin_account, admin_appointment, management_scope, scope_system | T9,T11 | I-18,I-20 |
| UC-02,UC-03,UC-04,UC-11 | POST deliverOaSubmission | oaSubmission | change_request, oa_outbox, oa_link, service_identity, audit_event | T0,T10 | I-11,I-12,I-14 |

批准后自动执行由T4映射到UC-02/03/04/11；它没有管理员执行API，不为追踪新增公开接口。执行读写还涉及role_permission、role_configuration_history、role_grant、grant_employment_evidence、baseline、claims及success，完整事务见T4。

全部写操作另依赖mutation_guard；HTTP写接口按契约例外决定幂等（登录/本人改密/退出、预览、只读POST和OA回调无需管理员key；OA独立防重、出站用requestId），不能因表中列出http_idempotency就改变契约。列表、详情及计数统一当前范围过滤；预览和审计原来源不是业务数据权限。

关联验收：DR-01/02/03→I-04/07/12/18；DR-04～06→I-08/09/13/15；DR-07/08→I-13/20；DR-09→无公司/部门状态列；DR-10→I-11/14；R-01～03→T1/T3/T11。API原型缺口仍按D-029后续跟踪，本轮未修改原型。

读操作仅映射T9/T11，不执行领域写事务；预览只写快照，T0/T11不提交正式变更。OA接收T10可靠落库，T4在独立执行事务运行。http_idempotency及I-16仅映射实际声明Idempotency参数的入站管理员操作。

表列为主要持久化/领域读取依赖，不是每次都执行的SQL清单；当前账号/员工/会话/单项资格仍按T9统一复核，T列区分实际事务协议，不因列出领域表就写入该表。文件上传只持久化文件元数据/安全结果，不执行人员或组织导入。
