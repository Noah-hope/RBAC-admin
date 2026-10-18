# 后端详细设计与协作实施入口

2026-10-18/D-049：用户已确认健康status=UP，并明确回复RBAC_IDEMPOTENCY_ENABLED“是，已设为 true”。结合15:28本机启动/测试库连接日志，按BE-05交接范围记录d059d11为OWNER_ACCEPTED；不是仅凭UP推断启用。BE-02～05单项已本人确认，BE-01仍IN_PROGRESS、其余30项PLANNED，基座总门槛NOT_EVALUATED；下一步核对BE-01剩余条件和F-01～06整体交接，不自行开始业务、合并或推送。见[BE-05确认记录](BE-05-IDEMPOTENCY.md)。下方待确认/待核验描述是此前历史，以本段及机器清单为准。

2026-10-18/D-048：BE-05幂等预留、租约栅栏、当前授权后的安全重放及HTTP适配已实现，64项离线＋42项隔离MySQL检查全通过，临时实例已关闭；默认关闭，显式启用时要求独立HMAC密钥。BE-02/03/04已本人确认，BE-01/05保持IN_PROGRESS，30项业务未分派；BE-05待本人确认、基座总门槛未评估。详见[BE-05记录](BE-05-IDEMPOTENCY.md)。下方D-043～047保留为此前历史，以本段和机器清单为准。

2026-10-18/D-047确认更新（沿用D-046实施证据）：BE-03在本机运行/健康及账号核对后，按用户“继续”的上下文记录阶段本人确认；BE-04公共RC写事务、RR读快照、有效WriteContext及同事务审计已实现，51离线＋25项隔离MySQL检查全部通过，临时实例已关闭、用户已有库未操作。BE-04已由用户确认服务UP并按D-047记录单项本人确认，BE-01仍开放、BE-05未实现，基座总门槛未评估；未合并或推送。见[BE-04记录](BE-04-TRANSACTIONS.md)。以下D-043～045的待核验/未实现描述保留为当时历史，以本段及机器清单为准。

2026-10-18/D-045：已实现UUID/可空Long存储版本/UTC微秒转换和缺guard启动保护，44离线＋9项隔离MySQL检查及打包通过；包含真实旧版本覆盖拒绝、生成列只读与非UTC JVM写入值核验。用户测试库迁移/历史/guard/重复运行已成功。AT-02仍局部验证，BE-03的本机运行/健康和账号库内授权已核对，待本人阶段确认，完整领域/跨域并发和基座未验收，见[持久化基础](BE-03-PERSISTENCE.md)。下文早期“未执行MySQL/JPA”等为历史。

设计/实施计划修订1.2；2026-10-17。技术输入仍为需求V1.3、API1.2.0、数据库1.1；D-038先基座后分派，D-039基座本人确认，D-040确定IDE/工程与账号约定，D-041授权完成BE-02公共骨架，不改变业务契约。

当前：JDK21/Wrapper3.9.11、Boot4.0.8实际构建和BE-02自测通过，使用com.adgn.backend/YAML；D-042已由用户本人确认通过（实现提交f911c60）。用户本机测试库迁移和guard/重复运行已核对；普通后端运行及账号库内授权已核对，开发库初始化和云端部署仍待后续。真实OA不存在，继续示例方案；正式前端后置。运行见[后端README](../../backend/README.md)，证据见[BE-02实施记录](BE-02-IMPLEMENTATION.md)。

完整任务低/中/高估算仍为78/145.5/239人时，高于40～72预算，不以当前局部实测重估全量。41项验收中AT-01/37的BE-02部分及AT-02的独立引擎部分为PARTIALLY_VERIFIED，其余38项仍未运行。不能把公共骨架通过视为完整后端或全量交付承诺。

**当前分工不变：用户先实现BE-01～05，整体验收并交付共同主线后再分派BE-06～35。** BE-01为IN_PROGRESS，BE-02/03/04/05本人确认通过（OWNER_ACCEPTED），其余30项PLANNED；基座总门槛未评估，业务仍未分派。D-045已补齐基础JPA和启动保护，44项离线＋9项隔离引擎检查通过；用户已有库未由agent操作。首次依赖/脚本准备历史见[BE-03准备记录](BE-03-PREPARATION.md)。真实身份留第二阶段，13逻辑模块和原依赖保留。范围及门槛见[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)，机器阶段见[delivery-phases.json](delivery-phases.json)，实际环境见[ENVIRONMENT.md](ENVIRONMENT.md)。

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
