# Clone 后的本机配置与协作开发指南

BE-32 接入：需要真实后台账号时，在完成数据库迁移后，按 [IDEA 初始化步骤](../../deploy/README.md)创建一次本机总管理员，再通过现有登录/首次改密接口使用。初始化无需额外密钥；已有总管理员时不要重复创建，普通启动不会自动初始化。只有部署操作者需要阅读该文档的 Linux 示例，组员本机开发无需选择服务器模板。

D-056业务接入更新：用户认领A并开始BE-06，普通身份业务启用、RSA密钥文件及Controller/Service目录样例见[BE-06接入说明](BE-06-LOCAL-SETUP.md)。本文仍用于配置公共基座；新业务启用不以健康UP代替验收。

D-058：BE-07账号管理沿用原RSA/HMAC和数据库配置，不新增迁移或密钥；写接口要求身份与幂等均启用，操作与验证见[账号管理](BE-07-ACCOUNTS.md)。

任务接入：先在[四人分工说明](TEAM-ASSIGNMENT.md)查看自己对应A/B/C/D的任务。30项已登记成员代号，具体姓名/GitHub账号由团队补充；完成本文环境配置并满足所属任务原前置后再开工，各任务由负责人自己确认完成。

适用于首次加入项目的成员。按下面顺序完成一次配置，即可在自己的电脑构建、初始化数据库、启动后端，并从共同基线领取任务开发。主流程使用 Windows、IntelliJ IDEA 和本机 MySQL；PowerShell只用于Git命令、生成本机密钥及可选的终端构建。所有路径均相对项目根目录 `rbac-admin`。

每位成员使用自己的本机数据库、密码和幂等密钥；不需要复制其他成员的数据库文件或个人 IDEA 配置。公共基座已按D-053交付main，具体提交及后续状态以[共享记忆](../project/MEMORY.md)为准；正式业务开工还需实际任务已分派及对应依赖就绪。

**先明确：主流程在IDEA中选择自己的JDK21即可，不要求设置Windows的JAVA_HOME或PATH。** Project SDK、Maven使用的JDK及每份运行配置的JRE按下面界面核对；已经继承正确JDK的字段无需重复修改。后面使用PowerShell生成密钥也不依赖JAVA_HOME。

## 0. 让AI协助时，先给它这段说明

可以把下面内容连同本文交给自己的AI助手，按步骤推进；由成员在自己的IDEA中填写秘密：

```text
我已clone rbac-admin，请按 docs/05-detailed-design/FOUNDATION-USAGE.md 帮我完成本机配置。
先读根AGENTS.md和docs/project中的MEMORY、WORKFLOW、DECISIONS、GLOSSARY。
优先走IDEA界面，不把配置系统JAVA_HOME/PATH当作前置。
先核对共同代码版本和本机已有工具；需要菜单定位时核对我的IDEA版本、语言和当前界面。
每一步给出点击路径、字段、应该选择的值和成功标志，确认本步结果后再推进依赖步骤。
请区分项目SDK、模块SDK、Maven导入JDK、Maven运行JRE和应用运行JRE。
密码和HMAC密钥由我本机输入，不索取这些值，不读取或回显我的运行配置秘密。
写库前核对本机实例和指定schema，保留已有数据；不要把未运行的操作写成成功。
完成后给我一份已核对/未完成清单，不自行接受业务任务、合并主线或推送。
```

不必让AI重新设计环境方案。成员反馈IDEA版本、无敏感内容的界面和错误类别即可；菜单名有差异时，AI应按实际界面继续定位，而不是强行要求改成终端操作。本文说明和检查结果不能代替成员机器上的真实构建、迁移与启动结果。

## 1. 确认拿到共同代码版本

在 clone 得到的 `rbac-admin` 根目录打开终端，工作区没有待保存改动时执行：

```powershell
git fetch origin
git switch main
git pull --ff-only
git rev-parse --short HEAD
```

确认目录内有 `backend/pom.xml`、`backend/mvnw.cmd` 和 `docs/project/MEMORY.md`，并与项目负责人公布的交付版本一致。若拉取后仍看不到backend，先核对origin是否为团队仓库及当前分支是否为更新后的main；正式任务从共同主线创建分支。

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

### 2.1 打开项目，设置项目JDK

1. IDEA欢迎页点“打开”，选择 `backend/pom.xml`，再选 **作为项目打开 / Open as Project**；Git仍使用上一级根仓库。已有项目窗口时可从“文件 → 打开”进入。
2. 点左上角主菜单（新版界面可能在三横线里）→ **文件 → 项目结构 / File → Project Structure**。Windows默认快捷键为 `Ctrl+Alt+Shift+S`。
3. 左侧选 **项目设置 → 项目 / Project Settings → Project**，找到 **SDK / Project SDK** 下拉框，选本机JDK21，例如显示 `corretto-21`、`java 21` 的SDK；以实际版本21为准，不要求大家名称一样。
4. 如果列表没有自己的JDK，点 **添加SDK → JDK / Add JDK from disk**，选本机JDK根目录，也就是包含`bin`、`lib`的文件夹，不选择`bin`目录或`java.exe`。完全没安装时，也可在这里选“下载JDK / Download JDK”，版本选21；下载完成后再选中它。
5. 同一页的 **语言级别 / Language level** 选21的正式级别，或继承SDK21的默认值，不选preview。点“应用”。

这一步是在IDEA里登记/选择JDK，和Windows系统环境变量是两回事。无需去“系统属性 → 环境变量”操作。界面依据见[JetBrains项目结构](https://www.jetbrains.com/help/idea/project-settings-and-structure.html)及[SDK配置](https://www.jetbrains.com/help/idea/sdk.html)。

### 2.2 让backend模块继承项目JDK

仍在“项目结构”窗口：

1. 左侧选 **模块 / Modules**，中间选 `backend`；若此时没有该模块，先确认打开的是pom.xml并完成Maven导入。
2. 右侧选 **依赖 / Dependencies** 标签，**模块SDK / Module SDK** 选 **项目SDK / Project SDK**，确认显示继承的是21；已经如此就保持。
3. 切到 **源 / Sources** 标签，语言级别选 **项目默认 / Project default**，继承上一页的21。
4. 点“应用 → 确定”。如果有额外列出的backend子模块，也核对其SDK继承21。

项目语言级别、模块SDK及模块语言级别可以独立设置，所以需要核对继承关系。菜单依据见[JetBrains模块配置](https://www.jetbrains.com/help/idea/configure-modules.html)。

### 2.3 配置Maven的Wrapper、导入JDK和运行JRE

点 **文件 → 设置 / File → Settings**（Windows默认 `Ctrl+Alt+S`），展开 **构建、执行、部署 → 构建工具 → Maven / Build, Execution, Deployment → Build Tools → Maven**：

| 左侧页面 | 右侧字段 | 选择的值 |
| --- | --- | --- |
| Maven | Maven主路径 / Maven home path | **使用Maven Wrapper / Use Maven wrapper**；版本来自仓库，固定3.9.11 |
| Maven → 正在导入 / Importing | 导入程序的JDK / JDK for importer | 本机JDK21；“项目JDK”能解析为21时也可使用 |
| Maven → 运行程序 / Runner | JRE | 本机JDK21或已解析为21的项目JDK；无需选择依赖未配置JAVA_HOME的选项 |

点“应用 → 确定”。这些字段决定Maven同步和执行目标时用哪个Java，并非给Windows设置环境变量。入口依据见[JetBrains的Maven JDK设置](https://www.jetbrains.com/help/idea/maven-support.html)。如果当前版本菜单略有不同，在设置窗口搜索 `Maven`，按Importing和Runner字段定位。

打开 **视图 → 工具窗口 → Maven / View → Tool Windows → Maven**（或右侧m图标），确认列出backend，点击顶部 **重新加载所有Maven项目 / Reload All Maven Projects** 循环箭头，等待下载和同步完成。看不到Maven项目时，在项目树右击pom.xml，选择“添加为Maven项目 / Add as Maven Project”，再重新加载。

**本步成功标志：** Project SDK=21、Module SDK继承21、Maven导入和运行使用21，Wrapper来自项目；Java/Spring依赖已识别。只修改pom.xml里的Java版本不能替代本机这些JDK选择。

## 3. 在IDEA里构建一次，确认代码和工具正常

按以下方式创建可重复使用的Maven构建配置，全程无需JAVA_HOME：

1. 点 **运行 → 编辑配置 / Run → Edit Configurations**，左上角 `+` 选择 **Maven**，命名 `VerifyBackend`。
2. 工作目录选当前项目的 `backend` 文件夹；**运行 / Run（部分版本叫命令行 / Command line）** 填 `clean verify`，只填Maven目标，不加`mvn`或`mvnw.cmd`前缀。
3. Maven版本选项目Wrapper/继承上述Wrapper设置；JRE继承第2.3步的Runner JDK21，或在该配置的“修改选项 → Java选项 → JRE”中显式选21。
4. 确认 **跳过测试 / Skip Tests** 未勾选，不添加`-DskipTests`；首次依赖下载时不勾选Maven的 **脱机工作 / Work offline**。
5. 点“应用 → 运行”，或在IDEA右上角配置下拉框选 `VerifyBackend` 后点绿色三角。

这组默认测试不连接你的MySQL，也不要求先填数据库密码或HMAC密钥。“离线测试”指测试不访问数据库，并不表示Maven首次下载依赖可以断网。Maven运行配置字段依据见[JetBrains说明](https://www.jetbrains.com/help/idea/run-debug-configuration-maven.html)。

**本步成功标志：** 构建控制台显示 `BUILD SUCCESS`，测试没有失败或错误。当前BE-07分支为75项离线测试；基座交付时为64项，数量以所在提交的实际报告为准，jar位于 `backend/target/`。`VerifyBackend`是Maven构建配置，后面三份是应用运行配置，作用不同。

仅当你自己选择在终端运行Maven时，才查看文末“可选：终端构建”；IDEA流程不需要先去做该附录。

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

在IDEA点 **运行 → 编辑配置 / Run → Edit Configurations**，点击左上角 `+`，选择 **应用程序 / Application**，创建下面的迁移配置（与第3步的Maven配置分开）：

| 项目 | 填写内容 |
| --- | --- |
| 名称 | `MigrateTestDatabase` |
| 主类 | `com.adgn.backend.migration.MigrationApplication` |
| JRE / 模块类路径 | JDK21 / `backend` 模块 |
| 工作目录 | 本机项目的 `backend` 目录 |
| 程序参数、VM中的profile参数 | 留空；入口自动启用 migrate，不再另配 dev/test |
| 存储为项目文件 | 不勾选，本机密码不随Git共享 |

JRE在该配置右侧“构建和运行”区域的Java下拉框选择21；“使用模块的类路径 / Use classpath of module”选backend。如果JRE或类路径字段隐藏，点“修改选项 / Modify options”，启用相应Java选项再填写。项目SDK正确时可显示继承backend模块的SDK21；仍需确认每份配置实际用21。

找不到“环境变量”时，点 **修改选项 → 操作系统 → 环境变量 / Modify options → Environment variables**。出现字段后，点最右侧编辑按钮；在弹窗上方“用户环境变量”表格点 `+`，每行填写一个“名称”和“值”，无需自己拼接分号字符串。填写后先点弹窗“确定”，再点外层配置窗口“应用”。本表格只作用于当前选中的配置：

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

回到“运行 → 编辑配置”。如果左侧已经有主类正确的 `BackendApplication` 普通运行配置，就直接选中并复用；没有时，点 `+` 新建 **Spring Boot** 配置，没有该类型就选 **应用程序 / Application**。只需维护其中一份普通启动配置，不必在两个分类下各建一份同名配置。

| 项目 | 填写/核对的值 |
| --- | --- |
| 名称 | `BackendApplication`（也可自定，但启动时须选中同一份） |
| 主类 / Main class | `com.adgn.backend.BackendApplication` |
| JRE / SDK | 本机JDK21或继承backend模块的SDK21；不是在这里填写JAVA_HOME字符串 |
| 使用模块的类路径 | `backend`；隐藏时用“修改选项”显示 |
| 工作目录 | 本机项目的 `backend` 文件夹，用文件夹按钮选择 |
| 程序参数 / Program arguments | 留空 |
| 有效配置文件 / Active profiles（Spring Boot类型） | 留空，普通应用默认dev |
| 存储为项目文件 | 不勾选 |

不要从迁移配置复制管理账号到普通运行配置。

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

1. 在IDEA右上角配置下拉框选择刚填写变量的 `BackendApplication`，再点绿色三角运行；不要选成 `VerifyBackend`、迁移配置或另一个同名BackendApplication。确认控制台开头的Java可执行文件来自自己的JDK21（不必分享个人完整路径），日志显示Java21、Hikari/JPA初始化及应用启动成功。
2. 浏览器打开 `http://localhost:8080/actuator/health`，应返回包含 `"status":"UP"` 的JSON；`liveness`、`readiness`分组是正常内容。
3. 确认运行配置URL仍指向自己的开发库，用户名为rbac_app；健康响应不会替你显示或证明账号权限。

如果8080被其他进程占用，可以在普通运行配置增加 `SERVER_PORT=8081`，再访问8081。后续健康检查和调用地址都使用自己配置的端口。

首次配置完成后，除 `VerifyBackend` Maven构建配置外，应有下面三份应用运行配置（名称可自定，主类及变量要对应）：

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

先按[四人分工](TEAM-ASSIGNMENT.md)明确自己的成员代号与任务，补姓名/GitHub账号并确认依赖就绪；随后按[任务清单](TASKS.md)阅读对应UC、[OpenAPI契约](../03-api/openapi.yaml)、数据库和模块设计，使用[项目TDD技能](../../.agents/skills/test-driven-development/SKILL.md)及配套writing-good-tests开展开发。需要Apifox时按[契约同步说明](../03-api/APIFOX.md)导入；Git中的契约是共同依据。

每次拉到新迁移，先在本机测试库执行迁移、核对成功，再对开发库迁移并重启后端。新增迁移与其他任务协调版本号，不改已经执行的V1～V3。普通应用不会自动建表或迁移。

提交前运行适用测试并核对差异，记录RED/GREEN和限制；推送自己的任务分支，例如 `git push -u origin codex/be-06-login`。如果没有推送权限，联系项目负责人配置仓库访问；不因此改推共同main。每项任务由负责人核对证据并确认完成，不设置独立评审人，详情见[团队协作规则](TEAM-WORKFLOW.md)。

**测试范围区别：** 默认 `clean verify` 是入门必做的离线构建；本机 `rbac_admin_test` 用于手工调试/迁移核对，不能直接用作真实引擎自动化测试目标。涉及事务/约束/并发行为时，按[真实MySQL测试说明](BE-03-PERSISTENCE.md)准备专属临时实例（独立datadir、非3306端口），再运行显式 `mysql-check`；不要仅添加该profile后指向已有开发/测试库。入门配置通过不等于这些测试已在你的电脑执行。

## 常见问题与最后核对

| 现象 | 先检查什么 |
| --- | --- |
| Maven仍标红或JVM目标不对 | Project/模块、Maven导入、Maven运行及应用JDK是否均为21；是否使用Wrapper并重新加载 |
| 终端java无法识别/JAVA_HOME未设置 | 若走IDEA主流程，继续核对IDEA里的SDK/JRE即可；它不表示必须先修改Windows环境变量。仅终端运行Wrapper时使用文末可选配置 |
| 配好变量仍提示缺失 | 是否编辑了Spring Boot分类下的一份、却启动应用程序分类下的另一份；确认顶部选中的正是已填写的配置，并重新启动进程 |
| Connection refused | 本机MySQL服务是否启动、端口是否与JDBC URL一致 |
| Access denied | 当前运行的是哪份配置；账号/Host/密码是否对应；用同账号在数据库客户端验证 |
| Missing RBAC_MIGRATION_URL | 填变量的配置和当前选中运行配置是否同一份；主类是否为MigrationApplication |
| 迁移提示profile不能混用 | 迁移配置的有效profile、程序参数、VM或继承环境是否额外设了dev/test；只保留入口自动启用的migrate |
| V2触发器权限失败 | 按第5步核对binlog策略及独立迁移身份，不提高普通运行账号权限 |
| RBAC_DB_NOT_INITIALIZED | 普通应用URL所指库是否已迁移成功并存在guard；是否误连空库 |
| Invalid idempotency key configuration | 开关、keyId、JSON原文及Base64解码后至少32字节；JSON值未多套引号 |
| 后端启动但找不到登录或业务API | 当前仅有公共基座，相应业务接口按后续任务实现；不是靠增加数据库账号解决 |

完成下面清单即可向项目负责人报告“本机配置完成”，只反馈版本、提交号、测试结论及健康状态，不发送密码或密钥：

- [ ] 共同代码版本已核对，项目/模块SDK、Maven导入及Runner、每份应用JRE均使用21，Wrapper3.9.11导入正常，IDEA中的`VerifyBackend`成功。
- [ ] 自己的开发库/测试库及账号就绪，测试库与开发库迁移均成功。
- [ ] 三份IDEA运行配置分开，普通应用使用rbac_app，幂等开关和自己的密钥已保存。
- [ ] 普通应用启动成功，正确端口的健康接口为UP。
- [ ] 仓库访问权限、实际任务负责人及依赖已明确，可以在自己的任务分支开始工作。

底层事务/幂等/审计接口的详细使用另见[后端运行及接入说明](../../backend/README.md)、[BE-04](BE-04-TRANSACTIONS.md)和[BE-05](BE-05-IDEMPOTENCY.md)。本文件负责首次clone后的本机准备，不要求成员重新实现公共基座。

## 可选：终端构建（主流程不需要）

仅当你选择在PowerShell而非IDEA的Maven配置中构建，且该终端尚未找到JDK时，从 `backend` 目录执行：

```powershell
$taskLocalJdk = Read-Host '输入本机JDK21根目录（不是bin目录）'
$env:JAVA_HOME = (Resolve-Path -LiteralPath $taskLocalJdk).Path
.\mvnw.cmd --version
.\mvnw.cmd clean verify
```

这里的JAVA_HOME只临时提供给当前终端启动的Wrapper，不永久修改Windows设置，也不会替代IDEA的Project SDK/Maven JDK/运行JRE。已有终端能正确使用JDK21时无需重复设置；选择IDEA构建的成员可以完整跳过本附录。
