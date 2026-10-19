# BE-07 后台账号管理与密码重置

2026-10-18：用户A在BE-06本人确认后要求开始BE-07（D-058）。在codex/be-07-accounts上继承BE-05及已确认的BE-06；该显式继续开发授权允许在同一开发谱系推进，不伪称BE-06已合入main，不自行合并或推送。本文先记录设计与验收，结果随实际执行补齐。

## 范围与调用关系

沿用UC-09、API1.2.0的listAccounts/createAccount/getAccount/setAccountState/resetAccountPassword，以及数据库admin_account/admin_session/employee/http_idempotency/audit_event，不改迁移。Controller/DTO位于web/identity，AccountService和AccountRepository分别位于identity/service及repository；复用CurrentIdentityReader、PasswordPolicy、CommandBoundary、ReadBoundary、IdempotencyCoordinator和AuditAppender，不另建认证或事务机制。

所有办理者须为正常会话的当前总管理员，读操作在同一个RR快照复核，写操作在每个guard事务内复核；普通权限管理员列表/写入403，详情按契约统一404。目标只能是权限管理员账号，不能通过创建或写接口升级、停用或重置任何总管理员。

## 写流程与安全

- 创建：结构/密码确认校验→当前总管理员检查→规范内容HMAC预留→事务外慢哈希→guard下重新资格检查→员工ACTIVE、登录名及员工唯一→新账号、GLOBAL审计和幂等安全结果同事务提交。enabled=true、mustChangePassword=true，业务/凭据/周期初值1；不创建任命或目标会话。
- 启停：expectedVersion须匹配；同状态不推进版本、不追加重复成功审计。实质停用推进version/sessionEpoch并撤销全部会话；启用仅改账号状态与version，不恢复任命/授权或旧会话。拒绝全部总管理员目标。
- 重置：事务外慢哈希；guard下版本及目标检查，更新摘要、强制改密、credentialVersion/sessionEpoch/version，撤销全部旧会话；账号enabled保持原值，员工/任命/业务授权不变。临时密码由总管理员设置，不返回或缓存。
- 每次写入均接入BE-05预留/令牌栅栏/安全重放；关闭幂等时写接口503，不能直通。请求规范字节明确包含操作、路径、目标、版本、密码及确认值、原因，按类型固定顺序编码，不称为RFC8785；秘密只在短期内存参与HMAC，不落幂等表/审计/响应。GLOBAL_ONLY授权事实用于重放复核当前总管理员，不重新验证已推进的目标版本。
- 确定性版本/唯一性/状态/业务校验错误按既有白名单独立提交；401/403/404/503及未知异常不缓存。业务/审计/安全结果任一失败全部回滚，预留保留内容绑定。
- Account只含契约七字段（含布尔mustChangePassword）；公共SafeResult仅对准确名称且布尔类型的mustChangePassword放行，其他password/token/credential等敏感字段继续拒绝，先以失败测试证明接入缺口。

## 读与HTTP

列表默认page=1/pageSize=20（已核对契约），pageSize仅10/20/50/100，支持employeeId、登录名前缀q、enabled；前缀按字面匹配，SQL参数化，createdAt/id稳定排序，count和items共享RR快照。详情只返回安全投影。HTTP使用no-store；创建201及Location，状态/重置200；重放Idempotent-Replayed=true，当前X-Request-Id保持新请求值，安全Problem不重放旧requestId。MySQL JSON字段可能调整属性顺序，重放核对JSON字段和值，不把对象属性顺序当业务差异。

## TDD与验收顺序

1. SafeResult布尔标志真实RED，最小放行及敏感嵌套/错误类型GREEN；真实MySQL创建端点先RED再实现。
2. 创建约束、资格、无秘密/无额外授权、同键重放与内容冲突、列表分页/过滤及详情边界。
3. 停用/启用/重置旧会话失效，强制改密、无资格仍拒绝、总管理员保护、版本和同状态幂等。
4. 审计及结果保存故障回滚、并发重复/唯一性、资格撤销后重放拒绝、原版本变化仍可重放；保留全部旧回归。
5. 完整clean verify与显式mysql-check，真实数据库仅新建MySQL8.4独立datadir、非3306端口，写前核验实例，结束清理schema/用户/专属进程和临时密钥。不使用已有开发/测试库或IDE秘密。

BE-07已按D-059登记OWNER_ACCEPTED：用户A明确“BE-07验证通过”，随后确认健康UP与未登录账号入口401两项本机检查通过；接受版本584eb4e8365b0041c09ccf3f76fcaf284bb32e26。结合下述75＋80自动化证据，由负责人本人确认；不推断本人已手工成功登录/创建/重置或重跑全部测试。BE-06/07仍待主线交付，AT-06/07仍按所属部分记录，不扩大为完整跨域验收。

## 本机与团队使用

继续使用BE-06的RSA/JWT和BE-05的HMAC配置，无需生成新密钥，也没有新增迁移。身份开启后账号读接口工作；账号写接口还要求RBAC_IDEMPOTENCY_ENABLED=true及原HMAC密钥环，关闭幂等返回503。正常启动沿用rbac_app，不使用迁移/root身份。

| 操作 | HTTP | 输入/响应 |
| --- | --- | --- |
| 账号列表 | GET /api/v1/admin/accounts | page/pageSize/employeeId/q/enabled；AccountPage |
| 创建权限管理员账号 | POST /api/v1/admin/accounts | AccountCreate及UUID Idempotency-Key；201 Account和Location |
| 账号详情 | GET /api/v1/admin/accounts/{accountId} | 200 Account；不存在或普通权限管理员详情请求404 |
| 启停账号 | PUT /api/v1/admin/accounts/{accountId}/state | AccountStateInput及幂等键；200 Account |
| 重置临时密码 | PUT /api/v1/admin/accounts/{accountId}/password | PasswordReset及幂等键；200 Account |

所有接口使用BE-06正常总管理员Bearer会话，写入字段/状态/错误以[正式API契约](../03-api/openapi.yaml)为准。重试保持原幂等键和所有原字段（含密码、原因和expectedVersion），不要把重放响应中的历史版本当作最新账号状态；需要当前状态另查详情。新操作使用新键并读取新version。临时密码由操作者另行安全交付，接口响应、幂等结果、审计都不回显。

本人可在IDEA重新加载Maven，运行VerifyBackend的clean verify；再重启原BackendApplication核对健康UP和未认证账号访问401。健康仅证明装配可用，登录/账号办理的真实行为见隔离数据库测试。没有初始总管理员时不能直接手工办理；初始化仍归BE-32，本轮不向已有开发/测试库写夹具或创建临时正式接口。组员沿用web/identity的Controller/DTO和identity的Service/Repository布局，不复制账号凭据写逻辑。

## 实际验证与交付边界

2026-10-19 00:31:05（Asia/Shanghai）完整`./mvnw.cmd -Pmysql-check clean verify`为BUILD SUCCESS，用时3分21秒。JUnit汇总：**75项离线＋80项真实MySQL检查，失败/错误/跳过均0**；新增1项SafeResult行为测试及21项AccountMySqlCheck，原74＋59全部保留。没有使用H2或静默跳过。JDK21以进程环境显式选择，TEMP/TMP指向backend/.tmp，未修改永久环境、IDE或读取本机密钥。

| 验证组 | 真实证据 |
| --- | --- |
| SafeResult RED/GREEN | 原实现对合法布尔mustChangePassword拒绝，5项单元中1失败0错误；最小修正后5项全过，仍拒绝同名字符串/null/对象/数组/数字、别名及嵌套密码。原始本机输出`.tmp/be07-red-safe-result.log`。 |
| 创建RED/GREEN | 正式HTTP创建预期201、实现前404，1失败0错误；实现后同项通过。`.tmp/be07-red-account-create.log`与`.tmp/be07-green-account-create.log`。 |
| 后续行为RED | 16项新增组中11失败0错误；9项由未实现的读取/状态/重置接口返回404触发，另外2项是测试错误地比较JSON属性顺序。后两项改为比较字段和值，不将测试修正伪称产品缺陷。最终21项全部通过，见`.tmp/be07-account-complete-check.log`。 |
| 账号读写 | 仅当前正常总管理员可读列表/办理；普通权限管理员详情与不存在统一404。员工必须ACTIVE、登录名/员工关联唯一，创建不留任命或目标会话；分页、字面前缀、员工/状态过滤及安全字段通过。 |
| 状态与重置 | 停用撤销所有旧会话，启用不复活会话/增加任命，无资格仍不能登录；同状态不推进版本/审计，陈旧版本409；任何总管理员目标拒绝。重置后受限改密、空格保留、再次改密后重登通过，停用账号重置不启用。安全整数上限先拒绝。 |
| 幂等与安全 | 创建/重置重放不重复写凭据或审计；原版本已推进仍返回原安全结果，同键密码变化冲突；4并发重复只落1账号/1审计。缓存Problem使用当前请求ID；账号失效401、失去全局身份但普通资格仍有效403，均无缓存正文/Location。密码不入响应/安全结果/审计快照；关闭幂等写503、授权读可用。 |
| 事务与并发 | 审计注入失败时账号创建/密码重置全回滚；幂等结果保存失败时状态、会话撤销、审计全回滚，预留仍为IN_PROGRESS且内容绑定保留，过期后同内容可重试。真实第二连接在guard等待期间撤销调用者，最终401且不写账号/预留。 |
| 完整回归与产物 | `.tmp/be07-final-complete-verify.log`及target/surefire-reports、failsafe-reports证明75＋80全绿。jar包含AccountController，不含identityfixture/MySqlCheck；SHA-256为`ee23792678c2d91fbdcd8dd96ba5eb851356dd5b8303c699853b572acb658724`。 |
| 实例与清理 | 专属MySQL8.4.8、非3306端口、backend/.tmp独立datadir，写前核验datadir/version/binlog=0。完成时查询自建schema=0、随机用户=0，核对PID/命令行/目录/端口后关闭专属进程，剩余be07临时密钥目录=0；已有开发/测试库未操作。 |

原始输出和临时文件由Git忽略，仅提交代码、测试和本记录。保留Flyway对MySQL8.4的验证版本WARN及Hibernate已有incubating提示，不将其隐藏或改成跳过。现有UC/API/数据库/迁移无变更；实施轮未创建Apifox或远程Issue/PR、未连接真实OA/HR、未合并/推送。当时待A本人确认，后续已按D-059确认；AT-06/07保持局部验证，初始总管理员及完整范围/员工生命周期留所属任务。

静态收尾PASS：58项唯一决策、387处本地链接、35任务/68操作主责及生成器确定性、原技术依赖/契约/表/估算不变、已确认BE-06祖先包含关系及BE-07待本人确认状态一致；Git空白核对通过。静态检查本身不启动数据库或运行业务测试，真实运行结果以上述JUnit和本机输出为准。
