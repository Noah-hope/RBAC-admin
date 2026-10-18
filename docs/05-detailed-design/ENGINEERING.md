# 工程布局与技术设计取值

2026-10-18/D-044：补充显式mysql-check profile，在已安装MySQL8.4.8的独立临时实例中运行真实迁移/基础约束，41离线＋1引擎检查通过；没有依赖Docker或改变已有数据库服务。两处CHECK引用修复及binlog/版本WARN等限制见[MySQL记录](BE-03-MYSQL-VALIDATION.md)，JPA/并发仍待验证。

2026-10-18/D-043：BE-03依赖/配置/迁移脚本已加入工程，JPA4.0.7/Hibernate7.2.24.Final/Connector9.7.0/Flyway11.14.1为实际BOM解析版本；41项离线测试及打包通过。迁移入口未连接数据库，真实MySQL/JPA仍待验证，详见[BE-03准备记录](BE-03-PREPARATION.md)。

产品选型沿用D-028；原版本/布局/工具为技术设计取值。D-041后BE-02已在backend实际实现公共骨架，固定Boot4.0.8/Java21、Maven3.9.11及发行包校验和，记录实际解析版本和RED/GREEN结果，见[BE-02实施记录](BE-02-IMPLEMENTATION.md)。下列业务/持久化部分仍为后续设计，不能因骨架通过就视为全部组合已运行。

修订1.2/D-038/D-039：用户先实施BE-01～05，由用户本人确认基座交接，无需他人评审，随后再分派业务。`common`是公共基座责任组，Java包沿用`core`；不新增并行同义包、不按四人合并13逻辑模块。基座包括M12公共HTTP装配/M11读事务约定及迁移适配，真实身份、领域实体、审批和导入由第二阶段任务补齐。交接清单见[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)。BE-02公共部分已有实际工程，其余仍按目标布局在后续任务实现。

| 项目 | 设计取值与依据 | 实现检查 |
| --- | --- | --- |
| JDK/后端 | Java21、Spring Boot4.0.8，沿用数据库阶段建议，单应用可执行jar | [Boot4.0系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)支持Java17～26，Java21在范围内；BE-02组合已运行，持久化/认证组合仍待后续 |
| 构建 | Maven Wrapper固定3.9.11，编译release21，运行`verify` | [Maven3.9.11发布说明](https://maven.apache.org/docs/3.9.11/release-notes.html)可确认版本存在；Wrapper校验和与插件已在BE-02固定、实际构建通过，见实施记录 |
| 持久化 | Spring Data JPA4.0.7、Hibernate7.2.24.Final、Connector/J9.7.0，跟随Boot4.0.8 BOM | [BOM依赖表](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html)当日核实；不分别覆盖版本或把普通Javax包混入Jakarta工程 |
| 认证 | Spring Security/OAuth2 Resource Server/Jose，BOM管理；RS256及三类受众/签发方隔离 | 管理员当前资格和服务identity/system绑定仍逐请求读库 |
| 迁移 | Flyway11.14.1及flyway-mysql，BOM管理，迁移与运行身份分开 | [Boot初始化说明](https://docs.spring.io/spring-boot/how-to/data-initialization.html)要求数据库特定支持模块；MySQL DDL/触发器须真实验证 |
| 测试 | Boot测试支持、JUnit Jupiter/Mockito、Testcontainers2.0.5 MySQL模块，BOM管理 | Surefire单元/Failsafe集成，MySQL镜像固定8.4补丁及digest后运行；Docker未就绪时选隔离实例，不能悄然skip或改H2 |
| 文件解析 | Apache POI poi-ooxml5.5.1、Commons CSV1.14.1 | [POI发布](https://poi.apache.org/download.html)、[CSV发布](https://commons.apache.org/csv/download_csv.cgi)核实；BE-28复核安全公告/传递依赖，拒绝宏/公式/超限，不执行公式 |
| 规范摘要 | RFC8785兼容CanonicalPayloadCodec，普通DTO JSON不是规范化算法 | [RFC8785](https://www.rfc-editor.org/rfc/rfc8785)向量＋本项目固定摘要包；BE-21记录选定实现/版本/向量结果再使用 |
| 数据库 | MySQL8.4 LTS/InnoDB，UTC；候选补丁沿用本机可见8.4.8，部署前固定真实版本 | 用户确认已有云服务器，当前先用本机MySQL；实际本机服务版本未核验，云端Docker数据库未部署，候选不是实测版本 |

## 正式工程布局

D-040：用户确认使用IntelliJ IDEA配置JDK21，后端位置确定为`rbac-admin/backend`。IDE向导最终目标应为该目录，避免生成`backend/backend`；沿用根Git仓库，不勾选创建新Git仓库。Project SDK、Maven导入/运行JDK及应用运行JRE均设为21；pom编译release21和Wrapper仍须在BE-02落实并实测。IDE配置不代表外部命令行已有Java环境。参考[IDE SDK配置](https://www.jetbrains.com/help/idea/sdk.html)、[Maven项目](https://www.jetbrains.com/help/idea/maven-support.html)。

以下为目标布局；当前已实现入口、core/config、web公共适配及相关测试，其余业务包、迁移、deploy/tools留对应任务创建。配置扩展名统一.yaml：

```text
backend/
  pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/
  src/main/java/com/adgn/backend/
    core/ identity/ people/ scope/ catalog/ role/ grant/
    change/ approval/ execution/ lifecycle/ query/ web/
  src/main/resources/
    application.yaml, application-dev.yaml, application-test.yaml, application-server.yaml
    db/migration/                 # 审核后的版本化迁移，无密码/真实人员
  src/test/java/...               # 单元、HTTP和MySQL集成测试
  src/test/resources/fixtures/    # 虚构样例、非法/冲突/并发输入
deploy/
  README.md, systemd/, proxy/     # 服务器确定后建立，环境秘密放服务器外置目录
tools/demo/
  README.md, requests/            # 演示次序和模板，不保存可用JWT或固定密码
```

单工程按功能模块维护，M12控制器仍按域分文件，不强迫四人改一个ApplicationService。当前根目录工程限制在实施开始时按本方案更新，设计阶段不提前建backend/deploy/tools或`.github`。前端已有原型位置不动。

## 密码、JWT和输入细节

密码12～128 Unicode字符且允许空格。设计采用Spring DelegatingPasswordEncoder注册`pbkdf2-v1`：PBKDF2-HMAC-SHA256、随机16字节盐、256位派生、600000迭代；前缀选择固定参数版本，存储编码由框架完成。该取值避免用短字节截断来适配长Unicode密码，部署前测成本/限流；不自动降低成本掩盖性能。未知前缀拒绝，不接受noop。校验和重置哈希在锁外计算，再锁内检查原credentialVersion/摘要未变。

依据：[Spring密码编码机制](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)、[OWASP密码存储](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)。这是技术设计参数，未在本机进行耗时/库兼容测试；BE-06验证最长多字节密码、空格保留、版本升级和旧密码不同。

服务令牌受控部署签发，管理员登录不签BusinessJwt/OaJwt。服务token≤5分钟、管理员15分钟/改密受限5分钟，时钟偏差≤60秒；各issuer/aud/keyring按契约配置，任意传入kid不触发不受信远程地址读取。私钥/密码/HMAC密钥和真实会话不进入Git、Issue、PR或演示导出。

Boot/Jackson默认行为不可代替OpenAPI严格约束；DTO校验显式拒绝未知字段/非法联合/越界版本，字符串长度按code point。Web层将异常映射成既有Problem，不新增“万能200失败”或管理员批准/强制执行接口。

## 运行配置责任

D-040：用户报告开发空库`rbac_admin_dev`已创建，Navicat截图显示服务端8.4.8；agent未连接/执行SQL。后端使用项目专用数据库用户，团队共享密码经私下渠道分发，各自本机注入；共享配置只留占位符。具体用户名/Host和授权待配置验证，迁移与运行身份分离约定保留，云端凭据部署时独立配置。

BE-01先核验用户基座开发所需工具和隔离MySQL，记录已有云服务器和后续Docker数据库部署待核验条件；OS/架构、网络/证书和部署存储在后续BE-32/34落实，不要求买好服务器才完成基座。当前建议Linux+systemd+jar作为演示目标，属于部署设计取值，未确认实际宿主。不引入Kubernetes/消息队列/Redis。单实例内存限流承担初始基础限制，当前资格和权限不能用该缓存；未来多实例需重新设计共享限流。

BE-02固定依赖树/构建插件，BE-03真实验证迁移/JPA，BE-32产物和启动说明，BE-34目标机实测。确认环境前不选购服务器或承诺吞吐/内存配置。现在仅有设计文件，以上命令/目录都未实际存在或执行。

D-039环境补充：JDK21已由用户在IDE配置；Get-Command实测java/javac不在PATH，JAVA_HOME未配置。可用IDE构建JDK或会话显式指定，不要求永久修改系统环境变量。本机MySQL的真实版本及独立测试schema应在迁移前确认，本次未连接。详见[ENVIRONMENT.md](ENVIRONMENT.md)。
