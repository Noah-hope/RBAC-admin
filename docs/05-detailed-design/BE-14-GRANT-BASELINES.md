# BE-14 来源、关系基线与授权历史

2026-10-20，成员D要求“我后面再验收吧，先开始下一项工作任务”。按既定顺序实施BE-14；BE-10、BE-11代码及所属自动化证据已交付共同main，基线61132f8，分支codex/be-14-grant-baselines。BE-28维持IMPLEMENTED/mergedToMain=true/ownerAccepted=false，本次继续开发不代记其人工验收，也不自动实施BE-16/29或扩展此前BE-28的合并推送授权。

依据UC-03/04/11、API1.2.0的listGrants/grantBaselines/sourceBaselines、T9/T11、I-03/08/09及现有V1～V3迁移；不改需求、接口或数据库结构。AT-13/14/15仅验证本任务的读取与资格事实部分，授予/结束/周期推进和执行竞争留BE-16/26及C执行器。

## 边界与流程

- `grant/GrantReader` 是同数据源、调用方已有事务内的事实端口；无自建事务、无缓存、无授权/DML。供BE-16及B/C复用逻辑来源/关系版本、准确grant历史与当前资格；内部返回源/基线ID，不在API额外暴露。不存在的持久锚点返回逻辑0，既有来源周期不能因关系缺失被重置；读取不推进版本或物化行。
- 当前资格由员工状态、有效角色配置、系统启用、角色归属及准确来源部门内的全部有效任职共同决定，复用OrganizationReader；公司级共享角色也须选定实际来源部门。所有任职完整保留，不裁为100项，不将岗位当授权。纯基线查询可读取当前不合格目标的版本，没有当前适用任职时返回空数组；系统或角色不可用不抹去仍有效的HR任职事实。eligibleForGrant表示当前资格，不代表需要新增关系或已经授予/批准。历史读取不依赖当前任职/角色可用性，保留当时grant_employment_evidence。
- `grant/GrantQueries` 使用ReadBoundary的新主库RR快照；在同一事务中复核当前非受限后台账号和BE-08 ManagementContextResolver，不拼接任命。总管理员无上下文为全局；显式选择任命后同样受其公司、部门、系统集合限制。所有目标先按固定来源核对当前单项范围；公司级共享角色按只读/分配边界可见，其他部门角色不能借此越界。
- `GET /api/v1/admin/grants`：七个业务过滤及分页；按来源公司/部门/系统过滤并计数后分页，稳定createdAt DESC/id DESC，返回准确grant自身relationVersion、授予时sourceEpoch和不可变任职证据，不替换为当前baseline版本。员工离开原部门后仍可看范围内来源历史；不可见不表示其他来源不存在。ACTIVE缺少应有任职证据视为存储不完整500，不伪造合法响应。
- `POST /api/v1/admin/grant-baselines`：纯查询，1～100条明确GrantKey配对，拒绝重复键、null/未知字段/缺字段；不做员工×角色组合，保持请求顺序。返回角色当前配置版本、当前来源周期和永久关系版本、当前全部适用任职及ACTIVE关系ID；无历史为0/null且不落库。
- `POST /api/v1/admin/source-baselines`：纯查询，1～100个唯一systemIds，明确员工/公司/来源部门；无来源返回0周期/0版本，无当前任职也可查，用于关权/历史预览；不创建来源。不存在资源404，已知归属不匹配422，显式越当前公司/部门/系统403，全部目标失败不返回部分数据。
- HTTP返回no-store，POST查询不要求幂等key、不写幂等行或审计。复用Problem、输入限制和重复管理头拒绝；缺/陈旧上下文分别422/409、他人任命403、未登录/失效会话401、受限会话403。授权边界是后台管理范围，不新增业务数据权限。

## 验证顺序

先真实HTTP/MySQL RED：三路由未实现而404；再实现到GREEN，补端口事务边界及双连接RR验证。测试使用专属非3306 MySQL8.4和随机schema/账号/临时RSA，不读取或操作用户现有库。

场景：逻辑0与无副作用；已有周期/版本和准确ACTIVE grant；100/101条请求边界及101项任职完整返回；同源去重/异源独立；结束任职与员工停用不隐藏历史；历史证据不跟随当前任职变化；源系统/部门/公司隔离；共享/部门角色当前资格；缺/陈旧/他人/重复上下文；未知字段/无效ID/重复业务键；越权整份拒绝；两个连接证明同快照读取不混合并发新建/变更。

完整clean verify/mysql-check和静态追踪通过后记录实际数量、清理事实与本机接入；本任务实现、负责人验收和主线交付分别维护，不将文件存在或其他模块自动化证据记为本任务完成。

## 执行证据

2026-10-20，设计先行后按以下实际证据推进：

| 检查 | 实际观察 | 实现/验证范围 |
| --- | --- | --- |
| 正式HTTP/MySQL RED | 9项中8失败、0错误、1项既有未登录401检查通过；目标三路由返回404 | 日志be14-http-final-red.log。此前夹具结束任职缺recorded_end_at及员工枚举错误已修正，错误运行不作有效RED |
| 内部事务端口 RED | 2项失败、0错误；桩端口返回UnsupportedOperationException而非需要的事务边界拒绝 | be14-reader-red.log；随后实现必须处于调用方同数据源事务内的读取保护，两项通过 |
| 首轮定向 GREEN | 2项离线＋9项真实MySQL/HTTP全通过 | be14-targeted-green.log，逻辑0/无DML、101项完整任职、当前/历史版本、固定来源权限、输入边界 |
| 边界补查与首轮完整构建 | 定向2＋14通过，完整112离线＋192真实MySQL/HTTP通过 | be14-expanded-green.log、be14-full-verify.log；100条配对、当前资格、受限账号、重复头及双连接RR |
| 历史证据归属 RED | 1项失败，期望500、实际200 | be14-evidence-red.log，真实持久证据错误关联到其他部门/员工时会返回其任职ID；修正为核对员工/公司/部门，不因任职已结束而删除合法历史 |

最终完整回归于2026-10-20 13:43（Asia/Shanghai）通过：显式JDK21执行backend工作目录的 `./mvnw.cmd -q clean verify -Pmysql-check`，Surefire **112项/19组**、Failsafe **193项/16组**，失败/错误/跳过均0。包含本任务2项离线＋15项真实MySQL/HTTP，及共同main既有全部回归；新增归属错误已返回500且不返回其他任职ID，合法已结束任职证据仍可读取。最终日志 `backend/.tmp/be14-final-verify.log`。

构建产物backend/target/backend-0.0.1-SNAPSHOT.jar的SHA256为 `b83678322c26476c9163fe7e39282091c8814b075add6f51286b9f05e1ce3fc6`。专属MySQL8.4仅监听127.0.0.1:23328，复用仓库临时datadir；结束回查随机schema/账号0/0、BE-14临时RSA目录0，PID7144正常关闭，用户原MySQL84仍Running，未操作已有库。生成器及完整validate_design.py静态校验通过，原契约/迁移摘要不变；静态校验本身不运行Java、SQL或HTTP。

日志仅保存在backend/.tmp忽略目录，不纳入Git；长期证据以本记录和可重复运行的测试为准。真实持久查询夹具不代表C的OA/审批执行全链路已实现，本任务不伪造原申请已由真实OA批准。BE-14为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，未推送/合并；BE-28仍已交付待人工验收，其他任务按原依赖继续。

## 本机与组员接入

沿用已完成的身份/RSA/数据库配置；无新迁移、密钥或环境变量。三接口都是纯读取，POST也不传Idempotency-Key。使用总管理员NORMAL token可无管理上下文；权限管理员须带同一项当前任命的X-Admin-Appointment-Id、X-Admin-Appointment-Version、X-Admin-Scope-Version三个头。

- `GET /api/v1/admin/grants?page=1&pageSize=20`，可选employeeId、roleId、companyId、sourceDepartmentId、systemId、status（ACTIVE/REVOKED/INVALIDATED），响应200及过滤后total；无记录返回空页，不创建模拟授权。
- `POST /api/v1/admin/grant-baselines`，JSON为 `{"items":[{"employeeId":"已存在员工ID","roleId":"已存在角色ID","sourceCompanyId":"来源公司ID","sourceDepartmentId":"来源部门ID"}]}`。使用实际选定ID；无持久来源/关系但目标存在时返回逻辑0/null，employmentIds是该准确部门内全部当前适用任职。
- `POST /api/v1/admin/source-baselines`，JSON为 `{"employeeId":"已存在员工ID","companyId":"来源公司ID","departmentId":"来源部门ID","systemIds":["业务系统ID"]}`。即使员工已离开原部门，也可按当前管理范围读取周期；不存在来源为0，不需先造grant或改HR任职。

内部调用由B/C/D的ReadBoundary或CommandBoundary建立同数据源事务，再注入GrantReader；当前HTTP授权不能替代执行/提交时重新校验。`source(SourceKey)`返回内部ID或null及当前周期/业务版本；`baseline(Key)`返回内部锚点、角色事实、当前全部任职、ACTIVE grantId与eligibleForGrant；`grant(grantId)`读取准确历史版本、周期和固定任职证据。ID缺失先保持逻辑0，BE-16再负责同一guard内物化并比较，不使用row_version替代业务版本。

本任务不提供授予/撤销/关闭来源的公开写入口。B/BE-18仍负责完整有效权限合并，C/BE-20及执行器仍负责固定配对、审批、重查和整批原子处理，D/BE-16负责底层物化与变更；此端口的存在不代表这些任务已实现。
