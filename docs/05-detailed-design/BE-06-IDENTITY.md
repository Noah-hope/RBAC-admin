# BE-06 身份、会话与团队接入样例

2026-10-18：用户明确本人负责A，并选择本轮完整实现BE-06；BE-07留下一轮。本文先记录实施设计，验证结果随实际执行补充。业务前置BE-04及基座交接已满足；用户认领A不代表B/C/D环境已核验。

## 验收与边界

- 沿用UC-09、API1.2.0的login/currentSession/logout/changeOwnPassword，覆盖AT-05/06/07所属部分。账号创建/重置归BE-07、完整范围选择归BE-08；不新增服务令牌签发或初始化业务接口。
- 正常登录创建15分钟会话，临时密码仅5分钟改密/退出。普通员工、无有效任命、账号/员工停用均拒绝；不以业务角色代替后台资格。
- 密码PBKDF2-HMAC-SHA256，600000迭代、随机16字节盐、256位派生，版本前缀pbkdf2-v1。长度按Unicode码点，保留空格，不持久化明文/请求/JWT，不记录哈希或凭据。
- 三类JWT固定RS256、配置的issuer和唯一audience，强制有效期/iat/exp/jti及管理员sid/凭据版本/周期；服务最长5分钟，管理员15分钟/受限5分钟，最多60秒偏差。密钥按本地JWK Set的kid选择，不读取token中的远程地址。管理员签发私钥与三类信任公钥分别配置，支持旧公钥验证。
- 登录哈希校验、改密哈希生成在guard锁外；guard锁内重新读账号摘要/版本、当前资格。创建会话、改密/撤销及成功审计同事务，失败全部回滚。查询使用新RR快照；业务调用者在自己的读/写边界再次复核，不能依赖HTTP入口较早的检查。
- 重复/过期/非法token退出统一204；非法token不撤销任何会话。本人改密校验旧密码、新旧不同及确认一致，推进credential_version/session_epoch并撤销全部旧会话，需重新登录。
- 登录按客户端真实连接IP及账号作有界单实例限流；不信任任意X-Forwarded-For。超限429及正数Retry-After。代理/多实例限流留部署细化，不使用Redis或权限缓存。

## 代码组织

| 路径（基础包com.adgn.backend） | 本轮责任 | 后续成员使用方式 |
| --- | --- | --- |
| web/identity/controller、dto | HTTP路由、输入验证及响应 | B/C/D在web自己的领域子目录放Controller/DTO |
| identity/service | 登录、会话与改密应用流程；当前主体读取 | 所属业务Service复用CurrentIdentityReader，在事务内再确认 |
| identity/repository | 本领域账号/会话SQL与安全投影 | 其他领域不复制账号/会话写逻辑 |
| identity/security、config | 本地密钥环、JWT、密码与认证装配 | 后续三个入口沿用同样认证规则；不扩建空业务接口 |
| src/test/java/com/adgn/identityfixture | 离线行为/HTTP及独立MySQL集成 | 照真实测试和故障注入模式扩展，夹具不进正式jar |

不创建无人维护的空Controller/Service或占位实现；Git中的实际功能文件建立目录。现有core、query、web公共模块布局保留。

## 装配与失败方式

`RBAC_IDENTITY_ENABLED=true`启用真实身份模块；关闭时不装配四个业务接口，三个受保护入口返回503，不能通过关闭开关绕过认证执行其他业务。健康及既有公共骨架仍可验证。启用后必须提供本地管理员JWK Set（当前kid含RSA私钥）以及业务/OA公钥Set和各issuer，缺少/非法配置启动失败；私钥不进入Git、数据库、日志或共享文档。测试临时生成RSA密钥，不读取用户IDE秘密。

## TDD执行顺序

1. 真实HTTP登录及MySQL会话提交的失败测试，再实现最小登录；保留实际RED/GREEN。
2. 当前资格、三类JWT隔离/非法token/受限模式、退出、密码边界与限流逐组先失败后实现。
3. 两个真实连接及故障注入验证会话/审计回滚、凭据并发变化拒绝、即时失效与恢复不复活；旧回归一并运行。
4. 完整clean verify及显式mysql-check；专属MySQL8.4使用backend/.tmp的独立datadir、随机非3306端口，写入前核验身份，结束清理schema/随机用户并关闭专属进程。

依据：[Spring JWT验证](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)、[Spring密码编码](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)。具体依赖跟随既有Boot BOM，实际版本及限制在验证后记录。

## 验证记录

最终命令为`./mvnw.cmd -Pmysql-check clean verify`，2026-10-18 20:37（Asia/Shanghai）BUILD SUCCESS：**74离线＋59真实MySQL检查，失败/错误/跳过均0**。本轮命令行临时指定JDK21并将TEMP/TMP指向backend/.tmp，规避本机JDK临时socket路径问题，未修改永久环境变量。原64离线＋42引擎回归保留，新增8项安全单元、2项安全HTTP检查及17项身份HTTP/MySQL场景。实际解析Spring Security7.0.7、Nimbus JOSE JWT10.4，均由Boot4.0.8 BOM管理。

| 证据 | 实际结果与边界 |
| --- | --- |
| 初次登录RED | 实现前1项真实HTTP测试，预期201、实际401；1失败0错误。`.tmp/be06-red-login.log`保留本机输出，不提交原始日志。 |
| JWT形状RED | 8项安全测试中，小数credentialVersion=1.5未被拒绝导致1失败0错误；修正为仅接受整数后全部通过。输出为`.tmp/be06-red-token.log`。 |
| 编码路由RED | 容器路由已解码的/api/v1/%61dmin路径原先落到404，预期受保护的503；1失败0错误。改按Servlet解码路径分类后离线及真实HTTP都拒绝无认证访问。 |
| 迁移隔离RED | 在既有不执行SQL的迁移配置测试中设置身份开关true，原先因加载运行期身份密钥而启动失败；明确不抛异常断言1失败0错误。身份配置排除migrate profile后通过，独立迁移无需运行期密钥。 |
| 资格与会话 | 有效任命（空系统集合也可登录）、普通员工拒绝、任命撤销/员工停用即时401、账号周期推进后启用不复活旧token；退出只撤当前会话，非法/重复退出204。 |
| 改密 | 原密码校验、确认一致、新旧不同、保留空格、128个多字节Unicode码点；成功递增凭据/周期、撤全部旧会话、临时模式改密后须重登。 |
| 事务与并发 | 审计触发器注入失败时登录不留会话、改密不改哈希/版本/撤销；第二真实连接等待guard时变更凭据，登录锁后复核拒绝创建会话。 |
| 三类JWT | RS256签名、错误issuer/audience/算法/时间/缺声明、小数版本、历史公钥轮换、跨类型拒绝；服务停用及systemId/oaInstanceId当前绑定不符均401。服务身份测试只经过正常404处理，不创建业务接口。 |
| HTTP安全 | Location、空204、no-store、无Cookie、当前请求ID、WWW-Authenticate、429及正数Retry-After，非法字段422；数据库审计不含密码/哈希/token。 |
| 运行装配 | 关闭身份的三个受保护路径均503；缺失当前私钥或把服务私钥作为信任配置拒绝装配。健康与原公共HTTP行为回归通过。 |
| 密钥与产物 | 测试随机生成密钥；本机生成指南的PowerShell片段在专属临时目录执行成功。jar包含正式身份Controller，不含测试夹具；SHA-256为`5dc20c69d432371a4713935534a284d8389317138486888e98afa229e9cc5c95`。 |
| 实例与清理 | 专属MySQL8.4.8、非3306端口、独立backend/.tmp目录，写入前核验datadir/version/binlog=0。结束查得自建schema和用户均0、核验专属PID后关闭进程；5个本轮临时密钥目录已清理，现有开发/测试库未操作。 |

完整输出在本机`.tmp/be06-final-complete-verify.log`，JUnit在target/surefire-reports和failsafe-reports。保留Flyway对8.4的版本验证WARN与Hibernate的既有incubating提示；没有静默跳过测试或引入H2替代。

BE-06为IN_PROGRESS：代码与自测完成，**待A（用户本人）确认及后续合入**；不会因为测试通过自动标记OWNER_ACCEPTED。AT-05/06/07保持PARTIALLY_VERIFIED：BE-07账号创建/重置、BE-08完整范围上下文、正式业务资源授权与OA回调端到端不在本轮。初始总管理员仍由BE-32的受控初始化处理，本轮不向用户库填充夹具。

## 本机生成指南兼容性纠错（2026-10-18）

用户在Windows PowerShell执行指南时，KeySize赋值报只读；实际在Windows PowerShell5.1.26100.9711复现，RSA.Create返回RSACryptoServiceProvider且PowerShell暴露的属性只有getter，原先PowerShell7.6.5的验证不足以覆盖该环境。先执行指南原脚本的隔离回归得到失败，再改用RSACng(2048)构造、脚本作用域ErrorActionPreference=Stop、全部文件写前预检，并直接输出五个环境变量的非秘密路径。

修正后对文档实际代码块执行回归，Windows PowerShell5.1与PowerShell7均PASS：生成六个JWK文件、三个2048位密钥对真实RS256签名验签、公钥不含私有参数、重复执行不覆盖、仅部分旧文件存在时在写入前终止。测试把LOCALAPPDATA临时指向backend/.tmp的专属随机目录，finally清理目录与进程变量，没有读取或改写用户真实密钥。旧版脚本遇错可能继续生成文件，指南明确使用新恢复目录并按新输出配置，不建议直接删除或覆盖旧文件。

本次只修正指南中的PowerShell脚本，不改Java、JWT协议、API或数据库；没有重新运行Java/MySQL测试，既有74＋59结果为前次实施证据，BE-06仍待负责人确认。
