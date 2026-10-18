# BE-05 HTTP幂等预留与安全重放

2026-10-18实施设计，用户明确授权执行已确认计划；BE-04/58b556a已本人确认。沿用API1.2.0、数据库1.1及V1～V3，不新增正式业务路由或改DDL。最终64离线＋42真实引擎检查全通过；本人确认与完整业务验收边界见后文。

代码入口：[幂等协调器](../../backend/src/main/java/com/adgn/backend/core/idempotency/IdempotencyCoordinator.java)、[启用及密钥装配](../../backend/src/main/java/com/adgn/backend/core/idempotency/IdempotencyAutoConfiguration.java)、[HTTP适配](../../backend/src/main/java/com/adgn/backend/web/IdempotencyHttp.java)。测试入口：[真实引擎](../../backend/src/test/java/com/adgn/backend/IdempotencyMySqlCheck.java)、[配置](../../backend/src/test/java/com/adgn/idempotencyfixture/IdempotencyConfigTest.java)、[HTTP](../../backend/src/test/java/com/adgn/idempotencyfixture/IdempotencyHttpTest.java)。

## 内部接口与边界

core/idempotency提供IdempotencyCoordinator：reserve先保存请求，complete持租约执行业务，execute组合两阶段。调用方传账号UUID、operationId、规范大写方法/资源路径、UUID键、规范请求字节；CanonicalRequestCodec只定义编码端口，正式领域负责目标/版本/业务参数规范化，固定申请RFC8785仍归BE-21。HMAC输入使用带长度前缀的operationId/method/path/body，协议版本固定；不保存原请求字节、密码或认证头。请求及密钥类型不输出内容到toString。

每个入口复用CommandBoundary，拒绝外部事务。资格回调在guard锁后重新读取当前账号/员工/会话/任命，返回ACCOUNT操作者且账号必须匹配请求主体；测试身份仅夹具。重放授权回调接收保存的AuthorizationBasis，必须用当前一项任命完整覆盖，不能依赖已删除目标。GLOBAL_ONLY/SCOPED/PRINCIPAL_ONLY分别表达全局操作、完整目标范围、安全通用错误；PRINCIPAL_ONLY禁止成功结果。

新键在短事务写IN_PROGRESS/HMAC/keyId/随机令牌，使用数据库UTC时间和60秒租约、至少七天保留。未过期同内容返回REQUEST_IN_PROGRESS及向上取整的正Retry-After；异内容IDEMPOTENCY_CONFLICT。过期同内容更换令牌，保留原摘要keyId；缺历史密钥DEPENDENCY_UNAVAILABLE，不当新请求执行。complete进入事务时检查同令牌及有效租约，执行完整业务/审计/安全结果，条件完成并清租约、保留期延至完成后七天；持guard期间其他处理者不能接管，不因回调耗时机械拆分事务。

COMPLETED先当前身份和保存事实授权，再匹配摘要并重放，业务回调不再执行或校验已推进的目标版本。旧令牌不覆盖已完成或已接管结果。成功提交后响应丢失由同键重放恢复。业务失败原事务整体回滚、预留不丢；只有显式安全错误类型且代码在白名单，才另开边界重新检查资格/令牌后保存确定4xx。401/403/503/未知异常不缓存，不自动重试；完整性失败沿BE-04分类。租约等待恢复，无自动删除任务，过期记录仍保留比较和授权。

## 安全结果与配置

SafeResult仅保存状态、已脱敏JSON正文或204空正文、受控相对Location；不保存任意HTTP头。内部JSON封装支持对象/数组/标量，safe_response顶层仍OBJECT。防御性拒绝密码/token/hash等敏感字段名；不能识别任意字符串中的秘密，领域必须提供安全投影，含一次性凭据的接口不得直接缓存原响应。HTTP适配仅允许Content-Type/Location/Idempotent-Replayed及错误Retry-After，X-Request-Id/no-store仍由现有请求链管理；缓存Problem请求ID在返回时绑定当前请求ID。

默认rbac.idempotency.enabled=false，不注册协调器或无保护替代入口。显式启用时要求当前keyId及Base64密钥环（各随机密钥至少32字节），使用HMAC-SHA256和恒定时间比较；缺失/短密钥以不含输入值的通用错误拒绝启动。密钥使用独立环境变量注入，不复用数据库或JWT密钥；独立迁移及离线模式不强制配置。测试用临时随机密钥，不写共享文档。

## TDD验收清单

先记录有效RED，再实现和回归：摘要/配置/密钥轮换；同键内容冲突和不同主体/路径隔离；并发最多一次；预留后中断/到期接管/旧令牌拒绝；业务/审计/结果失败回滚且内容预留保留；已提交丢响应/删除后204重放/当前资格撤销拒绝；安全错误缓存与503不缓存；HTTP两类409、Retry-After、重放/Location/当前请求ID及敏感字段拒绝。

真实检查仅在新建MySQL8.4临时datadir、非3306端口运行，先核验实例身份，再随机schema/账号，清理后关闭专属实例。用户现有库、口令和全局参数不操作。保留原51离线＋25引擎回归，实际新增数以最终报告为准。AT-04只公共机制PARTIALLY_VERIFIED，正式身份/草稿/权限业务与基座整体验收不扩大。

## 实施约定补充

SafeFailure仅允许VERSION_CONFLICT、CONTEXT_CHANGED、STATE_CONFLICT及VALIDATION_ERROR四种通用错误，不携带原异常、输入值或隐藏目标明细。普通业务回调只能完成2xx，不能直接返回4xx绕开回滚；只有业务回调内部抛出的SafeFailure才进入回滚后的缓存流程，资格/授权回调抛出的错误直接拒绝。失败结果使用标准Problem字段，requestId返回时绑定当前HTTP请求。

AuthorizationBasis以operationId、policy和完整targets列表保存；targets中每项为companyId/departmentId/systemId。领域需把原目标、整份申请查看以及安全响应披露所需的全部范围合并为该集合。GLOBAL_ONLY表示当前总管理员资格，SCOPED要求非空完整三元组、当前一项任命覆盖全部，PRINCIPAL_ONLY只用于白名单通用错误且targets为空。资格失败不得以此降级绕过。完成记录不因过期自动覆盖；长期OA/执行/sourceBatchId防重完全独立。

HTTP安全结果只保存body封装、status和相对Location；没有任意headers输入，成功为JSON或204。SafeResult递归拒绝敏感字段名，JSON拒绝重复键和尾随内容；它不是任意文本秘密检测器。一次性凭据响应需要所属业务任务另定符合契约的安全重放投影，不能把原凭据响应交给本追加器。

所有时刻来自数据库UTC_TIMESTAMP(6)，租约与保留期不依赖某个应用节点的本机时钟；读取DATETIME显式UTC绑定，测试JVM为Asia/Shanghai。HMAC输入为版本字节1，再依次写operationId、method、path、canonicalBody的四字节大端长度与字节。CanonicalRequestCodec只定义端口，不提供伪JCS实现，领域改变编码规则前须考虑现存幂等记录兼容。

## TDD执行记录

- 摘要/响应值对象：3项测试在占位实现上全部断言失败，0错误；实现后3项全通过。
- 幂等真实引擎：实例被中断停止导致的一次连接拒绝只算环境排障；重启同一专属实例后，10项为2断言失败＋8项UnsupportedOperationException（协调器未实现），不是引擎语法错误。实现后10项全部通过。
- 配置与HTTP：8项中7失败、0错误；默认关闭测试原已通过，启用装配/两类409/结果重放此前未实现。实现后该组全部通过。
- 安全反例：重复/尾随响应JSON未拒绝，离线9项中1失败；普通业务直接返回4xx仍提交，真实15项中1失败。为一次收集两组报告仅本次RED开启maven.test.failure.ignore，外层BUILD SUCCESS不算通过；以JUnit失败报告为准。补严格JSON、限制正常完成只能2xx后通过。
- 原完整回归捕获测试配置假数据源被另一上下文扫描，64离线中1失败；把配置/HTTP夹具迁至应用扫描范围外，原运行配置检查恢复通过。测试夹具只在src/test，不进入jar。
- 资格失败反例：资格函数抛安全错误被误当业务错误缓存，1项失败；仅捕获业务回调内的显式SafeFailure后通过。真实17项中的最后一项还证明错误DataSource装配能逃离原事务，1项失败；所有入口增加原WriteContext同数据源断言，修复后执行最终全量回归。

原始输出位于被忽略的backend/logs/build/be05-red-values.log、be05-red-engine.log、be05-red-config-http.log、be05-red-hardening.log、be05-red-qualification.log、be05-red-datasource.log和be05-verify.log。XML在target/surefire-reports与failsafe-reports。首次GREEN和此前64＋41完整回归是阶段证据，不代替最后一次结果；最终汇总在下节记录。

## 最终验证与交付边界

2026-10-18 15:03（Asia/Shanghai）完整执行`./mvnw.cmd --batch-mode --no-transfer-progress -Pmysql-check clean verify`，BUILD SUCCESS，约2分12秒。Surefire 64项、Failsafe 42项，均0失败/0错误/0跳过。新增13离线（值对象4、配置5、HTTP4）和17项幂等引擎检查，原51离线＋25引擎全部回归；最终GREEN后未再修改Java代码。报告按XML汇总，未以RED期间忽略失败的进程退出状态充当通过。

真实17项覆盖原子完成/丢响应重放、受检失败保留预留、到期接管/旧令牌、删除后204及撤销资格、完成验证失败/真实SQL完成失败回滚业务审计、白名单错误另事务保存/503不缓存、缺历史密钥拒绝、两阶段并发及业务持锁时实际data_lock_waits后重放、主体/方法/路径隔离、阶段间资格变化/过期令牌、资格错误不缓存，以及错误DataSource不能逃离原事务。测试中身份、范围和删除对象均明确为夹具，不是真实JWT/管理员范围/草稿API。

正式jar SHA256：`a734b1e8acbd4e7cd5acfe542b48b7fdd4e36870fa3f22b8a8e6367169f57f7b`。包中含幂等协调器和自动配置，不含idempotencyfixture/MySqlCheck或其他测试夹具；本轮未另做独立jar HTTP部署验收。Flyway对MySQL8.4验证版本的既有WARN保留，不因测试通过声称所有版本兼容/云端部署已验收。

收尾核验临时服务端datadir与本轮目录完全一致、随机RBAC schema数为0，再mysqladmin正常关闭并确认本轮进程退出。用户已有3306服务、开发/测试库及口令未操作/读取，未修改全局设置。日志、数据目录、target和无关.codeartsdoer本机文件不入Git。

静态设计/任务/来源校验PASS：309处本地链接、48个唯一决策、生成器确定性及冻结API/DDL/迁移来源一致。改动UTF-8、Markdown围栏/无本机绝对路径和Git空白检查通过；该静态校验不执行SQL/HTTP/业务测试。代码、测试与文档同一提交，未合并或推送。

BE-05保持IN_PROGRESS，等待用户本人按交付记录确认；AT-04仅PARTIALLY_VERIFIED，全部基座门槛仍NOT_EVALUATED。无需清库或重跑迁移；默认关闭时保留原启动配置，启用核对按backend/README添加独立HMAC环境变量后重启看健康UP。UP只证明本机装配/启动，不替代上述幂等行为测试。下一步是本人确认BE-05、核对BE-01剩余环境/协作条件及整体基座交付，再由用户安排共同主线与业务分派，不自行启动BE-06。

## 本机启动回报（2026-10-18）

2026-10-18 15:28用户本机运行核对：最新日志显示BackendApplication/dev成功连接localhost:3306/rbac_admin_test（MySQL8.4.8），Hikari与JPA初始化完成、Tomcat8080启动，GET记录200；用户明确确认健康status=UP。此前15:27的Connection refused已不再出现在本次日志，原失败不能解释为HMAC错误。日志未展示RBAC_IDEMPOTENCY_ENABLED或幂等Bean装配，故只确认服务启动/健康，不由UP推断HMAC启用已验证；当前等待用户仅确认开关是否true，不读取IDE口令/密钥。BE-05保持IN_PROGRESS及待本人确认，既有64离线＋42引擎结果沿用，基座总门槛不变。agent本轮只读用户日志并同步文档，未连接数据库、执行SQL或运行新Java测试。
