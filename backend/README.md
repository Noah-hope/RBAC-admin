# 后端工程

Java 21、Spring Boot 4.0.8、Maven Wrapper 3.9.11（Wrapper脚本3.3.4）。基础包`com.adgn.backend`；配置使用YAML。BE-02公共骨架已本人确认，BE-03初始迁移已在独立MySQL8.4.8验证；用户测试库迁移及重复运行已成功，基础JPA和启动guard检查已实测（D-045），详见[持久化基础](../docs/05-detailed-design/BE-03-PERSISTENCE.md)。本机运行/健康和账号库内授权已核对，BE-03已按D-046记录阶段本人确认。BE-04公共事务/审计已实现并自测，用户确认服务UP，已按D-047记录单项本人确认，见[实施与接入记录](../docs/05-detailed-design/BE-04-TRANSACTIONS.md)。正式身份、授权和审批仍按任务实现。状态与任务统一见[共享记忆](../docs/project/MEMORY.md)和[任务清单](../docs/05-detailed-design/TASKS.md)，本页只说明使用方式。

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

默认verify运行64项离线测试。真实MySQL检查须显式使用`./mvnw.cmd -Pmysql-check clean verify`，并先准备独立临时8.4实例、设置RBAC_ISOLATED_MYSQL_PORT（非3306）和RBAC_ISOLATED_MYSQL_DATADIR（必须在backend/.tmp中）；共追加42项引擎检查，JVM为Asia/Shanghai。缺参数会失败，不静默跳过、不连接开发/共享测试库。实际结果及隔离步骤见[持久化验证](../docs/05-detailed-design/BE-03-PERSISTENCE.md)。

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

密码、JWT及密钥只通过成员本机环境变量或仓库外配置注入；团队共享密码私下分发，不进入Git、文档、Issue、PR、导出或日志。本轮没有读取IDE个人配置中的密码，没有连接或修改用户已有数据库；自动化验证只操作自建临时实例。

## 数据库运行与迁移

2026-10-18用户本机开发库已确认V1～V3成功、guard(1,0)，普通后端日志明确连接rbac_admin_dev且健康UP；后续本机开发可继续使用该目标。以下未迁移空库的拒绝行为用于新成员环境，不表示当前开发库仍为空。迁移和运行账号保持分离，现有库无需再次初始化。

普通`BackendApplication`的IDE运行配置使用以下变量：

| 变量 | 本机取值 |
| --- | --- |
| SPRING_DATASOURCE_URL | `jdbc:mysql://localhost:3306/rbac_admin_dev` |
| SPRING_DATASOURCE_USERNAME | `rbac_app` |
| SPRING_DATASOURCE_PASSWORD | 本机注入实际密码 |

普通运行关闭Flyway和Spring SQL初始化，Hibernate只`validate`，不建表/改表；关闭open-in-view，JDBC会话和Hibernate按UTC，UUID偏好CHAR。当前尚无领域实体，`validate`不会替代43表结构检查；现已补齐启动前只读guard检查（缺表/缺记录均失败，不自动补建）及UUID/版本/UTC/生成列样例；普通运行接受已有非零revision，不重置。运行连接默认RC；BE-04已提供独立RC写边界与RR只读快照，连接会话设置UTC及5秒InnoDB锁等待，不修改全局配置。

迁移使用单独进程。IDE新增“应用程序”运行配置，例如`MigrateTestDatabase`，主类`com.adgn.backend.migration.MigrationApplication`、JDK21、工作目录backend，不存储为项目文件。该入口自动激活migrate，拒绝混合其他profile和HTTP模式，不扫描业务组件。使用以下独立变量；先针对已确认可用于测试的空库，不向运行配置提供迁移口令：

| 变量 | 隔离测试目标示例 |
| --- | --- |
| RBAC_MIGRATION_URL | `jdbc:mysql://localhost:3306/rbac_admin_test` |
| RBAC_MIGRATION_USER | 实际具有DDL/触发器权限的迁移账号；不能从Navicat连接名称推断 |
| RBAC_MIGRATION_PASSWORD | 对应账号的本机密码 |

迁移身份由专用变量强制提供，不回退到运行账号。JDBC URL确定目标schema；配置禁止自动建库、clean、自动baseline或乱序迁移，普通进程不需要迁移口令。

`MigrationApplication`是代码入口；`MigrateTestDatabase`是IDE运行配置的自定义名称，里面保存该主类、JDK和变量，并非另一个测试类。目标库由RBAC_MIGRATION_URL决定，不由配置名称决定。连接初始化的两条SET均须带分号，Boot拼接init-sqls时只插入换行。

打包后的等价入口如下；实际执行将连接目标库并改变结构，需先核对目标、备份和独立迁移账号。缺配置的jar入口拒绝启动已实测；完整迁移在独立实例中使用同一迁移代码验证，用户已有数据库没有由agent操作：

```powershell
java "-Dloader.main=com.adgn.backend.migration.MigrationApplication" -cp target/backend-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

成功时按V1表/约束→V2触发器→V3写入锚点执行并关闭进程；Flyway另建schema history表。V1/V2明确不使用事务包装，MySQL DDL可能部分提交；失败后保留现场诊断，禁止直接clean/repair或重复手工运行脚本。先完成测试库真实约束/JPA验证，再初始化开发库。详细证据和后续清单见[BE-03准备记录](../docs/05-detailed-design/BE-03-PREPARATION.md)。

用户测试库已完成V1～V3，历史三项success=1、guard(1,0)及重复迁移无待执行项均有实际回报；无需再次清库。此前失败恢复记录见[MySQL验证与恢复](../docs/05-detailed-design/BE-03-MYSQL-VALIDATION.md)，仅供故障时参考。

本轮本人运行确认：IDE重载Maven，在Spring Boot的BackendApplication中临时将SPRING_DATASOURCE_URL指向已迁移的rbac_admin_test，账号仍rbac_app、密码本机注入，重启并访问/actuator/health。暂未迁移的开发空库会被新检查拒绝；不要通过改成root或关闭检查绕过。开发库初始化另走独立迁移流程。

新增公共映射位于core/persistence：只有专用row_version的实体才继承VersionedUuidEntity；时间字段按需显式@Convert(UtcInstantConverter.class)，业务版本独立维护。生成列须insertable=false/updatable=false。测试映射位于src/test的com.adgn.persistencefixture，不进入正式jar。完整接入、真实测试和迁移账号边界见[BE-03持久化记录](../docs/05-detailed-design/BE-03-PERSISTENCE.md)。

## 公共能力怎样接入

- HTTP装配放`web`，与框架无关的公共配置放`core`；其他功能包按[模块说明](../docs/05-detailed-design/MODULES.md)逐任务添加，不建立空业务接口或第二套common包。
- 请求ID接收`X-Request-Id`，只允许`[A-Za-z0-9_-]{1,64}`且只能出现一次；省略时生成UUID，非法时以安全新ID返回422。ID进入响应头和日志MDC，不能用于认证或幂等。全部响应no-store。
- 请求JSON拒绝未知字段、重复键、尾随JSON及隐式标量转换；语法错误400，字段/类型或必填参数不符422。DTO仍需显式标注各自的校验约束，跨字段/版本/授权规则由所属模块实现。显示文本修剪与Unicode code point长度按具体DTO处理，密码保持原值，不做全局字符串裁剪。
- 错误为API1.2.0的`application/problem+json`：type/title/status/code/detail/requestId/errors；字段路径采用JSON Pointer，单次最多100项。未知路由404，未知服务异常按现有500契约返回DATA_INTEGRITY_ERROR；不披露原始异常、请求值、SQL或堆栈。领域专用冲突码/认证异常由对应业务任务显式接入，不能只按HTTP状态推断所有业务原因。
- 请求日志只记录requestId、运行实例、方法、状态和耗时；不记录URL查询、原始body或异常消息。异常仅记录类别。MDC在请求结束恢复，避免线程复用串号。

## 公共事务与审计接入

通过构造注入CommandBoundary、ReadBoundary及AuditAppender。写命令使用`execute(qualify, work)`：在外层新RC事务锁guard后，qualify读取当前身份/范围并返回Identity，work复用同一WriteContext；内层写端口先requireActive，不再开启独立事务。Identity是内部资格投影，完整JWT/任命/权限判断由业务模块提供，不接受客户端自行声明。

只读使用`read(qualify, project)`在新RR快照内完成资格和DTO查询；不要向序列化层返回懒加载实体。审计使用`append(context, record)`与业务共提交；快照是已脱敏、schemaVersion=1的JSON对象，调用方提供事件和subjects。JPA新行被后续JDBC或外键引用前，须在原事务先flush；审计追加器不替代领域插入顺序。边界拒绝嵌套、跨线程或已结束context，不自动重试，不在持锁期间等待网络。

BE-04没有DDL变化，已完成V1～V3的测试库无需清库或重新迁移。IDE重载Maven后，继续用rbac_app及本机密码重启BackendApplication，访问健康接口确认装配；未迁移开发空库仍会被启动保护拒绝。具体失败语义、同事务回滚、测试证据与后续责任见[BE-04记录](../docs/05-detailed-design/BE-04-TRANSACTIONS.md)。

## BE-05幂等功能启用

D-049：用户已确认启用true并启动/健康UP，BE-05/d059d11单项本人确认已记录；以下配置方式继续适用，应用默认关闭策略不变，不表示完整业务或全基座验收。

幂等默认关闭，不要求现在修改已有BackendApplication配置；关闭时不注册IdempotencyCoordinator，也不提供忽略幂等的执行入口。后续业务接入需要显式启用并使用统一协调器。BE-05只提供公共机制，当前没有新增业务接口；设计、TDD与结果见[幂等实施记录](../docs/05-detailed-design/BE-05-IDEMPOTENCY.md)。

在IDE的Spring Boot → BackendApplication → 环境变量中添加：

| 变量 | 配置 |
| --- | --- |
| RBAC_IDEMPOTENCY_ENABLED | `true` |
| RBAC_IDEMPOTENCY_CURRENT_KEY_ID | 自定密钥版本名，例如`local-v1` |
| RBAC_IDEMPOTENCY_KEYS_JSON | JSON对象：版本名映射至随机密钥的Base64文本；当前版本必须存在，每把密钥至少32字节 |

密钥由团队在私下渠道保管，不复用数据库密码/JWT密钥，不截图、粘贴到聊天或提交Git。需要生成时，可在自己的PowerShell会话运行下列代码（不会打印密钥）；此会话启动的应用才继承这些变量，已打开IDE的运行配置仍需单独填入相同值。重启时保留原密钥，不每次重新生成，否则已有记录无法比较。

```powershell
$taskIdempotencyBytes = New-Object byte[] 32
$taskIdempotencyRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$taskIdempotencyRandom.GetBytes($taskIdempotencyBytes)
$taskIdempotencyRandom.Dispose()
$env:RBAC_IDEMPOTENCY_ENABLED = 'true'
$env:RBAC_IDEMPOTENCY_CURRENT_KEY_ID = 'local-v1'
$env:RBAC_IDEMPOTENCY_KEYS_JSON = (@{ 'local-v1' = [Convert]::ToBase64String($taskIdempotencyBytes) } | ConvertTo-Json -Compress)
```

启用后缺失、格式错误、当前版本不存在或密钥过短会以通用配置错误拒绝启动；不会在异常中回显配置值。轮换时新增版本并切换current-key-id，保留仍有记录引用的旧版本；本轮没有自动删除任务，不能仅按七天时间就删除旧密钥。缺历史版本返回503安全失败，不会重新执行旧请求。独立迁移入口不要求幂等密钥。

Java接入通过reserve/complete或组合execute，身份和授权回调必须在锁内读取当前状态；显式领域端口提供规范请求字节与安全结果，不能捕获任意Servlet响应作为缓存。租约60秒、完成后至少七天保留；业务失败回滚后预留仍存在，不能换内容重用同一个key。正式业务须按契约声明的Idempotency-Key入口接入，登录/改密/退出、只读POST及OA回调不使用本机制。

## 测试与验收范围

`clean verify`运行当前64项离线测试并打包，失败0、错误0、跳过0。包括原51项回归及BE-05新增13项（摘要/响应4、配置5、HTTP4）；原HTTP请求链27项继续回归。测试用控制器仅位于`src/test`，不会加入正式jar。Surefire负责`*Test`；Failsafe生命周期已配置；默认verify仅运行离线测试，真实MySqlCheck须显式选择mysql-check，结果按下段单列。

迁移入口测试通过测试专用策略阻止`Flyway.migrate()`，只验证装配、独立身份、缺配置/HTTP拒绝及业务隔离；脚本测试调用Flyway11.14.1实际MySQL解析器，仅确认语句边界，不证明MySQL语法、约束执行或业务正确。解析器为内部API，只用于测试，版本变更时需复核。

真实MySQL检查另用显式profile：`./mvnw.cmd -Pmysql-check clean verify`。必须先准备全新临时MySQL8.4实例（数据目录位于backend/.tmp、回环非3306端口、binlog关闭、仅临时root空口令），注入RBAC_ISOLATED_MYSQL_PORT与RBAC_ISOLATED_MYSQL_DATADIR；测试在任何写操作前核验服务端datadir，随后创建并清理随机schema/账号。缺参数或指向错误实例会失败，不跳过，不连接开发库。最新实测为64项离线＋42项真实引擎检查通过：BE-05新增17项引擎检查，原BE-04的25项（16公共事务、9迁移/JPA/启动）保持回归。临时schema/账号清理及本轮独立进程关闭已确认；服务生命周期、RED/GREEN及限制见[BE-04记录](../docs/05-detailed-design/BE-04-TRANSACTIONS.md)。它不替代正式领域授权、全部业务约束或云端部署验收。

测试覆盖安全/自动请求ID、头重复、严格JSON、嵌套字段指针、400/404/405/409/415/422/500/503、缺配置失败、日志脱敏和健康暴露边界。Mockito采用显式测试Java agent，测试JVM关闭CDS，避免动态自附加警告；不是跳过测试。

完整RED/GREEN、打包HTTP验证、依赖/插件实际版本与限制见[BE-02实施记录](../docs/05-detailed-design/BE-02-IMPLEMENTATION.md)。该记录不等于完整AT-01/AT-37或公共基座已经验收。
