# BE-03持久化基础与启动检查

2026-10-18，D-045。用户明确要求实现UUID/UTC/版本/生成列基础映射和缺guard启动检查。沿用需求V1.3、API1.2.0、数据库1.1，未修改契约、字段、DDL或既有迁移摘要。各领域实体/Repository和业务命令仍由所属任务实现。

## 实现与接入

- [VersionedUuidEntity](../../backend/src/main/java/com/adgn/backend/core/persistence/VersionedUuidEntity.java)：只供已有专用row_version的UUID单主键实体继承；id明确按CHAR(36)绑定且禁止更新，调用方预分配UUID。Long @Version在首次持久化前保持null，交给Spring Data识别新对象，数据库首次保存后为0。无row_version、复合主键或只有业务version的表不机械继承。
- [UtcInstantConverter](../../backend/src/main/java/com/adgn/backend/core/persistence/UtcInstantConverter.java)：DATETIME(6)时刻字段显式使用@Convert；不是autoApply全局转换。Instant先截断到微秒再转换为Timestamp，读取用toInstant；配合现有JDBC/Hibernate UTC配置，避免JVM默认时区影响。NULL保持NULL，不把任职业务日期等字段自动改为时刻。
- [启动自动配置](../../backend/src/main/java/com/adgn/backend/core/persistence/DatabaseInitializationAutoConfiguration.java)：DataSource装配后、应用完成启动前只读检查mutation_guard。表/记录缺失、记录不唯一、id非1或revision非法即RBAC_DB_NOT_INITIALIZED失败；不INSERT、不建表、不运行迁移，不把正常非零revision重置为0。离线test和独立迁移入口没有DataSource，此检查不装配；不是提供给普通运行的关闭开关。
- [映射样例](../../backend/src/test/java/com/adgn/persistencefixture/PersistenceSamples.java)：只在测试源中，包位于应用扫描范围之外，通过显式测试配置启用；使用真实迁移后的business_system/admin_appointment，不建样例表。正式jar不含样例实体或测试接口。

业务version、catalogVersion、appointmentVersion等由领域事务显式维护，不能标成同一个@Version，也不能向API暴露row_version代替既定版本。样例中技术更新只推进row_version，业务版本仍为41/73；任命变更样例显式推进appointmentVersion。无变化flush不推进版本。乐观锁异常到各API错误语义的映射留对应业务实现，本任务没有新增业务HTTP接口。

生成槽映射必须使用`@Column(insertable=false, updatable=false)`，读取新计算值时refresh或重新查询；不向生成列写NULL或伪造值。当前样例没有把生成槽作为版本或业务可写字段。

## TDD与实际验证

实现前在隔离MySQL8.4.8确认：缺guard行/表时原应用仍能启动，2项断言失败；UTC转换占位实现导致2项断言失败；基础映射导致新UUID对象走merge、陈旧更新覆盖成功、生成槽被写入、重复ID对象覆盖原记录，5项映射测试中4失败/0错误。连接沙箱拒绝和一次测试方法编译笔误只算环境/编写排障，不算有效RED。

第一轮完整构建还捕获了真实时区缺陷：将Instant转LocalDateTime再交给Hibernate UTC JDBC绑定，在Asia/Shanghai JVM中把02:03:04Z写成前一天18:03:04。保持数据库应存的UTC字面值断言不变，改为Timestamp保存时间点；修复后的完整结果见本节后续记录。不能仅用单元转换或往返相等掩盖数据库中错误的UTC值。

最终完整命令`./mvnw.cmd --batch-mode --no-transfer-progress -Pmysql-check clean verify`成功，Surefire 44项离线测试、Failsafe 9项真实引擎检查，均0失败/0错误/0跳过，耗时约1分40秒，完成打包。运行环境为JDK21.0.12.1、Boot4.0.8、JPA4.0.7/Hibernate7.2.24.Final、Connector9.7.0/Flyway11.14.1及MySQL8.4.8；没有为兼容新增依赖或改BOM。

| 实际检查 | 结果与范围 |
| --- | --- |
| 迁移/基本约束（既有1项引擎测试） | 43业务表＋历史、79FK、41触发器、3条成功历史/重复迁移、CHECK/PK/FK拒绝与runtime DDL拒绝仍通过 |
| JPA映射（5项引擎测试） | 新UUID对象save返回原受管对象；CHAR小写UUID实际落库；UTC字面值02:03:04.123456及Instant读回一致；旧版本flush被拒绝且已提交数据保留；row_version推进不自动推进API业务版本；无变化不推进版本；重复UUID的新对象不能merge覆盖；生成槽插入与更新由MySQL计算 |
| 启动保护（3项引擎测试） | 缺guard行、缺guard表分别失败且不自动创建；有效revision=7正常启动且未重置、迁移历史仍3条；使用普通DML账号 |
| 普通可执行jar附加核验（非JUnit） | 重新启动同一自建临时实例，只建随机schema及最小guard夹具，用DML账号运行实际jar、非Web模式：guard(1,9)退出0且revision不变，删除行后退出1并含RBAC_DB_NOT_INITIALIZED、仍0行。证明打包自动配置注册实际生效；该夹具不是全量schema/JPA/HTTP或部署验收 |

jar中包含3个公共持久化类和自动配置注册资源，不含PersistenceSamples/MySqlCheck/testsupport。SHA256为`4c68769026440e1639faa7eb206322dc858edfa8d12f9222e7cf2583e95c035e`，不承诺跨机器字节相同。测试结束无遗留随机RBAC schema；附加核验清理后临时进程已正常关闭。

实际证据在本机忽略目录logs/build：be03-red-startup-guard.log、be03-red-utc.log、be03-red-jpa-mapping.log、be03-red-utc-jdbc.log、be03-persistence-verify.log及be03-jar-guard-present/missing.log；JUnit XML在target/surefire-reports和target/failsafe-reports。原始日志/临时启动脚本/数据目录不入Git。预期反例会记录异常；Flyway对8.4的验证版本WARN、Hibernate UUID配置incubating/JTA提示及CLI skip-password产生的通用提示均保留，不屏蔽或当作测试失败。

同步检查：详细设计生成器确定性、265处本地链接、45个唯一决策、来源/迁移摘要及任务状态静态校验通过；共享文本UTF-8、本机绝对路径、Markdown围栏和Git空白检查通过。静态检查未启动数据库，与上述实际引擎/JPA结果分开记录。

## 隔离和运行方式

本轮使用已安装MySQL8.4.8、新建backend/.tmp下数据目录、--no-defaults和非3306回环端口，binlog关闭。Java测试在任何写入前核验真实datadir、端口、版本和binlog，再创建随机schema/账号；runtime仅DML，迁移账号仅目标schema权限。只清理测试生成对象，用户已有开发/测试库不连接、不清理；临时实例结束后正常关闭。binlog关闭不证明用户开启binlog时的迁移账号策略已经验证。

普通`./mvnw.cmd verify`只运行离线测试；显式`./mvnw.cmd -Pmysql-check clean verify`另运行真实引擎测试，JVM固定Asia/Shanghai。执行前必须先准备独立临时MySQL，并在本机提供RBAC_ISOLATED_MYSQL_PORT和RBAC_ISOLATED_MYSQL_DATADIR（解析后必须属于backend/.tmp）；参数缺失/错误会失败，不skip、不回退H2、不允许3306或已有业务库。运行样例的test-only清理工具不进入正式代码。

## 本人确认和剩余边界

用户后续提供2026-10-18 10:52启动日志及健康截图：BackendApplication、默认dev、Java21.0.12.1连接localhost:3306/rbac_admin_test/MySQL8.4.8，Hikari/JPA完成初始化、Tomcat8080启动，HTTP GET记录200，健康status=UP。确认本机普通后端连接/启动/健康检查成功；日志没有输出实际数据库用户名或GRANTS，不能据此认定确实使用rbac_app或迁移账号权限已齐全。没有把这次运行回报标记为BE-03整项OWNER_ACCEPTED或基座验收；以下IDE步骤已完成运行结果核对，当前先核验两个账号权限，再收尾BE-03。

IDE重载Maven后，普通Spring Boot配置BackendApplication可临时连接已迁移的rbac_admin_test，使用rbac_app及本机密码，确认启动与/actuator/health。不要切到root作为运行账号，也不要让新启动检查自动补空开发库；开发库初始化另用独立迁移入口开展。本轮未读取IDE口令或操作用户库。

迁移账号仍保留rbac_migrator职责。库内DDL/TRIGGER权限与binlog额外策略需同时满足；root是此前本机首次触发器初始化的临时管理身份，不是所有迁移必须使用root。当前不改变共享本机MySQL的全局trust_creators，不给rbac_app或迁移账号无依据授予全局SUPER。项目专用MySQL实例可由管理员评估配置trust_creators=1并让受限迁移账号执行；保留0的实例则由管理员受控处理触发器迁移。两类方案都会影响触发器DEFINER与后续权限维护，云端落地时核验，不由普通后端设置。实际rbac_migrator完整GRANTS仍未核验，不把技术说明记成已授权/已执行。

AT-02维持PARTIALLY_VERIFIED：这些样例只证明基础适配，不代表全部领域映射、41触发器逐项业务保护或跨域并发已通过。BE-03仍IN_PROGRESS，保留用户本人运行确认及账号配置核验；BE-04写上下文/审计、BE-05幂等尚未实现，整个基座未验收，也未合并或推送。

官方依据：[Spring Data JPA实体新旧识别](https://docs.spring.io/spring-data/jpa/reference/4.0/jpa/entity-persistence.html)、[Hibernate ORM7.2映射与JDBC时区](https://docs.hibernate.org/orm/7.2/userguide/html_single/)、[MySQL触发器权限与DEFINER](https://dev.mysql.com/doc/refman/8.4/en/create-trigger.html)。文档说明不是本项目的执行证据。
