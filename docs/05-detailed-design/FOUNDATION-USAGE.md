# Clone 后的本机配置与协作开发指南

适用于首次加入项目的成员。按下面顺序完成一次配置，即可在自己的电脑构建、初始化数据库、启动后端，并从共同基线领取任务开发。示例使用 Windows、PowerShell、IntelliJ IDEA 和本机 MySQL；所有路径均相对项目根目录 `rbac-admin`。

每位成员使用自己的本机数据库、密码和幂等密钥；不需要复制其他成员的数据库文件或个人 IDEA 配置。当前公共基座交付状态以[共享记忆](../project/MEMORY.md)为准；正式业务开工还需基座交付主线、实际任务已分派及对应依赖就绪。

## 1. 确认拿到共同代码版本

在 clone 得到的 `rbac-admin` 根目录打开终端，工作区没有待保存改动时执行：

```powershell
git fetch origin
git switch main
git pull --ff-only
git rev-parse --short HEAD
```

确认目录内有 `backend/pom.xml`、`backend/mvnw.cmd` 和 `docs/project/MEMORY.md`，并与项目负责人公布的交付版本一致。若 main 尚无公共基座，先使用负责人指定的基座分支/提交熟悉配置，正式任务从交付后的共同主线开始，不从各自不同的旧版本开发。

设置当前仓库的提交身份，替换下面两项为自己的信息：

```powershell
git config user.name "你的姓名"
git config user.email "你的提交邮箱"
```

## 2. 准备本机工具并导入 IDEA

| 工具 | 本项目需要的配置 |
| --- | --- |
| Git | 能拉取团队仓库；开始协作前核实自己的任务分支推送权限 |
| JDK | JDK 21，基座实测为 Corretto21.0.12.1；个人安装位置可以不同 |
| IntelliJ IDEA | 能导入 Java/Maven 工程；有 Spring Boot 支持时可用专用运行配置，否则用“应用程序”配置 |
| MySQL | 本机 MySQL8.4，基座实测8.4.8；服务已启动并知道自己的管理账号密码 |
| 数据库客户端 | Navicat 或 MySQL 命令行，用于建库、授权和查看迁移结果 |

Maven 使用仓库自带 Wrapper，固定3.9.11，不需要另装一套 Maven。后端入门配置不需要 Node.js、前端工程、Docker 或云服务器。

1. 在 IDEA 打开 `backend/pom.xml`，选择作为项目打开；Git 仍使用上一级项目根仓库。
2. 在“项目结构”中将 Project SDK 和模块 SDK 设置为自己的 JDK21。
3. 在“设置 → 构建、执行、部署 → 构建工具 → Maven”选择 Maven Wrapper；“正在导入”的 JDK 和“运行程序”的 JRE 都选 JDK21。
4. 打开右侧 Maven 面板，点击顶部的 **重新加载所有 Maven 项目**（循环箭头）。等待依赖下载完成、Java/Spring 类不再因缺依赖标红。

**本步成功标志：** IDEA 识别 Maven 工程，导入及运行均使用21，Wrapper发行版为3.9.11。

## 3. 先构建一次，确认代码和工具正常

在 IDEA 的 Maven 面板执行目标 `clean verify`。这组默认测试不连接你的 MySQL，也不要求先填数据库密码或 HMAC 密钥。

也可以在 `backend` 目录的 PowerShell 执行；若未配置 JAVA_HOME，先临时指定自己的 JDK 根目录：

```powershell
$taskLocalJdk = Read-Host '输入本机 JDK21 根目录（不是 bin 目录）'
$env:JAVA_HOME = (Resolve-Path -LiteralPath $taskLocalJdk).Path
.\mvnw.cmd --version
.\mvnw.cmd clean verify
```

临时变量只影响这个终端，不会自动传给已经打开的 IDEA。IDEA 使用上一步单独配置的 JDK。

**本步成功标志：** `BUILD SUCCESS`；当前基座为64项离线测试通过，后续新增测试时以实际报告为准，jar 位于 `backend/target/`。首次执行会下载依赖，需要网络。

## 4. 创建自己的本机数据库和账号

下面仅用于首次初始化你自己的本机实例。先在数据库客户端使用本机管理账号连接，核对实例：

```sql
SELECT VERSION() AS mysql_version, @@hostname AS host_name, @@port AS port;
SELECT @@partial_revokes AS partial_revokes;
SELECT @@log_bin AS log_bin,
       @@log_bin_trust_function_creators AS trust_creators;
```

示例端口为3306；本机使用其他端口时，后续 JDBC URL 一并替换。确认将要使用的库名没有需要保留的数据。下面不删除或覆盖已有库/账号；如果提示已存在，先核对已有对象及权限，不重建已有数据。

在本机管理连接执行以下初始化语句。**先将两个 `REPLACE_WITH_LOCAL_...` 占位符改成符合本机MySQL密码策略的不同密码**，不要将填好密码的 SQL 保存进仓库：

```sql
CREATE DATABASE `rbac_admin_dev`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE `rbac_admin_test`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE USER 'rbac_app'@'localhost'
  IDENTIFIED BY 'REPLACE_WITH_LOCAL_APP_PASSWORD';
CREATE USER 'rbac_migrator'@'localhost'
  IDENTIFIED BY 'REPLACE_WITH_LOCAL_MIGRATION_PASSWORD';

GRANT SELECT, INSERT, UPDATE, DELETE
  ON `rbac\_admin\_dev`.* TO 'rbac_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE
  ON `rbac\_admin\_test`.* TO 'rbac_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER, TRIGGER
  ON `rbac\_admin\_dev`.* TO 'rbac_migrator'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER, TRIGGER
  ON `rbac\_admin\_test`.* TO 'rbac_migrator'@'localhost';
```

授权语句中的 `\_` 用于转义库名下划线，适用于默认 `partial_revokes=OFF` 的实例；如果你使用已定制权限策略的实例，请由管理者核对精确库授权。账号创建与授权依据见 MySQL8.4 的[CREATE USER](https://dev.mysql.com/doc/refman/8.4/en/create-user.html)和[GRANT](https://dev.mysql.com/doc/refman/8.4/en/grant.html)说明。

分别用两个新账号建立本机连接，执行 `SELECT CURRENT_USER();`、`SHOW GRANTS;`，确认能登录且权限仅覆盖这两个项目库。`rbac_app` 用于普通后端，`rbac_migrator` 用于结构迁移；两者都不是权限中心的业务管理员账号。

**本步成功标志：** 两个本机库存在；两个账号可以登录；运行账号只有上述DML权限。空库此时还不能启动普通后端，先做下一步迁移。

## 5. 配置并执行独立迁移：测试库 → 开发库

在 IDEA 打开“运行 → 编辑配置”，点击 `+`，选择 **应用程序**，创建下面的迁移配置：

| 项目 | 填写内容 |
| --- | --- |
| 名称 | `MigrateTestDatabase` |
| 主类 | `com.adgn.backend.migration.MigrationApplication` |
| JRE / 模块类路径 | JDK21 / `backend` 模块 |
| 工作目录 | 本机项目的 `backend` 目录 |
| 程序参数、VM中的profile参数 | 留空；入口自动启用 migrate，不再另配 dev/test |
| 存储为项目文件 | 不勾选，本机密码不随Git共享 |

找不到“环境变量”时，点击 **修改选项 → 操作系统 → 环境变量**，再点字段右侧的编辑按钮，在上方“用户环境变量”表格中逐行添加：

| 变量名 | 测试库迁移取值 |
| --- | --- |
| `RBAC_MIGRATION_URL` | `jdbc:mysql://localhost:3306/rbac_admin_test` |
| `RBAC_MIGRATION_USER` | `rbac_migrator`，或下文触发器策略要求的本机管理身份 |
| `RBAC_MIGRATION_PASSWORD` | 上述迁移身份对应的本机密码 |

**迁移前处理触发器权限：** 本项目V2创建触发器。若第4步返回 `log_bin=1` 且 `trust_creators=0`，库内TRIGGER授权仍可能不够；本机首次初始化沿用管理员受控方案，在这份迁移配置中使用本机管理身份（如root）和相应密码，完成V1～V3。其余允许受限迁移账号执行的实例使用rbac_migrator。普通后端仍用rbac_app，不修改全局trust设置，也不向运行账号授予SUPER。依据见[MySQL触发器权限说明](https://dev.mysql.com/doc/refman/8.4/en/create-trigger.html)。

点击应用并运行 `MigrateTestDatabase`。成功后该进程会自动结束，不监听8080。在客户端切换到测试库查询：

```sql
USE `rbac_admin_test`;
SELECT version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, revision FROM mutation_guard;
```

当前初始版本应看到1、2、3的success均为1；全新库的锚点为 `id=1, revision=0`，并有43张业务表及Flyway历史表。业务表没有业务记录是正常的。再次运行迁移应提示已是最新版本；后续提交有新迁移时则执行新版本。

测试库成功后，**复制这份运行配置**，命名为 `MigrateDevDatabase`，只将 `RBAC_MIGRATION_URL` 最后的库名改成 `rbac_admin_dev`，保留正确迁移身份，运行并对开发库执行同样的查询。

**本步成功标志：** 两个库迁移成功、进程退出码0、存在mutation_guard。表结构由现有Flyway脚本创建，不手工粘贴schema.sql。若DDL中途失败，保留错误定位，不直接clean/repair或删除已有数据。

## 6. 配置普通后端和自己的 HMAC 密钥

再建一份 **Spring Boot** 配置（没有该选项就选“应用程序”），命名 `BackendApplication`：主类为 `com.adgn.backend.BackendApplication`，JRE为21，类路径为backend，工作目录为backend。不要从迁移配置复制管理账号到普通运行配置。

同样通过“修改选项 → 环境变量”添加下列变量。这里的“用户环境变量”指**这份IDEA运行配置内的变量**，无需去Windows系统设置里添加：

| 变量名 | 填写内容 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/rbac_admin_dev` |
| `SPRING_DATASOURCE_USERNAME` | `rbac_app` |
| `SPRING_DATASOURCE_PASSWORD` | 第4步自己设置的运行账号密码 |
| `RBAC_IDEMPOTENCY_ENABLED` | `true` |
| `RBAC_IDEMPOTENCY_CURRENT_KEY_ID` | `local-v1` |
| `RBAC_IDEMPOTENCY_KEYS_JSON` | 按下面步骤生成的JSON对象原文，不额外包一层引号 |

普通应用默认使用dev，Spring Boot的“有效配置文件”可留空；普通“应用程序”的程序参数留空。不添加 `RBAC_MIGRATION_*`，不勾选“存储为项目文件”。这三份运行配置各自保存变量，修改其中一份不会自动修改另外两份；也不要把自己的启动配置当成已经发给队友。

在自己的 PowerShell **执行一次**下面的代码，生成32字节随机密钥，并把完整密钥JSON复制到本机剪贴板；随后粘贴到上表 `RBAC_IDEMPOTENCY_KEYS_JSON` 的值：

```powershell
$taskKeyBytes = New-Object byte[] 32
$taskKeyRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$taskKeyRandom.GetBytes($taskKeyBytes)
$taskKeyRandom.Dispose()
$taskKeyJson = @{ 'local-v1' = [Convert]::ToBase64String($taskKeyBytes) } | ConvertTo-Json -Compress
Set-Clipboard -Value $taskKeyJson
Remove-Variable taskKeyBytes, taskKeyRandom, taskKeyJson
```

生成结果保存在自己的本机运行配置中，后续重启沿用，不要每次重新生成。各自独立数据库可各自生成；将来多个实例连接同一数据库时，才需通过私下渠道使用一致的密钥环。密钥不发聊天、不截图、不进Git，不复用数据库密码。Spring Boot不会自动读取项目中的`.env`文件；本指南直接使用IDEA环境变量，不依赖.env。

**本步成功标志：** 普通配置有上述6项变量；密码为运行账号密码、密钥JSON可解析，current keyId与JSON中的版本名一致。

## 7. 启动后端并确认本机环境就绪

1. 选择刚配置的 `BackendApplication` 运行，确认控制台显示Hikari/JPA初始化完成及应用启动成功。
2. 浏览器打开 `http://localhost:8080/actuator/health`，应返回包含 `"status":"UP"` 的JSON；`liveness`、`readiness`分组是正常内容。
3. 确认运行配置URL仍指向自己的开发库，用户名为rbac_app；健康响应不会替你显示或证明账号权限。

如果8080被其他进程占用，可以在普通运行配置增加 `SERVER_PORT=8081`，再访问8081。后续健康检查和调用地址都使用自己配置的端口。

首次配置完成后，应有三份配置：

| 运行配置 | 用途 | 运行时机 |
| --- | --- | --- |
| `MigrateTestDatabase` | 对本机测试库执行结构迁移，完成后退出 | 首次建库或拉到新迁移时 |
| `MigrateDevDatabase` | 对本机开发库执行结构迁移，完成后退出 | 测试迁移确认成功之后 |
| `BackendApplication` | 以运行账号启动HTTP后端 | 日常开发调试 |

## 8. 环境通过后，开始配合开发

在仓库根目录，从团队最新共同主线创建自己的任务分支。下面以已分派BE-06为例，按实际任务改名：

```powershell
git switch main
git pull --ff-only
git switch -c codex/be-06-login
```

先由项目负责人登记任务的唯一Owner，并确认依赖就绪；随后按[任务清单](TASKS.md)阅读对应UC、[OpenAPI契约](../03-api/openapi.yaml)、数据库和模块设计，使用[项目TDD技能](../../.agents/skills/test-driven-development/SKILL.md)及配套writing-good-tests开展开发。需要Apifox时按[契约同步说明](../03-api/APIFOX.md)导入；Git中的契约是共同依据。

每次拉到新迁移，先在本机测试库执行迁移、核对成功，再对开发库迁移并重启后端。新增迁移与其他任务协调版本号，不改已经执行的V1～V3。普通应用不会自动建表或迁移。

提交前运行适用测试并核对差异，记录RED/GREEN和限制；推送自己的任务分支，例如 `git push -u origin codex/be-06-login`。如果没有推送权限，联系项目负责人配置仓库访问；不因此改推共同main。每项任务由负责人核对证据并确认完成，不设置独立评审人，详情见[团队协作规则](TEAM-WORKFLOW.md)。

**测试范围区别：** 默认 `clean verify` 是入门必做的离线构建；本机 `rbac_admin_test` 用于手工调试/迁移核对，不能直接用作真实引擎自动化测试目标。涉及事务/约束/并发行为时，按[真实MySQL测试说明](BE-03-PERSISTENCE.md)准备专属临时实例（独立datadir、非3306端口），再运行显式 `mysql-check`；不要仅添加该profile后指向已有开发/测试库。入门配置通过不等于这些测试已在你的电脑执行。

## 常见问题与最后核对

| 现象 | 先检查什么 |
| --- | --- |
| Maven仍标红或JVM目标不对 | Project/模块、Maven导入、Maven运行及应用JDK是否均为21；是否使用Wrapper并重新加载 |
| Connection refused | 本机MySQL服务是否启动、端口是否与JDBC URL一致 |
| Access denied | 当前运行的是哪份配置；账号/Host/密码是否对应；用同账号在数据库客户端验证 |
| Missing RBAC_MIGRATION_URL | 填变量的配置和当前选中运行配置是否同一份；主类是否为MigrationApplication |
| 迁移提示profile不能混用 | 迁移配置的有效profile、程序参数、VM或继承环境是否额外设了dev/test；只保留入口自动启用的migrate |
| V2触发器权限失败 | 按第5步核对binlog策略及独立迁移身份，不提高普通运行账号权限 |
| RBAC_DB_NOT_INITIALIZED | 普通应用URL所指库是否已迁移成功并存在guard；是否误连空库 |
| Invalid idempotency key configuration | 开关、keyId、JSON原文及Base64解码后至少32字节；JSON值未多套引号 |
| 后端启动但找不到登录或业务API | 当前仅有公共基座，相应业务接口按后续任务实现；不是靠增加数据库账号解决 |

完成下面清单即可向项目负责人报告“本机配置完成”，只反馈版本、提交号、测试结论及健康状态，不发送密码或密钥：

- [ ] 共同代码版本已核对，JDK21/Wrapper3.9.11导入正常，`clean verify`成功。
- [ ] 自己的开发库/测试库及账号就绪，测试库与开发库迁移均成功。
- [ ] 三份IDEA运行配置分开，普通应用使用rbac_app，幂等开关和自己的密钥已保存。
- [ ] 普通应用启动成功，正确端口的健康接口为UP。
- [ ] 仓库访问权限、实际任务负责人及依赖已明确，可以在自己的任务分支开始工作。

底层事务/幂等/审计接口的详细使用另见[后端运行及接入说明](../../backend/README.md)、[BE-04](BE-04-TRANSACTIONS.md)和[BE-05](BE-05-IDEMPOTENCY.md)。本文件负责首次clone后的本机准备，不要求成员重新实现公共基座。
