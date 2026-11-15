ALTER TABLE sys_application DROP INDEX uq_application_token, DROP COLUMN token_hash;
INSERT INTO sys_application (code, name) VALUES ('permission-center', '权限管理中心');
DELETE FROM sys_session;
