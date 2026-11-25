# RBAC 权限管理中心

本项目为多个业务系统统一管理用户、业务角色、菜单、按钮和 API 权限。管理员先把权限授予业务角色，再把角色分配给用户。用户可以拥有多个角色，同一项权限只计算一次。

仓库包含 RBAC0 结构化后端、RBAC1 结构化后端和 RBAC1 面向对象（DDD）后端，以及配套的 RBAC1 管理前端。RBAC1 在基础授权上增加业务角色继承，并为课程系统提供统一身份接入。

## 从这里开始

| 想了解什么 | 阅读入口 |
| --- | --- |
| 项目范围和业务规则 | [RBAC0 需求](docs/01-requirements/RBAC0需求说明.md) · [RBAC1 需求](docs/01-requirements/RBAC1需求说明.md) |
| 设计资料和界面原型 | [文档索引](docs/README.md) · [统一设计图](docs/项目设计图.drawio) · [RBAC1 界面原型](docs/02-ui-design/RBAC1界面原型.html) |
| 代码结构和阅读顺序 | [RBAC0 代码阅读指南](docs/04-development/代码阅读指南.md) · [RBAC1 结构化后端说明](rbac1-structured/README.md) · [RBAC1 DDD 分层](rbac1-ddd/README.md#分层) |
| 后端配置、启动和测试 | [RBAC0 结构化后端](rbac0-structured/README.md#启动) · [RBAC1 结构化后端](rbac1-structured/README.md#启动) · [RBAC1 DDD 后端](rbac1-ddd/README.md#运行) |
| 管理前端启动和构建 | [前端说明](frontend/README.md) |
| Docker 镜像构建和 Swarm 部署 | [Swarm 部署配置](deploy/stack.yml) · 各前后端目录中的 Dockerfile |
| HTTP 接口契约 | [RBAC0 OpenAPI](docs/03-api/openapi.yaml) · [RBAC1 结构化 OpenAPI](docs/03-api/RBAC1-api.yaml) · [RBAC1 DDD OpenAPI](docs/03-api/rbac1-ddd-api.yaml) |
| 课程系统统一身份接入 | [应用登记、角色码映射和目录授权](rbac1-structured/README.md#课程系统统一身份接入) |

## 目录结构

```text
RBAC-admin/
├── README.md                 项目入口
├── AGENTS.md                 协作约定
├── rbac0-structured/          RBAC0 结构化 Java 后端（默认 8080）
│   ├── README.md             运行和测试说明
│   ├── pom.xml               依赖和构建配置
│   ├── tools/                JWT 密钥生成工具
│   └── src/
│       ├── main/java/        接口、业务、持久化代码
│       ├── main/resources/   应用配置和 RBAC0 数据库迁移
│       └── test/java/        API 集成测试
├── rbac1-structured/          RBAC1 结构化 Java 后端（默认 8081）
│   ├── README.md             运行、测试和接入说明
│   ├── pom.xml               依赖和构建配置
│   ├── tools/                JWT 密钥生成工具
│   └── src/
│       ├── main/java/        Controller、Service 和 Mapper
│       ├── main/resources/   应用配置和两套 RBAC1 共用的数据库迁移
│       └── test/java/        API 集成与数据库迁移测试
├── rbac1-ddd/                 RBAC1 面向对象 Java 后端（默认 8082）
│   ├── README.md             分层、接口和运行说明
│   ├── pom.xml               构建配置，引用结构化版的迁移脚本
│   └── src/
│       ├── main/java/        接口、应用、领域和基础设施代码
│       ├── main/resources/   应用配置
│       └── test/java/        领域单元、架构和 API 集成测试
├── frontend/                  Vue 3 + TypeScript 管理前端（默认 5173）
│   ├── README.md             启动、构建和测试说明
│   ├── .env.example          开发代理配置示例
│   ├── src/                  页面、组件、路由和 API 客户端
│   └── e2e/                  浏览器流程测试
├── deploy/
│   └── stack.yml             MySQL、两套后端和前端的 Swarm 配置
└── docs/
    ├── README.md             文档索引
    ├── 01-requirements/       RBAC0 和 RBAC1 需求
    ├── 02-ui-design/          界面操作清单和 HTML 原型
    ├── 03-api/                三个后端的 OpenAPI 契约
    ├── 04-development/        代码阅读指南和开发资料
    └── 项目设计图.drawio       统一设计图源文件
```

两个 RBAC1 后端提供相同的 47 个 HTTP 接口，共用 RBAC1 数据库结构和 Flyway 迁移，首次构建均需先安装课程提供的 CloneFactory。RBAC0 使用独立数据库。

正式管理界面位于 `frontend/`，开发代理默认连接 RBAC1 结构化后端。改为连接 DDD 后端（默认端口 8082）时，参照 [`frontend/.env.example`](frontend/.env.example) 在 `frontend/.env.local` 中设置 `RBAC_API_TARGET=http://127.0.0.1:8082`，再重启前端开发服务。

后端集成测试使用 H2。仓库已提供前后端 Dockerfile，由 `deploy/stack.yml` 统一管理 MySQL、两个后端和前端；所有正式服务运行在服务器的 Docker Swarm 中，数据库数据和后端日志挂载到对应节点的宿主机目录。真实镜像运行、MySQL Flyway 迁移、Docker Swarm 部署和课程系统联调仍待验收。

## 已实现的功能

- 用户可以**登录、查询当前身份和退出**。接入应用使用统一账号登录，权限中心签发 JWT 并提供验签公钥。退出后当前会话立即失效。
- 管理员可以**查看部门树，搜索、新增和修改用户**。总管理员可以维护部门结构、调动员工并任免部门管理员。员工调动时保留原有业务角色，账号停用时撤销已有会话。
- 总管理员可以**登记应用、菜单和按钮权限，并维护 API**。按钮归属同一应用的菜单。权限标识在应用内唯一，采用 `资源:操作` 格式，RBAC1 也接受 `CV` 这样的课程权限短码。菜单或按钮可以关联同一应用的多个 API，解除关联时保留 API 登记。
- 总管理员可以**创建和修改业务角色**，为同一个角色配置多个应用的权限。配置按钮权限时，角色须同时拥有所属菜单。删除业务角色前须解除用户关联，RBAC1 还须解除两个方向的继承关系。预置管理角色无法删除。
- RBAC1 支持**业务角色多父、多层继承**。角色详情区分直接权限和继承权限，并列出实际配置权限的来源角色。系统合并重复权限，禁止循环继承。修改角色时，系统校验当前角色及直接、间接子角色的按钮都具备所属菜单。
- 管理员可以**单独或批量分配、撤销业务角色**。批量操作先校验全部用户是否在管理范围内，整批保存后生效。
- 用户可以按应用**查询自己的菜单和按钮权限**。业务系统使用面向该应用的令牌检查菜单、按钮或已登记 API 的权限，授权变更在下一次检查时生效。
- 用户可以**申请菜单或按钮权限，撤回待审核申请**。管理员在职责和部门范围内审核，从符合权限要求的现有业务角色中选择一个授予申请人。RBAC1 在计算可申请权限和审核候选角色时纳入继承权限。
- 部门管理员可以**提交角色创建申请**。总管理员受理后可调整角色配置，实际创建成功后申请通过。员工之后通过正常授权流程获得该角色。
- RBAC1 支持**按应用返回角色码**。部署方在数据库启用映射后，`GET /auth/me` 返回直接分配或继承的业务角色所对应的应用角色码。未启用映射时，该接口返回直接分配且在目标应用提供有效权限的角色 ID 字符串。
- 课程系统可以接入 RBAC1 的**统一身份和选人目录**。角色映射配置完成后，课程应用可识别 `teacher`、`student` 和 `admin`，具体操作仍按课程应用的权限码校验。登记目录 API 并授予关联权限后，调用人可以逐层查询部门、按部门分页选人，以及按统一身份 ID 查询姓名和账号。
- 系统记录角色配置、授权变更、员工调动等操作及其结果，**支持搜索和分页**。总管理员可查看全部记录，应用登记、API 配置、角色配置和跨部门调动等全局记录仅总管理员可见。部门管理员可查看分配角色、撤销角色、停用账号等用户管理记录，按操作发生时被操作用户的所属部门判断范围，批量记录只显示范围内的用户。

总管理员负责全局配置。部门管理员负责本部门及下级部门的用户维护和业务角色分配。
