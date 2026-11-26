[需求规格说明书](docs/01-requirements/需求规格说明书.md)

## 需求说明

- **应用与权限** 登记业务应用、菜单、按钮和 API，配置调用接口所需的权限。
- **角色管理** 新建业务角色，配置直接权限和父角色，查看有效权限及其来源。
- **用户与部门** 维护组织部门和用户资料，支持停用账号。
- **用户授权** 给用户分配或撤销业务角色，支持批量操作。
- **我的权限** 用户可以按应用查看自己拥有的角色和有效权限。
- **我的申请** 用户可以申请需要的权限，查看进度或撤回待审核申请。
- **权限申请审核** 管理员在自己的管理范围内审核用户的权限申请。
- **角色创建申请** 部门管理员可申请创建业务角色，由总管理员审批。
- **操作记录** 管理员可以在授权范围内查询角色配置、用户授权和审批等操作记录。
- **统一身份接入** 课程系统等接入应用可以验证登录身份，查询用户在当前应用中的角色和权限。

## 演示系统

- [权限管理系统](http://113.44.137.160:8088/)
- [课程管理系统](http://120.46.76.13:8089/)

| 账号 | 密码 | 角色 | 登录入口 |
| --- | --- | --- | --- |
| `admin` | `admin123+` | 权限系统总管理员 | 权限管理系统 |
| `SG000001` | `teacher123+` | 课程管理员 | 课程管理系统教师入口 |
| `SG000011` | `teacher123+` | 教学组长 | 课程管理系统教师入口 |
| `SG000019` | `teacher123+` | 任课教师 | 课程管理系统教师入口 |
| `SG000191` | `student123+` | 学生 | 课程管理系统学生入口 |

## 项目设计图

- [详细设计文档](docs/design/详细设计文档.adoc)
- [GitHub Pages 图册导航](https://noah-hope.github.io/RBAC-admin/#design)

### 两种后端设计

- **结构化设计** 按功能划分 Service，由 Service 调用 Mapper 读取数据、执行业务规则并组装结果。例如 `GET /auth/me` 的角色继承计算和权限筛选都在 Service 层完成。
- **DDD 设计** 应用服务负责组织用例，领域对象承担业务规则。同一个接口由 `User` 和 `RoleHierarchy` 计算有效权限，`PermissionCatalog` 筛选所属应用的权限码，基础设施层负责实现仓储接口。

## 测试代码

- [RBAC1 结构化版测试](rbac1-structured/src/test/java)
- [RBAC1 DDD 版测试](rbac1-ddd/src/test/java)

### 测试方法

- **结构化版业务测试** 用 JUnit 和 `@DataJpaTest` 加载 JPA 及相关 Service，在 H2 内存数据库中准备用户、角色和权限数据，断言角色继承后的有效权限和来源是否正确。
- **DDD 版领域测试** 用 JUnit 直接构造 `User`、`RoleHierarchy` 等领域对象，验证权限计算规则。领域单元测试无需启动 Spring 或数据库。
- **两版接口测试** 用 `@SpringBootTest` 启动 Spring 测试环境，用 MockMvc 模拟登录和业务请求，断言 HTTP 状态和 JSON 响应。接口测试用 H2 和临时密钥，无需启动 MySQL。

**需求测试示例（FR-01.6）** 没有配置课程权限的角色，仍然可以取得已映射的课程身份。

1. 启用课程应用的角色码映射。创建一个没有配置课程权限的学生角色，将它映射为 `student`，再分配给测试账号。
2. 用该账号登录，携带令牌调用 `GET /auth/me`。
3. 断言响应为 HTTP `200`，`roles` 为 `["student"]`，`permissions` 为空数组。[结构化版用例](rbac1-structured/src/test/java/com/rbac/rbac1/RbacApiTest.java#L600) · [DDD 版用例](rbac1-ddd/src/test/java/com/rbac/rbac1ddd/acceptance/SharedAcceptanceTest.java#L607)

在仓库根目录运行测试。首次运行前，按[构建准备说明](rbac1-ddd/README.md)安装课程提供的 CloneFactory。

```shell
mvn -f rbac1-structured/pom.xml clean test
mvn -f rbac1-ddd/pom.xml clean test
```

JaCoCo 报告位于各模块的 `target/site/jacoco/index.html`，可查看指令和分支覆盖率。[覆盖率查看指南](docs/04-development/JaCoCo覆盖率测试指南.md)
