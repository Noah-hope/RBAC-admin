# 模块结构与维护责任

均为详细设计，尚未创建Java工程。公共包前缀取`com.noah.rbacadmin`（技术取值）。A～D是待团队映射的职责槽位，不是已分配的真实账号。

| 模块 | 包后缀/负责人 | 编译依赖 | 职责与主要接口 |
| --- | --- | --- | --- |
| M00 事务、幂等、审计追加与公共契约 | `core` / A | 无 | CommandBoundary/ReadBoundary、WriteContext、HttpIdempotency、AuditAppender、CanonicalPayloadCodec；只定义公共能力，不调用具体业务服务 |
| M01 后台账号、JWT与会话 | `identity` / A | M00,M11 | LoginService、AccountService、SessionRevoker；当前身份SQL投影，不以JWT内范围作为授权 |
| M02 组织、员工与任职基础操作 | `people` / D | M00,M11 | OrganizationReader、EmploymentWriter；维护稳定ID/映射及单项状态，跨域失效由M10编排 |
| M03 范围与管理员任命基础操作 | `scope` / A | M00,M11 | ScopeWriter、AppointmentWriter、ScopeCoveragePolicy；资格读取投影，不回调人员命令服务 |
| M04 系统、目录和权限 | `catalog` / B | M00,M11 | CatalogCommandService、FinalTreeValidator；同系统/无环/进入槽/菜单绑定及稳定排序 |
| M05 角色元数据与有效配置 | `role` / B | M00,M11 | RoleWriter、RoleConfigurationApplier；只接受受控写上下文，有效配置仅由M09调用应用器 |
| M06 来源、永久关系基线与授权历史 | `grant` / D | M00,M11 | BaselineWriter、GrantApplier、SourceCloser；返回失效事实，不自行调用申请服务 |
| M07 草稿、固定申请与占用 | `change` / C | M00,M11,M05,M06 | RoleDraftCoordinator、ChangeService、ClaimService、WorkflowStateWriter、RequestInvalidator；提交同事务创建outbox事实，不调用发送器 |
| M08 OA投递、关联和最终结果接收 | `approval` / C | M00,M11,M07 | DeliveryWorker、OaSubmissionPort、FinalResultReceiver；网络/模拟受理在事务外，可靠结果独立提交 |
| M09 已批准申请自动执行与恢复 | `execution` / C | M00,M11,M03,M04,M05,M06,M07 | ExecutionWorker、ExecutionCoordinator、FailureRecorder；全清单预检后全写，一份申请一次成功 |
| M10 关权、员工/范围变更及HR原子导入 | `lifecycle` / D | M00,M11,M01,M02,M03,M06,M07 | LifecycleCoordinator、ImportPlanner/Committer；统一编排来源/任命/会话/申请持久失效；scope/员工入口由A共同实现 |
| M11 安全读取、有效权限与影响投影 | `query` / B | M00 | IdentitySnapshotReader、QualificationReader、EffectivePermissionEvaluator、ImpactService、AuditReader；按功能拆投影文件，可读多域表，不调用各域命令或引用其JPA实体 |
| M12 HTTP适配与装配入口 | `web` / A | M00,M01,M02,M03,M04,M05,M06,M07,M08,M09,M10,M11 | 按功能分控制器/DTO；路由到应用服务，不直接改实体；每个控制器由对应Issue成员维护，A负责公共装配 |

模块依赖为编译/调用契约方向；通过持久状态唤醒的后台扫描不构成服务互调。M11采用独立SQL/DTO投影，不引用各域JPA实体，不调用命令模块。当前身份读取投影由A维护，组织/来源投影由D维护，OA/申请投影由C维护，权限/影响纯计算由B维护，避免一个巨大QueryService成为共用编辑热点。

共同事务与接口约定见[ARCHITECTURE.md](ARCHITECTURE.md)，具体步骤见[CORE-FLOWS.md](CORE-FLOWS.md)。
