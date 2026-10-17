# API契约验证记录

## 正式跨阶段修订验证（1.2.0，2026-10-17）

在codex/database-design/e3fa818输入上开展正式需求—API—数据库评审，6项设计缺陷已修订，设计层面有条件通过。报告及兼容性见[正式评审](../04-database/CONSISTENCY-REVIEW.md)，完整实际命令与工具边界见[当前验证记录](../04-database/VALIDATION.md)。

`validate_contract.py --baseline`实际通过：67入站/56路径/1出站/129模型、1142引用、120模型示例、789媒体示例、原18项及API评审25项Schema场景、19组联合/467对互斥分支、269d269首版10项缺陷复现。固定Redocly2.8.0 lint通过，无错误/告警。本次另有8项跨阶段Schema场景及4项数据库CHECK表达式静态推演，见[consistency-validation.json](../04-database/consistency-validation.json)；不是业务执行或数据库验证。

权威契约1.2.0 SHA-256：`9910ceafadea88d85793d00f7bb4e6e5673700a8565366a407185c4517e0cbdf`。Apifox1.2.0尚未人工同步，原1.1.0指定导出及MCP差异记录保留。本轮没有运行Prism、真实服务/数据库/SQL/并发/JPA或OA集成，不能沿用旧Mock结果宣称新版业务正确。

## 1.1.0单阶段评审验证历史

日期：2026-10-16（Asia/Shanghai）。基线269d269；分支codex/api-design，开始时干净，沿用当前目录、不建worktree、不合并main。本轮只评审并修订API，报告见[REVIEW.md](REVIEW.md)。

## 本轮实际结果

| 检查 | 结果 |
| --- | --- |
| 结构、引用与追踪 | OpenAPI3.1、67个入站操作/56路径、1个出站webhook、129模型；1142处引用及UC-01～11追踪通过 |
| 示例和反例 | 120个模型示例、789处媒体示例、原18项及新增25项Schema场景通过 |
| 首版缺陷复现 | --baseline从Git269d269复现10个非法输入被旧Schema接受，修订版全部拒绝 |
| 联合分支互斥 | 18组对象联合、466对分支均可由必填字段枚举/类型证明互斥，无未证明对 |
| 固定Redocly2.8.0 | 0错误、0告警 |
| 额外Redocly2.64.1 | 0错误、55条no-illogical-composition-keywords启发式重叠告警；独立域检查证明分支互斥；保留告警记录，未关闭规则或削弱约束 |
| Prism5.14.2 | 仅127.0.0.1:4010；67项请求/成功响应、5项非法输入、1项409 Problem检查通过；Mock已停止 |
| Apifox | 首尾刷新、根文档及三项组件引用读取成功；远程模块仍0接口/空组件；未导入新契约，未宣称同步完成 |
| 文档与提交前检查 | 85个本地链接有效、两份维护脚本语法通过、31条决策编号唯一、MEMORY无本机绝对路径、契约SHA匹配及Git差异格式通过 |

契约SHA-256：`ec7ef9ef1ab046bb61cec9728c423c32f58311c850860be8ef54a9cf3a4798fd`。

新版Redocly对嵌套oneOf及null/字符串引用分支仍报告重叠，固定版及独立JSON Schema校验通过；未用忽略配置掩盖。修订先采用条件组合时也曾暴露工具示例预处理差异，最终改为逻辑等价的完整互斥分支和单值enum。这个调整保留严格未知字段拒绝、非法状态/来源拒绝及正反例，不是为lint删需求。

本轮Mock首次调用早于监听就绪，连接被拒绝；待监听后完整重跑通过。没有将暂态启动失败当成业务失败，也没有将Mock成功当成真实认证、权限、OA执行或事务测试通过。正式后端仍未实现。API评审有条件通过；人工导入/完整回读后按项目顺序交接数据库阶段，本轮未开展数据库设计。

## 复现

依赖版本及临时venv安装方法沿用下方首版记录（PyYAML6.0.3、jsonschema4.26.0、openapi-spec-validator0.7.2）；本轮使用先前临时目录的同版本工具，无项目依赖或环境文件提交。

```powershell
python docs/03-api/validate_contract.py --baseline
npx --yes @redocly/cli@2.8.0 lint docs/03-api/openapi.yaml --format=summary
npx --yes @redocly/cli@2.64.1 lint docs/03-api/openapi.yaml --format=summary
# 一个终端启动Mock，另一个终端运行检查，完成后停止Mock
npm exec --offline --yes --package @stoplight/prism-cli@5.14.2 -- prism mock docs/03-api/openapi.yaml --host 127.0.0.1 --port 4010
python docs/03-api/mock_check.py
```

离线Mock命令要求已有npm缓存；未缓存时先按下方npx命令下载固定版本。--baseline要求本地Git仍可访问269d269；普通校验不依赖该历史提交。首次确认条件Schema错误的原始值与修订版输入分开保存，避免新增submittedAt掩盖旧缺陷。

## 首版交付历史（269d269 / 1.0.0）

以下是首版已发生的验证记录，其数量和契约摘要不代表本次1.1.0。

### 首版 API 设计验证与待办

日期：2026-10-16（Asia/Shanghai）；需求V1.3；契约版本1.0.0。起点Git `d032a95`，工作区干净，从main创建并切换`codex/api-design`，未创建worktree、未合并main。交付是本地API设计与设计自查，未开展数据库设计或后续跨阶段一致性评审，不代表用户已逐项评审契约。

## 交付与校验结果

- OpenAPI3.1：55个入站路径、66个操作，另1个出站OA webhook；119个模型。覆盖UC-01～UC-11及R-01～R-03业务语义。
- `@redocly/cli@2.8.0 lint`：通过，0错误、0告警。
- `openapi-spec-validator==0.7.2`：结构校验通过；所有1091处本地引用可解析。
- `validate_contract.py`：117个模型示例、780处HTTP媒体示例、18项正反JSON Schema场景通过；operationId唯一，路径参数/分页/三类认证方案/范围上下文版本头/UC覆盖及接口追踪检查通过。
- Prism `@stoplight/prism-cli@5.14.2` 仅绑定`127.0.0.1:4010`：66个操作逐项HTTP请求和成功响应模型检查通过；5项非法输入（缺员工、业务数据范围字段、非最终回调、非法页码/条数）被拒绝；1项文档化409 Problem响应检查通过。检查后停止Mock服务。
- 79个本地链接全部有效，两份维护脚本Python语法检查通过，提交前Git差异格式检查通过。契约工具环境未提交。

OpenAPI文件SHA-256：`29a338f0bfa999530b07df0b1e85afb2ec1a4358795f139c2a0d169eb7d68e7a`。

以上是契约结构/示例和Mock检查。Prism不会实现当前账号/任命校验、JWT受众隔离、幂等、审批状态机、数据库唯一性/事务或真正的OA自动执行。没有将Mock返回200/202写成业务验收通过；尚未运行正式后端TDD测试。原型没有修改，不重复运行其构建或浏览器测试。

## 可复现命令

在独立临时环境安装工具，避免修改项目原型依赖：

```powershell
python -m venv "$env:TEMP/rbac-api-validation-env"
& "$env:TEMP/rbac-api-validation-env/Scripts/python.exe" -m pip install PyYAML==6.0.3 jsonschema==4.26.0 openapi-spec-validator==0.7.2
& "$env:TEMP/rbac-api-validation-env/Scripts/python.exe" docs/03-api/validate_contract.py
npx --yes @redocly/cli@2.8.0 lint docs/03-api/openapi.yaml --format=summary
```

一个终端启动Mock，另一个终端运行检查，完成后停止Mock：

```powershell
npx --yes @stoplight/prism-cli@5.14.2 mock docs/03-api/openapi.yaml --host 127.0.0.1 --port 4010
& "$env:TEMP/rbac-api-validation-env/Scripts/python.exe" docs/03-api/mock_check.py
```

本轮实际通过临时目录`pip --target`加载同版本Python依赖，不向仓库提交工具环境或生成正式工程；上列venv命令是其他成员的等价隔离复现方法，不冒充本轮实际创建过venv。api-designer已读取并核验，当前全局可用版本1.1.0（Jeffallan/claude-skills），项目没有同名技能副本；未修改技能文件。

## 设计自查与业务未决

| 项目 | 结论 |
| --- | --- |
| 原型限制 | 未沿用模拟批准/执行按钮、128KB/六列CSV、内存角色编号；未修改原型 |
| 权限边界 | 一个指定部门、不合并任命；共享角色仅总管理员维护；原范围执行、独立来源、恢复不复权、无业务数据权限 |
| 请求一致性 | 写入防重、草稿/配置/关系/周期版本分开；无变化与失败/待执行分开；批准后批量不截取子集 |
| 外部入口 | 管理员、业务系统、OA回调分离；服务端出站OA适配器协议；实际对接/密钥未提供、未接通 |
| R-01～R-03 | 员工影响受限预览、批量一般撤销、目录同父级稳定排序已有契约；原型补齐继续按D-029跟踪 |
| Apifox | 启动和收尾refresh均成功；收尾下载时间2026-10-16T09:06:16.900Z（北京时间17:06），默认模块仍0接口；read空模块成功；本地契约未导入，不能宣称同步完成 |
| 数据库交接 | 仅持久化、唯一性、历史、版本、事务要求；未设计物理表/索引DDL/JPA实体 |

本轮未发现必须新增业务含义才能继续设计的阻塞问题，未重新开启全部需求访谈。沿用已知非阻塞问题：原材料“全部”、空白I340、“系统管理后台”、本人限定来源的初始化映射仍待核对；不生成未知宽泛授权，不影响通用契约。

待办与边界：

1. 人工导入Apifox8934976默认模块，MCP刷新/读取全部引用并做语义核对；平台webhooks/3.1兼容性未实测，记录在APIFOX.md，不能标记已同步。
2. 用户/团队评审契约设计取值（JWT期限、批次/分页上限、错误及字段约定）；它们是本轮技术设计，不伪称此前用户逐项确认。若无业务变化，可在同主版本内按兼容性规则调整；破坏性变化要有迁移说明。
3. 后续数据库阶段设计物理结构/约束/事务并回查交接场景；随后再开展用户安排的一致性评审，本轮不提前宣布完成。
4. Spring Boot/MySQL具体版本、密钥/签发方/真实OA适配地址、文件与审计保留/归档、部署恢复和正式模块测试框架留后续技术设计；不影响本地契约结构定稿。

重要技术依据链接见CONVENTIONS、CONSISTENCY和APIFOX；业务依据始终是正式详细用例。
