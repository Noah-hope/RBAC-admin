-- MySQL8.4不可变性触发器设计草案；未执行。先审查并应用schema.sql。

-- DELIMITER是mysql客户端指令；迁移器需要逐条提交完整CREATE TRIGGER。

DELIMITER $$

CREATE TRIGGER `tr_http_idempotency_completed` BEFORE UPDATE ON `http_idempotency` FOR EACH ROW
BEGIN
  IF OLD.state='COMPLETED' AND (NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`principal_id` <=> NEW.`principal_id`) OR NOT (OLD.`http_method` <=> NEW.`http_method`) OR NOT (OLD.`canonical_path` <=> NEW.`canonical_path`) OR NOT (OLD.`idempotency_key` <=> NEW.`idempotency_key`) OR NOT (OLD.`request_digest` <=> NEW.`request_digest`) OR NOT (OLD.`digest_key_id` <=> NEW.`digest_key_id`) OR NOT (OLD.`state` <=> NEW.`state`) OR NOT (OLD.`http_status` <=> NEW.`http_status`) OR NOT (OLD.`resource_location` <=> NEW.`resource_location`) OR NOT (OLD.`safe_response` <=> NEW.`safe_response`) OR NOT (OLD.`authorization_basis` <=> NEW.`authorization_basis`) OR NOT (OLD.`lease_token` <=> NEW.`lease_token`) OR NOT (OLD.`lease_until` <=> NEW.`lease_until`) OR NOT (OLD.`expires_at` <=> NEW.`expires_at`) OR NOT (OLD.`created_at` <=> NEW.`created_at`)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='completed HTTP result is immutable'; END IF;
END$$

CREATE TRIGGER `tr_change_request_fixed` BEFORE UPDATE ON `change_request` FOR EACH ROW
BEGIN
  IF OLD.submitted_version IS NOT NULL AND (NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`kind` <=> NEW.`kind`) OR NOT (OLD.`company_id` <=> NEW.`company_id`) OR NOT (OLD.`content` <=> NEW.`content`) OR NOT (OLD.`reason` <=> NEW.`reason`) OR NOT (OLD.`draft_version` <=> NEW.`draft_version`) OR NOT (OLD.`submitted_version` <=> NEW.`submitted_version`) OR NOT (OLD.`submitted_at` <=> NEW.`submitted_at`) OR NOT (OLD.`submitted_by_account_id` <=> NEW.`submitted_by_account_id`) OR NOT (OLD.`authority_type` <=> NEW.`authority_type`) OR NOT (OLD.`scope_id` <=> NEW.`scope_id`) OR NOT (OLD.`appointment_id` <=> NEW.`appointment_id`) OR NOT (OLD.`original_authority` <=> NEW.`original_authority`) OR NOT (OLD.`fixed_payload_bytes` <=> NEW.`fixed_payload_bytes`) OR NOT (OLD.`content_digest` <=> NEW.`content_digest`)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixed request is immutable'; END IF;
  IF NOT ((OLD.state=NEW.state) OR (OLD.state='DRAFT' AND NEW.state='PENDING') OR (OLD.state='PENDING' AND NEW.state IN ('APPROVED','REJECTED','INVALIDATED')) OR (OLD.state IN ('APPROVED','EXECUTION_FAILED') AND NEW.state IN ('EXECUTION_FAILED','EXECUTED','INVALIDATED'))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='illegal request state transition'; END IF;
  IF NEW.delivery_status='LINKED' AND NOT EXISTS (SELECT 1 FROM oa_link WHERE request_id=NEW.id) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='missing reliable OA link'; END IF;
  IF NEW.approval_result<>'NONE' AND NOT EXISTS (SELECT 1 FROM oa_final_result WHERE request_id=NEW.id AND decision=NEW.approval_result) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='missing consistent final result'; END IF;
  IF NEW.state='EXECUTED' AND NOT EXISTS (SELECT 1 FROM execution_success WHERE request_id=NEW.id) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='missing execution success receipt'; END IF;
END$$

CREATE TRIGGER `tr_change_request_insert` BEFORE INSERT ON `change_request` FOR EACH ROW
BEGIN
  IF NEW.state<>'DRAFT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='new request must start as draft'; END IF;
END$$

CREATE TRIGGER `tr_change_request_delete` BEFORE DELETE ON `change_request` FOR EACH ROW
BEGIN
  IF OLD.submitted_version IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='submitted history cannot be deleted'; END IF;
END$$

CREATE TRIGGER `tr_change_target_insert` BEFORE INSERT ON `change_target` FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM change_request WHERE id=NEW.request_id AND submitted_version IS NOT NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='submitted targets are immutable'; END IF;
END$$

CREATE TRIGGER `tr_change_target_update` BEFORE UPDATE ON `change_target` FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM change_request WHERE id=OLD.request_id AND submitted_version IS NOT NULL) OR EXISTS (SELECT 1 FROM change_request WHERE id=NEW.request_id AND submitted_version IS NOT NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='submitted targets are immutable'; END IF;
END$$

CREATE TRIGGER `tr_change_target_delete` BEFORE DELETE ON `change_target` FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM change_request WHERE id=OLD.request_id AND submitted_version IS NOT NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='submitted targets are immutable'; END IF;
END$$

CREATE TRIGGER `tr_import_batch_fixed` BEFORE UPDATE ON `import_batch` FOR EACH ROW
BEGIN
  IF OLD.sealed_at IS NOT NULL AND (NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`file_id` <=> NEW.`file_id`) OR NOT (OLD.`source_system` <=> NEW.`source_system`) OR NOT (OLD.`source_batch_id` <=> NEW.`source_batch_id`) OR NOT (OLD.`import_type` <=> NEW.`import_type`) OR NOT (OLD.`input_snapshot` <=> NEW.`input_snapshot`) OR NOT (OLD.`input_digest` <=> NEW.`input_digest`) OR NOT (OLD.`content_digest` <=> NEW.`content_digest`) OR NOT (OLD.`planned_counts` <=> NEW.`planned_counts`) OR NOT (OLD.`blocking_issue_count` <=> NEW.`blocking_issue_count`) OR NOT (OLD.`warning_count` <=> NEW.`warning_count`) OR NOT (OLD.`sealed_at` <=> NEW.`sealed_at`) OR NOT (OLD.`created_by` <=> NEW.`created_by`) OR NOT (OLD.`created_at` <=> NEW.`created_at`)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='sealed import input is immutable'; END IF;
  IF OLD.state IN ('SUCCEEDED','STALE','BLOCKED') AND NEW.state<>OLD.state THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='terminal import cannot be revived'; END IF;
END$$

CREATE TRIGGER `tr_import_batch_delete` BEFORE DELETE ON `import_batch` FOR EACH ROW
BEGIN
  IF OLD.sealed_at IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='sealed import history cannot be deleted'; END IF;
END$$

CREATE TRIGGER `tr_import_plan_row_insert` BEFORE INSERT ON `import_plan_row` FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM import_batch WHERE id=NEW.batch_id AND sealed_at IS NOT NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='sealed import details are immutable'; END IF;
END$$

CREATE TRIGGER `tr_import_plan_row_update` BEFORE UPDATE ON `import_plan_row` FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM import_batch WHERE id=OLD.batch_id AND sealed_at IS NOT NULL) OR EXISTS (SELECT 1 FROM import_batch WHERE id=NEW.batch_id AND sealed_at IS NOT NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='sealed import details are immutable'; END IF;
END$$

CREATE TRIGGER `tr_import_plan_row_delete` BEFORE DELETE ON `import_plan_row` FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM import_batch WHERE id=OLD.batch_id AND sealed_at IS NOT NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='sealed import details are immutable'; END IF;
END$$

CREATE TRIGGER `tr_import_issue_insert` BEFORE INSERT ON `import_issue` FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM import_batch WHERE id=NEW.batch_id AND sealed_at IS NOT NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='sealed import details are immutable'; END IF;
END$$

CREATE TRIGGER `tr_import_issue_update` BEFORE UPDATE ON `import_issue` FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM import_batch WHERE id=OLD.batch_id AND sealed_at IS NOT NULL) OR EXISTS (SELECT 1 FROM import_batch WHERE id=NEW.batch_id AND sealed_at IS NOT NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='sealed import details are immutable'; END IF;
END$$

CREATE TRIGGER `tr_import_issue_delete` BEFORE DELETE ON `import_issue` FOR EACH ROW
BEGIN
  IF EXISTS (SELECT 1 FROM import_batch WHERE id=OLD.batch_id AND sealed_at IS NOT NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='sealed import details are immutable'; END IF;
END$$

CREATE TRIGGER `tr_audit_event_update` BEFORE UPDATE ON `audit_event` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_audit_event_delete` BEFORE DELETE ON `audit_event` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_audit_subject_update` BEFORE UPDATE ON `audit_subject` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_audit_subject_delete` BEFORE DELETE ON `audit_subject` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_oa_callback_event_update` BEFORE UPDATE ON `oa_callback_event` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_oa_callback_event_delete` BEFORE DELETE ON `oa_callback_event` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_oa_final_result_update` BEFORE UPDATE ON `oa_final_result` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_oa_final_result_delete` BEFORE DELETE ON `oa_final_result` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_oa_link_update` BEFORE UPDATE ON `oa_link` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_oa_link_delete` BEFORE DELETE ON `oa_link` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_execution_success_update` BEFORE UPDATE ON `execution_success` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_execution_success_delete` BEFORE DELETE ON `execution_success` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_execution_attempt_update` BEFORE UPDATE ON `execution_attempt` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_execution_attempt_delete` BEFORE DELETE ON `execution_attempt` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_role_configuration_history_update` BEFORE UPDATE ON `role_configuration_history` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_role_configuration_history_delete` BEFORE DELETE ON `role_configuration_history` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_grant_employment_evidence_update` BEFORE UPDATE ON `grant_employment_evidence` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_grant_employment_evidence_delete` BEFORE DELETE ON `grant_employment_evidence` FOR EACH ROW
BEGIN
  IF TRUE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='append-only history'; END IF;
END$$

CREATE TRIGGER `tr_company_identity` BEFORE UPDATE ON `company` FOR EACH ROW
BEGIN
  IF NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`code` <=> NEW.`code`) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='stable identity or ownership cannot change'; END IF;
END$$

CREATE TRIGGER `tr_department_identity` BEFORE UPDATE ON `department` FOR EACH ROW
BEGIN
  IF NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`code` <=> NEW.`code`) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='stable identity or ownership cannot change'; END IF;
END$$

CREATE TRIGGER `tr_position_identity` BEFORE UPDATE ON `position` FOR EACH ROW
BEGIN
  IF NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`code` <=> NEW.`code`) OR NOT (OLD.`department_id` <=> NEW.`department_id`) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='stable identity or ownership cannot change'; END IF;
END$$

CREATE TRIGGER `tr_employee_identity` BEFORE UPDATE ON `employee` FOR EACH ROW
BEGIN
  IF NOT (OLD.`id` <=> NEW.`id`) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='stable identity or ownership cannot change'; END IF;
END$$

CREATE TRIGGER `tr_business_system_identity` BEFORE UPDATE ON `business_system` FOR EACH ROW
BEGIN
  IF NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`code` <=> NEW.`code`) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='stable identity or ownership cannot change'; END IF;
END$$

CREATE TRIGGER `tr_directory_node_identity` BEFORE UPDATE ON `directory_node` FOR EACH ROW
BEGIN
  IF NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`system_id` <=> NEW.`system_id`) OR NOT (OLD.`code` <=> NEW.`code`) OR NOT (OLD.`node_type` <=> NEW.`node_type`) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='stable identity or ownership cannot change'; END IF;
END$$

CREATE TRIGGER `tr_permission_identity` BEFORE UPDATE ON `permission` FOR EACH ROW
BEGIN
  IF NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`system_id` <=> NEW.`system_id`) OR NOT (OLD.`code` <=> NEW.`code`) OR NOT (OLD.`permission_type` <=> NEW.`permission_type`) OR NOT (OLD.`function_id` <=> NEW.`function_id`) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='stable identity or ownership cannot change'; END IF;
END$$

CREATE TRIGGER `tr_business_role_identity` BEFORE UPDATE ON `business_role` FOR EACH ROW
BEGIN
  IF NOT (OLD.`id` <=> NEW.`id`) OR NOT (OLD.`code` <=> NEW.`code`) OR NOT (OLD.`company_id` <=> NEW.`company_id`) OR NOT (OLD.`department_id` <=> NEW.`department_id`) OR NOT (OLD.`system_id` <=> NEW.`system_id`) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='stable identity or ownership cannot change'; END IF;
END$$

DELIMITER ;
