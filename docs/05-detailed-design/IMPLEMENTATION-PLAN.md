# 先基座、后分工：实施估算与依赖计划

D-091（2026-10-21）：C在明确确认后要求继续全部剩余任务，于BE-21-31-申请审批与执行（main/7914788）依次设计/TDD实现BE-21/23/24/25/31。固定七字段RFC8785包、预览/无变化/占用、可靠OA待办/租约/三层回调防重、整份原子执行/失败记录/启动与周期恢复、显式demo适配器和短期服务JWT工具已实现；完整139离线＋316真实MySQL/HTTP及静态校验通过，失败/错误/跳过0。专属测试资源已清理关闭，用户既有库未操作。五项IMPLEMENTED、ownerAccepted=false、mergedToMain=false；既有BE-19/20/22负责人确认与主线交付保持，C八项实现均具备，剩本轮五项本人验收/主线交付。真实外部OA及服务器/全系统验收不在本轮，其他成员状态保持；共13项OWNER_ACCEPTED、10项IMPLEMENTED、5项IN_PROGRESS、7项PLANNED。证据见BE-21-31-APPROVAL-WORKFLOW.md，下方旧状态为历史。

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

修订1.4/D-055：BE-01～05公共基座已确认并纳入主线，BE-06～35已登记A～D成员代号，详见[四人分工](TEAM-ASSIGNMENT.md)。实名/GitHub账号及成员接入证据待补；BE-06为OWNER_ACCEPTED（D-057，已由D-060交付main）、BE-07为OWNER_ACCEPTED（D-059，已由D-060交付main）、BE-32为OWNER_ACCEPTED（D-062，已由D-063/PR#1交付main），BE-10为IMPLEMENTED（六个查询接口及内部读写端口已实现，已由D-065交付main，待负责人验收），BE-08为OWNER_ACCEPTED（D-068，已由D-069/PR#2交付main），其余25项PLANNED，全部任务由负责人自行确认完成。业务原有技术依赖及启动门槛保留。

团队原投入40～72人时（4人、5～6天、每天2～3小时）仍作为比较口径；不是可保证的工期。逐任务技术范围及低/中/高工作量沿用1.0，D-051将全部任务单列独立评审工时归零，负责人自检/确认含原开发估算，重算总量和阶段容量；不是实测重新估价。下列个人负载只是按当前任务估算汇总，仍须按实测和成员可投入时间调整排期。

| 情境 | 全量开发与负责人自检确认人时 | 相对72人时上限 |
| --- | --- | --- |
| 低 | 70.5 | 超出0 |
| 中 | 130.5 | 超出58.5 |
| 高 | 209 | 超出137 |

| 阶段 | 开发人时（低/中/高） | 另计他人评审人时（均0） | 合计人时 |
| --- | --- | --- | --- |
| 用户实现基座（5项） | 9 / 18 / 28 | 0 / 0 / 0 | 9 / 18 / 28 |
| 已分派业务/交付（30项，未实现） | 61.5 / 112.5 / 181 | 0 / 0 / 0 | 61.5 / 112.5 / 181 |

| 成员代号 | 任务数 | 低/中/高人时（含自检确认） |
| --- | --- | --- |
| A | 7 | 12.5 / 25 / 40 |
| B | 8 | 16 / 29.5 / 47 |
| C | 8 | 17 / 30 / 48 |
| D | 7 | 16 / 28 / 46 |

用户基座开发及本人确认估算为9/18/28小时；全部任务由负责人确认，不另设评审人，单列评审为0。业务负载按D-055的A～D成员代号汇总，见businessPersonLoad；具体人员身份及实际可用时间尚未核验，不能把估算当完成时间。

**完整范围低估算70.5人时接近72上限，中估130.5人时仍超出预算，不能据此保证5～6天全量交付。** 增加投入或顺延需团队协调，业务范围变化另确认。

## 两阶段容量下界

基座阶段由用户开发并确认，其他人可准备场景；业务阶段暂以四人可投入计算容量，实际投入仍待成员确认。粗下界=向上取整〔基座开发人时/每人每日小时＋业务阶段开发及负责人自检确认总人时/(4×每人每日小时)〕。该式不含依赖和环境延迟；不是可执行日历。

| 情境 | 每人每天小时 | 仅看总量的容量下界 | 加入基座单人先行的下界 |
| --- | --- | --- | --- |
| 低 | 3 | 6工作日 | 9工作日 |
| 中 | 2.5 | 14工作日 | 19工作日 |
| 高 | 2 | 27工作日 | 37工作日 |

公式与阶段汇总见[estimates.json](estimates.json)。基座已通过并交付main，30项已按成员代号分派；技术依赖未就绪的任务不能记为已开工。用户已认领A，BE-06在开发分支实现且已本人确认（D-057），已交付main，D/BE-10可先行；BE-07已按D-059确认已交付main；A已确认BE-32且由D-063/PR#1交付main、BE-08已获A确认（D-068）并由D-069/PR#2交付main，BE-09仍等B/BE-17，B/C按前置交付情况推进。

## 实际推进顺序

1. 用户按BE-01→02→03→04→05实施基座，逐项TDD并保留可评审的小PR；基座已交付，D-056开始实施BE-06，详见BE-06-IDENTITY.md。
2. 按[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)完成真实环境验证、说明、用户本人确认和主线交付，记录基座验收证据；不是以文件生成或Mock摘要通过门槛。
3. D-055已登记30项任务的A～D分工；团队补成员实名/GitHub账号、访问权限、本机验证及依赖提交。需要时再创建真实Issue/PR，不虚构编号。
4. 业务阶段按既有依赖并行，BE-06/10等基础业务先行，之后目录角色、申请OA、来源导入按条件接入；完整闭环是集成里程碑，不要求所有人等整条闭环才开始自己的就绪任务。
5. BE-33汇总业务实证，BE-34目标服务器验收，BE-35发布交接；未完成项保持开放。

## 外部条件

用户确认已有云服务器可后续部署Docker数据库，目前用本机MySQL。JDK21可在IDE引用，命令行java/javac/JAVA_HOME尚不可用；不要求永久环境变量，实施时明确IDE/显式JDK方式。用户本机MySQL8.4.8、应用连接、开发/测试库迁移及账号权限已有基座证据；其他成员本机配置与云端部署仍待核验。JDK21/Wrapper3.9.11及BE-02已实测，见ENVIRONMENT.md和BE-02-IMPLEMENTATION.md。演示OA按既定方案，真实OA不是本期前置。外部等待未计入估算。
