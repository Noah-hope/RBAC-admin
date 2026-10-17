# 后端工程

Java 21、Spring Boot 4.0.8、Maven Wrapper 3.9.11（Wrapper脚本3.3.4）。基础包`com.adgn.backend`；配置使用YAML。当前是BE-02公共骨架，正式身份、授权、审批和数据库能力仍按任务实现。状态与任务统一见[共享记忆](../docs/project/MEMORY.md)和[任务清单](../docs/05-detailed-design/TASKS.md)，本页只说明使用方式。

## 在IntelliJ IDEA运行

打开本目录或`pom.xml`，沿用根Git仓库。Project SDK、Maven导入/运行JDK和应用运行JRE均选JDK21；Maven主路径选择Wrapper。重新加载Maven后运行`BackendApplication`。

默认dev配置监听`127.0.0.1:8080`。访问`http://localhost:8080/actuator/health`，应返回`status: UP`；`groups`是正常的探针分组。此结果仅表示当前应用健康，尚未验证数据库或业务权限。

## 命令行构建与启动

先使当前会话可用JDK21；无需统一个人安装位置，也无需永久修改系统变量。Windows PowerShell中，必要时临时设置`JAVA_HOME`为自己的JDK目录，然后从backend执行：

```powershell
.\mvnw.cmd --version
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Linux/macOS使用`./mvnw`。打包后可执行`java -jar target/backend-0.0.1-SNAPSHOT.jar`。Wrapper首次需要下载Maven及依赖；发行包SHA256已固定。Spring Boot父BOM管理依赖和插件版本，不单独升级其中一个组件。

## 配置边界

| profile | 配置及用途 |
| --- | --- |
| dev（默认） | 本机调试，运行实例标识local，默认8080端口 |
| test | 自动化测试，实例标识test，随机端口；不连接开发库 |
| server | 必须外部提供RBAC_INSTANCE_ID（1～64位字母、数字、下划线或连字符）；缺失/非法则启动失败 |

运行实例标识只用于运维日志，不能作为员工、后台账号或授权依据。server监听仍为本机，正式部署网络和认证由后续任务落实。

例如先在本机终端设置`RBAC_INSTANCE_ID=server-01`对应环境变量，再用`--spring.profiles.active=server`启动；IDEA中使用运行配置的“环境变量”。Spring Boot不会因为存在`.env`文件就自动读取它。

密码、JWT及密钥只通过成员本机环境变量或仓库外配置注入；团队共享密码私下分发，不进入Git、文档、Issue、PR、导出或日志。BE-03再接入独立迁移用户和专用运行用户，当前不预填数据库密码，也不执行任何DDL。

## 公共能力怎样接入

- HTTP装配放`web`，与框架无关的公共配置放`core`；其他功能包按[模块说明](../docs/05-detailed-design/MODULES.md)逐任务添加，不建立空业务接口或第二套common包。
- 请求ID接收`X-Request-Id`，只允许`[A-Za-z0-9_-]{1,64}`且只能出现一次；省略时生成UUID，非法时以安全新ID返回422。ID进入响应头和日志MDC，不能用于认证或幂等。全部响应no-store。
- 请求JSON拒绝未知字段、重复键、尾随JSON及隐式标量转换；语法错误400，字段/类型或必填参数不符422。DTO仍需显式标注各自的校验约束，跨字段/版本/授权规则由所属模块实现。显示文本修剪与Unicode code point长度按具体DTO处理，密码保持原值，不做全局字符串裁剪。
- 错误为API1.2.0的`application/problem+json`：type/title/status/code/detail/requestId/errors；字段路径采用JSON Pointer，单次最多100项。未知路由404，未知服务异常按现有500契约返回DATA_INTEGRITY_ERROR；不披露原始异常、请求值、SQL或堆栈。领域专用冲突码/认证异常由对应业务任务显式接入，不能只按HTTP状态推断所有业务原因。
- 请求日志只记录requestId、运行实例、方法、状态和耗时；不记录URL查询、原始body或异常消息。异常仅记录类别。MDC在请求结束恢复，避免线程复用串号。

## 测试与验收范围

`clean verify`运行所有当前测试并打包：生成的上下文测试1项、配置测试3项、Spring MVC/Servlet请求链测试26项，共30项；失败0、错误0、跳过0。测试用控制器仅位于`src/test`，不会加入正式jar。Surefire负责`*Test`；Failsafe的`*IT`生命周期已配置，当前没有IT类，因此不能宣称MySQL集成测试已通过。

测试覆盖安全/自动请求ID、头重复、严格JSON、嵌套字段指针、400/404/405/409/415/422/500、缺配置失败、日志脱敏和健康暴露边界。Mockito采用显式测试Java agent，测试JVM关闭CDS，避免动态自附加警告；不是跳过测试。

完整RED/GREEN、打包HTTP验证、依赖/插件实际版本与限制见[BE-02实施记录](../docs/05-detailed-design/BE-02-IMPLEMENTATION.md)。该记录不等于完整AT-01/AT-37或公共基座已经验收。
