# 后端详细设计与协作实施入口

后端 JaCoCo：231普通＋469真实MySQL/HTTP，共700项通过；行覆盖率100.00%、方法100.00%、分支78.59%。见[覆盖率报告](JACOCO-COVERAGE.md)、[完整HTML归档](reports/JaCoCo-2026-10-27.zip)和[测试与采集验证](JACOCO-IMPROVEMENT.md)。

D-108（2026-10-26）：C明确同意继续合并推送验收记录。来源d816d0f4f976e7a603cf98b054388d0d9ca387ef已无冲突no-ff合入main/b9ce0d32147a0742b9a61e71995c3fd342922566，合并树等于确认来源；main与文档-C任务验收确认分支普通Git原子推送成功，两个远程完整SHA回查一致，分支保留。C八项均为OWNER_ACCEPTED且既有业务主线交付保持；本轮只同步验收记录及静态追踪，不重跑业务测试、连接数据库或部署。D-107仅本地提交、未推送的描述为当轮历史，其他成员验收状态保持。

D-107（2026-10-26）：C负责人在明确本人验收规则及五项待确认范围后回复“确认好了”，BE-21/23/24/25/31登记OWNER_ACCEPTED、ownerAccepted=true，既有主线交付保持。C八项任务均已实现、获负责人确认并交付主线。接受既有139离线＋316真实MySQL/HTTP证据；本轮只同步确认及静态追踪，不新增人工演示、数据库配置、真实外部OA或服务器部署验证，不代其他成员验收。下方C待本人确认记录为历史；本次记录仅本地提交，未推送。

D-102实际交付（2026-10-21）：D明确“完成合并并推送”。BE-30来源a669e5f1958ff163a0bcf32e4394887acf6d1130已无冲突no-ff合入远程main/98f14c6a4b53582c9e9efc587a0e3095d1693eea，双亲为main/126fbc9和验证来源，合并树等于已完整验证来源。普通Git原子推送main及codex/be-30-import-commit成功，两个远程完整SHA回查一致，任务分支保留。沿用139普通/21组＋425真实MySQL/HTTP/27组及18辅助检查证据，失败/错误/跳过0；本轮静态追踪通过，代码未变，不重复Java/SQL。专属测试资源此前已清空并正常关闭，原MySQL84保持Running。BE-30为IMPLEMENTED、mergedToMain=true、ownerAccepted=false；D七项实现均已交付共同主线，D人工验收仍开放。共13项OWNER_ACCEPTED、14项IMPLEMENTED、5项IN_PROGRESS、3项PLANNED；BE-33可接入本次交付，其他成员任务及验收状态保持，不部署或自动开新任务。下方未授权/待交付为历史。

D-101实际结果（2026-10-21）：BE-30冻结导入整批提交、历史与失败恢复已实现。完整139普通/21组＋425真实MySQL/HTTP/27组、18辅助工具及静态追踪通过，失败/错误/跳过0，含本任务19项真实检查。覆盖固定候选身份、1000行及第500条/最后审计故障全批回滚、同批长期成功回放、独立来源/角色部门资格、Q-01离岗再任职、员工停用恢复不复权、整份申请claim/outbox失效、部门迁移即时FK及迟到失败记录不覆盖成功。随机库/账号/密钥文件目录0，专属MySQL子进程5840正常关闭，原MySQL84仍Running。BE-30为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，仅本地任务分支提交；D七项代码均具备，既有人工验收不代确认。共13项OWNER_ACCEPTED、14项IMPLEMENTED、5项IN_PROGRESS、3项PLANNED；未合并推送或部署，不接管BE-33/34/35。接入基线main/126fbc9；D原准备D-095与B重复统一D-101，36c6cb5及双方事实保持。详见docs/05-detailed-design/BE-30-IMPORT-COMMIT.md，下方仅准备/IN_PROGRESS为历史。

D-101（2026-10-21）：D明确“开始执行最后一项任务”，已fetch核对并接入远程main/126fbc9，A/BE-27已通过PR #4交付92d126f，直接依赖齐备。D原BE-30准备D-095与B重复，统一为D-101，原36c6cb5及B的D-095/096/097/098、A的D-099/100事实保持。已按现有设计/TDD进入IN_PROGRESS；真实首轮3项断言失败、0错误，commit路由404；核心3项GREEN已通过，后续员工/生命周期和故障场景仍在实现。复用BE-29固定计划、EmploymentWriter与A/EmployeeStateWriter、BE-16/26及guard/幂等/审计，同批整体成功或回滚。共13项OWNER_ACCEPTED、13项IMPLEMENTED、6项IN_PROGRESS、3项PLANNED；不代本人验收、不合并推送或部署。下方仅准备/缺BE-27为历史。

D-101（2026-10-21）：D要求BE-26推送后自行继续下一项。BE-26及交付记录已推送并回查远程main/d79e28a，任务分支保留、D人工验收仍开放。已建立codex/be-30-import-commit并完成BE-30详细设计、实际端口衔接及14组TDD验收清单，见docs/05-detailed-design/BE-30-IMPORT-COMMIT.md。直接依赖BE-29已交付，A/BE-27仍PLANNED且主线缺员工全局状态与任命/会话失效编排，故只准备可独立部分，BE-30保持PLANNED、implementationStarted=false、testsRun=false，未改业务代码或运行本任务Java/SQL/HTTP。共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED；不跨成员实现BE-27、不代本人验收或部署。本次继续授权不扩展为BE-30合并推送，准备仅本地提交，下方BE-26未交付为历史。

- [BE-27员工状态与范围替换](BE-27-CROSS-DOMAIN-LIFECYCLE.md)：四个接口、同事务失效、验证与BE-30接入。

D-094实际交付（2026-10-21）：D明确“先进行合并推送”。BE-26已接入远程main/6b9a02e的C工作流，来源857d4a9d20fa43578f70385d256b86ce02ad3fdc已no-ff合入远程main/b00ffc98277e754757b0e3d9bd0fe3d920db53b4，合并树等于集成验证来源。普通Git连接超时，改用官方GitHub Git API按本地对象原始SHA、force=false交付，main及codex/be-26-source-closure两个完整SHA回查一致，任务分支保留。集成完整139离线/21组＋350真实MySQL/HTTP/23组及静态校验通过，失败/错误/跳过0；本机python3商店占位导致首轮1项环境失败，临时测试入口修正后完整重跑通过，业务代码未因此修改。随机schema/账号/密钥目录均0，专属MySQL子进程17336正常关闭，原MySQL84仍Running。BE-26为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，D人工验收仍开放，其他成员状态保持；共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED。原D实施D-091与C重复，统一D-093，原f745648/b011a8a及C的D-091/092事实保留。用户另要求交付后自行继续下一项，后续按依赖核对BE-30，不代A实施BE-27、不部署。下方未授权/待交付为历史。

D-093集成结果（2026-10-21）：接入远程main/6b9a02e及C/BE-21/23/24/25/31后，完整clean verify/mysql-check通过，139项离线/21组＋350项真实MySQL/HTTP/23组，失败/错误/跳过0，日志backend/.tmp/be26-delivery-final.log。首轮1项演示脚本因本机python3商店占位退出9009，临时测试目录配置真实Python入口后完整重跑通过；未因此修改业务代码或永久环境变量。随机库/账号/测试密钥目录均0，专属MySQL子进程17336正常关闭，原MySQL84仍Running。BE-26保持IMPLEMENTED、ownerAccepted=false，主线交付由用户明确“先进行合并推送”授权，实际成功后登记；共13项OWNER_ACCEPTED、11项IMPLEMENTED、5项IN_PROGRESS、6项PLANNED。原D编号D-091与C重复统一为D-093，C的D-091/092及双方历史保持。

D-093：已完成[BE-26来源关闭与统一申请失效](BE-26-SOURCE-CLOSURE.md)，完整137离线＋318真实MySQL/HTTP及静态校验通过，IMPLEMENTED待D负责人确认和主线交付。复用已交付C/BE-20状态/占用及BE-19/22的安全可见性，未实施BE-27/30或真实OA链路。

D-085：BE-29已合入并推送main/6506cda，交付记录86089d2远程回查一致，完整137离线＋240真实MySQL/HTTP通过，仍待D人工验收，见[交接](BE-29-IMPORT-PRECHECK.md)。D-093按用户要求继续下一项，已完成[BE-26设计与验收准备](BE-26-SOURCE-CLOSURE.md)；C/BE-20未交付，BE-26仍PLANNED，未运行本任务业务测试。

C的BE-19/22读取接口已在本地任务分支实现、待负责人确认及主线交付，见[审计与申请查询交接](BE-19-22-AUDIT-REQUEST-QUERIES.md)。

C的BE-20已获负责人确认并交付共同main，其余C任务仍开放，见[设计与验收交接](BE-20-CHANGE-DRAFTS.md)。

D-083：BE-09已通过[PR #3](https://github.com/Noah-hope/RBAC-admin/pull/3)交付main/5f42a2c，本地及远程已核对，任务分支保留；112普通＋227真实MySQL/HTTP通过。A确认沿用D-082，未进行人工演示；组员可更新main接入，见[交付记录](BE-09-SCOPE-CREATION.md)。下方待交付为历史。

D-082：A已按自动化测试证据确认BE-09，待主线交付，本机演示未进行；见[确认依据](BE-09-SCOPE-CREATION.md)及[PR正文草稿](BE-09-PR.md)。下方待负责人确认为历史。

D-081：A/BE-09创建预览与原子范围/任命已实现，待负责人确认及主线交付，见[设计、验证与本机步骤](BE-09-SCOPE-CREATION.md)。正常写入使用JPA，复用既有BE-17安全预览、guard与幂等；BE-27编辑/失效编排仍开放。

D-069：BE-08已通过[PR #2](https://github.com/Noah-hope/RBAC-admin/pull/2)合入main/ca85189并回查，任务已确认并交付；B可接入BE-17，A的BE-09仍等BE-17。下方待交付为历史。

D-068：BE-08已获A确认，待共同main交付；[PR正文](BE-08-PR.md)已准备，尚未创建PR或合并。下方待确认是此前历史。

D-067：A 已在任务分支实现 [BE-08 任命与范围读取、当前上下文及覆盖策略](BE-08-MANAGEMENT-SCOPE.md)，待 A 确认和主线交付。依赖的 D/BE-10 已合入 main，D 人工验收仍开放；BE-09 创建和 BE-27 持久失效按原依赖另行实施。

BE-32 已实现并通过 80 离线＋88 真实 MySQL 检查（D-061），前置已交付 main；管理员初始化、服务器配置与发布模板见 [BE-32](BE-32-DEPLOYMENT.md)及[操作指南](../../deploy/README.md)。未部署云服务器，任务已按 D-062 获 A 确认并通过 [PR #1](https://github.com/Noah-hope/RBAC-admin/pull/1) 交付 main/609ee4b（D-063）；BE-06/07 的既有确认与主线交付保持。

D-058（2026-10-19）：A已继续实现[BE-07账号管理](BE-07-ACCOUNTS.md)，5个接口复用当前身份、guard事务、HMAC幂等与GLOBAL审计。BE-07已按D-059本人确认，BE-06/07均已按D-060合入推送main/ff19d17并回查一致，其他28项PLANNED；本地连续开发依据显式授权及确认祖先，后续已按D-060合并推送。

2026-10-18/D-053：用户明确确认基座整体交接通过，并授权合并main及推送。BE-01～05均OWNER_ACCEPTED、F-01～06通过，门槛FOUNDATION_ACCEPTED；main/b68aa9a已推送GitHub且回查一致。D-055已登记30项业务的A～D分工，BE-06在D-056按用户认领A后实施、D-057本人确认、D-060交付main，其余29项PLANNED；各成员GitHub账号及其余接入证据待补，开工仍须原技术依赖。

先读[整体交接核对](FOUNDATION-REVIEW.md)和[团队使用指南](FOUNDATION-USAGE.md)，实际环境操作见[后端运行说明](../../backend/README.md)。公共HTTP、迁移/基础JPA、读写事务/审计及幂等机制已实现；BE-06最终74离线＋59隔离MySQL检查全通过（保留基座64＋42回归），开发/测试库迁移和普通启动也有用户证据。D-056本轮已按TDD运行Java和专属临时MySQL验证；用户现有库未操作。

设计/实施计划修订1.4（D-055四人分工，沿用D-051负责人确认）；输入仍为需求V1.3、API1.2.0、数据库1.1。13模块、35个本地任务，取消独立评审单列工时后，完整工作量70.5/130.5/209人时，低估接近72上限、中估仍高于40～72容量，不以基座结果承诺完整后端。AT-01/02/03/04/05/06/07/33/37仅局部验证，其余32项未运行。身份实施见[BE-06](BE-06-IDENTITY.md)，启用及团队样例见[接入说明](BE-06-LOCAL-SETUP.md)；各域业务及服务器验收留后续，正式前端后置、OA采用演示方案。

组织及门槛见[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)，机器状态见[delivery-phases.json](delivery-phases.json)，BE-01环境/协作待办见[ENVIRONMENT.md](ENVIRONMENT.md)。

| 阅读顺序 | 文件与用途 |
| --- | --- |
| 1 | [ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md)：结构化分解、依赖、公共契约及模块职责 |
| 2 | [module-structure.drawio](module-structure.drawio)：两页可编辑模块层次、需求数据流映射 |
| 3 | [HANDLER-CONTRACTS.md](HANDLER-CONTRACTS.md)、[CORE-FLOWS.md](CORE-FLOWS.md)、[core-flows.drawio](core-flows.drawio)：四页流程图及详细调用/事务/恢复步骤 |
| 4 | [PERSISTENCE.md](PERSISTENCE.md)：JPA映射、查询投影、版本、锁和事务适配 |
| 5 | [OA-DEMO.md](OA-DEMO.md)：演示OA受理、可信最终回调及场景脚本设计 |
| 6 | [ENGINEERING.md](ENGINEERING.md)、[DEPLOYMENT.md](DEPLOYMENT.md)：工程/依赖技术取值和后续部署/初始化/回退验收 |
| 7 | [TEAM-ASSIGNMENT.md](TEAM-ASSIGNMENT.md)：先汇总、再按A/B/C/D展开任务与交接；[TEAM-WORKFLOW.md](TEAM-WORKFLOW.md)：分支、CI和负责人确认 |
| 8 | [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md)、[TASKS.md](TASKS.md)：35个可复制本地Issue草案、依赖、分工和估算 |
| 9 | [TEST-PLAN.md](TEST-PLAN.md)、[TRACEABILITY.md](TRACEABILITY.md)：41项验收及67入站＋1出站逐操作主责追踪 |
| 10 | [FRONTEND-HANDOFF.md](FRONTEND-HANDOFF.md)、[REVIEW.md](REVIEW.md)、[VALIDATION.md](VALIDATION.md)：前端后置、自查/待办及实际验证边界 |

[design_plan.py](design_plan.py)只生成本目录元数据、追踪、Issue正文、估算及原生drawio，不生成业务代码。对应机器资料为[modules.json](modules.json)、[issues.json](issues.json)、[acceptance.json](acceptance.json)、[operation-map.json](operation-map.json)、[estimates.json](estimates.json)。[validate_design.py](validate_design.py)执行设计静态检查；[validation.json](validation.json)记录实际结果，不能替代MySQL/JPA/并发/部署测试。

上游权威入口：[正式需求](../01-requirements/usecases-v1/用例说明.md)、[管理规则](../01-requirements/usecases-v1/管理范围与人员权限规则.md)、[API](../03-api/openapi.yaml)、[数据库](../04-database/README.md)、[正式一致性评审](../04-database/CONSISTENCY-REVIEW.md)。状态以[MEMORY](../project/MEMORY.md)为准，不从目录存在推断实现完成。


C固定申请、OA审批、可靠执行与演示工具的设计及验证见[BE-21/23/24/25/31](BE-21-31-APPROVAL-WORKFLOW.md)，本机使用见[演示步骤](../../deploy/DEMO-OA.md)。
