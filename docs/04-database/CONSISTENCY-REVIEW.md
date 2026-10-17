# 需求—API—数据库正式一致性评审

日期：2026-10-17（Asia/Shanghai）。用户授权开展本次正式评审；起点`codex/database-design / e3fa818`，工作区干净，API交付祖先为`2b78430`。沿用当前目录，不建worktree、不合并main。评审输入为正式需求V1.3及D-033/Q-01、Git API1.1.0、数据库1.0草案；修订输出为未上线API1.2.0及数据库1.1设计草案。

**结论：修订后在设计层面有条件通过，可进入详细设计。** 发现6项设计缺陷（3项P1、3项P2），本轮均修订；没有需要重新改变业务规则才能继续的阻塞项。这里的“关闭”表示需求、契约及持久化协议已对齐，不表示正式业务代码、真实引擎、并发或外部集成已验证。Apifox1.2.0人工同步、实际引擎/JPA与OA验证仍待办。

## 依据及方法

- 正式依据：[管理规则](../01-requirements/usecases-v1/管理范围与人员权限规则.md)、[用例总览](../01-requirements/usecases-v1/用例说明.md)、同目录UC-01～11，及[决策D-033](../project/DECISIONS.md)。需求/已有决策优先；不以原型Mock或表结构改变业务。
- 接口依据：[openapi.yaml](../03-api/openapi.yaml)、[公共约定](../03-api/CONVENTIONS.md)、[一致性](../03-api/CONSISTENCY.md)、[追踪](../03-api/TRACEABILITY.md)、[数据库交接](../03-api/DATABASE-HANDOFF.md)、[API评审](../03-api/REVIEW.md)、[Apifox事实](../03-api/APIFOX.md)及[验证](../03-api/VALIDATION.md)。Git是权威，不重新排查已记录的MCP差异。
- 数据依据：本目录逻辑模型、43表元数据/DDL/触发器、字段映射、I-01～22、T0～12、索引、追踪、自查和迁移验证。逐场景核对“正式规则→接口表达→数据库可表达部分→事务检查/失败恢复”，不能仅数表或操作。
- 使用可用api-designer及erd-schema-designer审查方法，仓库无同名副本。没有新增业务模型、ORM实体或工程；原可编辑ER图由元数据生成器维护，关系未改变。没有启动/连接MySQL或执行SQL。

## 缺陷、影响与修订

| 编号/级别 | 具体反例与依据 | 原设计问题 | 本轮修订/关闭证据 |
| --- | --- | --- | --- |
| CR-01 / P1 | 角色配置已有A和停用B，申请新增有效C且保留B；或为员工授予仍EFFECTIVE且保留B的角色。UC-01停用保留配置，UC-02 E05只拒绝新增无效授权，UC-03并不要求角色每项当前启用 | T4要求所有保留/新增项均有效，授予还要求角色全部权限有效，额外拒绝合法业务 | T4及API一致性按固定基础集合差集检查新增项；保留停用项合法且运行时过滤，移除合法；引用缺失/非法归属仍拒绝。不补授进入权限、不更改已批准集合。关闭：文档交叉核对；待业务实现验证 |
| CR-02 / P1 | 权限管理员关闭来源A，命中总管理员提交的A+B跨部门申请，整份必须失效但该管理员无权读B或整份申请 | SourceCloseResult未说明计数范围，也无完整性标志；若直接返回内部实际失效申请数/审计ID，会泄露隐藏对象；若返回0，又易误报无影响 | Schema明确可见计数和可读审计ID，新增必填visibility及hasUndisclosedImpact，成对互斥；T5完整失效与响应投影分开，缓存安全投影。关闭：Schema反例C04～08＋事务推演；真实鉴权未实现 |
| CR-03 / P1 | DELETE草稿已提交204但响应丢失；同key重试时原行不存在，仍须先做当前范围授权，再回放204；后来资格取消必须拒绝 | http_idempotency只有响应和摘要，缺独立原目标范围事实，无法明确实现删除后重放的当前授权 | 新增内部authorization_basis，完成时CHECK要求JSON对象；与删除/安全响应同事务固定，完成记录触发器拒绝UPDATE。T0列明当前单项覆盖、全局操作策略、401/403及通用错误白名单，不依赖已删除行、不重比已推进目标版本。关闭：C09～12有限表达式推演；非引擎/授权行为测试 |
| CR-04 / P2 | 某员工同来源有101项适用任职，基线能返回完整集合，提交又必须固定全部依据 | GrantLine.employmentIds误用maxItems=100，形成无法提交的合法状态；这不是100条授予目标的限制 | 仅删除此字段任意100上限，保留非空/唯一和全部集合一致性，授予目标items仍最多100，不剪裁任职。关闭：旧契约拒绝/新契约接受101依据，重复/空集合仍拒绝C01～03 |
| CR-05 / P2 | OA回调改变申请状态但无Idempotency头，业务授权查询/预览也使用POST；DB幂等主体FK只指后台账号 | 公共约定“所有改变业务状态”与精确参数/独立OA防重边界冲突；部分GET/预览/出站追踪误挂HTTP幂等或正式写事务 | 公共约定、T0及68项追踪明确管理员HTTP幂等、只读POST、OA三层防重、出站requestId；GET不执行领域写事务，预览只写快照。关闭：15项无幂等键写方法例外、全部追踪及OA仅OaJwt精确检查 |
| CR-06 / P2 | 后续实现按字段映射查找OaReceiveResult/PermissionCheckResult | 两个名称不在权威components.schemas中，交接无法精确对应 | 更正为OaReceipt/CheckResult；所有字段映射中模型名称与129个Schema交叉检查。关闭：静态映射检查 |

P1表示会造成权限泄露、合法业务被拒绝或安全恢复协议缺失的设计问题；P2表示可表达性、入口边界或交接名称错误。没有将设计缺陷说成已运行系统故障。

## 跨阶段场景矩阵

以下均为**正式文档核对与设计推演**，并发的两个顺序不是实际线程测试。67个入站及1个出站操作的完整追踪仍见[TRACEABILITY.md](TRACEABILITY.md)，这里补充不能靠一对一表映射证明的关键情境。

| 场景 | UC / API入口或资源 | 数据库/事务、不变量及失败结果 | 评审结论 |
| --- | --- | --- | --- |
| S01 稳定标识、编码、源用户名和组织更名 | UC-10；precheckImport/ImportPlanChange/fieldChanges | employee_source_identity、organization_mapping、冻结候选ID/code；T8/I-01～03/21；提交唯一冲突全批STALE，不按姓名归并 | 对齐 |
| S02 新FUNCTION与操作同批创建，无环/同系统/绑定 | UC-01；saveCatalog、本地引用 | 复合FK/生成进入槽；T1停用新节点→操作→绑定→启用；完整最终树检测，任何失败回滚 | 对齐；即时FK执行待真实引擎 |
| S03 目录、操作停用/再启用及稳定排序 | UC-01/05/06；updateSystem/saveCatalog/authorizationQuery/permissionCheck | 不结束grant或周期；T1/T11按有效祖先过滤，sortOrder/code/id；独立操作无继承 | 对齐；CR-01已修订 |
| S04 公司级共享角色只总管理员维护；部门角色归属 | UC-02/07；createRole/updateChangeDraft/Scope | role归属复合FK，T2/T4当前办理及固定原范围，不能拼接多任命 | 对齐 |
| S05 配置新增/保留/移除停用权限，非空与无变化 | UC-02；RoleConfiguration/changePreview/submitChange | I-07/T3/T4按固定基础差集判定；空422，无差异200不投OA，执行后历史+版本 | CR-01关闭 |
| S06 全部适用任职依据与任职非自动授权 | UC-03；GrantBaseline/GrantLine | T3固定完整证据，T4当前仍有适用任职即可，证据历史与固定申请不改写；不按岗位自动grant | CR-04关闭 |
| S07 同来源去重与不同来源并存 | UC-03/04；GrantKey/Grant/SourceBaseline | source四元UQ、baseline二元UQ、ACTIVE槽UQ；T3/T4同来源已有ALREADY_GRANTED、其他部门可并存 | 对齐 |
| S08 来源零基线、关权与授权执行竞态 | UC-04/11；sourceBaselines/closeSource/receiveOaResult | T0锚点/T5先物化0再比较；执行先→随后关权，关权先→旧申请整份失效；epoch持久推进 | 对齐；真实并发未测 |
| S09 撤销后重授及迟到旧批准 | UC-04/11；RoleRevocation/Grant | 永久baseline版本不重置、新grantId；旧撤销只针对准确旧ID，批准后不能裁剪；T4/T5 | 对齐 |
| S10 无变化、版本冲突、预览陈旧顺序 | UC-02～04；changePreview/submitChange | T3全部身份/范围→去重→准确关系无变化分类→仍需目标版本→预览依赖一致；已授予/已结束不机械冲突；提交批准后不裁剪 | 对齐 |
| S11 原申请人离任与原范围实质缩小 | UC-07/11；Scope/OriginalAuthority | T6实际失覆盖持久INVALIDATED，名单换人/原账号停用不机械失效；以后扩回也不复活；T4原范围全目标覆盖 | 对齐 |
| S12 失去资格草稿与占用清理 | UC-02～04/07；getChangeRequest/updateChangeDraft/deleteChangeDraft | 草稿保留DRAFT，当前范围外由总管理员用现有入口修复/删除；无伪submittedVersion、无自动裁剪/过期；占用冲突不泄露隐藏申请ID | H-01设计处置明确，见下方 |
| S13 固定OA内容不可变 | UC-11；ChangeRequest/OaSubmission | T3固定RFC8785字节和摘要包，change_target固定投影；提交后触发器禁止修改，当前更名只影响当前展示，不改批准内容 | 对齐；规范化库验证留实现 |
| S14 OA投递超时、关联滞后、迟到最终结果 | UC-11；retryOaDelivery/oaSubmission/receiveOaResult | T10无网络事务、同requestId重投；关联未落地409不消耗事件；INVALIDATED只补历史不复活；不同事件同决定重复/反决定409 | 对齐；真实OA能力待核实 |
| S15 一申请一次成功，业务/审计存储失败 | UC-11；OaReceipt/执行状态 | execution_success请求PK＋不可变最终结果；T4全目标先验再全写，失败回滚后另事务复核未成功/未失效才记失败，重试不覆盖成功 | 对齐；真实故障注入未测 |
| S16 管理员HTTP重试、同key异内容、删除后重放 | UC-02/09等；Idempotency参数/DELETE草稿 | T0摘要预留＋栅栏＋授权事实＋安全响应原子完成；当前资格先校验、目标版本后；过期接管同内容，401/403不返回缓存 | CR-03/05关闭 |
| S17 全局员工停用/恢复与授权并发 | UC-10/11；setEmployeeState | T7同事务终结所有旧grant/任命、推进来源/会话；恢复只ACTIVE；与T4锚点确定先后、无部分提交 | 对齐 |
| S18 同批离岗后新岗、始终有兼岗、岗位更名 | UC-10/11；冻结ImportPlanChange | T8顺序执行D-033明确事件；最后适用任职结束即推进，不按最终岗位存在掩盖；其他适用任职始终存在则保留；全批原子 | 对齐，未重新访谈 |
| S19 预检冻结、规范前后值与候选唯一冲突 | UC-10；ImportInput/ImportBatch/listImportPlanChanges | 原文件SHA、映射/顺序/前后值/依赖冻结，预检不创建正式组织；提交完整重查，不重新解释文件或静默换code | 对齐 |
| S20 第N行失败、1000行原子批量与恢复 | UC-10；commitImport/ImportCounts | T8一次本地事务全部业务/失效/审计；失败全回滚，另事务只记失败状态；已成功批次回原counts，不分批、不裁剪 | 对齐；真实回滚未测 |
| S21 普通员工不登录；账号停用与员工停用分离 | UC-07/09/10；Account/Session/Appointment | T9当前会话/账号/员工/至少一资格；密码版本/sessionEpoch即时依据，JWT不作当前资格替代；账号停用不结束业务grant | 对齐 |
| S22 任命取消/范围变化后的旧JWT | UC-07/09；myAppointments/上下文版本头 | T6/T9写锁内最终复核、失全部资格推进sessionEpoch，恢复不降；主库新读事务，无延迟副本/缓存兜底授权 | 对齐；JWT实现未测 |
| S23 系统进入与独立功能并集 | UC-05/06；AuthorizationResult/CheckResult | T11同公司/系统完整有效来源并集；进入资格可来自另一角色，菜单查看与编辑独立；无数据权限/直接用户权限表 | 对齐 |
| S24 敏感影响分页/计数/审计/关闭响应 | UC-04/08；ImpactPreview/listPreviewTargets/getAudit/SourceCloseResult | T11同快照与当前资格交集，混合固定申请不拆，查询结果键集检测新增影响；T5响应安全投影不影响完整执行 | CR-02关闭；真实越权测试待实现 |
| S25 历史、删除、保留、版本与JPA | 全UC；各version及历史资源 | I-19/21、T12；不一律软删除，明确限制FK和草稿删除次序；API版本按业务含义，row_version单独@Version；成功凭据/周期不因过期清理丢失 | 对齐；JPA/运维参数待固定 |

## 原有待办的正式处置

- **H-01：设计边界关闭，界面提示留详细设计。** 不新增草稿失效状态或自动释放规则。权限管理员遇到不可处理占用仅收通用RELATION_OCCUPIED提示，不能借提示读取不完整范围申请；总管理员按现有列表及单份查看入口取得当前draftVersion，通过现有编辑/删除处理。删除和占用释放/审计/授权快照同事务。后续详细设计给出“联系总管理员处理”的现有错误展示；不是新增业务模块，原型本轮不改。
- **H-02～05：仍为交接待办，不伪关闭。** 真实OA按固定requestId幂等、文件/审计保留与备份RPO/RTO、真实MySQL/JPA/版本验证、原始清单映射均仍未完成；通用设计可以继续，不自动生成猜测授权。
- **Apifox：本次1.2.0尚未导入。** 1.1.0指定客户端导出通过的事实保留，但不能扩展为1.2.0或MCP完全一致。已更新本地完整两路径覆盖配置的版本，不能手改既有导出证据。同步只需针对本次契约差异按既定流程操作，无需重查旧工具问题。

## 契约与数据演进

API路径、方法、认证、67入站＋1出站操作及129模型保持；未上线版本从1.1.0修订为1.2.0。GrantLine放宽任职依据数量，仍要求全部集合、非空唯一及目标上限100；SourceCloseResult增加两个必填响应字段，并明确计数/审计ID可见性。已有严格additionalProperties=false的旧客户端需更新模型及响应处理，不能声称响应完全兼容；服务端与客户端从Git1.2.0统一生成/校验，不升级URL主版本。正式需求未变化，无需重生成需求图或修改阅读副本。

数据库增加一个内部JSON授权依据列及完成记录不可变UPDATE触发器，无新增业务实体/关系。当前没有已部署数据库，不创建迁移工程或执行ALTER；未来若部署过旧草案，迁移前须清理过期HTTP幂等窗口，仍在窗口的旧COMPLETED结果缺授权依据时不得臆造或授权重放，应暂停升级并制定受控保留/窗口迁移，不直接对业务库执行本草案。主键/外键/长期成功防重及来源周期模型不变。

## 验证与下一阶段

实际命令、数量、摘要及边界见[VALIDATION.md](VALIDATION.md)、[consistency-validation.json](consistency-validation.json)。新回归脚本从Git e3fa818读取原始反例，做8项JSON Schema场景及4项有限CHECK表达式推演，并检查精确幂等入口、模型名称、操作覆盖及本地覆盖配置。它不验证应用授权、SQL事务调度、隐藏计数投影、外部OA或JPA。

下一步先按既定配置人工更新Apifox1.2.0并保留新导出核对证据；详细设计以Git1.2.0和数据库1.1草案分解模块、查询授权、事务调用及恢复职责，转化S01～25为后续验收/TDD清单。此轮不进入详细设计或正式实现；真实MySQL、并发、JPA、OA、性能测试仍须后续授权与实施。
