# 后端详细设计与协作实施入口

设计/实施计划修订1.2；2026-10-17。技术输入仍为需求V1.3、API1.2.0、数据库1.1；D-038确定先基座后分派，D-039更新本机环境与基座本人确认。当前从main/169556d建立codex/foundation-environment，只记录准备事实和规则，不改变业务契约。

当前用户确认：已有云服务器，后续可Docker部署数据库，先用本机MySQL；使用IDE可引用的JDK21，命令行未配置环境变量；基座由用户本人确认，无需别人评审。实际JDK安装运行、本机MySQL版本/连接和云端部署尚未验证。BE-01环境准备中，其余基座代码未实现；真实OA不存在，继续示例方案，正式前端后置。

完整任务低/中/高估算为78/145.5/239人时（业务评审保留，基座本人确认含开发估算），高于40～72人时可用预算；见实施计划的阶段汇总与假设。需求保持完整，41项验收（40后端、1前端后置）全部只是设计，未记录为实际通过。当前下一步是用户实施公共基座，业务留待验收后分派，不能把设计交付视为后端已完成或5～6天全量交付承诺。

**当前分工：用户先实现BE-01～05基座，验收并交付共同主线后，再分派BE-06～35。** BE-01正在准备、基座代码尚未实施/验收，30项业务实际Owner/Reviewer待定；A～D只作建议。真实登录等身份业务在第二阶段，13逻辑模块和原技术依赖保留。先读[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)的范围、F-01～06门槛及分派规则；机器阶段见[delivery-phases.json](delivery-phases.json)。本轮只读核验PATH/JAVA_HOME并更新记录，没有修改环境变量、连接数据库或开始工程。环境及确认方式见[ENVIRONMENT.md](ENVIRONMENT.md)。

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
