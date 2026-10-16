# 索引与典型查询

完整键名、字段顺序及FK支撑见[KEYS-INDEXES.md](KEYS-INDEXES.md)。本文件说明访问路径设计，未运行EXPLAIN、压测或实际查询；没有“已达到毫秒级”的性能结论。

| 查询/API | 访问路径与顺序 | 成本和限制 |
| --- | --- | --- |
| 登录/服务身份 | admin_account(login_name)UQ；service_identity(subject)UQ；session按sid PK/jti UQ | 不索引密码摘要；专用慢哈希在事务外，最终版本锁内复核 |
| 我的任命/当前资格 | appointment(account_id,status,id)先账号和ACTIVE；scope PK及scope_system(scope_id,system_id) | 后台资格总是当前事实；scopeVersion不固化进JWT作唯一依据 |
| 公司/部门/岗位 | code UQ、department(company_id,code)、position(department_id,code)；code/id稳定组织排序 | 排序追加id，唯一code时id是稳定兜底；不使用部门递归授权 |
| 员工任职/兼岗 | employment(employee_id,company_id,department_id,status)；部门/岗位FK前缀 | 先员工/公司查全部适用任职，不只看固定某个任职ID |
| 管理员可见员工 | 当前指定部门任职路径联结员工；离岗来源历史通过authorization_source(department_id,company_id,system_id)联结员工 | 当前任职与历史授权展示需按API场景区分；当前员工状态不抹掉历史可查性；部门范围扫描量须后续测量 |
| 目录树/权限 | directory(system_id,parent_id,sort_order,code,id)按同父稳定排序；permission(system_id,code)定位；function_id索引取操作 | 树计算一次读取完整系统节点；不为每个祖先建多余闭包表；目录规模要做上限与深树验证 |
| 角色列表 | role(company_id,department_id,system_id,created_at,id)，system FK；公司级共享角色与部门角色分支UNION后统一分页 | 可见部门与共享角色分支不靠NULL=比较；全局可见多公司查询可能filesort，不能声称所有组合覆盖 |
| 当前角色配置/影响 | role_permission PK(role_id,permission_id)；反向(permission_id,system_id)找引用；baseline(role_id,company_id,system_id)定位所有来源 | 删除/插入集合写放大，配置历史JSON额外空间；只在执行成功时替换 |
| UC-05/06有效权限 | employee_key UQ→employment按员工公司→source(employee_id,company_id,department_id,system_id)→baseline(source_id,role_id)→grant(baseline_id,active_slot)→role_permission→permission | source索引system不是第三列，先用employee/company缩小后过滤system；不因一个筛选追加近重复索引。初期资料1万人，不构成性能验证 |
| 来源关闭 | source四元UQ定位；baseline(source_id,role_id)→grant ACTIVE唯一；change_target(source_id)→request PK | 必须包括无grant来源，不能只从ACTIVE grant起查；关权可能影响多个整份申请，所有目标仍一个事务 |
| 关系基线/准确撤销 | baseline(source_id,role_id)UQ与grant PK；(baseline_id,relation_version)UQ；claims PK | 同来源至多一有效行，结束历史可多条；索引不回退关系版本 |
| OA接收 | oa_link(request)PK/(instance,approvalId)UQ；callback(instance,eventId)UQ；final_result(request)PK | 三层防重分别索引，不能合成一个“回调key”；成功凭据独立 |
| 执行恢复 | change_request(state,next_retry_at,id)；候选ID读完后逐份T0，扫描不先持申请锁 | APPROVED nextRetryAt可空视为立即；FAILED按<=now；or分支可分两次候选查询，不能分批提交同一申请 |
| 投递恢复 | outbox(task_state,next_attempt_at,request_id)；lease超时查询辅以过滤lease_until | 不为尚未证实的吞吐再建lease索引；LEASED故障回收量后续测量 |
| 申请列表/范围变更失效 | request(company_id,created_at,id)、(scope_id,state,id)；change_target按role/source/baseline/grant各FK前缀 | 列表必须对全份固定目标做覆盖校验再COUNT/PAGE；不能只过滤某条命中目标而暴露整份混合申请 |
| 授权审计 | audit(company_id,source_department_id,system_id,occurred_at,id)先固定来源三等值后时间范围；全局(occurred_at,id)；关联申请(change_request_id,occurred_at,id) | 全局/混合不可安全分隔记录仅总管理员；记录时来源不随调岗变化；不把当前公司代入历史 |
| 员工/角色审计筛选 | audit_subject(subject_type,subject_id,audit_event_id)→audit_event，当前授权过滤后分页/计数 | 一个事件可含多个目标，DISTINCT事件ID再计数，禁止目标行JOIN造成重复页；其他actor/result过滤可能残余扫描 |
| 幂等 | UQ(principal,method,canonical_path,key)；expires_at供受控清理 | 宽路径唯一索引有空间成本；完整索引不截断前缀，防不同路径错误相等 |
| 导入 | batch(source_system,source_batch_id)UQ；row PK(batch,row_number,plan_no)；issue(batch,row_number,id) | 同批号长期保留，预检JSON不全量索引；每次最多1000行仍需真实锁/undo容量检查 |
| 预览分页 | preview_item(preview_id,item_kind,id)，UQ(preview_id,item_kind,stable_key)；preview.expires_at | 表内仅安全投影；统计来自同快照；不按每个JSON字段建立生成列索引 |

## 查询形状示例（设计伪SQL）

```sql
-- 有效关系的基础候选，还必须按T11检查员工/任职、周期、目录及进入前提。
SELECT g.id, b.role_id, s.department_id
FROM authorization_source s
JOIN grant_baseline b ON b.source_id=s.id
JOIN role_grant g ON g.baseline_id=b.id AND g.active_slot=1
WHERE s.employee_id=:employeeId AND s.company_id=:companyId
  AND s.system_id=:systemId AND g.source_epoch=s.source_epoch;

-- 同一份申请所有目标都被当前单项范围覆盖才可返回（GLOBAL另分支）。
SELECT r.id FROM change_request r
WHERE r.company_id=:companyId AND NOT EXISTS (
  SELECT 1 FROM change_target t WHERE t.request_id=r.id
  AND (t.department_id IS NULL OR t.department_id<>:departmentId
       OR NOT EXISTS (SELECT 1 FROM scope_system ss
                      WHERE ss.scope_id=:scopeId AND ss.system_id=t.system_id))
);
```

第二例仅为分配/撤销或部门配置申请的保守覆盖形状，公司级共享角色配置按维护规则仅总管理员；草稿content和实际目标数量也必须完整一致，不能利用“0目标”空真绕过访问。同一条件用于列表、计数、详情、关联事件和预览待申请目标。详情越权/不存在统一404，显式越范围筛选403。

列表默认createdAt/id降序；审计主列表occurredAt/id降序、关联事件升序；目录同父sortOrder/code/id升序。MySQL可反向扫描相同方向的B-tree，混合方向或跨分支合并可能排序。包含检索`%q%`无法靠普通B-tree保证高效，先范围/分页限制，暂不引入全文搜索改变中文查询语义。排序字段与唯一性索引不得互相替代。

所有二级索引包含聚簇UUID主键；随机UUID降低插入局部性，复合候选键、FK、ACTIVE槽、审计subject均增加写放大/空间。先保完整性和有依据查询，未按所有字段建单列索引。数据量、热点、EXPLAIN ANALYZE及统计信息更新在真实测试后决定增删访问索引，唯一约束/外键支撑不能随优化删除。
