# API字段与持久化映射

权威契约1.2.0（正式跨阶段评审修订，原输入1.1.0）。API响应是当前联结/安全投影，不能把每个字段都机械建列。所有API `id` 对应稳定UUID字符串，所有createdAt/occurredAt/到期时间对应UTC DATETIME(6)，BINARY(32)摘要输出64位小写hex；分页page/pageSize/total是范围过滤后的查询结果。

| API资源/字段 | 持久化来源或生成方式 |
| --- | --- |
| Company.id/code/name/version | company同名列；公司无enabled/status/parent字段 |
| Department.id/companyId/code/name/version | department.id/company_id/code/name/version；没有部门parent/status |
| Position.id/departmentId/code/name/version | position同名映射；公司通过department取得 |
| Employee.id/employeeKey/name/status/version/createdAt | employee.id/employee_key/name/status/version/created_at；employeeKey不是loginName |
| Employment.id/employeeId/companyId/departmentId/positionId/status/version | employment对应字段；归属受复合FK约束 |
| Employment.startedAt/endedAt | employment.started_at/ended_at可空，来自明确源日期；recorded_end_at仅内部处理时间，不伪造源日期 |
| Account.id/employeeId/loginName/enabled/mustChangePassword/version/createdAt | admin_account对应列；employeeId对部署总管理员可空；不输出password_hash/is_super_admin/credential_version/session_epoch |
| Session/SessionView.sessionId/accountId/employeeId/identity/mode/expiresAt | admin_session.id/account_id/mode/expires_at；employeeId联结账号；identity由当前总管理员身份/任命判定，不存业务Role |
| Session.accessToken/tokenType | 认证后签发JWT/常量Bearer；不存JWT原文；sid/jti及登录凭据版本/会话周期持久化 |
| 登录/创建/改密/重置密码输入 | 原样校验专用密码哈希，只写admin_account.password_hash；确认值不存；幂等仅存受保护HMAC，安全响应不含密码/哈希 |
| Application.id/code/name/description/enabled/version/catalogVersion/createdAt | business_system对应列 |
| Application.systemAccessPermissionId | permission按system_id/access_slot=1唯一定位；缺项完整性错误，不能返回默认放行 |
| DirectoryNode.id/systemId/code/name/nodeType/parentId/sortOrder/enabled/visibilityPermissionId/version | directory_node对应列；新增本地引用localKey只在本次命令解析/createdIds及幂等响应中保留，无永久localKey列 |
| Permission.id/systemId/code/name/permissionType/functionId/menuEligible/enabled/version | permission对应列；SYSTEM_ACCESS.functionId=NULL，menuEligible=false；function必须属于同系统FUNCTION |
| Role.id/code/name/description/companyId/departmentId/systemId/state/createdAt | business_role对应列；departmentId=NULL表示公司级共享角色，不表示无来源授权 |
| Role.metadataVersion/configurationVersion | business_role.metadata_version/configuration_version，互不替代 |
| Role.permissionIds | 当前role_permission集合，UNCONFIGURED为空，EFFECTIVE非空；不是history JSON |
| Role.activeChangeRequestId | role_change_claim.request_id；释放通道不删除申请历史 |
| SourceKey.employeeId/companyId/departmentId/systemId | authorization_source四元唯一键；只读baseline接口可对不存在行返回逻辑0，不必因GET/查询而写库 |
| SourceBaseline.sourceEpoch/version | authorization_source.source_epoch/source_version；缺失逻辑0，T0写事务物化后比较 |
| GrantKey及GrantBaseline.key/systemId | source联结baseline.role_id，company/system通过来源/角色复合约束取得 |
| GrantBaseline.roleConfigurationVersion/sourceEpoch/relationVersion | business_role.configuration_version、source.source_epoch、baseline.relation_version；不存在baseline关系版本0 |
| GrantBaseline.employmentIds/activeGrantId | 当前适用任职集合/该baseline.active_slot=1的grant.id，可空；适用任职不是自动授权 |
| Grant.id/status/endedReason/createdAt | role_grant.id/status/ended_reason/created_at |
| Grant.employeeId/roleId/companyId/sourceDepartmentId/systemId | grant→baseline→source/role联结；固定来源不按当前员工部门重新推断 |
| Grant.employmentIds/sourceEpoch/relationVersion | grant_employment_evidence、role_grant.source_epoch/relation_version；已结束旧grant版本不跟着新grant更新 |
| Scope.id/companyId/departmentId/version/createdAt | management_scope对应列，API version对应scope_version |
| Scope.systemIds | scope_system集合，允许空；账号名单不塞入此数组 |
| Appointment.id/scopeId/accountId/status/version | admin_appointment对应列；version对应appointment_version |
| Appointment.employeeId/scopeVersion/companyId/departmentId/systemIds | 当前账号员工及当前management_scope/scope_system联结；历史原任命依据另在固定申请快照 |
| ChangeRequest.id/content/reason/draftVersion/submittedVersion/submittedAt/statusVersion | change_request对应列；content固定时为有效明细，草稿允许空配置；提交时间参与规范字节摘要 |
| ChangeRequest.contentDigest/originalAuthority/submittedByAccountId | content_digest/original_authority/submitted_by_account_id；后端构造原资格，客户端不能覆写 |
| ChangeRequest.state/deliveryStatus/approvalResult/executionStatus | change_request三轴及受约束state；状态分支是响应模型，没有七套数据库申请表 |
| ChangeRequest.oaApprovalId | oa_link.oa_approval_id；LINKED时必须有关联；与OA实例共同唯一 |
| ChangeRequest.retryable/failureCode/nextRetryAt/createdAt | retryable从APPROVED或EXECUTION_FAILED派生；其余列直接映射；不是管理员执行按钮 |
| ChangeContent/GrantLine/RevokeLine/RoleConfiguration | change_request.content保存完整规范形状；change_target保存可检索的角色/来源/准确grant及期望版本投影；配置permissionIds在固定内容中，不提前写role_permission |
| Authority.GLOBAL/SCOPE | original_authority保存契约完整固定字段；scope_id/appointment_id是可检索原历史FK；相应版本快照不被当前scopeVersion覆盖 |
| copiedFromRequestId | 复制来源作为新草稿创建审计快照保存，不建立可变内容继承；不影响新申请独立ID/审批 |
| ChangePreview.effectiveContent/noChangeItems/addedPermissionIds/removedPermissionIds/draftId/draftVersion | impact_preview.result_snapshot，和summary/targets同一计算快照；无变化时effectiveContent=null；不创建空授予申请 |
| Preview.id/contentDigest/expiresAt/impact/warnings | impact_preview.id/content_digest/expires_at/result_snapshot；不是永久业务授权事实 |
| ImpactSummary各可见分类计数、visibility/hasUndisclosedImpact/warning | 安全过滤后的固定result_snapshot；隐藏数量不保存到可返回投影；不能把SCOPED计数说成全局计数 |
| ImpactEmployee.employeeId/companyId/systemId/canAccessBefore/canAccessAfter/resultCompleteness/visibleGrantIds | preview_item.safe_item，item_kind=EMPLOYEE；结论依据完整有效来源，解释明细仅有权部分 |
| ImpactTarget各类型稳定ID/版本/拟影响 | preview_item.safe_item，SOURCE使用完整四元键；visibility_basis用于当前边界复核，无多态业务目标FK |
| AuditEvent.id/occurredAt/eventType/companyId/sourceDepartmentId/systemId/actorId/targetId/result/changeRequestId/reason | audit_event对应列；requestId对应correlation_id（HTTP关联号），不能与changeRequestId混淆 |
| AuditDetail.differences/originalSubmitterId/oaApprovalId/submittedVersion/failureCode | event_snapshot当时差异/审批版本/错误；original_submitter_id可直接关联但显示名仍快照 |
| ChangeEvent.id/requestId/occurredAt/eventType/actorId/message | audit_event按当前可见整份申请筛选事件投影，requestId在此模型是change_request_id；event_snapshot提供安全message。DRAFT删除后普通GET申请404，历史事件仍可查 |
| OaSubmission全字段 | 从change_request固定规范字节/固定列读出，不用当前角色名称/权限重建；oa_outbox只保存待办与关联对象 |
| OaLink.requestId/submittedVersion/contentDigest/oaApprovalId/acceptedAt | oa_link及固定申请联合校验/存储；不因回调自报审批号创建关联 |
| OaFinalResult.eventId/requestId/oaApprovalId/submittedVersion/contentDigest/decision/isFinal/decidedAt/comment | callback_event.safe_payload/摘要+oa_final_result决定；isFinal仅接受true，不是可变状态列；审批号按oa_link匹配 |
| OaReceipt各接收/重复/执行字段 | 回调防重记录、最终结果及当前申请执行状态联结；202仅表示可靠接收，不表示已执行 |
| ImportFile.id/fileName/sha256/sizeBytes/sheetNames/createdAt | import_file对应列；storage_ref仅内部服务端读，不当作公开任意下载URL |
| ImportBatch.id/input/version/contentDigest/state/plannedCounts/actualCounts/blockingIssueCount/warningCount/failureCode/createdAt | import_batch对应列；input_snapshot完整保存ImportInput，包括fileId/sourceSystem/sourceBatchId/importType/worksheet/fieldMappings/organizationMappings/initialEmploymentState/reason |
| ImportPlanChange.rowNumber/action/targetId/stableCode/expectedVersion/summary/companyCode/departmentCode/positionCode/fieldChanges | import_plan_row对应列；field_changes保存ImportValueChange的field/before/after；内部plan_no允许同一原行展开多规范动作，结果稳定按rowNumber/plan_no排序 |
| ImportIssue.rowNumber/field/severity/code/message | import_issue对应列，只向当前总管理员开放；阻断问题不靠跳错行提交 |
| ImportCounts八字段 | planned_counts/actual_counts JSON；实际失效grant/任命由同事务真实变化统计，非复制预检计数 |
| CatalogResult/SourceCloseResult/EmployeeStateResult/ScopeResult及auditEventIds | 成功事务变更值和审计ID组成安全响应，同事务保存幂等结果；NO_CHANGE不制造假审批或假业务成功事件 |
| SourceCloseResult.visibility/hasUndisclosedImpact及失效计数 | 从完整实际失效结果按当前单项资格投影；不保存新业务表。混合申请不可完整查看则不返回其ID/计数；缓存同一安全投影 |
| HTTP幂等内部授权事实（非API字段） | http_idempotency.authorization_basis固定操作策略及原目标/响应范围；删除草稿后仍可复核当前资格再重放，不能作为授权凭证 |
| AuthorizationResult/CheckResult/Menu/EffectiveOperation | T11新鲜一致快照计算，不持久化员工直接权限表/结果缓存；permissionType来自目标目录，未知编码类型null |
| Problem/requestId/errors、分页/排序参数 | 安全HTTP结果和诊断，不为每种错误/响应模型建表；无SQL/凭据进入响应或审计 |

后台审计混合来源无法安全分隔时，完整审计事件visibility=GLOBAL，仅总管理员访问；同一申请的安全ChangeEvent时间线仍按当前整份申请资格读取，两种入口各按其契约过滤，不因持有requestId跳过授权。多系统批次不会为了适配单systemId审计字段而拆成多笔业务提交。

字段映射需要的API技术上限继续沿用：名称100、说明/原因500、外部审批/事件ID64、原文件名200、sourcePath300；sourceSystem/sourceBatchId/employeeKey/loginName及业务编码100。系统新建code93；密码12～128，原样处理。权限配置最多10000项、批量明确目标100项、导入1000行。SQL更宽类型不是放宽API校验的理由。
