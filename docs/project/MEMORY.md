# 项目共享记忆

D-095（2026-10-21）：D要求BE-26推送后自行继续下一项。BE-26及交付记录已推送并回查远程main/d79e28a，任务分支保留、D人工验收仍开放。已建立codex/be-30-import-commit并完成BE-30详细设计、实际端口衔接及14组TDD验收清单，见docs/05-detailed-design/BE-30-IMPORT-COMMIT.md。直接依赖BE-29已交付，A/BE-27仍PLANNED且主线缺员工全局状态与任命/会话失效编排，故只准备可独立部分，BE-30保持PLANNED、implementationStarted=false、testsRun=false，未改业务代码或运行本任务Java/SQL/HTTP。共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED；不跨成员实现BE-27、不代本人验收或部署。本次继续授权不扩展为BE-30合并推送，准备仅本地提交，下方BE-26未交付为历史。

D-094实际交付（2026-10-21）：D明确“先进行合并推送”。BE-26已接入远程main/6b9a02e的C工作流，来源857d4a9d20fa43578f70385d256b86ce02ad3fdc已no-ff合入远程main/b00ffc98277e754757b0e3d9bd0fe3d920db53b4，合并树等于集成验证来源。普通Git连接超时，改用官方GitHub Git API按本地对象原始SHA、force=false交付，main及codex/be-26-source-closure两个完整SHA回查一致，任务分支保留。集成完整139离线/21组＋350真实MySQL/HTTP/23组及静态校验通过，失败/错误/跳过0；本机python3商店占位导致首轮1项环境失败，临时测试入口修正后完整重跑通过，业务代码未因此修改。随机schema/账号/密钥目录均0，专属MySQL子进程17336正常关闭，原MySQL84仍Running。BE-26为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，D人工验收仍开放，其他成员状态保持；共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED。原D实施D-091与C重复，统一D-093，原f745648/b011a8a及C的D-091/092事实保留。用户另要求交付后自行继续下一项，后续按依赖核对BE-30，不代A实施BE-27、不部署。下方未授权/待交付为历史。

D-093集成结果（2026-10-21）：接入远程main/6b9a02e及C/BE-21/23/24/25/31后，完整clean verify/mysql-check通过，139项离线/21组＋350项真实MySQL/HTTP/23组，失败/错误/跳过0，日志backend/.tmp/be26-delivery-final.log。首轮1项演示脚本因本机python3商店占位退出9009，临时测试目录配置真实Python入口后完整重跑通过；未因此修改业务代码或永久环境变量。随机库/账号/测试密钥目录均0，专属MySQL子进程17336正常关闭，原MySQL84仍Running。BE-26保持IMPLEMENTED、ownerAccepted=false，主线交付由用户明确“先进行合并推送”授权，实际成功后登记；共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED。原D编号D-091与C重复统一为D-093，C的D-091/092及双方历史保持。

D-093集成准备（2026-10-21）：D明确“先进行合并推送”，本次授权BE-26主线交付。远程main/6b9a02e新增C的BE-21/23/24/25/31，已接入且Java无冲突，正在专属隔离MySQL完整回归。D原BE-26准备/实施编号D-091与C重复，统一为D-093；原f745648、b011a8a及双方事实保留，C的D-091/092和验收/交付保持。BE-26保持IMPLEMENTED、ownerAccepted=false、mergedToMain=false，真实交付结果成功后登记。共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED；不代负责人验收或部署。

D-093实际结果（2026-10-21）：BE-26两个公开操作与统一RequestInvalidator已完成设计/TDD，完整137离线/20组＋318真实MySQL/HTTP/22组及静态追踪通过，失败/错误/跳过0；含34项本任务真实验收。覆盖逻辑0、100来源、整份失效与全部claim/outbox、原范围实质失覆盖、独立角色/来源任职、隐藏混合申请及统计回放授权、并发与第N项/审计故障回滚。随机schema/账号/测试密钥目录均0，专属MySQL子进程27740正常关闭，原MySQL84仍Running。BE-26登记IMPLEMENTED、ownerAccepted=false、mergedToMain=false，只本地任务分支提交，既有各成员验收不代确认。共13项OWNER_ACCEPTED、6项IMPLEMENTED、5项IN_PROGRESS、11项PLANNED；BE-27/30未实施，不自动合并推送或部署。交接见docs/05-detailed-design/BE-26-SOURCE-CLOSURE.md，下方IN_PROGRESS/待实现为历史。

D-093（2026-10-21）：D明确开始执行BE-26，已接入main/7914788及C/BE-20、BE-19/22交付。D原准备编号D-086与C实施编号重复，统一为D-093，原f745648准备事实保留；C的D-086/088/089/090及验收/交付保持。首轮4项真实HTTP断言失败、0错误，目标预览路由404。BE-26进入IN_PROGRESS，复用WorkflowStateWriter/claim与既有SourceCloser、预览、guard、幂等和审计。共13项OWNER_ACCEPTED、5项IMPLEMENTED、6项IN_PROGRESS、11项PLANNED；不代D人工验收、不合并推送或开BE-27/30，下方未交付依赖/仅准备为历史。

D-093（2026-10-20）：D要求BE-29推送完成后自行继续下一项。BE-29及交付记录已推送并回查main/86089d2，任务分支保留、D人工验收仍开放。已从该主线建立codex/be-26-source-closure，完成BE-26设计、端口衔接清单和11组TDD验收场景，见docs/05-detailed-design/BE-26-SOURCE-CLOSURE.md。直接依赖BE-05/16/17已交付，但C/BE-20仍PLANNED且主线缺申请/claim写端口，故只推进可独立准备，BE-26保持PLANNED、implementationStarted=false，未改业务代码或运行本任务Java/SQL/HTTP。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED；BE-30仍须A/BE-27，不跨成员实现依赖。本次不扩展BE-29特定合并推送授权，准备提交仅在本地分支；下方不自动开新任务是此前边界。

D-092（2026-10-21）：C明确数据库暂不处理、先提交合并既有工作。来源adab1861906dc7d973f796961249e1ef87563e29已无冲突no-ff合入远程main/311aa5d5cbc3d7bfbd064888b4172d48de0a0e99，合并树等于已验证来源；main与BE-21-31-申请审批与执行原子推送，远程完整SHA回查一致，任务分支保留。BE-21/23/24/25/31为IMPLEMENTED、mergedToMain=true、ownerAccepted=false；合并授权不代记本机人工验收。核对既有139离线＋316真实MySQL/HTTP报告及jar摘要一致，本轮代码未变，仅静态校验，不重新运行Java/SQL，不配置或连接本机数据库、不部署。C八项代码均已交付主线，本机环境与人工演示仍待后续；下方未交付为历史。

D-091（2026-10-21）：C在明确确认后要求继续全部剩余任务，于BE-21-31-申请审批与执行（main/7914788）依次设计/TDD实现BE-21/23/24/25/31。固定七字段RFC8785包、预览/无变化/占用、可靠OA待办/租约/三层回调防重、整份原子执行/失败记录/启动与周期恢复、显式demo适配器和短期服务JWT工具已实现；完整139离线＋316真实MySQL/HTTP及静态校验通过，失败/错误/跳过0。专属测试资源已清理关闭，用户既有库未操作。五项IMPLEMENTED、ownerAccepted=false、mergedToMain=false；既有BE-19/20/22负责人确认与主线交付保持，C八项实现均具备，剩本轮五项本人验收/主线交付。真实外部OA及服务器/全系统验收不在本轮，其他成员状态保持；共13项OWNER_ACCEPTED、10项IMPLEMENTED、5项IN_PROGRESS、7项PLANNED。证据见BE-21-31-APPROVAL-WORKFLOW.md，下方旧状态为历史。

D-090（2026-10-21）：C明确“确认，然后继续完成剩下的任务”，接受BE-19/22既有137离线＋284真实MySQL/HTTP证据；人工演示未进行。来源cc88885f2c97df838720fc2f7f0e524d72e56351已no-ff合入main/cf27f7f910bf5e904c89542f56e3f56fddd884d9，合并树等于验证来源，main及任务分支原子推送且远程完整SHA回查一致。两项OWNER_ACCEPTED、mergedToMain=true。后续按BE-21→23→24→25→31连续实施，依赖沿同一任务分支继承，各项实现、本人确认、主线交付分开记录；不代其他成员验收、不部署。共13项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、12项PLANNED。下方待确认/待交付为历史。

D-089（2026-10-21）：C要求继续其他任务，本轮在BE-19-22-审计与申请查询（main/09abaa2）先设计/TDD连续实现BE-19审计列表/详情及BE-22申请列表/详情/时间线，复用整份当前单项范围投影。两项为IMPLEMENTED待C负责人确认与共同主线交付；不会把BE-20的特定合并推送授权扩展到本轮。原始事件append-only，关联主体和缺损目标不能绕过授权，历史名称不使用当前值倒推；验证及本机步骤见docs/05-detailed-design/BE-19-22-AUDIT-REQUEST-QUERIES.md。BE-21/23/24/25/31仍PLANNED，其他成员状态保持。

D-088（2026-10-21）：C在BE-20负责人确认与逐项合并建议后明确“可以”，接受既有137离线＋265真实MySQL/HTTP验证并授权交付。来源310313a07e0321f58a5b1b03d3d59231fe2a17a6已保留历史合入远程main/a9b214cce38d4396120b3902ce19f2b7440bf4f1，合并树等于验证来源；main和中文任务分支已原子推送，远程完整SHA回查一致。BE-20为OWNER_ACCEPTED、mergedToMain=true，人工演示未进行；本轮代码未变，仅复核报告/jar摘要与静态追踪，无新Java/SQL或部署。D/BE-26技术依赖已交付，其他C任务不自动开展，既有其他负责人验收保持。详细证据见docs/05-detailed-design/BE-20-CHANGE-DRAFTS.md。

D-086（2026-10-20）：本轮用户明确负责C，要求先列计划再执行BE-20。已在codex/be-20-change-drafts（main/86089d2）完成设计、真实RED/GREEN、四写接口及最小WorkflowStateWriter交接；完整137离线＋265真实MySQL/HTTP和设计静态校验通过，失败/错误/跳过0。随机库/账号/临时密钥目录0，专属MySQL正常关闭并清理。BE-20为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，待C负责人确认及共同主线交付；A/D历史认领和验收保持，BE-19及BE-21～25/31不自动开工，不合并推送或部署。实现与验收步骤见docs/05-detailed-design/BE-20-CHANGE-DRAFTS.md。

D-085实际交付（2026-10-20）：成员D明确“合并推送吧”，BE-29接入A已交付BE-09后，来源db3419687f8dc43b1959564ddc19b0e7d32826ed已无冲突no-ff合入远程main/6506cda54c4898e736a2e730ba001294f8de4c2c，合并树等于已完整验证来源，任务分支codex/be-29-import-precheck保留。普通Git连接连续失败，改用官方GitHub Git API按本地对象原始SHA、force=false推送main和任务分支，远程两个完整SHA均回查一致。完整137离线＋240真实MySQL/HTTP及静态校验通过，专属资源清理关闭，原MySQL84仍Running；BE-29为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，既有人工验收保持。D原实施编号D-081与A冲突已统一为D-084；A的D-081/082/083及PR#3交付保留。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED；不强推或部署，下方未授权/待主线为历史。

D-084集成结果（2026-10-20）：接入远程main/06a6b5c（A已验收并经PR#3交付的BE-09）后，完整clean verify/mysql-check通过，137项离线/20组＋240项真实MySQL/HTTP/19组，失败/错误/跳过0，日志backend/.tmp/be29-main-integration-verify.log。集成未改BE-29业务代码，静态追踪通过；BE-29保持IMPLEMENTED待D人工验收，本次主线交付由用户“合并推送吧”明确授权，实际SHA成功后补。

D-084编号协调（2026-10-20）：BE-29原本地实施记录D-081与远程A/BE-09重复，现统一为D-084；原实现80abde1及真实RED/GREEN事实保留，A的D-081/082/083、验收与PR#3交付均保持。已接入main/06a6b5c，业务代码无冲突；BE-29仍IMPLEMENTED、ownerAccepted=false，待集成回归与本次授权的主线交付。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED。

D-084实际结果：BE-29已按设计/TDD完成五个预检/报告操作，完整137离线＋228真实MySQL/HTTP通过（均0失败/错误/跳过），含25项规划器和13项真实预检验证。来源/映射/候选/前后差异、动作顺序与依赖冻结、同key/同源批次防重、权限与事务回滚均有证据；不写正式人员/授权。随机库/账号0/0、测试密钥文件目录0，专属MySQL5140正常关闭、原MySQL84仍Running。BE-29 IMPLEMENTED/ownerAccepted=false/mergedToMain=false；9项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、16项PLANNED。完整静态检查及交接见[BE-29](../05-detailed-design/BE-29-IMPORT-PRECHECK.md)；只本地提交，原验收/交付事实保持，下方旧数量为历史。

D-084（2026-10-20）：成员D明确“接下来进行下一项任务”，已fetch核对main/072585e，在codex/be-29-import-precheck实施BE-29；BE-10/17/28技术依赖已交付。预检仅冻结输入、完整候选ID/code、规范前后值、动作次序/版本、映射写计划、集合依赖和影响，以及问题与报告分页，不写正式数据。设计/真实RED/GREEN及交接见[BE-29](../05-detailed-design/BE-29-IMPORT-PRECHECK.md)。BE-26仍等C/BE-20，BE-30仍待A/BE-27及BE-29；不代其他成员实施、不把READY当作导入成功，也不扩展此前BE-16合并推送授权。既有人工验收事实保持。

D-083实际交付（2026-10-20）：BE-09已通过[PR #3](https://github.com/Noah-hope/RBAC-admin/pull/3)合入远程main，来源0fee462503274fbed1ab03826c8d0723c9825c19，合并5f42a2cff11d68c49a4926373eeee97e3f5e8889。已回查closed/merged=true、远程完整SHA、双亲及合并树等于验证来源，本地main已同步、任务分支保留；OWNER_ACCEPTED、mergedToMain=true、remotePrNumber=3。接入BE-16后完整112普通＋227真实MySQL/HTTP及静态校验通过，专属资源清理关闭，原MySQL84仍Running；远程检查条目0，不记为CI通过。本轮交付不部署或开新任务，D各任务验收不代确认；下方待授权/待PR/待主线为历史。

D-083集成结果：接入main/072585e后完整112普通＋227真实MySQL/HTTP及静态校验通过，失败/错误/跳过0；随机库/账号和BE-09密钥目录0，专属实例关闭，原MySQL84仍Running。当前准备推送及PR交付，真实结果成功后补，BE-09代码仍与A确认来源41931f4一致。

D-083（2026-10-20）：A已明确授权BE-09的PR与main合并交付。已接入远程main/072585e及BE-16，Java自动合并，共享记忆/任务生成器/静态规则保留A与D双方事实；BE-09仍OWNER_ACCEPTED，BE-16仍IMPLEMENTED待D确认，交付不代验收。正在执行完整隔离MySQL回归，真实PR/来源/合并SHA与远程回查成功后补录。分支和无关.codeartsdoer保留，不部署或自动开始BE-27/34；下方未授权/未整合为历史。

D-082（2026-10-20）：A先暂缓本机演示，再明确“测试通过即可”，确认BE-09/41931f4可按已有112离线＋205真实MySQL/HTTP证据验收，登记OWNER_ACCEPTED，未记录人工演示通过；主线交付待办理，本轮未推送/创建PR/合并或运行Java/SQL。fetch核对远程main/072585e已新增D/BE-16（来源dba2eaa，合并7ab021a），D人工验收仍开放；BE-26和BE-33仍PLANNED。本任务分支尚未整合该增量，交付前应接入并回归。A余下BE-27依赖D/BE-26、BE-34依赖B/BE-33，可先交付BE-09；不自动实施后续任务。为避免与远程D/BE-16的D-079/080冲突，本地BE-09实施编号统一改D-081，原事实和41931f4实现提交保留。下方待A确认及旧数量为历史，见[BE-09](../05-detailed-design/BE-09-SCOPE-CREATION.md)。

D-081（2026-10-20）：本会话A明确“可以开始做BE-09吗”，按行动指令同步main/a860dc8并建立codex/be-09-scope-creation。BE-05/08/17技术依赖已交付；实现创建预览和范围/系统集合/任命原子保存，正常写入使用Spring Data JPA，复用BE-17预览、D组织任职事实及原guard/幂等/审计。实现、真实RED/GREEN、最终回归与本机验收见[BE-09](../05-detailed-design/BE-09-SCOPE-CREATION.md)。完整112离线＋205真实MySQL/HTTP检查及静态校验通过，失败/错误/跳过均0；随机库/账号0/0，BE-09密钥目录0，专属实例已关闭，原MySQL84仍Running。BE-09登记IN_PROGRESS、implemented=true、ownerAccepted=false、mergedToMain=false，尚未远程推送/创建PR/合并；B/D原验收状态保持。当前35项中9项OWNER_ACCEPTED、3项IMPLEMENTED、6项IN_PROGRESS、17项PLANNED；A仍待本人确认BE-09，其后BE-27/34按原依赖，不自动开新任务。

D-080实际交付（2026-10-20）：D明确“先完成合并推送”，核对远程main仍为BE-16基线a860dc8、既有112离线＋215真实MySQL/HTTP及jar摘要一致，静态复核通过后将来源dba2eaab8990fd17780d7f655fdfa049a6f7b2e1无冲突no-ff合入main/7ab021a80e26cbf4e3b4bc245f574fcd5c31c2ff；合并树等于验证来源，普通推送main和任务分支成功，ls-remote回查完整SHA一致。分支保留，本轮不重跑Java/SQL、无PR/部署。BE-16为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，BE-14/28人工验收继续开放；下方待主线/未授权为历史。35项中9项OWNER_ACCEPTED、4项IMPLEMENTED、5项IN_PROGRESS、17项PLANNED；BE-26仍须C/BE-20，不自动开新任务。见[BE-16交接](../05-detailed-design/BE-16-GRANT-MUTATIONS.md)。

D-079实际结果：BE-16完整112离线＋215真实MySQL/HTTP及静态检查通过（均0失败/错误/跳过），其中22项本任务真实写端口验证。新/旧来源与永久关系、授予/撤销/关闭、独立部门资格、版本上限、双连接guard竞争和证据/审计失败回滚均有断言；专属随机库/账号0/0、测试密钥目录0，MySQL子进程39780正常关闭、原MySQL84仍Running。登记BE-16 IMPLEMENTED/ownerAccepted=false/mergedToMain=false，9项OWNER_ACCEPTED、4项IMPLEMENTED、5项IN_PROGRESS、17项PLANNED；未合并推送，BE-14/28人工验收继续开放，下方旧数量为历史。

D-079（2026-10-20）：成员D明确开始下一项，依既定顺序在codex/be-16-grant-mutations实施BE-16，基线共同main/a860dc8，所需BE-04/14已交付。内部物化0锚点、全固定目标授予/准确撤销、来源周期及关系资格失效端口均复用同一WriteContext；来源与角色部门任职独立检查，返回AffectedFacts给后续编排。设计、真实RED/GREEN、完整验证及清理事实见[BE-16](../05-detailed-design/BE-16-GRANT-MUTATIONS.md)。不将内部能力记为C执行器、真实OA、BE-26/27/30完成；实现、本人验收、主线交付分别登记，BE-14/28人工验收继续开放，本次未授权合并推送。


D-078实际交付（2026-10-20）：D明确“先完成合并与推送”，已核对远程main仍为BE-14开发基线61132f8、来源367cdae的112离线＋193真实MySQL/HTTP报告及jar摘要一致。静态复核通过后以no-ff无冲突合入main/c7435d9b17a2f9be57d1e748cae24f125a45deb2，合并树等于来源367cdae2acc6ff247116651abad53adafb6a34b9；git push普通推送main及codex/be-14-grant-baselines成功，ls-remote回查两个完整SHA一致。分支保留，无PR/强推/部署，本轮不重跑Java或SQL。BE-14已主线交付，仍待D人工确认，BE-28人工验收延后状态不变；下方待主线/未授权为历史，见[BE-14交接](../05-detailed-design/BE-14-GRANT-BASELINES.md)。B/BE-18、C/BE-20及D/BE-16可按原技术依赖核对接入，不自动开始新任务。

D-077（2026-10-20）：成员D明确“我后面再验收吧，先开始下一项工作任务”，BE-28继续保留IMPLEMENTED/mergedToMain=true/ownerAccepted=false。按顺序选择依赖代码已交付的BE-14，在codex/be-14-grant-baselines（共同main/61132f8，官方API回查一致）实现三个查询及事务内GrantReader，供B/BE-18、C/BE-20与D/BE-16接入；无新迁移、依赖或来源/授权DML。完整112离线＋193真实MySQL/HTTP及静态检查通过，失败/错误/跳过0；随机库/账号0/0、临时密钥目录0，专属PID7144正常关闭，原MySQL84仍Running。实际证据见[BE-14](../05-detailed-design/BE-14-GRANT-BASELINES.md)。BE-14登记IMPLEMENTED，待D人工确认和主线交付，未合并推送；AT-13补本任务读取证据，AT-14/15仅局部读取验证。35项中9项OWNER_ACCEPTED、3项IMPLEMENTED、5项IN_PROGRESS、18项PLANNED；其他成员及BE-28原交付/验收事实保持，不自动实施BE-16/29。

D-076实际交付（2026-10-20）：BE-28来源233f63352b0c8dea92fd916c47bd29d59267f97c已合入远程main/4b384fb7b629affd8fbd0e5762cad652bbdb3158，main及任务分支回查均为预期完整SHA，合并树等于验证来源，分支保留。接入最新B/BE-17/12后完整110离线＋178真实MySQL/HTTP通过，失败/错误/跳过0；专属实例已正常关闭，随机库/账号及临时密钥/文件目录0，用户原MySQL84仍Running。Git传输失败后使用官方API按相同对象SHA及force=false更新，无PR/部署。静态追踪记录BE-28的mergedToMain=true、ownerAccepted=false；手工验收仍开放，步骤见[BE-28](../05-detailed-design/BE-28-IMPORT-FILES.md)。下方待交付和未授权是历史；不自动启动BE-29/30。

D-076（2026-10-20）：D已要求先合并推送BE-28，再按交接步骤人工验收；先fetch核对远程main/1f7886e并接入最新B/BE-17/12。无Java冲突，共享设计/记忆冲突合并保留两方事实，D原BE-28实施编号调整为D-075，B原D-073/074不变。集成后完整回归及实际主线交付成功后记入[BE-28](../05-detailed-design/BE-28-IMPORT-FILES.md)，不以授权或机器验证代记D人工验收。下方旧未授权/旧数量为历史。

D-075（2026-10-20）：本会话成员D明确开始BE-28，已fetch并将本地main快进至93acc63，创建codex/be-28-import-files。实现uploadImportFile/getImportFile、安全CSV/XLSX解析、SHA256不可变存储和后续明确选表读取端口；沿用V1～V3及现有身份/事务/审计/幂等，无新增依赖或组织人员变更。完整102离线＋159真实MySQL/HTTP及静态校验通过，失败/错误/跳过均0。随机库/账号和临时密钥/存储目录均0，专属PID3652正常关闭，原MySQL84仍Running。真实RED/GREEN与交接见[BE-28设计和交接](../05-detailed-design/BE-28-IMPORT-FILES.md)。BE-28登记IMPLEMENTED，待D人工确认和主线交付，未合并推送；AT-26只标本任务部分验证。BE-29预检、BE-30整批提交及真实HR集成未实现，本轮不自动开始。当前35项中9项OWNER_ACCEPTED、2项IMPLEMENTED、5项IN_PROGRESS、19项PLANNED；保留A/B已有主线交付与人工确认事实，下方旧数量和任务边界均属历史。

D-074实际交付（2026-10-20）：按 B 用户“你检查完之后合并就好”，BE-17/12 来源 1fb95ad3d89789b8e45de7bedbb6397bc04f37e7 已无冲突合入并推送远程 main/3103b351089f300fe2553b79bb6f63ae5a383cf1；任务分支已推送保留，远程 SHA 回查一致。远程原基线仍 93acc63，无他人新增改动，合并树与完整 93＋169 测试通过源一致，本轮合并后 93 普通回归再次通过。静态追踪记录已交付、本人验收仍开放，不将合并授权代记 OWNER_ACCEPTED；原 D-073 未交付为历史。Git 连接超时后经官方 API 按本地对象 SHA 且 force=false 交付，不创建 PR/部署。组员可更新 main 接入 BE-17；不自动开始 BE-09/18 等剩余任务。见 [验证与接入](../05-detailed-design/BE-17-12-PREVIEW-CATALOG.md)。

D-073（2026-10-20）：B 已按用户“17和12你做一下”在 codex/be-17-12-preview-catalog 实现 BE-17 固定安全快照/影响计算/确认端口和 BE-12 六个系统、目录操作，基线 main/93acc63，原契约/迁移不变。完整 93 普通＋169 真实 MySQL/HTTP 回归通过；实现与测试见 [实施记录](../05-detailed-design/BE-17-12-PREVIEW-CATALOG.md)。两项 IN_PROGRESS 表示代码已实现、待本人确认及主线交付，不虚构 OWNER_ACCEPTED 或已推送；原三项远程交付保留。BE-18 仍等 BE-14，BE-33/35 等其他模块及部署验收；整个 B 尚未全量完成。下方 BE-17/12 未实现是历史。

D-072实际交付（2026-10-20）：B 的 BE-11/13/15 来源 b92cdbb，已保留历史合入并交付远程 main/0e391c6；B 分支同样更新并保留，远程完整 SHA 回查一致。合并树等于通过验证的源分支，85 普通＋150 真实 MySQL/HTTP 检查及完整静态校验 PASS，隔离随机库/账号均 0、专属实例关闭。Git 连接超时后经官方 Git Database API 按本地对象 SHA 原样交付，force=false，无强推/PR/部署。任务已主线交付仍待负责人确认，不代验收。实际完整 SHA、原设计修正、构建产物摘要及限制见 [交付记录](../05-detailed-design/B-MAIN-INTEGRATION.md)。下方待推送/静态受阻为历史；BE-17 技术依赖已就绪但未实现，BE-12 等 BE-17、BE-18 等 BE-14，整个 B 和 OA 链路未完成。

D-072（2026-10-20）：当前 B 用户明确授权拉取、按原要求修正后合入远程 main。最新远程仍为 5b695a7；B 已接入该主线，并按 TDD 补查重复管理上下文头，真实 RED 为期望 422、实际 200，随后修正为与人员接口一致的拒绝行为。完整回归和实际合并/推送结果成功后记录于 [集成交付记录](../05-detailed-design/B-MAIN-INTEGRATION.md)。本轮保留任务分支，不强推、不部署；B 人工验收不因交付授权自动记为完成。

D-071（2026-10-20）：按当前 B 用户要求将最新 main/5b695a7 接入 B 任务分支并核对原设计，不改远程 main。复用正式 BE-08 resolver 修正他人任命在人员/B 接口间 403/409 不一致；先真实 RED，再完整 85 普通＋149 真实 MySQL 检查通过。原 B/abc5cf2 已推送分支；此前“未推送”“BE-08/10 未交付”为历史。BE-17 技术依赖已就绪但本轮仅集成检查，BE-12/18 等原剩余依赖。B 的原 D-061 编号因与 A 冲突调整为 D-070，双方原验收事实不变。详见 [集成记录](../05-detailed-design/B-MAIN-INTEGRATION.md)。

D-070（2026-10-19）：当前用户授权直接按现有计划实施 B，不再逐项询问。在共同主线 9857e5e 上创建 `codex/be-11-13-15-b-module`，实现 BE-11 系统登记/列表/详情、BE-13 角色列表/详情/元数据修改和 BE-15 内部配置应用器；未推送或合并 main。使用项目 TDD 技能，真实 RED/GREEN 与完整构建、隔离 MySQL 结果见 [B 实施记录](../05-detailed-design/BE-11-13-15-B-MODULE.md)。沿用原契约和迁移，未操作已有数据库。三项 IN_PROGRESS 表示已实现待负责人确认，不虚构 OWNER_ACCEPTED 或 OA 全链路完成；原 A 的 D-056～060 为既往交付事实。BE-17 仍缺 BE-08/10，BE-12 等 BE-17，BE-18 缺 BE-10/14，BE-33/35 仍等待其他板块与部署验收。
D-069实际交付（2026-10-19）：按A明确指令推送BE-08任务分支并创建[PR #2](https://github.com/Noah-hope/RBAC-admin/pull/2)，正文包含变更、85离线＋136真实MySQL证据和组员接入。已核对head为2199f0ef7b36a048845842c9803b08acf0f6612a、base为db1be16、mergeable=true/clean；检查/status条目0，不称CI通过。普通merge生成ca8518918bd668f48769134763a10a163e10612b，回查PR为closed/merged=true，远程main同SHA，父提交及合并树与来源一致。本地main已快进同步，BE-08维持D-068的OWNER_ACCEPTED并登记mergedToMain=true、PR#2；任务分支保留，未动无关.codeartsdoer。后端自1c0eb3c未改，本轮只核对旧证据及静态追踪，不重跑Java/SQL、不部署。B可pull后推进BE-17；A的BE-09仍须BE-17，其他未实现任务不自动开始。

D-069交付授权（2026-10-19）：A明确要求提交PR并合并主线，已授权推送codex/be-08-management-scope、创建main目标PR、核对后合并并同步记录。本轮fetch核对远程main仍db1be16；后端自已验证1c0eb3c未改，复核85离线＋136真实MySQL成功日志，沿用原证据不重跑Java/SQL。实际PR链接与合并SHA待成功后补，不能将授权记为交付成功；保留任务分支及无关.codeartsdoer，不部署或开新业务。

D-068（2026-10-19）：A明确“验证通过，接下来做什么”，接受BE-08实现1c0eb3c055fe267fd0ff69c37e5120de3951eca1，登记OWNER_ACCEPTED、mergedToMain=false。本人回报按原话保留，不虚构新的HTTP截图或逐项人工测试结果；既有85离线＋136真实MySQL证据沿用，源码未变。本轮同步确认/生成器/静态追踪，准备[PR正文](../05-detailed-design/BE-08-PR.md)，不重跑Java/SQL。fetch核对远程main仍db1be16，BE-17仍PLANNED；下一步先交付BE-08共同主线，供B实施BE-17及C等任务复用；A的BE-09仍等B/BE-17。未授权远程写入，不推送、创建PR、合并或自动实施下一任务。A的7项已确认4项，剩BE-09/27/34三项PLANNED；不代D确认BE-10。

D-067（2026-10-19）：A明确开始开发BE-08，在codex/be-08-management-scope实施四个任命/范围GET、Spring Data JPA只读实体/仓储、统一ManagementContextResolver和纯ScopeCoveragePolicy。先真实RED再实现；people查询复用resolver，保持D的人员可见性和事务，不新增写接口或改迁移。首轮完整回归检出两组旧夹具实体扫描覆盖问题，补正式实体扫描且保留全部断言后，最终clean verify/mysql-check为85离线＋136真实MySQL，全无失败/错误/跳过，包含双连接RR快照及JPA/JDBC同事务复核。专属实例schema/账号0/0、进程正常退出、临时密钥目录0，原MySQL84仍Running。实际证据、本机Apifox步骤与组员接入见[BE-08](../05-detailed-design/BE-08-MANAGEMENT-SCOPE.md)。任务IN_PROGRESS待A确认，未合并推送；用户已有库未操作，不代D确认BE-10。AT-08/19仅本任务局部验证，BE-09仍等B/BE-17。

D-066（2026-10-19）：A 要求拉取远程、同步本地并准备开发 BE-08。fetch 后将本地 main 从5966834无冲突快进至远程db1be16，建立codex/be-08-management-scope；保留无关.codeartsdoer。已核对BE-10交付及OrganizationReader同事务接入要求，准备[BE-08设计与测试顺序](../05-detailed-design/BE-08-MANAGEMENT-SCOPE.md)。本轮仅开发准备，BE-08仍PLANNED，未修改业务代码或运行Java/SQL，未推送或创建PR；BE-10保持IMPLEMENTED/已交付main、D人工验收开放，不代为确认。

D-065实际交付：已按授权将codex/be-10-people来源6bfae88c89d0bad4b1599787ea47e2107a3bbcf9以no-ff无冲突合入main/8f1db6927b6d82e1a352ad016a4c1c056a6c648a，合并双亲为5966834和6bfae88，合并树与验证来源一致；git push origin main成功，ls-remote回查远程main为同SHA。BE-10的6个正式查询操作登记IMPLEMENTED_MAIN_DELIVERED_PENDING_OWNER，整项IMPLEMENTED、mergedToMain=true、ownerAccepted=false，保留成员D后续人工验收，不把合并授权等同验收通过。任务分支保留，当前81＋122验证证据见[BE-10](../05-detailed-design/BE-10-PEOPLE.md)；交付追踪由生成器统一更新。A可核对main提供的端口安排BE-08；其他26项PLANNED，不自动开始新任务。当前收尾只更新文档/静态交付证据，不重复执行Java或数据库。

D-065验证完成：BE-10最终代码完整clean verify/mysql-check为81离线＋122真实MySQL，失败/错误/跳过均0；六个查询HTTP、受控导入基础写与既有读取均已实现验证。版本上限无变化与迁移审计新公司归属补充RED后修正。专属23310实例随机schema/账号0/0，PID8392正常退出，临时RSA目录0，本机原MySQL84仍Running；未操作已有项目库。静态追踪同步通过。按本轮明确授权准备合入main并推送，实际交付SHA待成功后补；负责人人工验收未虚构，BE-10为IMPLEMENTED。

D-065（2026-10-19）全量实施授权：成员D要求完成全部BE-10并合并推送，替代D-064仅内部读取、不推送的边界。在codex/be-10-people补六个HTTP查询、当前身份与单任命/双版本的窄范围读取、过滤后分页及安全投影、受控EmploymentWriter基础变更/映射/任职/无历史部门迁移及同事务审计。状态登记IMPLEMENTED、本人确认未伪造；BE-08通用范围、BE-26/27失效、BE-28～30文件及整批导入仍为原任务，不因基础端口实现标记完成。完整测试和远程交付结果在实际核对后补充；详见[BE-10](../05-detailed-design/BE-10-PEOPLE.md)。

D-064验证结果：先记录离线事务保护RED和8个真实引擎场景RED，再实现；完整clean verify/mysql-check为81离线＋96真实MySQL，全无失败/错误/跳过；随后无生产改动补UTC精度/存储故障两项并重跑全部10项端口检查通过，XML覆盖合计81＋98。专属23310实例随机schema/账号剩余0/0，PID37380正常退出，用户MySQL84仍Running。静态校验PASS。A接入方法与BE-10剩余边界见[交接](../05-detailed-design/BE-10-PEOPLE.md)；本轮不以测试通过代替D的确认，不合并推送。

D-064（2026-10-19）：本会话用户认领D，要求先完成A需要的数据能力。在codex/be-10-people（基线5966834）实施BE-10内部OrganizationReader：公司/部门/岗位/员工、源身份与组织路径精确映射、准确公司/部门全部有效任职和版本；强制调用方同数据源事务，不缓存、不授权、不新开事务。A可在BE-08/09的ReadBoundary/CommandBoundary内复用，仍自行复核身份/单项范围。设计与实际验证见[BE-10交接](../05-detailed-design/BE-10-PEOPLE.md)。BE-10整体IN_PROGRESS，内部读取实现不等于六个HTTP接口/EmploymentWriter/整项AT完成；本人确认及main交付待办理，其余26项PLANNED。成员D本机已通过JDK21/Wrapper构建、MySQL8.4.8双库迁移及权限、HMAC启用启动和健康UP，证据为本会话用户输出/回报；不记录密码/密钥，不推断RSA/管理员初始化完成。历史A会话认领保持，本会话未获得远程写入授权。

D-063实际交付（2026-10-19）：按用户明确授权，已推送codex/be-32-bootstrap并创建[PR #1](https://github.com/Noah-hope/RBAC-admin/pull/1)，正文包含变更、80离线＋88真实MySQL、本机人工验证及组员接入。远程main基线9857e5e，PR来源d5941c902712c92d4d8d8d7505bd6df4f5ceb139；GitHub确认mergeable=true/clean，check runs与status条目均0，不把无CI记录称为CI通过。普通merge成功生成609ee4bb017e46b42e43bcd8c2616d31ab410f5a；PR回查state=closed/merged=true，远程main回查同SHA，两个父提交及合并树与PR来源一致。本地主线已快进同步，BE-32登记OWNER_ACCEPTED/已交付main及remotePrNumber=1；保留任务分支和无关.codeartsdoer。后端自cc76ac9未改，复核既有报告/摘要，本轮不重跑Java或SQL，不部署服务器。A剩BE-08/09/27/34四项PLANNED，BE-08仍等待D/BE-10；下方待主线描述均为此前历史。

D-063 交付授权（2026-10-19）：用户明确要求代提PR并合并，已核对origin/main仍为9857e5e、任务分支含4个本地提交，后端自已验证cc76ac9未改；已有80离线＋88真实MySQL及同包摘要证据复核。本轮获授权推送codex/be-32-bootstrap、创建main目标PR并合并、同步交付追踪和本地主线，不删除任务分支、不提交无关.codeartsdoer。仓库权限和无重复开放PR已只读核对；创建与合并进行中，链接/SHA待实际成功后补，未运行Java/SQL或部署服务器。

D-062（2026-10-19）：用户 A 明确“现在确认BE-32验证通过”，按D-051登记OWNER_ACCEPTED，接受实现cc76ac9c4bf4d073f7306b2f5e95c1d5cf52a614、人工证据27b99700d7d6f747f9e1f1377ca7aed20c5e92c9；沿用80离线＋88真实MySQL及重复构建证据，源码自cc76ac9未变。本轮只改状态/文档并静态核对，不重跑Java或数据库，不记录凭据。A还剩BE-08/09/27/34四项PLANNED，BE-32待主线交付；整体AT和服务器验收边界不变。用户询问直接合并推送或先提PR，建议先推任务分支、提PR说明变更和证据，再合入main；[PR说明草案](../05-detailed-design/BE-32-PR.md)已准备，尚未创建远程PR/合并/推送，不把建议当作授权。下方待本人确认均为此前历史。

D-061 本机链路验证进展（2026-10-19）：用户确认 BackendApplication 原连dev、BootstrapTestAdmin连test，正常应用改连初始化目标测试库后，截图依次显示首次登录201/PASSWORD_CHANGE_ONLY、新密码登录201/NORMAL；最后明确“成功获取了”，账号列表正文为employeeId=NULL、enabled=true、mustChangePassword=false、version=2、page=1/pageSize=20/total=1。本机首次登录/改密生效/正常会话/列表读取链路已获截图与用户回报支持；最终截图没有HTTP状态栏，不虚构200原始截图，改密204/旧token拒绝/重复初始化/审计SQL没有新人工截图，沿用既有自动化证据。已在[BE-32](../05-detailed-design/BE-32-DEPLOYMENT.md)补记录，在[操作指南](../../deploy/README.md)补Dev/Test独立运行配置、列表可选参数与手动凭证设置。本轮只改文档并做静态校验，不执行登录、SQL、Java测试或远程写入，不记录密码/token/账号UUID；BE-32仍待A整体确认，未合并推送。下方“本机登录未确认”等为此前排查历史。

D-061 本机排查补充（2026-10-19）：用户确认首次初始化配置的密码长度不足，后续 Apifox 先出现空/非法 RequestId 的422、再出现400，最新为401 AUTHENTICATION_FAILED。已读源码/Git契约并 refresh/read/ref 复核 Apifox MCP：默认模块登录仍声明AdminJwt、截图实际发出未解析 Bearer变量，Git登录为公开security:[]；这是既有默认模块/指定导出边界，不改写过去指定1.2.0导出已核对的事实。后端登录路径跳过Bearer，因此不把客户端继承认证直接当作401原因；仍须用户核对正常应用的目标库、初始账号及本机密码。补充 [登录排查说明](../../deploy/README.md)与Apifox复核记录；未读取本机秘密、连接已有库或执行登录，未改Java/API/DDL。本机成功登录/首次改密仍未确认，BE-32保持待A确认，未合并推送。

D-061（2026-10-19）：用户 A 要求开始 BE-32，前置已交付 main；在 codex/be-32-bootstrap 先设计/TDD 实现独立 BootstrapAdminApplication、server 配置保护和 deploy 模板/操作指南。普通启动不初始化；DML 账号、目标库显式确认、拒绝覆盖已有总管理员、首次改密、guard/账号/审计原子提交。两组实际 RED 留证，新增 5 离线＋8 引擎，完整 clean verify/mysql-check 为 80＋88，全无失败/错误/跳过；第二次独立 clean verify 的 jar SHA256 一致。专属随机端口 MySQL8.4.8 的自定义 schema/账号清理为0/0、专属进程关闭、临时密钥目录归零，用户已有库未操作。服务器和本机使用相同代码/jar，差别只在外置配置/启动方式，无需用户当前选择 OS；未连接或部署云服务器，systemd/nginx/HTTPS/备份恢复由 BE-34 核验。BE-32 仍 IN_PROGRESS 待 A 确认，未合并/推送；BE-06/07 既有确认及 main 交付保持，其余27项 PLANNED，BE-08 仍等待 D/BE-10。同步任务生成器/校验器及接入说明，AT-36 仅局部验证；[实现证据](../05-detailed-design/BE-32-DEPLOYMENT.md)、[IDEA 初始化与部署指南](../../deploy/README.md)。保留无关 .codeartsdoer，本轮不创建远程 Issue/PR。

D-060（2026-10-19）：用户明确“合并main并推送”。fetch核对本地/远程main均e58b6ba，以9b6d89e为源无冲突no-ff合入main，合并提交ff19d1792a6934dbd6bca6c174dcba29f7acdad8；合并树与源一致。push成功，远程refs/heads/main回查为同一SHA。BE-06/07均OWNER_ACCEPTED且已交付共同main，9个正式操作主责记录同步，其他28项PLANNED。复核现有75离线报告及完整日志的80引擎通过证据，后端自584eb4e未改，本轮不重跑Java/SQL、不读密钥或动已有库。保留任务分支及无关.codeartsdoer；下方D-059及更早待主线为历史。组员可pull共同main，B/BE-11、D/BE-10或28及A/BE-32可按各自依赖安排；A/BE-08仍等D/BE-10，不自动开新任务。

D-059（2026-10-19）：用户A明确“BE-07验证通过”，随后确认健康UP和未登录账号入口401两项通过。结合584eb4e的75离线＋80真实MySQL证据，BE-07登记OWNER_ACCEPTED；BE-06/07均待主线交付，其他28项PLANNED。不推断本人已手工登录/创建/重置或重跑全部测试，本轮仅文档/静态同步，不运行Java或数据库、不合并推送。下一步建议先交付main供组员复用；A的BE-08仍依赖D/BE-10（当前PLANNED），BE-32部署产物/配置与管理员初始化技术前置已具备，可作为后续实施建议，尚未开始。下方D-058待确认为历史。

D-058（2026-10-19）：用户A在BE-06确认后要求实施BE-07并回复“继续”。在codex/be-07-accounts继承已确认BE-06及BE-05，允许显式授权的本地连续开发，BE-06主线交付仍待办理、不自动合并推送。BE-07五个账号接口已实现，当前总管理员读/写资格复核、创建/启停/重置与审计/安全结果同事务，完整clean verify/mysql-check为75离线＋80真实MySQL检查，失败/错误/跳过均0；自建schema/随机用户均0，专属进程关闭、临时密钥目录0，未操作已有库；静态追踪与接入说明同步，结果见[BE-07](../05-detailed-design/BE-07-ACCOUNTS.md)。BE-07仍IN_PROGRESS待A确认，其余28项PLANNED；初始管理员BE-32、范围BE-08等边界不变，下方D-057未启动BE-07为此前历史。

D-057（2026-10-18）：用户A在收到BE-06本机密钥配置、应用启动/健康、未登录401和VerifyBackend验证步骤后明确回复“验证通过”。按D-051登记BE-06为OWNER_ACCEPTED，接受版本1907a63（含488367a实现及指南兼容修正），主线交付仍待办理；本机验证为用户回报、未提供新控制台/截图，不推断人工成功登录。既有74离线＋59真实MySQL证据沿用，本轮仅更新状态/文档并做静态核对，未重新运行Java/数据库或读取秘密。其他29项PLANNED，AT-05/06/07保持局部验证；下一项BE-07后台账号管理与密码重置。没有本轮合并/推送授权，不自动交付main或启动BE-07。下方D-056待确认内容为此前历史。

2026-10-18/D-056本机启用纠错：用户截图报告密钥指南的KeySize赋值只读错误；在Windows PowerShell5.1真实复现，旧版.NET的RSA.Create返回对象暴露只读KeySize，原指南仅在PowerShell7验证不足。修正为RSACng(2048)构造，脚本作用域内遇错即停、所有目标文件写前预检并输出五项配置路径；旧脚本可能留存文件，恢复改用新目录，不覆盖或读取用户现有密钥。原文档脚本回归在5.1先失败、修正后5.1与7均通过：六文件、2048位、真实RS256往返、公钥无私有参数、重复与部分文件存在时拒绝且不改文件；专属临时密钥清理完成。仅修正文档与操作脚本，未改Java/API/DDL、未连接数据库，不将此次验证等同于本人验收；BE-06仍待A确认。

D-056（2026-10-18）：用户明确本人负责A，并选择本轮完整实现BE-06；先设计/TDD，在codex/be-06-identity开发分支实现登录、当前会话、退出、改密和三类RS256身份入口。目录样例为web/identity/controller、dto及identity/service、repository、security、config；不建立空业务接口。用户A认领已登记，BE-06为IN_PROGRESS待本人确认及合入，其余29项PLANNED；BE-07下一轮、BE-08仍等D/BE-10。身份启用需独立本地RSA密钥环，关闭时受保护路径503，不能绕过认证。最终完整clean verify/mysql-check为74离线＋59真实MySQL，失败/错误/跳过均0；专属实例随机schema/用户清理为0、进程关闭及本轮临时密钥目录清理已核对。静态校验通过，原API/DDL/迁移未改。设计/证据见[身份记录](../05-detailed-design/BE-06-IDENTITY.md)，本机与团队接入见[启用说明](../05-detailed-design/BE-06-LOCAL-SETUP.md)。未操作用户已有数据库、IDE秘密或共同主线；下方D-055的全PLANNED/未认领A为历史。

D-054/D-055主线交付（2026-10-18）：用户明确“合并主分支，推送至云端”。fetch后本地/远程main同为5a07259，分支43c243a含4个文档提交；静态核对PASS，27个文档/元数据/校验文件、后端实现未变。保留历史合入main生成c2cab3a63e5f3d3b9da08bcad4291a6c1f03126c，合并树与43c243a一致、无冲突；push成功，ls-remote回查refs/heads/main为上述完整SHA。README业务依赖图、A～D分工和同步任务资料已进入共同主线，成员可pull更新。30项业务仍PLANNED，实名/账号与成员接入证据待补；不运行Java/SQL、不标记业务验收或部署通过。原工作分支及无关.codeartsdoer保留。下方“未合并/推送”为各文档编辑轮次的历史状态。

D-055文档呈现补充（2026-10-18）：按用户给定的三列表替换四人分工文档开头，随后按用户补充要求统一使用“A～D／负责方向／对应任务”，删除成员编号对应关系说明；后续A→B→C→D详细说明及任务归属保持。同步生成源，静态核对通过。

D-055（2026-10-18）：按用户要求完成[四人分工](../05-detailed-design/TEAM-ASSIGNMENT.md)，先汇总全部任务，再按A→B→C→D展开具体交付、依赖和接口交接。A身份/范围/运行交付7项，B系统角色/权限/集成收尾8项，C申请审批/执行/审计8项，D组织人员/来源/导入7项。30项各唯一Owner代号且仍PLANNED；实名/GitHub账号、成员权限/本机验证待补，不默认用户=A。同步任务生成器、校验器、68操作主责、共享模块责任及负载汇总；原技术依赖/范围/估算不变，D-051负责人自行确认，无独立评审人。当前A/BE-06与D/BE-10可按接入情况先行，其余按依赖就绪推进。本轮静态核对PASS：30项唯一Owner、68操作主责、四人负载、55项唯一决策及生成器确定性通过，README依赖图与原技术依赖等价；Markdown的UTF-8/围栏/共享路径与空白核对通过。本轮为文档及静态追踪，不实现业务、不运行Java/SQL、不合并或推送；下方D-054/D-053待分派描述为当时历史。

更新日期：2026-10-18（Asia/Shanghai）。这里只维护项目状态与事实，历史通过 Git 和下方明确标注的历史章节查询。

## 当前状态

D-054：用户要求将业务开发先后/并行关系直接以Mermaid树状图放入根README，节点仅BE编号与任务名称。已按issues.json中BE-06～35的依赖绘制30节点，省略可由其他路径表达的重复连线后保留49条箭头，语义覆盖原62条业务依赖；多前置汇合仍全部等待，起点BE-06/10可并行。同步README目录及过期实施/独立评审描述，静态校验器新增节点名称/完整覆盖/依赖可达关系等价核对。工作分支codex/business-development-flow，本轮只改文档与静态检查，未实现/分派业务、运行Java/SQL或合并推送；D-053主线交付事实保持。静态校验PASS：30节点编号/名称及依赖可达关系与任务清单一致，331处本地链接、54项唯一决策及生成器确定性通过；Markdown的UTF-8/围栏/无本机路径/链接和Git空白检查通过。该检查未进行图像渲染。

D-053（2026-10-18）：用户明确“确认基座通过，合并 main 并推送”。已将接受版本379ad43以b68aa9a保留历史合入main，push成功，远程refs/heads/main回查为b68aa9aee6e78dd0b2cc1922d68028ce68ddeb11，与本地合并一致。BE-01～05均OWNER_ACCEPTED、F-01～06均VERIFIED，基座门槛FOUNDATION_ACCEPTED。此记录不把成员环境、业务实现或云端部署记为已完成；30项业务仍未分派。

| 阶段 | 当前事实 | 交接入口 |
| --- | --- | --- |
| 需求/原型 | 需求V1.3仅功能权限；Material3原型有条件通过，3项缺口按D-029后续跟踪 | [正式用例](../01-requirements/usecases-v1/用例说明.md)、[原型评审](../02-ui-design/STAGE-REVIEW.md) |
| API/数据库 | Git契约1.2.0与指定Apifox导出核心一致；数据库1.1设计及基础迁移/映射/约束已有证据，完整业务待后续 | [契约同步](../03-api/APIFOX.md)、[数据库](../04-database/README.md) |
| 基座 | BE-01～05已本人确认并交付共同main；当前实现沿用64离线＋42引擎通过结果 | [整体交接报告](../05-detailed-design/FOUNDATION-REVIEW.md) |
| 业务与交付 | BE-06～35共30项PLANNED/ASSIGNED，D-055已登记A～D唯一成员代号，实名/GitHub账号及成员接入证据待补；D-051由负责人确认完成，不设独立评审人；正式JWT/业务/OA示例/部署继续按所属任务推进 | [任务清单](../05-detailed-design/TASKS.md) |

成员clone后按[本机配置指南](../05-detailed-design/FOUNDATION-USAGE.md)操作。D-052已改为IDEA优先，项目结构、模块SDK继承、Maven导入/运行及应用JRE分别核对为21；VerifyBackend在IDEA构建，JAVA_HOME仅在可选终端附录。各成员的实际配置尚未代为验证，密码和HMAC密钥自行本机保存，独立库可独立密钥，同库实例须同密钥环。

## 基座证据与边界

- BE-02/f911c60按D-042由用户“确认通过”；BE-03/b8caeb2按D-046技术核对后“继续”的上下文确认，保留原始消息；BE-04/58b556a按D-047确认本机健康UP；BE-05/d059d11按D-049结合15:28测试库连接/启动日志、用户健康UP及单独明确开关true确认。单项确认不等于主线交付或整体门槛通过。
- 用户15:44开发库证据：Flyway1/2/3的success均1，mutation_guard为id1/revision0；普通BackendApplication日志连接localhost:3306/rbac_admin_dev/MySQL8.4.8，Hikari/JPA、Tomcat8080及健康UP。开发库已初始化；测试库此前迁移/重复运行/普通启动及账号授权截图已核对。日志未展示的实际账号不作推断。
- 运行账号rbac_app仅两个库的DML；迁移账号rbac_migrator有库内DDL/触发器权限，但用户binlog=1/trust_creators=0还有额外触发器限制。沿用管理员受控独立迁移策略，普通应用不以root运行。密码/密钥只经本机环境注入，不进入共享文档、日志或Git。
- 本轮复核现有JUnit报告：64离线＋42真实MySQL检查，失败/错误/跳过均0；当前实现自d059d11未变，现有jar包含三项公共自动配置且不含测试夹具。没有重新运行Java或SQL、连接已有库、读取IDE秘密。静态验证结果见[validation.json](../05-detailed-design/validation.json)，不代替真实测试。
- AT-01/02/03/04/33/37仅PARTIALLY_VERIFIED，其余35项DESIGNED_NOT_RUN。当前资格/重放授权为回调端口，正式JWT/范围未实现；固定申请RFC8785归BE-21。基座不等于完整后端验收。

## 下一步与协作待办

1. 团队成员clone/pull共同main，按指南完成本机配置并反馈版本、提交、测试及健康结果；不分享秘密，不重建已有数据。
2. 团队补A～D成员代号对应的姓名/GitHub账号并核验拉取/任务分支推送或PR权限；D-055已登记BE-06～35归属，继续补依赖提交及成员本机证据。各项按原依赖正式开工，当前A/BE-06、D/BE-10技术前置满足。当前没有远程Issue/PR，不虚构人员或链接。
3. CI/保护规则、云端Docker数据库及部署/备份恢复按后续任务落实；服务器存在不代表部署验收完成，正式前端后置、演示OA边界不变。
4. D-053本轮已完成远程读取、主线合并、推送及回查，后端实现自d059d11未变；没有运行新Java/SQL、连接已有库或读取IDE秘密。无关.codeartsdoer、日志、临时数据库和产物未提交。当前工作分支为main，原codex/foundation-environment保留。

D-053静态收尾PASS：328处本地链接、53个唯一决策、生成器确定性、确认原文、合并父提交与祖先关系及状态/来源一致性通过；17份改动Markdown的UTF-8/围栏/无本机路径/本地链接及Git空白检查通过。30项业务仍PLANNED且未分派，6项AT保持局部验证、其余35项未运行；没有新Java/SQL运行结果。

共享状态、任务生成器与静态校验同步记录用户整体确认及交付SHA，后续状态记录提交与首次交付合并分开追踪。下方为明确历史章节，其旧状态由本节替代。

## 先基座后分派的交付历史（2026-10-17）

后续发布交接：用户明确要求“更新推送至云端”。合并前工作区干净，fetch核实main与origin/main均为9416980；以b087e78无冲突合入5e521ef，合并树与文档分支一致。按本轮明确授权同步main，D-038中前一轮不自行合并/推送的边界不妨碍本次操作；未改变任务状态或启动业务实现。

用户已明确选择并授权同步文档，见D-038。BE-01～05由用户先实现，真实评审人尚待登记；基座范围、结束条件和交接见[FOUNDATION-HANDOFF](../05-detailed-design/FOUNDATION-HANDOFF.md)。公共身份接口先定，真实登录/JWT/范围等业务留第二阶段，基座不预写全部业务实体。

BE-06～35的实际Owner/Reviewer均未分派，A～D只保留suggested建议。原技术dependencies保留，另以FOUNDATION_ACCEPTED和BUSINESS_OWNER_ASSIGNED控制业务启动。机器阶段、任务、API归属及模块清单已同步，所有任务仍PLANNED，基座门槛NOT_EVALUATED、证据为空。common是公共责任组，Java包沿用core，13逻辑模块不重划。

逐任务技术范围/工作量沿用1.0，两阶段汇总重算：基座开发9/18/28人时、独立评审1.25/2.5/5；后续开发61.5/112.5/181、评审7.5/15/30。全量仍79.25/148/244，超40～72容量；不再展示未分派人员的固定负载。仅作乐观容量下界，不承诺5～6天全量。

本轮只修订仓库文档及设计维护脚本；未开始正式编码、运行MySQL/DDL、创建远程任务、修改需求/API/数据库或更新旧外部讨论附件。其他成员可先阅读/评审/准备样例，实际业务分派在基座通过后进行。下一步由用户启动BE-01→05的TDD实施；实施开始时再按ENGINEERING更新正式工程目录约定。以下保留先前交接历史。

本轮静态复核PASS：5项用户基座、30项未分派任务、68项未分派业务操作及阶段门槛一致；182处本地链接和38条决策有效；原35项技术依赖/操作/表/T/I/估算与9416980一致，技术图源逐字节未变，上游权威摘要不变。该结果不代表F-01～06或业务测试已通过。

本轮修改文档的本机路径/代码块复核时，发现DECISIONS的D-001及LAYOUT迁移说明仍带旧本机目录；按用户既有约定去除存放前缀，项目根统一为rbac-admin，不改仓库边界、导入快照或Git历史。

## 编码启动交接历史（2026-10-17）

用户明确授权合并README修订并推送，要求保持可开始编码的状态。合并前工作区干净，fetch核实本地main与origin/main均为1320557，没有遗漏远程提交；本次以d36534a无冲突合入5fac275，合并文件树与文档分支一致。本次只维护并同步编码基线，不创建正式工程、运行数据库、实施业务代码或登记远程Issue/PR。

后续从最新main创建任务分支，先完成BE-01中尚未落实的成员/环境/规则准备，再进入BE-02骨架和相应依赖任务。设计、TASKS及项目TDD技能已提供；“可开始编码”指设计和协作入口可用，不代表JDK/MySQL/服务器环境已验证、应用已可构建或业务测试已通过。正式工程目录在实施启动时按ENGINEERING更新约定，业务行为按TDD及PR评审交付。

交接核对：26份README及AGENTS无本机路径，9项关键设计/编码入口文件存在，35项任务仍为PLANNED，设计及README修订提交均为main祖先，未创建backend/deploy/tools/.github，差异空白检查通过。沿用此前366处README本地链接验证；本次未改这些链接或重复运行无关业务检查。

## README路径与导航复核历史（2026-10-17）

用户要求项目根目录统一表述为rbac-admin，不暴露个人本机存放路径。已检查根目录和docs下26份README：清理根README的绝对项目路径及原型README的绝对启动路径，同步协作入口AGENTS的根目录约定；原型启动示例改为从rbac-admin执行相对路径。顺带修正README中远程未配置、API尚未开始、测试框架待定等过期状态，未修改原型代码或业务设计。

本次使用独立文档分支codex/docs-portable-readme；26份README及AGENTS复扫未发现本机路径，366处本地链接有效，代码块成对、原型相对启动目录存在，Git差异空白检查通过。当前文件检查不代表已清理既有Git历史。本节为文档维护事实，实施、数据库和远程Issue/PR状态不变。

## 主分支合并与GitHub交接（2026-10-17）

用户明确指定GitHub仓库并授权“合并到主分支后，配置远程仓库并推送”。开始时工作区干净，main为d032a95且是设计分支10edfdd的祖先，未配置远程。只读ls-remote成功但无refs，确认目标仓库初始为空；按授权配置origin，原地切换main，以--no-ff合并设计，保留完整设计历史。

合并提交abbcc1e的文件树与10edfdd完全相同，无冲突；main首次推送成功并设置跟踪origin/main。远程回查HEAD指向refs/heads/main且SHA为abbcc1e8a49bd580beccecd90cf3f3c4fa0a0aa2，与本地一致。本次只推送main，其他设计分支保留本地；后续交接记录单独提交并同步，不改业务需求或实现状态。

默认沙箱不能连接本机代理，使用获准的沙箱外Git命令完成连接和推送；未修改系统/全局代理，未在文档保存凭据。尚未创建远程Issue/PR、邀请成员或配置分支保护/CI。本次主分支直接合并与推送已有用户明确授权，不代表后续任务可跳过Issue/PR评审。

下一步：邀请成员并落实真实负责人/评审人；在此GitHub仓库登记已有任务、按可用权限配置PR规则，准备开发环境并协调超预算排期。团队从最新origin/main开始后续任务分支；工程、数据库、部署与远程任务状态均须按实际验证推进。以下保留设计交付时的历史事实。

## 后端详细设计与团队任务交接历史（2026-10-17）

用户授权执行上一轮讨论的后端详细设计、任务分解、估算和部署方案，未授权本轮正式业务实现或远程Issue/PR创建。已确认4人、5～6天每天每人2～3小时（40～72人时）；前端原型已交付、正式前端后置；服务器未买、MySQL未配置，无真实OA，采用示例演示。D-035/036记录业务交付边界与本轮技术取值，实际团队账号/平台/服务器仍未定。

从bd26465干净工作区原地建立codex/detailed-design，无worktree、不合并main。交付[详细设计](../05-detailed-design/README.md)：模块结构/数据流映射、68操作处理输入输出、公共端口、关键流程/状态/事务/恢复、JPA适配、演示OA、工程/部署/初始化/回退、前端后置交接、Issue/PR协作及验收。13个逻辑模块；35个本地BE任务均PLANNED且无远程号；41项验收均未运行，其中40后端/部署/协作、1前端后置；两份六页原生可编辑drawio。

设计采用单体本地事务，M00统一guard/幂等/审计，M10编排跨域失效，M09自动一次执行，M11按新鲜主库快照安全读取。来源资格与角色所属部门资格分别失效，沿用Q-01和恢复不复权；不改需求/API/表结构。正式工程目录/版本/工具在ENGINEERING给出设计取值，未创建目录/工程或实际构建。演示受理不批准，最终决定仍走原OaJwt回调；真实OA不作为本期示例验收前置。

完整任务含评审低/中/高估算79.25/148/244人时，高于40～72容量，A负载较高；提供重分配建议和容量下界，未伪造精确日历。全部需求仍保留，若维持全量应增加投入或顺延；范围调整另确认，不把Mock/空工程部署当全量完成。服务器/数据库、远程与实际分工在BE-01落实，真实服务器验收BE-34仍是后续工作。

静态结果见[VALIDATION](../05-detailed-design/VALIDATION.md)与机器报告：模块/Issue依赖DAG、67入站＋1出站唯一主责、表/T/I/模型/参数引用、估算/负载、生成确定性、图源几何/端点、本地链接及上游摘要。没有业务RED/GREEN测试、MySQL/JPA/并发/部署/OA集成结果；未修改原型、正式需求、API、数据库草案或Apifox原始导出。drawio客户端未找到，仅交付XML检查；Apifox指定1.2导出已通过的事实及MCP限制沿用、不重查。

下一步：团队读设计，确定真实成员/平台/远程与环境、协调超预算排期，将本地卡片登记为真实Issue；各模块对应设计评审后TDD实现，经PR评审和真实集成/部署验收关闭。当前仍无远程仓库或已创建Issue/PR，不自行推送或合并main。以下同步记录保留其交付时的历史事实。

## Apifox1.2.0指定客户端导出通过历史（2026-10-17）

用户明确前次误选保留的旧导出规格；本次使用下方RBAC权限中心API/1.2.0规格，原始文件SHA为67fd08c024a365cfef26485a5984be28c7941cfc9662e1ae8d3ddac558fd5501。比较器实际退出0、coreContractConsistent=true；67入站＋1出站/129模型及120模型/104媒体示例通过，认证、任命ID、上传及1.2.0新增约束一致。只有license/占位servers两处元数据差异，摘要保留。权威Git契约SHA不变。

后续从指定已保存规格导出，无需每次重填或重新导入；上方旧默认规格建议改名以免混淆，未代用户操作。固定链接可作为便捷入口候选，官方支持RAW URL，但本轮未获取/核对该链接，不称自动同步已完成。当前MCP旧暴露差异沿用，无刷新/重复排查；指定导出通过不扩大为整个工具链完全一致。验证/历史见[APIFOX.md](../03-api/APIFOX.md)。

下一阶段可按Git1.2.0/数据库1.1开始详细设计、模块分解及验收追踪；本轮只完成同步核对/共享记忆，没有进入详细设计/正式实现、建库或执行SQL。以下保留此前误选规格、尚未通过时的真实检查历史。

## Apifox此前再次导出历史（2026-10-17）

新原始文件SHA为f7a3a59bd1b595c3e65b7646d552405e5662c5c5b105228ca280e829326d162e。Appointment三处ID类型及全部约束已修复，120/120模型、105/105媒体示例通过；67入站＋1出站/129模型及1.2.0新增约束仍在。但info为默认模块/1.0.0，登录/退出认证、上传根约束及空示例均未体现指定完整三路径覆盖。比较器退出1，核心一致性未通过；8处差异为6阻断路径＋2展示差异。具体见[APIFOX.md](../03-api/APIFOX.md)最新章节。

下一步只在实际点击导出的规格里保存完整OAS覆盖并从同一配置再次导出，不重新导入或再改正确的Appointment。默认规格保留，不手改导出证据，不重查MCP，不改权威契约/业务设计/原型/数据库。以下同步跟进保留前次导出尚未修复任命时的历史事实。

## Apifox1.2.0同步跟进历史（2026-10-17）

用户已重新导入并导出，新增字段及任职依据约束、67入站＋1出站/129模型到位；实际info.version=1.2.0，不删除截图中仍标1.0.0的默认规格。原导出SHA852c787ec244417bc92268f87013352f7c1d7c745079996849536049b3628475保持原样。比较器检查119/120模型示例通过（Appointment失败）、104媒体示例通过，核心一致性未通过：登录误继承AdminJwt；Appointment三个ID位置被改为object。具体修正见[APIFOX.md](../03-api/APIFOX.md)最新章节，本地完整覆盖配置已扩为三路径补登录，尚待用户替换；Appointment需客户端基础模型改回string后再次导出。

比较器只在外层object上下文归一化冗余分支type；4项边界检查通过，不放宽类型/认证规则。63处差异对应两项阻断问题及两处展示差异，不能将重复引用展开计为61项业务问题。未改Git权威契约或远程工具，未重复调查MCP、未建库/实现。下一步是客户端修正并再导出，不重新导入同一文件覆盖已有手工修正。以下正式评审段落保留当时尚未导入1.2.0的交接事实。

## 正式需求—API—数据库评审交接（2026-10-17）

用户授权本次正式跨阶段评审，从codex/database-design/e3fa818干净工作区开展，不建worktree、不合并main。报告见[CONSISTENCY-REVIEW.md](../04-database/CONSISTENCY-REVIEW.md)。6项设计缺陷（3项P1/3项P2）已修订：配置保留停用项不额外拒绝、来源关闭响应不泄露混合申请计数/审计ID、删除后幂等重放保留当前授权所需事实、完整任职依据不误用100上限、HTTP/OA幂等边界及读写追踪、模型名称精确映射。

输出未上线API1.2.0及数据库1.1草案：43表/356列/79外键，新增内部authorization_basis及完成结果不可变触发器（共41）；67入站＋1出站、129模型。25场景正式文档核对与设计推演、12项新增Schema/CHECK静态回归及契约/DDL/引用/追踪检查见[VALIDATION.md](../04-database/VALIDATION.md)及机器报告。未启动/连接数据库或执行SQL，未进行真实业务/并发/JPA/OA/性能测试。未更改需求、原型或创建工程。

H-01已明确沿用DRAFT及总管理员现有编辑/删除清理，错误不泄露隐藏申请；H-02～05仍待后续真实OA、运维保留恢复、真实引擎/JPA及原材料映射核对。Apifox既有1.1.0指定导出证据保留，1.2.0尚未人工同步；不重查旧MCP差异，不把工具链称完全一致。下一步按既定配置同步新契约，再依据Git1.2.0/数据库1.1开展详细设计，转化25场景为后续验收/TDD清单；本轮尚未进入详细设计。决策D-034。

## 数据库1.0首次交付历史（2026-10-17）

用户授权只完成数据库设计，不进详细设计/正式实现；明确不需真实建库，因此本轮仅检查MySQL二进制版本8.4.8，未启动临时或已有实例、未连接数据库、未执行SQL。起点codex/api-design/2b78430且工作区干净，分支与指定提交无差异，原地建立codex/database-design。正式交付见[数据库入口](../04-database/README.md)、[自查与交接](../04-database/REVIEW.md)、[验证记录](../04-database/VALIDATION.md)。

43张表、完整字典、四页原生可编辑ER图、MySQL8.4 DDL及不可变性触发器草案、唯一性/删除历史/索引/并发/JPA/幂等/预览/导入/OA设计和68操作追踪。设计采用独立来源周期、永久关系基线、一次grant历史、固定RFC8785原字节和OA最终结果/成功凭据三层防重。T0统一写锚点串行本地状态变更，网络/OA等待不持锁，成本及后续验证保留。具体MySQL8.4、Boot4.0.8/Java21及BOM依赖是本轮技术设计取值/建议，不伪称用户此前逐项确认。

Q-01：用户将同批“结束A部门最后适用任职后新增该部门岗位任职”解释为离岗后新岗就职，采用明确事件顺序：推进周期、旧授权/失去资格的任命及相关旧申请持久失效，同批新任职不复权；其他适用兼岗始终存在、仅岗位更名仍保留。已同步正式需求、相关图表源/阅读副本及API交接说明，权威openapi.yaml仍1.1.0且SHA未变。见D-033。

本轮只做静态结构、引用、命名、DDL方言解析、有限CHECK表达式推演和追踪检查；未将其称为真实数据库、JPA、JWT、并发、性能、OA/HR或正式跨阶段评审通过。Apifox指定客户端导出核心一致的既有结论与MCP暴露差异沿用，未重新排查或宣称工具链完全一致。非阻塞交接包括DRAFT范围变化后占用清理的API状态边界、保留/归档及备份恢复、真实引擎/JPA和OA能力验证、原材料初始化映射；详见REVIEW H-01～05。

## 已确认技术栈

2026-10-16 用户确认并要求沉淀：前端 Vue 3 + TypeScript + Tailwind CSS；后端 Spring Boot + Spring Data JPA；数据库 MySQL；API 采用 RESTful API，继续使用 Apifox 管理契约；身份认证凭证采用 JWT，功能授权按既有 RBAC 规则判断；采用结构化分析与结构化设计（延续 D-016）。决策见 D-028，技术栈总览见根目录 README。

API与数据库设计草案已交付，正式后端/认证/数据库实现待开展，当前阶段以本文件状态表为准。后续按阶段细化具体版本、JWT 有效期/续期/失效机制、测试框架及工程布局，落实退出、账号停用及资格变更后的既有会话失效要求。该次技术选型记录仅更新四份项目文档，核对选型与待办一致性、Git 差异格式通过；当时未运行应用测试或连接外部工具。API本轮验证另见下文。

## 分析与设计方法

2026-10-15 用户确认在项目说明中体现采用结构化设计。已在根目录 README 明确结构化分析与结构化设计方法，并在 WORKFLOW 补充自顶向下模块分解、高内聚低耦合、模块层次与调用关系、输入输出及接口的交付要求；决策见 D-016。现有顶层与第一层数据流分析作为分析依据，详细设计仍待开展，本次未改变业务需求与阶段顺序。

该次结构化方法说明仅变更文档，核对四份文档差异及 `git diff --check` 通过，未运行应用测试；当时尚未将工作区原型纳入其验收。后续本轮原型交付与验证见下文，结构化方法决策保留。

## 已确认需求

权威依据：[管理范围与人员权限规则](../01-requirements/usecases-v1/管理范围与人员权限规则.md)、需求总览及 UC-01～11。

- 仅功能权限，RBAC0；无业务数据范围、角色继承、直接用户授权或显式拒绝。公司/部门表达管理边界、任职及授权来源。
- 组织采用公司／分公司—部门—岗位，部门平级。一项任命指定一个公司、一个管理部门及系统，不包含其他部门；同一人多部门管理分别任命并切换，不能拼接资格。同范围协作，不再整公司共享管理资格。
- 公司级共享角色只由总管理员维护，权限管理员可为获准范围内合格员工申请分配。部门角色要求角色所属部门有效任职，岗位名称不自动授予角色。
- 普通员工不登录平台；总管理员管理后台账号和任命，账号状态、员工身份、管理员资格与业务授权分别判断。
- 不设计公司/部门启停。离岗关闭原部门来源权限立即生效并审计、不经 OA，保留其他来源与员工身份；全局员工启停仅总管理员，恢复不复权。
- 来源部门与角色归属/管理部门分开。相同员工及角色可有多个部门来源；关闭记录来源周期，阻止迟到批准在调岗/返回后恢复旧授权。
- 角色配置、分配及一般指定角色撤销仍经 OA。审批、执行、投递分别显示；发送失败重试同一固定申请，不新增本地撤回或超时取消。
- HR/OA/业务系统一期采用文件、模拟或简单调用演示，未宣称真实接通。

## 文档维护

2026-10-16：按用户要求，共享记忆以 `rbac-admin` 表示项目根目录，仓库内文件使用相对路径，不记录本机绝对路径；导入来源以用途描述保留。已检查本文无本机绝对路径，Git 差异格式检查通过；仅文档修改，未运行应用测试。

2026-10-16：按用户要求建立团队[统一术语表](GLOSSARY.md)，整理总管理员、权限管理员（部门管理员）、公司级共享角色、部门角色、管理范围、授权来源及审批/执行状态等概念、别称、边界和示例。AGENTS 将术语表纳入开工阅读，根目录及 docs 导航、WORKFLOW 已加入统一表达和持续维护约定；决策见 D-018。沿用 V1.2 业务规则，未采纳“公司通用角色”的命名建议，界面首版仍待评审，API 阶段仍待开始。

本次仅修改协作文档：7 份文档的 62 个本地链接、38 个术语及 3 条对比示例的名称唯一性、18 条决策编号唯一性检查通过，Git 差异格式检查通过。未修改需求正文、图源、预览或原型代码，未运行应用测试。

用户已选择 **PNG 按需生成**。当前正文/图源 V1.3，现有 PNG/SVG 保留 V1.1；README 和 HTML 已标明旧图可能包含已替代规则。新增子流程尚无图片。

日常运行 `python docs/01-requirements/refresh_requirements.py`，更新图源、课程简表、索引和阅读副本，不写图片。评审/交付显式运行 `python docs/01-requirements/refresh_requirements.py --previews`，完整成功后更新预览版本。只同步副本可用 `python docs/01-requirements/sync_usecase_folders.py`，不得绕过手工修改保护。详见 [PREVIEWS.md](PREVIEWS.md)。

## API 契约评审（2026-10-16）

沿用codex/api-design、基线269d269，开始时工作区干净；核验api-designer1.1.0可用。评审记录见[REVIEW.md](../03-api/REVIEW.md)。发现并修订7项契约问题（3项P1、4项P2）：静态状态/来源非法组合、预览缺少目标影响、已失效申请重投歧义、无变化与版本优先级、两项201/Location语义、导入规范字段回读、目录/权限归属结构。修订为API1.1.0，67个入站操作/56路径、1个出站webhook、129模型；新增状态分支仅为API快照，不是数据库表。无新增业务阻塞问题，不重开需求访谈。

本轮OpenAPI结构及1142处引用、120模型示例、789媒体示例、原18项及新增25项Schema场景通过；--baseline可重现首版10个非法组合被接受，修订版拒绝。18组联合模型的466对分支有必填字段域互斥依据；固定Redocly2.8.0为0错误0告警，额外2.64.1有55条启发式重叠告警，独立检查确认互斥，未关闭规则或削弱约束。Prism下67项HTTP结构、5项非法输入、1项错误响应通过，Mock已停止；不是正式业务/认证/事务验证。验证及SHA见[VALIDATION.md](../03-api/VALIDATION.md)。

MCP本轮刷新及读取根/三类组件引用成功，下载时间2026-10-16T15:12:56.488Z（Asia/Shanghai23:12），默认模块仍空；无写入/导入工具，本地1.1.0未同步。评审有条件通过，待人工导入及完整回读后按项目顺序交接数据库阶段；本轮未进入数据库设计、后续跨阶段一致性评审或正式实现，未改需求/原型/技能，未合并main。

## 首版 API 设计交付（2026-10-16，269d269）

使用已核验可用的 api-designer 1.1.0，先整理接口清单/公共约定，再完成逐模块契约。本地交付见 [API入口](../03-api/README.md)：55个路径、66个入站操作、1个出站OA webhook、119个模型；覆盖UC-01～11、受限员工影响预览、批量一般撤销及目录稳定排序。JWT与当前身份/范围校验、固定申请、原范围依据、来源周期、关系版本、OA自动执行与失败恢复已在契约说明中明确。

Redocly 2.8.0 校验0错误0告警；OpenAPI结构、1091处引用、117个模型示例、780处媒体示例、18项正反Schema场景及追踪检查通过；本地Prism下66个操作HTTP形状、5项非法输入、1项文档化Problem响应检查通过，Mock已停止。以上不验证真实认证/事务/业务行为，正式后端未实现。验证、SHA及未决项见 [API验证记录](../03-api/VALIDATION.md)。未修改原型、需求或技能，未设计物理数据库表，未合并main。Apifox默认模块仍为空，新契约未导入。

## 验证与限制

2026-10-16 API 交接优先级：用户明确三项原型展示/演示缺口在当前阶段优先级低，准备新对话开展 API 设计。按 D-029，不再要求先补齐原型或以此阻塞契约定稿；原型补齐保留为后续跟踪项，员工影响预览、批量一般撤销及目录排序的既有业务规则仍须在 API 中覆盖。同步评审报告和导航，只改文档，未运行应用测试，未创建分支/worktree、API 契约或真实接口。

2026-10-16 阶段评审：用户要求评估是否可进入 API。对照 V1.3、阶段流程、UC-01～11、原型代码及浏览器路径，结论为有条件通过；没有发现必须重开需求分析的重大业务矛盾。额外浏览器复现 3 项缺口：角色配置仅显示权限差异而无员工影响摘要；一般撤销只演示单条，多选员工只有批量分配；目录排序值保存后未改变呈现顺序。它们保留既定需求，在 API 设计期间同步补齐、契约冻结前复核。详见 [阶段评审报告](../02-ui-design/STAGE-REVIEW.md)，不把评审建议记为新增已确认业务规则。

本轮重新运行完整 46 项 Edge 检查（约 1 分钟）及类型/构建，全部通过；额外核对上述 3 项缺口，未修改原型代码。重新从服务器刷新、读取 Apifox 默认模块成功，仍为 OpenAPI 3.1.0/文档 1.0.0、0 个接口；只读工具就绪，Git/Apifox 同步流程待确定。报告列出下一阶段首批工作，尚未编写契约或进入数据库/业务实现。此前 51 组合布局验证沿用，不记为本轮重跑。

2026-10-16 界面信息密度收敛：用户明确要求减少解释性语句及不必要 UI。移除 8 个视图的副标题、员工组织/角色/调用演示固定说明横幅、说明页脚、申请固定流程图和工作区品牌页脚；一般规则转入顶部“页面帮助”，按当前模块展示，原型演示说明默认折叠。办理范围压缩，保留公司、部门、系统与任命切换；单部门员工视角隐藏重复部门筛选，多部门仍可筛选。字段校验、只读限制、关权/停用影响、OA 投递/审批/执行及错误状态保留。沿用 V1.3 与 Material 3 Token，没有需求变化或真实接口实现。见 D-027。

新增 2 项页面帮助检查先失败再实现，窄屏巡检又发现旧样式隐藏手机帮助入口，新增复现测试先失败后修复；最终完整 46 项 Edge 检查通过（约 1 分钟），类型/构建和格式通过。一次旧回归依赖已移除的审计页脚，现改为验证筛选及每页条数切换后保留正确事件；初次类型检查发现误移除侧栏公司名引用，已恢复并通过最终构建。390、1063、1440px 下 8 视图与帮助、两种身份范围共 51 组合无横向溢出或页面/控制台异常，已查看手机帮助及桌面角色、组织、申请和单部门员工截图。仍待用户继续评审，详见 [VALIDATION.md](../02-ui-design/VALIDATION.md)。

2026-10-16 自定义操作登记：根据用户反馈将功能标题移至按钮前；补齐“登记操作”的自定义名称、编码及完整编码预览，保留未登记常用操作快捷选择。先整体验证再写入，拒绝缺字段、非法编码及重复名称/编码；系统进入权限继续由系统登记维护。新增操作按所属系统进入角色矩阵且默认不选，保存草稿不改变有效配置。父级只组织外部业务菜单，操作归属功能、不设自己的父级；无分组时选根目录。沿用 UC-01/V1.3，未自动初始化真实业务操作；见 D-026。

新增 3 项测试先在旧版全部失败（缺少操作输入区），实现后定向 7 项及最终完整 43 项 Edge 检查通过（59.9 秒）；类型/构建通过。8 个宽度下目录/配置弹窗及展开侧栏共 28 项，加 390、1063、1440px 自定义登记/角色矩阵 6 项，均无横向溢出或页面异常；已查看标题、登记与矩阵截图。仍为界面原型评审，API、数据库与真实外部连接未实现。详见 [VALIDATION.md](../02-ui-design/VALIDATION.md)。下列 D-025 的五类候选限制已被替代，其余修复保留。

2026-10-16 侧栏与目录评审修复：用户在 1063×704 提出侧栏无法展开、配置字段难理解、操作登记空白及卡片折叠需求。修复 840–1199px 导航按钮同时隐藏；首访图标栏可展开为文字导航。功能操作增加单项/全部展开收起，状态保持；登记操作只补充尚未登记项，功能/编码只读、无可新增操作时提示并禁用空提交。父级目录与菜单可见权限就地解释，术语表同步，沿用 UC-01/V1.3，未修改业务规则或需求图源。见 D-025。

新增 4 项浏览器检查先全部失败，再实现；最终完整 40 项 Edge 检查通过（55.1 秒），类型/构建与格式检查通过。8 视图 × 5 尺寸共 40 组合无文档溢出、运行时错误或外部请求；额外 8 个宽度下的目录/弹窗及侧栏展开共 28 个布局检查无横向溢出，已查看 1063px 的 4 张截图。详见 [VALIDATION.md](../02-ui-design/VALIDATION.md)。当前仍是界面原型评审，未接通真实后端/OA/HR。

2026-10-16 平级部门收敛：用户指出实际资料仅公司—部门—岗位并明确要求同步需求、术语和原型。只读核对 4 份工作簿并记录结构及 SHA-256，不复制完整表或员工姓名/电话；事实见 [参考材料与组织层级](../01-requirements/参考材料与组织层级.md)。V1.3 取代 V1.2 的部门子树规则；UC-01～11 共通规则、UC-07/10 专章、课程表/流程/时序生成器和术语已同步，功能目录父子规则保留。见 D-024。

原型移除部门 parent、管理根及递归范围扩展；以一个明确部门办理，3 项独立示例任命可切换。采用材料中的道路/桥梁工程分公司、第一/第二项目经理部等平级名称，员工仍为虚构；未导入全部人事资料或接通外部系统。3 项新增边界检查先失败再修复；既有多部门流程改为明确切换任命或由总管理员演示。曾发现示例申请统一绑定旧 M-01 导致审批执行回归失败，现按明确部门绑定对应任命并复核通过；最终完整结果见 [VALIDATION.md](../02-ui-design/VALIDATION.md)。

最终完整 36 项 Edge 检查通过（44.6 秒），构建/类型及格式检查通过；8 视图 × 5 尺寸无页面溢出、运行时错误或外部请求，另复核 1063×704 的部门、组织与范围弹窗。统一刷新器、227 份阅读文件同步成功；229 个图页 XML/ID/引用及 DFD 边界检查通过，196 个 PNG/SVG 摘要全部保持不变；JSON、Python、本地链接和 24 条决策编号校验通过。原有图源 CRLF 保留，差异按 `cr-at-eol` 核对；源工作簿未修改，临时截图及检查脚本不纳入 Git。

下列为历史阶段验证，V1.2 的部门子树及旧示例名称已由本轮规则替代。当前阶段仍是原型评审，待办是继续评审页面与流程后进入 API；后续契约/数据库采用指定部门精确匹配，不再设计组织递归授权。

2026-10-16 角色编码原型调整：用户针对手填编码规范问题要求调整，已采用 UC-02 允许的自动生成方式。创建窗口不接受手填编码，保存后在列表及配置详情只读展示；更名和权限配置不改变编码，旧样例编码保留。原型使用全局内存序号及已有编码检查；正式系统由后端生成、数据库保证全局唯一，API/数据库阶段需细化生成与并发策略。该实现不代表真实后端或数据库完成；决策见 D-023，统一术语见 [GLOSSARY.md](GLOSSARY.md)。

新增 2 项浏览器检查先在旧原型运行失败，再实现并验证，覆盖无需手填编码、名称必填、更名/重新打开编码稳定及跨公司同名角色编码不同；既有 OA 配置用例改为读取自动编码继续追踪角色。完整 33 项 Edge 检查通过（44.4 秒），类型/构建及代码格式检查通过。1063×704、1440×900、390×844 的新建/配置共 6 个弹窗布局检查无内部横向溢出及页面异常，已查看桌面新建和手机配置截图。当前仍为界面评审阶段，详见 [VALIDATION.md](../02-ui-design/VALIDATION.md)。

2026-10-16 原型评审反馈：总管理员页面保留，顶部增加常显“切换为总管理员（演示）”及返回部门身份入口；继续按身份隐藏无权菜单。修复 `.field input` 错误覆盖复选框宽度导致的分配弹窗控件拉伸和文字竖排，桌面弹窗收紧为 720px、员工选择区为 104px。沿用 V1.2 功能权限规则，快捷切换只属于原型工具，不代表正式身份升级；见 D-022。

本轮新增 4 项回归先运行并观察失败，再修复；最终完整 31 项 Edge 浏览器检查通过（39.6 秒），类型/构建和格式检查通过。1063×704、1440×900、390×900 的分配截图及初始/批量选择尺寸复核无弹窗内部横向溢出；复选框 15px，角色行约 62px。8 视图 × 5 尺寸的 40 组合无文档横向溢出、运行时错误或外部请求。此前文档宽度巡检未覆盖该弹窗尺寸，现已补齐。共享交付及限制见 [VALIDATION.md](../02-ui-design/VALIDATION.md)；当前仍为界面阶段，等待继续评审，API 未开始。

2026-10-16 Material 3 实施：用户明确“执行此方案”，已按确认方案完成浅色导航、蓝色强调、语义 Token、本地 Roboto/Noto Sans SC 及 49 个 Material Symbols；平台管理仅总管理员显示，调用演示及身份/状态控制移到默认收起的原型工具。保留 7 个管理主页面、独立调用演示及登录/帮助，界面统一称“权限中心”。决策见 D-021。

补齐草稿保存/合法删除、驳回重提、逐人来源批量分配与预览、列表分页、审计筛选及关联跳转、目录层级/菜单绑定/资料编辑/独立操作、组织岗位 Mock 预检及未保存配置退出确认。角色与员工授权边界继续遵循 V1.2；组织岗位预检为固定场景，人员 CSV 限制及其他原型边界见阶段 README。

本轮 `npm run build` 及格式检查通过，最终 27 项浏览器交互检查全部通过（41.3 秒）；8 视图 × 5 尺寸的 40 个组合无页面横向溢出、未捕获运行时错误、未发现外部网络请求。55 个视觉资源及许可证文件共 207,807 字节，来源与 SHA-256 清单随代码保存；旧图标依赖移除。新增行为检查先观察失败再补齐，正式服务仍须后续按契约与详细设计开展 TDD；当前没有真实认证、API、数据库或 OA/HR 连接。详细证据见 [VALIDATION.md](../02-ui-design/VALIDATION.md)。

交接检查：69 个改动文档本地链接、21 条决策编号唯一性及 55 个资源摘要核对通过。代码及文档 Git 差异格式检查通过；两份 Google Fonts OFL 原件各保留第 21 行的上游行尾空格，检查时仅排除这两份许可文件，文件原始摘要保持一致。未纳入构建、依赖或测试临时产物，需求与技能文件未改。

2026-10-16 Material 3 方案：用户明确要求浅色主题、Material Token、Roboto/Noto Sans SC、Material Symbols，并要求先交付方案、确认后再实现。已按 V1.2 用例核对现有 Vue 原型，形成 [MATERIAL3-PLAN.md](../02-ui-design/MATERIAL3-PLAN.md)，含 7 个管理主页面、独立权限调用演示、配套登录/帮助、导航、Token、旧版保留/修改/删除/新增清单及验收计划。待确认的视觉提案替代深蓝侧栏方向；需求规则、原型源码及依赖未修改，当前浏览器页面仍为旧版。决策见 D-020。

本轮访问用户指定的 M3 入口（文本读取仅返回 JavaScript 提示），补充核对官方 Material Web 主题/色彩/字阶/圆角及 Google Fonts Material Symbols 文档。文档链接、静态颜色配对及 Git 差异格式检查通过；本轮未运行应用构建或浏览器测试，未来交互和布局检查仍是计划。

2026-10-16 基础界面调整：用户选择优先改善视觉风格，采用深蓝侧栏、钴蓝操作色、浅灰画布，统一公共导航、表格和表单的阅读层级；新增术语说明抽屉，统一“公司级共享角色”文案，并移除未实现快捷键的搜索装饰。业务规则、模拟数据、依赖和需求图源未改变；决策见 D-019，设计与验证见 `docs/02-ui-design/`。

本轮 `npm run build` 通过，既有 16 项 Edge 交互检查全部通过（30.9 秒），8 页面 × 3 尺寸的 24 个布局组合无文档横向溢出及捕获到的运行时异常。额外检查桌面/手机术语抽屉的内容、滚动、Esc/按钮关闭及焦点恢复，登录页无横向溢出；已查看相关截图。格式及本地链接检查通过。以上均为原型验证，正式认证、API、数据库和业务逻辑仍待后续阶段实现。

2026-10-15 界面原型：在 `docs/02-ui-design/prototype/` 实现 Vue 3 + TypeScript + Tailwind CSS 工作台，含 8 个主要页面和登录/个人账号配套流程。`npm run build`（含类型检查）通过，Edge 下 16 项浏览器交互检查通过；8 页面 × 3 尺寸的 24 个布局组合无文档横向溢出、无捕获到的运行时异常。启动、用例映射与局限见 [原型入口](../02-ui-design/README.md)和[验证记录](../02-ui-design/VALIDATION.md)。

原型采用两公司、三系统、12 名虚构员工与内存演示，支持部门/总管理员视角、多项任命、角色申请与 OA 状态、来源关权和跨公司功能隔离。后台、真实认证/OA/HR、正式 API/数据库、正式业务逻辑与 TDD 模块实现均未开始；原型测试不作为这些能力的完成证据。当前状态为首版待用户评审，视觉展示名和细节不记为已确认业务需求。本轮未修改需求及技能文件，不生成需求 PNG/SVG。

此前需求修订的默认刷新成功，校验 11 个详细用例、12 个 JSON 示例、229 个图页（含汇总及副本）、227 个阅读文件与来源摘要、12 个 Python 文件语法；数据流上下层边界平衡通过。用例图布局/样式/连线保持一致，196 个既有 PNG/SVG 与当时上一提交逐字节一致，首次导入清单未修改。Git 差异格式检查通过。

图源未经 draw.io Desktop 人工导入验收；本轮未导出 V1.2 图片。需求整理阶段没有业务代码，未运行应用测试；当前原型验证另见下文。V1.1 历史验证见 [FUNCTION_PERMISSIONS.md](FUNCTION_PERMISSIONS.md)。

2026-10-15 目录迁移验证：400 个需求文件全部定位到新目录，11 个正式详细用例正文、114 个 drawio 文件、196 个 PNG/SVG 与迁移前逐字节一致；229 个图页结构及 617 个本地链接通过检查。迁移后的默认刷新成功，227 个阅读副本摘要一致，重复同步不改写相同文件；四个仓库技能共 9 个配套文件的摘要通过核对。历史导入清单未改写，迁移当时后续四阶段目录仅含 `.gitkeep`，尚未开始原型或业务实现。

## 历史下一步（2026-10-16，已由当前交接替代）

工具准备（2026-10-15）：`grill-me`、`grilling`、`frontend-design`、`test-driven-development` 均已作为项目副本纳入 `.agents/skills/`，随 Git 共享，保留原文件和配套元数据。`grill-me` 委托 `grilling`；后续正式逻辑仍使用先失败测试、再实现和重构。来源、版本和摘要见 [SKILLS.md](SKILLS.md)，不再依赖本机全局技能安装路径。正式业务逻辑未开始；原型自动化验证不作为正式逻辑的 TDD 证据。

目录整理（2026-10-15）：原需求资料与维护脚本整体迁入 `docs/01-requirements/`，需求内部分类保持原样。迁移时 `docs/02-ui-design/`～`05-detailed-design/` 仅用 `.gitkeep` 保留；现界面目录已含原型交付，其余三阶段继续保留目录。未创建额外工程目录。原始导入清单不改写，源项目不修改；详见 [LAYOUT.md](LAYOUT.md)。

1. 将 `docs/03-api/openapi.yaml` 1.1.0 人工导入 Apifox8934976默认模块，再完整回读核对。本地API评审有条件通过；原型补齐仍按D-029跟踪。本轮仅评审/修订API，数据库设计和后续跨阶段一致性评审尚未开展。
2. 清单“全部”、空白 I340、“系统管理后台”和本人限定来源差异仍需映射核对；不猜测授权，不阻止通用原型设计。
3. API 工具同步：2026-10-16 用户已安装 Apifox、配置本机 `APIFOX_ACCESS_TOKEN`，指定项目 ID `8934976`；本机配置与凭据不纳入 Git。15:36（Asia/Shanghai）及本次评审均实际刷新并读取成功，返回 OpenAPI 3.1.0、文档版本 1.0.0，`paths` 与 `webhooks` 为空。可调用工具仅为读文档、读引用、刷新，未提供写入/导入；远程项目未修改。本轮已确定 Git 权威 → 人工导入 Apifox → MCP 刷新/完整回读语义核对流程（D-030）；收尾刷新下载时间 2026-10-16T09:06:16.900Z，默认模块仍 0 接口。本次API契约评审收尾下载时间2026-10-16T15:12:56.488Z，根文档及三类组件引用仍为空。1.1.0尚未导入，不宣称同步完成或自动写入能力。
4. 正式逻辑实现时读取仓库内 `test-driven-development` 及测试编写参考文件，记录失败原因与通过证据；新环境先核验项目技能文件。
5. 按 D-028 的已确认技术栈推进；具体版本、JWT 生命周期与失效机制、测试框架、组件库、最终工程布局和远程 Git 在对应阶段细化。

## Apifox人工导入后回读（2026-10-16）

2026-10-17最新：用户重新导出后确认登录认证、Appointment三个字符串标识及11项Problem媒体类型均修复，120模型示例全部通过，67入站+1出站完整。剩余退出可选认证、multipart根对象additionalProperties:false及模块版本/元数据；仍不宣布完全一致。MCP刷新2026-10-16T16:12:04.652Z并定向读取成功，未再次全量读203引用；webhook仅MCP不暴露。详见APIFOX.md最新章节，Git契约不改，数据库未开始。

后续客户端OpenAPI3.1完整导出已核对：67入站操作加1出站webhook，截图68条得到解释；webhook已导入，缺失仅在MCP文档暴露层，替代下方初次定位。字段类型、登录/退出认证、Problem媒体类型、上传约束及模块版本差异在客户端导出中仍存在。导出结构通过，120模型示例中Appointment一项失败（119通过）；Git契约未改。实质差异待Apifox端修复再导出/回读，不能标记同步完成。证据、比较脚本及修复清单见[APIFOX.md](../03-api/APIFOX.md)。

用户确认已人工导入471892c对应契约；刷新时间2026-10-16T15:41:12.514Z，读取全部203个外部引用。56路径/67操作、operationId及状态码集合一致、129模型存在，但发现任命三个字符串标识变对象、登录/退出认证改变、Problem媒体类型改变、文件上传未知字段约束缺失、出站webhook缺失和版本元数据差异。完整回读已完成，语义核对未通过，不能记为同步完成；详细证据和后续操作见[APIFOX.md](../03-api/APIFOX.md)。需客户端导出OpenAPI3.1交叉定位导入/存储/MCP层差异；Git契约不改，不进入数据库设计。本地契约校验复跑PASS；未运行业务测试或写入远程。

## 历史下一步（2026-10-17，已由顶部当前状态替代）

1. 先对需求V1.3/单点澄清、API1.1.0和数据库草案开展正式跨阶段一致性评审；数据库阶段自查不是该评审。
2. 按数据库REVIEW的H-01～05落实草稿占用提示/协作处理、版本/BOM、文件审计保留/备份恢复、真实OA适配及初始化映射。当前不重新排查已记录Apifox MCP差异。
3. 后续另行进入docs/05-detailed-design，先做结构化模块/流程/接口/事务与依赖设计，再确定工程布局、迁移工具和测试框架；本轮没有进入该阶段。
4. 正式实现经授权后使用项目TDD技能及测试编写参考，先失败测试，使用隔离MySQL8.4验证DDL/触发器/并发/JPA/故障恢复；不连接业务环境测试草案。
