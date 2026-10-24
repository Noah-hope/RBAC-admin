# 仓库目录约定

2026-10-23/D-104：用户明确授权根目录 `frontend/` 作为正式前端，沿用同一 Git 仓库。`src/pages` 按业务组织，`src/components` 放公共交互组件，`src/api` 从 Git OpenAPI 派生并封装请求；使用与检查见 [前端指南](../../frontend/README.md)。此前“不创建 frontend”为历史约定，原型仍保留供设计对照。

2026-10-18/D-056业务代码样例：web/identity/controller与dto放HTTP适配；identity/service、repository、security、config放身份应用流程、受控SQL和认证配置。其他成员按各自业务域建立对应文件，沿用单Maven和原逻辑模块。具体文件与调用说明见[BE-06记录](../05-detailed-design/BE-06-IDENTITY.md)，不要求为未实现业务建立空接口或空实现。

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
├── frontend/                     # Vue 3/TypeScript 正式前端，真实 API 请求
│   ├── package.json, package-lock.json, vite.config.ts
│   ├── src/, public/, tests/, e2e/, scripts/
│   └── README.md, DESIGN.md, API-COVERAGE.md, VALIDATION.md
├── backend/                      # 用户已生成的Spring Boot单Maven工程
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd, .mvn/wrapper/
│   ├── README.md
│   ├── logs/build/               # 本机手工保存的构建检查日志，Git忽略
│   └── src/main/, src/test/      # 普通/迁移入口、公共HTTP、配置、db/migration及离线测试
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

界面原型现位于`docs/02-ui-design/prototype/`；API、数据库及详细设计分别归入03～05阶段目录。D-041后正式后端位于`backend/`，com.adgn.backend按功能分包，配置YAML。当前不建立额外根tasks/frontend/prototype；deploy/tools/.github留对应任务实际需要时创建。后端用法见[README](../../backend/README.md)，进度以共享记忆为准。

## 验证记录

后续实际工程更新（2026-10-17）：用户已通过IDE创建backend，基础包为com.adgn.backend，配置为YAML；本次将Wrapper发行版固定回3.9.11，生成工程构建及1项上下文测试通过。只纳入可移植的生成文件，不纳入IDE个人设置、插件目录、构建产物或凭据；该段为初始工程历史；最新D-041已实现BE-02公共行为及TDD，仍待本人确认，基座未整体验收。下述创建前约定保留为历史，不表示应删除现有工程。

D-040后续确认：正式后端位置确定为`rbac-admin/backend`，采用ENGINEERING中的单Maven工程；IDE使用IntelliJ IDEA、JDK21。沿用根Git仓库，不生成嵌套仓库或`backend/backend`。本次仅确认位置，未创建工程，以上目录树仍表示现有目录；BE-02实施时再同步实际目录树。

D-038实施组织：common表示用户先交付的公共基座责任范围，实际Java包仍沿用ENGINEERING中的core，不创建另一套同义包。身份/权限/申请/组织为后续职责分组，保留13逻辑模块；M11/M12公共基础先行、业务部分后续分派。本轮仍只改文档，不创建正式工程目录。

迁移前核对 227 个阅读副本及其来源摘要，迁移后默认刷新和同步通过。400 个需求文件完整迁移，617 个本地链接及 229 个图页结构检查通过；114 个图源、196 个图片/矢量预览及 11 个详细用例正文与迁移前逐字节一致，重复同步不改写相同文件。四个技能共 9 个配套文件摘要一致，首次导入清单未改写。当前阶段及后续事项见 `MEMORY.md`。
