# RBAC 权限管理中心

为业务系统统一管理用户、角色以及菜单、按钮和 API 权限。仓库包含 RBAC0 结构化后端、RBAC1 结构化与 DDD 后端，以及 RBAC1 管理前端。RBAC1 支持角色继承和课程系统统一身份接入。

[现场检查](#现场检查) · [线上演示](#线上演示) · [项目导航](#项目导航)

## 现场检查

| 顺序 | 检查项 | 现场对照 |
| --- | --- | --- |
| 1. 需求与测试 | 核对角色继承、按应用映射角色码、授权范围和审批规则。运行两套 RBAC1 测试，查看 JaCoCo 指令和分支覆盖率。 | [需求规格说明书](docs/01-requirements/需求规格说明书.md) · [结构化角色码映射测试](rbac1-structured/src/test/java/com/rbac/rbac1/RbacApiTest.java#L596) · [DDD 会话与有效权限测试](rbac1-ddd/src/test/java/com/rbac/rbac1ddd/interfaces/SessionApiTest.java#L206) · [JaCoCo 现场命令与报告说明](docs/04-development/JaCoCo覆盖率测试指南.md) |
| 2. 运行两个系统 | 在 RBAC 查看角色继承来源、用户授权和操作记录。用课程管理员、教学组长、任课教师和学生账号核对课程操作范围。 | [线上演示与账号](#线上演示) |
| 3. 包图、接口与源码 | 对照两套后端包图，以 `GET /auth/me` 和角色继承配置为例，沿类图、顺序图和 API 映射表定位代码。 | [结构化包图](docs/design/assets/rbac1-structured-package.svg) · [DDD 包图](docs/design/assets/rbac1-ddd-package.svg) · [两套后端的类图、顺序图与源码索引](docs/design/详细设计文档.adoc#oral-review) |
| 4. 设计与技术问答 | 解释结构化与 DDD 的职责分配、GRASP 信息专家，以及 Spring MVC、持久化和 MySQL 在一次请求中的分工。切换后端后在前端打开菜单，对照两套实现的 Logback 分层日志。 | [职责对照](#设计问答) · [Swarm 部署与后端切换](deploy/README.md) · [Logback 实时日志命令](docs/04-development/Logback日志查看指南.md) |

### 设计问答

- **结构化与 DDD 如何分工？** 从同一个 `GET /auth/me` 入口开始。结构化版由 Service 查询、遍历角色关系并组装响应。DDD 版由应用服务编排，领域对象计算有效权限。[结构化接口入口](rbac1-structured/src/main/java/com/rbac/rbac1/controller/SessionController.java#L59) · [DDD 接口入口](rbac1-ddd/src/main/java/com/rbac/rbac1ddd/interfaces/rest/SessionController.java#L62) · [两套实现的职责对照](rbac1-ddd/README.md#与结构化实现的主要区别)
- **GRASP 的信息专家体现在哪里？** `User` 持有用户被分配的角色，`RoleHierarchy` 持有角色继承关系和权限配置。沿这两个对象查看有效权限和来源角色的计算。[User](rbac1-ddd/src/main/java/com/rbac/rbac1ddd/domain/user/User.java) · [RoleHierarchy](rbac1-ddd/src/main/java/com/rbac/rbac1ddd/domain/role/RoleHierarchy.java)
- **一次请求经过哪些层？** 从 Spring MVC 的 Controller 跟到应用服务，再到 Spring Data JPA 和 MySQL。用部署图定位对应服务，用 Logback 查看实际调用。[DDD 分层](rbac1-ddd/README.md#分层) · [Swarm 部署图](docs/design/assets/rbac1-swarm-deployment.svg) · [分层日志说明](docs/04-development/Logback日志查看指南.md#日志输出对照说明)

## 线上演示

| 系统 | 演示入口 |
| --- | --- |
| RBAC 权限管理中心 | [打开权限管理系统](http://113.44.137.160:8088/) |
| 课程项目管理系统 | [打开课程项目管理系统](http://120.46.76.13:8089/) |

演示密码由小组在现场提供。切换账号前先退出，或使用独立的浏览器会话。

| 账号 | 登录入口 | 检查内容 |
| --- | --- | --- |
| `admin` 总管理员 | RBAC | 查看应用权限目录、角色继承来源、用户授权和操作记录。 |
| `SG000001` 课程管理员 | 课程系统教师入口 | 查看学期、教师目录和全局课程，检查课程审核与开放。 |
| `SG000011` 教学组长 | 课程系统教师入口 | 打开「市政工程项目管理」，查看里程碑、教学班和任课教师配置。 |
| `SG000019` 任课教师 | 课程系统教师入口 | 打开「市政养护项目班」，查看本班名单、里程碑和评分入口。 |
| `SG000191` 学生 | 课程系统学生入口 | 打开「我的课程」中的「市政工程项目管理」，查看里程碑和本人成绩。 |

## 项目导航

| 资料 | 入口 |
| --- | --- |
| 需求与界面 | [需求规格说明书](docs/01-requirements/需求规格说明书.md) · [界面操作清单](docs/02-ui-design/界面操作清单.md) · [RBAC1 界面原型 HTML](docs/02-ui-design/RBAC1界面原型.html) |
| 设计与数据库 | [交互设计图册 HTML](docs/design/index.html) · [数据库设计与 ER 图](docs/design/数据库设计文档.adoc) · [可编辑的 Draw.io 原图](docs/design/项目设计图.drawio) · [RBAC1 Flyway 迁移](rbac1-structured/src/main/resources/db/migration) |
| API 契约 | [RBAC0 OpenAPI](docs/03-api/openapi.yaml) · [RBAC1 结构化 OpenAPI](docs/03-api/RBAC1-api.yaml) · [RBAC1 DDD OpenAPI](docs/03-api/rbac1-ddd-api.yaml) |
| 后端代码、启动与测试 | [RBAC0 结构化](rbac0-structured/README.md) · [RBAC0 代码阅读指南](docs/04-development/代码阅读指南.md) · [RBAC1 结构化](rbac1-structured/README.md) · [RBAC1 DDD](rbac1-ddd/README.md) |
| 前端、部署与统一身份 | [管理前端](frontend/README.md) · [Swarm 部署与后端切换](deploy/README.md) · [课程系统统一身份接入](rbac1-structured/README.md#课程系统统一身份接入) |
| 全部文档 | [文档索引](docs/README.md) |

下载仓库后，可在浏览器中打开 HTML 图册和界面原型。两套 RBAC1 后端共用数据库迁移，对外提供同样的 47 个 HTTP 接口。
