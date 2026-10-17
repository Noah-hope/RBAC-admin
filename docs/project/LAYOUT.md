# 仓库目录约定

确认日期：2026-10-15。依据：用户确认目前只采用以下目录，项目团队共享记忆统一由 `docs/` 维护。

```text
rbac-admin/
├── AGENTS.md
├── README.md
├── .agents/
│   └── skills/
│       ├── grill-me/
│       ├── grilling/
│       ├── frontend-design/
│       └── test-driven-development/
└── docs/
    ├── README.md
    ├── project/
    │   ├── MEMORY.md
    │   ├── WORKFLOW.md
    │   └── DECISIONS.md
    ├── 01-requirements/
    ├── 02-ui-design/
    ├── 03-api/
    ├── 04-database/
    └── 05-detailed-design/
```

`project/` 同时保留导入记录、预览规则、技能来源及验证说明等项目记忆，不另建根目录状态、任务或个人记忆文件。Git 元数据、忽略规则及本地临时文件不属于业务目录结构。

## 迁移与维护

现有需求资料整体从原 `docs/` 迁入 `docs/01-requirements/`，内部仍按用例、流程、时序、活动、数据流及课程简表分类；`按用例分类/` 继续作为受保护的同步阅读副本。生成器和预览状态随需求一起迁移。

原始需求来源项目不修改，此处不记录本机存放位置。`import-manifest.json` 是首次导入快照，保留当时的来源记录和摘要；当前文件以 Git 为准，查找时使用新需求根目录。PNG/SVG 不重绘，图源不改变业务含义。

日常命令：

```powershell
python docs/01-requirements/refresh_requirements.py
python docs/01-requirements/sync_usecase_folders.py
```

前者更新图源、简表及副本，后者只同步副本。需要导出图片时仅为前者增加 `--previews`。

界面原型现位于 `02-ui-design/prototype/`，设计说明、启动及验证文档在 `02-ui-design/`。`03-api/`已交付API1.2.0及指定Apifox导出核对，`04-database/`已交付数据库1.1与正式跨阶段评审，`05-detailed-design/`已交付后端详细设计1.0、本地Issue草案与静态验证。当前不创建根目录 `tasks/`、`frontend/`、`backend/`、`prototype/` 或 `.github/`；正式代码布局在详细设计ENGINEERING中提出backend单工程、deploy及tools/demo的拟定结构，当前只记录方案，待实施开始时更新约定并创建；本轮没有新增这些工程目录。

## 验证记录

D-038实施组织：common表示用户先交付的公共基座责任范围，实际Java包仍沿用ENGINEERING中的core，不创建另一套同义包。身份/权限/申请/组织为后续职责分组，保留13逻辑模块；M11/M12公共基础先行、业务部分后续分派。本轮仍只改文档，不创建正式工程目录。

迁移前核对 227 个阅读副本及其来源摘要，迁移后默认刷新和同步通过。400 个需求文件完整迁移，617 个本地链接及 229 个图页结构检查通过；114 个图源、196 个图片/矢量预览及 11 个详细用例正文与迁移前逐字节一致，重复同步不改写相同文件。四个技能共 9 个配套文件摘要一致，首次导入清单未改写。当前阶段及后续事项见 `MEMORY.md`。
