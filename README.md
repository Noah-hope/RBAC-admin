# rbac-admin · 集中权限管理系统

项目本地目录：`D:\NoahProject\rbac-admin`。

当前状态：已初始化 Git，已导入既有需求分析资料并建立项目流程和共享记忆；尚未开始界面原型、API、数据库及业务代码实现。

## 阅读入口

- [协作规则](AGENTS.md)：每次开展工作先读。
- [项目共享记忆](docs/project/MEMORY.md)：当前阶段、事实、待办和交接入口。
- [开发流程](docs/project/WORKFLOW.md)：五个阶段及 TDD 执行要求。
- [决策记录](docs/project/DECISIONS.md)：用户已确定的约束与后续变更依据。
- [需求资料总入口](docs/README.md)：已完成的需求分析及配套图。
- [需求总览与统一规则](docs/usecases-v1/用例说明.md)：2026-10-13 的 UC-01～UC-11 基线。
- [按用例分类阅读](docs/按用例分类/README.md)：详细说明、简表和图的同步阅读副本。
- [导入说明](docs/project/IMPORT.md)：资料来源、校验方式及导入边界。

## 规定的项目流程

1. 需求分析：使用 `grill-me`（实际调用 `grilling`）。本项目已有需求分析作为起点，新增或变更需求再按该方式澄清。
2. 界面原型设计：使用 Vue 3 + TypeScript + Tailwind CSS。
3. API 设计：使用 Apifox，并通过 Apifox MCP 协作，契约同时纳入 Git。
4. 数据库设计：依据已确定的需求与接口契约设计。
5. 详细设计与实现：先生成设计文档、分解业务流程和模块，再使用 TDD skill，逐模块执行“失败测试 → 最小实现 → 重构”。

各阶段的交付内容见[开发流程](docs/project/WORKFLOW.md)。后端语言、框架、数据库产品与测试框架尚未选定。

## Git 共享记忆

项目规则、阶段状态、设计决策、需求和测试证据写入仓库，与对应改动一起提交。其他协作者或新的会话从 `AGENTS.md` 和共享记忆恢复上下文。

当前仅建立本地仓库；远程地址尚未配置。
