# rbac-admin · 集中权限管理系统

项目本地目录：`D:\NoahProject\rbac-admin`。

项目团队共享记忆统一维护在 `docs/`；当前阶段、已完成事项及待办见[项目共享记忆](docs/project/MEMORY.md)。

当前业务范围：**仅管理功能权限**。员工经角色获得系统进入及查看、编辑、新增等操作权限；不设计业务数据权限。需求于 2026-10-15 按用户要求修订，说明见[变更记录](docs/project/FUNCTION_PERMISSIONS.md)。

权限管理员按公司、部门子树及系统范围管理；员工离岗关权直接生效。规则见[管理范围与人员权限](docs/01-requirements/usecases-v1/管理范围与人员权限规则.md)。PNG/SVG 按需生成，现有 V1.1 预览已标记，详见[预览维护规则](docs/project/PREVIEWS.md)。

## 分析与设计方法

本项目采用**结构化分析与结构化设计方法**，以自顶向下、逐步求精的方式分解业务处理和系统模块，并以高内聚、低耦合作为模块划分原则。

- 需求分析阶段：结合用例明确参与者、业务规则和验收行为，通过顶层及第一层数据流图描述系统边界、处理过程、数据流和逻辑存储，保持上下层数据流平衡。现有依据见[数据流图与数据流分析](docs/01-requirements/dataflows-v1/README.md)。
- 设计阶段：在需求、界面、API 和数据库设计的基础上，将处理过程逐步细化为职责明确的模块，说明模块层次、调用关系、输入输出、接口、异常及事务边界，并建立需求到模块和测试的追踪关系。数据流中的处理过程和逻辑存储不直接等同于代码模块和数据库表。
- 表达与验证：用例、活动图和时序图辅助描述业务行为；详细设计通过模块结构与接口说明落实结构化设计，实现通过 TDD 验证业务结果及边界。

上述内容是项目采用的方法与后续交付要求，不表示结构化设计已经完成。当前阶段及交付事实以[项目共享记忆](docs/project/MEMORY.md)为准，详细流程见[开发流程](docs/project/WORKFLOW.md)。

## 阅读入口

- [协作规则](AGENTS.md)：每次开展工作先读。
- [项目共享记忆](docs/project/MEMORY.md)：当前阶段、事实、待办和交接入口。
- [开发流程](docs/project/WORKFLOW.md)：五个阶段及 TDD 执行要求。
- [决策记录](docs/project/DECISIONS.md)：用户已确定的约束与后续变更依据。
- [统一术语表](docs/project/GLOSSARY.md)：团队及 agent 交流使用的名称、别称、定义和边界示例。
- [团队文档总入口](docs/README.md)：共享记忆及五个阶段的资料导航。
- [需求资料](docs/01-requirements/README.md)：正式需求分析及配套图。
- [需求总览与统一规则](docs/01-requirements/usecases-v1/用例说明.md)：2026-10-15 功能权限修订，保留 UC-01～UC-11。
- [按用例分类阅读](docs/01-requirements/按用例分类/README.md)：详细说明、简表和图的同步阅读副本。
- [导入说明](docs/project/IMPORT.md)：资料来源、校验方式及导入边界。
- [前端界面原型](docs/02-ui-design/README.md)：页面、启动方式和原型验证。
- [Material 3 界面方案](docs/02-ui-design/MATERIAL3-PLAN.md)：已确认并实施的页面、导航、Token 与改造清单，原型 0.2.0 待评审。
- [项目技能](docs/project/SKILLS.md)：仓库内四个技能的入口、来源、版本和文件摘要。

## 当前目录

```text
rbac-admin/
├── AGENTS.md
├── README.md
├── .agents/skills/
│   ├── grill-me/
│   ├── grilling/
│   ├── frontend-design/
│   └── test-driven-development/
└── docs/
    ├── README.md
    ├── project/
    ├── 01-requirements/
    ├── 02-ui-design/
    ├── 03-api/
    ├── 04-database/
    └── 05-detailed-design/
```

界面阶段已有可运行交互原型，位于 `docs/02-ui-design/prototype/`，待用户评审。API、数据库及详细设计阶段当前只保留目录，交付在对应阶段产生。目录约定及迁移说明见 [LAYOUT.md](docs/project/LAYOUT.md)。

## 规定的项目流程

1. 需求分析：使用 `grill-me`（实际调用 `grilling`）。本项目已有需求分析作为起点，新增或变更需求再按该方式澄清。
2. 界面原型设计：使用 `frontend-design`，Vue 3 + TypeScript + Tailwind CSS。
3. API 设计：使用 Apifox，并通过 Apifox MCP 协作，契约同时纳入 Git。
4. 数据库设计：依据已确定的需求与接口契约设计。
5. 详细设计与实现：先生成设计文档、分解业务流程和模块，再使用 TDD skill，逐模块执行“失败测试 → 最小实现 → 重构”。

各阶段的交付内容见[开发流程](docs/project/WORKFLOW.md)。后端语言、框架、数据库产品与测试框架尚未选定。

## Git 共享记忆

阶段状态、待办、设计决策、需求和验证证据统一写入 `docs/`，与对应改动一起提交。其他协作者或新的会话从 `AGENTS.md` 进入并读取 `docs/project/`，不另建根目录状态文件。`.agents/skills/` 仅保存技能，不作为项目事实记忆。

当前仅建立本地仓库；远程地址尚未配置。
