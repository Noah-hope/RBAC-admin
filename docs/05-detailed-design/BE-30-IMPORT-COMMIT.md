# BE-30 导入整批提交、历史与失败恢复

2026-10-21 / D-101：成员D要求完成BE-26推送后自行继续下一项。已核对远程main及交付记录到`d79e28a624fd4b9bf3862063c44274d3db602248`，建立`codex/be-30-import-commit`。BE-29已交付，A/BE-27仍PLANNED，主线缺少员工全局状态与任命、会话失效的统一生命周期内部编排。本轮完成设计、实际端口核对及TDD验收清单；BE-30保持PLANNED、implementationStarted=false、testsRun=false，未新增业务代码或执行本任务Java/SQL/HTTP。BE-26的139离线＋350真实MySQL/HTTP是上一项集成交付证据，不能计作BE-30验证。

## 依据与依赖门槛

沿用V1.3及[UC-10](../01-requirements/usecases-v1/UC-10_导入同步员工组织信息.md)、[管理范围与人员权限规则](../01-requirements/usecases-v1/管理范围与人员权限规则.md)、[OpenAPI](../03-api/openapi.yaml)的commitImport、[T0/T8/T9](../04-database/TRANSACTIONS.md)及[F5/F6/F7相关编排](CORE-FLOWS.md)。验收映射AT-24/25/27/28/29/41只在实际运行后登记本任务局部证据，不重开需求或修改契约。

| 能力 | 当前可验证事实 | BE-30接入要求 |
| --- | --- | --- |
| BE-29冻结预检及报告 | 已交付main/6506cda，冻结完整输入、候选ID/code、逐项前后值、动作顺序和依赖 | 消费已sealed计划；不重新解释文件，不重新分配候选身份 |
| BE-27跨域生命周期 | 任务仍PLANNED；现有主线没有员工启停/范围替换业务实现 | A交付实际可复用的同事务端口及测试后才正式实施；不以BE-26直接关来源替代全局停用 |
| BE-26申请失效 | 已交付main/b00ffc9，统一RequestInvalidator及直接关来源 | A/D复用同一失效实现，不复制申请状态机；不以任命撤销单独取消原范围仍覆盖的申请 |
| BE-10/16基础写 | EmploymentWriter、SourceCloser及受影响事实已交付 | 使用调用方WriteContext，无逐行独立提交；保留来源部门与角色所属部门两种资格 |
| 身份/guard/幂等/审计 | 已有公共实现 | 首次、实际写和成功回放均复核当前NORMAL总管理员；批次成功与HTTP成功同事务 |

正式开工前重新核对远程主线、BE-27来源/合并SHA及真实验证。仅有HTTP入口、文档或空接口不满足端口依赖。不跨成员实现BE-27，不将本轮继续指令扩展为下一任务合并推送或部署授权。

## 已核对的内部接入点

| 现有位置 | 可复用能力 | 待衔接事项 |
| --- | --- | --- |
| `imports/ImportPlan` | Input、Change、Batch、八字段Counts及Effects | 原样保留rowNumber/planNo与内部candidate targetId，公开报告继续隐藏新对象ID |
| `imports/ImportPlanService` | 已sealed批次和报告、同来源批次防重 | load和计划加载当前为私有读实现；实施时按TDD抽取同数据源仓储，T0内直接读取，不从HTTP或另一个读事务编排写入 |
| `imports/ImportSnapshot.requireCurrent` | 调用方事务内复核集合指纹，排除本批自身计划 | 在第一项正式DML之前调用一次，不能每执行一行后拿旧快照比对本批自己的变更；逐步版本另按固定动作检查 |
| `imports/ImportFileService.get` | 不可变文件与SHA校验及当前总管理员资格 | 大文件读取放guard外；T0内再核对固定元数据与全部批次依赖，不在成功回放时重复打开已完成批次原文件 |
| `people/EmploymentWriter` | organization/employee/sourceIdentity/organizationMapping/startEmployment/endEmployment/moveDepartment | 仅接受ImportAuthority，含操作者、批次ID/version/digest、原因和requestId，并在同一上下文复核资格；不伪造普通账号上下文或另开事务绕过 |
| `grant/SourceCloser` | close/invalidateRelations，返回完整AffectedFacts | 任职事件驱动关闭实际相关来源或准确关系；不是把全部系统一律关闭 |
| `lifecycle/RequestInvalidator` | sources及Changes统一失效、全部claim/outbox处理 | 通过生命周期调用链使用一次同一端口，保留固定内容与历史；公开结果不泄露不可读申请 |
| `identity/IdentityRepository.revokeAll` | 现有单账号会话撤销原语 | 不能替代BE-27的员工启停、关联账号版本与任命失效完整编排 |

交给A/BE-27的衔接需求是行为约束，Java签名尚未确定：内部能力须接收当前WriteContext和已认证导入主体，支持全局停用/恢复与任职变化后重检，保留来源/关系/任命/会话及申请失效的准确结果。同批END后ADD必须即时执行离岗失效，不能仅按最终名单复核。任命失效、关联后台账号/会话处理和防自锁均须同事务，可供导入嵌入；不得调用其外层HTTP/幂等流程造成嵌套提交。先核对A实际交付再确定适配，不先建立无人实现的接口。

## 入口和成功事务

唯一新增正式操作为`POST /api/v1/admin/import-batches/{batchId}/commits`，输入严格ImportCommit：expectedVersion、64位小写十六进制contentDigest，拒绝未知字段、错误类型、非法标识和超限版本；必须UUID Idempotency-Key。成功200 ImportBatch、Cache-Control:no-store，幂等回放使用既有标头。

1. 在独立读快照验证当前NORMAL总管理员与已sealed批次；准备固定计划和原文件SHA，昂贵读取在guard外完成。未登录401，受限会话403，非总管理员的批次资源与不存在统一404；不把客户端提供的员工/批次操作者当作身份。
2. 幂等规范输入绑定operationId、method、含batchId的path及完整ImportCommit；复用公共HMAC/租约/失败记录机制，成功回放也检查当前总管理员。不同key不能绕过同来源批次长期成功结果。
3. 真正执行进入同一T0，先guard、当前资格，再读取批次。SUCCEEDED在当前授权下返回原批次实际结果，不再按旧expectedVersion触发重执行或校验已消耗的计划依赖；同key异内容仍按公共幂等规则409。
4. 未成功批次核对expectedVersion/contentDigest及状态：BLOCKED按契约422，STALE为409，只允许READY或可重试FAILED。复核sealed计划完整性、内容摘要、原文件摘要、映射/目标版本、候选唯一键、引用集合与防自锁。不能在确认时删除失效行、跳错或改换候选ID。
5. 按冻结rowNumber/planNo及依赖顺序应用组织、员工、来源身份映射、组织映射和任职变更。每步校验固定前后值和目标版本；同一对象多步使用计划里的逐步版本，不用批次最终状态重算动作。全部1000源行在一个事务，分段flush不等于提交。
6. 每个END/DISABLE等事件同事务嵌入BE-27生命周期。最后来源部门任职丧失推进来源周期；仅角色所属部门任职丧失按准确关系处理，其他独立来源和适用兼岗保留。同批重新ADD/RESTORE不恢复已关闭来源、旧grant、任命、会话或旧申请。
7. 使用已冻结候选ID/code及显式旧新源键。组织更名不换身份；无历史引用的部门迁移复用moveDepartment及映射的合法即时FK顺序，有正式/历史引用则拒绝，不改旧历史公司。
8. 实际八字段Counts从本次成功事实累计，不能直接复制plannedCounts。业务DML、相关历史和审计、批次SUCCEEDED/version与row_version推进、actual_counts/committed_at、HTTP成功结果同事务提交。任一失败回滚全部正式数据，不新增后台账号、角色、业务授权或OA申请。

## 失败记录与恢复

原事务回滚后，另起T0先重查批次和是否已成功，身份仍须有效；未知提交结果先回查数据库，数据库不可用时不谎记FAILED。成功事实优先，不能被并发错误记录覆盖。

依赖陈旧或候选唯一冲突只允许在仍未成功的同一冻结批次上记录STALE/version+1，要求新批号重新预检。临时存储故障记录FAILED和契约允许的安全码，保留全部固定输入和基线；重试重新验证总管理员、状态、版本、摘要及完整依赖。不能把静态BLOCKED当临时失败，不能修改sealed计划重试。安全故障码以既有Problem/ImportBatch契约为准，具体映射在实现设计补充，不输出SQL、凭据或完整异常。

同批成功后响应丢失返回原actualCounts；新批相同资料重新预检应UNCHANGED，不复活ENDED任职或INACTIVE员工。无需新增自动导入调度；由既有commit入口按可重试状态恢复，不把C的申请执行恢复误当导入批次恢复。

## TDD验收清单（尚未运行）

| 场景组 | 独立观察与断言 |
| --- | --- |
| 1 身份和输入 | 401/受限403/非总管理员404；错误类型、字段、digest、版本及key拒绝；拒绝后正式表和批次无写入 |
| 2 状态和长期回放 | READY成功；BLOCKED422、STALE409；SUCCEEDED旧版本同批回读；同key异内容409、降权后无法回放 |
| 3 固定计划完整性 | 文件SHA、目标/映射版本、候选code/源键唯一冲突、空集合新增、计划摘要变化均拒绝；不重新分配候选对象 |
| 4 组织与身份 | 同名不同员工、兼岗、稳定重命名及源键重映射；新对象无后台账号、任命、角色或授权 |
| 5 同批与新批防重 | 两连接不同key竞争同批仅一次成功；响应丢失回读原计数；新批重复资料UNCHANGED |
| 6 1000行全批 | 合法1000源行全部提交；第N条故障全部正式DML/历史/业务审计回滚；不存在100行部分提交 |
| 7 来源与角色任职 | 结束来源最后任职推进其所有已物化来源；仅失去角色部门资格准确结束关系；其他公司/部门来源保留 |
| 8 Q-01动作顺序 | 同批END最后任职后ADD新任职，周期/关系/任命及旧申请持续失效；尚有适用兼岗或仅岗位更名不误关来源 |
| 9 全局停用及恢复 | 关联后台会话和全部来源、任命持久失效；RESTORE只恢复身份，旧批准迟到不能复权；自锁停用整体拒绝 |
| 10 整份申请失效 | 混合清单一条受影响整份失效，释放所有claim并停止可停outbox；迟到回调只留历史，原申请人离任本身不取消仍覆盖申请 |
| 11 部门迁移 | 无引用合法迁移及稳定映射即时FK；预检后新增角色/任职/审计/申请/历史引用则整批STALE，旧历史归属不改 |
| 12 最后审计失败 | 来源/关系、任命/会话、申请及全部基础数据回滚；无SUCCEEDED/actualCounts，安全失败另事务按当前批次状态处理 |
| 13 失败与成功竞争 | 原业务回滚后另一连接成功时，错误记录器不得覆盖SUCCEEDED；数据库不可用不虚构已落库故障 |
| 14 重试完整复核 | FAILED保留冻结内容，完整复核通过才重试；依赖已变转STALE，恢复身份/范围不能复活旧计划；版本耗尽不截断 |

BE-27交付后先写并运行真实commit路由失败测试，确保不是夹具/参数错误，再实现最小提交编排；按上述场景逐组RED/GREEN。使用显式非3306专属MySQL、随机schema/DML账号及仓库临时密钥/文件；禁止连接用户dev/test库。最终运行完整`./mvnw.cmd -q clean verify -Pmysql-check`、静态设计校验和资源清理，并记录真实命令、失败/通过计数、jar摘要及远程基线；不把这里的计划写成测试通过。

## 当前结果与后续

本轮只准备，无正式ImportCommit服务、路由、Java/SQL/HTTP或测试运行。BE-30仍PLANNED，13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED不变；既有D各任务人工验收继续开放。待A/BE-27实际交付后，从最新main接入真实内部生命周期端口，再按本清单正式TDD实施。当前准备只本地提交，不推送或合并新任务。
