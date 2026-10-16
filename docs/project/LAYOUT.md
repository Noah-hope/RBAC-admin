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

原始项目 `D:\NoahProject\codexProject\ooad` 不修改。`import-manifest.json` 是首次导入快照，保留旧路径和摘要；当前文件以 Git 为准，查找时使用新需求根目录。PNG/SVG 不重绘，图源不改变业务含义。

日常命令：

```powershell
python docs/01-requirements/refresh_requirements.py
python docs/01-requirements/sync_usecase_folders.py
```

前者更新图源、简表及副本，后者只同步副本。需要导出图片时仅为前者增加 `--previews`。

界面原型现位于 `02-ui-design/prototype/`，设计说明、启动及验证文档在 `02-ui-design/`。`03-api/`已交付API1.1.0，`04-database/`已交付数据库设计草案及静态验证，`05-detailed-design/`仍仅保留`.gitkeep`，尚未开展。当前不创建根目录 `tasks/`、`frontend/`、`backend/`、`prototype/` 或 `.github/`；正式代码布局到实现阶段再确定。

## 验证记录

迁移前核对 227 个阅读副本及其来源摘要，迁移后默认刷新和同步通过。400 个需求文件完整迁移，617 个本地链接及 229 个图页结构检查通过；114 个图源、196 个图片/矢量预览及 11 个详细用例正文与迁移前逐字节一致，重复同步不改写相同文件。四个技能共 9 个配套文件摘要一致，首次导入清单未改写。当前阶段及后续事项见 `MEMORY.md`。
