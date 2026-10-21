# 后端验收与TDD交接

D-101实际结果（2026-10-21）：BE-30冻结导入整批提交、历史与失败恢复已实现。完整139普通/21组＋425真实MySQL/HTTP/27组、18辅助工具及静态追踪通过，失败/错误/跳过0，含本任务19项真实检查。覆盖固定候选身份、1000行及第500条/最后审计故障全批回滚、同批长期成功回放、独立来源/角色部门资格、Q-01离岗再任职、员工停用恢复不复权、整份申请claim/outbox失效、部门迁移即时FK及迟到失败记录不覆盖成功。随机库/账号/密钥文件目录0，专属MySQL子进程5840正常关闭，原MySQL84仍Running。BE-30为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，仅本地任务分支提交；D七项代码均具备，既有人工验收不代确认。共13项OWNER_ACCEPTED、14项IMPLEMENTED、5项IN_PROGRESS、3项PLANNED；未合并推送或部署，不接管BE-33/34/35。接入基线main/126fbc9；D原准备D-095与B重复统一D-101，36c6cb5及双方事实保持。详见[BE-30交接](BE-30-IMPORT-COMMIT.md)，下方仅准备/IN_PROGRESS为历史。

D-101（2026-10-21）：D明确“开始执行最后一项任务”，已fetch核对并接入远程main/126fbc9，A/BE-27已通过PR #4交付92d126f，直接依赖齐备。D原BE-30准备D-095与B重复，统一为D-101，原36c6cb5及B的D-095/096/097/098、A的D-099/100事实保持。已按现有设计/TDD进入IN_PROGRESS；真实首轮3项断言失败、0错误，commit路由404；核心3项GREEN已通过，后续员工/生命周期和故障场景仍在实现。复用BE-29固定计划、EmploymentWriter与A/EmployeeStateWriter、BE-16/26及guard/幂等/审计，同批整体成功或回滚。共13项OWNER_ACCEPTED、13项IMPLEMENTED、6项IN_PROGRESS、3项PLANNED；不代本人验收、不合并推送或部署。下方仅准备/缺BE-27为历史。

D-100实际交付（2026-10-21）：BE-27已通过PR #4（https://github.com/Noah-hope/RBAC-admin/pull/4）合入远程main。验证实现72daaac009a9778a7446373e2094d2196a836514，PR来源682a052e5b38cb5be09d10f4856e93860b5091af，合并92d126f5465c8ecc5c2b4f293402fb21b0f1df7a；已回查closed/merged=true、远程完整SHA、双亲及合并树等于验证来源。本地主线已同步、任务分支保留。沿用139普通＋406真实MySQL/HTTP及18辅助证据，交付静态检查通过，不重跑Java/SQL；远程检查条目0，不记为CI通过。BE-27为IMPLEMENTED、mergedToMain=true、remotePrNumber=4、ownerAccepted=false；负责人验收仍单独待确认。D可更新main接入BE-30，BE-33仍待BE-30、BE-34仍待完整集成，不部署或自动开新任务；下方待交付为历史。

D-099（2026-10-21）：A要求同步远程并开始BE-27，已在main/8ad17e4上独立实现员工状态预览/保存及范围替换预览/保存，复用BE-16/26的来源与整份申请失效端口。完整139普通＋406真实MySQL/HTTP（含本任务31项）、18辅助工具及静态校验通过，失败/错误/跳过0；专属资源清理关闭，原MySQL84仍Running。详细证据见[BE-27交接](BE-27-CROSS-DOMAIN-LIFECYCLE.md)。实现、本人确认和主线交付分别记录，BE-27为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，不代B/C/D验收，不推送/合并/部署或实施BE-30。下方BE-27尚未实现为历史。

D-098实际主线交付（2026-10-21）：B 明确要求“把你做的东西弄好，然后做好测试后就合并”。来源 ba2178c60b1ec78b777952845b7bb71e5c9161ce 已无冲突 no-ff 合入并推送远程 main/43aeab78fbbf01acd568ff49c8893349e849d958，双亲和合并树等于已验证来源，远程完整 SHA 已回查。合并后完整139普通＋375真实MySQL/HTTP、18辅助工具及静态追踪通过，失败/错误/跳过0；隔离库/账号0/0、密钥清理、专属MySQL关闭。常规Git网络卡住后停止本轮卡住进程，改用官方GitHub Git API按原始SHA、force=false交付。BE-18为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，B六项业务代码均已交付主线；BE-33/35仅已完成的集成/交接准备随同交付，正式任务保持PLANNED，仍缺A/BE-27、D/BE-30及A/BE-34。保留任务分支、不代记本人验收、不部署；下方不合并/未交付为历史，本条后续仅补交付证据文字。

B 实际分支交付（2026-10-21/D-097）：已推送 codex/be-33-35-integration-handoff，远程完整 SHA 回查为 a5d5c3557b6da7cb8f52ea42c30ba03b5fcb18a3，包含 BE-18、集成修复/测试和交接证据；远程 main 仍 d79e28a624fd4b9bf3862063c44274d3db602248，未合并或推送 main。完整139＋375及18辅助检查通过，专属资源清理关闭；本条后续仅补交付文字。推送分支不等于主线交付、本人验收或 BE-33/35 完成；A/BE-27、D/BE-30、A/BE-34 及完整验收仍开放。下方准备推送/未授权为历史。

B 最新验证（2026-10-21/D-097）：已接入 main/d79e28a 的 BE-26，完整 clean verify/mysql-check 通过：139 普通＋375 真实 MySQL/HTTP，失败/错误/跳过 0；18 项辅助工具测试与设计静态校验通过。新增真实来源关闭→下次查询拒绝、迟到批准不复权及新周期重新申请链路，集成组 8 项通过。专属随机库/账号 0/0、临时密钥清理、13461 实例正常关闭；既有数据库未操作。BE-18 与 BE-33/35 当前准备提交至独立 B 分支，远程 main 不合并；BE-33 尚缺 A/BE-27、D/BE-30，BE-35 尚缺 A/BE-34，正式状态及其他成员验收保持。下方缺 BE-26、旧测试数与未授权推送是历史。

B 分支交付准备（2026-10-21）：按用户“那你就弄完提交”，已接入 main/d79e28a 的 BE-26，正在完成新增来源关闭/迟到批准与 BE-18 查询的真实集成回归，随后提交推送独立分支，不合并远程 main。B 原 D-093/094 为保留 D 的已交付编号统一为 D-095/096，双方历史及验收事实保持。BE-33 仍缺 A/BE-27、D/BE-30，BE-35 仍缺 A/BE-34；BE-26 不再列作未实现依赖，不接管其他成员任务或部署。下方旧基线、缺 BE-26 和未授权推送为历史。

D-101（2026-10-21）：D要求BE-26推送后自行继续下一项。BE-26及交付记录已推送并回查远程main/d79e28a，任务分支保留、D人工验收仍开放。已建立codex/be-30-import-commit并完成BE-30详细设计、实际端口衔接及14组TDD验收清单，见[BE-30准备](BE-30-IMPORT-COMMIT.md)。直接依赖BE-29已交付，A/BE-27仍PLANNED且主线缺员工全局状态与任命/会话失效编排，故只准备可独立部分，BE-30保持PLANNED、implementationStarted=false、testsRun=false，未改业务代码或运行本任务Java/SQL/HTTP。共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED；不跨成员实现BE-27、不代本人验收或部署。本次继续授权不扩展为BE-30合并推送，准备仅本地提交，下方BE-26未交付为历史。

D-094实际交付（2026-10-21）：D明确“先进行合并推送”。BE-26已接入远程main/6b9a02e的C工作流，来源857d4a9d20fa43578f70385d256b86ce02ad3fdc已no-ff合入远程main/b00ffc98277e754757b0e3d9bd0fe3d920db53b4，合并树等于集成验证来源。普通Git连接超时，改用官方GitHub Git API按本地对象原始SHA、force=false交付，main及codex/be-26-source-closure两个完整SHA回查一致，任务分支保留。集成完整139离线/21组＋350真实MySQL/HTTP/23组及静态校验通过，失败/错误/跳过0；本机python3商店占位导致首轮1项环境失败，临时测试入口修正后完整重跑通过，业务代码未因此修改。随机schema/账号/密钥目录均0，专属MySQL子进程17336正常关闭，原MySQL84仍Running。BE-26为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，D人工验收仍开放，其他成员状态保持；共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED。原D实施D-091与C重复，统一D-093，原f745648/b011a8a及C的D-091/092事实保留。用户另要求交付后自行继续下一项，后续按依赖核对BE-30，不代A实施BE-27、不部署。下方未授权/待交付为历史。

D-093集成结果（2026-10-21）：接入远程main/6b9a02e及C/BE-21/23/24/25/31后，完整clean verify/mysql-check通过，139项离线/21组＋350项真实MySQL/HTTP/23组，失败/错误/跳过0，日志backend/.tmp/be26-delivery-final.log。首轮1项演示脚本因本机python3商店占位退出9009，临时测试目录配置真实Python入口后完整重跑通过；未因此修改业务代码或永久环境变量。随机库/账号/测试密钥目录均0，专属MySQL子进程17336正常关闭，原MySQL84仍Running。BE-26保持IMPLEMENTED、ownerAccepted=false，主线交付由用户明确“先进行合并推送”授权，实际成功后登记；共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED。原D编号D-091与C重复统一为D-093，C的D-091/092及双方历史保持。

D-096最新集成（2026-10-21）：B 已接入 C 工作流 main/6b9a02e，修正正式审批预览 /previews 路径，并补真实配置审批→授予→业务查询→撤销串联、发布证据工具与交接清单。原 B D-091/092 为避免与 C 重号统一为 D-095/096，源提交与验收事实保留。BE-33 仍缺 BE-27/30（传递依赖 BE-26），BE-35 缺 BE-34 真实服务器验收，两项保持 PLANNED、单列 preparationProgress，不伪造 AT-38/39 或完整项目通过。本地记录显示 60/67 入站操作已有实现，不等于全部契约验收通过。实际结果见 [集成与交接准备](BE-33-35-INTEGRATION-HANDOFF.md)，未推送/合并远程或部署。下方基线 7914788/缺 C 模块为历史。

D-095（2026-10-21）：B 在已交付 BE-14 后按用户“那就改”实现 BE-18 两个业务权限接口，分支 codex/be-18-effective-permissions、基线 main/7914788。完整来源并集、独立进入/菜单/操作、当前身份与主库新快照、未知与故障分离；不改迁移/契约，不代记验收或主线交付。实际验证见 [BE-18](BE-18-EFFECTIVE-PERMISSIONS.md)。当前 13 项 OWNER_ACCEPTED、5 项 IMPLEMENTED、6 项 IN_PROGRESS、11 项 PLANNED；B 六项业务开发已实现待本人确认，BE-33/35 仍开放。下方 BE-18 等依赖/旧数量为历史。

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

下表为验收目标：AT-01/37的BE-02部分、AT-02的基础持久化及AT-03/33的公共事务/快照及AT-04的公共幂等部分、AT-05/06/07的BE-06身份部分及AT-06/07的BE-07账号管理部分、AT-36的BE-32打包初始化/配置部分为PARTIALLY_VERIFIED，AT-13/27/29的BE-10局部行为为PARTIALLY_VERIFIED，AT-08/19的BE-08任命读取/上下文/覆盖部分为PARTIALLY_VERIFIED，其余26项DESIGNED_NOT_RUN；完整业务仍未验收，局部证据见[BE-02实施记录](BE-02-IMPLEMENTATION.md)、[持久化基础验证](BE-03-PERSISTENCE.md)、[公共事务](BE-04-TRANSACTIONS.md)、[幂等机制](BE-05-IDEMPOTENCY.md)、[身份实施](BE-06-IDENTITY.md)、[账号管理](BE-07-ACCOUNTS.md)及[部署初始化](BE-32-DEPLOYMENT.md)。其中AT-40为前端后置交接，不计本期后端验收；其余40项覆盖后端、部署及协作。实际执行需对应Issue按项目TDD技能记录失败→实现→通过，并在隔离MySQL8.4运行关键事务/并发/故障场景。

D-038阶段约定：用户先验证BE-01～05的公共机制和对应acceptanceScope，F-01～06为基座交接检查，不新增业务测试编号。真实身份、草稿删除、审批、导入等端到端场景留业务分派后补齐；夹具证明机制不能标记整个AT或业务已通过。D-053已确认F-01～06，基座已合并并推送main/b68aa9a；见FOUNDATION-REVIEW.md。

| 用例 | 行为/准备 | 必须观察到的结果 | 操作 | 评审追踪 |
| --- | --- | --- | --- | --- |
| AT-01 可重复构建与最小部署 | 空工作目录按固定Wrapper构建；缺必需配置再启动 | 构建产物可追溯提交；缺配置清楚失败，日志无凭据；健康检查不暴露内部详情 | 内部/部署 | 新增验收准备 |
| AT-02 MySQL迁移、即时约束与JPA映射 | 隔离MySQL8.4空库运行迁移两次，尝试非法跨系统FK/重复ACTIVE/非法CHECK及UUID往返 | 首次成功、再次无额外业务数据；非法记录拒绝；43表/触发器实际编译与UUID/UTC/生成列/@Version行为有证据 | 内部/部署 | 新增验收准备 |
| AT-03 写锚点及原子回滚 | 两个独立连接先后取得guard，在最后一次审计插入注入异常 | 写者串行；失败事务所有业务/版本/成功凭据回滚，其他连接看不到半份状态 | 内部/部署 | S08/S15/S20 |
| AT-04 HTTP幂等栅栏与失败恢复 | 同主体同key相同/不同内容；模拟过期租约、提交后丢响应、删除草稿后重放和失去范围 | 相同结果回放不重比旧目标版本；异内容409；旧token不能提交；删除后以固定事实复核当前资格；失权401/403 | deleteChangeDraft | S16 |
| AT-05 登录与普通员工边界 | 有效管理员、普通员工、临时密码账号、错误密码分别登录 | 普通员工不能登录；失败不泄露账号存在；临时token仅改密/退出；限速/过期符合契约 | login,changeOwnPassword | S21 |
| AT-06 退出、改密及账号停用即时撤销 | 同账号两会话；退出一项、改密/重置、停用/启用后再次用旧token | 退出只撤当前sid且重复204；改密/重置撤全部旧会话；恢复账号不复活旧token或改变业务授权 | logout,currentSession,setAccountState,resetAccountPassword | S21/S22 |
| AT-07 三类JWT严格隔离 | Admin/Business/OA token交叉调用；错误iss/aud/alg、失效服务和错误system绑定 | 401/403且不泄露目标；业务凭据只限绑定系统，不增加公开令牌签发API | authorizationQuery,permissionCheck,receiveOaResult | 新增验收准备 |
| AT-08 管理员任命与单项上下文 | 两项互不覆盖的任命、陈旧/缺失上下文头、没有任职的候选账号 | 不能拼接；缺头422、陈旧409；不合资格拒绝且全批不变；仅共享角色维护由总管理员 | myAppointments,createScope,replaceScope | S04/S11 |
| AT-09 系统进入槽与同批功能目录 | 登记系统并同批建FUNCTION/操作/菜单绑定；跨系统/重复localKey/长环 | 进入权限唯一；合法本地引用一次提交；非法整体拒绝无半成品；排序稳定 | createSystem,saveCatalog | S02/S03 |
| AT-10 独立操作与目录启停 | 角色只有编辑、另一角色有进入；停用查看或父目录，再恢复 | 编辑不推导查看；角色/授权关系保留；父级/操作过滤正确，恢复只作用于仍有效关系 | updateSystem,saveCatalog,authorizationQuery,permissionCheck | S03/S23 |
| AT-11 角色元数据与归属 | 创建部门/共享角色、改名及伪造公司/系统归属 | 归属不可改；共享角色仅总管理员；改名仅metadataVersion，不改变有效configurationVersion | createRole,updateRoleMetadata | S04 |
| AT-12 角色配置新增/保留/移除与空配置 | 基础A+停用B，最终A+B+有效C；新增C在执行前停用；移除B/清空/无差异 | 保留B合法、可明确移除；新增C失效则整份INVALIDATED；清空422；无差异200不投OA | changePreview,submitChange | S05 |
| AT-13 完整任职依据与岗位不自动授权 | 同部门101项适用任职、重复/遗漏证据；岗位更名或新增岗位 | 全部依据可提交；空/重复/遗漏拒绝；岗位变化本身不创建grant，固定证据不悄然改写 | grantBaselines,createChangeDraft,submitChange | S06 |
| AT-14 同源去重与异源并存 | 同员工角色同部门重复授予，另一部门独立授予；撤销后新授予 | 同源仅一ACTIVE；不同来源同时有效；baseline不重置，新grantId，旧撤销不误伤新关系 | grantBaselines,listGrants,submitChange | S07/S09 |
| AT-15 零基线关权与执行两个顺序 | 来源无grant但有待授予；用两个连接控制执行先/关权先及同key重放 | 无grant也推进周期；执行先后被关掉，关权先旧批准全份失效；同key不再推进，其他来源保留 | sourceBaselines,closeSource | S08 |
| AT-16 无变化、权限与版本判定顺序 | 越范围已授予项、同源已授予但旧配置版本、已结束准确grantId、仍需项陈旧、预览后分类改变 | 先拒越权；合法已授予/已结束归无变化；仍需项版本409；预览变化409而不裁剪 | changePreview,submitChange | S10 |
| AT-17 草稿占用与删除 | 多人改同草稿/同baseline；移出范围后删除；一个新目标占用失败 | 冲突全回滚，原草稿/claims保留；当前不覆盖者不得处理，管理员通用提示不泄漏隐藏ID；总管理员现有入口清理 | createChangeDraft,updateChangeDraft,deleteChangeDraft | S12 |
| AT-18 固定申请与规范摘要 | 提交后改当前名称；尝试改内容/目标行；使用RFC8785向量重建摘要 | 冻结内容/字节/投影一致，当前更名不改批准内容；提交后修改拒绝；固定版本与statusVersion分开 | submitChange,getChangeRequest | S13 |
| AT-19 原申请人离任与原范围缩小 | 申请人离任/账号停用；原范围实际缩小后扩回；仅名单更换/增无关系统 | 前两种资格离任不机械影响已提交申请；实质失覆盖持久INVALIDATED且不复活；名单变化不误伤 | replaceScope,receiveOaResult | S11 |
| AT-20 OA投递超时与关联前回调 | 受理后返回超时；关联未写先回调；申请失效时发送返回 | 同requestId重试关联同审批号；未关联409且不消耗事件；失效只补历史不回PENDING/不执行 | retryOaDelivery,receiveOaResult,deliverOaSubmission | S14 |
| AT-21 OA事件、最终结果三层防重 | 同事件同/异内容；不同事件同决定；同申请相反决定；失效申请迟到批准 | 匹配重复200；异内容/矛盾409不覆盖；可靠新结果202不等于授权生效；失效留历史不复活 | receiveOaResult | S14/S15 |
| AT-22 一次成功与全批执行 | 100目标固定申请，第N项变陈旧；两执行器竞争；成功audit写入失败 | 不裁剪，一项非法整份失效无有效子集；success请求PK只一次；存储失败全回滚后另事务记失败 | receiveOaResult,getChangeRequest | S15 |
| AT-23 执行失败恢复与进程重启 | 已可靠APPROVED后宕机，执行写失败，回包丢失，失败记录前另一执行已成功 | 恢复扫描拾取；同申请退避重试；不重新审批；失败记录不能覆盖success/INVALIDATED，成功只回读 | getChangeRequest | S15 |
| AT-24 员工停用/恢复与会话 | 员工有多来源/兼岗/任命，与授予并发停用后恢复 | 全部旧grant/任命/会话持久失效，epoch推进；恢复仅员工ACTIVE；保护当前/唯一总管理员 | setEmployeeState | S17/S22 |
| AT-25 离岗事件顺序Q-01 | 同原子批次END最后适用任职再ADD同部门新岗；另有始终适用兼岗；仅更名 | 前者推进周期并使旧授权/任命/相关申请失效且新岗不复权；后两者按适用条件保留，全批原子 | commitImport | S18 |
| AT-26 上传与文件解析边界 | CSV/XLSX合法样例，超10MiB/解压50MiB/1000行、宏、业务公式、含糊工作表 | 合法可预检；超限/非法明确拒绝；不执行公式；预检前无正式组织/员工变更 | uploadImportFile,precheckImport | S19 |
| AT-27 预检冻结、映射与候选冲突 | 无源编码组织、同名不同路径/源账号、规范化前后值；预检后占用候选code | 预分配稳定ID/code并回读完整fieldChanges；同名不归并；提交冲突STALE，全批无正式写入 | precheckImport,listImportPlanChanges,commitImport | S01/S19 |
| AT-28 导入第N项失败与重试 | 1000行含停用/离岗，最后行冲突或审计失败；成功批次重放/新批同资料 | 整批含失效/版本/审计全回滚，失败另存；同成功批号回原counts，新批UNCHANGED，不复活旧状态 | commitImport | S20 |
| AT-29 部门迁移与历史保护 | 部门已有角色草稿/历史grant/审计；完全无业务历史引用的迁移 | 有引用阻断；合法迁移按冻结映射与即时FK顺序同事务完成，不级联改写历史 | precheckImport,commitImport | S25 |
| AT-30 业务权限完整并集与隔离 | 同公司系统多部门来源、另一公司同名权限、无进入但有操作、未知code | 当前公司/系统完整有效来源并集；进入与功能可跨角色；无进入结果空；未知与存储故障区分，无数据范围 | authorizationQuery,permissionCheck | S23 |
| AT-31 影响快照与新增目标 | 预览后出现新grant/申请/任职；权限缩小/切换/过期；无关改名 | 检测集合新增和真实依赖变化；陈旧409或拒绝旧预览，不裁剪/不配旧统计；无关变化不机械失效 | listPreviewEmployees,listPreviewTargets,submitChange | S24 |
| AT-32 敏感混合申请计数与审计 | 关闭可见来源A，命中跨A+B固定申请；当前任命只含A | 整份实际失效；不返回混合申请ID/计数或不可读auditId；SCOPED/true配可见计数，缓存相同安全投影 | closeSource,getAudit,listAudit | S24 |
| AT-33 当前快照与撤权后的新查询 | 业务查询建立新主库快照，另一事务关闭来源提交后再开新查询 | 新查询不见旧权限；同一响应身份与结果一致；不使用旧EntityManager/副本/权限缓存 | authorizationQuery,permissionCheck | S23 |
| AT-34 审计读取与历史删除 | 越权详情/显式筛选、混合全局事件、删除草稿与历史引用 | 详情404/筛选403；列表total按可见范围；不可拆完整混合事件仅总管理员；保留历史及防重事实 | listAudit,getAudit,listChangeEvents | S24/S25 |
| AT-35 演示OA与服务令牌边界 | demo profile受理固定申请但未批准；用可信OA短期JWT调用正式回调，再拒绝/重复/重启 | 受理不自动批准；只经原回调决定；无新管理员审批/强制执行接口；模拟标签明确，重启不丢本地防重依据 | 内部/部署 | 新增验收准备 |
| AT-36 服务器部署和数据持久化 | 新目标环境安装/启动、重启应用、备份恢复到隔离环境、回退应用版本 | 迁移校验通过，数据/文件/密钥持久；外部HTTPS仅暴露必需入口，健康可查，恢复/回退有证据；不把迁移回滚当事务 | 内部/部署 | 新增验收准备 |
| AT-37 全接口契约与Problem | 67入站逐项HTTP：成功、401/403、缺版本/非法字段、分页/位置/错误媒体类型 | 按OpenAPI1.2.0验证请求响应，目标资源当前授权；错误application/problem+json，敏感响应no-store；每项由所属Issue负责 | 内部/部署 | 新增验收准备 |
| AT-38 示例全链路演示 | 虚构两公司/两部门/兼岗、两业务系统、共享/部门角色；按正式导入及审批流程走一遍 | 角色配置→OA→授予→查询→关来源→迟到批准→恢复不复权可复现；日志与报告无凭据，不依赖正式前端 | 内部/部署 | 新增验收准备 |
| AT-39 PR与发布证据 | 一个任务分支、失败→通过测试记录、任务负责人自检确认、候选部署包 | Issue/PR/commit/构建校验和及负责人确认对应；未验收项不关闭，未运行测试不打勾；当前设计阶段不创建远程事项 | 内部/部署 | 新增验收准备 |
| AT-40 前端后续衔接 | 既有原型页面对照真实契约与当前任命/状态三轴 | 复用页面与交互基线，未来接入真实API；原型Mock不作为后端验收；本周不实现正式前端 | 内部/部署 | 新增验收准备 |
| AT-41 来源资格与角色部门资格分别失效 | 员工仍在来源部门A，但失去所持部门角色所属B部门的最后适用任职 | 只结束受影响角色各来源grant并推进其baseline/旧申请失效；不关闭仍有效的整个A来源，不误伤其他角色，不更换来源部门 | commitImport,grantBaselines | S06/S18 |

## 测试层次与运行证据

- 单元：纯策略/集合差异/规范化/目录无环，不以mock调用次数替代行为。
- HTTP：真实DTO/认证链/Problem与全部67入站响应Schema；单项管理上下文、旧token和三类JWT交叉调用。
- MySQL集成：Testcontainers MySQL8.4；无容器时使用团队批准的独立测试实例和唯一测试schema，绝不指向共享/业务库。H2不替代即时FK、JSON、触发器、生成槽与事务测试。
- 并发：两个独立连接/事务，以barrier控制锁取得/提交先后；测试方超时保证清理。既测执行先也测关权先，观察最终授权/epoch/success/audit，而不是仅断言抛异常。
- 故障：在业务第N项、audit、success及完成幂等结果前注入异常，另连接检查全回滚；结果可靠提交后模拟进程中断/响应丢失，恢复不覆盖成功。
- 部署：候选产物在实际目标服务器启动、迁移、重启、HTTPS与全链路演示；本机Mock不能替代。

Issue的验收AT列表是最低必测行为；具体负责部分见每卡acceptanceScope。AT-37覆盖每项所属API，包括未单独列出的列表/详情/预览。基础任务可按其负责部分验收合并，不能据此把整个AT标为通过；BE-33汇总后端完整行为与契约证据，BE-34完成目标服务器，BE-35汇总发布证据。BE-01只核验准备清单，不要求先有构建或发布包，因而不形成实现前置条件循环。测试类和断言在对应模块实现时按writing-good-tests设计，BE-02已有公共适配的RED/GREEN及打包HTTP证据，见[BE-02-IMPLEMENTATION.md](BE-02-IMPLEMENTATION.md)；BE-06身份验证见[身份记录](BE-06-IDENTITY.md)，其余业务尚无运行结果。

拟定构建命令为`./mvnw verify`，其中unit走Surefire、*IT走Failsafe、集成测试不得静默skip；另生成JUnit/契约验证报告。当前backend已有Wrapper及实际构建结果；BE-03准备后完整构建41项离线测试通过，包含原BE-02的30项，不等于真实数据库或完整业务验收通过，见BE-03-PREPARATION.md；最新BE-06身份与旧回归见BE-06-IDENTITY.md；此前BE-05幂等验证见BE-05-IDEMPOTENCY.md；51离线＋25引擎是此前BE-04结果，44离线＋9引擎是此前BE-03结果，领域映射/完整业务并发尚未验收。外部真实OA改为演示适配，信任及回调边界仍保留。
