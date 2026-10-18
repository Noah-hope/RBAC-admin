# 数据库设计验证记录

## 实现阶段D-044修正（2026-10-18）

schema.sql两处CHECK对ROW_NUMBER补反引号，逻辑模型/字段/约束含义不变；生成器同步。数据库静态检查重新运行通过，报告只说明该检查未执行SQL。另有独立8.4.8实例的真实Flyway/基础约束证据，见[BE-03验证](../05-detailed-design/BE-03-MYSQL-VALIDATION.md)，不能把下文历史“不执行”或静态报告的false字段误当作后续始终未运行引擎。JPA和完整并发仍未通过。

## 正式跨阶段评审修订验证（数据库1.1 / API1.2.0）

日期：2026-10-17；起点codex/database-design/e3fa818且工作区干净。正式结论、6项问题和25个文档场景见[CONSISTENCY-REVIEW.md](CONSISTENCY-REVIEW.md)。没有建库、连接数据库、执行SQL或业务实现。

- API：OpenAPI3.1结构、1142引用、67入站/56路径/1出站/129模型、120模型示例/789媒体示例、原18项＋API评审25项场景全部通过；19组对象联合、467对分支证明互斥。`--baseline`另复现269d269的10项旧非法输入被接受，和本次e3fa818反例不同。
- 本次跨阶段静态回归：[validate_consistency_review.py](validate_consistency_review.py)的8项JSON Schema场景＋4项有限CHECK表达式推演通过，精确检查15项无管理员幂等头例外、68项读写/幂等追踪、字段模型名称和本地Apifox覆盖派生配置。见[consistency-validation.json](consistency-validation.json)。C01/C04/C09/C11分别展示原契约或原CHECK与修订后的接受/拒绝差异；不是业务服务测试。
- 固定Redocly CLI2.8.0本地lint通过，无错误/告警。未重复Prism Mock或尝试真实HTTP业务流程；Mock即使通过也不能证明本次授权/事务正确。
- DDL元数据及MySQL方言静态解析：43表/356列/79外键/124条DDL、41触发器文本块、原20项CHECK表达式场景通过；当前SHA及完整计数见[static-validation.json](static-validation.json)。另4项HTTP幂等CHECK推演记录在跨阶段报告，不混计为引擎测试。全部字段/键/外键/索引/追踪/本地链接检查通过；图源与原先一致。
- [validate_handoff.py](validate_handoff.py)检查需求同步清单、历史预览保留、需求图源、Python语法、决策编号和共享记忆；实际数量见[handoff-validation.json](handoff-validation.json)。正式需求未变，无需重新生成图或阅读副本。
- 依赖只在隔离临时目录：PyYAML6.0.3、jsonschema4.26.0、openapi-spec-validator0.7.2、sqlglot27.28.1。首次普通沙箱pip因DNS失败、Redocly离线因未缓存失败；受控提升下载固定版本后实际重跑成功，没有把初次失败记成通过。未修改应用依赖或提交工具环境。
- Git差异检查使用`git -c core.whitespace=cr-at-eol diff --check`及暂存区同等命令；保留旧文件原换行风格。只有本轮相关设计/验证/共享文档纳入提交，原型和正式工程不变。

权威API1.2.0 SHA-256：`9910ceafadea88d85793d00f7bb4e6e5673700a8565366a407185c4517e0cbdf`。Apifox既有1.1.0导出仅是历史证据，当前1.2.0待人工同步；本次未重查远程或MCP，没有宣称同步完成。

复现：先按API目录原隔离安装方法准备固定依赖；本次实际通过临时PYTHONPATH加载，不创建仓库venv。

```powershell
python docs/03-api/validate_contract.py --baseline
python docs/04-database/validate_consistency_review.py --baseline e3fa818
python docs/04-database/validate_design.py
python docs/04-database/validate_handoff.py
npx --yes @redocly/cli@2.8.0 lint docs/03-api/openapi.yaml --format=summary
```

以上只做结构/引用/Schema和有限表达式静态检查；25个业务场景是设计推演。**没有真实MySQL触发器编译、并发、JPA、故障恢复、SQL计划/性能、JWT或OA/HR集成通过结论。** 这些不再列为“尚未正式评审”，而是正式设计评审后的实现验证待办。

## 数据库1.0首次交付验证历史

日期：2026-10-17（Asia/Shanghai）。开始时`codex/api-design`、HEAD `2b78430`、工作区干净；与指定API分支/提交无差异。原地建立`codex/database-design`，没有worktree，没有合并main。

## 实际工具事实

- 已读取项目入口/共享记忆/流程/决策/术语/目录、docs导航、需求总览/管理规则与UC-01～11，以及API全部设计说明和权威OpenAPI1.1.0。原型仅作既有交互输入，未修改或沿用Mock限制。
- 无项目erd-schema-designer/drawio副本，读取可用全局技能与drawio XML规范。业务单点澄清按项目grill-me→grilling处理，不重开全量访谈；Q-01依据见REVIEW和D-033。
- `mysql --version`与`mysqld --version`均返回MySQL8.4.8 Win64；仅查询二进制版本。Docker守护进程未运行，draw.io Desktop全链路定位未找到。
- 用户明确只要设计后，没有启动临时MySQL、连接数据库或执行SQL；没有真实并发、查询计划、JPA、OA/HR集成测试。没有应用工程、实体或业务实现。
- 静态工具隔离安装PyYAML6.0.3、sqlglot27.28.1，通过临时PYTHONPATH加载，没有改项目/原型依赖。早先准备的PyMySQL未使用。普通沙箱网络安装失败，改用受控提升安装成功；无凭据输出或入Git。

## 静态检查

完整机器可读结果见[static-validation.json](static-validation.json)。使用[validate_design.py](validate_design.py)，只读取设计文件并输出静态报告，不执行SQL。

检查范围包括MySQL方言DDL语法树（无未知Command回退）、模型/DDL表列一致、显式复合候选键与FK类型/索引前缀、命名与索引字节上界估算、不可变触发器文本块、有限CHECK三值逻辑场景、67入站+1出站操作追踪、UC覆盖、本地链接、四页图源XML/ID/连线，以及权威契约SHA保持不变。

触发器仅做文本结构/关键保护检查，**未提交MySQL编译**。CHECK场景是有限表达式推演，不模拟SQL mode、字符集/引擎或触发器。索引字节估算不等于实际页容量验证。图源XML有效不表示桌面布局或客户端导入已验收。DDL解析成功不等于业务一致性正确。

| 实际检查 | 结果与边界 |
| --- | --- |
| 表/列/外键 | 43表、355列、79条显式FK；全部引用完整主键/显式唯一候选键，类型及左前缀索引检查通过 |
| DDL解析 | sqlglot MySQL方言解析124条语句（2 SET、43 CREATE TABLE、79 ALTER），无Command未知回退；未提交引擎 |
| 触发器 | 40个文本块，名称唯一/长度/块闭合及必要保护检查通过；未验证MySQL编译/执行 |
| CHECK推演 | 20项有限表达式场景：七合法状态、非法三轴/缺固定依据/NULL、JSON顶层形状、超出安全版本及失效迟到驳回；不是MySQL测试 |
| 追踪与引用 | 全部67入站+1出站操作精确覆盖，UC-01～11、表/T/I编号存在；本地链接有效，数量见机器报告 |
| ER图源 | 4页、44个实体出现（员工跨页重复、共43个不同实体）、46条关系/协调线；XML/唯一ID/端点通过，未桌面视觉验收 |
| 需求同步 | 实际默认刷新成功，227阅读文件及原始源/副本摘要一致；114份需求drawio/229图页XML、ID及连线端点检查通过，含原图有意使用坐标的辅助线 |
| 预览保留 | 196个现有PNG/SVG与2b78430逐字节一致，未生成新图片 |
| 协作文件 | 33条决策编号唯一、15份相关Python语法通过、MEMORY无本机绝对路径；无原型/应用代码变更 |
| Git格式 | 保留原生成文档CRLF，工作区及暂存区使用`git -c core.whitespace=cr-at-eol diff --check` / `diff --cached --check`通过；修正两份新生成文档末尾空行，无设计语义变化 |

需求同步/预览/图源与共享记忆结果见[handoff-validation.json](handoff-validation.json)，可用[validate_handoff.py](validate_handoff.py)重现；初始检查对同步清单生成索引无source、时序辅助线无实体端点的假设已修正，未靠改图结构隐藏检查问题。

## 尚未验证

MySQL8.4真实建表/触发器/唯一与CHECK行为、所有事务隔离与并发先后、故障注入/恢复、真实SQL查询计划/性能、JPA生成列/UUID/@Version/flush与回滚、JWT即时撤销、真实OA/HR、本机draw.io客户端呈现，以及后续正式跨阶段一致性评审。具体后续矩阵见[MIGRATION-VALIDATION.md](MIGRATION-VALIDATION.md)。
