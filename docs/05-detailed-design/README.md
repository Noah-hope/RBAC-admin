# 后端详细设计与协作实施入口

BE-32 已实现并通过 80 离线＋88 真实 MySQL 检查（D-061），前置已交付 main；管理员初始化、服务器配置与发布模板见 [BE-32](BE-32-DEPLOYMENT.md)及[操作指南](../../deploy/README.md)。未部署云服务器，任务已按 D-062 获 A 确认，待主线交付；BE-06/07 的既有确认与主线交付保持。

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
