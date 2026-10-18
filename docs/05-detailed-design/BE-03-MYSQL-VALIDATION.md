# BE-03首次真实MySQL验证与失败恢复

2026-10-18，D-044。需求V1.3/API1.2.0/数据库设计1.1业务语义不变。用户rbac_admin_test曾发生V1/V2失败，最新10:21日志已确认从空库成功完成V1～V3。agent只操作独立临时实例，没有清理、修复或迁移用户已有数据库。

## 用户本机迁移成功回报（最新状态）

用户提供的2026-10-18 10:21:22～10:21:37（Asia/Shanghai）控制台日志显示：MigrationApplication使用Java21.0.12.1、Boot4.0.8和migrate profile，连接localhost:3306/rbac_admin_test（MySQL8.4）；原本无Flyway历史表、schema为空，校验3条迁移后依次执行V1表/约束、V2触发器及V3 guard，最终Successfully applied 3 migrations、版本v3，退出码0。这次初始化成功，旧失败不再作为当前阻塞。

证据来自用户实际运行日志，本轮agent未连接或重跑其数据库；日志未展示实际数据库账号、表/触发器数量查询、guard内容、重复迁移或JPA映射结果，不将独立实例的这些验证转记为用户库已逐项核验。用户此前反馈binlog=1/trust_creators=0，但本次日志未输出这两个值或证明配置始终未变。Flyway版本WARN仍存在，未导致本次失败。原始附件含本机启动路径，不复制入共享仓库。

下一步可在用户库只读核对历史、对象数量及guard，再重跑同一迁移配置确认无待执行项；无需再次清库。之后继续BE-03的UUID/JPA版本/UTC映射与guard缺失启动检查，BE-03、AT-02和基座验收状态保持原范围。下方失败恢复步骤仅供历史排障参考，不是要求对已成功库重建。

本次仅同步日志结论和项目导航；静态设计/追踪检查PASS（249处本地链接、44个唯一决策）及Git空白检查通过。没有修改代码、重跑Java测试或执行SQL。

## 问题与修复范围

用户日志确认目标为rbac_admin_test、MySQL8.4，初始化SQL已通过；V1在import_plan_row的CHECK处1064，错误位于row_number>0。V1为非事务DDL，前面表可能已提交，失败记录和部分结构不能靠重新运行/repair变成完整成功。

[MySQL8.4保留字说明](https://dev.mysql.com/doc/refman/8.4/en/keywords.html)将ROW_NUMBER列为保留字。列定义/主键已引用，但import_plan_row和import_issue的CHECK漏引号。现在两处改成反引号引用row_number；没有删除CHECK、重命名字段或放宽约束。[schema_model.py](../04-database/schema_model.py)在渲染SQL时处理引用，逻辑schema-model.json/字段字典不变；[设计DDL](../04-database/schema.sql)和[V1](../../backend/src/main/resources/db/migration/V1__create_tables_and_constraints.sql)同步，来源清单更新。V2/V3未修改。

详细设计静态检查仍逐字节保护需求/API/逻辑模型等上游；对schema.sql只允许D-044这两处引用修正，不整体替换基线或放开任意DDL变更。当前没有已成功部署的V1交付；因此修正初始V1，不伪造V4来跳过失败V1。已有失败目标必须先恢复可迁移状态。

## 实际环境和隔离

- 本机已安装mysqld/mysql均为8.4.8，Windows x86_64；没有安装/升级用户服务。
- --no-defaults忽略现有配置，首次初始化全新backend/.tmp/be03-mysql-engine/data；检查前目标目录必须不存在。
- 临时进程只监听127.0.0.1动态端口（非3306），关闭MySQL X端口，64MiB buffer pool/16MiB redo，binlog关闭。没有连接用户3306、读取IDE口令或改变已有服务配置。
- 测试先核验@@datadir与显式临时路径完全一致、路径在backend/.tmp、端口非业务端口，再创建随机schema与迁移/运行账号；不使用已有测试库名。
- 测试在独立schema上授予迁移DDL权限，运行账号仅DML；finally只删除本测试生成的schema/账号。全量结束后再次核验临时datadir并通过mysqladmin正常关闭该进程；未停止已有MySQL服务。忽略目录保留本次本机诊断文件。

限制：该实例binlog关闭，所以不证明用户实例开启binlog时的额外触发器创建权限已满足；用户目标仍需核验@@log_bin、@@log_bin_trust_function_creators及迁移账号权限。Flyway11.14.1对8.4输出“最新验证8.1”的WARN，保留而未屏蔽；本轮实测通过不等于工具官方兼容认证或云端验证。

[MySQL触发器权限说明](https://dev.mysql.com/doc/refman/8.4/en/create-trigger.html)明确开启binlog时可能有额外权限要求。重跑前由用户执行只读查询`SELECT @@log_bin AS log_bin, @@log_bin_trust_function_creators AS trust_creators;`核对；不由应用全局修改该设置或给运行账号提升DDL权限。

用户随后实际反馈log_bin=1、trust_creators=0，已作为其本机环境事实记录；现有迁移账号完整GRANTS仍未读取。为本机首次测试恢复提供的操作建议是使用已有root管理身份执行独立迁移，保留全局配置，普通后端继续rbac_app；此建议尚未由agent执行，不作为云端默认账号方案，也不声明binlog开启环境已由本轮集成测试覆盖。

## TDD和实际结果

[MigrationMySqlCheck](../../backend/src/test/java/com/adgn/backend/MigrationMySqlCheck.java)通过显式Maven profile mysql-check运行；普通verify仍运行离线测试，不偷偷启动数据库。mysql-check缺隔离参数会失败，不静默跳过，也不改用H2。

1. RED：初始V1在独立8.4.8复现同一1064，1项引擎测试失败、0错误，失败原因匹配用户日志。
2. 修复两处标识符引用，同一独立实例重新创建随机空schema，真实执行V1～V3成功。
3. 完整命令`./mvnw.cmd --batch-mode --no-transfer-progress -Pmysql-check clean verify`：Surefire 41项离线测试＋Failsafe 1项真实引擎检查均通过，失败/错误/跳过均0，打包成功。
4. 实测44表（43业务＋Flyway历史）、79FK、41触发器、V1/V2/V3三条成功历史和guard(1,0)。第二次迁移无待执行项，历史仍3条、guard仍1行。
5. guard非法id的CHECK返回3819、重复主键返回1062、不存在公司引用的department插入返回1452；运行账号可读guard但建表返回1142。没有关闭FK或省略失败对象。

41触发器编译/安装已验证，逐个触发器的业务保护和跨域并发尚未验证。UUID/JPA @Version/UTC映射样例、缺guard启动失败等仍待BE-03后续；AT-02仅PARTIALLY_VERIFIED，BE-03仍IN_PROGRESS，基座总门槛未通过。不是全量业务或正式部署验收。

本机证据位于被忽略的logs/build/be03-red-mysql-row-number.log、be03-green-mysql-row-number.log及be03-verify-mysql.log；JUnit报告在target/surefire-reports和target/failsafe-reports。jar SHA256为60e54d3ecf7fff2e6b126bb2cbbabacbb914eb67527b8d463d33a565544cfa9f，不承诺跨机器字节相同。原始日志/临时数据不入Git。

## 用户已有测试库的恢复步骤（未由agent执行）

先在Navicat检查当前失败现场：

```sql
SHOW TABLES FROM `rbac_admin_test`;
SELECT installed_rank, version, description, success
FROM `rbac_admin_test`.`flyway_schema_history`
ORDER BY installed_rank;
```

若确认rbac_admin_test仅用于可丢弃测试且没有需要保留的数据，可在Navicat的root管理连接手工重建该测试库。下面会删除其中所有表、数据和Flyway历史；有保留需求时改用新空测试schema并配置对应账号授权，不执行此段。agent没有执行清库，也未自动生成执行任务。

```sql
DROP DATABASE `rbac_admin_test`;
CREATE DATABASE `rbac_admin_test`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_bin;
```

刷新Maven并重新构建。针对用户已反馈的binlog=1/trust_creators=0，本机这次首次迁移建议在MigrateTestDatabase中填写RBAC_MIGRATION_USER=root、RBAC_MIGRATION_PASSWORD为本机root密码、RBAC_MIGRATION_URL仍为jdbc:mysql://localhost:3306/rbac_admin_test，使用管理身份完成触发器创建。密码不入Git或聊天；BackendApplication的SPRING_DATASOURCE_USERNAME继续rbac_app，其他运行配置不改为root。后续/云端迁移身份按实际权限单独配置。

再运行该迁移配置。不要以repair清除失败后直接在部分表上跑V1。完成后核对44表/41触发器、三条成功历史、guard(1,0)；错误继续保留诊断。开发库初始化需等测试和剩余映射验证完成，不在此恢复流程中操作。
