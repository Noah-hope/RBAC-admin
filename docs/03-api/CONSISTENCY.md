# 状态、权限边界与失败恢复

## 固定申请的三条状态轴

| state | deliveryStatus | approvalResult | executionStatus | 当前有效授权 |
| --- | --- | --- | --- | --- |
| DRAFT | NOT_SUBMITTED | NONE | NOT_STARTED | 不变，新角色不可分配 |
| PENDING | PENDING_SEND / SEND_FAILED / LINKED | NONE | NOT_STARTED | 不变 |
| REJECTED | LINKED | REJECTED | NOT_STARTED | 不变，释放占用 |
| APPROVED | LINKED | APPROVED | PENDING | 不变，可恢复自动执行 |
| EXECUTION_FAILED | LINKED | APPROVED | FAILED | 全批回滚，仍占用通道、服务端重试 |
| EXECUTED | LINKED | APPROVED | SUCCEEDED | 业务变更、版本、审计已一起提交 |
| INVALIDATED | 保留已知投递状态 | 保留 NONE/APPROVED/REJECTED | INVALIDATED | 不执行，释放占用；迟到可信最终结果可审计但不复活 |

`state` 是受约束的聚合展示状态，不是第四套可独立修改字段。无修改审批/执行状态的管理员接口；不提供 WITHDRAWN、本地取消、超时自动取消、接手、手动批准或强制执行。旧角色待审批仍使用旧配置；新角色只在首次配置执行成功后 EFFECTIVE。配置不得提交空集合，草稿允许空集合。

1.1.0将可静态验证的组合写入条件Schema：DRAFT的提交版本/时间/摘要/原依据为空；所有已提交状态都有正submittedVersion及固定依据；LINKED必须有oaApprovalId；APPROVED/EXECUTED/EXECUTION_FAILED分别匹配PENDING/SUCCEEDED/FAILED执行状态。`retryable`仅表示服务端可继续执行（APPROVED或EXECUTION_FAILED），不是投递重试资格。INVALIDATED收到首次可信驳回可保留REJECTED而仍不执行，不能将它改回可办理状态。

草稿创建/编辑校验当前范围。配置草稿从创建起占用该角色唯一变更通道；分配/撤销草稿保存实际目标并取得关系变更占用，同一关系不能并行办理未结束新增/撤销。无变化明细不单独占用。草稿删除、REJECTED、INVALIDATED、EXECUTED 原子释放。等待 OA 不持有数据库事务；通过持久占用约束控制重复办理。错误草稿可编辑修复或删除，不要求发送 OA。

预览返回 `effectiveContent` 和 `noChangeItems`，按输入零起始 index 对照。提交再次核对预览、目标版本和范围：若预览后任何会影响实际清单/影响的依赖已变，409 PREVIEW_STALE，不静默重新计算并提交。仅在管理员看到的实际清单上固定内容；全部无变化 200，不发送 OA。部分无变化不是“部分执行”：只有提交前显式剔除，余下固定清单全部审批、全部执行。

全部无变化的预览 `effectiveContent=null`，不返回违反最小明细数量的空授予/撤销申请；提交返回NO_CHANGE及保留的原草稿。空角色配置仍是不可提交的VALIDATION_ERROR/EMPTY_CONFIGURATION，不借null候选绕过非空要求。

无变化判定的优先级：先认证并检查全部输入目标及其固定来源是否有权访问，未知/越权项不能借“无变化”被忽略；再定位稳定关系键或准确grantId。对于同来源已存在有效授权的新增，只作为ALREADY_GRANTED，不用旧角色配置版本重新改变既有授权；对于准确旧grantId已REVOKED/INVALIDATED的撤销，作为ALREADY_ENDED，不因结束导致的版本推进再制造撤销。仍需新增的项必须匹配角色版本、来源周期、关系基线；仍ACTIVE但版本陈旧的撤销409，不能用旧ID去撤重授的新ID。返回可见无变化明细后再冻结实际清单。

上述分类发生于草稿预览/提交前；若预览之后任一分类或实际清单变化，提交必须409 PREVIEW_STALE，让用户重新预览，而不是暗中改变其确认内容。批准后的整批任一项失效仍整批INVALIDATED；执行器不使用提交前的无变化规则截取子集。RoleGrant/RoleRevocation中相同业务键重复输入先拒绝422，不能通过去重掩盖冲突字段。

## 版本职责及执行复核

| 版本 | 提交/执行含义 |
| --- | --- |
| metadataVersion | 角色名称说明；改名不单独使旧权限方案失效 |
| configurationVersion | 当前有效角色权限配置；配置/分配需精确匹配；一般撤销不依赖它 |
| catalogVersion / 对象 version | 目录聚合和节点状态；预览/目录保存匹配聚合，执行配置/授予复核当前权限有效性和归属，不因无关名称/排序变化机械失效 |
| draftVersion | 草稿内容协作修改；提交后冻结，不允许原地改内容 |
| submittedVersion / contentDigest | OA 审批的固定版本与摘要；与 statusVersion 分开，不因投递/失败重试改变 |
| statusVersion | 管理员投递重试的状态并发；不会改变批准内容 |
| scopeVersion / appointmentVersion | 提交保留原范围与任命依据；人工请求按当前任命/范围，自动执行按原范围对应当前覆盖条件，不要求原人继续任职 |
| sourceEpoch | 员工＋来源公司＋来源部门＋系统的关权周期；无有效关系也存在逻辑基线，旧授予必须匹配 |
| relationVersion | 员工＋角色＋来源公司＋部门的一次授权演进；结束后不得重置成 0；重新授予建立新关系ID并推进版本 |

`originalAuthority` 仅后端产生：GLOBAL 保存提交总管理员身份及目标公司；SCOPE 保存 scopeId/version、appointmentId/version 和提交时公司/部门/系统快照。不能通过 body 伪造或用当前查看人的更大范围替换。

自动执行时，原 scopeId 必须仍存在，当前公司/指定部门须与原目标匹配，当前 systems 必须覆盖全部原固定目标。单纯版本增加、增加一个不相关系统、管理员名单变化不自动使申请失效；实际缩小导致目标不再覆盖才 INVALIDATED。范围曾移走/缩小导致申请已失效后，后来恢复也不复活；范围变更事务应识别并持久标记受影响申请。GLOBAL 不受权限管理员系统清单限制。原申请人离任/账号停用/公司暂时无人不单独阻断合法已提交申请，但当前人工操作仍要具备资格。

| 类型 | 必须复核 | 不得机械要求 |
| --- | --- | --- |
| ROLE_CONFIGURATION | 原范围、归属、基础配置版本、唯一占用；新增权限存在且有效/同系统，移除已停用权限合法；最终非空 | 原申请人仍任职，角色名未变，员工全部可见 |
| ROLE_GRANT | 原范围、员工有效、角色已生效/配置版本、当前来源部门适用任职、固定来源周期/关系版本、无重复有效关系 | 同角色必须提供进入与功能、岗位名未改、所有员工角色来自单一部门 |
| ROLE_REVOCATION | 原范围、准确 grantId、仍有效的指定来源及 relationVersion | 员工仍在原岗位、当前满足授予条件、角色配置版本未变、系统必须启用 |

提交的 employmentIds 保存当时全部适用任职依据。执行时检查该固定员工/公司/来源部门当前仍有适用任职，允许仍有其他适用兼岗；若来源曾失去全部适用任职，则周期已推进，后来新增/返回也不能通过旧批准。岗位改名不单独影响资格。不能只检查某个任职 ID 结束就把全部兼岗关掉，也不能执行时改写来源部门。

## 来源关闭、全局停用和并发顺序

来源关闭事务：复核选择的一项任命覆盖全部来源及系统 → 锁定/条件比较来源 version/epoch（包括不存在时的逻辑 0 基线）→ 推进周期 → 失效该来源有效关系并推进关系版本 → 旧待分配申请任一明细受影响则整份 INVALIDATED → 保存关闭事件/成功审计。同 key 重试回原结果；不同新 key 明确新关权事件再次推进周期，不把“当前无有效关系”当无需关权。重复旧基线则 409。

并发与执行确定本地先后：执行先提交，则关闭失效刚新增关系；关闭先提交，则旧授予因周期变化整批失效。一般撤销若目标在并发关权后失效，原批准清单不再有意义，整批失效，不部分撤销。撤销后重授新 grantId，旧撤销仅回放原成功或因版本失效，不能误伤新关系。

员工停用是全局直接管理事务：身份状态＋所有有效来源/关系和任命持久失效＋来源周期/版本＋后台会话失效＋成功审计。恢复只恢复 ACTIVE，不恢复旧关系/任命/会话，不能依靠查询时过滤实现，否则恢复会复活旧授权。页面和 HR 批次共同执行防自锁：禁止全局停用关联当前/唯一总管理员身份的员工；总管理员维护恢复另走部署流程。账号停用只影响后台凭据与会话，不直接结束员工授权或删除任命。

## 员工影响与目录规则（R-01～R-03）

所有预览 `previewId` 5 分钟有效，绑定规范输入、版本、依赖基线、当前管理范围和影响快照。确认请求必须传相同内容和 previewId；过期、内容不一致或依赖变化均 409。预览不是授权凭据，也不保证未来审批执行时条件不变。

`ImpactSummary`按可见范围分别返回员工、角色、关系、来源、任命、待执行申请计数；具体对象通过`GET /admin/previews/{previewId}/targets`分页返回，包含类型、稳定标识（尚无关系的SOURCE用完整来源键）、目标版本及拟影响。系统/目录预览可展示受影响角色，即使尚无员工持有；来源关闭可展示旧申请，即使当时没有生效关系；范围/员工停用可展示任命与已提交申请。它们与员工明细使用同一个快照及当前资格复核，不能在分页时改成即时重新计算的另一批结果。

统计仅计当前可见目标、按类型和稳定键去重，不泄露范围外数量。混合部门/系统的整份固定申请若不能被当前一项任命全部覆盖，不返回其ID/内容/计数；可以对可见来源返回不完整影响提示，不能声称全局零影响。预览返回的每个目标仍需按当前身份检查，列表及计数遵守相同边界。预览过期或权限缩小后不能凭旧previewId读取原明细。

角色配置影响分为：权限配置新增/移除、可见员工当前是否因其他有效来源仍可进入、真正失去进入资格、重新获得进入后旧功能可能恢复。后端计算使用员工完整同公司/系统有效来源，不用权限管理员可见子集替代真实结果；响应只给可见员工与汇总，隐藏其他部门角色、关系、姓名及人数。`visibility=SCOPED` 时各 count 均仅可见部分；`hasUndisclosedImpact` 仅提示不完整，不返回范围外数量。`ImpactEmployee` 可给该可见员工的 true/false 结论，`visibleGrantIds` 只含有权来源，`resultCompleteness=REDACTED` 表示解释明细不完整。不得根据“没看到其他来源”断言员工必然无权。

批量一般撤销以 grantId/version 明确选择，一申请一个公司，多个员工/系统可组合；总管理员可包含该公司多个部门；权限管理员一项任命必须覆盖全部来源。整体拒绝越权/冲突，提交前已结束关系作为无变化排除，批准后不剔除任何明细。系统停用仍允许撤销有效关联，旧历史只读。

目录同父级按 sortOrder、code、id 稳定升序，根节点同样规则；负数/并列允许，操作排序不产生权限继承。NAVIGATION 不绑定 visibilityPermissionId；FUNCTION 启用前须绑定本功能有效 `menuEligible=true` 的查看/进入操作。系统进入权限 menuEligible=false/functionId=null，不可当页面可见条件。menuEligible 由总管理员明确选择查看/进入语义，不以编码含 view 自动推断。停用父目录只影响计算，不批量改变子节点状态；恢复不复活已撤销关系。

批量目录本地引用：新节点/权限 localKey 必须本批唯一；parentId 与 parentLocalKey 至多一个非 null，均 null 表示根；visibilityPermissionId 与 visibilityPermissionLocalKey 同样互斥；权限创建的 functionId/functionLocalKey 恰一非 null。新 FUNCTION 与其操作可通过本地引用在同一事务建立、校验循环与菜单绑定。NODE_UPDATE 不改 code/nodeType/systemId，PERMISSION_UPDATE 不改 code/function/type；新增 SYSTEM_ACCESS 只由系统登记产生。重复 kind+id 更新或重复 localKey 拒绝，不按数组顺序覆盖。目录/系统无变化应 200 NO_CHANGE（系统返回对象，可通过版本未变识别），不得声称创建了新目录变更。

## OA 投递、回调与恢复

出站 `webhooks.oaSubmission` 是权限中心到配置的 OA 适配器契约，不是管理员入口；审批人和流转由外部 OA 决定。固定 requestId 是外部投递业务幂等键，内容/版本摘要相同必须返回同一 oaApprovalId；不同内容拒绝。网络超时可能已受理，应重投同一固定申请核对关联，不能创建第二审批单。

摘要采用 UTF-8 JSON 的 [RFC 8785 规范化](https://www.rfc-editor.org/rfc/rfc8785) 后 SHA-256；摘要包为 `{requestId,submittedVersion,content,reason,originalAuthority,submittedByAccountId,submittedAt}`。集合型 ID 数组和明细按稳定业务键排序、去重后冻结，OA 直接回显平台提供的摘要，不自行猜测。审批编号、最终结果和审计不进入该固定内容摘要。示例摘要只是合成占位，不代表可执行批准。

本期 OA 适配器必须支持同 requestId 幂等受理和最终回调重试；实际 OA 不支持时由适配器维护关联。关联建立前抢先回调返回 409 OA_LINK_PENDING+Retry-After，适配器重试；不能仅凭回调自报审批号绑定未知申请。收到合法最终结果先独立事务保存接收事件与批准/驳回，再 202，触发自动执行；HTTP 202 绝不代表授权生效。未可靠保存返回 503，让 OA 重投同事件；平台也要有批准记录的恢复扫描，不能依赖 OA 再审批。

人工投递重试和后台发送器均仅处理PENDING且未有最终结果的申请，人工入口额外要求SEND_FAILED/statusVersion匹配。INVALIDATED保留SEND_FAILED只是历史，不构成可重试资格。领取待发送任务及最终发送前复核可投递状态；与关权/范围失效竞争时按本地事务决定先后，不把失效状态改回PENDING。已经在途、外部可能已经受理的请求不能承诺被网络撤回；关联恢复可记录事实，但不会启动新审批或业务执行，后续回调按失效历史处理。超时后仍用原requestId向适配器核对，不换业务键。

| 回调情况 | 结果 |
| --- | --- |
| 同 OA 实例/eventId，相同规范 payload | 200 duplicate=true，返回当前执行状态，不重新执行业务 |
| 同 eventId，不同 payload（包括不同申请） | 409 CALLBACK_CONFLICT，不覆盖 |
| 不同 eventId，同申请/审批/版本/摘要/decision | 200 duplicate=true，原最终结果和执行唯一，新投递可记诊断事件 |
| 同申请最终批准后驳回/驳回后批准 | 409 CALLBACK_CONFLICT，时间更晚不覆盖 |
| requestId/oaApprovalId/摘要/版本不匹配 | 409 CONTENT_MISMATCH，无新授权 |
| DRAFT/未关联/中间结果 | 409 STATE_CONFLICT / OA_LINK_PENDING；isFinal=false 为 422 |
| 已因合法条件 INVALIDATED 的申请收到匹配旧结果 | 可可靠记录最终结果并返回 INVALIDATED，绝不执行或复活；矛盾最终结果仍拒绝 |
| 已 EXECUTED 的合法重复批准 | 返回历史接收和当前成功结果，无第二次授权成功审计 |

身份失败先 401/403，不泄露申请细节。OA事件ID防重、申请最终结果一致性、一次成功执行是三层独立约束，不因 HTTP 幂等键过期而丢失。

执行器尝试在本地事务复核并提交全部批准内容＋目标版本＋执行状态＋成功审计。一项不合法整份 INVALIDATED；短暂数据库/写入故障整批回滚，另行可靠保存 EXECUTION_FAILED、失败码及 nextRetryAt。若数据库完全不可用，保持先前可靠 APPROVED/PENDING，服务诊断记录错误，不能声称失败审计已写入。

恢复建议：进程启动及每 30 秒扫描 APPROVED/可重试 EXECUTION_FAILED，指数退避 5秒起、上限5分钟，不会因达到某个重试次数而自动取消申请；并发领取必须受控。每次重试都重新校验业务条件，长期故障告警是运行事项。具体线程/锁/调度实现留详细设计。管理员只有投递失败重试接口，没有执行重试/强行失效入口。成功后回包丢失只回读原结果。

## HR 规范化与原子批次

正式文件输入支持 CSV 和 XLSX，不沿用原型六列表/128KB 限制。文件上传和预检独立，预检不改正式组织/员工/授权。CSV 的 worksheet 固定 `CSV`；XLSX 明确工作表，禁止默选含糊页；宏拒绝，公式单元格不执行，映射至业务字段的公式需转换为明确值后再导入。一个源列只映射一个字段，同一目标字段不得重复映射。

`ORGANIZATION`：action 允许 CREATE_COMPANY、RENAME_COMPANY、CREATE_DEPARTMENT、RENAME_DEPARTMENT、MOVE_DEPARTMENT、CREATE_POSITION、RENAME_POSITION；根据动作明确 code/name、归属及 expectedVersion，缺失编码的首次资料通过 organizationMappings 明确建立并冻结稳定映射，不从简称猜测。已有引用部门跨公司迁移整体阻断；不提供部门 parent/status 字段。

`EMPLOYEE_INITIALIZATION`：至少 employeeKey/name/companyCode/departmentCode/positionCode；initialEmploymentState=EXPLICIT_ACTIVE 明确当前有效、不伪造起始日期；FROM_SOURCE 时必须映射明确状态动作。按源唯一用户名建稳定 employeeId；相同任职去重报告，矛盾员工级字段阻断。既有员工不能因初始化选项重新启用或复活已结束任职，需明确变更动作。

`PERSONNEL_CHANGES`：action 允许 CREATE_EMPLOYEE、RENAME_EMPLOYEE、REMAP_EMPLOYEE_KEY、ADD_EMPLOYMENT、END_EMPLOYMENT、DISABLE_EMPLOYEE、RESTORE_EMPLOYEE。新增任职明确员工、公司/部门/岗位编码；结束任职明确 employmentId/expectedVersion；改名和启停明确员工标识/版本；重映射明确 previousEmployeeKey→employeeKey（同源映射依据），不得按姓名自动合并。多项动作按源行号关联成同一冻结原子批次，不从文件缺行推断任何结束/删除。岗位更名不自动授权或关权。

预检把文件SHA、映射、规范行、相关目标版本和依赖检查冻结成 batch/version/contentDigest，返回 plannedCounts、问题列表；READY 才可提交。修改文件/映射要新 sourceBatchId 创建新预检，旧批次不原地编辑。版本陈旧标 STALE，重新预检；可重试 FAILED 保留原内容和基线，重试仍复核。成功后相同批次返回原 actualCounts；新批次相同资料比对为 unchanged，而不是再生员工/任职。批量中含停用或结束任职时，人员资料变更、相应来源/授权/任命持久失效及审计全部同事务。原始文件和问题报告仅总管理员访问，保存期限留运维设计，不开放删除历史接口。

`listImportPlanChanges`的每行除摘要外，还返回companyCode/departmentCode/positionCode及fieldChanges（明确字段及前后值），不能只用一句summary代替规范化变更。首次无编码的组织按已确认完整来源路径映射，在预检分配候选稳定编码并在此明细回读；预检不创建正式组织，提交必须检查候选编码仍未冲突，否则STALE并重新预检。既有对象通过ID/code映射更名，不重新分配编码；组织迁移、员工标识重映射和结束任职均保留冻结前后值和目标版本，不因新姓名自动归并。

## 关键场景核对（设计断言，非实现测试）

| 场景 | 契约预期 |
| --- | --- |
| 两名员工、两个角色，仅选三对 | 固定三条，不补第四条 |
| 批次第100项越权/陈旧/执行写入失败 | 整体拒绝/失效/回滚，不提交99条 |
| 相同员工/角色 A、B 来源，关 A | A失效、B保留，功能去重 |
| A仅有待分配申请即关权，后返回A | epoch变化，旧批准不能执行 |
| 原申请人离任，原范围仍覆盖 | 自动执行不要求接手 |
| 原范围缩小，当前查看人为总管理员 | 仍按原范围，非法申请失效 |
| 角色配置变更后撤销既有指定关系 | 不机械检查角色配置版本，查 grantId/version |
| 最后进入来源移除 | 功能暂不可用但关联保留；新进入来源可恢复仍有效功能 |
| 仅编辑、有进入、无查看 | 编辑检查true，菜单不凭编辑出现 |
| 普通员工拿业务进入角色登录后台 | 统一登录失败 |
| 全局停用后恢复、后台账号停用再启用 | 前者不复授权/任命，后者不复旧会话 |
| 目录排序并列，部门平级 | 同父级排序稳定，目录父子规则保留 |

## 数据库阶段单点澄清（2026-10-17，Q-01）

按用户对“离岗后新岗就职”的澄清，UC-10明确END_EMPLOYMENT结束来源部门最后适用任职后，同一原子批次随后ADD_EMPLOYMENT也不复活旧来源授权/任命/申请；预检冻结事件顺序，提交全批原子，并持久推进周期。始终存在其他适用兼岗或仅岗位更名不关闭整个来源。字段/路径/响应Schema仍为Git1.1.0，未改openapi.yaml或Apifox覆盖配置；本条是既有明确动作与来源周期规则的场景细化，未宣称Apifox说明已更新。物理方案见[数据库事务T7/T8](../04-database/TRANSACTIONS.md)。
