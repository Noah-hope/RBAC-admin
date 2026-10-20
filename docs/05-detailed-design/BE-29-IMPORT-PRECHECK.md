# BE-29 冻结导入预检、差异与问题报告

集成验证清理：随机schema/账号回查0/0，测试密钥及文件目录无残留；专属MySQL子进程41836/父进程24424正常关闭，原MySQL84仍Running，未操作用户现有库。

D-084集成结果（2026-10-20）：接入远程main/06a6b5c（A已验收并经PR#3交付的BE-09）后，完整clean verify/mysql-check通过，137项离线/20组＋240项真实MySQL/HTTP/19组，失败/错误/跳过0，日志backend/.tmp/be29-main-integration-verify.log。集成未改BE-29业务代码，静态追踪通过；BE-29保持IMPLEMENTED待D人工验收，本次主线交付由用户“合并推送吧”明确授权，实际SHA成功后补。 集成jar SHA256为 `4534fe14d81d8d9999b589ffd2152ba82a64a79bcf7ec3a5f536e5e83cf26c5a`。

D-084编号协调（2026-10-20）：BE-29原本地实施记录D-081与远程A/BE-09重复，现统一为D-084；原实现80abde1及真实RED/GREEN事实保留，A的D-081/082/083、验收与PR#3交付均保持。已接入main/06a6b5c，业务代码无冲突；BE-29仍IMPLEMENTED、ownerAccepted=false，待集成回归与本次授权的主线交付。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED。

2026-10-20，成员D要求“接下来进行下一项任务”。已fetch核对共同main/072585e，在codex/be-29-import-precheck实施BE-29；直接依赖BE-10、BE-17、BE-28均已交付。BE-26仍缺C/BE-20，BE-30仍待BE-27/29，不跳过依赖实施。既有BE-10/14/16/28人工验收保持开放；本次不扩展BE-16特定合并推送授权。

依据UC-10 V1.3、ImportInput/ImportBatch/ImportPlanChange及五个既有操作、V1～V3、T0/T8/T11、AT-25/26/27/29。无新迁移、依赖或真实HR集成；详细输入、冻结计划与正式提交分开。

## 数据流与边界

1. POST /api/v1/admin/import-batches 仅当前NORMAL总管理员；严格ImportInput解析，拒绝未知字段、重复/歧义映射及非法参数。复用BE-28按明确worksheet读取不可变原文件，解析及SHA核对在guard外进行。
2. fieldMappings按表头名称精确匹配，不猜相似名称；首个非空行作为表头，保留原始物理行号，最多1000源行。组织路径使用精确的公司名/部门名/岗位名层次，缺编码的组织初始化按完整路径建立候选ID/code及映射；名称含路径分隔歧义时要求明确编码/映射，不按短名称合并。公司/部门/岗位不启停，不接受部门嵌套。
3. ORGANIZATION按公司→平级部门→岗位处理稳定编码或明确路径映射，生成CREATE/RENAME/MOVE计划。EMPLOYEE_INITIALIZATION以来源唯一employeeKey（可附employeeId）定位员工，冻结源身份映射，允许兼岗；同员工属性冲突阻断，相同任职重复警告去重。EXPLICIT_ACTIVE或FROM_SOURCE明确初始任职状态，不伪造入职日期，已有INACTIVE员工/ENDED任职不因重复初始化恢复。
4. PERSONNEL_CHANGES要求明确action，支持CREATE/RENAME/REMAP员工、ADD/END任职、DISABLE/RESTORE员工；变更已有对象须准确ID/源键及expectedVersion。结束任职使用准确employmentId，不从缺行推断离职；来源键重映射要求旧新键明确且无冲突。动作按源行固定顺序模拟，不为保权自动改序。
5. 纯ImportPlanner在调用方新RR业务快照上生成计划：完整规范前后值、源键、组织代码、候选稳定ID、每步目标版本、唯一键不存在断言、映射写计划和生命周期影响。新对象内部预分配UUID，changes接口按契约targetId=null，候选ID只留内部计划；候选code在changes接口回读。计划created/updated/unchanged按可见对象动作计数，END/DISABLE/RESTORE及失效各自单独计数，映射附在所属对象动作内。
6. 任职/员工变更逐步模拟资格丧失：来源部门最后任职丧失推进相关已物化来源；部门角色资格丧失仅影响其关系；其他来源/兼岗保留，恢复不复权。收集将失效的grant/任命及相关依赖，复用BE-17 ImpactService生成同快照影响与集合指纹。自锁停用、歧义身份/组织、非法跨公司归属、有正式或历史引用的部门迁移均BLOCKED。
7. inputDigest覆盖完整规范ImportInput和文件SHA；sourceSystem+sourceBatchId相同输入返回既有冻结批次，不同输入409 SOURCE_BATCH_CONFLICT，BLOCKED也不原地修改。HTTP Idempotency-Key复用基座，当前总管理员资格在首次及重放都复核。
8. 只读预检后在同一WriteContext复核当前资格、同批防重及相关依赖/集合（含空集合、映射与唯一性）；再插入未sealed批次、全部plan_row/issues，最后sealed_at赋值、成功审计及HTTP结果一起提交。任何一步失败全回滚，没有可见半份报告。原组织/员工/任职/授权/申请/任命/会话不写入，不物化来源/关系。
9. GET import-batches、/{batchId}、/{batchId}/issues、/{batchId}/changes只读已sealed批次，稳定分页/筛选/no-store，报告保持冻结时的差异与版本，不跟随当前正式数据变化；非总管理员列表403、详情及子资源与不存在统一404，未登录401、受限会话403。

依赖快照只选必要业务字段，不读取密码、密钥、会话正文或个人无关资料。组织迁移引用检测包含角色/任职/范围/来源/目标/审计以及其他冻结计划/预览的引用；本批自身计划不造成自引用陈旧。BE-30应复用本计划与依赖复核，不重新解释原文件或重新分配候选身份。保守集合指纹可能要求不相关变更后重新预检，不能以放松检查换取错误覆盖。

## TDD及交接计划

先纯规划器RED（候选组织、稳定身份、重复/冲突、顺序生命周期、迁移、自锁与依赖）及真实HTTP/MySQL RED（尚无五路由）；再实现、定向GREEN、完整clean verify/mysql-check和静态追踪。真实数据库仅仓库临时datadir/非3306专属实例及随机schema/DML账号，不操作用户现有库。覆盖1000行、XLSX明确选表、同批/同key防重、冻结触发器、最后审计故障回滚、资格/文件/依赖变化及并发冻结。

实现、负责人验收和主线交付分别记录，AT只登记本任务局部证据；不将预检READY或模拟影响记为正式导入、BE-26/27/30或真实OA/HR链路完成。

## 实际验证与状态

2026-10-20已完成设计、实现及以下实际RED/GREEN。日志只保留在backend/.tmp忽略目录，长期证据以本记录和可重复测试为准。

| 阶段 | 实际结果 | 观察及修正 |
| --- | --- | --- |
| 规划器正式RED | 14项失败、0错误 | be29-planner-valid-red.log；空规划桩未产生候选、差异、问题及生命周期影响。此前测试泛型编译问题已修正，不作为RED |
| 规划器首轮GREEN | 14项全通过 | be29-planner-first-green.log；按源键而非姓名识别、候选组织、重复/属性冲突、精确任职、迁移和防自锁 |
| 正式HTTP/MySQL RED | 8项失败、0错误，目标路由404 | be29-http-red.log；既有文件/身份已真实启动，不使用Mock路由 |
| 首轮HTTP实现 | SQL保留字row_number未引用导致500，修正后7/8通过 | be29-http-first-green.log、be29-http-second-green.log；余项仅因幂等结果存MySQL JSON后字段顺序变化，改为比较JSON语义，不要求对象键序相同。包引用编译错误及一次审批超时未执行均不作业务RED或通过证据 |
| 边界RED | 20项中5失败；候选/依赖2项均失败；若干单独用例真实失败 | be29-boundary-red.log、be29-freeze-boundary-red.log、be29-explicit-code-red.log、be29-event-order-red.log；独立复现其他部门多关闭、源存储版本上限遗漏、别名冲突、完整路径、候选ID提前披露、权限空集合新增漏检和事件被错误去重 |
| 映射边界RED | 2项失败；另1项新编码用例失败，均0错误 | be29-mapping-red.log、be29-candidate-code-red.log；矛盾公司/部门映射须阻断，同一路径文字的合法无引用迁移保留映射ID，明确映射允许选择新编码 |
| 最终定向GREEN | 25项规划器；13项真实HTTP/MySQL，均0失败/错误/跳过 | be29-planner-final-green.log、be29-http-final-targeted.log及最终完整报告。包括1000行/1001拒绝、XLSX只选明确工作表并保留第7行物理行号、真实grant影响与无正式变更 |

最终完整回归于2026-10-20 18:10（Asia/Shanghai）结束：backend工作目录显式JDK21执行 `./mvnw.cmd -q clean verify -Pmysql-check`，Surefire **137项/20组**、Failsafe **228项/18组**，失败/错误/跳过均0，包含本任务25项离线＋13项真实MySQL/HTTP及共同主线全部回归，日志be29-full-verify.log。jar `backend/target/backend-0.0.1-SNAPSHOT.jar` SHA256为 `bcb1fe4a97f0dafc8bccccbc340e5c12988d14ddf3c238f98b1e6576e8137f64`。

真实断言覆盖：同key回放、同源批次跨key返回同一冻结结果、字段映射顺序规范化、异输入SOURCE_BATCH_CONFLICT；正常总管理员/权限管理员/受限会话/未登录边界；已sealed批次和问题/计划不可改；后续正式资料变更不改旧报告；第N步seal或审计失败整份回滚；两个并发请求只产生一个源批次；依赖空集合新增/目录权限新增可检测，捕获依赖拒绝仍不能提交外层写入。生命周期纯规划按明确事件逐步记录来源前后周期/版本，影响数量去重不合并两个关闭事件；没有受影响业务系统时仍冻结任命计数及完整依赖，有业务系统时复用BE-17保存安全影响及集合指纹。

仅使用专属127.0.0.1:23328/MySQL8.4.8和仓库临时datadir，随机schema/最小DML账号完成后回查0/0，测试密钥及文件目录0。实际服务子进程5140/父进程1216已核对并正常关闭，用户原MySQL84仍Running，未操作用户现有库。部分测试文件元数据通过受控测试夹具配合真实ImportFileStore/Parser建立；授权影响使用持久测试grant，不代表C的真实OA批准执行已接通。

BE-29为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，只本地任务提交；无新迁移/依赖/环境变量、原API及V1～V3摘要保持。完整静态校验见validation.json，其自身不运行Java/SQL/HTTP。既有BE-10/14/16/28人工验收仍待确认，不代记OWNER_ACCEPTED，不合并推送或自动开始BE-26/30。

## 接入和负责人验收

重启本地应用即可加载五路接口，沿用BE-28的文件目录、身份和幂等配置。使用NORMAL总管理员token，POST传UUID格式Idempotency-Key；读取不需幂等key，均no-store。

可将以下明确虚构内容保存为UTF-8 CSV，通过既有POST /api/v1/admin/import-files上传并取得真实fileId：

```csv
companyName,departmentName,positionName
预检示例公司,预检示例部门,预检示例岗位
```

POST `/api/v1/admin/import-batches`（把fileId换成上传结果，批号使用本次选定唯一值）：

```json
{
  "fileId": "uploaded-file-id",
  "sourceSystem": "LOCAL_CHECK",
  "sourceBatchId": "be29-org-001",
  "importType": "ORGANIZATION",
  "worksheet": "CSV",
  "fieldMappings": [
    {"sourceColumn": "companyName", "targetField": "companyName"},
    {"sourceColumn": "departmentName", "targetField": "departmentName"},
    {"sourceColumn": "positionName", "targetField": "positionName"}
  ],
  "organizationMappings": [],
  "initialEmploymentState": "EXPLICIT_ACTIVE",
  "reason": "核对只读预检与冻结报告"
}
```

预期201、READY、plannedCounts.created=3、actualCounts=null；这只分配候选编码，不创建正式组织。读取 `GET /api/v1/admin/import-batches/{id}/changes?pageSize=10` 核对三动作、原行号2、新对象targetId=null和候选stableCode；读取 `/{id}`、`/{id}/issues?severity=BLOCKING&pageSize=10` 及列表 `?sourceBatchId=be29-org-001&pageSize=10` 核对批次与分页。相同输入重试返回同一id及摘要；保留源批号却改变原因/映射则409 SOURCE_BATCH_CONFLICT，应以新批号重新预检。

BLOCKED报告可另上传仅有action列、数据为不支持动作的PERSONNEL_CHANGES文件：接口仍201，但state=BLOCKED并在issues返回原行号、字段及INVALID_ACTION；不能据此继续正式提交。结束/停用等报告只展示冻结计划，调用现有查询确认原员工/任职/grant仍未变化；本轮不提供commitImport实现。

内部端口：ImportPlanner只读传入快照，返回完整Change/Issue/Counts/Effects；ImportSnapshot.capture(excludingBatchId)必须在调用方同数据源事务内，requireCurrent必须使用同一WriteContext。依赖采用保守集合指纹，包含空集合、映射、唯一性候选及目录/权限变化；不相关变化也可能要求新预检。BE-30应排除本批自身引用、按被冻结的row_number/plan_no顺序使用normalized_values、mappingWrites和dependency_snapshot，并重新校验版本/唯一性/自锁。新对象ID已在内部target_id冻结，公开changes不提前暴露；FROM_SOURCE的历史ENDED初始化用stampRecordedEndAt指明提交时记结束记录，不伪造源业务日期或复活旧任职。

重跑需先准备专属非3306 MySQL和RBAC_ISOLATED_MYSQL_PORT/RBAC_ISOLATED_MYSQL_DATADIR；backend执行 `./mvnw.cmd -q -Dtest=ImportPlannerTest test` 及 `./mvnw.cmd -q test-compile failsafe:integration-test failsafe:verify -Pmysql-check -Dit.test=ImportPrecheckMySqlCheck`，完整验收执行上述clean verify。测试会自行创建/清理随机库与账号，禁止指向现有dev/test库。实际机器验证与D本人验收分别记录。
