# 后端详细设计与协作实施入口

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
