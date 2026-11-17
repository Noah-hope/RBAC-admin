---
name: rbac-code-conventions
description: 在 RBAC 权限管理系统中编写或修改后端生产代码、测试和数据库迁移时使用：约束分层、DTO/VO、CloneFactory、Lombok、中文注释、AOP 日志、错误翻译、DDD 仓储边界和测试写法。改编自老师的 projectmgt-code-conventions。不用于纯文档修改、UML 绘图或 docs/02-ui-design 下的一次性原型。
metadata:
  short-description: RBAC 权限管理系统后端代码规范
---

# RBAC 权限管理系统代码规范

改编自老师提供的 `projectmgt-code-conventions`（课程项目管理系统）。老师说明规范不必全部采纳；本文件保留适用于权限系统的规则，去掉课程项目管理系统专用的内容，差异见文末「与老师原规范的差异」。

## 适用范围

| 模块 | 设计方式 | 根包 |
| --- | --- | --- |
| `rbac1-structured` | 面向功能（结构化） | `com.rbac.rbac1` |
| `rbac1-ddd` | 面向对象（DDD） | `com.rbac.rbac1ddd` |

两种实现使用同一份接口契约 `docs/03-api/RBAC1-api.yaml`，路径、字段、错误码和中文提示必须一致；只有后端内部设计不同。

### 分层对照

| 职责 | 结构化包 | DDD 包 |
| --- | --- | --- |
| HTTP 适配、参数校验 | `controller`、`dto` | `interfaces.rest`、`interfaces.rest.dto`、`interfaces.rest.vo` |
| 用例编排、事务、权限守卫 | `service` | `application.*` |
| 业务规则 | `service` | `domain.*`（聚合根、值对象、领域服务、仓储接口） |
| 持久化、外部系统、日志切面 | `entity`、`mapper`、`config` | `infrastructure.*` |

组内已确定保持两个独立模块（不采用老师式的单工程）。两个后端都部署、共用同一套前端和同一个数据库，各自完整实现全部 44 个接口。

## 必须遵守

1. **测试先行**：新增行为先写因行为缺失而失败的测试，确认 RED，再写最小实现，最后完整回归。RED 不能靠语法错误或测试写错制造。
2. **API 层完成输入校验**：非空、长度、范围、枚举、ID 上限、列表去重、跨字段关系放在 DTO/Controller，用 Bean Validation、`@AssertTrue` 或自定义校验器。应用层和领域层不重复做请求格式校验。
3. **业务规则的位置**：
   - 结构化：权限、状态、跨对象一致性放在 Service。
   - DDD：只依赖聚合自身数据的规则放在聚合根方法里（信息专家）；需要整张图或多个聚合的规则放领域服务；应用服务只做编排。
4. **DTO 与 VO 分离**：请求放 `dto` 包并以 `DTO` 结尾；响应放 `vo` 包并以 `VO` 结尾。Controller 负责 DTO → Command、Result → VO 映射，不把实体、持久化对象或领域对象直接返回给前端。
5. **优先 CloneFactory**：同名同类型字段优先 `CloneFactory.copy(...)`，PATCH 更新优先 `copyNotNull(...)`。派生值、多来源字段、类型转换、集合元素类型变化才显式赋值。细节见下文。
6. **Lombok 去样板代码**：优先 `@Getter`、`@Setter`、`@RequiredArgsConstructor`、`@NoArgsConstructor`，不手写无意义的 getter、setter 和构造器。例外：聚合根暴露集合时手写 getter，返回只读视图，防止外部绕过业务方法修改。
7. **禁止 `var`**：Java 局部变量写明确类型，包括 lambda 外的循环变量。
8. **中文注释**：生产代码和测试代码中的类、接口、record、枚举，以及公开方法和关键私有方法写中文 Javadoc，说明职责和非显然规则，不写「获取字段」之类无信息内容。
9. **列表分页**：会增长的列表统一 `page`（从 1 开始）、`pageSize`（1～100）、`total`；部门树、权限目录这类完整结构可以一次返回。
10. **数据库兜底约束**：唯一、检查、外键约束在数据库层实现。迁移文件只有一份，放在 `rbac1-structured/src/main/resources/db/migration`，只新增版本，不修改已执行的迁移；DDD 模块在构建时引用同一目录执行 Flyway，不另写迁移，JPA 只做 `ddl-auto: validate`。
11. **错误就地翻译**：唯一索引、检查约束等数据库错误在触发保存的仓储适配器或 Service 处转换为业务错误码，不在全局异常处理器里按约束名 if/else。需要在当前事务内捕获唯一约束时，对那一次保存使用 `saveAndFlush`。
12. **不要手工 flush**：普通保存用 `save`/`saveAll`，事务结束时提交；除第 11 条的情况外不调用 `flush()`。
13. **事务边界在 Service / 应用服务**：跨仓储写操作在同一业务事务内完成；操作成功记录与业务数据同一事务提交，失败记录用独立事务。
14. **身份与凭据**：本系统是统一身份认证的提供方。密码只保存 Argon2 摘要（与 `rbac1-structured` 相同参数，两个后端共用数据库时才能互相验证），会话只保存令牌的 SHA-256 摘要；JWT 用 RS256 签名，两个后端使用同一对密钥，私钥不进仓库。
15. **Spring Security 限制在基础设施层**：JWT 编解码、密码编码器放 `infrastructure`（结构化放 `config`）。业务权限判断（是否管理员、管理范围）放应用服务的权限守卫，不散落在 Controller 或领域对象里。
16. **外部系统调用使用 `@HttpExchange`**：调用其他系统（例如课程管理系统调用本系统的权限检查接口）时，用 Spring HTTP Interface 声明客户端，放在 `infrastructure.<system>.client`，应用层只依赖端口接口。
17. **统一 AOP 日志**：接口层、应用层、基础设施层的日志统一由 `infrastructure.logging.LayerLoggingAspect` 输出；业务类不写 `@Slf4j`、Logger 或 `log.*`。日志级别写在 `application.yml` 的 `logging.level`，`logback-spring.xml` 只负责 appender 和格式。
18. **DDD 应用服务只依赖端口**：`application.**` 只能依赖 `domain.**` 和 `application` 内定义的端口接口（如 `TokenVerifier`、`OperationRecorder`），禁止引用 `JpaRepository`、JPA 实体、持久化对象或 `infrastructure.**`。
19. **状态与类型值归口领域层**：角色类型、账号状态、申请状态、权限类型等取值定义为领域层枚举，或对应领域对象中的 `public static final` 常量；应用层和领域层不出现重复的状态字符串和魔法数字。

## 日志规范

- 唯一的业务日志入口是 `infrastructure.logging.LayerLoggingAspect`，覆盖接口层、应用层、基础设施层的 Spring Bean，记录方法开始、完成、耗时和异常类型。日志器使用目标类自身的名称，因此可以按包调整级别。
- 领域对象不是 Spring Bean，不被切面拦截；领域规则拒绝时由应用层的「异常」日志体现。
- 日志不打印参数值、DTO/VO、异常堆栈或完整响应体，只打印层名、类名、方法名、耗时和异常类型。
- 严禁记录密码、JWT、Bearer Token、Authorization 头和数据库密码。
- `application.yml` 中三个业务包为 `DEBUG`，根日志为 `INFO`。Docker Swarm 部署通过环境变量覆盖，例如 `LOGGING_LEVEL_COM_RBAC_RBAC1DDD_INTERFACES=INFO`。

## CloneFactory 细节

- 依赖课程提供的 `cn.edu.xmu:clonefactory`，首次使用前在课程代码 `clonefactory` 目录执行 `mvn install -Dmaven.test.skip=true`。
- 在 `maven-compiler-plugin` 的 `annotationProcessorPaths` 中同时声明 Lombok 和 CloneFactory。
- 目标类用 `@CopyFrom`、`@CopyNotNullFrom`；来源类用 `@CopyTo`。DDD 中注解只写在基础设施层的持久化对象或接口层的 VO 上，领域对象不依赖 CloneFactory。
- 同名但类型不同的字段用字段级 `@CopyFrom.Exclude` 排除，再显式转换。
- **CloneFactory 不能处理 `final` 字段和 record**：生成的方法只返回 `target`，不复制任何字段。对 record、不可变对象逐字段显式赋值。
- 修改注解后执行 `mvn clean test`，并检查 `target/generated-sources/annotations/cn/edu/xmu/clonefactory/util/CloneFactory.java` 确认生成了实际赋值。

## API 约定

- 业务 ID 使用 `Long`，OpenAPI 声明为 `integer`，上限 `9007199254740991`。
- 请求体使用 `@Valid`；Controller 不手写 request map。
- 错误响应使用 `application/problem+json`，包含稳定的 `code` 和可直接展示的中文 `detail`，与 `RBAC1-api.yaml` 一致。
- 未登录 401、无权限或超出管理范围 403、不存在 404、冲突 409、请求不合法 400。
- 接口契约变更先改 `docs/03-api/RBAC1-api.yaml`，两种实现同步；DDD 的 Apifox 模块文件 `docs/03-api/rbac1-ddd-api.yaml` 从主契约原样摘取，不单独修改接口定义。

## DDD 仓储规则

- 仓储接口放在 `domain.<聚合>` 包，只接收和返回领域对象或 JDK 集合、`Optional`；分页返回 `domain.shared.DomainPage<T>`。
- 仓储方法签名不得出现 Command、Result、DTO、VO、JPA 实体、持久化对象、Spring Data `Page`。
- 一个聚合对应一个仓储，按聚合整体保存，只新增或删除发生变化的关联记录。
- 只读的跨聚合查询（如应用名称目录）使用应用层端口，不伪装成领域仓储。
- 架构测试扫描依赖方向：领域层不依赖 Spring、JPA、CloneFactory 和其他层；应用层不依赖基础设施层和 JPA。

## 测试约定

- `@DisplayName` 带验收条件编号，编号取自 `docs/01-requirements/RBAC1需求说明.md` 第 8 节，例如「验收2：拒绝循环继承」。
- URL 模板集中为测试类顶部的 `private static final String`。
- 每个业务错误码至少有一个契约测试；所有按 ID 操作的接口覆盖不存在 ID。
- 领域规则用不启动 Spring 的单元测试；接口行为用 MockMvc 集成测试。
- 目前集成测试使用 H2 的 MySQL 模式；MySQL 环境就绪后补充真实 MySQL 集成测试，H2 不能代替 Flyway 迁移验证。
- 两种实现共用同一份验收用例和测试数据（`test-bootstrap.sql`），保证「两种实现通过相同的验收条件」。

## 代码格式硬约束

- **注解独占一行**：类、字段、方法、参数、record 组件上的每个注解单独占一行，不写成 `@NotNull @Positive Long id` 或 `@Id private Long id`。泛型类型参数上的类型注解（如 `List<@NotNull Long>`）除外，它们属于类型的一部分。
- **真实换行**：写入源码必须使用实际换行符，禁止字面量 `\n`。
- **禁止单行压缩类**：类声明、注解、字段、构造器和方法按常规格式分行。
- **写入后自检**：批量生成后检查字面量 `\n` 和单行压缩，执行 `mvn test-compile`，有问题先修复。

## 完成后自检

需要逐项审查时读取 `references/review-checklist.md`。

## 与老师原规范的差异

| 老师原规范 | 本项目做法 | 原因 |
| --- | --- | --- |
| 不保存本地密码，只保存统一身份 user_id | 保存 Argon2 密码摘要和令牌摘要 | 本系统就是统一身份认证的提供方 |
| `/auth/*` 由外部系统实现 | `/auth/*` 由本系统实现 | 同上 |
| Apifox 项目 8913225 与推送脚本 | 手工导入 `docs/03-api` 下的 YAML | 本组没有推送脚本和 Apifox 令牌 |
| 状态常量必须是领域对象中的 `public static final` | 也允许领域层枚举 | 枚举类型更安全，同样满足「归口领域层、无魔法字符串」 |
| 数据库集成测试必须使用真实 MySQL | 暂用 H2，环境就绪后补 MySQL | 本地暂无 MySQL |
| 包名 `cn.edu.xmu.projectmgt.*` | 见上文分层对照表 | 项目不同 |
