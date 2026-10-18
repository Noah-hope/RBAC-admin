# 后端工程

Java 21、Spring Boot 4.0.8、Maven Wrapper 3.9.11（Wrapper脚本3.3.4）。基础包`com.adgn.backend`；配置使用YAML。BE-02公共骨架已本人确认，BE-03已加入数据库依赖、配置和迁移脚本，真实迁移/JPA映射尚未验证。正式身份、授权和审批仍按任务实现。状态与任务统一见[共享记忆](../docs/project/MEMORY.md)和[任务清单](../docs/05-detailed-design/TASKS.md)，本页只说明使用方式。

## 在IntelliJ IDEA运行

打开本目录或`pom.xml`，沿用根Git仓库。Project SDK、Maven导入/运行JDK和应用运行JRE均选JDK21；Maven主路径选择Wrapper。重新加载Maven后运行`BackendApplication`。

默认dev配置监听`127.0.0.1:8080`。BE-03加入数据库依赖后，运行前需提供下述运行账号配置，先完成隔离库验证及目标库迁移。访问`http://localhost:8080/actuator/health`检查健康；`groups`是正常的探针分组。健康UP不能代替表/约束、授权或完整业务验收。

## 命令行构建与启动

先使当前会话可用JDK21；无需统一个人安装位置，也无需永久修改系统变量。Windows PowerShell中，必要时临时设置`JAVA_HOME`为自己的JDK目录，然后从backend执行：

```powershell
.\mvnw.cmd --version
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Linux/macOS使用`./mvnw`。打包后可执行`java -jar target/backend-0.0.1-SNAPSHOT.jar`。Wrapper首次需要下载Maven及依赖；发行包SHA256已固定。Spring Boot父BOM管理依赖和插件版本，不单独升级其中一个组件。

## 本机日志存放

应用当前输出到IDE/终端控制台，没有配置自动写入日志文件。手工保存的构建、测试或依赖检查输出统一放在`logs/build/`，不散放工程根目录；BE-02的三份历史构建日志已移入此目录，内容未改动。整个`logs/`目录由Git忽略，不提交原始本机日志。`target/`仍用于Maven构建产物和测试报告。

需要保存完整`clean verify`输出时，在backend目录执行：

```powershell
New-Item -ItemType Directory -Path logs/build -Force | Out-Null
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify *> logs/build/verify.log
```

日志位于`target/`之外，避免`clean`删除或占用当前输出文件。该命令会覆盖同名日志，需保留多轮证据时使用不同文件名；本轮整理没有重跑构建。

## 配置边界

| profile | 配置及用途 |
| --- | --- |
| dev（默认） | 本机调试，运行实例标识local，默认8080端口 |
| test | 当前离线配置/HTTP测试，实例标识test，随机端口；显式排除DataSource/JPA/Flyway自动配置，不连接数据库 |
| server | 必须外部提供RBAC_INSTANCE_ID（1～64位字母、数字、下划线或连字符）；缺失/非法则启动失败 |
| migrate | 仅由独立MigrationApplication入口启用，不启动HTTP或扫描业务组件；读取独立迁移凭据 |

运行实例标识只用于运维日志，不能作为员工、后台账号或授权依据。server监听仍为本机，正式部署网络和认证由后续任务落实。

例如先在本机终端设置`RBAC_INSTANCE_ID=server-01`对应环境变量，再用`--spring.profiles.active=server`启动；IDEA中使用运行配置的“环境变量”。Spring Boot不会因为存在`.env`文件就自动读取它。

密码、JWT及密钥只通过成员本机环境变量或仓库外配置注入；团队共享密码私下分发，不进入Git、文档、Issue、PR、导出或日志。本轮没有读取IDE个人配置中的密码，没有连接数据库或执行DDL。

## 数据库运行与迁移

普通`BackendApplication`的IDE运行配置使用以下变量：

| 变量 | 本机取值 |
| --- | --- |
| SPRING_DATASOURCE_URL | `jdbc:mysql://localhost:3306/rbac_admin_dev` |
| SPRING_DATASOURCE_USERNAME | `rbac_app` |
| SPRING_DATASOURCE_PASSWORD | 本机注入实际密码 |

普通运行关闭Flyway和Spring SQL初始化，Hibernate只`validate`，不建表/改表；关闭open-in-view，JDBC会话和Hibernate按UTC，UUID偏好CHAR。当前尚无领域实体，`validate`不会替代43表结构检查；缺失guard的运行时失败检查、UUID/版本样例和真实引擎验证仍在BE-03后续范围。RC为连接默认隔离，未来RR读取和写事务边界由BE-04实现。

迁移使用单独进程。IDE新增“应用程序”运行配置，例如`MigrateTestDatabase`，主类`com.adgn.backend.migration.MigrationApplication`、JDK21、工作目录backend，不存储为项目文件。该入口自动激活migrate，拒绝混合其他profile和HTTP模式，不扫描业务组件。使用以下独立变量；先针对已确认可用于测试的空库，不向运行配置提供迁移口令：

| 变量 | 隔离测试目标示例 |
| --- | --- |
| RBAC_MIGRATION_URL | `jdbc:mysql://localhost:3306/rbac_admin_test` |
| RBAC_MIGRATION_USER | 实际具有DDL/触发器权限的迁移账号；不能从Navicat连接名称推断 |
| RBAC_MIGRATION_PASSWORD | 对应账号的本机密码 |

迁移身份由专用变量强制提供，不回退到运行账号。JDBC URL确定目标schema；配置禁止自动建库、clean、自动baseline或乱序迁移，普通进程不需要迁移口令。

`MigrationApplication`是代码入口；`MigrateTestDatabase`是IDE运行配置的自定义名称，里面保存该主类、JDK和变量，并非另一个测试类。目标库由RBAC_MIGRATION_URL决定，不由配置名称决定。连接初始化的两条SET均须带分号，Boot拼接init-sqls时只插入换行。

打包后的等价入口如下；实际执行将连接目标库并改变结构，需先核对目标、备份和独立迁移账号。本轮只实测了缺配置时该入口以非零码拒绝启动，未执行迁移：

```powershell
java "-Dloader.main=com.adgn.backend.migration.MigrationApplication" -cp target/backend-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

成功时按V1表/约束→V2触发器→V3写入锚点执行并关闭进程；Flyway另建schema history表。V1/V2明确不使用事务包装，MySQL DDL可能部分提交；失败后保留现场诊断，禁止直接clean/repair或重复手工运行脚本。先完成测试库真实约束/JPA验证，再初始化开发库。详细证据和后续清单见[BE-03准备记录](../docs/05-detailed-design/BE-03-PREPARATION.md)。

## 公共能力怎样接入

- HTTP装配放`web`，与框架无关的公共配置放`core`；其他功能包按[模块说明](../docs/05-detailed-design/MODULES.md)逐任务添加，不建立空业务接口或第二套common包。
- 请求ID接收`X-Request-Id`，只允许`[A-Za-z0-9_-]{1,64}`且只能出现一次；省略时生成UUID，非法时以安全新ID返回422。ID进入响应头和日志MDC，不能用于认证或幂等。全部响应no-store。
- 请求JSON拒绝未知字段、重复键、尾随JSON及隐式标量转换；语法错误400，字段/类型或必填参数不符422。DTO仍需显式标注各自的校验约束，跨字段/版本/授权规则由所属模块实现。显示文本修剪与Unicode code point长度按具体DTO处理，密码保持原值，不做全局字符串裁剪。
- 错误为API1.2.0的`application/problem+json`：type/title/status/code/detail/requestId/errors；字段路径采用JSON Pointer，单次最多100项。未知路由404，未知服务异常按现有500契约返回DATA_INTEGRITY_ERROR；不披露原始异常、请求值、SQL或堆栈。领域专用冲突码/认证异常由对应业务任务显式接入，不能只按HTTP状态推断所有业务原因。
- 请求日志只记录requestId、运行实例、方法、状态和耗时；不记录URL查询、原始body或异常消息。异常仅记录类别。MDC在请求结束恢复，避免线程复用串号。

## 测试与验收范围

`clean verify`运行所有当前测试并打包：上下文1项、实例配置3项、Spring MVC/Servlet请求链26项，新增数据库profile4项、迁移入口/初始化4项、Flyway/MySQL离线解析3项，共41项；失败0、错误0、跳过0。测试用控制器仅位于`src/test`，不会加入正式jar。Surefire负责`*Test`；Failsafe的`*IT`生命周期已配置，当前没有IT类，明确报告No tests to run，因此不能宣称MySQL集成测试已通过。

迁移入口测试通过测试专用策略阻止`Flyway.migrate()`，只验证装配、独立身份、缺配置/HTTP拒绝及业务隔离；脚本测试调用Flyway11.14.1实际MySQL解析器，仅确认语句边界，不证明MySQL语法、约束执行或业务正确。解析器为内部API，只用于测试，版本变更时需复核。

测试覆盖安全/自动请求ID、头重复、严格JSON、嵌套字段指针、400/404/405/409/415/422/500、缺配置失败、日志脱敏和健康暴露边界。Mockito采用显式测试Java agent，测试JVM关闭CDS，避免动态自附加警告；不是跳过测试。

完整RED/GREEN、打包HTTP验证、依赖/插件实际版本与限制见[BE-02实施记录](../docs/05-detailed-design/BE-02-IMPLEMENTATION.md)。该记录不等于完整AT-01/AT-37或公共基座已经验收。
