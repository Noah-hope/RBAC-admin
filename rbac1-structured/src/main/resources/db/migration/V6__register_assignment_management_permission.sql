SET @app=(SELECT id FROM sys_application WHERE code='permission-center');
INSERT INTO sys_permission(application_id,parent_id,name,code,type)
SELECT application_id,id,'分配与撤销业务角色','assignment:manage','BUTTON'
FROM sys_permission WHERE application_id=@app AND code='assignment:view';
INSERT INTO sys_api_permission(permission_id,api_id)
SELECT p.id,a.id FROM sys_permission p,sys_api a
WHERE p.application_id=@app AND p.code='assignment:manage'
  AND a.application_id=@app AND a.operation_id='replaceUserBusinessRoles';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r,sys_permission p
WHERE r.role_type IN ('TOTAL_ADMIN','DEPARTMENT_ADMIN')
  AND p.application_id=@app AND p.code='assignment:manage';
