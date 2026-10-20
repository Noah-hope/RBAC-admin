# UC—API—模块—任务—验收追踪

D-085实际交付（2026-10-20）：成员D明确“合并推送吧”，BE-29接入A已交付BE-09后，来源db3419687f8dc43b1959564ddc19b0e7d32826ed已无冲突no-ff合入远程main/6506cda54c4898e736a2e730ba001294f8de4c2c，合并树等于已完整验证来源，任务分支codex/be-29-import-precheck保留。普通Git连接连续失败，改用官方GitHub Git API按本地对象原始SHA、force=false推送main和任务分支，远程两个完整SHA均回查一致。完整137离线＋240真实MySQL/HTTP及静态校验通过，专属资源清理关闭，原MySQL84仍Running；BE-29为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，既有人工验收保持。D原实施编号D-081与A冲突已统一为D-084；A的D-081/082/083及PR#3交付保留。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED；不强推或部署，下方未授权/待主线为历史。

D-084集成结果（2026-10-20）：接入远程main/06a6b5c（A已验收并经PR#3交付的BE-09）后，完整clean verify/mysql-check通过，137项离线/20组＋240项真实MySQL/HTTP/19组，失败/错误/跳过0，日志backend/.tmp/be29-main-integration-verify.log。集成未改BE-29业务代码，静态追踪通过；BE-29保持IMPLEMENTED待D人工验收，本次主线交付由用户“合并推送吧”明确授权，实际SHA成功后补。

D-084编号协调（2026-10-20）：BE-29原本地实施记录D-081与远程A/BE-09重复，现统一为D-084；原实现80abde1及真实RED/GREEN事实保留，A的D-081/082/083、验收与PR#3交付均保持。已接入main/06a6b5c，业务代码无冲突；BE-29仍IMPLEMENTED、ownerAccepted=false，待集成回归与本次授权的主线交付。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED。

D-084（2026-10-20）：D要求下一项，BE-29在codex/be-29-import-precheck（main/072585e）按设计/TDD实现五个预检/报告操作，冻结候选ID-code、规范差异、映射、动作顺序及依赖，不改正式人员/授权数据。完整137离线＋228真实MySQL/HTTP通过，失败/错误/跳过0；随机库/账号/临时密钥文件目录0，专属实例正常关闭，原MySQL84仍Running。BE-29为IMPLEMENTED、ownerAccepted=false、mergedToMain=false；AT-25/26/27/29仅补预检局部证据，BE-26仍等C/BE-20，BE-30仍等A/BE-27及本任务主线交付。共9项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、16项PLANNED；既有验收状态保持，不合并推送或自动开新任务。下方旧状态/数量为历史，见[BE-29](BE-29-IMPORT-PRECHECK.md)。

D-083实际交付（2026-10-20）：BE-09已通过[PR #3](https://github.com/Noah-hope/RBAC-admin/pull/3)合入远程main，来源0fee462503274fbed1ab03826c8d0723c9825c19，合并5f42a2cff11d68c49a4926373eeee97e3f5e8889。已回查closed/merged=true、远程完整SHA、双亲及合并树等于验证来源，本地main已同步、任务分支保留；OWNER_ACCEPTED、mergedToMain=true、remotePrNumber=3。接入BE-16后完整112普通＋227真实MySQL/HTTP及静态校验通过，专属资源清理关闭，原MySQL84仍Running；远程检查条目0，不记为CI通过。本轮交付不部署或开新任务，D各任务验收不代确认；下方待授权/待PR/待主线为历史。

D-082（2026-10-20）：A明确“测试通过即可”，按已有112离线＋205真实MySQL/HTTP证据确认BE-09/41931f4，登记OWNER_ACCEPTED、待主线交付；未做本机演示，不虚构人工操作通过。远程最新072585e已交付D/BE-16，但本任务分支尚未整合该增量，BE-26及BE-33仍PLANNED。下一步建议交付BE-09；A余下BE-27等D/BE-26、BE-34等B/BE-33，不自动开工或推送。原本地BE-09编号D-079与远程D/BE-16冲突，统一为D-081，保留双方事实；下方待A确认/旧数量为历史。

D-081（2026-10-20）：A按已交付BE-05/08/17开始BE-09，创建预览及范围/系统集合/任命的原子保存已实现，使用JPA写仓储、BE-17固定预览与公共guard/幂等/审计。完整112离线＋205真实MySQL/HTTP及静态校验通过；当前IN_PROGRESS待A本人确认，未交付main；验证和本机步骤见[BE-09](BE-09-SCOPE-CREATION.md)。共9项OWNER_ACCEPTED、3项IMPLEMENTED、6项IN_PROGRESS、17项PLANNED，其他成员人工验收状态不变；AT-08/31/37只补本任务部分证据，不代表BE-27/33完成。下方旧状态/数量为历史。

D-080（2026-10-20）：D明确“先完成合并推送”，BE-16来源dba2eaa已无冲突保留历史合入远程main/7ab021a，合并树等于已验证来源，任务分支推送并保留，远程完整SHA回查一致。核对112离线＋215真实MySQL/HTTP报告与jar摘要一致，本轮静态复核通过，无Java/SQL重跑或部署。BE-16为IMPLEMENTED、mergedToMain=true、ownerAccepted=false；BE-14/28人工验收继续开放。当前9项OWNER_ACCEPTED、4项IMPLEMENTED、5项IN_PROGRESS、17项PLANNED；下方未授权/待主线为历史，不自动开新任务。见[BE-16](BE-16-GRANT-MUTATIONS.md)。

D-079（2026-10-20）：D要求开始下一项，BE-16在codex/be-16-grant-mutations（基线main/a860dc8）按设计/TDD实现BaselineWriter、GrantApplier、SourceCloser及AffectedFacts。完整112离线＋215真实MySQL/HTTP通过，失败/错误/跳过0；专属实例正常关闭，随机库/账号/测试密钥目录0，用户原MySQL84仍Running。BE-16为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，未合并推送；BE-14/28仍已交付待本人确认。AT-14/15/22/41只补底层局部证据，不代表C执行器、真实OA或BE-26生命周期完成。当前9项OWNER_ACCEPTED、4项IMPLEMENTED、5项IN_PROGRESS、17项PLANNED，下方旧数量/边界为历史。见[BE-16](BE-16-GRANT-MUTATIONS.md)。

D-078（2026-10-20）：D明确要求先合并推送，BE-14来源367cdae已保留历史合入远程main/c7435d9，分支已推送并保留，远程完整SHA回查一致，合并树等于验证来源。沿用完整112离线＋193真实MySQL/HTTP证据，本轮静态检查通过，未重复执行Java/SQL。BE-14仍为IMPLEMENTED、ownerAccepted=false、mergedToMain=true，BE-28仍已交付待本人确认；下方未授权/待主线为历史。AT-13补本任务证据，AT-14/15仅读取局部验证，BE-16/26执行竞争不在本任务。当前共9项OWNER_ACCEPTED、3项IMPLEMENTED、5项IN_PROGRESS、18项PLANNED，下方旧状态/数量为历史。见[BE-14](BE-14-GRANT-BASELINES.md)。

D-076（2026-10-20）：BE-28 已接入最新 BE-17/12 后完整110离线＋178真实MySQL/HTTP通过，并由233f633保留历史合入远程main/4b384fb，远程SHA回查一致，分支保留。Git连接失败后使用官方API按本地SHA原样交付、force=false。BE-28仍待本人确认，mergedToMain=true；交付不代记人工验收。当前共9项OWNER_ACCEPTED、2项IMPLEMENTED、5项IN_PROGRESS、19项PLANNED。D原BE-28实施编号因与B冲突改为D-075，原事实保留；下方旧数量/未授权为历史。见 [BE-28](BE-28-IMPORT-FILES.md)。

D-074（2026-10-20）B 状态：BE-17/12 已由 1fb95ad 无冲突合入并交付远程 main/3103b35，任务分支保留，完整 SHA 回查一致，合并树等于已验证源。完整 93 普通＋169 真实 MySQL/HTTP 通过，本轮合并后 93 普通测试再次通过。任务待本人确认，不代记 OWNER_ACCEPTED。BE-11/13/15 原交付保留；共 9 项 OWNER_ACCEPTED、1 项 IMPLEMENTED、5 项 IN_PROGRESS、20 项 PLANNED。AT-09/10/11/12/31/32 仅局部验证，不代表全部消费者/OA/BE-33 完成。BE-18 仍缺 BE-14，BE-33/35 仍开放。详见 [BE-17/12 实施及交付](BE-17-12-PREVIEW-CATALOG.md)，下方更早数量/待交付说明为历史。

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
