# API 设计验证与待办

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
