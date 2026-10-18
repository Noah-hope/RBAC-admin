# BE-01环境与基座确认记录

更新至2026-10-18；实施计划修订1.2，D-039/D-040。BE-01处于环境准备中，不表示公共基座已经构建或验收。本记录区分用户确认与工具实测；不保存个人安装路径、主机地址、数据库口令或密钥。

| 事项 | 用户已确认的情况 | 本次实际核验及剩余事项 |
| --- | --- | --- |
| 云服务器 | 已有一台，可在后续通过Docker部署数据库服务 | 未连接服务器，Docker、数据库容器、网络、持久卷、版本和备份均未核验；不能记为已部署 |
| 当前数据库 | 先用本机MySQL服务；此前截图显示服务端8.4.8、本机3306；2026-10-18用户报告准备完成，新截图列出rbac_admin_dev和rbac_admin_test | 属于截图及用户报告，agent未连接或执行SQL；当前截图未展示SHOW TABLES结果，空库状态沿用用户完成反馈。迁移、SQL mode、后端JDBC连接及真实测试仍待核验 |
| 后端数据库账号 | 后端服务使用项目专用数据库用户，密码团队共享；2026-10-18截图的CURRENT_USER()为rbac_app@localhost | 已证实Navicat连接身份；截图另有local_mysql_rbactest连接名称，不能据此确定实际用户名或迁移身份。SHOW GRANTS结果未展开，具体授权待核验；root可用于建库/授权，服务运行使用专用账号。密码经私下渠道传递，各成员本机注入，禁止进入Git、文档、Issue、PR或日志；独立迁移身份保留 |
| IDE与工程位置 | 用户已通过IntelliJ IDEA生成rbac-admin/backend；包名com.adgn.backend，配置YAML | D-041已实现并自测BE-02公共适配，完整构建30测试通过；用户此前截图已确认IDE启动和健康UP，本次变更后需重新加载/启动。实施自测不代替用户本人确认 |
| JDK | 使用JDK 21，本地已下载，IDE可以引用 | java与javac实测21.0.12.1，Amazon Corretto；命令会话临时设置JAVA_HOME后实际构建，无永久系统环境变量修改 |
| 命令行 | 尚未配置Java环境变量，java/javac查询不到 | 本次Get-Command确认java和javac均未在PATH，JAVA_HOME未配置；未修改用户或系统环境变量 |
| 基座确认 | 基座由用户本人确认，不需要别人评审 | BE-01～05采用OWNER_ACCEPTANCE，不要求Reviewer；TDD、自检、真实约束/事务验证及交接证据仍保留 |
| 业务阶段 | 基座验收后再分派 | 30项业务/交付任务仍未分派；基座不要求他人评审的安排不自动取消业务阶段原评审规则 |

## 接下来怎样准备

D-043更新（2026-10-18）：用户已在IDE配置运行账号环境变量并授权添加依赖、补配置和整理迁移脚本。BE-03已准备这些内容，40项离线测试/打包通过；迁移专用变量RBAC_MIGRATION_URL/USER/PASSWORD需在本机独立运行配置中注入，当前没有读取IDE密码、连接MySQL或执行DDL。先核验迁移账号和隔离目标再开展真实验证，见[BE-03准备记录](BE-03-PREPARATION.md)。

D-041后续：BE-02公共骨架已实际按TDD实现并完成30项测试及jar随机端口HTTP验证，D-042用户已本人确认通过；2026-10-18用户报告数据库账号和双库准备完成，截图已核实运行账号身份及双库可见。BE-01/03仍需核验具体授权、独立迁移身份、JDBC连接和迁移/测试，不将Navicat准备记成后端数据库适配通过。运行说明见[后端README](../../backend/README.md)，完整命令/版本/环境限制见[BE-02实施记录](BE-02-IMPLEMENTATION.md)。

1. 在IntelliJ IDEA中确认Project SDK、Maven导入/运行JDK和应用运行JRE均为21，记录实际构建使用的JDK。可通过IDE验证，或在受控命令会话显式指定JDK；永久修改系统JAVA_HOME/PATH不是硬性前置，不要求团队统一个人安装目录。
2. 已生成Maven Wrapper，当前固定Maven3.9.11；在backend目录、JDK21可用的会话运行`./mvnw.cmd --version`及`./mvnw.cmd --batch-mode --no-transfer-progress verify`。本次实际构建通过，后续BE-02继续补齐公共行为及测试。
3. 保留用户已准备的rbac_admin_dev和rbac_admin_test，后者用于隔离测试，不清理开发库。BE-03核验rbac_app的目标库授权及独立迁移身份，配置本机凭据注入后验证JDBC连接，再按TDD落实Flyway/JPA。团队共享密码经私下渠道传递，各自本机注入。真实建表通过迁移，避免修改其他数据库；截图/完成反馈不等于迁移、约束或事务测试通过。
4. 基座完成后由用户确认F-01～06的真实结果并交付共同主线，不要求另一成员审批。当前环境说明和用户对组织方式的确认，不等于公共基座验收通过。
5. 云端Docker数据库作为后续部署事项，由BE-32/34落实版本、持久化、网络及恢复，不阻断本机基座开发；不在本次购买、安装或连接。

状态与任务见[TASKS.md](TASKS.md)；基座结束条件见[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)。

## Wrapper同步排障实测（2026-10-17）

用户生成工程默认Wrapper3.3.4、Maven发行版3.10.0。IDE截图先出现JVM目标5，后出现Guice/MissingImplementation（ArtifactHandlerManager）；与[Maven开发者记录的IDE导入问题](https://www.mail-archive.com/dev%40maven.apache.org/msg137878.html)高度吻合，但未在本机复现IDE内部过程，不能断言所有IDE版本均不支持3.10。按既有设计将distributionUrl固定回3.9.11，Wrapper脚本版本保留3.3.4；不是取消Wrapper或更改JDK。

实际结果：Wrapper --version输出Maven3.9.11/Java21.0.12.1；verify编译release21，生成的BackendApplicationTests上下文测试1项通过（失败0/错误0/跳过0），Spring Boot重新打包成功。保留Mockito动态附加agent、Byte Buddy动态加载及CDS警告，未用跳过测试/屏蔽警告处理。本次属于生成工程与构建配置排障，不宣称业务TDD或HTTP/数据库验收通过；未连接MySQL或执行SQL。

用户后续截图显示Sync Finished且无同步错误，但Spring类仍标红；IDE项目模型/索引及实际运行仍需重新加载后确认，命令行成功不能代替IDE已恢复。沙箱首次运行因用户缓存目录写权限失败，获准在沙箱外运行后正常下载官方发行版并构建；没有修改全局Maven配置。
