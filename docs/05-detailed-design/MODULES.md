# 模块结构与维护责任

D-079（2026-10-20）：A按已交付BE-05/08/17开始BE-09，创建预览及范围/系统集合/任命的原子保存已实现，使用JPA写仓储、BE-17固定预览与公共guard/幂等/审计。完整112离线＋205真实MySQL/HTTP及静态校验通过；当前IN_PROGRESS待A本人确认，未交付main；验证和本机步骤见[BE-09](BE-09-SCOPE-CREATION.md)。共9项OWNER_ACCEPTED、3项IMPLEMENTED、6项IN_PROGRESS、17项PLANNED，其他成员人工验收状态不变；AT-08/31/37只补本任务部分证据，不代表BE-27/33完成。下方旧状态/数量为历史。

D-078（2026-10-20）：D明确要求先合并推送，BE-14来源367cdae已保留历史合入远程main/c7435d9，分支已推送并保留，远程完整SHA回查一致，合并树等于验证来源。沿用完整112离线＋193真实MySQL/HTTP证据，本轮静态检查通过，未重复执行Java/SQL。BE-14仍为IMPLEMENTED、ownerAccepted=false、mergedToMain=true，BE-28仍已交付待本人确认；下方未授权/待主线为历史。AT-13补本任务证据，AT-14/15仅读取局部验证，BE-16/26执行竞争不在本任务。当前共9项OWNER_ACCEPTED、3项IMPLEMENTED、5项IN_PROGRESS、18项PLANNED，下方旧状态/数量为历史。见[BE-14](BE-14-GRANT-BASELINES.md)。

D-076（2026-10-20）：BE-28 已接入最新 BE-17/12 后完整110离线＋178真实MySQL/HTTP通过，并由233f633保留历史合入远程main/4b384fb，远程SHA回查一致，分支保留。Git连接失败后使用官方API按本地SHA原样交付、force=false。BE-28仍待本人确认，mergedToMain=true；交付不代记人工验收。当前共9项OWNER_ACCEPTED、2项IMPLEMENTED、5项IN_PROGRESS、19项PLANNED。D原BE-28实施编号因与B冲突改为D-075，原事实保留；下方旧数量/未授权为历史。见 [BE-28](BE-28-IMPORT-FILES.md)。

D-074（2026-10-20）B 状态：BE-17/12 已由 1fb95ad 无冲突合入并交付远程 main/3103b35，任务分支保留，完整 SHA 回查一致，合并树等于已验证源。完整 93 普通＋169 真实 MySQL/HTTP 通过，本轮合并后 93 普通测试再次通过。任务待本人确认，不代记 OWNER_ACCEPTED。BE-11/13/15 原交付保留；共 9 项 OWNER_ACCEPTED、1 项 IMPLEMENTED、5 项 IN_PROGRESS、20 项 PLANNED。AT-09/10/11/12/31/32 仅局部验证，不代表全部消费者/OA/BE-33 完成。BE-18 仍缺 BE-14，BE-33/35 仍开放。详见 [BE-17/12 实施及交付](BE-17-12-PREVIEW-CATALOG.md)，下方更早数量/待交付说明为历史。

修订1.4/D-055：基座已确认并纳入主线，30项任务已登记A～D成员代号，实名与GitHub账号映射待补。D-056用户认领A，BE-06为OWNER_ACCEPTED（D-057，已由D-060交付main）、BE-07为OWNER_ACCEPTED（D-059，已由D-060交付main）、BE-32为OWNER_ACCEPTED（D-062，已由D-063/PR#1交付main），BE-10为IMPLEMENTED（六个查询接口及内部读写端口已实现，已由D-065交付main，待负责人验收），BE-08为OWNER_ACCEPTED（D-068，已由D-069/PR#2交付main），其余25项PLANNED，由负责人自行确认完成（D-051）。分工详见[四人分工](TEAM-ASSIGNMENT.md)。公共包前缀`com.adgn.backend`、13个逻辑模块及技术依赖不变。

| 模块 | 包后缀/负责人 | 编译依赖 | 职责与主要接口 |
| --- | --- | --- | --- |
| M00 事务、幂等、审计追加与公共契约 | `core` / 用户（基座） | 无 | CommandBoundary/ReadBoundary、WriteContext、HttpIdempotency、AuditAppender、CanonicalPayloadCodec；只定义公共能力，不调用具体业务服务 |
| M01 后台账号、JWT与会话 | `identity` / A | M00,M11 | LoginService、AccountService、SessionRevoker；当前身份SQL投影，不以JWT内范围作为授权 |
| M02 组织、员工与任职基础操作 | `people` / D | M00,M11 | OrganizationReader、EmploymentWriter；维护稳定ID/映射及单项状态，跨域失效由M10编排 |
| M03 范围与管理员任命基础操作 | `scope` / A | M00,M11 | ScopeWriter、AppointmentWriter、ScopeCoveragePolicy；资格读取投影，不回调人员命令服务 |
| M04 系统、目录和权限 | `catalog` / B | M00,M11 | CatalogCommandService、FinalTreeValidator；同系统/无环/进入槽/菜单绑定及稳定排序 |
| M05 角色元数据与有效配置 | `role` / B | M00,M11 | RoleWriter、RoleConfigurationApplier；只接受受控写上下文，有效配置仅由M09调用应用器 |
| M06 来源、永久关系基线与授权历史 | `grant` / D | M00,M11 | BaselineWriter、GrantApplier、SourceCloser；返回失效事实，不自行调用申请服务 |
| M07 草稿、固定申请与占用 | `change` / C | M00,M11,M05,M06 | RoleDraftCoordinator、ChangeService、ClaimService、WorkflowStateWriter、RequestInvalidator；提交同事务创建outbox事实，不调用发送器 |
| M08 OA投递、关联和最终结果接收 | `approval` / C | M00,M11,M07 | DeliveryWorker、OaSubmissionPort、FinalResultReceiver；网络/模拟受理在事务外，可靠结果独立提交 |
| M09 已批准申请自动执行与恢复 | `execution` / C | M00,M11,M03,M04,M05,M06,M07 | ExecutionWorker、ExecutionCoordinator、FailureRecorder；全清单预检后全写，一份申请一次成功 |
| M10 关权、员工/范围变更及HR原子导入 | `lifecycle` / A、D按任务分别负责 | M00,M11,M01,M02,M03,M06,M07 | LifecycleCoordinator、ImportPlanner/Committer；统一编排来源/任命/会话/申请持久失效；scope/员工入口由A共同实现 |
| M11 安全读取、有效权限与影响投影 | `query` / A、B、C、D按任务分别负责 | M00 | IdentitySnapshotReader、QualificationReader、EffectivePermissionEvaluator、ImpactService、AuditReader；按功能拆投影文件，可读多域表，不调用各域命令或引用其JPA实体 |
| M12 HTTP适配与装配入口 | `web` / A、B、C、D按任务分别负责 | M00,M01,M02,M03,M04,M05,M06,M07,M08,M09,M10,M11 | 按功能分控制器/DTO；路由到应用服务，不直接改实体；每个控制器由对应Issue成员维护，A负责公共装配 |

M00的公共能力由用户实现；M11的读事务约定、M12的错误/配置/装配基础也属于BE-02/04的基座部分，不意味着全部查询/HTTP业务由用户承包。M00中规范化codec先明确接口，固定申请的JCS实现和向量验证仍归BE-21。

M11采用独立SQL/DTO投影，不引用各域JPA实体或命令模块。业务阶段按A～D的对应任务分别维护身份、人员来源、申请、权限投影，避免一个巨大QueryService成为共用编辑热点。

共同事务与接口约定见[ARCHITECTURE.md](ARCHITECTURE.md)，具体步骤见[CORE-FLOWS.md](CORE-FLOWS.md)，阶段交接见[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)。
