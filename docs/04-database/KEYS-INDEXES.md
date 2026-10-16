# 主键、唯一键、外键与索引清单

由 [schema_model.py](schema_model.py) 生成。用途、顺序、成本及查询见 [INDEXES.md](INDEXES.md)。所有显式外键均有左前缀索引；复合被引用键均显式唯一，适配MySQL8.4限制。

## mutation_guard

- PRIMARY KEY (id)：聚簇定位。

## company

- PRIMARY KEY (id)：聚簇定位。
- uq_company_1 (code)：唯一性/被引用复合候选键。

## department

- PRIMARY KEY (id)：聚簇定位。
- uq_department_1 (company_id, code)：唯一性/被引用复合候选键。
- uq_department_2 (id, company_id)：唯一性/被引用复合候选键。
- FK (company_id) → company (id)；删除/更新默认RESTRICT。

## position

- PRIMARY KEY (id)：聚簇定位。
- uq_position_1 (department_id, code)：唯一性/被引用复合候选键。
- uq_position_2 (id, department_id)：唯一性/被引用复合候选键。
- FK (department_id) → department (id)；删除/更新默认RESTRICT。

## employee

- PRIMARY KEY (id)：聚簇定位。
- uq_employee_1 (employee_key)：唯一性/被引用复合候选键。

## employee_source_identity

- PRIMARY KEY (id)：聚簇定位。
- uq_employee_source_identity_1 (source_system, source_key)：唯一性/被引用复合候选键。
- ix_employee_source_identity_1 (employee_id)：FK支撑/典型查询访问路径。
- FK (employee_id) → employee (id)；删除/更新默认RESTRICT。

## organization_mapping

- PRIMARY KEY (id)：聚簇定位。
- uq_organization_mapping_1 (source_system, source_path)：唯一性/被引用复合候选键。
- ix_organization_mapping_1 (company_id)：FK支撑/典型查询访问路径。
- ix_organization_mapping_2 (department_id, company_id)：FK支撑/典型查询访问路径。
- ix_organization_mapping_3 (position_id, department_id)：FK支撑/典型查询访问路径。
- FK (company_id) → company (id)；删除/更新默认RESTRICT。
- FK (department_id, company_id) → department (id, company_id)；删除/更新默认RESTRICT。
- FK (position_id, department_id) → position (id, department_id)；删除/更新默认RESTRICT。

## employment

- PRIMARY KEY (id)：聚簇定位。
- uq_employment_1 (employee_id, position_id, active_slot)：唯一性/被引用复合候选键。
- ix_employment_1 (employee_id, company_id, department_id, status)：FK支撑/典型查询访问路径。
- ix_employment_2 (department_id, company_id)：FK支撑/典型查询访问路径。
- ix_employment_3 (position_id, department_id)：FK支撑/典型查询访问路径。
- FK (employee_id) → employee (id)；删除/更新默认RESTRICT。
- FK (department_id, company_id) → department (id, company_id)；删除/更新默认RESTRICT。
- FK (position_id, department_id) → position (id, department_id)；删除/更新默认RESTRICT。

## admin_account

- PRIMARY KEY (id)：聚簇定位。
- uq_admin_account_1 (login_name)：唯一性/被引用复合候选键。
- uq_admin_account_2 (employee_id)：唯一性/被引用复合候选键。

## admin_session

- PRIMARY KEY (id)：聚簇定位。
- uq_admin_session_1 (token_jti)：唯一性/被引用复合候选键。
- ix_admin_session_1 (account_id, expires_at)：FK支撑/典型查询访问路径。
- FK (account_id) → admin_account (id)；删除/更新默认RESTRICT。

## management_scope

- PRIMARY KEY (id)：聚簇定位。
- ix_management_scope_1 (department_id, company_id)：FK支撑/典型查询访问路径。
- FK (department_id, company_id) → department (id, company_id)；删除/更新默认RESTRICT。

## scope_system

- PRIMARY KEY (scope_id, system_id)：聚簇定位。
- ix_scope_system_1 (system_id)：FK支撑/典型查询访问路径。
- FK (scope_id) → management_scope (id)；删除/更新默认RESTRICT。
- FK (system_id) → business_system (id)；删除/更新默认RESTRICT。

## admin_appointment

- PRIMARY KEY (id)：聚簇定位。
- uq_admin_appointment_1 (scope_id, account_id, active_slot)：唯一性/被引用复合候选键。
- ix_admin_appointment_1 (account_id, status, id)：FK支撑/典型查询访问路径。
- FK (scope_id) → management_scope (id)；删除/更新默认RESTRICT。
- FK (account_id) → admin_account (id)；删除/更新默认RESTRICT。

## business_system

- PRIMARY KEY (id)：聚簇定位。
- uq_business_system_1 (code)：唯一性/被引用复合候选键。

## directory_node

- PRIMARY KEY (id)：聚簇定位。
- uq_directory_node_1 (system_id, code)：唯一性/被引用复合候选键。
- uq_directory_node_2 (id, system_id)：唯一性/被引用复合候选键。
- ix_directory_node_1 (system_id, parent_id, sort_order, code, id)：FK支撑/典型查询访问路径。
- ix_directory_node_2 (parent_id, system_id)：FK支撑/典型查询访问路径。
- ix_directory_node_3 (visibility_permission_id, id, system_id)：FK支撑/典型查询访问路径。
- FK (system_id) → business_system (id)；删除/更新默认RESTRICT。
- FK (parent_id, system_id) → directory_node (id, system_id)；删除/更新默认RESTRICT。
- FK (visibility_permission_id, id, system_id) → permission (id, function_id, system_id)；删除/更新默认RESTRICT。

## permission

- PRIMARY KEY (id)：聚簇定位。
- uq_permission_1 (system_id, code)：唯一性/被引用复合候选键。
- uq_permission_2 (system_id, access_slot)：唯一性/被引用复合候选键。
- uq_permission_3 (function_id, name)：唯一性/被引用复合候选键。
- uq_permission_4 (id, system_id)：唯一性/被引用复合候选键。
- uq_permission_5 (id, function_id, system_id)：唯一性/被引用复合候选键。
- ix_permission_1 (function_id, system_id)：FK支撑/典型查询访问路径。
- FK (system_id) → business_system (id)；删除/更新默认RESTRICT。
- FK (function_id, system_id) → directory_node (id, system_id)；删除/更新默认RESTRICT。

## business_role

- PRIMARY KEY (id)：聚簇定位。
- uq_business_role_1 (code)：唯一性/被引用复合候选键。
- uq_business_role_2 (id, company_id, system_id)：唯一性/被引用复合候选键。
- uq_business_role_3 (id, system_id)：唯一性/被引用复合候选键。
- ix_business_role_1 (company_id, department_id, system_id, created_at, id)：FK支撑/典型查询访问路径。
- ix_business_role_2 (department_id, company_id)：FK支撑/典型查询访问路径。
- ix_business_role_3 (system_id)：FK支撑/典型查询访问路径。
- FK (company_id) → company (id)；删除/更新默认RESTRICT。
- FK (department_id, company_id) → department (id, company_id)；删除/更新默认RESTRICT。
- FK (system_id) → business_system (id)；删除/更新默认RESTRICT。

## role_permission

- PRIMARY KEY (role_id, permission_id)：聚簇定位。
- ix_role_permission_1 (role_id, system_id)：FK支撑/典型查询访问路径。
- ix_role_permission_2 (permission_id, system_id)：FK支撑/典型查询访问路径。
- FK (role_id, system_id) → business_role (id, system_id)；删除/更新默认RESTRICT。
- FK (permission_id, system_id) → permission (id, system_id)；删除/更新默认RESTRICT。

## role_configuration_history

- PRIMARY KEY (role_id, configuration_version)：聚簇定位。
- uq_role_configuration_history_1 (request_id)：唯一性/被引用复合候选键。
- FK (role_id) → business_role (id)；删除/更新默认RESTRICT。
- FK (request_id) → change_request (id)；删除/更新默认RESTRICT。

## authorization_source

- PRIMARY KEY (id)：聚簇定位。
- uq_authorization_source_1 (employee_id, company_id, department_id, system_id)：唯一性/被引用复合候选键。
- uq_authorization_source_2 (id, company_id, system_id)：唯一性/被引用复合候选键。
- ix_authorization_source_1 (department_id, company_id, system_id)：FK支撑/典型查询访问路径。
- ix_authorization_source_2 (system_id)：FK支撑/典型查询访问路径。
- FK (employee_id) → employee (id)；删除/更新默认RESTRICT。
- FK (department_id, company_id) → department (id, company_id)；删除/更新默认RESTRICT。
- FK (system_id) → business_system (id)；删除/更新默认RESTRICT。

## grant_baseline

- PRIMARY KEY (id)：聚簇定位。
- uq_grant_baseline_1 (source_id, role_id)：唯一性/被引用复合候选键。
- ix_grant_baseline_1 (source_id, company_id, system_id)：FK支撑/典型查询访问路径。
- ix_grant_baseline_2 (role_id, company_id, system_id)：FK支撑/典型查询访问路径。
- FK (source_id, company_id, system_id) → authorization_source (id, company_id, system_id)；删除/更新默认RESTRICT。
- FK (role_id, company_id, system_id) → business_role (id, company_id, system_id)；删除/更新默认RESTRICT。

## role_grant

- PRIMARY KEY (id)：聚簇定位。
- uq_role_grant_1 (baseline_id, active_slot)：唯一性/被引用复合候选键。
- uq_role_grant_2 (baseline_id, relation_version)：唯一性/被引用复合候选键。
- ix_role_grant_1 (granted_by_request_id)：FK支撑/典型查询访问路径。
- ix_role_grant_2 (ended_by_request_id)：FK支撑/典型查询访问路径。
- FK (baseline_id) → grant_baseline (id)；删除/更新默认RESTRICT。
- FK (granted_by_request_id) → change_request (id)；删除/更新默认RESTRICT。
- FK (ended_by_request_id) → change_request (id)；删除/更新默认RESTRICT。

## grant_employment_evidence

- PRIMARY KEY (grant_id, employment_id)：聚簇定位。
- ix_grant_employment_evidence_1 (employment_id)：FK支撑/典型查询访问路径。
- FK (grant_id) → role_grant (id)；删除/更新默认RESTRICT。
- FK (employment_id) → employment (id)；删除/更新默认RESTRICT。

## change_request

- PRIMARY KEY (id)：聚簇定位。
- uq_change_request_1 (id, kind)：唯一性/被引用复合候选键。
- ix_change_request_1 (scope_id, state, id)：FK支撑/典型查询访问路径。
- ix_change_request_2 (company_id, created_at, id)：FK支撑/典型查询访问路径。
- ix_change_request_3 (state, next_retry_at, id)：FK支撑/典型查询访问路径。
- ix_change_request_4 (submitted_by_account_id)：FK支撑/典型查询访问路径。
- ix_change_request_5 (appointment_id)：FK支撑/典型查询访问路径。
- FK (company_id) → company (id)；删除/更新默认RESTRICT。
- FK (scope_id) → management_scope (id)；删除/更新默认RESTRICT。
- FK (appointment_id) → admin_appointment (id)；删除/更新默认RESTRICT。
- FK (submitted_by_account_id) → admin_account (id)；删除/更新默认RESTRICT。

## change_target

- PRIMARY KEY (request_id, line_no)：聚簇定位。
- uq_change_target_1 (request_id, baseline_id)：唯一性/被引用复合候选键。
- uq_change_target_2 (request_id, grant_id)：唯一性/被引用复合候选键。
- ix_change_target_1 (request_id, line_kind)：FK支撑/典型查询访问路径。
- ix_change_target_2 (source_id)：FK支撑/典型查询访问路径。
- ix_change_target_3 (baseline_id)：FK支撑/典型查询访问路径。
- ix_change_target_4 (grant_id)：FK支撑/典型查询访问路径。
- ix_change_target_5 (role_id, company_id, system_id)：FK支撑/典型查询访问路径。
- ix_change_target_6 (department_id, company_id)：FK支撑/典型查询访问路径。
- ix_change_target_7 (employee_id)：FK支撑/典型查询访问路径。
- ix_change_target_8 (system_id)：FK支撑/典型查询访问路径。
- FK (request_id, line_kind) → change_request (id, kind)；删除/更新默认RESTRICT。
- FK (role_id, company_id, system_id) → business_role (id, company_id, system_id)；删除/更新默认RESTRICT。
- FK (department_id, company_id) → department (id, company_id)；删除/更新默认RESTRICT。
- FK (employee_id) → employee (id)；删除/更新默认RESTRICT。
- FK (source_id) → authorization_source (id)；删除/更新默认RESTRICT。
- FK (baseline_id) → grant_baseline (id)；删除/更新默认RESTRICT。
- FK (grant_id) → role_grant (id)；删除/更新默认RESTRICT。
- FK (system_id) → business_system (id)；删除/更新默认RESTRICT。

## role_change_claim

- PRIMARY KEY (role_id)：聚簇定位。
- uq_role_change_claim_1 (request_id)：唯一性/被引用复合候选键。
- FK (role_id) → business_role (id)；删除/更新默认RESTRICT。
- FK (request_id) → change_request (id)；删除/更新默认RESTRICT。

## relation_change_claim

- PRIMARY KEY (baseline_id)：聚簇定位。
- ix_relation_change_claim_1 (request_id)：FK支撑/典型查询访问路径。
- FK (baseline_id) → grant_baseline (id)；删除/更新默认RESTRICT。
- FK (request_id) → change_request (id)；删除/更新默认RESTRICT。

## service_identity

- PRIMARY KEY (id)：聚簇定位。
- uq_service_identity_1 (subject)：唯一性/被引用复合候选键。
- ix_service_identity_1 (system_id)：FK支撑/典型查询访问路径。
- FK (system_id) → business_system (id)；删除/更新默认RESTRICT。

## oa_link

- PRIMARY KEY (request_id)：聚簇定位。
- uq_oa_link_1 (oa_instance_id, oa_approval_id)：唯一性/被引用复合候选键。
- FK (request_id) → change_request (id)；删除/更新默认RESTRICT。
- FK (oa_instance_id) → service_identity (id)；删除/更新默认RESTRICT。

## oa_outbox

- PRIMARY KEY (request_id)：聚簇定位。
- ix_oa_outbox_1 (task_state, next_attempt_at, request_id)：FK支撑/典型查询访问路径。
- ix_oa_outbox_2 (oa_instance_id)：FK支撑/典型查询访问路径。
- FK (request_id) → change_request (id)；删除/更新默认RESTRICT。
- FK (oa_instance_id) → service_identity (id)；删除/更新默认RESTRICT。

## oa_callback_event

- PRIMARY KEY (id)：聚簇定位。
- uq_oa_callback_event_1 (oa_instance_id, event_id)：唯一性/被引用复合候选键。
- ix_oa_callback_event_1 (request_id)：FK支撑/典型查询访问路径。
- FK (oa_instance_id) → service_identity (id)；删除/更新默认RESTRICT。
- FK (request_id) → change_request (id)；删除/更新默认RESTRICT。

## oa_final_result

- PRIMARY KEY (request_id)：聚簇定位。
- uq_oa_final_result_1 (first_event_id)：唯一性/被引用复合候选键。
- FK (request_id) → change_request (id)；删除/更新默认RESTRICT。
- FK (first_event_id) → oa_callback_event (id)；删除/更新默认RESTRICT。

## execution_success

- PRIMARY KEY (request_id)：聚簇定位。
- uq_execution_success_1 (audit_event_id)：唯一性/被引用复合候选键。
- FK (request_id) → change_request (id)；删除/更新默认RESTRICT。
- FK (audit_event_id) → audit_event (id)；删除/更新默认RESTRICT。

## execution_attempt

- PRIMARY KEY (id)：聚簇定位。
- uq_execution_attempt_1 (request_id, attempt_no)：唯一性/被引用复合候选键。
- FK (request_id) → change_request (id)；删除/更新默认RESTRICT。

## http_idempotency

- PRIMARY KEY (id)：聚簇定位。
- uq_http_idempotency_1 (principal_id, http_method, canonical_path, idempotency_key)：唯一性/被引用复合候选键。
- ix_http_idempotency_1 (expires_at)：FK支撑/典型查询访问路径。
- FK (principal_id) → admin_account (id)；删除/更新默认RESTRICT。

## import_file

- PRIMARY KEY (id)：聚簇定位。
- ix_import_file_1 (uploaded_by)：FK支撑/典型查询访问路径。
- FK (uploaded_by) → admin_account (id)；删除/更新默认RESTRICT。

## import_batch

- PRIMARY KEY (id)：聚簇定位。
- uq_import_batch_1 (source_system, source_batch_id)：唯一性/被引用复合候选键。
- ix_import_batch_1 (created_at, id)：FK支撑/典型查询访问路径。
- ix_import_batch_2 (file_id)：FK支撑/典型查询访问路径。
- ix_import_batch_3 (created_by)：FK支撑/典型查询访问路径。
- FK (file_id) → import_file (id)；删除/更新默认RESTRICT。
- FK (created_by) → admin_account (id)；删除/更新默认RESTRICT。

## import_plan_row

- PRIMARY KEY (batch_id, row_number, plan_no)：聚簇定位。
- FK (batch_id) → import_batch (id)；删除/更新默认RESTRICT。

## import_issue

- PRIMARY KEY (id)：聚簇定位。
- ix_import_issue_1 (batch_id, row_number, id)：FK支撑/典型查询访问路径。
- FK (batch_id) → import_batch (id)；删除/更新默认RESTRICT。

## impact_preview

- PRIMARY KEY (id)：聚簇定位。
- ix_impact_preview_1 (expires_at)：FK支撑/典型查询访问路径。
- ix_impact_preview_2 (creator_account_id)：FK支撑/典型查询访问路径。
- ix_impact_preview_3 (appointment_id)：FK支撑/典型查询访问路径。
- FK (creator_account_id) → admin_account (id)；删除/更新默认RESTRICT。
- FK (appointment_id) → admin_appointment (id)；删除/更新默认RESTRICT。

## preview_item

- PRIMARY KEY (id)：聚簇定位。
- uq_preview_item_1 (preview_id, item_kind, stable_key)：唯一性/被引用复合候选键。
- ix_preview_item_1 (preview_id, item_kind, id)：FK支撑/典型查询访问路径。
- FK (preview_id) → impact_preview (id)；删除/更新默认RESTRICT。

## audit_event

- PRIMARY KEY (id)：聚簇定位。
- ix_audit_event_1 (occurred_at, id)：FK支撑/典型查询访问路径。
- ix_audit_event_2 (company_id, source_department_id, system_id, occurred_at, id)：FK支撑/典型查询访问路径。
- ix_audit_event_3 (source_department_id, company_id)：FK支撑/典型查询访问路径。
- ix_audit_event_4 (system_id)：FK支撑/典型查询访问路径。
- ix_audit_event_5 (change_request_id, occurred_at, id)：FK支撑/典型查询访问路径。
- ix_audit_event_6 (original_submitter_id)：FK支撑/典型查询访问路径。
- FK (original_submitter_id) → admin_account (id)；删除/更新默认RESTRICT。
- FK (company_id) → company (id)；删除/更新默认RESTRICT。
- FK (source_department_id, company_id) → department (id, company_id)；删除/更新默认RESTRICT。
- FK (system_id) → business_system (id)；删除/更新默认RESTRICT。

## audit_subject

- PRIMARY KEY (audit_event_id, subject_type, subject_id)：聚簇定位。
- ix_audit_subject_1 (subject_type, subject_id, audit_event_id)：FK支撑/典型查询访问路径。
- FK (audit_event_id) → audit_event (id)；删除/更新默认RESTRICT。
