# BE-21/23/24/25/31 申请审批与执行

D-091（2026-10-21）：C明确确认既有查询交付并继续全部剩余任务。基线main/7914788，同一任务分支按直接依赖连续实现；实现与负责人确认、主线交付分开记录。

## 实施计划和目录

1. BE-21：change/SubmissionService、CanonicalPayloadCodec；复用DraftPlanner、PreviewService与ImpactService，完整目标分类/依赖/占用重检；RFC8785固定七字段摘要包，PENDING与outbox/审计/幂等原子提交。
2. BE-23：oa/OaDeliveryService、OaSubmissionPort；guard领取租约和发送前复核，网络在事务外，同requestId固定重试；落库复核回执，失效在途仅留历史关联。
3. BE-24：oa/OaResultService；可信OA当前身份、可靠关联、事件/固定版本摘要/唯一最终结果三重去重；接收独立提交，批准不授予，驳回释放占用。
4. BE-25：execution/ExecutionService；全部固定字节/投影/原范围/资格先复核，复用RoleConfigurationApplier和GrantApplier，业务/成功审计/凭据/终态同事务；回滚后单独记录安全失败和退避，启动与每30秒恢复。
5. BE-31：demo显式适配器与受控CLI；稳定DEMO回执，真实持久状态，原回调API演示最终结果；无公开批准/执行/签发接口。

## 验收行为

以UC-02/03/04/11、API submitChange/changePreview/retryOaDelivery/receiveOaResult/deliverOaSubmission、数据库T3/T4/T7/T11及I-03/08/09/11为依据。先运行失败测试再实现：过期/陈旧/换上下文预览；无变化不投递；混合清单与全部任职证据；冻结后不可改；原子审计故障回滚；租约竞争/超时/失效在途；回调身份和重复冲突；批准可靠但权限未变；执行全部成功或全部回滚/失败重试；成功只一次；原scope而非原账号/任命；源周期与关系版本失效；demo关闭默认不可用。

JCS采用io.github.erdtman:java-json-canonicalization:1.1，Apache-2.0，版本/API来自[作者仓库](https://github.com/erdtman/java-json-canonicalization)；[RFC8785](https://www.rfc-editor.org/rfc/rfc8785)向量及本项目固定包独立验证后使用。固定集合与明细按稳定业务键排序；拒绝非法Unicode及重复JSON键，禁止普通序列化冒充JCS。

开发按以下实际RED/GREEN推进；最终完整回归与资源清理结果在执行完成后登记。验证仅专属临时MySQL，不接用户既有库或真实外部OA，不代BE-33/34服务器与全系统验收。

## 实现与边界

- `SubmissionService`先完整当前授权，复用DraftPlanner分类及物化基线；预览5分钟绑定草稿版本/原因/完整清单、当前角色和员工/任职名称版本、无变化分类和ImpactService依赖集合摘要。新增适用任职、权限目录或实际影响集合变化需重预览；不静默裁剪新增无变化。
- 实际明细按稳定员工/角色/来源/准确grant业务键排序，集合ID稳定排序；content与change_target一起固定。target_snapshot保存当时角色、权限名称/版本、公司/部门/系统、员工及完整适用任职证据。空配置提交422；全部无变化200保留草稿，不创建outbox。
- 固定包严格为`{requestId,submittedVersion,content,reason,originalAuthority,submittedByAccountId,submittedAt}`，UTF-8 RFC8785原字节与SHA256一同保存，submittedVersion取draftVersion，后续状态推进不改固定版本。`FixedRequestStore`逐项校验原字节/摘要/七字段包、数据库固定字段和完整目标SQL投影。UTC时刻与公开submittedAt一致。
- `OaDeliveryService`guard领取60秒随机租约→另guard重新检查→事务外适配器→guard条件回写；当前状态、原lease和唯一可靠关联一起约束，未关联超时同requestId重试。失败5秒至5分钟退避。仅PENDING/SEND_FAILED允许人工重试，statusVersion匹配且当前整份可见；保存幂等事实包含来源部门与角色所属部门，降权后不能重放旧跨范围结果。
- `OaResultService`重新读取可信OA当前服务身份，可靠link缺失409/OA_LINK_PENDING并Retry-After=5，不消耗事件号。实例/eventId规范内容、固定版本/摘要/审批号、最终决定三层核对；相同事件异内容或相反决定409，另eventId同决定只留诊断200。可靠事件/final_result/状态/审计同事务；批准202/PENDING并不写业务权限，驳回释放claims，迟到失效结果只留历史。
- `ExecutionService`始终先guard再success，完整固定投影、原scope及全部资格先复核；原账号/任命撤销不代替原scope判断。配置与授予/撤销分别复用B的RoleConfigurationApplier和D的GrantApplier；精确撤销不机械要求系统/配置启用。任何一项不合法整份INVALIDATED并释放全部占用，不更新合法子集。配置推进由外层失效命中的旧提交授予，避免M05反向依赖M07。
- 正式业务、版本/任职证据、配置历史、成功审计、execution_success、attempt、终态及claims同事务。第N条或审计故障回滚后，FailureRecorder另guard重新检查success/状态/整份条件，安全记EXECUTION_STORAGE_FAILURE与退避；不能覆盖并发成功或失效，恢复仍完整复核，重试次数不自动取消。
- `WorkflowRecovery`仅正常身份应用装配，启动和每30秒扫描持久outbox/APPROVED/到期EXECUTION_FAILED；不依赖易失回调通知。`DemoOaSubmissionAdapter`仅demo profile，由真实固定申请与目标OA实例重建稳定DEMO回执，不保存内存Map、不自动批准。
- 独立`DemoServiceApplication`同jar短进程命令，显式demo和schema确认、当前启用服务/类型/绑定、公私钥匹配；默认5分钟服务JWT写新受控文件，0600（支持POSIX时），不输出/覆盖、不启动Web或调度。支持既有身份指南的JWK Set，也可单JWK；多密钥需选kid。`tools/demo_oa_result.py`用真实GET详情和本地token文件调用正式回调，不跟随重定向，不打印token。

## TDD与集成证据

日志位于忽略目录`backend/.tmp/`，不提交密钥或真实数据库配置。

| 日志 | 实际观察 |
| --- | --- |
| workflow-jcs-red.log | 编码器缺失时2项断言失败，随后RFC数字/转义/UTF-16排序、重复键/孤立代理/非有限数字验证通过 |
| workflow-submit-red.log | 3项真实HTTP预览404失败，随后实现提交、版本/预览及审计回滚 |
| workflow-oa-time-red.log | 缺失投递服务断言失败；同轮过期夹具先触发数据库时间CHECK，修正为合法创建/到期时刻，未放宽生产约束 |
| workflow-callback-red.log | 4项真实回调404失败，随后接通可信身份/可靠接收/去重/失效历史/审计回滚 |
| workflow-execution-red.log | 6项执行服务缺失断言失败，随后实现配置、授予、准确撤销、整份失效与失败恢复 |
| workflow-demo-red.log | startup批准未恢复、CLI主类缺失、提交快照缺名称断言失败；演示适配器初轮缺link查询错误，调整断言后workflow-demo-adapter-red.log为明确1项失败 |
| workflow-replay-timeline-red.log | 修正测试字段名和唯一账号夹具后，2项明确失败：时间线缺提交/受理，降为范围管理员后旧跨部门重放202；修正映射及完整授权事实后通过 |
| workflow-script-red.log | 演示回调脚本缺失，子进程非0退出断言失败；实现后用本轮实际可靠申请与服务凭据验证 |
| workflow-jwkset-red.log | 使用既有身份指南JWK Set文件时签发工具拒绝，真实子进程断言失败；兼容后验证短期JWT及错误公钥/重签覆盖拒绝 |
| workflow-input-types-red.log | 非字符串回调字段误报500，断言期望422失败；显式类型检查后修复 |

失败原因未伪称全部来自业务：实施中也修正过测试独立SYSTEM_ACCESS唯一键/时间CHECK、Jackson依赖节点数字类型比较及Java编译问题。最终回归结果以最终完整运行记录为准。

32项本任务MySQL/HTTP行为覆盖：固定提交不授权/安全重放、陈旧编辑/过期/任职变动、无变化、提交审计回滚、UTC固定包、投递故障/人工重试/单租约并发/错误回执、可信回调/可靠关联/事件与最终结果去重/失效在途/接收审计回滚、配置与批量授予/准确撤销、成功一次、审计/第二条故障整批回滚、源周期/权限不可用/原scope失效、原提交人/任命撤销、启动恢复、demo隔离与重启稳定回执、真实CLI/演示回调、完整时间线、降权重放、两连接关闭/执行双顺序、并发成功一次与固定字段不可改、非法类型422。两连接竞争使用真实guard、独立连接与事务提交前门控，分别确认未提交写不可见及最终ACTIVE数，不仅比较返回码。

本机演示步骤与环境变量见[部署侧指南](../../deploy/DEMO-OA.md)、[无秘密模板](../../deploy/demo.env.example)。真实外部OA协议/网络尚未接通；本次替身仅用于外部适配器边界，审批事件/最终结果/业务执行均为真实隔离数据库和HTTP，不是浏览器模拟。

## 最终验证与交付状态（D-091，2026-10-21）

完整`clean verify -Pmysql-check`最终日志为`backend/.tmp/workflow-final-verify.log`：**139项离线/21组＋316项真实MySQL/HTTP/22组**，失败/错误/跳过均0。本任务新增2项JCS离线测试及32项工作流MySQL/HTTP验证，原有回归保持通过；静态追踪/生成确定性/输入API与数据库摘要校验PASS。早期完整两轮139＋315通过后，又依据既有JWK Set文件格式和非法字段类型补修并完成最终整套回归，未把中途编译/夹具错误记为通过。

最终jar：`backend/target/backend-0.0.1-SNAPSHOT.jar`，SHA-256 `c3a8c22b52b304d0cb46ceec0f282d96eac0692d2188c4002af04e51aa6e74c8`。JDK21显式运行，MySQL8.4.8专属临时实例；随机schema/账号0/0，测试密钥目录0，专属子进程已正常关闭并移除自建数据目录/下载包/二进制；没有操作既有数据库或服务。日志中故障注入的安全错误属于断言目标，不等于测试失败。

BE-21、BE-23、BE-24、BE-25、BE-31各自登记IMPLEMENTED、ownerAccepted=false、mergedToMain=false，位于`BE-21-31-申请审批与执行`本地任务分支（基线main/7914788），同分支直接依赖已实际实现，不假称都已交付main。C的BE-19/20/22已获本人确认并交付主线；至此C八项实现具备，本轮五项等待负责人核对本文件证据/本机步骤后确认，再办理共同主线交付。未推送本轮任务分支、创建PR、合并或部署；负责人本机人工演示未进行，其他成员验收保持。真实外部OA协议及BE-18/26/33/34等所属任务不代完成。
