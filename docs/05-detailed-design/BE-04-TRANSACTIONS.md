# BE-04公共事务、快照与审计追加

2026-10-18，D-046。用户在BE-03全部技术核对后回复“继续”，按上下文记录BE-03阶段本人确认并推进BE-04。BE-03实现b8caeb2，运行/账号证据截至56ea3a3；不代表全基座验收或允许合并/推送。需求V1.3/API1.2.0/数据库1.1不变，BE-05幂等及正式身份/授权/OA/导入业务仍未实现。

## 交付与调用约定

| 公共入口 | 实际行为与调用方责任 |
| --- | --- |
| [CommandBoundary](../../backend/src/main/java/com/adgn/backend/core/transaction/CommandBoundary.java) | 只允许最外层调用；新RC事务先锁mutation_guard(1)，锁后运行当前资格函数，生成WriteContext，再执行业务；审计、业务及guard.revision同提交。拒绝已有事务中的嵌套边界，不为每项批量开新事务，不自动重试 |
| [WriteContext](../../backend/src/main/java/com/adgn/backend/core/transaction/WriteContext.java) | 构造器包内可见；绑定原线程、原受管连接、事务及内部资源标记，结束/跨线程/被独立事务挂起后拒绝使用。提供当前资格载荷、审计操作者、UTC微秒Clock与UUID v4生成；不是HTTP DTO或权限凭证 |
| [ReadBoundary](../../backend/src/main/java/com/adgn/backend/core/transaction/ReadBoundary.java) | 每次独立RR只读事务；先非锁定读取guard建立InnoDB快照，再同连接查询当前身份/资格及全部数据。旧快照不追随中途写入，新读取看见已提交状态；不持写锁，不复用旧EntityManager事务 |
| [AuditAppender](../../backend/src/main/java/com/adgn/backend/core/audit/AuditAppender.java) / [AuditRecord](../../backend/src/main/java/com/adgn/backend/core/audit/AuditRecord.java) | 必须持有同DataSource的有效WriteContext；操作者来自锁后身份回调，事件/目标/来源及subjects由领域提供。事件和subject逐项写入同一事务；异常使上下文rollback-only，即使调用方捕获也不能提交部分业务或审计 |
| [自动装配](../../backend/src/main/java/com/adgn/backend/core/transaction/TransactionBoundaryAutoConfiguration.java) | DataSource及PlatformTransactionManager就绪后注册，使用实际JpaTransactionManager使JPA/JDBC共用连接；离线test与独立迁移入口没有运行DataSource，不装配这些入口 |
| [PersistenceFailure](../../backend/src/main/java/com/adgn/backend/core/transaction/PersistenceFailure.java) | 锁等待1205/死锁1213、连接08类、暂时数据访问和事务失败归DEPENDENCY_UNAVAILABLE；非暂时数据库约束/缺guard归DATA_INTEGRITY_ERROR；普通业务异常保留原类型，受检异常整体回滚后原样抛回 |

CommandBoundary.Identity<A>中的A是调用域的已复核资格投影扩展点，不是已经实现的完整ActorContext/JWT解析。调用方必须在资格函数内用同一数据源读取当前账号、员工、会话及一项任命/范围并作授权；不得把HTTP body或旧JWT范围直接包装成Identity。基础测试只用明确标注的SERVICE夹具，不声称真实鉴权或权限业务已实现。

领域内受控写端口复用已有context并先requireActive，不再次调用CommandBoundary。Clock可由Spring注入，默认UTC；只为具有实际需要的内部调用暴露UUID生成，不新增业务编码策略。guard.revision只作内部提交序号，不替代任命/范围/来源/关系等API版本，也不用于让所有预览无差别过期。无变化业务可保持API版本；成功边界仍可推进内部revision。

同一事务混用JPA与JDBC时，若后续JDBC语句或即时外键依赖尚未写出的JPA新行，领域编排须先在原事务flush，再追加依赖记录；flush不等于提交。AuditAppender不自动flush所有实体，也不负责领域插入顺序。提交阶段失败测试采用不依赖待插入实体外键的GLOBAL事件，不能据此省略实际领域的flush顺序。

## 锁、原子性与恢复

运行连接初始化采用单条`SET time_zone = '+00:00', innodb_lock_wait_timeout = 5`，同时设置UTC和会话级5秒InnoDB锁等待；没有修改全局MySQL配置或迁移凭据。锁等待超时/死锁会退出并回滚整个外层事务，HTTP用既有503 DEPENDENCY_UNAVAILABLE安全Problem，不能返回NO_CHANGE、无权限或部分成功。失败分类不把存储row_version冲突机械当成API业务版本冲突。

锁后资格→完整清单校验→全部业务写/完整审计→revision条件更新→COMMIT。缺guard拒绝，不临时补建；资格失败不进入业务。受检异常和RuntimeException均整体回滚；JPA在提交阶段才flush失败时，已执行的JDBC业务、审计及revision也回滚。任何内部审计错误会标记rollback-only，不能catch后跳过坏项。

CommandBoundary结束后上下文失效，不能存入后台队列或发给另一线程。禁止控制器/内层服务擅自REQUIRES_NEW分拆目标；上下文断言能拒绝在另一活动连接复用旧能力，但不能阻止任意不遵守架构的Java代码直接操作DataSource。领域仍须按统一锁顺序、全量校验、无网络持锁等设计接入，不宣称这是数据库用户访问控制替代品。

失败审计不是公共入口自动编造：调用方在原execute完全抛出并回滚后，再进入新的execute，先复核当前success/状态/资格，才追加允许的失败记录；数据库仍不可用时不得宣称已落库。测试只证明该公共机制，M09执行失败恢复和BE-05幂等错误缓存由后续模块实现。

## 审计与读取边界

AuditRecord.snapshotJson必须是schemaVersion=1的JSON对象，subject列表在构造时固定；字段长度沿数据库约定按Unicode code point校验。事件时间以UTC Calendar绑定DATETIME(6)，避免JVM时区二次转换。GLOBAL/SCOPED、可见来源、历史目标和原申请人字段分别保留；来源外键及SCOPED完整性仍由真实数据库约束检查。

调用方必须提供已脱敏快照：密码、JWT、服务密钥、完整手机号或回调凭据不得传入；追加器不会识别任意文本里的秘密，也不负责审计查询的当前可见性。BE-19负责查询与响应投影；不把内部完整auditId/混合事件直接回传权限管理员。读取回调必须在事务内完成DTO投影，不返回待序列化的懒加载实体；OSIV继续关闭。

## TDD及证据范围

有效RED：11项真实MySQL事务检查全部失败、0错误，观察到没有RC事务、业务未回滚、没有审计、跨线程context未拒绝、两个写者不等待、读取不是只读RR；存储分类/HTTP测试共33项中6失败、0错误，含503误报500。一次AssertJ泛型编译问题先修正，不记为有效RED。

首轮GREEN：完整51离线＋20引擎检查通过；随后补充写者先回滚、另一事务复用context、非法审计快照、只读拒绝写及运行中guard丢失反例。最终结果见下方收尾记录。所有引擎测试使用新建8.4.8、独立backend/.tmp数据目录、非3306回环端口、binlog关闭；创建随机schema及DML运行账号，写入前核验真实datadir。用户已有开发/测试库和IDE口令未读取或操作，不覆盖现有服务/全局设置。

测试以真实JpaTransactionManager/JDBC/JPA及数据库审计表运行。并发使用两个线程/连接和CountDownLatch，实际performance_schema.data_lock_waits确认第二写者在等guard后才释放第一写者；覆盖第一写者提交与回滚，第二资格读取看到相应已提交结果。不是依赖sleep猜测锁已取得，也不是吞吐/压力测试。死锁1213本轮是分类单元测试，不伪称实际诱发了死锁；锁超时1205是真实两连接实测。

AT-03/33仅公共基础PARTIALLY_VERIFIED：没有真实授权执行与关权竞争、批量业务清单或权限并集响应。AT-02/01/37保留原局部范围。BE-04已按下方D-047记录本人确认，BE-05未实现，整个基座仍未验收；不新增业务HTTP路由，不修改API/DDL/迁移，不创建远程Issue/PR、合并或推送。

## 最终构建与隔离实例收尾

2026-10-18最终执行`./mvnw.cmd --batch-mode --no-transfer-progress -Pmysql-check clean verify`，BUILD SUCCESS：Surefire 51项离线测试、Failsafe 25项真实MySQL检查，均0失败/0错误/0跳过，约2分钟。其中BE-04事务检查16项，另9项为此前迁移/JPA/启动检查回归；离线新增6项异常分类与1项HTTP安全503。未在最终GREEN后修改Java代码。

16项事务检查覆盖：RC锁后资格、审计/subjects/业务/revision原子提交、受检异常整批回滚、JPA提交flush失败回滚JDBC、被捕获的审计失败仍回滚、context跨线程/结束/挂起后拒用、拒绝嵌套边界、第二写者在首写者提交或回滚后重读、真实5秒锁等待超时、新RR快照、读写边界隔离、非法快照回滚、只读拒写、运行中缺guard拒绝且不重建，以及完全回滚后另起事务复核并记录失败。资格与业务均为测试夹具，非正式授权实现。

原始输出保存在被忽略的`backend/logs/build/be04-red-boundaries.log`、`be04-red-errors-http.log`、`be04-green-initial.log`及`be04-verify.log`；XML报告在`backend/target/surefire-reports/`和`failsafe-reports/`。当前jar SHA256为`8c35ffc5a6d2372a96698807f02a2ef6b64b0b784eb1c6f57b55bc7676da8b4a`，不承诺跨机器字节相同；本轮没有新增独立jar HTTP部署验收。

收尾再次核验服务端datadir与本轮临时目录一致、随机RBAC schema数为0，再通过mysqladmin正常关闭仅本轮创建的进程并确认退出。已有MySQL服务、开发/测试库及全局binlog/trust设置未操作。BE-04不改变DDL或V1～V3，用户已迁移测试库无需重建或再次迁移；重载Maven、用原rbac_app配置重启BackendApplication即可作本机装配确认，健康UP仍不等于完整事务/业务验收。

静态收尾检查PASS：生成器确定性、35项任务/41项验收状态、288处本地链接、46个唯一决策及上游来源摘要一致。改动文档UTF-8、代码围栏、无成员本机绝对路径与Git空白检查通过；正式jar含公共事务/审计类，不含测试映射、控制器或MySqlCheck夹具。该静态检查不执行SQL、HTTP或业务测试，不能替代上述实际构建与引擎结果。

官方依据：[Spring程序化事务](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)、[事务传播与rollback-only](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)、[MySQL一致性非锁定读取](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html)。这些是机制依据，不代替本项目执行证据。

## 本机运行及本人确认（D-047，2026-10-18）

用户在交接步骤后回复“BE-04 确认服务为up状态，接下来做什么”，截图显示/actuator/health返回status=UP及liveness/readiness分组。按该单项交接上下文记录BE-04/58b556a为OWNER_ACCEPTED；仅确认本机运行与本任务公共机制交付，不把健康UP本身当作事务测试或完整权限业务验收。原51离线＋25项隔离MySQL结果沿用，本轮未重跑Java测试、连接数据库或执行SQL。截图不显示数据库名/账号，不由本次健康响应推断新账号或开发库已迁移。

下一项为BE-05的HTTP幂等预留、租约栅栏和安全结果重放，仍PLANNED，尚未开始编码。BE-01其他协作条件、整个基座验收及共同主线交付保持开放；未合并或推送。
