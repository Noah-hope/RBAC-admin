# 代码审查清单

## API

- [ ] Controller 使用 `@Valid`。
- [ ] DTO 校验字段格式、范围、枚举和跨字段关系。
- [ ] 应用层、领域层没有重复做请求格式校验。
- [ ] 输入以 `DTO` 结尾，输出以 `VO` 结尾，未泄漏实体或领域对象。
- [ ] 错误码和中文提示与 `RBAC1-api.yaml` 一致。
- [ ] 按 ID 的接口覆盖不存在 ID。

## Java

- [ ] 没有 `var`。
- [ ] 类、接口、record、枚举和关键方法有中文 Javadoc。
- [ ] 每个注解独占一行。
- [ ] Lombok 已消除不必要的样板代码。
- [ ] 同名同类型字段用 CloneFactory，生成方法包含实际赋值。
- [ ] CloneFactory 没有用于 record 或 final 字段。

## 业务与事务

- [ ] 事务边界在 Service / 应用服务。
- [ ] 权限守卫在 Service / 应用服务，Controller 和领域对象不判断管理员身份。
- [ ] DDD 业务规则在聚合根或领域服务，应用服务只编排。
- [ ] 没有不必要的 `flush()`。
- [ ] 数据库约束错误在仓储适配器或 Service 处翻译。

## DDD 边界

- [ ] 仓储接口只出现领域对象、JDK 集合、`Optional`、`DomainPage`。
- [ ] 应用层不引用 JPA、Spring Data、持久化对象和 `infrastructure`。
- [ ] 领域层不引用 Spring、JPA、CloneFactory。
- [ ] 状态和类型值在领域层枚举或领域对象常量中定义。
- [ ] 架构测试通过。

## 日志

- [ ] 日志统一由 `LayerLoggingAspect` 输出，业务类没有 Logger 和 `log.*`。
- [ ] `application.yml` 将三个业务包设为 DEBUG，根日志为 INFO。
- [ ] `logback-spring.xml` 只配置 appender 和格式。
- [ ] 日志不含参数、令牌、Authorization 头和密码。

## 测试

- [ ] 新行为先见证 RED 再实现。
- [ ] `@DisplayName` 带验收条件编号。
- [ ] URL 常量集中在类顶部。
- [ ] 每个业务错误码有契约测试。
- [ ] 完整回归通过。
