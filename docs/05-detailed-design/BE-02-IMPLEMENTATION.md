# BE-02实施与验证记录

2026-10-17（Asia/Shanghai）；D-041；本轮用户明确授权执行BE-02公共骨架。沿用需求V1.3、API1.2.0、数据库草案1.1，不修改权威契约、需求或DDL。分支codex/foundation-environment，起点ee743dd；不合并main、不推送、不创建远程Issue/PR。用户本人确认安排仍有效。

## 交付与边界

正式工程使用用户确认的com.adgn/backend及基础包com.adgn.backend，配置YAML；旧设计中的com.noah包名前缀同步替换。M00/core承载配置，M12/web承载严格JSON、请求ID及Problem，13逻辑模块和原业务依赖不变；不为未实现模块创建空业务接口。

核心文件：RuntimeProperties、JsonConfiguration、RequestIdFilter、ApiProblem/ProblemResponses、ApiExceptionHandler、ProblemErrorController。入口、测试命令和使用约定见[后端README](../../backend/README.md)。仅健康/框架错误入口可运行，67个正式业务入口、JWT/当前资格、会话、数据库迁移/JPA/事务/幂等、真实OA及服务器部署均未实现。本轮没有连接MySQL、创建账号/测试库或执行SQL。

新配置rbac.runtime.instance-id是用于日志区分进程的技术取值，不是账号/员工/权限身份。默认dev/local和test/test，server必须外部提供合法RBAC_INSTANCE_ID；完整部署的数据库/JWT必需配置在对应任务添加，不把当前配置验收等同完整部署。

## TDD证据

| 顺序 | 实际运行及观察 | 结论 |
| --- | --- | --- |
| 测试准备 | 首次有AssertJ Optional断言调用错误，修正后；JDK HttpClient又遇本机回环连接错误 | 编译/环境错误，不作为目标行为RED证据 |
| 第一组RED | `-Dtest=HttpBoundaryTest,RuntimeConfigurationTest test`；25项，23失败、1错误、1通过 | 未生成/传递请求ID、默认错误格式、默认400、未严格拒绝JSON、无配置绑定；1错误为夹具抛出的异常未收敛，均在实现前观察 |
| 第一组GREEN | 实现公共边界后，同命令25项全部通过 | 覆盖22项Servlet/MVC及3项配置；测试夹具在测试目录 |
| 第二组RED | `-Dtest=HttpBoundaryTest#reportsMissingRequiredHeaderAs422+reportsMissingRequiredQueryAs422+validatesQueryBoundsAs422+reportsQuerySyntaxErrorsAs400 test`；4项，3失败、1通过 | 必填头、必填查询和方法约束仍是框架默认400，契约要求422 |
| 第二组GREEN | 补对应异常适配后，两测试类共29项全部通过 | 保留参数语法错误400，缺字段/约束422 |
| 全量 | `./mvnw.cmd --batch-mode --no-transfer-progress clean verify`；30项全部通过、0失败/错误/跳过，生成可执行jar | 包括原生成上下文测试；不将它冒充新增业务TDD |

一次clean验证因日志文件被重定向到正在清理的target而失败；将日志放在被忽略的backend根目录后完整重跑通过。预期的缺配置、非法方法测试会产生对应WARN；Mockito动态agent/CDS诊断已通过显式测试agent及测试JVM配置解决。没有屏蔽业务失败或skipTests。

## 实际构建基线

JDK/javac为Amazon Corretto21.0.12.1；Wrapper脚本3.3.4、Maven3.9.11；会话临时指定JDK，无永久JAVA_HOME/PATH修改。Maven发行包SHA512与Central提供值核对后固定SHA256：0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb。缓存已有解压目录时Wrapper可能复用；校验不代替团队对构建来源的管理。

dependency:tree与help:effective-pom实际执行通过，完整输出在忽略的target目录；以下为实际解析值，不是仅按版本表推断：Boot4.0.8、Spring7.0.9、Jackson3.1.5、Tomcat11.0.24；compiler3.14.1、Surefire/Failsafe3.5.6、dependency-plugin3.9.0、jar3.4.2、clean3.5.0、resources3.3.1。JPA/MySQL/Flyway/Testcontainers尚未加入，不把设计中的版本列记为本轮解析/验证。

最终jar SHA256：30d8205bfa90b0fc0d8d06c14722cd45abdaf1c8f452efe913ba2e92c8d504b4。clean verify之后保留标准启动成功日志，再次完整verify仍30项通过，并对最终jar重做健康/404/测试路径隔离/非法头422的真实HTTP检查。此值只对应本次产物；本轮未配置跨机器逐字节可重复打包，不能把可重复构建步骤写成二进制必然相同。

## 打包后的真实进程验证

在独立本机随机端口启动jar并实际发送HTTP：健康UP且追踪头一致；未知业务路径404/NOT_FOUND且媒体类型application/problem+json、requestId一致；测试探针路径404；非法X-Request-Id返回422/VALIDATION_ERROR。临时进程均在检查结束后停止，不占用户原8080进程。

另用server profile、非Web进程实际验证：不提供RBAC_INSTANCE_ID启动非零退出，提供合法值正常启动退出；仅在命令会话临时设置并恢复变量。

当前执行环境的JDK默认Unix domain socket临时路径导致Invalid argument/connect；因此主要自动化HTTP覆盖使用MockMvc真实Spring Servlet链，不声称这26项是TCP测试。首次jar启动也遇同类问题；本次进程临时使用项目内短socket目录（jdk.net.unixdomain.tmpdir）后真实HTTP验证通过，未写入共享启动配置或修改系统。另一次HTTP检查是PowerShell把无charset的响应按byte[]返回而解析错误，显式UTF-8解码后重试通过。仍需团队在各自IDE/部署环境核对原生启动；这是环境限制记录，不是项目内绕过网络/认证。

## 追踪与剩余事项

| 依据 | 本轮覆盖 | 后续责任 |
| --- | --- | --- |
| CONVENTIONS及OpenAPI Problem/FieldError/X-Request-Id | 公共形状、头、安全值、语法/语义区分、无敏感输出 | 每个业务DTO、联合/长度/版本/权限边界由对应任务补齐 |
| BE-02 / M00、M12 | 固定Wrapper、配置失败、健康、公共错误/日志及运行说明 | 当前实现待用户本人确认；无独立评审要求，不等于整个基座验收 |
| AT-01 | 本机构建、配置及jar健康的局部证据 | 最小部署/初始化在BE-32/34补齐 |
| AT-37 | 公共HTTP样例与Problem局部证据 | 67入站、401/403及各业务响应在对应任务和BE-33验收 |
| BE-01 / BE-03 | 用户报告MySQL8.4.8和开发空库 | 专用运行账号、独立迁移身份、隔离测试库与实际迁移/JPA验证仍待落实 |

机器状态：BE-01仍IN_PROGRESS；BE-02为IMPLEMENTED_PENDING_OWNER_ACCEPTANCE；BE-03～35仍PLANNED。AT-01/37为PARTIALLY_VERIFIED，其余39项仍DESIGNED_NOT_RUN。基座IN_PROGRESS但总门槛NOT_EVALUATED，30项业务仍未分派。后续先完成BE-01数据库准备并由用户确认BE-02，再按依赖开展BE-03；不能静默跳过验收或提前宣称全后端完成。
