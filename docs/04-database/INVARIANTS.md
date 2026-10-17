# 关键不变量与历史策略

所有机制按MySQL8.4/InnoDB设计。DDL草案未运行；CHECK/触发器不能替代操作资格、跨表集合校验和明确事务协议。JSON列另有JSON_TYPE顶层OBJECT/ARRAY检查，防JSON字面null/数组绕过对象形状；内部字段仍按契约与snapshot schemaVersion校验。下表的T编号见[事务设计](TRANSACTIONS.md)，索引/键名见[完整清单](KEYS-INDEXES.md)。

| 编号 | 不变量与数据库保证 | 事务、锁及服务端必须保证 | 并发失败与恢复 |
| --- | --- | --- | --- |
| I-01 | 稳定UUID主键；公司code、employeeKey、角色code、loginName全局UQ，部门/岗位编码按归属UQ；身份/归属触发器禁止非法改写 | T1/T8按显式映射匹配、更名；输入trim后验证，密码原样；唯一冲突不覆盖 | 同时创建同键一份成功，另一409/导入STALE；同key回放原结果 |
| I-02 | employee_source_identity(source_system,source_key)UQ；员工ID FK；组织路径UQ | 跨来源相同employeeKey不得自动新建或按姓名合并；重映射核对旧键/稳定ID，历史前后值入审计 | 不同源相同统一键整体阻断；不裁剪导入 |
| I-03 | 任职FK保证岗位/部门/公司一致；UQ(employee,position,active_slot)只约束当前；结束组合CHECK | T7/T8按员工完整兼岗判断失去全部适用任职，不以某个employmentId结束就关全来源；结束不可恢复原行 | 相同新资料去重报告；历史重聘用新ID，旧授权不复活 |
| I-04 | 后台账号employee_id可空UQ：普通员工至多一个账号；任命ACTIVE槽UQ(scope,account,slot)；范围系统PK去重 | T6/T9当前账号/员工/适用部门任职及单项任命检查；仅部署设置总管理员；范围可无人/空系统；只选一项上下文 | 重任命新ID；范围版本变化409 CONTEXT_CHANGED；业务角色不随账号停用删除 |
| I-05 | 系统code、目录/权限(system,code)UQ；权限类型/功能非空CHECK；系统access_slotUQ | T1创建系统与恰一SYSTEM_ACCESS同事务；UQ只能保证至多一项，提交前断言恰一项且API systemAccessPermissionId由查询取得 | 审计/绑定失败全部回滚；不靠缺项默认进入 |
| I-06 | 目录父FK同系统、非自身CHECK；可见权限复合FK保证属于本功能；NAVIGATION无绑定CHECK | T1在锁内对最终完整树遍历检测任何环；FUNCTION_OPERATION目标必须FUNCTION；启用功能绑定menuEligible=true有效操作；状态停用后允许绑定暂不可用 | 并发换父串行后第二事务重新查整棵树，出现环422 DIRECTORY_CYCLE；整批回滚 |
| I-07 | 角色归属复合FK；role_permissionPK去重及复合FK同系统；role状态/配置版本CHECK；固定归属不可改 | T2/T4公司级共享角色只准总管理员维护；生效配置1～10000项，初始0项；移除停用权限允许，保留/新增按API复核，不附赠进入权限 | 新角色/空草稿/占用/审计原子；配置失败旧集合保留 |
| I-08 | 来源四元键UQ；永久baseline(source,role)UQ及角色/来源同公司系统复合FK；grant ACTIVE槽UQ；baseline/versionUQ | T3/T4/T5源及baseline缺失时在写锚点内按0创建；关系版本只在授予/结束推进，不删除锚点；角色部门适用资格与来源资格分别验证 | 同来源重复ACTIVE不可产生，A/B来源均可存在；唯一冲突整批回滚后重新读取 |
| I-09 | role_grant每次新ID；状态/ended字段CHECK；grant证据FK任职；历史无级联删除 | T4新授予baseline v→v+1，新grant持新值；结束v→v+1并更新准确grant；再授予新ID/v+1；冻结原employmentIds | 旧grantId从不改指新grant；旧撤销不得按员工/角色查到新行后替换目标 |
| I-10 | 角色配置claim以role_id为PK，关系claim以baseline_id为PK；占用历史独立于申请表 | T2/T3草稿实际目标取得claim，编辑按差集原子替换；终态/合法删除释放，失败重试继续占用；无变化不新增占用 | 两份不同草稿竞争同目标409 CHANNEL_OCCUPIED/RELATION_OCCUPIED；其他员工/来源无跨OA周期业务占用 |
| I-11 | 申请DRAFT/固定字段、三轴组合CHECK；目标分支CHECK和父kind复合FK；固定内容/目标触发器保护；终态禁止回退 | T3一次冻结实际内容/原因/版本/原资格/规范字节；目标投影逐项与content一致，提交后不改；执行校验摘要损坏 | 草稿被删除留事件；驳回修改新申请；旧状态不能复活 |
| I-12 | 原scope/appointment历史FK保留；原依据JSON与规范字节不可变 | T4仅SCOPE按原scopeId当前公司/部门及全部系统覆盖；不机械比版本或要求原人ACTIVE；T6实质缩小当场整份INVALIDATED | 范围后来恢复不复活；增无关系统/原人离任不单独失效；GLOBAL不套部门系统清单 |
| I-13 | source_epoch/source_version、relation_version非负及安全整数CHECK，持久保留 | T5推进所选每个来源周期，即使无grant；失效关系推进baseline；旧未结束已提交授予任一受影响则整份失效；员工停用/失去任职同理 | 执行先提交则关闭随后失效新grant；关闭先提交则旧授予拒绝；恢复不倒退周期 |
| I-14 | oa_link(request)PK及(instance,approvalId)UQ；回调(instance,eventId)UQ；oa_final_result(request)PK | T10只接受可信实例，核对关联/摘要/版本；同event不同payload冲突，不同event同最终结果重复，新最终结果矛盾409；先存关联不能回调自绑 | 关联未落地409 OA_LINK_PENDING；接收未落地503；失效申请匹配迟到结果只留历史 |
| I-15 | execution_success(request)PK，唯一成功audit引用；request触发器要求成功凭据及一致最终结果存在 | T4业务全清单、关系/配置版本、成功审计、success、状态、释放claim同事务；失败尝试在回滚后另存，先锁再看成功凭据 | 两执行器顺序取得锚点，后者回读成功；HTTP超时不新建申请；不出现第二个业务成功事件 |
| I-16 | 幂等UQ(principal,method,path,key)，完成时authorization_basis非空JSON对象CHECK，状态/响应/租约CHECK，持久expires_at；完成记录UPDATE触发器禁止改写 | T0当前认证/范围之后、业务目标版本检查之前定位原结果；以固定目标范围复核当前单项资格，删除后也可授权；短事务预留摘要与租约，失败也不允许同key换内容；同token业务/授权事实/安全响应同事务；密码用HMAC | 处理中409、不同摘要409；租约过期仅同内容恢复；资格已失401/403不回放；失败结果不能覆盖成功；窗口过期不削弱长期业务防重 |
| I-17 | import_batch(source_system,source_batch_id)UQ；文件FK；sealed输入及行/报告不可变触发器；成功计数组合CHECK | T8预检冻结规范前后值/映射/候选code/依赖及不存在断言；正式提交逐条复核，全部1000行一个事务；明确动作顺序，不由缺行推删除 | 第N行失败全部回滚；唯一冲突/依赖变更STALE；FAILED保留原基线重试；成功原actualCounts长期回放 |
| I-18 | 预览主体/上下文与输入/内容摘要持久化；明细UQ(preview,kind,key)去重 | T11同快照生成统计/分页、5分钟；读取重新鉴权，变小范围使原快照失效；创建权限与当前权限交集，不能当凭证；完整授权计算后安全投影 | 过期/目标清单或无变化分类改变409 PREVIEW_STALE；不泄露隐藏来源、混合申请ID/计数 |
| I-19 | 审计append-only触发器；来源公司/部门FK；无级联；success唯一审计引用 | 每次成功动作与审计同事务；全局/混合不可分隔事件仅总管理员；关联申请也当前鉴权；历史名称/批准/执行区别快照 | 审计写入失败业务回滚；数据库不可用不宣称失败日志已落库；不倒推历史进入资格 |
| I-20 | 账号credential_version/session_epoch、session sid/jti/expiry/revoked_at持久；jtiUQ | T9退出单sid；改密/重置递增credentialVersion；停用/失全部资格递增sessionEpoch，恢复不回退；每请求查当前账号/员工/资格及上下文；写锁内最终复核 | JWT有效期内仍可立即拒绝旧会话；资格恢复只允许新登录；身份存储故障503而非放行 |
| I-21 | 全部FK即时RESTRICT，范围/任命/关系/审批/审计历史不级联删除 | T8部门跨公司迁移检查角色含草稿/关系含历史/任职含历史/范围任命/申请/审计/来源全部引用；系统/目录/权限无删除API | 新引用与迁移同锚点串行；有引用整个批次IMPORT_BLOCKED或基线陈旧STALE；以新组织迁移人员 |
| I-22 | API各业务版本分列；row_version独立列；所有版本不负且安全整数上界 | T12不以JPA自动版本代替metadata/configuration/source/relationship/status等；所有CAS判断在写事务内；NoChange遵循分类顺序 | 乐观锁或受影响行数非预期整批回滚，409或后台重新复核；不静默覆盖 |

## 删除、历史、保留与恢复

| 对象 | 策略与唯一键关系 |
| --- | --- |
| 公司/部门/岗位、员工、后台账号、范围 | 无业务删除入口，无统一deleted_at；编码/当前键保持自然唯一。员工/账号仅改变各自状态；组织无启停 |
| 任职、任命、授权关系 | 结束状态+新记录表达重建，ACTIVE槽唯一，NULL历史槽允许多段；ID和历史版本不复用 |
| 源与永久关系基线 | 永久保留，周期/关系版本不重置；不按当前有效授权数做清理 |
| 已提交申请/OA关联/回调/最终结果/执行凭据/配置历史/审计 | 保留，不提供清空入口；固定/append-only触发器阻止修改或删除。成功sourceBatchId长期防重 |
| 当前role_permission/scope_system及claim | 集合替换/释放在事务内物理增删；历史在固定申请、配置历史、审计，不为这些临时关系套软删除 |
| 未提交申请 | 仅DRAFT可删除：锁内先写删除快照，清claim和目标，再删申请。此前审计的change_request_id是有意无FK的历史ID，可指已删除草稿；UI显示“草稿已删除”。新角色还须从未提交/生效且无其他引用才可同事务删除；否则保留 |
| 预览 | 5分钟失效，可按到期批量删除preview_item再preview；不影响审计。清理仍按写锚点短事务、分页，不持无界锁 |
| 幂等 | 至少7天；过期可受控清理，OA/申请/sourceBatchId/基线防重独立保留，不随清理消失 |
| 文件与问题报告 | 文件本体、元数据及冻结报告暂不自动清理，期限/归档待运维确认；不擅自规定法规保留年限或设计前台删除接口 |

审计对已删除草稿的无FK历史引用是明确例外；导入候选target_id尚无实体亦不设多态FK。其余“有外键”不等于已保证所有业务归属：change_target的source/baseline/employee/grant互相对应、grant_evidence适用关系、scope与appointment同一原依据都要在T3/T4明确校验。DBA可绕过应用权限修改数据库；本项目不声称具备防DBA篡改的审计设施。
