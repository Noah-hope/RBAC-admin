# 后端验收与TDD交接

下表为验收目标：AT-01/37的BE-02部分、AT-02的基础持久化及AT-03/33的公共事务/快照及AT-04的公共幂等部分、AT-05/06/07的BE-06身份部分及AT-06/07的BE-07账号管理部分为PARTIALLY_VERIFIED，其余32项DESIGNED_NOT_RUN；完整业务仍未验收，局部证据见[BE-02实施记录](BE-02-IMPLEMENTATION.md)、[持久化基础验证](BE-03-PERSISTENCE.md)、[公共事务](BE-04-TRANSACTIONS.md)、[幂等机制](BE-05-IDEMPOTENCY.md)、[身份实施](BE-06-IDENTITY.md)及[账号管理](BE-07-ACCOUNTS.md)。其中AT-40为前端后置交接，不计本期后端验收；其余40项覆盖后端、部署及协作。实际执行需对应Issue按项目TDD技能记录失败→实现→通过，并在隔离MySQL8.4运行关键事务/并发/故障场景。

D-038阶段约定：用户先验证BE-01～05的公共机制和对应acceptanceScope，F-01～06为基座交接检查，不新增业务测试编号。真实身份、草稿删除、审批、导入等端到端场景留业务分派后补齐；夹具证明机制不能标记整个AT或业务已通过。D-053已确认F-01～06，基座已合并并推送main/b68aa9a；见FOUNDATION-REVIEW.md。

| 用例 | 行为/准备 | 必须观察到的结果 | 操作 | 评审追踪 |
| --- | --- | --- | --- | --- |
| AT-01 可重复构建与最小部署 | 空工作目录按固定Wrapper构建；缺必需配置再启动 | 构建产物可追溯提交；缺配置清楚失败，日志无凭据；健康检查不暴露内部详情 | 内部/部署 | 新增验收准备 |
| AT-02 MySQL迁移、即时约束与JPA映射 | 隔离MySQL8.4空库运行迁移两次，尝试非法跨系统FK/重复ACTIVE/非法CHECK及UUID往返 | 首次成功、再次无额外业务数据；非法记录拒绝；43表/触发器实际编译与UUID/UTC/生成列/@Version行为有证据 | 内部/部署 | 新增验收准备 |
| AT-03 写锚点及原子回滚 | 两个独立连接先后取得guard，在最后一次审计插入注入异常 | 写者串行；失败事务所有业务/版本/成功凭据回滚，其他连接看不到半份状态 | 内部/部署 | S08/S15/S20 |
| AT-04 HTTP幂等栅栏与失败恢复 | 同主体同key相同/不同内容；模拟过期租约、提交后丢响应、删除草稿后重放和失去范围 | 相同结果回放不重比旧目标版本；异内容409；旧token不能提交；删除后以固定事实复核当前资格；失权401/403 | deleteChangeDraft | S16 |
| AT-05 登录与普通员工边界 | 有效管理员、普通员工、临时密码账号、错误密码分别登录 | 普通员工不能登录；失败不泄露账号存在；临时token仅改密/退出；限速/过期符合契约 | login,changeOwnPassword | S21 |
| AT-06 退出、改密及账号停用即时撤销 | 同账号两会话；退出一项、改密/重置、停用/启用后再次用旧token | 退出只撤当前sid且重复204；改密/重置撤全部旧会话；恢复账号不复活旧token或改变业务授权 | logout,currentSession,setAccountState,resetAccountPassword | S21/S22 |
| AT-07 三类JWT严格隔离 | Admin/Business/OA token交叉调用；错误iss/aud/alg、失效服务和错误system绑定 | 401/403且不泄露目标；业务凭据只限绑定系统，不增加公开令牌签发API | authorizationQuery,permissionCheck,receiveOaResult | 新增验收准备 |
| AT-08 管理员任命与单项上下文 | 两项互不覆盖的任命、陈旧/缺失上下文头、没有任职的候选账号 | 不能拼接；缺头422、陈旧409；不合资格拒绝且全批不变；仅共享角色维护由总管理员 | myAppointments,createScope,replaceScope | S04/S11 |
| AT-09 系统进入槽与同批功能目录 | 登记系统并同批建FUNCTION/操作/菜单绑定；跨系统/重复localKey/长环 | 进入权限唯一；合法本地引用一次提交；非法整体拒绝无半成品；排序稳定 | createSystem,saveCatalog | S02/S03 |
| AT-10 独立操作与目录启停 | 角色只有编辑、另一角色有进入；停用查看或父目录，再恢复 | 编辑不推导查看；角色/授权关系保留；父级/操作过滤正确，恢复只作用于仍有效关系 | updateSystem,saveCatalog,authorizationQuery,permissionCheck | S03/S23 |
| AT-11 角色元数据与归属 | 创建部门/共享角色、改名及伪造公司/系统归属 | 归属不可改；共享角色仅总管理员；改名仅metadataVersion，不改变有效configurationVersion | createRole,updateRoleMetadata | S04 |
| AT-12 角色配置新增/保留/移除与空配置 | 基础A+停用B，最终A+B+有效C；新增C在执行前停用；移除B/清空/无差异 | 保留B合法、可明确移除；新增C失效则整份INVALIDATED；清空422；无差异200不投OA | changePreview,submitChange | S05 |
| AT-13 完整任职依据与岗位不自动授权 | 同部门101项适用任职、重复/遗漏证据；岗位更名或新增岗位 | 全部依据可提交；空/重复/遗漏拒绝；岗位变化本身不创建grant，固定证据不悄然改写 | grantBaselines,createChangeDraft,submitChange | S06 |
| AT-14 同源去重与异源并存 | 同员工角色同部门重复授予，另一部门独立授予；撤销后新授予 | 同源仅一ACTIVE；不同来源同时有效；baseline不重置，新grantId，旧撤销不误伤新关系 | grantBaselines,listGrants,submitChange | S07/S09 |
| AT-15 零基线关权与执行两个顺序 | 来源无grant但有待授予；用两个连接控制执行先/关权先及同key重放 | 无grant也推进周期；执行先后被关掉，关权先旧批准全份失效；同key不再推进，其他来源保留 | sourceBaselines,closeSource | S08 |
| AT-16 无变化、权限与版本判定顺序 | 越范围已授予项、同源已授予但旧配置版本、已结束准确grantId、仍需项陈旧、预览后分类改变 | 先拒越权；合法已授予/已结束归无变化；仍需项版本409；预览变化409而不裁剪 | changePreview,submitChange | S10 |
| AT-17 草稿占用与删除 | 多人改同草稿/同baseline；移出范围后删除；一个新目标占用失败 | 冲突全回滚，原草稿/claims保留；当前不覆盖者不得处理，管理员通用提示不泄漏隐藏ID；总管理员现有入口清理 | createChangeDraft,updateChangeDraft,deleteChangeDraft | S12 |
| AT-18 固定申请与规范摘要 | 提交后改当前名称；尝试改内容/目标行；使用RFC8785向量重建摘要 | 冻结内容/字节/投影一致，当前更名不改批准内容；提交后修改拒绝；固定版本与statusVersion分开 | submitChange,getChangeRequest | S13 |
| AT-19 原申请人离任与原范围缩小 | 申请人离任/账号停用；原范围实际缩小后扩回；仅名单更换/增无关系统 | 前两种资格离任不机械影响已提交申请；实质失覆盖持久INVALIDATED且不复活；名单变化不误伤 | replaceScope,receiveOaResult | S11 |
| AT-20 OA投递超时与关联前回调 | 受理后返回超时；关联未写先回调；申请失效时发送返回 | 同requestId重试关联同审批号；未关联409且不消耗事件；失效只补历史不回PENDING/不执行 | retryOaDelivery,receiveOaResult,deliverOaSubmission | S14 |
| AT-21 OA事件、最终结果三层防重 | 同事件同/异内容；不同事件同决定；同申请相反决定；失效申请迟到批准 | 匹配重复200；异内容/矛盾409不覆盖；可靠新结果202不等于授权生效；失效留历史不复活 | receiveOaResult | S14/S15 |
| AT-22 一次成功与全批执行 | 100目标固定申请，第N项变陈旧；两执行器竞争；成功audit写入失败 | 不裁剪，一项非法整份失效无有效子集；success请求PK只一次；存储失败全回滚后另事务记失败 | receiveOaResult,getChangeRequest | S15 |
| AT-23 执行失败恢复与进程重启 | 已可靠APPROVED后宕机，执行写失败，回包丢失，失败记录前另一执行已成功 | 恢复扫描拾取；同申请退避重试；不重新审批；失败记录不能覆盖success/INVALIDATED，成功只回读 | getChangeRequest | S15 |
| AT-24 员工停用/恢复与会话 | 员工有多来源/兼岗/任命，与授予并发停用后恢复 | 全部旧grant/任命/会话持久失效，epoch推进；恢复仅员工ACTIVE；保护当前/唯一总管理员 | setEmployeeState | S17/S22 |
| AT-25 离岗事件顺序Q-01 | 同原子批次END最后适用任职再ADD同部门新岗；另有始终适用兼岗；仅更名 | 前者推进周期并使旧授权/任命/相关申请失效且新岗不复权；后两者按适用条件保留，全批原子 | commitImport | S18 |
| AT-26 上传与文件解析边界 | CSV/XLSX合法样例，超10MiB/解压50MiB/1000行、宏、业务公式、含糊工作表 | 合法可预检；超限/非法明确拒绝；不执行公式；预检前无正式组织/员工变更 | uploadImportFile,precheckImport | S19 |
| AT-27 预检冻结、映射与候选冲突 | 无源编码组织、同名不同路径/源账号、规范化前后值；预检后占用候选code | 预分配稳定ID/code并回读完整fieldChanges；同名不归并；提交冲突STALE，全批无正式写入 | precheckImport,listImportPlanChanges,commitImport | S01/S19 |
| AT-28 导入第N项失败与重试 | 1000行含停用/离岗，最后行冲突或审计失败；成功批次重放/新批同资料 | 整批含失效/版本/审计全回滚，失败另存；同成功批号回原counts，新批UNCHANGED，不复活旧状态 | commitImport | S20 |
| AT-29 部门迁移与历史保护 | 部门已有角色草稿/历史grant/审计；完全无业务历史引用的迁移 | 有引用阻断；合法迁移按冻结映射与即时FK顺序同事务完成，不级联改写历史 | precheckImport,commitImport | S25 |
| AT-30 业务权限完整并集与隔离 | 同公司系统多部门来源、另一公司同名权限、无进入但有操作、未知code | 当前公司/系统完整有效来源并集；进入与功能可跨角色；无进入结果空；未知与存储故障区分，无数据范围 | authorizationQuery,permissionCheck | S23 |
| AT-31 影响快照与新增目标 | 预览后出现新grant/申请/任职；权限缩小/切换/过期；无关改名 | 检测集合新增和真实依赖变化；陈旧409或拒绝旧预览，不裁剪/不配旧统计；无关变化不机械失效 | listPreviewEmployees,listPreviewTargets,submitChange | S24 |
| AT-32 敏感混合申请计数与审计 | 关闭可见来源A，命中跨A+B固定申请；当前任命只含A | 整份实际失效；不返回混合申请ID/计数或不可读auditId；SCOPED/true配可见计数，缓存相同安全投影 | closeSource,getAudit,listAudit | S24 |
| AT-33 当前快照与撤权后的新查询 | 业务查询建立新主库快照，另一事务关闭来源提交后再开新查询 | 新查询不见旧权限；同一响应身份与结果一致；不使用旧EntityManager/副本/权限缓存 | authorizationQuery,permissionCheck | S23 |
| AT-34 审计读取与历史删除 | 越权详情/显式筛选、混合全局事件、删除草稿与历史引用 | 详情404/筛选403；列表total按可见范围；不可拆完整混合事件仅总管理员；保留历史及防重事实 | listAudit,getAudit,listChangeEvents | S24/S25 |
| AT-35 演示OA与服务令牌边界 | demo profile受理固定申请但未批准；用可信OA短期JWT调用正式回调，再拒绝/重复/重启 | 受理不自动批准；只经原回调决定；无新管理员审批/强制执行接口；模拟标签明确，重启不丢本地防重依据 | 内部/部署 | 新增验收准备 |
| AT-36 服务器部署和数据持久化 | 新目标环境安装/启动、重启应用、备份恢复到隔离环境、回退应用版本 | 迁移校验通过，数据/文件/密钥持久；外部HTTPS仅暴露必需入口，健康可查，恢复/回退有证据；不把迁移回滚当事务 | 内部/部署 | 新增验收准备 |
| AT-37 全接口契约与Problem | 67入站逐项HTTP：成功、401/403、缺版本/非法字段、分页/位置/错误媒体类型 | 按OpenAPI1.2.0验证请求响应，目标资源当前授权；错误application/problem+json，敏感响应no-store；每项由所属Issue负责 | 内部/部署 | 新增验收准备 |
| AT-38 示例全链路演示 | 虚构两公司/两部门/兼岗、两业务系统、共享/部门角色；按正式导入及审批流程走一遍 | 角色配置→OA→授予→查询→关来源→迟到批准→恢复不复权可复现；日志与报告无凭据，不依赖正式前端 | 内部/部署 | 新增验收准备 |
| AT-39 PR与发布证据 | 一个任务分支、失败→通过测试记录、任务负责人自检确认、候选部署包 | Issue/PR/commit/构建校验和及负责人确认对应；未验收项不关闭，未运行测试不打勾；当前设计阶段不创建远程事项 | 内部/部署 | 新增验收准备 |
| AT-40 前端后续衔接 | 既有原型页面对照真实契约与当前任命/状态三轴 | 复用页面与交互基线，未来接入真实API；原型Mock不作为后端验收；本周不实现正式前端 | 内部/部署 | 新增验收准备 |
| AT-41 来源资格与角色部门资格分别失效 | 员工仍在来源部门A，但失去所持部门角色所属B部门的最后适用任职 | 只结束受影响角色各来源grant并推进其baseline/旧申请失效；不关闭仍有效的整个A来源，不误伤其他角色，不更换来源部门 | commitImport,grantBaselines | S06/S18 |

## 测试层次与运行证据

- 单元：纯策略/集合差异/规范化/目录无环，不以mock调用次数替代行为。
- HTTP：真实DTO/认证链/Problem与全部67入站响应Schema；单项管理上下文、旧token和三类JWT交叉调用。
- MySQL集成：Testcontainers MySQL8.4；无容器时使用团队批准的独立测试实例和唯一测试schema，绝不指向共享/业务库。H2不替代即时FK、JSON、触发器、生成槽与事务测试。
- 并发：两个独立连接/事务，以barrier控制锁取得/提交先后；测试方超时保证清理。既测执行先也测关权先，观察最终授权/epoch/success/audit，而不是仅断言抛异常。
- 故障：在业务第N项、audit、success及完成幂等结果前注入异常，另连接检查全回滚；结果可靠提交后模拟进程中断/响应丢失，恢复不覆盖成功。
- 部署：候选产物在实际目标服务器启动、迁移、重启、HTTPS与全链路演示；本机Mock不能替代。

Issue的验收AT列表是最低必测行为；具体负责部分见每卡acceptanceScope。AT-37覆盖每项所属API，包括未单独列出的列表/详情/预览。基础任务可按其负责部分验收合并，不能据此把整个AT标为通过；BE-33汇总后端完整行为与契约证据，BE-34完成目标服务器，BE-35汇总发布证据。BE-01只核验准备清单，不要求先有构建或发布包，因而不形成实现前置条件循环。测试类和断言在对应模块实现时按writing-good-tests设计，BE-02已有公共适配的RED/GREEN及打包HTTP证据，见[BE-02-IMPLEMENTATION.md](BE-02-IMPLEMENTATION.md)；BE-06身份验证见[身份记录](BE-06-IDENTITY.md)，其余业务尚无运行结果。

拟定构建命令为`./mvnw verify`，其中unit走Surefire、*IT走Failsafe、集成测试不得静默skip；另生成JUnit/契约验证报告。当前backend已有Wrapper及实际构建结果；BE-03准备后完整构建41项离线测试通过，包含原BE-02的30项，不等于真实数据库或完整业务验收通过，见BE-03-PREPARATION.md；最新BE-06身份与旧回归见BE-06-IDENTITY.md；此前BE-05幂等验证见BE-05-IDEMPOTENCY.md；51离线＋25引擎是此前BE-04结果，44离线＋9引擎是此前BE-03结果，领域映射/完整业务并发尚未验收。外部真实OA改为演示适配，信任及回调边界仍保留。
