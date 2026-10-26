# 归档：systemd 直接运行 JAR 部署方案

归档日期：2026-10-26。当前项目采用 Docker Swarm，本目录保留旧方案及原模板，仅供查阅，不作为当前云端部署入口。切勿与 Swarm 同时启动同一后端。归档不表示相关历史部署已验收。当前使用 [Swarm 操作指南](../docker/README.md)。

以下为归档时的原操作说明，历史状态按原文保留。

BE-32 提供同一个 jar 的发布方法及通用 Linux 模板。Windows/IDE 与服务器不使用不同的业务代码，只调整数据库、密钥、持久目录和进程启动配置。本轮没有连接或配置云服务器；真实 HTTPS、重启与备份恢复由 BE-34 验证。

## 本机先验证管理员初始化

已经能启动 BackendApplication 的成员无需重建密钥。先确认用于本次验证的数据库已通过独立迁移，保留 V1～V3 成功记录；不要用不明数据库试运行。

在 IDEA 的“运行 → 编辑配置”中：

1. 点击 `+ → 应用程序`，命名 `BootstrapTestAdmin`，JRE 选项目 JDK21，模块选 backend。
2. 主类填 `com.adgn.backend.bootstrap.BootstrapAdminApplication`，工作目录选 backend，程序参数留空。
3. 打开“环境变量”编辑表格，逐行添加以下七项（口令只填本机，不提交运行配置）。这里**不需要额外 RSA 或 HMAC 密钥**，它们仍由正常 BackendApplication 使用。

| 变量 | 测试库示例/填写方式 |
| --- | --- |
| RBAC_BOOTSTRAP_URL | `jdbc:mysql://localhost:3306/rbac_admin_test` |
| RBAC_BOOTSTRAP_USER | 已有 `rbac_app`；SELECT/INSERT/UPDATE 即可，不用 root |
| RBAC_BOOTSTRAP_PASSWORD | 该数据库账号的本机口令 |
| RBAC_BOOTSTRAP_CONFIRM_DATABASE | `rbac_admin_test`，必须和 URL 中的库名完全一致 |
| RBAC_BOOTSTRAP_ADMIN_LOGIN | 自定后台登录名，例如 `initial.admin` |
| RBAC_BOOTSTRAP_ADMIN_PASSWORD | 自定首次登录口令，12～128 字符，不是数据库密码 |
| RBAC_BOOTSTRAP_ADMIN_CONFIRM_PASSWORD | 再输入相同首次登录口令 |

4. 停止 BackendApplication，运行此配置。成功输出 `BOOTSTRAP_CREATED` 并自动退出；不会启动 8080。
5. 再启动原 BackendApplication（保留原身份/RSA 配置），按 [BE-06 接入说明](../../docs/05-detailed-design/BE-06-LOCAL-SETUP.md)调用登录接口。首次 token 仅能改密或退出；先 `PUT /api/v1/admin/password`，再用新密码登录，账号列表应返回 200。尚无正式登录页面，可用 Apifox 本机环境调用，勿把返回 token 保存为共享示例。
6. 重跑初始化应拒绝 `ADMIN_ALREADY_EXISTS`，不会重置原密码。已有总管理员（包括停用账号）时不需要初始化；此工具不承担找回密码。
7. 删除 IDEA 初始化配置中的口令或移除此配置；不要勾选“存储为项目文件”。正常运行配置不添加任何 `RBAC_BOOTSTRAP_*`。

正常后端必须连接初始化管理员的同一实例/schema。可复制为 `BackendApplication-Dev`（dev库）和 `BackendApplication-Test`（test库），均使用 `rbac_app` 运行账号，分别保存URL，验证时选Test，不必每次修改。两份默认都用8080，切换前先停止当前实例；bootstrap只创建指定库的账号，不修改其他运行配置或同步其他库的数据。独立迁移仍使用 `rbac_migrator`。

只读核对（选择本次目标库）：

```sql
SELECT login_name, employee_id, enabled, is_super_admin, must_change_password
FROM admin_account WHERE is_super_admin=1;
SELECT event_type, actor_kind, visibility, result
FROM audit_event WHERE event_type='ADMIN_BOOTSTRAPPED';
```

首次改密前应为 enabled=1、is_super_admin=1、employee_id=NULL、must_change_password=1；改密后最后一项为 0。不要查询/分享 password_hash、会话或 token。

退出码：0 创建成功；2 配置/目标确认无效；3 已有总管理员或登录名被占用；4 数据库、迁移、guard 或事务失败。遇 4 先核对数据库中的账号和审计状态，不自动重试或清库。Flyway 校验和不符时按迁移恢复流程处理，不直接 repair。

## 本机登录调试

`POST http://localhost:8080/api/v1/admin/sessions` 是公开登录入口，Apifox 的 Auth 应选“无认证”，不继承项目 Bearer；实际请求不应出现未解析的 `Bearer {{bearerToken}}`。Git 的登录契约为 `security: []`。默认模块/MCP 暴露的旧认证差异不能当作正确登录设置，导出覆盖也不等于已修改每份本机调试请求。

Body 使用 JSON 对象，字段为 `loginName`、`password`；前者填写初始化时的管理员登录名，后者填写首次 `BOOTSTRAP_CREATED` 时的管理员密码。`RBAC_BOOTSTRAP_PASSWORD` 是数据库口令，不能用来登录后台。初始化成功后，再改 bootstrap 环境变量不会重置已存在账号；首次改密成功后应使用新密码。

| 响应 | 本项目的排查方向 |
| --- | --- |
| 422，指向 X-Request-Id | 删除空、非法或重复请求头，允许后端生成；不是管理员密码验证失败 |
| 400 MALFORMED_REQUEST | 核对实际 URL、Content-Type、真实发送的 JSON；先排除 `//api/`、空正文或格式错误 |
| 401 AUTHENTICATION_FAILED | 账号在运行库不存在、密码不匹配或当前账号资格无效；按统一失败响应处理，不能从正文区分具体原因 |
| 201 | 登录成功；初始化账号首次应为 PASSWORD_CHANGE_ONLY，改密后重新登录为 NORMAL |

账号列表 `GET /api/v1/admin/accounts` 返回422且errors为空时，核对 Params：可只发送 `page=1&pageSize=20`；未使用的employeeId/q/enabled取消勾选，不要发送空字符串。Body选none；Auth使用新密码重新登录后的正常token。若鉴权凭证由父级继承且输入框不可编辑，在右上凭证下拉切换“手动设置”后填入accessToken本身，不额外加Bearer前缀；实际请求应只出现一项有效Authorization头。本人改密和列表分别配置，登录请求保持无认证。

登录 401 时先比较两个 IDEA 配置的目标：bootstrap 的 `RBAC_BOOTSTRAP_URL` 与正常 BackendApplication 的 `SPRING_DATASOURCE_URL` 应指向同一实例和 schema，例如均为 `rbac_admin_test`。修改运行配置后须停止旧进程并重新启动；Navicat 选择了测试库不代表 Java 应用也连接测试库。

在初始化目标库运行以下只读查询，不查询密码哈希或会话：

```sql
SELECT login_name, employee_id, enabled, is_super_admin, must_change_password
FROM rbac_admin_test.admin_account WHERE login_name='initial.admin';
```

首次初始化账号应存在，employee_id=NULL、enabled=1、is_super_admin=1；必须改密标记为1不会阻止登录，只会限制首次会话。没有记录时先核对初始化成功输出及实际目标；已有记录时不清库、不手工改哈希、不靠重跑初始化重置密码。密码只在本机核对，不粘贴到共享资料。

当前后端对该 POST 路径跳过 Bearer 校验，因此发现客户端继承 Bearer 时应纠正配置，但不能据此断言它导致了本次 401。Apifox 右侧若固定选择 201 校验，会将正确的 401 Problem 响应误报为“成功结构不匹配”；应分别核对各状态定义。正式排查不降低后端认证要求。

## 构建与发布产物

在已提交的任务版本上记录 `git rev-parse HEAD` 和 `git status --porcelain`。有工作区改动时先归入对应提交，不能把未提交代码标成某个已存在的 commit。backend 中执行：

```powershell
# IDEA 的 Maven 配置也可直接运行 clean verify；JDK 选 21。
./mvnw.cmd clean verify
Get-FileHash ./target/backend-0.0.1-SNAPSHOT.jar -Algorithm SHA256
```

Linux 使用 `./mvnw clean verify`、`sha256sum target/backend-0.0.1-SNAPSHOT.jar`。发布前还须在独立临时 MySQL8.4 上执行 `-Pmysql-check clean verify`，按[隔离验证记录](../../docs/05-detailed-design/BE-03-MYSQL-VALIDATION.md)准备显式的临时端口和数据目录；不是对当前开发库运行集成测试。打包命令不能添加 skipTests。

归档 `app.jar`（由上述 jar 重命名）、`SHA256SUMS`、`release.txt`（完整源码 commit、Java/Maven 版本、两组测试结果）、本目录的无凭据模板。测试证据来自实际报告，不能只复制示例计数。传输后再校验 SHA256；密钥、真实 env 文件、数据库和原始测试日志不进入发布包或 Git。

## 通用 Linux 示例：准备一次，后续复用

本节由服务器操作者核对发行版和 JDK21 的实际路径后执行；不是要求本机开发者安装 Linux。`/usr/bin/java` 必须确认为 Java21。安装 systemd/nginx 的发行版命令、DNS、证书和防火墙由 BE-34 落实；数据库不开放公网。

1. 创建专用无登录系统账号 `rbac-admin`。创建 root 管理的 `/opt/rbac-admin/releases/<commit>/` 和 `/etc/rbac-admin/`；发布文件只允许 root/部署操作者改写，运行账号只读。创建运行账号可写、其他用户不可读写的 `/var/lib/rbac-admin/imports/`。持久目录不放在 releases 中。
2. 将已校验 `app.jar` 放入对应 release；`/opt/rbac-admin/current` 指向该 release。发布配置前确认没有外来 `loader.properties`、LOADER_* 或 JAVA_TOOL_OPTIONS，避免覆盖独立入口。
3. 分别复制三个 `.env.example` 到 `/etc/rbac-admin/server.env`、`migrate.env`、`bootstrap.env`，填写真实值，root 所有、权限 0600；运行服务不读取迁移/初始化文件。systemd 负责读取 EnvironmentFile；这些文件不是 shell 脚本，不要 `source` 或写 `export`。不要在聊天、PR、日志粘贴其内容。
4. 服务端使用独立部署密钥，按现有 [RSA 配置说明](../../docs/05-detailed-design/BE-06-LOCAL-SETUP.md)准备 admin 私钥及 business/OA 公钥（后两者不能包含私钥），写入 `/etc/rbac-admin/keys/`，root 所有、rbac-admin 组只读，目录 0750、文件 0640。HMAC 使用独立随机至少 32 字节的密钥环，格式见 [后端运行说明](../../backend/README.md)；同一部署的多实例使用同一有效密钥环，不能每次启动重生成。
5. server.env 显式开启身份和幂等，填实例标识及导入持久目录；server profile 缺开关或目录会拒绝启动。BE-28 已交付 main/4b384fb，支持上传与不可变存储，详见 [BE-28 使用说明](../../docs/05-detailed-design/BE-28-IMPORT-FILES.md)，仍待负责人确认；本指南的真实服务器验收仍由 BE-34 完成。已有身份与幂等装配负责验证密钥；不要同时传 dev/migrate profile。服务端 MySQL 独立账号/权限沿用 [数据库运行说明](../../backend/README.md)。
6. 将三个 `.service` 放入 `/etc/systemd/system/`，按实际 JDK 路径调整 ExecStart；执行 `systemd-analyze verify` 核对本机兼容性，再 `systemctl daemon-reload`。两个 oneshot 入口**不启用开机运行，也没有自动依赖**。迁移触发器所需 MySQL binary log/权限条件须先按迁移文档核对，不能为了省事改用应用 root 账号。

首次发布的显式顺序：

```sh
sudo systemctl stop rbac-admin
# 先完成备份、目标实例/schema 与账号核验，再迁移。
sudo systemctl start rbac-migrate
sudo systemctl status rbac-migrate --no-pager
# 只有迁移成功且确实没有总管理员，才执行初始化。
sudo systemctl start rbac-bootstrap
sudo systemctl status rbac-bootstrap --no-pager
# 核对成功后删除临时 bootstrap.env；迁移凭据移交受控保管。
sudo systemctl start rbac-admin
curl --fail http://127.0.0.1:8080/actuator/health
```

确认健康 UP 后再配置域名/证书，审查 `nginx.conf.example`，运行 `nginx -t` 后加载；使用 HTTPS 验证健康、未登录 401、首次改密与正常会话。HTTPS 代理透传认证头，不能以代理头替代认证。应用回环绑定不改，443 是公网入口，8080 与数据库端口均不开放公网。

`systemctl enable rbac-admin` 只在该部署验证完成后执行。日志查看 `journalctl -u rbac-admin`，不要开启 SQL/请求正文/环境 DEBUG 日志。journald 容量与保留周期按实际服务器规划，不把日志写入 release。示例未运行 systemd/nginx，也未证明真实 HTTPS 已通。

## 更新、回退及备份

每次升级先保存旧 commit/jar/SHA 和当前 schema 版本，暂停流量并停止应用写入，再备份 MySQL、imports 与对应密钥配置，核验迁移脚本兼容性。新版本放新 release，独立迁移成功后才启动服务；任何一步失败即停止后续步骤，不能用健康 UP 掩盖迁移失败。

回退时先停止服务，确认旧 jar 与现有 schema 兼容，再将 current 指回旧 release 并启动、验证健康与登录。保持密钥环、账号/会话状态和 imports 不变；不删除 Flyway 记录、不自动降级、不执行 clean/repair。schema 不兼容时使用预先验证的恢复或修复方案，不能只换旧 jar。

BE-34 在独立恢复环境演练数据库与文件恢复，核对审计、会话撤销和已受理外部事实后才确认服务器验收。保留周期、RPO/RTO 尚未确认；本轮不宣称服务器或完整业务恢复已通过。

参考：[Boot 独立入口](https://docs.spring.io/spring-boot/4.0/specification/executable-jar/property-launcher.html)、[systemd 执行配置](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html)、[nginx 代理](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)。模板应按目标服务器安装版本复核。


申请、OA受理与获批执行的本机演示见[演示OA步骤](../DEMO-OA.md)，受控CLI变量见[模板](../demo.env.example)。
