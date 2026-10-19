# BE-32 部署产物、配置与管理员初始化

2026-10-19，负责人 A 授权实施，前置 BE-01/02/03/06 已确认并交付 main。先设计/TDD 后实现，完整验证为 80 离线＋88 真实 MySQL 全通过；代码与文档归入本地任务提交，仍待 A 确认，未合并/推送。未连接云服务器或操作已有开发库、测试库。

## 交付边界

提供同一可执行 jar 内的独立管理员初始化命令、Linux/systemd 与 HTTPS 代理模板、构建及校验和说明、迁移/初始化/运行分离的操作指南。BE-34 负责真实服务器部署、重启、备份恢复验收；尚未实现的 OA/执行器和导入功能不作为本轮已交付能力。

## 初始化设计

- `bootstrap.BootstrapAdminApplication` 是独立 Java main，使用 PropertiesLauncher 调用。普通 BackendApplication 不调用它，无控制器、自动初始化或重置已有管理员入口。
- 只读取专用 `RBAC_BOOTSTRAP_*` 环境变量：URL、USER、PASSWORD、CONFIRM_DATABASE、ADMIN_LOGIN、ADMIN_PASSWORD、ADMIN_CONFIRM_PASSWORD。无默认密码，不回退运行/迁移凭据；拒绝命令行参数。目标 schema 须与显式确认值一致，迁移必须已完成。
- 使用已有 PasswordPolicy 的 PBKDF2 哈希；初始口令满足既有 12～128 字符规则。创建 employee_id=NULL 的总管理员，enabled=true、must_change_password=true；登录后沿用 BE-06 的受限改密流程。
- 显式命令凭数据库账号获得部署授权，只需 SELECT/INSERT/UPDATE，不授予 root/DDL。复用 CommandBoundary，先锁 guard，再检查没有任何现存总管理员（包括已停用的），并拒绝占用的登录名。创建账号、GLOBAL 部署审计、guard 推进同事务提交；失败整体回滚，两命令竞争仅一个成功。
- 只输出成功/安全错误类别；不输出 URL、登录名、口令、哈希、SQL、异常堆栈。审计仅保存 schemaVersion 和账号安全标识，不保存凭据。初始化后从运行环境移除全部 bootstrap 变量。

## 发布与配置设计

固定 Java21、Wrapper3.9.11、Boot4.0.8；构建必须通过 clean verify，真实数据库检查使用专属 MySQL8.4 隔离实例。保留 commit、jar SHA256 与测试证据。发布产物不包含开发密钥、.env、原始日志或本机数据库。

普通运行、独立迁移、独立初始化分别使用受限配置；运行账号没有 DDL。模板显式开启身份和幂等，使用外置 RSA/HMAC 密钥；服务以专用系统账号运行，应用只监听回环地址，HTTPS 代理只暴露业务入口和有限健康检查。

持久目录在 releases 外；不依赖工作目录存放导入文件。回退只换与当前 schema 兼容的 jar，保留数据库、密钥和持久目录；不执行自动数据库降级、clean 或 repair。服务器 OS 未确认时提供通用 Linux 模板，实际证书、路径、账号与 systemd/nginx 校验留 BE-34。

## 验收顺序

1. 真实 jar 初始化的 RED：期望成功退出及真实账号/审计写入，当前入口未实现，应失败。
2. 最小实现后 GREEN；再逐组验证目标确认、已有管理员/重名拒绝、并发串行、审计失败回滚、凭据不泄露、首次改密与正常后台访问。
3. 验证配置错误不连接默认数据库，普通启动不自动初始化；完整离线与 mysql-check 回归。
4. 更新共享记忆、任务生成器/校验器、团队接入与操作文档；清理随机账号/schema，关闭专属实例；BE-32 保持待 A 确认。

## 实际证据

| 验证 | 实际结果 |
| --- | --- |
| 初始化入口 RED | `.tmp/be32-bootstrap-red.log`：1 项失败、0 错误；真实 jar 命令期望退出 0，入口未实现时为 1。原 75 项离线回归通过。 |
| 初始化最小 GREEN | `.tmp/be32-bootstrap-green.log`：1 项真实 MySQL 通过；无 DDL 运行账号创建哈希总管理员、部署审计和 guard 推进。 |
| server 配置 RED | `.tmp/be32-server-red.log`：5 项中 3 项失败、0 错误；关闭身份/幂等或缺持久目录时，旧代码仍能启动。 |
| 配置 GREEN 与扩展行为 | `.tmp/be32-behaviors-green.log`：5 离线＋8 真实 MySQL 全通过；覆盖重复/停用管理员拒绝、重名不提权、双进程竞争、审计失败全回滚、配置错误、迁移/guard 缺失及首次改密 HTTP 链路。 |

日志位于 backend 的忽略目录，仅供本机追溯，不纳入 Git。8 项 MySQL 中的 7 项为首个入口 RED/GREEN 后扩展回归，不将其描述为分别观察过 RED。完整回归和清理结果如下。

- `./mvnw.cmd -Pmysql-check clean verify`，08:49:51 +08 完成：80 离线＋88 MySQL，失败/错误/跳过全部为 0；保留既有 75＋80，新增 5＋8。日志 `.tmp/be32-final-complete-verify.log`，报告归档 `.tmp/be32-full-reports/`。
- 第二次 `./mvnw.cmd clean verify`，08:50:45 +08 完成：80 离线通过，不运行 MySQL；用于验证独立干净构建的产物可重复性。日志 `.tmp/be32-reproducible-verify.log`。当前 target 下的 Failsafe 报告被 clean 清理，真实 88 项证据以上述归档及完整日志为准。
- 两次 jar SHA256 完全相同：`f350a7fd9c230dac5c0bdba242d5a31ba243fadb06a829dbe02ae7570c957cde`。同一 jar 含 Web、迁移、bootstrap 入口；应用 classes 无真实 env、密钥目录或测试夹具。
- 本轮专属 MySQL8.4.8 仅监听回环随机非3306端口；写前核对进程/独立数据目录与实例，结束后自定义 schema/随机账号为 0/0，复核实例身份后关闭专属进程。临时 RSA 目录已删除。已有开发/测试库均未操作。
- 静态生成器/追踪校验通过；35 任务、68 操作、原 API/DDL/迁移和依赖不变。AT-36 新增局部证据，完整服务器验收保持开放。

## A 的本机验证记录（2026-10-19）

用户发现正常 BackendApplication 原先连接 dev，而 BootstrapTestAdmin 指向 test；正常应用改连同一测试库后，截图确认首次登录201、SUPER_ADMIN、PASSWORD_CHANGE_ONLY。随后使用新密码重新登录，截图确认201、NORMAL；用户最终回复“成功获取了”，并提供账号分页正文：employeeId=NULL、enabled=true、mustChangePassword=false、version=2、page=1、pageSize=20、total=1。

据此记录初始管理员首次登录、改密生效、新密码正常会话和账号列表读取的本机链路通过。最终截图未包含HTTP状态栏，不能单独将其记为已截图核验200；改密204、旧token拒绝、重复初始化及审计SQL本轮也未单独提供人工结果，相关自动化证据仍沿用上表，不扩大用户实际回报。真实密码/token/账号UUID不写入文档。

列表此前422的排查重点为五个查询参数；用户按page=1/pageSize=20、取消未用筛选参数的步骤后回报成功，不据此推断究竟哪个原始参数非法。部署指南补充可选空值、独立运行配置与手动鉴权操作。本轮agent仅更新文档/静态追踪，没有登录或操作用户数据库、重跑Java/MySQL、修改契约或代码。BE-32仍待A整体确认，未合并/推送；真实服务器验收仍由BE-34承担。

## 文件与接入

- `backend/.../bootstrap/BootstrapAdminApplication.java`：唯一显式初始化入口，无 Spring 组件装配。
- `core/config/ServerSettingsConfiguration.java` 与 server YAML：同一应用在 server profile 校验开关及预先准备的绝对可写持久目录。该目录预留 BE-28 接入，不代表上传能力已实现。
- `BootstrapMySqlCheck` 与 `ServerSettingsTest`：真实 jar 命令/数据库/HTTP 和配置负面验证；现有数据库隔离夹具仅增加安全传递自身临时凭据的方法。
- `deploy/`：三份独立 env 示例、三个服务模板、nginx 示例及 IDEA/发布/回退指南。真实 env、密钥和发布包不提交。
- pom 固定 archive outputTimestamp，为重复构建校验提供稳定输入；commit/SHA 在发布旁单独记录。

本轮 API、表结构、V1～V3 和 RSA/HMAC 格式均不改；初始化复用既有机制，不增加新密钥。AT-01/02 增加初始化/打包证据，AT-36 仅记录局部验证。真实服务器、systemd/nginx 执行、HTTPS、备份恢复及尚未实现的业务任务仍开放。
