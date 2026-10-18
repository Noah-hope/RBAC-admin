# 模块结构、接口与责任

D-056：BE-06在开发分支实现当前后台账号/员工/有效任命资格、会话、三类JWT及四个身份HTTP接口。CurrentIdentityReader必须在调用方读/写边界内再次调用，HTTP层校验不代替业务授权。完整单项范围覆盖仍归BE-08，其他领域按原技术依赖推进；见[身份设计与证据](BE-06-IDENTITY.md)及[团队接入](BE-06-LOCAL-SETUP.md)。

D-048补充：core/idempotency提供显式两阶段协调器、CanonicalRequestCodec端口、授权事实和安全结果类型，见[BE-05](BE-05-IDEMPOTENCY.md)。没有全局Servlet缓存或默认管理员身份；正式领域必须提供当前资格/完整覆盖检查及安全投影。

D-046实施范围：M00公共CommandBoundary/ReadBoundary/WriteContext和AuditAppender已有实现及隔离引擎自测，见[BE-04记录](BE-04-TRANSACTIONS.md)。正式资格、业务受控写端口、幂等和审计查询仍由相应任务实现，下文接口设计不表示这些业务已实现。

## 设计取值与分层

实施组织修订1.1/D-038：先由用户实现BE-01～05公共基座，验收后再分派30项业务/交付任务。人员分组不替代下述13模块及依赖；common责任范围以M00/core为主，M11/M12仅其公共支撑部分先行。各业务负责人当前未指派，详见[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)。

采用单个Spring Boot应用、单个MySQL数据库的模块化单体，所有业务写共享本地事务锚点。它便于四人协作、原子批量和部署；这是本轮技术设计取值，不是此前用户已指定的部署拓扑。无Redis、消息队列或微服务前置依赖。OA使用可替换的演示适配器，正式入站契约不变。

HTTP适配层M12负责DTO、认证头、状态码和Problem；应用编排层负责当前授权、完整用例和事务；领域策略/受控写端口维护本域不变量；JPA/JDBC与文件/OA适配器提供持久化和外部边界。使用普通Java接口及构造注入，不为每个字段/表建一套抽象层。

模块及编译依赖见[MODULES](MODULES.md)，可编辑图见[module-structure.drawio](module-structure.drawio)。公共包取`com.adgn.backend`，按功能包再分`web/application/domain/persistence`，不按全局Controller/Service/Repository大目录让四人同时编辑同一服务。M11跨表只做独立SQL/不可变DTO投影，实体写入仍归各模块；统计和详情使用相同授权谓词。

## 数据流到结构化模块

需求第一层数据流的过程是分析职责，详细设计进一步分解为可调用模块：

| 数据流过程 | 设计分解与理由 |
| --- | --- |
| 1.0身份与管理范围 | M01账号/会话、M03范围/任命；实质缩小的跨域失效交M10，身份与业务角色分开 |
| 2.0目录 | M04最终树/独立操作，M11安全影响；修改目录不直接终结grant |
| 3.0人员导入失效 | M02基础组织任职，M10冻结导入与生命周期编排，M06关系，M01会话/M03任命/M07申请失效 |
| 4.0申请办理 | M05角色、M06基线、M07草稿/claim/固定申请、M11预览，M08读取待办投递 |
| 5.0审批执行 | M08可靠最终结果；M09独立执行全清单，成功不依赖回调HTTP存活 |
| 6.0有效权限 | M11独立新鲜主库快照和并集计算，不查询OA、不写授权 |
| 7.0审计读取 | M11当前范围投影；M00只追加审计，避免查询反向调用业务写模块 |

来源：[第一层数据流分析](../01-requirements/dataflows-v1/第一层数据流分析.md)。D1～D9逻辑存储已细化为数据库43表和文件/日志，不能按逻辑存储数量制造模块或表数量。

## 共用类型与写入口

| 设计接口/类型 | 输入及返回 | 调用与失败约定 |
| --- | --- | --- |
| ActorContext | principal、sid/jti、当前总管理员或所选单项任命/范围/版本，服务实例及绑定系统 | 由可信JWT和锁内/读快照内数据库事实生成，HTTP body不能提供；不能拼接任命 |
| CommandBoundary | 请求元信息、身份读取函数、授权谓词、业务lambda → 安全结果 | 获取T0锚点→当前资格→幂等/范围→完整校验→业务/audit/结果同提交；不允许控制器开启第二事务 |
| ReadBoundary | 身份读取和业务读取函数 → 不可变DTO | 每HTTP新RR只读事务，同快照完成当前身份、授权谓词和全部查询；禁止返回懒加载实体 |
| WriteContext | 当前事务/连接标识、guard已持有、actor、Clock/IdGenerator | 仅CommandBoundary创建；写端口拒绝无活动事务/无guard的调用，内部端口不公开成HTTP接口 |
| CurrentIdentityReader / QualificationReader | 当前账号/员工/会话/任命/范围及员工/部门/系统/角色键 → Snapshot | SQL投影由域主责维护，使用调用者同一连接；查询策略不能另开事务而失去一致性 |
| ScopeCoveragePolicy | ActorContext/OriginalAuthority、完整目标范围 → 允许或安全拒绝 | 当前人工操作按当前一项任命；自动执行按固定原范围当前覆盖且持久失效优先，不依赖原申请人仍任职 |
| PreviewPlanner / ImpactService | 完整输入、当前Snapshot → 依赖键/版本集合、实际变化、无变化分类、安全投影 | 完整计算与响应披露分开；M11不调用命令模块，确认由外层命令在锁内复算 |
| RoleWriter / RoleConfigurationApplier | 受控WriteContext、固定归属/基础版本及最终权限集合 → 新版本/历史 | 新角色可UNCONFIGURED；有效配置应用器仅M09使用，差集检查新增而允许保留停用项 |
| BaselineWriter / GrantApplier / SourceCloser | WriteContext、固定键/版本/证据/结束原因 → 新baseline/grant与AffectedFacts | 先物化缺失0基线；历史不重置；SourceCloser只报告实际受影响源/关系，不反向调用M07 |
| WorkflowStateWriter / RequestInvalidator | WriteContext、固定申请及合法状态转换/受影响事实 → 状态、claim释放、outbox停止 | 审批/生命周期/执行编排统一调用；只整份失效已提交申请，DRAFT仍可修复/删除 |
| AuditAppender | 同事务操作者、范围、前后值、目标 → auditId | 失败抛出使业务回滚；不吞错，不把内部完整事件直接当可返回响应 |
| OaSubmissionPort | 固定OaSubmission → 合法受理回执或可分类失败 | 事务外调用；demo实现不决定批准；重试固定requestId，不能创建新申请 |
| ExecutionCoordinator / FailureRecorder | requestId → 已成功/本次成功/失效/可重试失败 | 扫描只取候选ID；先T0再读申请。失败记录只能在原事务完全回滚后另起事务复核 |

M00不依赖具体业务模块：事务边界接受函数/接口，查询适配器由装配层注入。M07提交写outbox事实后返回；M08扫描事实，M09扫描已批准状态，M07不调用二者，所以不形成申请—OA—执行服务互调环。M10负责范围、员工、来源、导入的高层生命周期编排，M02/M03/M06不反调M10。实际业务方法按所属模块进行封装，控制器不得直接获得写Repository。

## 授权、错误与返回

三类SecurityFilterChain/等价隔离链：管理员`/admin`、业务系统`/integration`、OA`/oa`。先校验固定算法RS256/iss/aud/时间，再在事务内校验当前服务/账号/员工/任命/范围。登录匿名；退出允许无效/过期token并统一204，不借坏token定位他人会话；临时密码token仅改密/退出。健康入口单独配置，内部细节不公开。

授权分两层：入口身份失败401/403；资源列表/total按授权谓词过滤，猜测详情越权与不存在统一404，显式越范围筛选403。写操作先授权全部目标再无变化和版本判定；幂等回放也需当前授权，删除后的原范围事实取内部authorization_basis。原资格不是当前查看者提供，后台人工权限和业务角色不互相授予。

业务策略返回语义明确的结果/安全错误码，Web层映射已有Problem；不新增契约枚举来掩盖实现问题。422用于非法输入，409用于版本/占用/预览冲突，503用于无法可靠读取/提交的存储故障；后台不可重试条件整份INVALIDATED，暂时失败进入EXECUTION_FAILED。详情见[CORE-FLOWS](CORE-FLOWS.md)。

DTO不直接绑定JPA实体。输入严格oneOf、必填/枚举/未知字段拒绝；ID不透明，版本安全整数，长度按Unicode code point与OpenAPI一致。普通业务字符串按契约trim，密码保留原始空格和Unicode，不截断、不trim、不擅自NFC归一化。响应只映射已授权投影；目录/预览/审计的排序和分页分别按契约定义。
