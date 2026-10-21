# BE-18 有效权限与单项检查

B 实际分支交付（2026-10-21/D-097）：已推送 codex/be-33-35-integration-handoff，远程完整 SHA 回查为 a5d5c3557b6da7cb8f52ea42c30ba03b5fcb18a3，包含 BE-18、集成修复/测试和交接证据；远程 main 仍 d79e28a624fd4b9bf3862063c44274d3db602248，未合并或推送 main。完整139＋375及18辅助检查通过，专属资源清理关闭；本条后续仅补交付文字。推送分支不等于主线交付、本人验收或 BE-33/35 完成；A/BE-27、D/BE-30、A/BE-34 及完整验收仍开放。下方准备推送/未授权为历史。

当前交付（2026-10-21/D-097）：用户已要求“那你就弄完提交”，本实现由 codex/be-33-35-integration-handoff 一并接入 main/d79e28a 后完整验证并提交推送独立分支，不合并远程 main。原 BE-18 决策 D-091 后协调为 D-093，现因 D/BE-26 已交付编号重叠统一为 D-095；历史提交不改。最新结果与实际远程交付回查见 [B 集成交接](BE-33-35-INTEGRATION-HANDOFF.md)，下方仅本地/未授权推送为此前历史，负责人确认仍不代记。

2026-10-21：当前 B 用户在拉取新主线、说明 BE-14 已交付后要求“那就改”，按本轮下一步实施 BE-18。基线 main/7914788，分支 codex/be-18-effective-permissions。仅本地实现与验证，不扩展上一轮 BE-17/12 的推送/合并授权，不代记负责人验收。

## 原设计与验收顺序

沿用 UC-05/06、API1.2.0 的 authorizationQuery/permissionCheck、主库 T11、新快照 ReadBoundary 及 BE-06 当前业务服务身份。query/authorization 独立 SQL/DTO，无角色/目录命令依赖，不改契约、迁移或身份过滤器。

每次请求在一个新的主库 RR 事务中复核当前服务身份及绑定系统，系统停用 403；忽略不了非法 systemId 等未知输入字段。未知公司/员工、停用员工或无适用任职正常 false，故障不得伪装无权限。读取完整当前公司/绑定系统来源及永久关系版本，要求 ACTIVE grant 的 sourceEpoch/relationVersion 当前匹配；角色 EFFECTIVE 且配置版本有效，来源部门和部门角色所属部门分别须有当前有效任职，角色权限合并去重、不截断 100 项。

SYSTEM_ACCESS 独立决定进入资格，不混入 operations；功能必须同时具备当前公司/系统进入资格。操作独立，不因编辑推导查看。菜单仅按已授予的绑定查看权限显示，必要祖先可作 containerOnly，不泄露兄弟功能。功能及任一祖先停用过滤子树；缺失/跨系统/循环/非法菜单绑定等目录损坏返回 500，不静默拼树。超过响应契约上限不截断成功。单项检查与完整查询共用同一计算，未知编码 false/type=null，合法但未授予/停用的编码保留类型。

先写真实 HTTP/MySQL 测试并观察未实现路由断言 RED；再实现两个 POST，覆盖多来源并集、独立菜单/操作、系统/公司/来源隔离、当前身份与参数、撤权后新查询、双连接一致快照、完整性/存储错误及大集合。完整后端回归和设计静态校验分别记录；只使用专属隔离 MySQL，不操作已有库。本机未验收、OA/后端全链路未完成不得记为通过。

## 实际实现与接入

`query/authorization/AuthorizationService` 提供独立 SQL/DTO 计算，现有 QueryConfiguration 仅增加装配；`web/authorization` 实现下列两个入站操作，不改同学已交付的 grant/change/imports 业务逻辑。依据数据库事实直接投影，不依赖域实体/命令，不修改授权、审计或幂等数据。

- `POST /api/v1/integration/authorization-queries`：JSON 为 `{"employeeKey":"实际员工统一标识","companyId":"实际公司ID"}`。
- `POST /api/v1/integration/permission-checks`：同上，再加 `"permissionCode":"实际权限编码"`。

使用已登记且启用的 BUSINESS 服务身份及原可信 RSA/issuer/audience 配置；调用方持有签名私钥，服务端仍仅配置原业务验证公钥。不能使用后台登录 token 或 OA token。不新增环境变量、密钥类型、依赖或迁移。systemId 只取服务身份，输入禁止 systemId、ownerId、recordId、page 等额外字段；两接口均只读、不要求 Idempotency-Key，成功响应 no-store，回显当前请求编号与 UTC evaluatedAt。

SYSTEM_ACCESS 通过 canAccessSystem 单独表达，operations 只含功能操作；菜单按 sortOrder/code/id 稳定父先子后输出，必要功能祖先即使没有自己的查看授权也仅 containerOnly=true。当前目录/操作停用过滤，不结束 grant；恢复仅使仍当前有效的关系可参与计算。校验 ACTIVE grant 的不可变任职证据存在且归属准确，但是否当前合格取当前任职（不是授予时历史）。部门角色归属与来源部门可不同，两处都需当前任职。

全量查询不分页、不按 100 项截断，菜单/操作超过契约各 10000 项上限时返回 503/DEPENDENCY_UNAVAILABLE，不返回部分成功。单项检查不受完整响应数组上限影响，仍按全部有效来源判断，未知编码 false/type=null、已知但不满足条件 false/保留类型。目录损坏或合法当前 ACTIVE grant 缺少任职证据返回 500/DATA_INTEGRITY_ERROR，数据库连接故障通过现有 ReadBoundary 翻译为 DEPENDENCY_UNAVAILABLE，不伪造 false。未实现缓存或外部业务登录/页面。

## 真实 TDD 与验证

使用项目 test-driven-development 技能及 writing-good-tests 指引；日志仅在 backend/.tmp 忽略目录，长期可重跑测试在 AuthorizationMySqlCheck。

| 日志 | 实际结果 |
| --- | --- |
| be18-http-red.log | 8 项失败、0 错误；两个路由尚未实现，实际 404，与期望 200/403/422 不符。真实缺失功能断言 RED |
| be18-http-green.log | 原 8 项真实 HTTP/数据库检查全部通过 |
| be18-boundaries.log | 16 项中 15 通过、1 夹具错误：将角色写为不存在的 DRAFT 枚举，被数据库 CHECK 拒绝。更正为既定 UNCONFIGURED 后重跑；不把此错误冒充生产 RED |
| be18-limit-red.log | 17 项中 1 断言失败、0 错误：超过全量响应上限后，合法单项检查应 200 实际 503；修正上限仅约束完整响应 |
| be18-final-verify.log | 完整 clean verify/mysql-check：137 普通＋301 真实 MySQL/HTTP 检查，失败/错误/跳过均 0；新增 17 项全部通过 |

最终完整构建于 **2026-10-21 10:57:50（Asia/Shanghai）** 通过，耗时 1 分 59 秒。命令为 backend 内使用显式 JDK21/Maven，配置 RBAC_ISOLATED_MYSQL_PORT=13461 和专属 backend/.tmp/mysql-b-isolated 后运行 `clean verify -Pmysql-check`。jar SHA-256：`0f2a09990dc7be5df6d4de245fc922d4ad2bbe84beb9ea0c4306945c961f70c8`。未通过删减旧测试获得通过，包含同学最新已交付模块的全部 284 项真实检查。

新增场景：完整 121 操作无 100 项裁剪、10001 操作全量不截断而单项仍可查、独立进入/编辑/查看及去重、跨来源并集与关闭一个/最后一个来源、准确来源与角色两部门任职、员工停用/未配置角色、未知员工/公司/编码、跨公司/系统绑定、系统停用与服务身份撤销、输入字段及 JWT 家族拒绝、祖先停用与必要父功能容器、no-store、目录循环与缺失历史证据的 500、SQL 连接故障不变成 false、两连接提交期间同一 RR 快照/后续请求立即反映新状态。故障连接用测试端 DataSource 注入异常，其余业务数据为真实 MySQL；快照测试仅在测试包装层协调两个连接，无生产测试钩子。

申请/OA/执行记录仅是遵循既有触发器建立有效 grant 的受控持久夹具，不代表 C 的真实 OA/执行器已完成。真实人工接入验收未进行，BE-33/35 未完成；AT-07/10/30/33/37 仅补本任务证据，不代整个端到端验收。本轮仅本地任务分支提交，不推送或合并 main，仍待 B 本人确认和共同主线交付。

收尾已回查专属实例随机测试库 0、测试账号 0，授权测试临时签名密钥由夹具清理；13461 专属 MySQL 已正常关闭，忽略目录保留专属实例自身数据/证书与日志，不提交。构建 jar 包含生产服务/控制器，不含测试夹具或私钥。设计静态校验 PASS，生成器确定性和原用例/契约/迁移摘要保持；静态校验不运行 SQL/HTTP。项目 TDD 技能决定了上述设计先行、真实 RED/GREEN 与完整隔离回归顺序。
