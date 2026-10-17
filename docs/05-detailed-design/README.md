# 后端详细设计与协作实施入口

设计/实施计划修订1.1；2026-10-17（Asia/Shanghai）。1.0技术输入为`bd26465`；本轮从`main/9416980`干净工作区建立`codex/foundation-first-plan`，按D-038更新实施组织。需求V1.3/D-033、API1.2.0、数据库1.1及正式跨阶段评审不变；Git契约SHA-256为`9910ceafadea88d85793d00f7bb4e6e5673700a8565366a407185c4517e0cbdf`。

用户本轮授权：执行后端详细设计、任务分解、四人估算和部署验收方案。目标是后续实现整个既定RBAC0系统并可部署；团队4人、5～6天、每天每人2～3小时；服务器未买、MySQL未配置、真实OA不存在，采用示例演示。前端原型已有交付，正式前端实现后置。本轮没有创建应用工程/ORM实体、运行数据库/SQL、购买服务器、创建远程Issue/PR或部署。

完整任务低/中/高估算为79.25/148/244人时（含评审），高于40～72人时可用预算；见实施计划的阶段汇总与假设。需求保持完整，41项验收（40后端、1前端后置）全部只是设计，未记录为实际通过。当前下一步是用户实施公共基座，业务留待验收后分派，不能把设计交付视为后端已完成或5～6天全量交付承诺。

**当前分工：用户先实现BE-01～05基座，验收并交付共同主线后，再分派BE-06～35。** 基座尚未实施/验收，30项业务实际Owner/Reviewer待定；A～D只作建议。真实登录等身份业务在第二阶段，13逻辑模块和原技术依赖保留。先读[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)的范围、F-01～06门槛及分派规则；机器阶段见[delivery-phases.json](delivery-phases.json)。本轮只修订设计，没有开始编码、建库或创建远程任务。

| 阅读顺序 | 文件与用途 |
| --- | --- |
| 1 | [ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md)：结构化分解、依赖、公共契约及模块职责 |
| 2 | [module-structure.drawio](module-structure.drawio)：两页可编辑模块层次、需求数据流映射 |
| 3 | [HANDLER-CONTRACTS.md](HANDLER-CONTRACTS.md)、[CORE-FLOWS.md](CORE-FLOWS.md)、[core-flows.drawio](core-flows.drawio)：四页流程图及详细调用/事务/恢复步骤 |
| 4 | [PERSISTENCE.md](PERSISTENCE.md)：JPA映射、查询投影、版本、锁和事务适配 |
| 5 | [OA-DEMO.md](OA-DEMO.md)：演示OA受理、可信最终回调及场景脚本设计 |
| 6 | [ENGINEERING.md](ENGINEERING.md)、[DEPLOYMENT.md](DEPLOYMENT.md)：工程/依赖技术取值和后续部署/初始化/回退验收 |
| 7 | [TEAM-WORKFLOW.md](TEAM-WORKFLOW.md)：Issue/PR协作、分支、评审、CI和完成条件 |
| 8 | [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md)、[TASKS.md](TASKS.md)：35个可复制本地Issue草案、依赖、分工和估算 |
| 9 | [TEST-PLAN.md](TEST-PLAN.md)、[TRACEABILITY.md](TRACEABILITY.md)：41项验收及67入站＋1出站逐操作主责追踪 |
| 10 | [FRONTEND-HANDOFF.md](FRONTEND-HANDOFF.md)、[REVIEW.md](REVIEW.md)、[VALIDATION.md](VALIDATION.md)：前端后置、自查/待办及实际验证边界 |

[design_plan.py](design_plan.py)只生成本目录元数据、追踪、Issue正文、估算及原生drawio，不生成业务代码。对应机器资料为[modules.json](modules.json)、[issues.json](issues.json)、[acceptance.json](acceptance.json)、[operation-map.json](operation-map.json)、[estimates.json](estimates.json)。[validate_design.py](validate_design.py)执行设计静态检查；[validation.json](validation.json)记录实际结果，不能替代MySQL/JPA/并发/部署测试。

上游权威入口：[正式需求](../01-requirements/usecases-v1/用例说明.md)、[管理规则](../01-requirements/usecases-v1/管理范围与人员权限规则.md)、[API](../03-api/openapi.yaml)、[数据库](../04-database/README.md)、[正式一致性评审](../04-database/CONSISTENCY-REVIEW.md)。状态以[MEMORY](../project/MEMORY.md)为准，不从目录存在推断实现完成。
