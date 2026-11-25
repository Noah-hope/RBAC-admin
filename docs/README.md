# 文档索引

首次阅读建议按“需求 → 设计图 → 界面 → 接口 → 代码”的顺序。

| 顺序 | 资料 | 用途 |
| --- | --- | --- |
| 1 | [RBAC0 需求文档](01-requirements/RBAC0需求说明.md) · [RBAC1 需求文档](01-requirements/RBAC1需求说明.md) | 确认角色、管理范围、角色继承和统一身份规则 |
| 2 | [数据库设计文档（AsciiDoc）](数据库设计文档.adoc) | Gitee 在线直接查看概念 ER 图、物理表结构图与 18 张表数据字典 |
| 2 | [详细设计文档（AsciiDoc）](详细设计文档.adoc) | Gitee 在线直接查看两版后端包图、DDD 领域全景图、Swarm 部署图及 7 组核心用例类图/顺序图/GRASP 对照 |
| 2 | [RBAC1 设计与架构图册（HTML）](design-atlas/index.html) | 浏览器打开交互式图册，支持双实现左右并排、类图/顺序图一键切换、拖拽无损缩放与接口检索 |
| 2 | [项目设计图（36 页母本）](项目设计图.drawio) | 使用 diagrams.net（draw.io）桌面端打开查看与编辑全部 36 页设计图 |
| 3 | [界面操作清单](02-ui-design/界面操作清单.md) | 按页面理解用户操作 |
| 3 | [RBAC0 多系统权限配置示例](02-ui-design/多系统权限配置示例.html) | 下载后用浏览器打开基础界面示例 |
| 3 | [RBAC1 界面原型](02-ui-design/RBAC1界面原型.html) | 演示角色多继承、继承来源和删除约束 |
| 4 | [OpenAPI](03-api/openapi.yaml) | 查看请求、响应和接口约束 |
| 4 | [RBAC1 OpenAPI](03-api/RBAC1-api.yaml) | RBAC1 结构化后端的完整接口契约 |
| 4 | [RBAC1 DDD OpenAPI](03-api/rbac1-ddd-api.yaml) | 由 RBAC1 OpenAPI 生成的 DDD 后端契约（同样 47 个接口，服务地址 8082），在 Apifox 中单独建模块导入 |
| 5 | [代码阅读指南](04-development/代码阅读指南.md) | 对照业务找到实现位置 |
| 6 | [JaCoCo 覆盖率测试操作指南](04-development/JaCoCo覆盖率测试指南.md) | 助教现场终端执行测试并打开 HTML 报告 |
| 6 | [Logback 日志查看操作指南](04-development/Logback日志查看指南.md) | 助教现场切换菜单时查看服务器实时分层日志 |
| 7 | [后端说明](../rbac0-structured/README.md) | 配置数据库、启动和测试 |

[返回项目首页](../README.md)
