# 四人业务开发分工

D-095（2026-10-21）：D要求BE-26推送后自行继续下一项。BE-26及交付记录已推送并回查远程main/d79e28a，任务分支保留、D人工验收仍开放。已建立codex/be-30-import-commit并完成BE-30详细设计、实际端口衔接及14组TDD验收清单，见[BE-30准备](BE-30-IMPORT-COMMIT.md)。直接依赖BE-29已交付，A/BE-27仍PLANNED且主线缺员工全局状态与任命/会话失效编排，故只准备可独立部分，BE-30保持PLANNED、implementationStarted=false、testsRun=false，未改业务代码或运行本任务Java/SQL/HTTP。共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED；不跨成员实现BE-27、不代本人验收或部署。本次继续授权不扩展为BE-30合并推送，准备仅本地提交，下方BE-26未交付为历史。

D-094实际交付（2026-10-21）：D明确“先进行合并推送”。BE-26已接入远程main/6b9a02e的C工作流，来源857d4a9d20fa43578f70385d256b86ce02ad3fdc已no-ff合入远程main/b00ffc98277e754757b0e3d9bd0fe3d920db53b4，合并树等于集成验证来源。普通Git连接超时，改用官方GitHub Git API按本地对象原始SHA、force=false交付，main及codex/be-26-source-closure两个完整SHA回查一致，任务分支保留。集成完整139离线/21组＋350真实MySQL/HTTP/23组及静态校验通过，失败/错误/跳过0；本机python3商店占位导致首轮1项环境失败，临时测试入口修正后完整重跑通过，业务代码未因此修改。随机schema/账号/密钥目录均0，专属MySQL子进程17336正常关闭，原MySQL84仍Running。BE-26为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，D人工验收仍开放，其他成员状态保持；共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED。原D实施D-091与C重复，统一D-093，原f745648/b011a8a及C的D-091/092事实保留。用户另要求交付后自行继续下一项，后续按依赖核对BE-30，不代A实施BE-27、不部署。下方未授权/待交付为历史。

D-093集成结果（2026-10-21）：接入远程main/6b9a02e及C/BE-21/23/24/25/31后，完整clean verify/mysql-check通过，139项离线/21组＋350项真实MySQL/HTTP/23组，失败/错误/跳过0，日志backend/.tmp/be26-delivery-final.log。首轮1项演示脚本因本机python3商店占位退出9009，临时测试目录配置真实Python入口后完整重跑通过；未因此修改业务代码或永久环境变量。随机库/账号/测试密钥目录均0，专属MySQL子进程17336正常关闭，原MySQL84仍Running。BE-26保持IMPLEMENTED、ownerAccepted=false，主线交付由用户明确“先进行合并推送”授权，实际成功后登记；共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED。原D编号D-091与C重复统一为D-093，C的D-091/092及双方历史保持。

D-092（2026-10-21）：C明确数据库暂不处理、先提交合并既有工作。来源adab1861906dc7d973f796961249e1ef87563e29已无冲突no-ff合入远程main/311aa5d5cbc3d7bfbd064888b4172d48de0a0e99，合并树等于已验证来源；main与BE-21-31-申请审批与执行原子推送，远程完整SHA回查一致，任务分支保留。BE-21/23/24/25/31为IMPLEMENTED、mergedToMain=true、ownerAccepted=false；合并授权不代记本机人工验收。核对既有139离线＋316真实MySQL/HTTP报告及jar摘要一致，本轮代码未变，仅静态校验，不重新运行Java/SQL，不配置或连接本机数据库、不部署。C八项代码均已交付主线，本机环境与人工演示仍待后续；下方未交付为历史。

D-091（2026-10-21）：C在明确确认后要求继续全部剩余任务，于BE-21-31-申请审批与执行（main/7914788）依次设计/TDD实现BE-21/23/24/25/31。固定七字段RFC8785包、预览/无变化/占用、可靠OA待办/租约/三层回调防重、整份原子执行/失败记录/启动与周期恢复、显式demo适配器和短期服务JWT工具已实现；完整139离线＋316真实MySQL/HTTP及静态校验通过，失败/错误/跳过0。专属测试资源已清理关闭，用户既有库未操作。五项IMPLEMENTED、ownerAccepted=false、mergedToMain=false；既有BE-19/20/22负责人确认与主线交付保持，C八项实现均具备，剩本轮五项本人验收/主线交付。真实外部OA及服务器/全系统验收不在本轮，其他成员状态保持；共13项OWNER_ACCEPTED、10项IMPLEMENTED、5项IN_PROGRESS、7项PLANNED。证据见BE-21-31-APPROVAL-WORKFLOW.md，下方旧状态为历史。

D-093实际结果（2026-10-21）：BE-26两个公开操作与统一RequestInvalidator已完成设计/TDD，完整137离线/20组＋318真实MySQL/HTTP/22组及静态追踪通过，失败/错误/跳过0；含34项本任务真实验收。覆盖逻辑0、100来源、整份失效与全部claim/outbox、原范围实质失覆盖、独立角色/来源任职、隐藏混合申请及统计回放授权、并发与第N项/审计故障回滚。随机schema/账号/测试密钥目录均0，专属MySQL子进程27740正常关闭，原MySQL84仍Running。BE-26登记IMPLEMENTED、ownerAccepted=false、mergedToMain=false，只本地任务分支提交，既有各成员验收不代确认。共13项OWNER_ACCEPTED、6项IMPLEMENTED、5项IN_PROGRESS、11项PLANNED；BE-27/30未实施，不自动合并推送或部署。交接见[BE-26交接](BE-26-SOURCE-CLOSURE.md)，下方IN_PROGRESS/待实现为历史。

D-093（2026-10-21）：D明确开始执行BE-26，已接入main/7914788及C/BE-20、BE-19/22交付。D原准备编号D-086与C实施编号重复，统一为D-093，原f745648准备事实保留；C的D-086/088/089/090及验收/交付保持。首轮4项真实HTTP断言失败、0错误，目标预览路由404。BE-26进入IN_PROGRESS，复用WorkflowStateWriter/claim与既有SourceCloser、预览、guard、幂等和审计。共13项OWNER_ACCEPTED、5项IMPLEMENTED、6项IN_PROGRESS、11项PLANNED；不代D人工验收、不合并推送或开BE-27/30，下方未交付依赖/仅准备为历史。

D-093（2026-10-20）：D要求BE-29推送完成后自行继续下一项。BE-29及交付记录已推送并回查main/86089d2，任务分支保留、D人工验收仍开放。已从该主线建立codex/be-26-source-closure，完成BE-26设计、端口衔接清单和11组TDD验收场景，见[BE-26设计与准备](BE-26-SOURCE-CLOSURE.md)。直接依赖BE-05/16/17已交付，但C/BE-20仍PLANNED且主线缺申请/claim写端口，故只推进可独立准备，BE-26保持PLANNED、implementationStarted=false，未改业务代码或运行本任务Java/SQL/HTTP。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED；BE-30仍须A/BE-27，不跨成员实现依赖。本次不扩展BE-29特定合并推送授权，准备提交仅在本地分支；下方不自动开新任务是此前边界。

D-090（2026-10-21）：C明确“确认，然后继续完成剩下的任务”，接受BE-19/22既有137离线＋284真实MySQL/HTTP证据；人工演示未进行。来源cc88885f2c97df838720fc2f7f0e524d72e56351已no-ff合入main/cf27f7f910bf5e904c89542f56e3f56fddd884d9，合并树等于验证来源，main及任务分支原子推送且远程完整SHA回查一致。两项OWNER_ACCEPTED、mergedToMain=true。后续按BE-21→23→24→25→31连续实施，依赖沿同一任务分支继承，各项实现、本人确认、主线交付分开记录；不代其他成员验收、不部署。共13项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、12项PLANNED。下方待确认/待交付为历史。

D-089（2026-10-21）：C要求继续其他任务，BE-19/22在BE-19-22-审计与申请查询（main/09abaa2）先设计及真实HTTP/MySQL RED，再实现审计列表/详情和申请列表/详情/时间线。当前单项完整范围先SQL过滤再统计/分页，关联主体与缺损目标投影不能绕过授权，审计仅投影历史公开字段。实现与实际验证见[BE-19/22](BE-19-22-AUDIT-REQUEST-QUERIES.md)。两项为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，不代记人工验收或延用BE-20特定交付授权。其他C写链路仍PLANNED，已交付BE-20状态保持；共11项OWNER_ACCEPTED、7项IMPLEMENTED、5项IN_PROGRESS、12项PLANNED。下方旧状态/数量为历史。

D-088实际交付（2026-10-21）：C在确认BE-20及逐项合并方案后明确“可以”，接受既有137离线＋265真实MySQL/HTTP验证并授权交付。来源310313a07e0321f58a5b1b03d3d59231fe2a17a6已无冲突no-ff合入远程main/a9b214cce38d4396120b3902ce19f2b7440bf4f1，合并树等于验证来源；main与BE-20-角色与变更草稿已原子推送并完整SHA回查一致，任务分支保留。BE-20为OWNER_ACCEPTED、mergedToMain=true，本机人工演示未进行；本轮仅复核原报告/jar摘要和静态追踪，不重跑代码未变的Java/SQL。D/BE-26技术依赖已交付，其他C任务仍开放，不自动开工或部署。共11项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、14项PLANNED。下方待验收/待交付为历史，见[BE-20](BE-20-CHANGE-DRAFTS.md)。

D-086（2026-10-20）：本轮用户明确负责C并要求先列计划再执行。BE-20在BE-20-角色与变更草稿（main/86089d2）先设计及真实HTTP/MySQL RED，再实现新角色/三类草稿/完整目标/原子占用/编辑删除/当前授权幂等重放，以及供D调用的最小WorkflowStateWriter。验证及交接见[BE-20](BE-20-CHANGE-DRAFTS.md)。BE-20为IMPLEMENTED、ownerAccepted=false、mergedToMain=false；此前A/D认领和验收事实保留。BE-19及BE-21～25/31仍PLANNED，BE-26仍须本任务主线交付；不代记验收，不合并推送或自动开展其他任务。共10项OWNER_ACCEPTED、6项IMPLEMENTED、5项IN_PROGRESS、14项PLANNED，下方旧状态/数量为历史。

D-085实际交付（2026-10-20）：成员D明确“合并推送吧”，BE-29接入A已交付BE-09后，来源db3419687f8dc43b1959564ddc19b0e7d32826ed已无冲突no-ff合入远程main/6506cda54c4898e736a2e730ba001294f8de4c2c，合并树等于已完整验证来源，任务分支codex/be-29-import-precheck保留。普通Git连接连续失败，改用官方GitHub Git API按本地对象原始SHA、force=false推送main和任务分支，远程两个完整SHA均回查一致。完整137离线＋240真实MySQL/HTTP及静态校验通过，专属资源清理关闭，原MySQL84仍Running；BE-29为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，既有人工验收保持。D原实施编号D-081与A冲突已统一为D-084；A的D-081/082/083及PR#3交付保留。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED；不强推或部署，下方未授权/待主线为历史。

D-084集成结果（2026-10-20）：接入远程main/06a6b5c（A已验收并经PR#3交付的BE-09）后，完整clean verify/mysql-check通过，137项离线/20组＋240项真实MySQL/HTTP/19组，失败/错误/跳过0，日志backend/.tmp/be29-main-integration-verify.log。集成未改BE-29业务代码，静态追踪通过；BE-29保持IMPLEMENTED待D人工验收，本次主线交付由用户“合并推送吧”明确授权，实际SHA成功后补。

D-084编号协调（2026-10-20）：BE-29原本地实施记录D-081与远程A/BE-09重复，现统一为D-084；原实现80abde1及真实RED/GREEN事实保留，A的D-081/082/083、验收与PR#3交付均保持。已接入main/06a6b5c，业务代码无冲突；BE-29仍IMPLEMENTED、ownerAccepted=false，待集成回归与本次授权的主线交付。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED。

D-084（2026-10-20）：D要求下一项，BE-29在codex/be-29-import-precheck（main/072585e）按设计/TDD实现五个预检/报告操作，冻结候选ID-code、规范差异、映射、动作顺序及依赖，不改正式人员/授权数据。完整137离线＋228真实MySQL/HTTP通过，失败/错误/跳过0；随机库/账号/临时密钥文件目录0，专属实例正常关闭，原MySQL84仍Running。BE-29为IMPLEMENTED、ownerAccepted=false、mergedToMain=false；AT-25/26/27/29仅补预检局部证据，BE-26仍等C/BE-20，BE-30仍等A/BE-27及本任务主线交付。共9项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、16项PLANNED；既有验收状态保持，不合并推送或自动开新任务。下方旧状态/数量为历史，见[BE-29](BE-29-IMPORT-PRECHECK.md)。

D-083实际交付（2026-10-20）：BE-09已通过[PR #3](https://github.com/Noah-hope/RBAC-admin/pull/3)合入远程main，来源0fee462503274fbed1ab03826c8d0723c9825c19，合并5f42a2cff11d68c49a4926373eeee97e3f5e8889。已回查closed/merged=true、远程完整SHA、双亲及合并树等于验证来源，本地main已同步、任务分支保留；OWNER_ACCEPTED、mergedToMain=true、remotePrNumber=3。接入BE-16后完整112普通＋227真实MySQL/HTTP及静态校验通过，专属资源清理关闭，原MySQL84仍Running；远程检查条目0，不记为CI通过。本轮交付不部署或开新任务，D各任务验收不代确认；下方待授权/待PR/待主线为历史。

D-082（2026-10-20）：A明确“测试通过即可”，按已有112离线＋205真实MySQL/HTTP证据确认BE-09/41931f4，登记OWNER_ACCEPTED、待主线交付；未做本机演示，不虚构人工操作通过。远程最新072585e已交付D/BE-16，但本任务分支尚未整合该增量，BE-26及BE-33仍PLANNED。下一步建议交付BE-09；A余下BE-27等D/BE-26、BE-34等B/BE-33，不自动开工或推送。原本地BE-09编号D-079与远程D/BE-16冲突，统一为D-081，保留双方事实；下方待A确认/旧数量为历史。

D-081（2026-10-20）：A按已交付BE-05/08/17开始BE-09，创建预览及范围/系统集合/任命的原子保存已实现，使用JPA写仓储、BE-17固定预览与公共guard/幂等/审计。完整112离线＋205真实MySQL/HTTP及静态校验通过；当前IN_PROGRESS待A本人确认，未交付main；验证和本机步骤见[BE-09](BE-09-SCOPE-CREATION.md)。共9项OWNER_ACCEPTED、3项IMPLEMENTED、6项IN_PROGRESS、17项PLANNED，其他成员人工验收状态不变；AT-08/31/37只补本任务部分证据，不代表BE-27/33完成。下方旧状态/数量为历史。

D-080（2026-10-20）：D明确“先完成合并推送”，BE-16来源dba2eaa已无冲突保留历史合入远程main/7ab021a，合并树等于已验证来源，任务分支推送并保留，远程完整SHA回查一致。核对112离线＋215真实MySQL/HTTP报告与jar摘要一致，本轮静态复核通过，无Java/SQL重跑或部署。BE-16为IMPLEMENTED、mergedToMain=true、ownerAccepted=false；BE-14/28人工验收继续开放。当前9项OWNER_ACCEPTED、4项IMPLEMENTED、5项IN_PROGRESS、17项PLANNED；下方未授权/待主线为历史，不自动开新任务。见[BE-16](BE-16-GRANT-MUTATIONS.md)。

D-079（2026-10-20）：D要求开始下一项，BE-16在codex/be-16-grant-mutations（基线main/a860dc8）按设计/TDD实现BaselineWriter、GrantApplier、SourceCloser及AffectedFacts。完整112离线＋215真实MySQL/HTTP通过，失败/错误/跳过0；专属实例正常关闭，随机库/账号/测试密钥目录0，用户原MySQL84仍Running。BE-16为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，未合并推送；BE-14/28仍已交付待本人确认。AT-14/15/22/41只补底层局部证据，不代表C执行器、真实OA或BE-26生命周期完成。当前9项OWNER_ACCEPTED、4项IMPLEMENTED、5项IN_PROGRESS、17项PLANNED，下方旧数量/边界为历史。见[BE-16](BE-16-GRANT-MUTATIONS.md)。

D-078（2026-10-20）：D明确要求先合并推送，BE-14来源367cdae已保留历史合入远程main/c7435d9，分支已推送并保留，远程完整SHA回查一致，合并树等于验证来源。沿用完整112离线＋193真实MySQL/HTTP证据，本轮静态检查通过，未重复执行Java/SQL。BE-14仍为IMPLEMENTED、ownerAccepted=false、mergedToMain=true，BE-28仍已交付待本人确认；下方未授权/待主线为历史。AT-13补本任务证据，AT-14/15仅读取局部验证，BE-16/26执行竞争不在本任务。当前共9项OWNER_ACCEPTED、3项IMPLEMENTED、5项IN_PROGRESS、18项PLANNED，下方旧状态/数量为历史。见[BE-14](BE-14-GRANT-BASELINES.md)。

D-076（2026-10-20）：BE-28 已接入最新 BE-17/12 后完整110离线＋178真实MySQL/HTTP通过，并由233f633保留历史合入远程main/4b384fb，远程SHA回查一致，分支保留。Git连接失败后使用官方API按本地SHA原样交付、force=false。BE-28仍待本人确认，mergedToMain=true；交付不代记人工验收。当前共9项OWNER_ACCEPTED、2项IMPLEMENTED、5项IN_PROGRESS、19项PLANNED。D原BE-28实施编号因与B冲突改为D-075，原事实保留；下方旧数量/未授权为历史。见 [BE-28](BE-28-IMPORT-FILES.md)。

D-074（2026-10-20）B 状态：BE-17/12 已由 1fb95ad 无冲突合入并交付远程 main/3103b35，任务分支保留，完整 SHA 回查一致，合并树等于已验证源。完整 93 普通＋169 真实 MySQL/HTTP 通过，本轮合并后 93 普通测试再次通过。任务待本人确认，不代记 OWNER_ACCEPTED。BE-11/13/15 原交付保留；共 9 项 OWNER_ACCEPTED、1 项 IMPLEMENTED、5 项 IN_PROGRESS、20 项 PLANNED。AT-09/10/11/12/31/32 仅局部验证，不代表全部消费者/OA/BE-33 完成。BE-18 仍缺 BE-14，BE-33/35 仍开放。详见 [BE-17/12 实施及交付](BE-17-12-PREVIEW-CATALOG.md)，下方更早数量/待交付说明为历史。

## 分工汇总

| 成员 | 负责方向 | 对应任务 |
| --- | --- | --- |
| A | **身份与管理范围、运行交付**：登录、账号、任命、范围、员工状态联动、初始化与部署 | BE-06～09、BE-27、BE-32、BE-34 |
| B | **系统、角色与功能权限**：系统目录、角色配置、影响预览、权限查询、集成验收 | BE-11～13、BE-15、BE-17～18、BE-33、BE-35 |
| C | **申请、审批与审计**：草稿、提交申请、OA投递与回调、执行恢复、审计查询 | BE-19～25、BE-31 |
| D | **组织、人员与授权来源**：组织任职、授权基线、授予撤销、来源关权、文件导入 | BE-10、BE-14、BE-16、BE-26、BE-28～30 |

按D-055登记分工；D-056用户明确本人负责A，其他成员姓名及GitHub账号映射待补。每项BE只有一名负责人，由该负责人自行确认完成，不另设评审人。

**当前：A实施BE-08（D-067），四查询与统一上下文/覆盖策略已获A确认（D-068）并由D-069/PR#2交付main；D的BE-10已由D-065交付main，D人工验收开放。** BE-07按D-058在包含确认祖先的任务分支实现，并已获D-059本人确认；其他成员依赖仍等已由D-060交付main。A已复用BE-10实施BE-08，后续BE-09仍须B/BE-17；B可推进BE-11，D可按依赖推进BE-28，C可先细化接口及测试场景；各任务就绪即可推进，不必等所有人完成同一批。

BE-01～05公共基座已由用户完成并确认；本次分工覆盖BE-06～35共30项，BE-06为OWNER_ACCEPTED（D-057，已由D-060交付main）、BE-07为OWNER_ACCEPTED（D-059，已由D-060交付main）、BE-32为OWNER_ACCEPTED（D-062，已由D-063/PR#1交付main），BE-10为IMPLEMENTED（六个查询接口及内部读写端口已实现，已由D-065交付main，待负责人验收），BE-08为OWNER_ACCEPTED（D-068，已由D-069/PR#2交付main），其余25项PLANNED。下文“前置”列列出完整直接依赖；开发先后也可对照[README流程图](../../README.md#业务开发顺序)。

每个业务功能由负责人贯穿实现：需要HTTP时负责Controller、请求/响应DTO、Service、所属领域的持久化或查询投影，以及测试和文档；内部能力无需新建Controller，也不要求每个BE重复创建Repository。详细UC/API/表/事务/验收映射见[任务卡](TASKS.md)和[追踪表](TRACEABILITY.md)。

## A：身份与管理范围、运行交付

主要维护`identity`、`scope`，以及本人任务对应的`lifecycle`、查询投影和HTTP适配。身份是后台账号、任命及三类JWT；管理范围不是业务数据权限。

| 任务 | 完整前置 | 具体交付 |
| --- | --- | --- |
| BE-06 三类JWT与后台登录、退出、本人改密 | BE-04 | RS256三类入口、当前资格、会话持久化、受限改密token与限速，普通员工拒绝。 |
| BE-07 后台账号管理与密码重置 | BE-05、BE-06 | 账号查询/创建/停用启用/重置，不改变员工业务授权，临时密码响应遵循契约且不缓存秘密。 |
| BE-08 当前单项任命、范围读模型与覆盖策略 | BE-06、BE-10 | 三上下文头、当前单项覆盖、列表/详情边界、任命版本与范围版本独立。 |
| BE-09 创建管理范围及任命、创建预览 | BE-05、BE-08、BE-17 | 总管理员创建完整范围/名单，当前部门任职及系统合法，失败全回滚。 |
| BE-27 员工状态及管理范围替换的跨域失效 | BE-07、BE-08、BE-09、BE-26 | 来源/关系/任命/会话/原范围申请同事务持久失效，恢复不复权，范围扩回不复活、防总管理员自锁。 |
| BE-32 部署产物、配置与管理员初始化 | BE-01、BE-02、BE-03、BE-06 | 可执行jar、独立迁移步骤/运行账号、受控bootstrap命令、持久目录、HTTPS代理/服务管理与回退说明。 |
| BE-34 实际服务器部署、重启与演示验收 | BE-32、BE-33 | 真实目标服务器的版本/提交/校验和、迁移/健康/HTTP演示/重启持久化/恢复证据与未通过项。 |

**推进与交接：**

1. 先做BE-06，提供后台/业务系统/OA三类凭据的校验与后台会话能力。BE-07、BE-32在BE-06之后可按各自依赖推进；D的BE-10已交付main，BE-08已获A确认（D-068）并由D-069/PR#2交付main，BE-08已交付，BE-09须等B的BE-17。BE-27等待账号、范围及D的BE-26，BE-34最后等待BE-32和B的BE-33。
2. 交给B/C/D：认证后的当前主体、受控服务身份及当前任命/范围资格读取接口。调用者仍在基座事务边界中复核当前资格，不能只相信JWT中的旧范围。接口签名和失败语义先写入设计，再由各方分别实现和测试。
3. 需要D提供组织/任职读取、来源关闭与统一失效编排，C提供申请状态写端口，需要B提供影响快照。BE-27由A完成员工停用/恢复、范围替换的跨域入口，调用已有内部能力；与D共同维护lifecycle时按功能分文件，协调会话/任命/来源/申请的原子失效。
4. BE-32交付部署配置与管理员初始化，BE-34负责实际服务器部署、重启和演示证据；本机启动成功不能代替服务器验收。公共HTTP装配的业务接线由A协调，各控制器仍归各自任务负责人。

## B：系统、角色与功能权限、集成验收

主要维护`catalog`、`role`，以及系统/角色/有效权限/影响预览对应的查询投影与HTTP适配。负责功能权限规则，不添加SELF/ALL、业务行过滤或数据范围字段。

| 任务 | 完整前置 | 具体交付 |
| --- | --- | --- |
| BE-11 系统登记及查询 | BE-03、BE-04、BE-05、BE-06 | 稳定系统编码和唯一SYSTEM_ACCESS，登记同事务，分页/详情权限与契约一致。 |
| BE-12 目录原子编辑、系统启停及影响预览 | BE-11、BE-17 | 完整最终树/本地引用/绑定/稳定排序/无环，合法即时FK顺序、整批/no-change及预览依赖。 |
| BE-13 角色元数据和当前配置读取 | BE-03、BE-05、BE-06、BE-11 | 固定归属、共享角色维护边界、元数据版本与配置版本，当前配置与待审批状态分开显示。 |
| BE-15 有效角色配置应用器 | BE-04、BE-13 | 仅执行器可用的配置差集校验/替换/版本和历史写入，保留停用项合法，全部非空。 |
| BE-17 安全影响预览与分页、依赖集合快照 | BE-04、BE-08、BE-10 | 各业务预览共用纯影响计算及数据投影，5分钟、当前上下文重查、集合新增检测，混合申请安全计数。 |
| BE-18 业务系统有效权限和单项检查 | BE-06、BE-10、BE-14 | 主库新快照、完整有效来源并集、进入资格、独立菜单/操作、未知编码与存储错误区分。 |
| BE-33 完整业务链路及并发/失败集成验收 | BE-12、BE-18、BE-19、BE-22、BE-25、BE-27、BE-30、BE-31 | 执行器/停用/来源关闭/导入双连接屏障与故障注入，逐操作契约覆盖，补跨模块遗漏。 |
| BE-35 交接、任务证据与发布收尾 | BE-34 | README/共享记忆、任务与验收追踪、发布说明及已知限制，所有未完成项保留开放。 |

**推进与交接：**

1. 最早从BE-11开始，须等A的BE-06。BE-13在系统登记后推进，BE-15在角色读取后推进；BE-17所需A的BE-08和D的BE-10已交付main，可按依赖推进，BE-12再依赖BE-17。BE-18还要等D的BE-14。以上分支按任务前置推进，无需等待OA链路全部完成。
2. 交给C：BE-15的RoleConfigurationApplier，供BE-25在同一WriteContext中应用已批准角色配置；该内部应用器不新增公开业务接口，不自行提交独立事务。C负责执行编排，B负责目录和角色配置不变量。
3. 交给A/C/D：BE-17的影响计算、分页和依赖集合快照；明确输入、当前授权与快照过期/陈旧失败语义。BE-17只读表和投影，不反向依赖BE-12的目录命令，避免依赖环。BE-18使用D提供的来源/关系事实计算当前有效权限。
4. BE-33由B负责跨模块整链路、并发和失败集成，发现缺陷交对应模块负责人修复；其他成员仍各自完成任务测试。BE-35在A的BE-34通过后汇总交接与发布证据，不能提前关闭尚未通过的业务验收。

## C：申请、OA审批与执行、审计

主要维护`change`、`approval`、`execution`和审计读取投影，负责草稿→固定申请→OA投递/结果→自动执行的流程。OA批准与实际执行是不同状态，模拟适配不能宣称真实OA已接通。

| 任务 | 完整前置 | 具体交付 |
| --- | --- | --- |
| BE-19 审计列表、详情与敏感访问 | BE-04、BE-06、BE-08 | 原事件append-only，混合不可安全分割事件仅总管理员，列表total和详情当前授权一致。 |
| BE-20 角色创建与配置/授予/撤销草稿、占用 | BE-05、BE-08、BE-13、BE-14 | 新角色+初始草稿/claim原子，草稿完整投影与并发版本；删除安全快照、幂等授权依据及占用释放。 |
| BE-21 预览确认、固定申请与OA待办 | BE-17、BE-20 | 先当前授权再无变化/仍需版本/预览依赖，JCS固定摘要包/目标投影/原资格/outbox同事务，提交不授权。 |
| BE-22 申请列表、详情及安全时间线 | BE-20 | 整份当前单项范围覆盖，三轴状态/历史与安全失败码，驳回复制仅新草稿。 |
| BE-23 OA可靠投递、关联及人工投递重试 | BE-21 | 租约领取/发送前复核/事务外适配器/关联回写，超时同requestId，不重投已失效申请。 |
| BE-24 OA最终结果接收与三层去重 | BE-21、BE-23、BE-06 | 可信服务身份和可靠关联，事件/最终结果冲突检测、可靠接收独立提交，批准不直接变更权限。 |
| BE-25 自动执行、整份失效与失败恢复 | BE-15、BE-16、BE-24 | 完整固定清单复核→全部业务/audit/success/状态同事务，失败后另事务复核记录，启动/定时扫描恢复。 |
| BE-31 演示OA适配器及受控服务身份工具 | BE-06、BE-23、BE-24 | demo-only受理端口和可信服务JWT离线工具；固定申请回执，批准/驳回仍调用原回调，不增加公开审批入口。 |

**推进与交接：**

1. BE-19须等A的BE-06/08；BE-20须等A的BE-08、B的BE-13和D的BE-14。BE-22可在BE-20后推进；BE-21还须B的BE-17，之后推进BE-23→BE-24。BE-25须再等B的BE-15和D的BE-16；BE-31在BE-23/24后开展。
2. BE-20/21负责占用、冻结内容和授权事实，提交时同事务产生可靠投递事实。固定申请的RFC8785实现和向量验证在BE-21完成，不能将普通JSON序列化当成规范化实现。
3. BE-23/24负责事务外可靠投递、关联及OA结果去重；复用A的OA服务身份认证。BE-25负责批准后全清单预检、执行及恢复，同一WriteContext中调用B的角色配置应用器和D的授予/撤销端口，保证业务、审计、成功凭据一起提交，禁止复制其他领域的更新逻辑。
4. 交给D/A：申请状态读取、合法状态转换及占用释放端口；D的BE-26负责统一失效能力的交付与关权编排，申请内部状态约束由C维护。grant基础模块只返回受影响事实，由上层编排调用申请端口，避免领域命令相互回调。审计列表/详情由C实现，所有成员的业务审计仍通过基座AuditAppender在本事务追加。

## D：组织、人员与授权来源、导入

主要维护`people`、`grant`和来源关闭/导入对应的`lifecycle`，负责组织、员工、任职、授权来源及关系基线。岗位/任职变化本身不自动授予业务角色。

| 任务 | 完整前置 | 具体交付 |
| --- | --- | --- |
| BE-10 组织、员工与任职读取及基础变更端口 | BE-03、BE-04 | 稳定员工/源身份/公司部门岗位映射及任职读取，EmploymentWriter仅供受控导入调用；无新增组织写API。 |
| BE-14 来源和关系基线、授权历史及当前资格 | BE-04、BE-10、BE-11 | 缺失逻辑0基线、全部适用任职、同来源去重/异源并存、准确历史grant版本。 |
| BE-16 授予/撤销及关闭来源底层原子操作 | BE-04、BE-14 | WriteContext内物化源/永久baseline、ACTIVE槽、新grantID、结束与证据，返回受影响事实供编排器失效申请。 |
| BE-26 直接来源关闭与统一申请失效编排 | BE-05、BE-16、BE-20、BE-17 | 统一RequestInvalidator，关权/关系结束触发整份已提交申请失效，安全计数/可见审计及幂等原子，DRAFT不伪终态。 |
| BE-28 CSV/XLSX文件上传、元数据及解析 | BE-03、BE-05、BE-06 | 正式文件边界、受控不可变存储/摘要/工作表/宏公式限制，不沿用原型上传限制。 |
| BE-29 冻结导入预检、差异与问题报告 | BE-10、BE-17、BE-28 | 冻结完整输入/映射/规范前后值/动作顺序/候选ID-code/依赖/唯一性断言，无正式数据副作用。 |
| BE-30 导入整批提交、历史与失败恢复 | BE-27、BE-29 | 1000源行一个事务，嵌入同一生命周期操作；STALE/FAILED后另事务防覆盖成功；同批防重及新批无变化。 |

**推进与交接：**

1. 先做BE-10，为A提供组织/任职及资格所需事实。BE-14再等B的BE-11，BE-16在BE-14后推进。BE-28须等A的BE-06，可与来源能力并行；BE-29还须B的BE-17。BE-26等待BE-16、C的BE-20及BE-17；BE-30再等A的BE-27与BE-29。
2. 交给A/B/C：组织、员工、任职读取，来源/关系基线与当前资格，以及BE-16的GrantApplier/SourceCloser等受控写端口。内部原子操作接收现有WriteContext，不新增公开Controller、不自行提交；同源去重、异源并存、周期推进和不复活旧授权由D保证。
3. BE-26的统一失效能力与直接关来源编排由D负责交付，内部状态转换/占用释放复用C提供的申请端口；涉及change文件与C协调，向A的BE-27提供可复用能力。A负责员工状态/范围替换入口，D负责来源规则和导入，不能各写一套来源关闭逻辑。
4. BE-28只处理受限文件及元数据；BE-29冻结导入计划、差异和问题；BE-30调用A/C/D已提供的能力完成整批正式变更及失效。第N项或审计失败时全批回滚，失败记录按设计另存；结束任职再新增不代表恢复旧授权。

## 四人共同遵守

1. 各自在最新共同主线建立`codex/be-xx-主题`分支。先按[本机配置指南](FOUNDATION-USAGE.md)完成环境；实名/GitHub账号对应、仓库读写权限及成员本机验证结果仍需如实登记。业务代号已分工不代表这些操作已完成。
2. M11查询投影、M12 Controller/DTO按功能分文件，由对应BE负责人维护；不共写一个巨大QueryService或统一业务Controller。M10按本表A/D的任务拆分。调用其他领域公开内部端口，不随意改其实体和Repository。
3. 修改公共core、pom.xml、全局配置/Problem/认证装配时先与基座维护者（用户）及受影响成员协调；迁移先协调版本号，每个版本只由一人提交。复用既有43表设计，结构变化同步权威设计；协调不增加独立评审人或额外审批门槛。
4. 接口交接写清输入/输出、权限与事务前提、失败语义、验收场景和依赖提交。未就绪时可以准备设计和测试场景，不能把Mock端口记成真实依赖已完成。
5. 每项先设计，再按项目TDD保留RED/GREEN，完成所属行为/权限/异常/事务与恢复验证，同步共享记忆和文档。负责人自行确认完成；合并状态、部署状态分别记录，未运行不勾选。真实数据库测试沿用专属临时实例，不向共享开发库或测试库写夹具。
6. 本文由[design_plan.py](design_plan.py)从任务数据与分工规则生成；调整任务归属先更新生成器，再运行[validate_design.py](validate_design.py)。BE编号是本地任务编号，不是已经创建的GitHub Issue。
