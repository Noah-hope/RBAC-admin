# UC—API—模块—任务—验收追踪

D-072（2026-10-20）B 状态：BE-11/13/15 已实现并合入远程 main/0e391c6，来源 b92cdbb，任务仍为 IN_PROGRESS（待负责人确认）。完整 85 普通＋150 真实 MySQL 检查及设计静态校验通过；6 个 B 入站操作已交付。当前共 9 项 OWNER_ACCEPTED、1 项 IMPLEMENTED、3 项 IN_PROGRESS、22 项 PLANNED。AT-09/11/12 仅局部验证，AT-37 补 B 接口证据。BE-17 技术依赖已就绪但未实现；BE-12 等 BE-17，BE-18 仍缺 BE-14。详见 [B 实施记录](BE-11-13-15-B-MODULE.md)及[集成交付](B-MAIN-INTEGRATION.md)，更早日期的数量/待交付说明为历史。

输入：需求V1.3/D-033、API1.2.0、数据库1.1；交接提交bd26465。契约SHA-256 `9910ceafadea88d85793d00f7bb4e6e5673700a8565366a407185c4517e0cbdf`。全部68操作各有唯一主责任务；按D-055登记A～D成员代号，实名/账号待补。操作、表及T/I不变，BE-06的4个操作已获负责人确认（D-057），已由D-060交付main，BE-07的5个操作已本人确认（D-059），已由D-060交付main，BE-10的6个操作已实现并交付main待D确认，BE-08的4个操作已获A确认（D-068）并由D-069/PR#2交付main，其余49项待实现；启动仍须满足门槛和原依赖。

| UC | 方法/operationId | 路径/方向 | 模块/负责人 | 本地Issue | 验收 | 事务 |
| --- | --- | --- | --- | --- | --- | --- |
| UC-09 | POST login | /api/v1/admin/sessions / INBOUND | M01 / A | BE-06 | AT-05,AT-06,AT-07,AT-37 | T0,T9,T12 |
| UC-09 | GET currentSession | /api/v1/admin/sessions/current / INBOUND | M01 / A | BE-06 | AT-05,AT-06,AT-07,AT-37 | T9,T11 |
| UC-09 | DELETE logout | /api/v1/admin/sessions/current / INBOUND | M01 / A | BE-06 | AT-05,AT-06,AT-07,AT-37 | T0,T9,T12 |
| UC-09 | PUT changeOwnPassword | /api/v1/admin/password / INBOUND | M01 / A | BE-06 | AT-05,AT-06,AT-07,AT-37 | T0,T9,T12 |
| UC-09 | GET myAppointments | /api/v1/admin/my-appointments / INBOUND | M03 / A | BE-08 | AT-08,AT-19,AT-37 | T9,T11 |
| UC-09 | GET listAccounts | /api/v1/admin/accounts / INBOUND | M01 / A | BE-07 | AT-06,AT-07,AT-37 | T9,T11 |
| UC-09 | POST createAccount | /api/v1/admin/accounts / INBOUND | M01 / A | BE-07 | AT-06,AT-07,AT-37 | T0,T9 |
| UC-09 | GET getAccount | /api/v1/admin/accounts/{accountId} / INBOUND | M01 / A | BE-07 | AT-06,AT-07,AT-37 | T9,T11 |
| UC-09 | PUT setAccountState | /api/v1/admin/accounts/{accountId}/state / INBOUND | M01 / A | BE-07 | AT-06,AT-07,AT-37 | T0,T9 |
| UC-09 | PUT resetAccountPassword | /api/v1/admin/accounts/{accountId}/password / INBOUND | M01 / A | BE-07 | AT-06,AT-07,AT-37 | T0,T9 |
| UC-01 | GET listSystems | /api/v1/admin/systems / INBOUND | M04 / B | BE-11 | AT-09,AT-37 | T9,T11 |
| UC-01 | POST createSystem | /api/v1/admin/systems / INBOUND | M04 / B | BE-11 | AT-09,AT-37 | T0,T1,T11 |
| UC-01 | GET getSystem | /api/v1/admin/systems/{systemId} / INBOUND | M04 / B | BE-11 | AT-09,AT-37 | T9,T11 |
| UC-01 | PUT updateSystem | /api/v1/admin/systems/{systemId} / INBOUND | M04 / B | BE-12 | AT-09,AT-10,AT-31,AT-37 | T0,T1,T11 |
| UC-01 | POST systemStatePreview | /api/v1/admin/systems/{systemId}/state-previews / INBOUND | M04 / B | BE-12 | AT-09,AT-10,AT-31,AT-37 | T0,T11 |
| UC-01 | GET listDirectoryNodes | /api/v1/admin/systems/{systemId}/directory-nodes / INBOUND | M04 / B | BE-12 | AT-09,AT-10,AT-31,AT-37 | T9,T11 |
| UC-01 | GET listPermissions | /api/v1/admin/systems/{systemId}/permissions / INBOUND | M04 / B | BE-12 | AT-09,AT-10,AT-31,AT-37 | T9,T11 |
| UC-01 | POST catalogPreview | /api/v1/admin/systems/{systemId}/catalog-previews / INBOUND | M04 / B | BE-12 | AT-09,AT-10,AT-31,AT-37 | T0,T11 |
| UC-01 | POST saveCatalog | /api/v1/admin/systems/{systemId}/catalog-changes / INBOUND | M04 / B | BE-12 | AT-09,AT-10,AT-31,AT-37 | T0,T1,T11 |
| UC-02 | GET listRoles | /api/v1/admin/roles / INBOUND | M05 / B | BE-13 | AT-11,AT-12,AT-37 | T9,T11 |
| UC-02 | POST createRole | /api/v1/admin/roles / INBOUND | M07 / C | BE-20 | AT-11,AT-13,AT-16,AT-17,AT-37 | T0,T2 |
| UC-02 | GET getRole | /api/v1/admin/roles/{roleId} / INBOUND | M05 / B | BE-13 | AT-11,AT-12,AT-37 | T9,T11 |
| UC-02 | PUT updateRoleMetadata | /api/v1/admin/roles/{roleId}/metadata / INBOUND | M05 / B | BE-13 | AT-11,AT-12,AT-37 | T0,T2 |
| UC-03 | GET listEmployees | /api/v1/admin/employees / INBOUND | M02 / D | BE-10 | AT-13,AT-27,AT-29,AT-37 | T9,T11 |
| UC-03 | GET getEmployee | /api/v1/admin/employees/{employeeId} / INBOUND | M02 / D | BE-10 | AT-13,AT-27,AT-29,AT-37 | T9,T11 |
| UC-03 | GET listEmployments | /api/v1/admin/employees/{employeeId}/employments / INBOUND | M02 / D | BE-10 | AT-13,AT-27,AT-29,AT-37 | T9,T11 |
| UC-03,UC-04 | GET listGrants | /api/v1/admin/grants / INBOUND | M06 / D | BE-14 | AT-13,AT-14,AT-15,AT-37 | T9,T11 |
| UC-03 | POST grantBaselines | /api/v1/admin/grant-baselines / INBOUND | M06 / D | BE-14 | AT-13,AT-14,AT-15,AT-37 | T9,T11 |
| UC-02,UC-03,UC-04,UC-11 | GET listChangeRequests | /api/v1/admin/change-requests / INBOUND | M07 / C | BE-22 | AT-17,AT-18,AT-19,AT-34,AT-37 | T9,T11 |
| UC-02,UC-03,UC-04 | POST createChangeDraft | /api/v1/admin/change-requests / INBOUND | M07 / C | BE-20 | AT-11,AT-13,AT-16,AT-17,AT-37 | T0,T2,T3,T11 |
| UC-02,UC-03,UC-04,UC-11 | GET getChangeRequest | /api/v1/admin/change-requests/{requestId} / INBOUND | M07 / C | BE-22 | AT-17,AT-18,AT-19,AT-34,AT-37 | T9,T11 |
| UC-02,UC-03,UC-04 | PUT updateChangeDraft | /api/v1/admin/change-requests/{requestId} / INBOUND | M07 / C | BE-20 | AT-11,AT-13,AT-16,AT-17,AT-37 | T0,T2,T3,T11 |
| UC-02,UC-03,UC-04 | DELETE deleteChangeDraft | /api/v1/admin/change-requests/{requestId} / INBOUND | M07 / C | BE-20 | AT-11,AT-13,AT-16,AT-17,AT-37 | T0,T2,T3,T11 |
| UC-02,UC-03,UC-04 | POST changePreview | /api/v1/admin/change-requests/{requestId}/previews / INBOUND | M07 / C | BE-21 | AT-12,AT-13,AT-16,AT-18,AT-37 | T0,T11 |
| UC-02,UC-03,UC-04 | POST submitChange | /api/v1/admin/change-requests/{requestId}/submissions / INBOUND | M07 / C | BE-21 | AT-12,AT-13,AT-16,AT-18,AT-37 | T0,T2,T3,T11 |
| UC-11 | POST retryOaDelivery | /api/v1/admin/change-requests/{requestId}/delivery-retries / INBOUND | M08 / C | BE-23 | AT-20,AT-35,AT-37 | T0,T10 |
| UC-11 | GET listChangeEvents | /api/v1/admin/change-requests/{requestId}/events / INBOUND | M07 / C | BE-22 | AT-17,AT-18,AT-19,AT-34,AT-37 | T9,T11 |
| UC-01,UC-02,UC-03,UC-04,UC-07,UC-10 | GET listPreviewEmployees | /api/v1/admin/previews/{previewId}/employees / INBOUND | M11 / B | BE-17 | AT-31,AT-32,AT-37 | T9,T11 |
| UC-04 | POST sourceBaselines | /api/v1/admin/source-baselines / INBOUND | M06 / D | BE-14 | AT-13,AT-14,AT-15,AT-37 | T9,T11 |
| UC-04 | POST sourceClosePreview | /api/v1/admin/source-closure-previews / INBOUND | M10 / D | BE-26 | AT-15,AT-16,AT-19,AT-32,AT-37,AT-41 | T0,T11 |
| UC-04 | POST closeSource | /api/v1/admin/source-closures / INBOUND | M10 / D | BE-26 | AT-15,AT-16,AT-19,AT-32,AT-37,AT-41 | T0,T5,T11 |
| UC-07,UC-10 | GET listCompanies | /api/v1/admin/companies / INBOUND | M02 / D | BE-10 | AT-13,AT-27,AT-29,AT-37 | T9,T11 |
| UC-07,UC-10 | GET listDepartments | /api/v1/admin/departments / INBOUND | M02 / D | BE-10 | AT-13,AT-27,AT-29,AT-37 | T9,T11 |
| UC-07,UC-10 | GET listPositions | /api/v1/admin/positions / INBOUND | M02 / D | BE-10 | AT-13,AT-27,AT-29,AT-37 | T9,T11 |
| UC-10 | POST employeeStatePreview | /api/v1/admin/employees/{employeeId}/state-previews / INBOUND | M10 / A | BE-27 | AT-08,AT-19,AT-24,AT-31,AT-37 | T0,T11 |
| UC-10 | PUT setEmployeeState | /api/v1/admin/employees/{employeeId}/state / INBOUND | M10 / A | BE-27 | AT-08,AT-19,AT-24,AT-31,AT-37 | T0,T7,T11 |
| UC-07 | GET listScopes | /api/v1/admin/management-scopes / INBOUND | M03 / A | BE-08 | AT-08,AT-19,AT-37 | T9,T11 |
| UC-07 | POST createScope | /api/v1/admin/management-scopes / INBOUND | M03 / A | BE-09 | AT-08,AT-31,AT-37 | T0,T6,T11 |
| UC-07 | POST scopeCreatePreview | /api/v1/admin/management-scope-previews / INBOUND | M03 / A | BE-09 | AT-08,AT-31,AT-37 | T0,T11 |
| UC-07 | GET getScope | /api/v1/admin/management-scopes/{scopeId} / INBOUND | M03 / A | BE-08 | AT-08,AT-19,AT-37 | T9,T11 |
| UC-07 | PUT replaceScope | /api/v1/admin/management-scopes/{scopeId} / INBOUND | M10 / A | BE-27 | AT-08,AT-19,AT-24,AT-31,AT-37 | T0,T6,T11 |
| UC-07 | GET listScopeAppointments | /api/v1/admin/management-scopes/{scopeId}/appointments / INBOUND | M03 / A | BE-08 | AT-08,AT-19,AT-37 | T9,T11 |
| UC-07 | POST scopeEditPreview | /api/v1/admin/management-scopes/{scopeId}/previews / INBOUND | M10 / A | BE-27 | AT-08,AT-19,AT-24,AT-31,AT-37 | T0,T11 |
| UC-08 | GET listAudit | /api/v1/admin/audit-events / INBOUND | M11 / C | BE-19 | AT-32,AT-34,AT-37 | T9,T11 |
| UC-08 | GET getAudit | /api/v1/admin/audit-events/{auditEventId} / INBOUND | M11 / C | BE-19 | AT-32,AT-34,AT-37 | T9,T11 |
| UC-10 | POST uploadImportFile | /api/v1/admin/import-files / INBOUND | M10 / D | BE-28 | AT-26,AT-37 | T0,T8 |
| UC-10 | GET getImportFile | /api/v1/admin/import-files/{fileId} / INBOUND | M10 / D | BE-28 | AT-26,AT-37 | T9,T11 |
| UC-10 | POST precheckImport | /api/v1/admin/import-batches / INBOUND | M10 / D | BE-29 | AT-25,AT-26,AT-27,AT-29,AT-37 | T0,T8 |
| UC-10 | GET listImportBatches | /api/v1/admin/import-batches / INBOUND | M10 / D | BE-29 | AT-25,AT-26,AT-27,AT-29,AT-37 | T9,T11 |
| UC-10 | GET getImportBatch | /api/v1/admin/import-batches/{batchId} / INBOUND | M10 / D | BE-29 | AT-25,AT-26,AT-27,AT-29,AT-37 | T9,T11 |
| UC-10 | GET listImportIssues | /api/v1/admin/import-batches/{batchId}/issues / INBOUND | M10 / D | BE-29 | AT-25,AT-26,AT-27,AT-29,AT-37 | T9,T11 |
| UC-10 | POST commitImport | /api/v1/admin/import-batches/{batchId}/commits / INBOUND | M10 / D | BE-30 | AT-24,AT-25,AT-27,AT-28,AT-29,AT-37,AT-41 | T0,T7,T8 |
| UC-05 | POST authorizationQuery | /api/v1/integration/authorization-queries / INBOUND | M11 / B | BE-18 | AT-07,AT-10,AT-30,AT-33,AT-37 | T11 |
| UC-06 | POST permissionCheck | /api/v1/integration/permission-checks / INBOUND | M11 / B | BE-18 | AT-07,AT-10,AT-30,AT-33,AT-37 | T11 |
| UC-11 | POST receiveOaResult | /api/v1/oa/final-results / INBOUND | M08 / C | BE-24 | AT-07,AT-20,AT-21,AT-37 | T0,T10 |
| UC-10 | GET listImportPlanChanges | /api/v1/admin/import-batches/{batchId}/changes / INBOUND | M10 / D | BE-29 | AT-25,AT-26,AT-27,AT-29,AT-37 | T9,T11 |
| UC-01,UC-02,UC-03,UC-04,UC-07,UC-10 | GET listPreviewTargets | /api/v1/admin/previews/{previewId}/targets / INBOUND | M11 / B | BE-17 | AT-31,AT-32,AT-37 | T9,T11 |
| UC-02,UC-03,UC-04,UC-11 | POST deliverOaSubmission | oaSubmission / OUTBOUND | M08 / C | BE-23 | AT-20,AT-35 | T0,T10 |

自动执行无管理员API，主责M09/BE-25；源码/成功凭据/状态事务设计见数据库T4及本目录流程。operation-map.json另保留逐操作表/I编号；本表不是已实现接口清单。
