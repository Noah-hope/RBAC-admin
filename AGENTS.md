# 项目协作规则

D-079实际结果：BE-16完整112离线＋215真实MySQL/HTTP及静态追踪通过，失败/错误/跳过0；含本任务22项内部写端口验证、双连接关闭/执行竞争和第N条/审计回滚。随机库/账号/测试密钥目录0，专属MySQL子进程39780正常关闭，用户原MySQL84仍Running。BE-16为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，本地任务分支保留，未合并推送；BE-14/28待D人工验收状态不变，不自动开始BE-26/29。

D-079（2026-10-20）：成员D明确“接下来开始执行下一个任务”，按既定依赖选择BE-16；BE-04/14代码已交付共同main，基线a860dc8，分支codex/be-16-grant-mutations。范围为BaselineWriter、GrantApplier、SourceCloser内部原子端口，先设计/TDD及真实隔离MySQL验证；不新增公开写入口，不替代C的固定申请/OA/执行恢复或BE-26生命周期及申请失效编排。来源部门与角色所属部门分别校验任职，允许两处均合格时不同部门；不改变角色归属或后台管理范围规则。BE-14/28人工验收仍开放，本次开发不扩展此前BE-14特定合并推送授权。实际结果和交接见docs/05-detailed-design/BE-16-GRANT-MUTATIONS.md。

D-078实际交付（2026-10-20）：成员D明确“先完成合并与推送”，BE-14来源367cdae2acc6ff247116651abad53adafb6a34b9已无冲突保留历史合入远程main/c7435d9b17a2f9be57d1e748cae24f125a45deb2，任务分支已推送并保留，远程完整SHA回查一致，合并树等于已验证来源。主线未新增业务变更，沿用完整112离线＋193真实MySQL/HTTP证据；本轮静态追踪通过，不重跑Java/SQL，不部署或开新任务。BE-14保持IMPLEMENTED、mergedToMain=true、ownerAccepted=false，BE-28人工验收也仍开放，不以交付授权代记验收。此前D-077未授权/待主线为历史，接入见docs/05-detailed-design/BE-14-GRANT-BASELINES.md。

D-077（2026-10-20）：当前成员D明确推迟BE-28人工验收并开始下一项，按既定顺序实施BE-14；远程main已核对61132f8，分支codex/be-14-grant-baselines。先设计/TDD实现三查询与GrantReader事务内来源/关系/历史/当前资格事实端口，保持纯读取与逻辑0、完整任职及固定来源历史边界。完整112离线＋193真实MySQL/HTTP及静态检查通过；随机库/账号及临时密钥目录均0、专属实例关闭、原MySQL84仍Running，详见docs/05-detailed-design/BE-14-GRANT-BASELINES.md。BE-14为IMPLEMENTED待本人确认及主线交付，BE-28仍已交付待本人验收；本次继续开发不代验收，也不扩展此前BE-28特定合并推送授权，不自动开BE-16/29。

D-076实际交付（2026-10-20）：BE-28来源233f63352b0c8dea92fd916c47bd29d59267f97c已保留历史合入远程main/4b384fb7b629affd8fbd0e5762cad652bbdb3158，任务分支已推送并保留，远程完整SHA回查一致、合并树等于验证来源。接入B/BE-17/12后完整110离线＋178真实MySQL/HTTP及静态检查通过；Git连接失败后通过官方Git Database API按本地对象SHA原样、force=false交付，无PR/部署。BE-28为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，仍待D人工验收；D原实施编号因与B冲突改为D-075，B的D-073/074及双方事实保留。下方待交付/未授权为历史，验收步骤见docs/05-detailed-design/BE-28-IMPORT-FILES.md。

D-076（2026-10-20）：成员D询问“我该怎么验收，能不能先合并和推送”，授权先交付BE-28至main再人工验收。先接入远程main/1f7886e（含B/BE-17/12）并完整验证，保留双方实现与交付事实；D原BE-28编号D-073与B冲突，统一改为D-075，不改原授权和验证事实。BE-28仍IMPLEMENTED、ownerAccepted=false，实际合并/推送结果成功后补；不强推、不部署、不自动实施BE-29/30。下方未授权BE-28交付为此前边界。

D-075（2026-10-20）：当前成员D明确“可以，那接下来先进行BE-28”。已同步main/93acc63，在codex/be-28-import-files按设计/TDD实现CSV/XLSX上传与元数据查询、不可变文件存储及安全解析，复用现有迁移、身份、guard、幂等和审计。完整102离线＋159真实MySQL/HTTP及静态校验通过；随机库/账号和临时密钥/文件目录均清空、专属实例关闭，原MySQL84仍Running，见docs/05-detailed-design/BE-28-IMPORT-FILES.md。实现、负责人验收与主线交付分别记录，BE-28为IMPLEMENTED、ownerAccepted=false、mergedToMain=false；不将此前BE-10特定交付授权扩展到本任务，不自动实施BE-29/30。A/B此前交付与人工验收状态保留，下方旧数量为历史。

D-074实际交付（2026-10-20）：BE-17/12 来源 1fb95ad 已无冲突保留历史合入远程 main/3103b35，分支已推送并保留，远程完整 SHA 回查一致，合并树等于已验证源。完整 93＋169 通过，本轮合并后 93 普通回归及静态校验再次通过；交付不代记本人验收。Git 连接超时改用官方 API 按本地对象摘要、force=false 交付，无 PR/部署。下方待交付是历史，接入见 docs/05-detailed-design/BE-17-12-PREVIEW-CATALOG.md。

D-074（2026-10-20）：B 用户明确“你检查完之后合并就好”，授权检查 BE-17/12 后合并并推送远程 main，替代 D-073 当轮未授权交付的边界。保留任务分支，不强推/部署，不将交付指令代记本人验收；实际合并与远程回查见 docs/05-detailed-design/BE-17-12-PREVIEW-CATALOG.md。

D-073（2026-10-20）：当前 B 用户明确“17和12你做一下”，在 codex/be-17-12-preview-catalog（基线 main/93acc63）按原设计及 TDD 实现 BE-17 安全预览和 BE-12 系统/目录编辑。完整 93 普通＋169 真实隔离 MySQL/HTTP 检查通过。BE-12 同分支消费 BE-17，两项待本人确认及主线交付，不把此前三项的合并授权扩展为此次自动推送 main。实现、验证及接入见 docs/05-detailed-design/BE-17-12-PREVIEW-CATALOG.md；下方未实现为历史，BE-18/33/35 仍开放。

D-072实际交付（2026-10-20）：B 的 BE-11/13/15 已由 b92cdbb 合入并交付远程 main/0e391c6，B 分支保留；远程完整 SHA 回查一致，合并树等于源分支。85 普通＋150 真实 MySQL/HTTP 检查及完整静态校验通过；任务仍待负责人验收，不代记 OWNER_ACCEPTED。Git 连接超时改用官方 Git API 按本地对象 SHA 原样、force=false 交付，不强推/创建 PR/部署。BE-17 技术依赖就绪但未实现；下方此前未交付为历史。见 docs/05-detailed-design/B-MAIN-INTEGRATION.md。

D-072（2026-10-20）：当前 B 用户明确要求“现在拉到本地做合并，按照最初要求修正以后，合并到远程”，授权拉取最新 main、依原设计修正和验证后合并并推送远程 main；此前 D-071 不推送 main 的边界为历史。保留任务分支、不强推、不部署；实现交付与负责人验收分别记录。当前验证及实际交付见 docs/05-detailed-design/B-MAIN-INTEGRATION.md，不把 BE-11/13/15 交付说成整个 B 或 OA 全链路完成。

D-069实际交付（2026-10-19）：BE-08已通过[PR #2](https://github.com/Noah-hope/RBAC-admin/pull/2)合入远程main，来源2199f0ef7b36a048845842c9803b08acf0f6612a，合并ca8518918bd668f48769134763a10a163e10612b。PR回查closed/merged=true，远程main SHA回查一致，合并树等于来源，本地main已同步；任务OWNER_ACCEPTED、mergedToMain=true、remotePrNumber=2，原任务分支及无关.codeartsdoer保留。远程检查条目0，不记为CI通过；沿用85＋136真实验证，本轮无Java/SQL或部署。B可更新main接入BE-17，A/BE-09仍等BE-17，不自动开新任务。下方待交付为历史。

D-069（2026-10-19）：A明确“提交PR并合并主线”，授权推送BE-08任务分支、创建main目标PR并合并、同步本地主线和交付记录，不因D-068此前未授权描述重复询问。先核对源分支/远程主线/已有85＋136验证证据，按D-051不指定独立评审人；真实PR及合并SHA在成功后记录，保留任务分支和无关.codeartsdoer。不部署服务器、不自动开始BE-09。

D-068（2026-10-19）：A在BE-08交接与登录说明后明确“验证通过，接下来做什么”，登记BE-08/1c0eb3c055fe267fd0ff69c37e5120de3951eca1为OWNER_ACCEPTED、待主线交付，沿用85离线＋136真实MySQL证据。本轮只同步确认/静态追踪并准备PR正文，不重跑Java/SQL，不把询问下一步视为推送、创建PR或合并授权。fetch核对远程main仍db1be16，BE-17仍PLANNED；建议先交付BE-08供B/BE-17等复用，BE-09仍须BE-17交付，不自动开始新任务。下方待A确认均为此前历史。

D-067（2026-10-19）：本会话A明确“开始开发BE-08”，已在codex/be-08-management-scope按既有设计/TDD实现四查询、当前单项任命/双版本解析和覆盖策略。复用已交付BE-10、Spring Data JPA只读仓储及原事务边界，people查询接入统一resolver，既有迁移/身份机制不变。修正两组旧测试夹具实体扫描后，完整85离线＋136真实MySQL通过，专属实例/账号/临时密钥已清理关闭，未操作已有库。实现、真实RED/GREEN及本机步骤见docs/05-detailed-design/BE-08-MANAGEMENT-SCOPE.md；BE-08保持IN_PROGRESS待A确认，不自行合并推送。D-066仅准备为此前状态，BE-10仍不代D验收；BE-09/27等不扩展实施。

D-066（2026-10-19）：本会话A要求拉取远程、同步本地并准备开发BE-08。已fetch并将main无冲突快进到db1be16，建立codex/be-08-management-scope；设计与测试顺序见docs/05-detailed-design/BE-08-MANAGEMENT-SCOPE.md。本轮仅开发准备，BE-08仍PLANNED，未改业务代码、未运行Java/SQL或推送。BE-10已交付main且可核对端口，D人工验收仍开放；历史会话D认领不覆盖本会话A。后续实现先按TDD，保留无关.codeartsdoer。

D-065实际交付（2026-10-19）：BE-10已全量实现（六个GET查询、组织/员工/任职/映射内部读写、受控导入基础DML及历史保护），最终81离线＋122真实隔离MySQL检查均通过。任务来源6bfae88c89d0bad4b1599787ea47e2107a3bbcf9已无冲突no-ff合入main/8f1db6927b6d82e1a352ad016a4c1c056a6c648a，推送成功且远程SHA回查一致；任务分支保留。BE-10为IMPLEMENTED/mergedToMain=true，负责人人工验收仍待明确确认，不伪造OWNER_ACCEPTED。A可核对已交付BE-10安排BE-08，其余任务按原依赖，不自动开新任务。下方待交付为历史。

D-065（2026-10-19）：用户明确要求“BE-10要全部实现，实现完还要实现合并和推送”。本轮授权补齐六个组织/人员GET查询及EmploymentWriter内部受控导入基础变更，完成设计/TDD、真实隔离MySQL及既有回归后合入main并推送，不因D-064旧范围重复询问。不自动认领BE-08/26/27/30；查询自身窄范围复核不代表A的通用范围任务完成，基础写端口不代表导入/跨域失效编排完成。负责人验收事实与实现/主线交付分别记录，实际提交与远程SHA在成功后补充。

D-064（2026-10-19）：本会话用户明确负责成员D，并要求优先完成A所需数据能力；历史用户认领A的记录保留，不将两次会话视为同一成员。本机IDE/JDK21、构建、MySQL双库V1～V3、DML账号权限及健康UP已获本会话用户输出/回报核对。优先实施BE-10中供A/BE-08使用的组织、员工及准确部门有效任职内部事实端口，详见docs/05-detailed-design/BE-10-PEOPLE.md。BE-10整项保持IN_PROGRESS；六个HTTP查询接口、EmploymentWriter和跨域失效仍开放，不将局部能力记为整项通过；A的正式依赖交付仍须核对负责人确认及共同主线。仅专属临时MySQL验证，不操作用户现有库；未授权合并/推送本次任务。

D-063实际交付（2026-10-19）：用户已明确授权代提PR并合并，BE-32已通过 https://github.com/Noah-hope/RBAC-admin/pull/1 合入远程main，合并609ee4bb017e46b42e43bcd8c2616d31ab410f5a，来源d5941c902712c92d4d8d8d7505bd6df4f5ceb139。PR状态MERGED及远程main SHA已回查，本地主线已同步、任务分支保留；BE-32保持OWNER_ACCEPTED且mergedToMain=true。下方待创建/待合并为此前历史；BE-08仍等D/BE-10，其余27项PLANNED、服务器BE-34未部署，本轮不自动开新任务。

D-063（2026-10-19）：用户明确“你能帮我提PR，然后合并吗”，已授权推送BE-32任务分支、创建指向main的PR并合并，再同步本地和交付记录；不再因D-061/062此前未授权描述重复询问。按D-051由A确认、不指定独立评审人，保留任务分支及无关本机文件。实际PR/合并SHA在成功后记录，不虚构已完成；真实服务器部署仍不在本轮范围。

D-062（2026-10-19）：用户 A 已明确“现在确认BE-32验证通过”，BE-32 登记 OWNER_ACCEPTED，接受实现 cc76ac9、人工验证记录27b9970，主线交付仍待办理。用户正在询问直接合并推送还是先提PR，本轮仅记录确认、准备PR说明与静态核对，不把交付方式咨询视为远程写入授权；未创建PR、合并或推送。D-051负责人自行确认、不设独立评审人的规则保持；BE-34真实服务器验收仍开放。下方D-061待确认为此前状态。

D-061（2026-10-19）：用户 A 已要求开始 BE-32，前置 BE-01/02/03/06 已交付 main，在 codex/be-32-bootstrap 上按设计/TDD 实现，80 离线＋88 真实隔离 MySQL 检查通过并已清理专属实例。独立管理员初始化不随普通应用启动，复用密码哈希、guard 和审计，首次改密且拒绝覆盖已有总管理员；部署使用同一 Java 代码/jar，只调整外置配置与启动方式，无需用户当前选择服务器模板。验证限专属临时 MySQL8.4，实际云端部署/恢复留 BE-34；BE-32 待 A 确认，不自行合并推送。详见 docs/05-detailed-design/BE-32-DEPLOYMENT.md 与 deploy/README.md。

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

D-057已记录用户A确认BE-06通过，主线交付仍待办理；D-058用户明确继续BE-07，允许在含已确认BE-06祖先的codex/be-07-accounts任务分支上连续实施，不虚构main已交付，也不自行合并推送。BE-07先设计/TDD并复用身份、事务、幂等和审计，完成后仍由A确认；其他团队依赖按实际主线交付核对。

D-059已记录A确认BE-07（本机健康UP及未登录401回报，结合既有75离线＋80真实MySQL证据）；BE-06/07均OWNER_ACCEPTED待主线交付，其他28项PLANNED。下一步建议交付主线，A可安排BE-32，BE-08仍须D/BE-10；未授权就不把建议当作新任务实施或合并推送。

D-060当前交付：用户已明确授权合并推送，BE-06/07已无冲突合入main/ff19d17并推送，远程SHA回查一致；上述待主线是此前历史。任务均OWNER_ACCEPTED、9个正式操作已交付，其余28项PLANNED；组员可pull共同main，后续任务仍按原依赖及本人明确实施指令推进，不自动开始BE-32。

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
