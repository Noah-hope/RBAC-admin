# B：系统登记、角色读取与配置应用

实际主线交付（2026-10-20/D-072）：本任务三项已由 b92cdbb 合入远程 main/0e391c6，远程 SHA 回查一致，完整 85＋150 和静态校验通过。管理上下文复用 BE-08 并拒绝重复头；B 分支保留，负责人验收仍开放。见 [交付记录](B-MAIN-INTEGRATION.md)；下方原实施及 D-071 阶段记录按历史理解。

当前补充（2026-10-20/D-071）：原实现 abc5cf2 已推送 B 分支；现接入 main/5b695a7，管理上下文已复用正式 BE-08，完整 85＋149 检查通过。BE-17 的 BE-08/10 技术依赖现已就绪。详见 [集成记录](B-MAIN-INTEGRATION.md)。下方为原实施设计和当时验证，不覆盖此次接入事实；原 B 决策编号 D-061 调整为 D-070，避免与 A 冲突。

2026-10-19。用户授权按现有计划实施 B，分支 `codex/be-11-13-15-b-module`，基线 `9857e5e`。不推送或合并 main。

## 范围与依赖

BE-11 → BE-13 → BE-15；沿用 UC-01/02/11、API 1.2.0、数据库 1.1 和现有迁移。BE-17 缺 BE-08/10，BE-12 等 BE-17，BE-18 缺 BE-10/14，均保留开放，不代做 A/C/D 模块。

系统登记以总管理员当前资格执行，guard 内创建停用系统及唯一 `code:access`，审计与安全幂等结果同事务。列表与详情使用主库新 RR 快照，缺进入权限是完整性错误。排序 `createdAt DESC,id DESC`，先过滤后计数分页。

角色只修改名称和说明，固定公司/部门/系统与编码不变；metadataVersion 与 configurationVersion 独立。当前 permissionIds 从有效配置表读取，activeChangeRequestId 从 claim 读取，两者不混淆。相同元数据不推进版本、不新增变更审计，仍检查预期版本。

B 的管理读取适配器只查询既有任命/范围/任职事实，不提供 BE-08 的范围管理 API。每次在本次事务中重新校验身份，权限管理员必须选择单项任命并提供两个版本，禁止拼接；角色查询可见共享角色，维护共享角色仅总管理员。显式范围外列表筛选 403，不可见详情统一 404。元数据幂等回放仍复核当前范围及原目标。

配置应用器无 HTTP 入口，只接受有效 `WriteContext`，不新开事务。C/BE-25 负责固定申请解析、批准与原范围复核、执行成功凭据、跨域失效、审计及 claim 释放。B 负责基线配置版本、最终非空、完整引用/同系统/功能归属，按当前集合与最终集合计算新增/保留/移除；新增要求系统、权限、功能及全部祖先有效，保留停用项和移除停用项合法。替换当前集合、推进配置版本、首次 EFFECTIVE、保存完整历史同事务；元数据版本不变。失败必须回滚整个调用事务。

## 验证计划

测试先于实现：真实 HTTP 验证登记/读取/改名及权限、分页、幂等、版本、未知字段；真实 MySQL 验证唯一槽、审计失败回滚和应用器整批回滚。测试使用专属非 3306 端口、独立 datadir 与随机 schema/账号，沿用 IsolatedMySqlDatabase，不访问已有开发库。构建执行完整 `verify`，真实数据库执行 `verify -Pmysql-check`。实际结果在收尾追加，不将设计计划记为通过。

## 实际实现与交接

新增 `catalog/{repository,service,config}`、`role/{repository,service,config}`、`query/management` 及按领域分开的 Controller；不修改公共 core、身份实现、依赖、DDL、迁移或 OpenAPI。管理读取适配使用既有表的 SQL 投影，不宣称 BE-08 已完成。页面列表按本次选择的 scopeId 限制系统，禁止合并其他任命；角色页先限定公司、部门/共享角色与系统，再计数分页。列表批量读取权限，避免每个角色各查一次。

| 任务 | 已实现能力 | 仍不包含 |
| --- | --- | --- |
| BE-11 | POST/GET `/api/v1/admin/systems`，GET `/{systemId}`；停用登记、唯一进入权限、分页/详情、鉴权、幂等与审计 | 目录编辑与系统启停归 BE-12 |
| BE-13 | GET `/api/v1/admin/roles`，GET/PUT `/{roleId}`；当前配置/claim、固定归属、共享角色维护边界、独立元数据版本、无变化与幂等 | 新角色及审批草稿归 C/BE-20 |
| BE-15 | `RoleConfigurationApplier.apply(WriteContext, requestId, roleId, expectedVersion, permissionIds)`；完整差集、同系统/功能/有效祖先、新增与保留停用项、版本/历史与整事务回滚 | C/BE-25 的执行编排及 OA 全链路 |

BE-15 先检查当前申请为 APPROVED/EXECUTION_FAILED、有可靠关联与最终批准、claim 与固定目标匹配，基础配置版本未变且本申请尚无配置历史；没有公开配置写 API。调用方须先核对固定规范字节与投影、原范围及所有执行条件，把最终权限 ID 完整集合传入；本端口不会替调用方生成或验证 RFC8785 摘要，也不自行写 `execution_success`、审计、申请终态或释放 claim。测试中的 OA 表记录仅为独立数据库夹具。

失败 `Rejection.code()` 包括 `EMPTY_CONFIGURATION`、`VALIDATION_ERROR`、`INVALID_OWNERSHIP`、`PERMISSION_UNAVAILABLE`、`ROLE_CONFIGURATION_CHANGED`、`STATE_CONFLICT`；C 在外层事务回滚后按原执行设计处理整份失效/失败，不得裁剪明细。应用失败会标记当前 WriteContext 只能回滚，即使调用方捕获异常也不能提交前面的修改。普通存储异常仍向外抛出，由公共事务边界分类。再次调用必须由 C 先查一次成功凭据，不重放旧配置。

## 真实 RED / GREEN 与最终结果

2026-10-19，JDK 21.0.10、Maven 3.9.11、MySQL 8.4.11。数据库只运行于独立 13461 端口及 `backend/.tmp/` 专属 datadir，测试工具逐次创建随机 schema 与 DDL/DML 分离账号，临时 RSA/HMAC 不进入 Git。

| 阶段 | 实际命令（从 backend 执行，JDK/Maven 指向本轮临时安装） | 结果 |
| --- | --- | --- |
| 主线基线 | `mvn verify` | 75 普通测试通过，构建成功 |
| BE-11 RED | `mvn -Dtest=BModuleMySqlCheck test` | 4 项失败、0 错误；未实现接口返回 404 |
| BE-11 GREEN | 同上 | 4 项通过 |
| BE-13 RED / GREEN | 同上 | 新增 3 项因接口缺失失败，随后累计 7 项通过 |
| BE-15 RED / GREEN | 同上 | 新增 4 项因内部应用器缺失失败，随后累计 11 项通过 |
| 隐藏详情纠错 RED | `mvn -Dtest=BModuleMySqlCheck#invisibleRoleWithDamagedConfigurationStillReturnsNotFound test` | 1 项失败：预期 404，实际 500 |
| 最终完整 GREEN | `mvn clean verify -Pmysql-check` | **75 普通测试 + 92 真实 MySQL 检查通过，失败/错误/跳过均 0**；其中 B 新增 12 项 |

最后一项纠错先按固定归属校验可见性，再读取有效配置，避免不可见损坏角色返回不同错误暴露存在性。真实检查还覆盖系统/角色审计失败回滚、配置历史失败及调用方吞异常时的整事务回滚、版本冲突、共享角色只读和范围撤除后禁止重放。

最终构建 2026-10-19 15:53:36（Asia/Shanghai）成功。候选包为 `backend/target/backend-0.0.1-SNAPSHOT.jar`，SHA-256：`ab2cf2d6ca581fc4b776800d6a8c78801fa59f34a773b0ddc1e742d3cdba560c`。原框架的弃用/孵化特性警告及故障注入的预期日志存在，不把它们写成零告警；没有未报告的测试失败。

运行证据保留在本机 `backend/.tmp/` 的 `baseline-retry.log`、`be11-red/green.log`、`be13-red/green.log`、`be15-red/green.log`、`hidden-role-red.log`、`b-final-verify.log`，最终 JUnit XML 在 `backend/target/surefire-reports/` 和 `failsafe-reports/`；日志、密钥、数据库和构建产物不纳入源码提交。首次网络失败/依赖未缓存的构建没有算作有效 RED。

清理核对：专属实例中 `rbacjpa%` schema 数和 `rbacj%` 测试账号数均为 0，测试生成的临时 RSA 目录由测试清理，专属 MySQL 已关闭。仅保留本机忽略目录中的日志及空测试实例数据，不访问任何已有业务库。

文档生成器确定性检查通过，Git 空白检查通过。项目 `validate_design.py` 的完整历史追踪检查**未通过**：本地是主线 9857e5e 的浅克隆，原脚本要求旧基座合并及设计基线的祖先/原文件；多次补历史遭网络超时/断流，未伪造祖先或跳过保护。`validation.json` 保存实际 FAIL 和浅克隆原因；生成确定性已通过，后续依赖历史的检查没有视为通过。此限制与上述已实际完成的 Java 构建、167 项测试分开记录；网络可用后补齐历史再运行脚本。

## 状态与限制

BE-11/13/15 已实现并自测，机器任务保持 IN_PROGRESS、implemented=true、ownerAccepted=false、mergedToMain=false；不替负责人确认完成。AT-09/11/12 及 AT-37 仅本模块部分有证据，AT-18 的规范摘要及完整审批/授权/部署仍未验收。BE-17/12/18 前置未交付，BE-33/35 仍开放。不推送、合并 main 或建立远程 Issue/PR。
