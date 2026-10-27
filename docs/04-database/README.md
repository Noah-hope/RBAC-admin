# 数据库设计入口

当前状态（2026-10-26）：[四页ER图](database-erd.drawio)已更新并完成原生渲染检查，展示43张不同表、44个跨页表节点和46条关系（45跨表直线、1同表自关联回线）。中文/英文表名、核心居中与辅助围绕已统一。数据库1.1、数据字典、DDL及字段约束保持；全量数据库静态报告仍为历史证据，最新图源验证边界见[VALIDATION.md](VALIDATION.md)。

与[当前数据流图](../01-requirements/README.md)的状态已同步：P1～P7描述业务处理，D1～D8描述共享逻辑存储，本目录ER及数据字典描述物理表与关系。八份逻辑存储会映射到多张表，四个ER页面按数据主题组织，均不直接等同于人员分工或代码模块数量。

2026-10-26 展示调整 D-123：各表新增明确中文表名并保留英文名、原说明与字段，如“员工表／employee”“后台账号表／admin_account”。45条不同表关系全部改为无拐点直线；按用户补充，同表自关联保留回折线（目录节点表1条），不增加引用框。四页统一生效，沿用核心居中、辅助围绕、宋体粗体和线宽3。

2026-10-26 展示调整 D-122：四页 ER 图改为核心表居中、辅助表围绕排列，表框按展示字段数量调整；统一宋体加粗，关系线宽3，核心表用较深浅蓝底和深蓝边框区分。表、字段、关系端点、N:1基数及虚实线含义保持不变。仅更新原生drawio及图源布局维护，不修改DDL、数据库元数据或业务代码；原生渲染检查见[验证记录](VALIDATION.md)。

2026-10-18实施修正D-044：两处CHECK引用ROW_NUMBER保留字补反引号，逻辑模型/约束含义和API不变。派生V1～V3已在独立MySQL8.4.8真实执行并验证基础约束；用户测试库后续已迁移成功；D-045补齐基础JPA/启动guard样例（44离线＋9隔离引擎检查通过），全部领域/跨域并发和业务未验收，见[持久化实施证据](../05-detailed-design/BE-03-PERSISTENCE.md)。下述“不执行”是原数据库设计阶段边界。

初始设计及评审记录（2026-10-17，Asia/Shanghai）：数据库设计版本1.1草案；需求V1.3；权威API [openapi.yaml](../03-api/openapi.yaml) 1.2.0，SHA-256 `9910ceafadea88d85793d00f7bb4e6e5673700a8565366a407185c4517e0cbdf`。原输入为`2b78430/API1.1.0`，正式评审起点`e3fa818`。

上述初始交付仅完成数据库设计，未在当时连接MySQL、执行DDL或创建ORM实现；并发场景为设计推演。正式需求—API—数据库评审修订6项缺陷，设计层面有条件通过，见[CONSISTENCY-REVIEW.md](CONSISTENCY-REVIEW.md)。后续实施记录与本次图源检查分别保留，静态设计和渲染检查不代替业务/引擎验证。

## 阅读与交付

| 文件 | 内容 |
| --- | --- |
| [TECHNICAL-BASELINE.md](TECHNICAL-BASELINE.md) | 基线、技能、MySQL/框架版本设计取值、官方能力依据 |
| [LOGICAL-MODEL.md](LOGICAL-MODEL.md) | 逻辑实体、基数、范式及快照边界，Mermaid ER表达 |
| [database-erd.drawio](database-erd.drawio) | 四页当前ER图，43表/44展示节点/46展示关系；中文及英文表名，跨表直线/自关联回线，已检查；子视图不代替完整外键清单 |
| [DATA-DICTIONARY.md](DATA-DICTIONARY.md) | 每张物理表的字段、类型、长度、空值、默认、键、检查及含义 |
| [INVARIANTS.md](INVARIANTS.md) | 数据库与事务各自保证的关键不变量、历史和删除策略 |
| [TRANSACTIONS.md](TRANSACTIONS.md) | 锁顺序、版本、状态、执行、幂等、导入及失败恢复 |
| [INDEXES.md](INDEXES.md)、[KEYS-INDEXES.md](KEYS-INDEXES.md) | 典型访问路径及完整键索引清单，顺序与成本 |
| [TRACEABILITY.md](TRACEABILITY.md)、[traceability.json](traceability.json) | UC—API操作—表/字段—事务/不变量追踪 |
| [schema.sql](schema.sql)、[immutability.sql](immutability.sql) | MySQL建表/外键与不可变触发器草案，仅文本 |
| [MIGRATION-VALIDATION.md](MIGRATION-VALIDATION.md) | 初始化、演进、后续真实MySQL验证计划及本轮静态复现 |
| [REVIEW.md](REVIEW.md)、[VALIDATION.md](VALIDATION.md) | 自查、未决、详细设计交接及实际验证边界 |
| [CONSISTENCY-REVIEW.md](CONSISTENCY-REVIEW.md)、[consistency-validation.json](consistency-validation.json) | 正式跨阶段评审、6项修订、25场景及12项静态回归 |

[schema_model.py](schema_model.py) 是表元数据与文档生成器；[schema-model.json](schema-model.json) 为其可检查导出。它生成字典、键索引、DDL和图源，不生成业务代码、不执行SQL。[erd_layout.py](erd_layout.py) 只维护四页ER图的核心布局、表框尺寸及避表连线。仅刷新图源可执行 `python -c "import sys; sys.path.insert(0,'docs/04-database'); import schema_model; schema_model.generate_diagram()"`。 [validate_design.py](validate_design.py) 只做静态检查。设计变化先修改元数据及对应人工说明，再生成并检查，避免维护三份互不一致的表结构。

## 业务边界

只管理功能权限。公司/平级部门/岗位表达组织、任职、管理边界与授权来源，无租户、数据范围、角色继承、直接用户授权或显式拒绝。后台账号、员工、管理员任命、管理范围和业务角色分开。角色配置、分配与一般撤销经 OA；关原部门来源直接生效；员工恢复不复权。公司级共享角色仅总管理员维护，普通员工不登录权限中心。

一份固定申请全部执行或全部失效/回滚。原申请人离任不单独阻断执行；原范围曾实质不再覆盖或来源周期推进后，后来恢复也不复活旧申请。权限管理员每次只选一项任命，不拼接范围。JWT每请求校验持久化当前状态。

## 初始设计阶段的工具与交付历史

开始时工作区干净，`codex/api-design` 与 `2b78430` 无差异。原地新建并切换 `codex/database-design`，未建worktree、未合并main。Git元数据在沙箱只读，分支/提交操作使用受控权限提升。

当时沿用Apifox指定客户端导出核心一致的既有结论，MCP暴露差异保留，见[APIFOX.md](../03-api/APIFOX.md)；正式数据库、后端及真实OA尚未实现，设计作为后续详细设计输入。此段记录初始交付边界，不覆盖后续主线实现、测试或部署事实；当前状态见[共享记忆](../project/MEMORY.md)。
