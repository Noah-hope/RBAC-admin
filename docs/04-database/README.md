# 数据库设计入口

日期：2026-10-17（Asia/Shanghai）。数据库设计版本：1.0 草案交付；需求 V1.3；权威 API [openapi.yaml](../03-api/openapi.yaml) 1.1.0，输入提交 `2b78430`，SHA-256 `ec7ef9ef1ab046bb61cec9728c423c32f58311c850860be8ef54a9cf3a4798fd`。

本次只完成数据库设计。没有启动 MySQL、连接任何数据库、执行 DDL、迁移资料、创建 ORM 实体或应用工程。并发场景均是设计推演。数据库阶段自查不表示后续正式跨阶段一致性评审已经完成。

## 阅读与交付

| 文件 | 内容 |
| --- | --- |
| [TECHNICAL-BASELINE.md](TECHNICAL-BASELINE.md) | 基线、技能、MySQL/框架版本设计取值、官方能力依据 |
| [LOGICAL-MODEL.md](LOGICAL-MODEL.md) | 逻辑实体、基数、范式及快照边界，Mermaid ER表达 |
| [database-erd.drawio](database-erd.drawio) | 四页原生可编辑 ER 图源；图中子视图不代替完整外键清单 |
| [DATA-DICTIONARY.md](DATA-DICTIONARY.md) | 每张物理表的字段、类型、长度、空值、默认、键、检查及含义 |
| [INVARIANTS.md](INVARIANTS.md) | 数据库与事务各自保证的关键不变量、历史和删除策略 |
| [TRANSACTIONS.md](TRANSACTIONS.md) | 锁顺序、版本、状态、执行、幂等、导入及失败恢复 |
| [INDEXES.md](INDEXES.md)、[KEYS-INDEXES.md](KEYS-INDEXES.md) | 典型访问路径及完整键索引清单，顺序与成本 |
| [TRACEABILITY.md](TRACEABILITY.md)、[traceability.json](traceability.json) | UC—API操作—表/字段—事务/不变量追踪 |
| [schema.sql](schema.sql)、[immutability.sql](immutability.sql) | MySQL建表/外键与不可变触发器草案，仅文本 |
| [MIGRATION-VALIDATION.md](MIGRATION-VALIDATION.md) | 初始化、演进、后续真实MySQL验证计划及本轮静态复现 |
| [REVIEW.md](REVIEW.md)、[VALIDATION.md](VALIDATION.md) | 自查、未决、详细设计交接及实际验证边界 |

[schema_model.py](schema_model.py) 是表元数据与文档生成器；[schema-model.json](schema-model.json) 为其可检查导出。它生成字典、键索引、DDL和图源，不生成业务代码、不执行SQL。[validate_design.py](validate_design.py) 只做静态检查。设计变化先修改元数据及对应人工说明，再生成并检查，避免维护三份互不一致的表结构。

## 业务边界

只管理功能权限。公司/平级部门/岗位表达组织、任职、管理边界与授权来源，无租户、数据范围、角色继承、直接用户授权或显式拒绝。后台账号、员工、管理员任命、管理范围和业务角色分开。角色配置、分配与一般撤销经 OA；关原部门来源直接生效；员工恢复不复权。公司级共享角色仅总管理员维护，普通员工不登录权限中心。

一份固定申请全部执行或全部失效/回滚。原申请人离任不单独阻断执行；原范围曾实质不再覆盖或来源周期推进后，后来恢复也不复活旧申请。权限管理员每次只选一项任命，不拼接范围。JWT每请求校验持久化当前状态。

## 工具和阶段事实

开始时工作区干净，`codex/api-design` 与 `2b78430` 无差异。原地新建并切换 `codex/database-design`，未建worktree、未合并main。Git元数据在沙箱只读，分支/提交操作使用受控权限提升。

Apifox人工导入及指定完整路径覆盖导出核心一致的既有结论沿用；MCP暴露差异仍在，不重新排查、不改权威契约，见 [APIFOX.md](../03-api/APIFOX.md)。正式数据库、后端及真实OA仍未实现。本次设计可作为详细设计输入；下一阶段先正式评审跨阶段一致性及本目录交接事项。
