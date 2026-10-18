# BE-03依赖、配置与迁移脚本准备

2026-10-18/D-045最新实施：基础UUID/JPA版本/UTC/生成列与缺guard启动保护已实现，44离线＋9项真实隔离MySQL检查通过，用户测试库迁移及重复运行也已核对；本人运行确认、账号权限及完整领域/基座验收仍待后续。详见[持久化基础](BE-03-PERSISTENCE.md)。下文D-043/044失败现场、JPA未验证或未执行SQL等为当时历史，不覆盖本次进展。

最新D-044：已修复两处ROW_NUMBER保留字引用，独立MySQL8.4.8真实迁移及基础约束检查通过，AT-02推进为局部验证；用户现有测试库V1失败现场保留，JPA和其他BE-03待办继续开放。见[真实验证与恢复步骤](BE-03-MYSQL-VALIDATION.md)。下文为首次准备及初始化修复历史，不再表示所有环境均未执行SQL。

## 连接初始化SQL修复（2026-10-18）

用户提供IDE控制台日志：migrate profile已激活，MySQL返回1064，失败发生在JdbcConnectionFactory.openConnection的initSql阶段，报错位置为第二条SET time_zone。该证据说明用户本机已建立数据库连接并尝试执行初始化SQL；没有V1～V3迁移成功证据，不代表真实约束/JPA通过。agent没有连接或操作用户数据库，原始日志含本机路径，不纳入Git。

根因是application-migrate.yaml两条init-sqls都缺终止分号。[Boot4.0.8源码](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.0.8/module/spring-boot-flyway/src/main/java/org/springframework/boot/flyway/autoconfigure/FlywayAutoConfiguration.java)按换行拼接列表，导致Flyway把两个SET作为单一语句提交。本次只为两项补齐分号，没有启用allowMultiQueries或修改V1～V3正文/校验和。

按TDD新增connectionInitializationSeparatesCharsetAndTimeZoneStatements，取得真实Boot装配后Flyway的initSql并交由实际MySQL解析器解析：RED为1测试1失败/0错误（两条合成一条）；修复后完整clean verify为41测试通过，0失败/错误/跳过，打包成功。此回归离线运行，使用原测试专用策略阻止migrate，不替代MySQL重跑。原始日志为backend/logs/build/be03-red-init-sql.log及be03-verify-init-sql-fix.log，均忽略。

本次jar SHA256为8afbf71fcc416d33417138dc590f3be422abe71929e1749c4dacfe87cf170bba；下文40项及旧摘要是首次准备的历史记录。用户可保留原MigrateTestDatabase配置，在IDE重新构建后运行同一配置继续验证，若再次失败保留错误诊断；不能由本次离线修复推断所有表/触发器已创建。MigrationApplication是代码入口，MigrateTestDatabase只是IDE配置名称，目标schema始终由URL决定。

2026-10-18，D-043；需求V1.3、API1.2.0、数据库设计1.1。用户授权“添加、补齐配置、整理迁移脚本”。本轮实现这三项，不连接已有数据库、不执行迁移，不把BE-03或AT-02标为通过。

## 实际交付

- [pom.xml](../../backend/pom.xml)：加入JPA、MySQL驱动、Boot Flyway starter及flyway-mysql，版本继续交给Boot4.0.8父BOM；显式指定普通jar主类，避免两个main入口导致打包歧义。
- [普通配置](../../backend/src/main/resources/application.yaml)：运行账号由三个SPRING_DATASOURCE变量注入；Flyway关闭、SQL初始化关闭、Hibernate validate、关闭open-in-view、UTC、CHAR UUID偏好及RC连接默认值。不保存密码，不提前创建业务实体。
- [迁移配置](../../backend/src/main/resources/application-migrate.yaml)及[迁移入口](../../backend/src/main/java/com/adgn/backend/migration/MigrationApplication.java)：独立非Web一次性进程，无组件扫描/JPA/运行DataSource，专用RBAC_MIGRATION_URL/USER/PASSWORD强制提供，不回退运行账号；不允许混合profile或开启HTTP。
- [离线测试配置](../../backend/src/main/resources/application-test.yaml)：排除DataSource/JPA/Flyway；现有HTTP/实例配置测试明确使用它。此profile仅用于离线测试，不能作为数据库验收模式或正常部署配置。
- [V1](../../backend/src/main/resources/db/migration/V1__create_tables_and_constraints.sql)：保留原43表、字段/检查/索引及79条FK，先建表后加FK；保留原连接SET。仅改文件头说明，不改设计SQL正文。
- [V2](../../backend/src/main/resources/db/migration/V2__create_immutability_triggers.sql)：保留41触发器。使用Flyway MySQL原生DELIMITER支持，逐个完整解析触发器；没有自行按分号切分或重写触发器规则。
- [V3](../../backend/src/main/resources/db/migration/V3__initialize_mutation_guard.sql)：仅INSERT mutation_guard(1,0)，不INSERT IGNORE，不初始化账号/人员/授权或默认密码。Flyway历史控制一次执行；未来运行时不得重建缺失guard。
- V1/V2配套.sql.conf指定executeInTransaction=false，避免把MySQL DDL包装误认为整批可回滚；并不让失败自动恢复。
- [来源清单](BE-03-MIGRATIONS.json)：原草案及迁移文件SHA256，便于审查整理范围；草案、需求和API本轮均未修改。

## 版本与官方依据

实际dependency:tree确认：Spring Data JPA4.0.7、Hibernate7.2.24.Final、Connector/J9.7.0、Flyway core/mysql11.14.1；starter为Boot4.0.8，不逐项覆盖版本。JDK为Corretto21.0.12.1，Wrapper3.9.11。

[Boot初始化说明](https://docs.spring.io/spring-boot/how-to/data-initialization.html)说明独立迁移工具、validate及MySQL特定模块；[Flyway MySQL说明](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)说明DELIMITER支持。本轮以本地解析到的4.0.8依赖为运行依据，不将官网当前版本标成项目升级。

## TDD与实际检查

| 检查 | 实际结果 | 适用范围 |
| --- | --- | --- |
| 第一轮RED | DatabaseProfileTest 4测试，4失败/0错误，缺数据库profile行为 | 配置行为未实现 |
| 第一轮GREEN | 同4测试全部通过 | 运行/迁移参数有效配置 |
| 第二轮RED | MigrationEntryTest＋MigrationScriptTest 6测试，6失败/0错误，缺独立入口和迁移资源 | 入口/资源未实现 |
| 完整clean verify | 40测试通过，0失败/错误/跳过，打包成功 | 原30项＋新增10项，全部离线 |
| Flyway11.14.1 MySQL解析 | V1共124语句（2SET＋43CREATE＋79ALTER）；V2为41完整触发器；V3为1语句 | 仅离线语句边界，不连接MySQL |
| 独立入口上下文 | 独立Flyway DataSource使用迁移凭据，无运行DataSource/BackendApplication/requestIdFilter；缺专用配置或要求HTTP均失败 | 测试专用策略不调用migrate，不执行SQL |
| 打包入口缺配置 | PropertiesLauncher启动MigrationApplication，缺URL以退出码1拒绝，未连接数据库 | 实际jar入口及失败边界 |
| jar内容 | V1/V2/V3、V1/V2配置及独立入口已打包 | 无真实迁移结果 |
| Failsafe | No tests to run，无IT类 | 不算MySQL集成测试通过 |

实现中先纠正了Boot4的DataJpaRepositoriesAutoConfiguration类名，编译错误不记为有效RED；另外Boot向Flyway提供的是DataSource，早期测试读取configuration.getUser()得null，改为检查实际DataSource的用户名/URL。没有为满足断言改写生产账号逻辑。缺配置及故意异常测试会输出预期ERROR/WARN，未静默忽略失败或跳过测试。

原始检查输出在被忽略的backend/logs/build/be03-*.log，JUnit报告在target/surefire-reports，不把本机路径和原始日志提交Git。完整构建后jar SHA256为9bffebe043cb421eb4d6a3e178c4e7a58cab8487c5826b102daac2978d03c0d5；它只标识本次构建，不承诺跨机器相同二进制。

设计/追踪静态检查通过：231处本地链接、43个唯一决策、生成器确定性、三份迁移/来源摘要及任务状态一致，上游摘要不变；Markdown围栏、本机绝对路径及Git空白检查通过。该检查不运行Java/SQL，Java实际运行结果单列于上表。

## 后续操作与未完成事项

用户先重新加载Maven。普通运行配置保留SPRING_DATASOURCE三项；迁移另建本机Application配置，主类MigrationApplication，使用独立三项RBAC_MIGRATION变量，先指向rbac_admin_test。用户名必须按实际授权核实，Navicat连接名不能代替账号身份；密码只在本机注入。步骤见[后端运行说明](../../backend/README.md)。

执行前核对实例/测试库、独立账号权限、MySQL8.4实际补丁、严格SQL mode及备份/恢复条件。完成受控测试库迁移后核对43业务表＋Flyway历史表、79FK、CHECK/索引/生成列、41触发器、guard及三条成功迁移记录；测试只对隔离目标操作，不能通过关闭FK/clean或裁剪表规避失败。MySQL DDL失败可能部分提交，停止后诊断，不直接repair或盲目重跑。

真实UUID CHAR/UTC读写、JPA @Version与API业务版本区别、生成列映射和即时约束/触发器反例仍未运行；普通应用缺guard时的启动失败检查仍需补齐。当前无领域实体，Hibernate validate不能证明全部表已正确初始化。本轮不以H2、mock或配置绑定取代MySQL验收。

BE-01/BE-03保持IN_PROGRESS；AT-02仍DESIGNED_NOT_RUN，待真实MySQL及JPA证据。BE-02 OWNER_ACCEPTED及AT-01/37原局部证据保持，BE-04～35仍PLANNED。基座总门槛未评估，业务未分派，未创建远程事项、合并或推送。
