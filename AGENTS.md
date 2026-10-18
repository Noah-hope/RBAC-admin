# 项目协作规则

## 工作入口与共享记忆

- 项目根目录统一记为 `rbac-admin`，后续项目工作在本仓库开展；共享文档使用相对项目路径，不记录成员本机的存放位置。
- 开始工作先读本文件、`docs/project/MEMORY.md`、`docs/project/WORKFLOW.md`、`docs/project/DECISIONS.md`、`docs/project/GLOSSARY.md`，涉及需求时再读 `docs/README.md` 及对应详细用例。
- 团队成员及 agent 的交流、文档和设计统一采用 `docs/project/GLOSSARY.md` 的术语；“部门管理员”对应“权限管理员”，“公司共享角色”对应“公司级共享角色”。首次使用别称注明统一名称，明确后台身份与业务角色、管理范围与授权来源、OA 批准与实际执行的区别。术语变化同步维护本表；业务含义变化先按需求变更流程确认，不以术语整理改写需求。
- 项目团队共享记忆统一由 `docs/` 维护；状态、待办、决策与流程以 `docs/project/` 为准，各阶段交付归入对应编号目录。根目录 `AGENTS.md` 是协作入口，`README.md` 是项目导航，不另建第二份状态或任务记忆。
- 使用 Git 管理共享记忆。完成一项工作时，同步更新当前阶段、已完成事项、待办、相关决策与验证结果，再将文档和对应改动纳入同一提交。
- 记录可验证的事实；将未决问题、建议与已确认决策分开。不得把工具未连接、测试未运行或原型演示记成已完成实现。
- 共享文档和 API 契约不得包含令牌、密码、真实会话或其他凭据。Apifox 凭据只使用本地环境配置。

## 必须遵循的项目顺序

1. 需求分析：使用用户指定的 `grill-me` skill，其入口委托 `grilling`。对新增或变更的需求按轮次追问并记录决策；已导入的需求分析沿用当前基线，不自动重新开启全部访谈。
2. 界面原型设计：使用项目 `frontend-design` skill，Vue 3 + TypeScript + Tailwind CSS；覆盖用例中的角色、页面、状态、范围约束及异常展示。
3. API 设计：使用 Apifox 和 Apifox MCP；接口契约、示例、错误与用例映射纳入 Git，记录 Apifox 项目及同步方式。
4. 数据库设计：依据需求与 API 契约形成实体、关系、约束、索引及事务设计。
5. 详细设计与实现：先产出设计文档，分解流程、模块和依赖，再使用 TDD skill，先编写并运行失败测试，再按模块实现、重构并验证。

当前实施组织按 D-038：先由用户实现 BE-01～05 公共基座，按 `docs/05-detailed-design/FOUNDATION-HANDOFF.md` 验收并纳入共同主线后，再分派 BE-06～35 的业务/交付任务。D-055已按用户要求将BE-06～35登记到A～D四位成员代号，分工见`docs/05-detailed-design/TEAM-ASSIGNMENT.md`；D-056用户已明确认领A，并授权完整实现BE-06；其余成员实名、各成员GitHub账号及未核验的环境/权限证据待补。A负责身份与范围，B负责系统角色与权限，C负责申请审批，D负责人员来源与导入。按D-051由负责人自行确认完成，不设置独立评审人；不将文档修订或基座静态检查记为已实现/已验收。正式业务启动仍须满足原技术依赖。

D-039补充：基座阶段由用户本人确认，无需其他成员评审；TDD、自检、真实数据库/事务验证和交接证据仍保留。D-051进一步确认全部任务均由唯一负责人核对测试和自检证据并确认完成，不设置独立评审人；agent生成代码仍须满足验收行为、TDD及真实验证。当前先使用本机MySQL，云服务器已具备、Docker数据库留后续部署；JDK21由IDE引用，命令行未配置，可用IDE或显式JDK运行，不强制永久环境变量。

完整阶段交付要求见 `docs/project/WORKFLOW.md`，当前阶段及交接事实见 `docs/project/MEMORY.md`。不因目录或文档存在就宣布阶段完成。尚未选定的技术不自行记为既定方案。

项目技能统一从 `.agents/skills/<技能名>/SKILL.md` 读取，包含 `grill-me`、`grilling`、`frontend-design`、`test-driven-development`；优先使用仓库副本，来源与版本见 `docs/project/SKILLS.md`。`grill-me` 是委托入口，实际访谈同时读取 `grilling`。后续正式业务逻辑的新功能、缺陷修复及行为变更使用 TDD，编写或修改测试时同时读取其 `writing-good-tests.md`。

进入依赖某个 skill/MCP 的实际阶段前，检查其可用性，不因文件存在就声称外部工具已连接。Apifox MCP 已于 2026-10-16 验证文档读取与刷新，写入能力未提供；当前事实以共享记忆为准。

当前采用 `.agents/skills/`、`docs/project/`、`docs/01-requirements/`～`05-detailed-design/`及已生成的`backend/`。D-041授权完成BE-02公共HTTP骨架；正式后端为单Maven工程，基础包`com.adgn.backend`，配置YAML，不另建Git仓库或common同义包。未实现业务不建空接口；deploy/tools/.github按后续任务实际需要创建。界面阶段交付归入 `docs/02-ui-design/`，完整目录约定见 `docs/project/LAYOUT.md`。

## 当前需求的权威来源

当前 RBAC 系统只管理功能权限。角色选择是否授予系统进入权限及查看、编辑、新增等独立操作；不设计 SELF/ALL、数据范围字段、本人数据判断或业务行过滤。公司/部门仍用于后台管理边界、角色归属、任职资格和授权来源隔离，不能把这些概念扩展为业务数据权限。以 2026-10-16 V1.3 需求为准。权限管理员按总管理员指定的公司、指定部门及系统范围管理，同范围协作；公司级共享角色只由总管理员维护。离岗关权直接关闭原部门授权来源，一般角色变更经 OA；全局员工停用/恢复仅总管理员，恢复不复权。不设计公司/部门启停，普通员工不登录权限中心。详见 `docs/01-requirements/usecases-v1/管理范围与人员权限规则.md`。

- `docs/01-requirements/usecases-v1/用例说明.md` 与 UC-01～UC-11 详细用例：业务规则、范围与验收依据。
- `docs/01-requirements/usecase-tables-v1/`：课程展示简表，服从详细用例。
- `docs/01-requirements/usecases-v1/权限管理系统_用例图.drawio` 与 `docs/01-requirements/flows-v1/`：当前用例及业务流程表达。
- `docs/01-requirements/sequences-v1/`：需求分析级时序图，服从详细用例；不代表 API 已定稿。
- `docs/01-requirements/activities-v1/`：活动图及责任泳道，服从详细用例。
- `docs/01-requirements/dataflows-v1/`：数据流与边界分析；逻辑存储不代表数据库表或技术模块已确定。
- `docs/01-requirements/按用例分类/`：同步阅读副本，不是独立需求基线。

正式修改在按类型的原始目录完成，再于项目根目录运行：

```powershell
python docs/01-requirements/refresh_requirements.py
```

默认仅更新正文配套图源及阅读副本，PNG/SVG 按需生成：评审/交付时显式运行 `python docs/01-requirements/refresh_requirements.py --previews`。旧预览保留并标明版本，不能当作当前需求；规则见 `docs/project/PREVIEWS.md`。仅同步阅读副本可运行 `python docs/01-requirements/sync_usecase_folders.py`。

同步器若发现副本手工修改，应先将有用修改合并回正式文件，不绕过保护覆盖副本。业务规则变化时，同步相关详细用例、简表与图。

不得引用或自动重建已弃用的 `docs/history/`。原项目的 `.tool-install-review/`、临时依赖和导出暂存目录不属于需求来源，未导入本项目。

## 实现与验证约束

- 业务实现必须可追溯至用例、API 契约、数据库设计和模块设计。
- 每个模块先记录验收行为，再运行对应测试并确认失败原因，随后实现；不以写完实现后补测试冒充 TDD。
- 测试应覆盖业务结果、权限边界、异常和事务一致性，避免只验证实现细节。
- 原型可使用明确标注的模拟数据；模拟 OA/HR/业务系统交互不得宣称真实外部系统已接通。
- 修改需求、契约、数据结构或实现后，更新受影响的文档与共享记忆。
