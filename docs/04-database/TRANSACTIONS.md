# 状态、版本、事务与失败恢复

2026-10-18/D-045实施补充：T12的基础UUID/专用row_version、UTC微秒及生成槽映射已有隔离MySQL样例，缺guard启动检查已实现，见[持久化验证](../05-detailed-design/BE-03-PERSISTENCE.md)。这不代表T01～T11业务事务、所有实体或完整并发已实现；下文原设计期“尚未真实测试”仅为历史。

这是数据库设计协议与并发推演，不是已实现的服务流程或测试通过报告。所有本地业务状态写入必须按本协议；等待OA、网络投递、密码慢哈希与文件解析不占用写事务。业务批量最多100目标，导入最多1000源行；这些上限沿用API，不是性能保证。

## T0：统一锁与提交协议

一期选取单写锚点`mutation_guard(id=1)`，不是分布式应用内存锁。初始化只建立这一锚点；丢失锚点报完整性错误，不能静默插入新锚点绕开正在运行的事务。

所有修改正式状态、草稿/占用、来源/基线、身份/资格、OA关联/结果、执行结果、导入提交及预览清理的事务遵循：

1. 开启本地READ COMMITTED事务，首先`SELECT id FROM mutation_guard WHERE id=1 FOR UPDATE`，要求恰一行。统一锁等待超时建议5秒，超时回滚并返回503可重试或后台退避，不伪装业务无变化。
2. 当前读复核服务身份，或后台账号/员工/会话/当前总管理员或所选任命、appointmentVersion与scopeVersion。读请求开始时的认证只作提前拒绝，不能作为写入最终依据。
3. 有HTTP幂等键的请求按主体/方法/规范路径/key查记录。已完成且摘要相同，经当前范围检查后回放原安全结果，**不再用已被原请求推进的目标版本制造冲突**；不同摘要409；已有未过期租约409 REQUEST_IN_PROGRESS/Retry-After。新键在短T0事务中先可靠插入IN_PROGRESS、受保护摘要和随机leaseToken/leaseUntil，提交这份内容预留；随后执行业务的事务重新从步骤1/2开始，并检查同一leaseToken。过期预留仅同摘要可换新token接管；旧处理者提交前token不符则停止，不覆盖新处理者。
4. 取得同key预留处理资格后锁目标行。顺序固定：账号/员工ID升序→范围/任命ID升序→公司/部门/岗位ID升序→系统/目录ID升序→角色ID升序→来源四元键→baseline键→grantId→申请ID→批次ID。锚点使所有遵循协议的写者不可能交叉持业务锁；排序便于将来拆锁审查及缩小意外死锁面。
5. 锁后重新读取依赖，校验预览/版本/合法性，执行整个清单，写审计和成功结果，完成幂等记录，递增guard.revision（只内部用途），一起COMMIT。客户端收到响应不参与提交判定。
6. 异常ROLLBACK业务事务，可靠预留仍保留内容摘要。确定的业务4xx结果在后续短T0事务确认同token且无已提交结果后保存COMPLETED错误回放；暂时存储故障不永久缓存503，而是保持同内容预留并到期/释放租约供重试。进程在预留后中断，租约到期同摘要恢复；若业务已提交，COMPLETED响应与业务原子可见。禁止失败记录覆盖已完成结果，不能捕获唯一键异常后在同一事务跳过坏项。

幂等预留只固定请求内容，不提前提交业务成功结果。摘要包括规范业务body中的目标/expectedVersion、具有业务意义的查询参数及方法/规范路径；JWT、HTTP关联号和任命/范围上下文头每次独立验证，不混入业务内容摘要使换新会话后无法回放。租约初值建议60秒，业务事务持锚点期间不会被别的处理者接管；长解析放在外面，重新进入T0时若已失租必须停止。响应/Location/安全响应头与COMPLETED同业务事务保存，清空租约，expiresAt至少延长到完成后7天。HMAC按记录digest_key_id比较，轮换时须在窗口内保留旧密钥验证能力，密钥不入库。相同key处理失败后换内容仍冲突，不能因失败回滚遗忘其内容预留。

完成记录同时固定`authorization_basis`对象：`operationId`、`policy`（GLOBAL_ONLY或SCOPED）、完整原目标`companyId/departmentId/systemId`元组集合、完整申请查看所需的目标集合，以及已缓存响应可见对象的范围事实；均由服务端授权成功的锁内读取生成，不接受客户端声明。草稿删除先取全份范围事实再删除，204也保存安全空响应及这些事实。重放不依赖已删除行：先验证当前会话/操作身份，再以**当前所选一项任命**完整覆盖固定事实，任命/范围版本头检查当前值，不能拼接、不强求原任命ID；全局专属操作仍须当前总管理员。失去任意必要覆盖则403且不回放正文、Location或审计ID；身份失效401。完成记录触发器拒绝改写依据和结果，保留期到期后按窗口清理，不参与长期业务成功去重。只含通用错误且不含目标资料的4xx可记录policy=PRINCIPAL_ONLY及空目标，但仅允许已通过当前操作入口资格、经过明确安全错误白名单的响应，不能用于成功/敏感响应；未通过当前认证/范围的错误不写入可回放结果。CHECK只检查完成时非空JSON对象，具体策略/完整投影由上述事务核对。

本表仅用于OpenAPI明确声明Idempotency参数的管理员操作；OA事件及只读POST不使用它，防重边界与API公共约定一致。

来源关闭的授权依据记录输入来源及缓存中实际可见结果的完整范围；只有缓存披露整份申请时才加入其完整查看范围。命中的不可披露混合申请仍按T5完整失效，不能把其范围变成调用者原本不需要的办理资格，使合法关权重试反而越权。任何内部范围事实均不对外返回。

锚点在全体写事务提交前一直持有，资格取消/关权不能在别的写事务“最后复核”后插进来。后台发送器和执行器、恢复扫描、HR导入、部署维护都必须参与；绕过此约定直接SQL写入不受业务一致性承诺。运行账号不授予DDL/触发器删除权限，迁移使用独立身份。这里只提出部署约束，不生成真实账号。

该选择牺牲写并发，但不在OA等待期间占锁，也不给不同员工或不同来源设置持续业务占用。不同草稿可同时等待OA；其短提交会串行。未测吞吐、延迟、锁等待或查询计划。以后要拆成员工/范围/系统锁，必须重新证明不存在来源0基线插入竞态、范围变更与执行竞态及批量锁环，再做真实并发验证；不能只移除guard以“优化性能”。

## T1：系统、目录与稳定树

创建系统：校验code≤93及唯一→插入system→插入`${systemCode}:access` SYSTEM_ACCESS→断言唯一且恰一项→审计/幂等提交。SYSTEM_ACCESS的至多一项由生成槽唯一键保证，“至少一项”由同事务完成并在目录完整性巡检核对，查询缺项报DATA_INTEGRITY_ERROR。API systemAccessPermissionId通过唯一行读取，不加一条循环系统外键。

目录批量：锁系统/目录聚合，校验expectedCatalogVersion及每个更新对象版本，拒绝重复kind+id和localKey。先分配全部新ID，构造最终树和权限集合，校验同系统/类型/无环、绑定及状态。物理写入按父依赖排序创建停用新节点→新操作→设置菜单绑定→最终节点状态，避免MySQL即时循环外键阻断合法同批创建。更新现有节点时同样按最终约束组织操作；任何中间顺序问题均回滚，不能提交停用半成品作为成功。

无环检查使用锁内读取的全部同系统parent关系，对每个节点做visited/recursion-stack遍历（或等价拓扑检测）；禁止自身、跨系统及任意长度环。串行锚点使两个换父操作不能各自只看旧树同时提交成环。仅依靠递归查询深度上限不是无环证明。FUNCTION_OPERATION的function_id必须是FUNCTION，启用功能须绑定本功能menuEligible=true的有效查看/进入操作。之后停用该操作允许保留绑定，菜单自然不可见，不能级联撤销编辑等其他独立操作。

目录/权限/系统启停不改来源周期或已有效grant，不持久关闭员工；重新启用可恢复尚有效配置。执行时复核当前权限与有效祖先，名称/排序改变不机械失效。目录批量无实际变化不递增版本、不写假成功变更审计，返回NO_CHANGE；有变化只递增发生变化的对象版本及一次catalogVersion。

## T2：角色与草稿占用

新角色：锁内校验公司/部门/系统及当前资格；公司级共享角色只准总管理员。生成UUID及稳定roleCode，插入UNCONFIGURED/configurationVersion=0、空ROLE_CONFIGURATION草稿、配置目标、role_change_claim和审计，同事务提交。角色code唯一冲突整体回滚，后端可重新生成候选UUID重试整个同key事务，不复用他人角色。

已有角色改名只推进metadataVersion，不推进configurationVersion。配置草稿的claim持续到删除、驳回、失效或成功；EXECUTION_FAILED仍占用。分配/撤销草稿保存完整明确目标投影，物化必要source与baseline（0基线），仅实际需变更的目标按baseline主键获得relation_change_claim；已完成无变化目标可保留在草稿供分类展示，但不占用，提交时才把content和投影一起固定成实际清单。草稿编辑在相同事务释放已移除目标claim、取得新目标claim并更新内容/draftVersion；任一新占用冲突全部回滚，原草稿及占用保留。

删除DRAFT按INVARIANTS的顺序留下完整删除快照，不修改已有业务授权。不可把已提交方案改成DRAFT。草稿在范围缩小后仍是可修复DRAFT（API不允许缺提交依据的INVALIDATED模型）；越范围管理员不能再编辑，总管理员可按全局资格修复/删除。占用不会自动过期或被后台悄悄裁剪，详细设计需明确展示管理员可处理方式。

## T3：预览、无变化及固定提交

预览从一致状态计算，确认时重新进入T0并按以下顺序：

1. 校验当前身份及**全部输入目标/固定来源**的可见与办理资格；未知/越权不借无变化略过。拒绝重复业务键明细，不用去重掩盖冲突字段。
2. 定位稳定grantKey或准确grantId。新增同来源已ACTIVE为ALREADY_GRANTED，不机械要求原配置版本；撤销准确旧ID已结束为ALREADY_ENDED，不因结束时版本推进再撤一次。仍需办理的新增检查configurationVersion/sourceEpoch/relationVersion、当前员工/任职及系统；仍ACTIVE撤销按准确grantId/version检查，陈旧409。
3. 对照preview绑定的输入、草稿版本及依赖，重新计算实际清单、无变化分类及影响集合摘要；任何会影响这些结果的变化409 PREVIEW_STALE。不能在确认时静默删除新无变化项。全部无变化返回200 NO_CHANGE、保留原草稿，不投OA；空配置仍422 EMPTY_CONFIGURATION。
4. 有实际明细时，从同一最终候选生成content及change_target；核对投影中员工、role/source/baseline/grant、company/department/system一一对应，配置目标等于父kind/公司。target_snapshot固定提交时所有适用employmentIds、角色/权限当时名称及版本。最多100明细，一申请一公司；配置权限集合1～10000，不等于100个员工目标上限。
5. 构造后端原资格：GLOBAL保存原总管理员/目标公司；SCOPE保存原scopeId/scopeVersion、appointmentId/appointmentVersion及公司/指定部门/完整系统快照。当前查看者不能提供或替换originalAuthority。
6. 固定submittedVersion=当前正draftVersion、submittedAt、原提交人、原因和实际内容。集合按稳定键排序；规范摘要包严格为API约定的`{requestId,submittedVersion,content,reason,originalAuthority,submittedByAccountId,submittedAt}`。保存RFC8785 UTF-8原字节及SHA-256，写SUBMITTED审计和oa_outbox，转PENDING/PENDING_SEND/NONE/NOT_STARTED、推进statusVersion，原子提交；有效授权不变。

内容固定后，JSON、规范字节、原资格与目标行不能更新/删除；immutability触发器作第二道保护。statusVersion的变化不修改submittedVersion/contentDigest。驳回后复制新ID草稿，不能在原行改内容重新审批。

## T4：获批执行、一次成功与失败记录

执行器扫描APPROVED或到期EXECUTION_FAILED候选，只取ID，不在扫描事务先锁申请而后申请全局锚点。逐份开启T0：锁后查execution_success；已有则返回历史。读取固定申请与oa_link/oa_final_result，确认一致可信批准；校验固定字节与投影完整性，再复核整个批准清单。

| 类型 | 锁内复核与实际写入 |
| --- | --- |
| ROLE_CONFIGURATION | 原范围及角色固定归属、claim、基础configurationVersion；最终非空。以固定基础集合与最终集合之差确定新增/保留/移除：新增必须当前同系统有效且祖先有效，保留停用项合法并由运行时过滤，明确移除停用项合法；全部引用须存在且同系统/功能归属合法。替换role_permission，configurationVersion+1，首次EFFECTIVE，插入完整配置历史 |
| ROLE_GRANT | 原范围当前覆盖全部目标；员工ACTIVE、系统有效、角色EFFECTIVE及配置版本，配置引用存在/同系统且功能归属合法；不因保留停用项而额外拒绝合法角色授予，停用项与停用祖先在有效权限查询时过滤，不自动补授进入权限；当前来源部门有适用任职且部门角色有其所属部门任职；固定sourceEpoch/baselineVersion匹配、无ACTIVE、claim匹配；每条baseline+1，插新grantID及执行时当前适用任职证据。原申请固定employmentIds不改写，执行只要求当前仍存在适用兼岗；历史周期曾推进则永远不通过 |
| ROLE_REVOCATION | 原范围覆盖；准确grantId仍ACTIVE且relationVersion等于固定值，source/baseline对应不变、claim匹配；逐条baseline+1并把该grant设REVOKED及新版本/结束信息。不要求角色配置版本未变、员工仍在原岗位或系统启用 |

原范围校验：GLOBAL不依赖权限管理员系统清单；SCOPE按原scopeId当前公司/指定部门及systems覆盖每个固定目标，不要求原申请人账号/任命仍有效，不机械比scopeVersion。范围曾实质失去覆盖时T6已把申请持久失效，不能后来恢复范围再执行。

**先复核全部，再写全部**。批准后没有ALREADY_GRANTED/ALREADY_ENDED裁剪；任何目标已结束、周期/版本变动、资格失去或范围不覆盖，整申请INVALIDATED，释放全部claim、停投递待办、保存安全原因及审计，不改其他尚合法目标。配置版本变化导致旧授予失效，一般撤销不受其机械影响。

成功事务中按依赖顺序：写全部业务目标/版本→成功audit_event→execution_success(request PK)→配置历史/执行attempt→申请EXECUTED/SUCCEEDED且failure/nextRetry清空→释放claims，和相关HTTP结果（如适用）一起COMMIT。request触发器要求成功凭据、最终结果与关联已存在。同申请再次执行会先读success；PK是额外保险，不靠不同回调ID决定新成功。

若业务存储或成功审计异常，整个事务回滚。另起T0事务，**重新查当前success/状态**：若已成功不记失败；若已INVALIDATED不改回失败；仍合法APPROVED/EXECUTION_FAILED才保存FAILED attempt与失败审计、转EXECUTION_FAILED/FAILED、failureCode=EXECUTION_STORAGE_FAILURE、nextRetryAt。若数据库不可用，保留之前可靠APPROVED状态，由诊断日志记录，不能声称失败审计已落库。执行attempt的attempt_no在锚点内按已有最大值+1，不靠无锁MAX分配。

进程在批准落地后中断：恢复扫描处理；业务未提交中断：InnoDB回滚；成功提交后响应丢失：回读success。恢复沿用API建议：启动及每30秒扫描、5秒起指数退避至5分钟；没有重试次数自动取消。正式调度和告警模块留详细设计。

## T5：来源关闭与零基线

关闭入口只接受所选任命覆盖的来源系统；总管理员也必须明确来源。T0内按四元键读取，不存在时**先插入epoch=0/version=0**，再核对expectedSourceEpoch/expectedVersion。所有缺失键物化及比较都在锚点内，无“两个事务都读不到然后各自从0写入”的窗口。

同一个新关权事件对所选每个来源epoch+1、sourceVersion+1，即使没有ACTIVE或只有待分配申请；失效所有该来源ACTIVE grant，每条baseline+1并更新grant版本/原因；找到change_target引用这些source的未结束**已提交**分配申请，任一命中整份INVALIDATED，释放全份claims。准确一般撤销目标因关系已结束也整份INVALIDATED。记录关闭原因及成功审计/幂等响应，同事务提交。DRAFT不借此伪造INVALIDATED固定申请，后续按T3修正旧基线。

竞态推演：执行先取得锚点并提交→关闭随后读取到新grant并使它失效；关闭先提交→执行后来看到INVALIDATED/新epoch，不能新增任何明细。不同新key再次确认关权是新事件，继续推进周期；同key仅回放原结果，旧expectedVersion不能以无变化绕过。

内部审计记录全部实际失效目标，完整混合事件按GLOBAL限制读取。HTTP结果及其幂等缓存单独投影：grantCount仅可见关系，requestCount仅整份可见申请，auditEventIds仅可读事件；任意实际影响不可披露则SCOPED/hasUndisclosedImpact=true，不返回隐藏数量。业务失效不能因响应过滤而只做可见子集，完整内部明细不得直接缓存成管理员响应。

## T6：管理范围、任命与当前资格

范围/名单替换在T0内校验expectedVersion与完整输入，校验新增账号ACTIVE/员工ACTIVE及新指定部门适用任职、新增系统已登记可用。未变化的合法集合不推进版本；实质变化scopeVersion+1；新增任命新ID/version=1，保留未变ACTIVE任命ID/version，移除任命设REVOKED/version+1。

范围公司/部门变化时，对原ACTIVE名单按新范围重新校验；不再适用的任命持久INVALIDATED，若仍在新名单却不合格则按输入校验整体拒绝，由总管理员明确修正名单。对所有原scopeId的未结束已提交申请逐目标比较当前新范围，实质不覆盖整份INVALIDATED/释放claim；单纯名单更换、原人离任、增加无关系统不失效合法固定申请。

失去最后有效后台资格时对账号sessionEpoch+1，即使随后重新任命也不接受旧会话。仍有其他合法任命不撤销全部会话，旧上下文用appointmentVersion/scopeVersion拒绝409，新请求重新选择。任命失效不关闭该员工业务授权；范围变更不改任何业务角色归属。

## T7：员工全局状态、任职结束与恢复

员工停用在T0内先防自锁（当前/唯一可用总管理员及关联员工）；更新INACTIVE/version，查询该员工所有已有来源（含草稿/待分配物化来源），逐个推进epoch/sourceVersion，失效ACTIVE grants并推进baseline，失效命中旧已提交授予/撤销申请的整份；失效全部ACTIVE任命/version，关联账号sessionEpoch+1，审计与结果同事务。员工恢复仅ACTIVE/version+1，不触碰原grant、周期、任命、session，不自动结束或恢复任职。

结束任职按准确employmentId/version写ENDED及recorded_end_at，保留明确源结束日期或NULL。锁内按变更后剩余全部ACTIVE任职重算：仅当某员工/公司/部门**从有适用任职变成完全没有**时，推进该部门相关已物化系统来源周期并失效其grant及旧申请；同部门其他适用兼岗存在则保留。部门角色还要验证角色所属部门资格；若角色部门不同于来源部门而丧失其资格，失效相应grant/旧授予并推进对应关系基线，不把原来源任意换为新部门。重新任职不复活已失效记录。

在同一导入批次内先END后ADD同部门也不能抹去中间明确离岗事件：按冻结动作顺序处理，每一步维护资格/周期及失效事实；随后ADD不会倒退周期。两个动作只更换同部门岗位但需保留连续资格时，预检可按源行显式ADD再END，冻结次序并展示影响；不得执行时为了保权自动调换顺序。

## T8：预检冻结、正式导入与部门迁移

文件上传先解析到受控临时存储、校验CSV/XLSX/10MiB/解压50MiB/无宏/业务公式拒绝，再T0写不可变文件元数据。文件本体先持久化并校验SHA再发布引用；元数据事务失败产生的孤立文件可后续受控清理，不能返回成功却缺文件。预检只读业务快照并生成计划，最终在T0复核依赖后写import_batch（sealed_at暂NULL）、规范行/问题，再sealed_at赋值并提交；外部看不到未冻结半份报告。

input_digest包含文件SHA、来源、批号、类型、worksheet、字段/组织映射、初始化选项、原因；同sourceSystem+sourceBatchId同输入返回原预检/结果，不同输入409 SOURCE_BATCH_CONFLICT。新批号重新预检，不修改已sealed旧内容。规范行包含候选ID/code、完整company/department/positionCode、每字段before/after、目标版本、依赖及唯一键不存在断言；不是一句summary。

提交锁内先当前总管理员资格和同批长期成功回放，再expectedVersion/contentDigest及READY或允许重试FAILED；复核文件SHA、每个既有目标版本、唯一键仍不存在、映射版本、引用集合、周期与防自锁。按规范计划的已冻结依赖/动作顺序全部更新，包含必要来源/关系/任命/会话失效。全部计数、实际成功结果、审计、批次SUCCEEDED/version+1同事务。分100行flush允许，但**flush不是commit**；禁止REQUIRES_NEW逐行、跳错行、裁剪目标或把同一批准/批次拆成多事务提交。

依赖陈旧/候选唯一冲突：不提交正式数据，另T0重查批次仍未成功才设STALE/version+1，要求新批号预检；业务预检阻断BLOCKED不得提交；暂时存储失败另存FAILED及安全码，保留固定输入/基线，下次仍完整复核。未知提交结果先查数据库，不直接写FAILED覆盖可能成功。成功后同批原actualCounts长期回放；新批相同资料归UNCHANGED，不复活ENDED任职或INACTIVE员工。

部门迁移在锚点内检查全部正式及历史引用，包括角色草稿/已生效、来源/关系、任职、范围/任命、固定申请目标、审计及组织映射。发现业务/历史引用阻断，不级联改公司。完全无业务/历史引用时，预检冻结目标公司合法及其code无冲突；岗位归属部门不变。若仅有当前来源映射，可在同一事务暂删该映射（保留完整before快照）→改department.company_id→按新归属重建同映射ID/递增版本，以适配MySQL即时复合FK；岗位映射同样更新公司。映射不自动重写sourcePath，必须有显式映射依据，否则阻断。迁移日志在新公司下记录旧/新归属，禁止后来假称此部门一直属于新公司。

## T9：后台会话与JWT即时失效

密码慢哈希在事务外执行；登录先读候选账号/摘要并校验密码，进入T0后重读比对credentialVersion/摘要未变及账号/员工/至少一个当前后台资格，再写session。新JWT只包含必要身份/sid/jti/credentialVersion，不保存JWT正文。NORMAL有效15分钟，临时密码PASSWORD_CHANGE_ONLY有效5分钟，后者仅本人改密/退出。

退出在T0内按可信sid/account撤销单session；无效/过期退出统一204，不根据无效token定位其他人的session。改密/重置原子更新摘要、mustChangePassword、credentialVersion+1、账号version及安全审计，拒绝所有旧session的版本。账号停用更新enabled/version并sessionEpoch+1；启用不减epoch，不改员工或任命/业务grant。失去全部有效资格或员工停用同样推进epoch。恢复后的旧token即使尚未过期也永远版本不匹配。

每个管理读请求以主库新事务查sid/jti/到期/撤销、账号enabled/credentialVersion/sessionEpoch、员工ACTIVE（有员工关联时）、当前全局身份或至少一项有效任命；办理请求另查选择的任命及scope最新版本。所有写在T0第2步最终复核并持锚点至提交。并发停用先提交则请求被拒；请求先提交则停用排在后面，不出现资格取消后新的旧资格事务提交。无异步缓存作为安全依据，一期不读延迟副本。

## T10：OA投递、接收和可靠恢复

投递领取T0：查申请PENDING/NONE、deliveryStatus为PENDING_SEND或SEND_FAILED、尚无可靠oa_link，且outbox READY/到期LEASED，分配leaseToken/leaseUntil，提交。实际发送前另T0核对同leaseToken和申请仍可投递，确认后释放事务，再进行HTTP。关权/范围失效可能在复核后发生；已在途网络无法跨系统撤回，不能承诺“失效绝不产生外部受理”。返回落库时再次T0，匹配固定requestId/version/digest及OA实例，插oa_link，保持唯一关联；正常关联后outbox DONE、清租约，停止新发送；若申请已INVALIDATED，只补历史LINKED，outbox STOPPED、清租约，不改回PENDING、不启动执行。已存在同关联只回读，不以旧租约覆盖后续状态。

发送超时保留同requestId业务键重投核对，不换申请ID。实例必须支持同键受理返回同oaApprovalId；不支持由适配器持久关联。明确失败转SEND_FAILED及安全事件，人工重试仅PENDING+NONE+SEND_FAILED、expectedStatusVersion匹配，转PENDING_SEND/重置待办，绝不重投历史INVALIDATED。外部网络异常不影响已生效业务权限。

接收T0：先验证OaJwt/当前实例，核对oa_link（无则409 OA_LINK_PENDING，不创建最终结果/消耗成功事件号）→按(instance,eventId)比较完整规范payload→核对申请提交版本/摘要/审批号→与已有oa_final_result比较。不同eventId同结果插入新事件诊断、200 duplicate=true；相同eventId不同payload或同申请相反decision409，不覆盖。可信新最终结果先插callback_event、final_result，再更新request批准/驳回三轴/statusVersion，追加审计；驳回释放claim，批准不改权限。可靠提交后202。申请原已INVALIDATED时保留失效状态，记录匹配迟到最终结果，仍不执行。

事务未可靠提交返回503，OA重试同event；不同event不绕过一申请一最终结果和一次成功执行。审批决定时间不作“后者覆盖前者”的依据。回调认证失败不泄露目标细节；冲突诊断只存安全标识和摘要，不记原始认证头。

## T11：预览、敏感影响与一致读取

创建预览在T0中读取新鲜状态、计算并保存快照，不改变正式业务状态；5分钟后过期。依赖不使用guard.revision，否则无关更名也会全部陈旧。dependency_snapshot明确保存：输入目标版本、当前角色配置版本、当前来源epoch/baseline/grant状态及版本、适用任职ID/版本集合、相关目录/权限状态、当前范围/任命上下文，以及会影响影响集合的查询结果键/版本集合摘要（包括“结果为空”的集合断言）。

确认在锚点内按相同过滤条件重新读取这些集合并规范排序比较，因此新增grant、新任职、新待执行申请、新范围资格等幻读也会被发现；只比较已有行版本不能检出新增目标。对角色配置影响还包括所有相关员工的完整同公司系统有效来源及配置/目录状态依赖，用于判断剩余进入资格。发生无关变化但结果/实际依赖没变不因全库序号拒绝。

统计、员工项和通用目标项同一快照去重；SOURCE用完整四元键、员工用employee/company/system键，避免把跨系统员工影响错误折为一条结论。预览明细仅存创建时可见安全投影；全量计算只用于可见员工的true/false结论，不保存或返回越范围姓名/角色/数量。混合申请需全部目标被同一项当前任命覆盖才暴露ID/内容/计数，否则只给hasUndisclosedImpact提示，不给隐藏人数。

读取预览页面重新查当前账号及创建上下文：身份/范围缩小、任命失效、版本上下文改变或过期则整个预览失效，要求重新预览；不返回原明细部分页配旧统计。扩大范围也不补入快照外对象。确认时复核依赖并与固定实际清单一致，不把previewId当权限凭证。

UC-05/06权限查询与后台审计读取使用主库独立REPEATABLE READ只读事务，显式`START TRANSACTION WITH CONSISTENT SNAPSHOT`或等价在首次查询前建立新快照，不跨HTTP复用EntityManager旧事务。当前身份检查、员工/任职、system、grants/roles/permissions/祖先及最终菜单/操作来自同一快照。已提交关闭之后开始的新事务可见关闭；已经在执行中的外部业务事务不在承诺内。

功能计算：员工ACTIVE且当前公司有有效任职→系统/服务有效→按所有当前公司系统ACTIVE、周期有效、适用任职有效grant合并角色当前配置→先判SYSTEM_ACCESS；无则canAccess=false、菜单/操作空；有则独立功能操作集合去重并检查祖先有效。菜单只按本功能可见操作，必要父级containerOnly，不由编辑推导查看。后台管理可见子集不能代替完整业务授权计算，无权限和存储故障分别响应。

## T12：JPA适配与版本职责

| API/存储概念 | 对应字段及处理 |
| --- | --- |
| 普通对象version | company/department/position/employee/employment/business_system.version；锁后显式比较并在实质变化时+1 |
| metadataVersion | business_role.metadata_version；改名说明推进 |
| configurationVersion | business_role.configuration_version及role_configuration_history版本；仅执行配置推进 |
| catalogVersion | business_system.catalog_version；同批目录只推进一次，节点/操作version各自推进 |
| scopeVersion | management_scope.scope_version；名单或范围实质变化推进，当前上下文要求精确，自动执行判断实际覆盖 |
| appointmentVersion | admin_appointment.appointment_version；该任命结束/变化推进，范围版本不机械写入每项任命 |
| sourceEpoch/version | authorization_source.source_epoch/source_version；关权或来源资格丧失推进；物化0基线不产生新周期；仅新增grant不推进来源周期 |
| relationVersion | grant_baseline.relation_version为当前永久基线；role_grant.relation_version为该准确关系最后版本；旧结束grant不随以后新grant推进 |
| draft/submitted/status | change_request三独立列；submittedVersion提交时取draftVersion后冻结；投递/接收/执行只推进statusVersion |
| credentialVersion/sessionEpoch | 后台认证失效版本，非Account.version或任命版本；对JWT精确匹配 |
| JPA @Version | 存储专用row_version，和上述API语义分开；同实体只允许一个@Version，不能把所有业务版本标成@Version |

已提供row_version的可变聚合使用JPA `Long @Version`，业务版本显式维护。简单有单一version的表也可采用锁内`UPDATE ... WHERE id=? AND version=?`并检查affectedRows=1，详细设计决定是否复用为@Version；若复用必须保持API无变化不推进，不可因审计/脏检查自动推进。集合替换不能依赖Hibernate是否自动更新父行版本，必须显式推进对应业务版本/父row_version。

UUID按CHAR映射（明确Hibernate JDBC字符类型/属性转换），不是默认假设UUID会绑定CHAR；复合关联字段可使用只读关联或嵌入ID，避免同column双写；generated槽insertable=false/updatable=false。新实体预分配UUID时，用可空包装类型@Version或Persistable明确isNew，不能让save误判为merge后覆盖；这仍需未来框架集成验证。

`@Lock(PESSIMISTIC_WRITE)`或等效SQL用于锚点及必要当前读；事务必须由服务边界建立。bulk JPQL/native UPDATE会绕过实体脏检查/@Version，必须显式带版本条件并clear/refresh，禁止随后flush陈旧实体覆盖。受检异常也需rollbackFor或等价回滚配置；失败审计在原事务退出后另开事务，不能在rollback-only里伪报持久化。关闭OpenEntityManagerInView，不在序列化阶段懒加载另一状态快照；列表避免无界关联抓取与N+1。

官方参考：[Spring Data JPA锁](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)、[实体新旧判断](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html)、[Spring事务回滚](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)。这些适配尚未创建实体或真实测试，工程级类/仓库/调度分解留详细设计。
