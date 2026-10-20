# BE-16 授予、撤销与来源关闭原子端口

2026-10-20，成员D要求“接下来开始执行下一个任务”。BE-04、BE-14已交付共同main，以a860dc8为基线创建codex/be-16-grant-mutations。先设计、真实RED，再实现；本次范围为BE-16内部端口，不新增HTTP、迁移或OA调用。BE-14、BE-28人工验收继续待确认；此次开始开发不扩展此前特定任务的合并推送授权。

依据UC-03/04/10/11、既有API固定目标、数据库T0/T4/T5/T7和I-03/08/09；AT-14/15/22/41仅登记本任务实际覆盖的底层部分。C/BE-20～25负责草稿、规范字节与投影一致性、OA可信接入、成功凭据及恢复；D/BE-26/27/30负责生命周期入口、申请整份失效、claims释放及导入事件编排，端口存在不代表这些流程完成。

## 端口及事务边界

- BaselineWriter：在调用方同数据源WriteContext内物化source及永久baseline，缺失为0，已有锚点永不重置；不根据岗位/角色可用性拒绝建立历史锚点。供草稿及后续编排使用，不自行占用claim。
- GrantApplier.apply(context, requestId)：读取该申请全部固定change_target，不接受裁剪后的列表。复核可信批准持久状态、无成功凭据、每条claim、固定归属及版本，先全部检查，再全部写入。调用方须先核对固定规范字节/摘要/content/投影的一致性、原范围及当前执行身份；端口额外核对原scopeId当前覆盖，不要求原申请人账号或任命仍有效。无自建事务、无成功凭据/申请状态/claim释放，外层必须把审计、成功凭据、状态及释放同事务完成。
- 授予：当前员工ACTIVE、系统有效、角色EFFECTIVE/准确配置版本，配置引用完整且同系统/功能归属合法；保留停用权限不额外拒绝，不补授进入权限。准确来源部门须有当前任职，部门角色还须有角色所属部门当前任职，两者独立。每条baseline+1、新grantID/固定sourceEpoch，保存执行时来源部门全部当前任职证据，不裁为100项、不修改原申请固定employmentIds。新增grant不推进来源epoch/version。
- 撤销：准确grantId仍ACTIVE、归属及固定关系版本匹配，baseline+1，结束原grant并保存请求/时刻/原因；不要求当前岗位、员工状态、系统有效或角色配置版本。结束后重授新ID；旧撤销不得结束新grant。
- SourceCloser.close(context, selections, reason)：所有明确来源先物化0并比较expectedSourceEpoch/expectedVersion；每个新关闭事件epoch/sourceVersion/rowVersion+1，即使没有grant；其全部ACTIVE grant逐条baseline+1并INVALIDATED，历史证据不改，其他来源保留。
- SourceCloser.invalidateRelations(context, selections, reason)：供角色所属部门资格丧失、但来源部门资格仍在的编排调用；准确关系版本比较并推进baseline，结束ACTIVE（如有），来源周期不变。无ACTIVE也返回关系事实，供外层失效该关系旧已提交授予；不自行推断生命周期事件。
- 两个关闭端口返回完整不可变AffectedFacts（来源前后周期/版本、关系前后版本/准确grantID/状态）；不反调M07，不做HTTP可见性投影。来源系统的管理范围及关权原因由外层入口授权，关闭端口只接受已选择固定键/版本。
- 所有失败均将原WriteContext标记rollback-only，调用方捕获也不能提交之前的写入。版本及存储修订上限9007199254740991，任何一项溢出全批拒绝。guard串行化两种关闭/执行顺序；审计失败由同一事务回滚全部。

GrantReader的当前资格补齐为独立检查来源部门及角色所属部门任职；授权证据仍绑定准确来源员工/公司/部门，不把角色部门任职替换成来源。历史读取不依赖当前任职，不改已有合法历史。

## 验证计划

专属非3306 MySQL8.4、随机schema/DML账号，不操作用户现有库。先以未实现桩观察业务失败，再GREEN；覆盖零物化永久性、已批准整批授予/撤销、同源去重/异源共存、完整执行时证据、独立资格及关系关闭、固定目标/claim/状态/范围拒绝、版本边界、两连接guard关闭/执行次序、证据和成功审计故障回滚、捕获内层异常仍全回滚。最终完整clean verify/mysql-check及静态追踪，记录真实数量和专属资源清理；负责人验收与主线交付分开。

## 实际验证与交接

2026-10-20已完成以下验证，日志只存backend/.tmp忽略目录，长期证据以本记录及可重复测试为准。

| 阶段 | 实际结果 | 范围 |
| --- | --- | --- |
| 正式端口RED | 12项，5失败、6错误、1通过；未实现桩抛UnsupportedOperationException而非业务结果/拒绝 | be16-valid-red.log。此前夹具试图改角色归属，被既有不可变触发器拒绝，已修正为创建时固定部门；那次不作有效业务RED |
| 首轮GREEN | 12项真实MySQL全通过 | be16-first-green.log；零锚点、完整101项执行时证据、精确撤销/新ID重授、异源保留、整批版本复核、证据/审计回滚 |
| 独立资格读取RED | 1项失败、0错误：两部门均有适用任职，预期eligibleForGrant=true，实际false | be16-qualification-red.log。专门针对旧GrantReader强制部门相同的分支，再修正为两个独立任职检查 |
| 扩展GREEN | 20项真实MySQL全通过 | be16-expanded-green.log；完整双目标授予/撤销、第N条故障回滚、关系单独资格失效、原范围而非原任命存活、过期/错误数据源上下文、双连接guard竞争 |
| 最终完整构建 | Surefire 112项/19组；Failsafe 215项/17组；失败/错误/跳过均0 | be16-full-verify.log，backend目录显式JDK21执行 `./mvnw.cmd -q clean verify -Pmysql-check`，含本任务22项真实MySQL及全部既有HTTP/事务回归 |

最终完整回归于2026-10-20 16:58（Asia/Shanghai）结束。本任务22项还验证：来源与角色部门均有任职即可授予；丧失角色部门资格只推进指定关系，保留同来源其他角色与来源周期；无ACTIVE时也能推进关系基线阻断旧批准；同原子事务明确END→close→ADD不抹去关闭事实；零锚点新物化遇后项陈旧会回滚；业务版本和存储rowVersion溢出均整批拒绝。原申请target_snapshot保持原字节JSON内容，实际grant证据保留当前来源部门完整任职；已结束证据的删除被既有不可变触发器拒绝。

两独立线程/连接竞争时，第二个guard工作体等待第一个提交，额外自动提交连接看不到第一事务的半份grant/周期。执行先提交则关闭使刚生成grant变INVALIDATED；关闭先提交则执行拒绝旧epoch，未生成任何grant。此证据仅证明M06原子端口；申请状态持久INVALIDATED、整份claims释放、可靠OA和恢复调度仍待对应C/BE-26实现。

最终jar `backend/target/backend-0.0.1-SNAPSHOT.jar` SHA256：`d60fe48dd616786e300f7f5c32e0566a3e6b6713d111692a398c0b5213b8c83d`。验证仅专属127.0.0.1:23328/MySQL8.4.8及仓库临时datadir；清理回查随机schema/账号0/0、测试密钥目录0。已核对mysqld实际服务子进程39780来自本任务启动父进程37732，正常关闭日志及退出均确认，用户MySQL84仍Running；未操作用户现有库。清理时路径尾斜杠及Windows子进程PID检查先拒绝，规范化路径并核对实际进程链后才关闭，没有绕过实例身份校验。

无新迁移/依赖/环境变量，原API及V1～V3摘要保持。BE-16登记IMPLEMENTED、ownerAccepted=false、mergedToMain=false；未合并推送，不把测试夹具的合成批准/外层成功记账当成真实OA或C链路完成。BE-14/28人工验收仍开放。静态校验记录另见validation.json，其本身不运行Java/SQL/HTTP。

## 调用方接入及负责人核对

Spring已注册四个事实/写端口。内部调用顺序：

1. 草稿/提交由当前身份及完整管理范围授权后进入CommandBoundary，在同一WriteContext调用BaselineWriter.source/baseline物化稳定ID，随后由C保存固定目标和claim；只物化不推进业务版本。
2. C执行器在同一guard中验证规范固定字节/摘要、content/投影一致性、可靠OA、当前执行主体和原范围；调用GrantApplier.apply(context, requestId)，端口总是加载并检查该申请完整固定目标，返回所有变更事实。外层继续append成功审计、execution_success、EXECUTED状态、attempt及claim释放，任何失败共同回滚。成功回放/失败重试调度由外层处理，不能直接再次调用端口当作幂等API。
3. 直接关来源及员工/任职事件由生命周期入口先授权全部准确来源，用GrantReader.source读expectedSourceEpoch/expectedVersion，调用SourceCloser.close；每个新关闭事件都推进，旧expected拒绝。角色部门资格丧失但来源仍合格时，按准确key/关系版本调用invalidateRelations。保留动作原顺序，恢复任职不自动恢复关系。
4. AffectedFacts包含完整内部来源前后快照及关系前后版本、准确grantID/状态；无ACTIVE的关系失效仍返回该baseline、null grantID；无grant的来源关闭仍返回来源事实。外层按这些事实调用C的RequestInvalidator使命中未终结已提交申请整份失效并释放全份claim，且同事务审计/幂等响应。此数据不可直接作为受限管理员的HTTP结果或幂等缓存，需安全投影。

负责人可先核对本文件22项行为与测试断言、上述完整报告，并确认三端口已由Spring装配。本任务没有新浏览器地址或写HTTP可供点击验收；当前已有健康/查询仍按BE-14步骤核对，不能将健康UP当作写端口验收。若需重跑本任务，在显式专属非3306 MySQL及RBAC_ISOLATED_MYSQL_PORT/RBAC_ISOLATED_MYSQL_DATADIR配置就绪后，从backend运行 `./mvnw.cmd -q test-compile failsafe:integration-test failsafe:verify -Pmysql-check -Dit.test=GrantMutationsMySqlCheck`；测试自行建立/清理随机库和最小DML账号，禁止指向现有dev/test库。完整验收用上述clean verify命令。
