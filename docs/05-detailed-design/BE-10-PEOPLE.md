# BE-10：提供给 A 的组织与任职事实

## D-065 当前交付

BE-10全量实现并已交付远程main，待负责人人工验收。来源提交`6bfae88c89d0bad4b1599787ea47e2107a3bbcf9`，无冲突no-ff合并为`8f1db6927b6d82e1a352ad016a4c1c056a6c648a`；合并树与验证来源完全一致。`git push origin main`成功，随后`git ls-remote origin refs/heads/main`实际回查同一合并SHA。保留本地任务分支，不创建或虚构本任务远程Issue/PR。

负责人验收仍是ownerAccepted=false，已完成实现和主线交付不再以D-064历史局部状态描述。组员可拉取main复用全部端口；此后记录交付事实的提交只改文档/追踪，不改变已验证后端代码。

## D-065 全量实施及交付授权

用户明确“BE-10要全部实现，实现完还要实现合并和推送”，授权本任务完成后合入main并推送，替代下方D-064仅内部读取且不推送的范围。仍不虚构负责人已人工验收。

六个GET接口遵循Git API1.2.0：组织按code/id升序；员工/任职按created_at/id降序；page默认1、pageSize默认20且仅10/20/50/100；q为经过转义的字面包含检索；筛选、计数、详情及投影均在一次ReadBoundary事务中。Employee含createdAt，Employment含可空startedAt/endedAt，不泄露账号/源身份映射。

人员查询自身实现窄范围资格投影：每次调用CurrentIdentityReader复核当前后台会话；总管理员省略三上下文头为全局，带头则按同一项任命处理。权限管理员必须提供三头，任命必须属于本人、ACTIVE、公司/部门有效任职且版本匹配；当前资格不符403、上下文已变409，缺失或非法头422。范围内组织只返回指定公司/部门/岗位；员工可见性为该部门当前任职，或在获准系统内持久历史来源；详情越范围与不存在均404，显式越范围筛选403。历史员工仅返回安全员工摘要，任职列表仍限定指定部门，不泄露其他任职。此窄投影不宣称实现A的BE-08我的任命/通用覆盖策略，不改变其原任务归属。

EmploymentWriter为内部基础DML能力，由调用方提供真实WriteContext及经过复核的总管理员导入身份；无HTTP写入口、不新建事务、不逐条提交。提供稳定候选ID的公司/部门/岗位/员工创建及更名、源身份/组织路径显式映射、任职新增/结束、无历史引用部门迁移。每次版本匹配，实质变更才递增；重复同内容无变化，错误将外层事务标为rollback-only，即使调用方捕获也不可提交部分数据。更名不变ID/code/归属；员工状态由A/BE-27负责，结束任职返回完整前后事实及最后任职丧失事实供BE-30调用BE-26/27编排，不提供独立导入提交API，不承诺本任务完成跨域失效。部门迁移检查正式/历史引用，映射需显式迁移且保留ID，旧/新归属和审计同事务。

验收按模块RED/GREEN：HTTP真实认证、范围与双版本、分页排序与安全字段、历史来源/兼岗/显式越范围；基础写入的稳定标识、版本、无变化、归属、映射、历史、失败回滚及审计。全套已有测试回归、静态追踪同步后，再提交/合并/推送并回查远程SHA。所有引擎夹具限专属临时实例。

### D-065 实际实现与验证

- 六个GET操作：`/companies`、`/departments`、`/positions`、`/employees`、`/employees/{employeeId}`、`/employees/{employeeId}/employments`（统一前缀`/api/v1/admin`）。控制器位于web/people/controller，投影与资格复核由people.PeopleQueries负责。沿用身份RSA配置，未启用身份时既有入口保护返回503；未改API或迁移。
- EmploymentWriter已作为Spring Bean装配。`ImportAuthority`固定当前accountId、batchId、batchVersion、contentDigest、原因和requestId；仅接受真实WriteContext，复核当前启用/非受限总管理员及已冻结READY/FAILED批次、版本和摘要。调用方仍须按冻结计划完整复核并驱动批次提交。组织更名保留code/ID/归属，员工元数据更新不改全局状态；显式源身份/组织映射保留映射ID；新增任职使用冻结候选ID，结束不删除历史，返回最后任职丧失事实。无历史部门迁移要求完整显式映射列表，保留映射ID/递增版本，结构化审计归属新公司并保存before/after；有正式或历史引用则阻断。
- HTTP RED：11场景中10个因接口尚未注册而断言失败、0错误；既有账号停用保护场景已通过。初始写端口RED：6场景中5个未实现异常，另1个回滚拒绝场景通过。实现后通过；引擎曾检出JSON_SEARCH的排序规则冲突，已显式使用utf8mb4_bin修正。补充版本上限无变化场景曾因VERSION_CONFLICT失败，修正后通过；迁移新公司审计字段断言曾期望新公司而得到NULL，修正后通过。
- 最终完整`mvnw.cmd -q clean verify -Pmysql-check`：81离线＋122真实MySQL，失败/错误/跳过均0，jar成功打包。包括12项人员HTTP、12项导入基础写、10项内部读取以及既有身份/账号/事务/初始化回归。此数量为最终一次完整运行，不混计历史结果。已有Flyway兼容提示、Hibernate配置提示保留，不称为零警告。
- 专属MySQL8.4.8端口23310（非3306），随机schema/账号清理回查均0；本轮PID8392已正常shutdown，be10-http-keys临时目录剩余0。本机原MySQL84仍Running，未操作用户已有开发/测试库，也未重启IDEA应用。静态校验及diff空白检查通过。

AT-13/27/29仅将BE-10负责的行为记PARTIALLY_VERIFIED，完整授权/预检/跨域导入仍由原任务验证。BE-10实现完成不等于用户已人工验收，任务登记IMPLEMENTED、ownerAccepted=false。主线交付按实际结果补充，不阻塞已授权的合并推送。

最终jar SHA256：`2c9b0b2a557a7f2306ec7214ea0bdef0579f9a857cd063c52aebb58caddf6050`。构建产物由target忽略，不将临时密钥或数据库目录提交到Git。

## D-064 历史：内部读取首次交付

以下为截至提交0c479e9的历史记录，局部范围与不推送边界已由上方D-065授权替代。

2026-10-19，成员 D 授权优先完成 A 所依赖的数据能力。任务分支 `codex/be-10-people`，基线 `5966834`。该次交付范围是 BE-08/09 所需的内部读取端口，未将完整 BE-10 或跨域验收记为完成。

依据：UC-03/07/10、管理范围与人员权限规则 V1.3、数据库 1.1 的 company/department/position/employee/employment/employee_source_identity/organization_mapping，M02 和 T9/T11。不改需求、API 或已有迁移。

## 接口与事务

`people.OrganizationReader` 返回不可变事实：公司、部门、岗位、员工及其业务版本；源员工身份和完整组织路径的精确映射；全部适用有效任职及任职版本。使用 UUID 定位，不按姓名匹配或根据路径猜测归属。不存在返回 Optional.empty 或空集合，存储故障传播给外层边界分类，不能伪装成无任职。

读取必须在调用方主库事务中进行，并确认所用 DataSource 已绑定该事务。A 在 ReadBoundary 的同一 RR 快照或 CommandBoundary 已锁 guard、复核身份的 RC 写事务中调用。不新开事务、不缓存、不接受客户端声称的任职事实。此端口不授予后台资格；A 仍复核当前身份、单项任命、范围及系统集合。

`applicableEmployments(employeeId, companyId, departmentId)` 返回 ACTIVE 员工在准确公司/部门的全部 ACTIVE 任职；其他部门、其他公司及 ENDED 任职排除；业务起止时间未知不影响状态判断。返回按任职 UUID 稳定排序且不可变，保留每个任职的版本，支持多个兼岗依据。员工全局停用返回空集合，但 employee 查询仍返回 INACTIVE 事实。岗位名称不产生角色或授权。

## 验收与边界

先运行失败测试，再实现：实际组织归属及版本、缺失对象、员工停用、同部门多岗位、跨公司/跨部门隔离、ENDED 排除、未知起始时间、准确源身份/组织路径、参数化查询、外部事务缺失/错误数据源拒绝、同一 RR 快照及写事务复核、不修改 guard/账号/授权表。

真实 SQL 验证只使用 backend/.tmp 下专属 MySQL8.4 数据目录、非3306端口和测试工具随机 schema/账号；不连接本机已有项目库，不索取密码。离线测试覆盖事务前置保护；完整默认构建必须运行。

BE-10 六个 HTTP 查询接口与其范围投影、EmploymentWriter/受控导入写端口仍开放，不为解除 BE-08 的内部事实依赖先写缺少范围策略的公开接口。BE-26/27 的跨域失效需要原 B/C 依赖。本人确认、合入共同 main 和推送均另行记录；本次不自动执行。

## 验证记录

使用本机 Corretto21.0.12.1、项目 Wrapper/Maven3.9.11；仅为当前命令设置 JAVA_HOME，不修改系统环境变量。真实数据库为独立 MySQL8.4.8，23310端口、backend/.tmp/be10-mysql-data，绑定127.0.0.1且禁用binlog及X端口。测试工具实际核验datadir/版本/端口/binlog后才创建随机schema与账号。

RED：`mvnw.cmd -q -Dtest=OrganizationReaderTest test`，1失败/0错误（未实现事务前置，得到UnsupportedOperationException而非拒绝无事务调用）；真实引擎8个场景均失败：2断言失败/6未实现异常，原因为未装配端口与未实现查询。初次夹具SQL因position保留字报错，修正引用后重新运行，未将夹具错误视作有效RED；首次误带skipTests的命令未运行测试，也不算证据。

GREEN：同一离线测试与8个真实引擎场景通过。完整 `mvnw.cmd -q clean verify -Pmysql-check` 实际81项离线＋96项真实MySQL检查，失败/错误/跳过均0，包含已有身份、账号、bootstrap和事务回归。保留已有Flyway版本兼容警告和Hibernate配置提示，不把有提示的运行描述为零警告。其后新增两项回归，仅验证已知任职时间UTC微秒精度与存储错误传播，没有修改生产代码；重新执行全部10项OrganizationReaderMySqlCheck通过。最终XML报告合计81项离线、98项真实引擎场景，均0失败/错误/跳过；98为完整96与补充2的覆盖合计，不伪称再次整套执行98项。

清理回查：专属实例随机schema剩余0、随机账号剩余0，正常shutdown后PID37380已退出；已有MySQL84服务仍Running。专属datadir/日志位于Git忽略目录，不提交。没有连接或更改用户rbac_admin_dev/rbac_admin_test，没有重启用户IDEA应用。`validate_design.py`与`git diff --check`通过；静态校验不当作业务测试。

## A 的接入

Spring直接注入`com.adgn.backend.people.OrganizationReader`，无需新增密钥或迁移。在A自己的ReadBoundary资格回调/投影回调或CommandBoundary已锁guard的资格回调中调用：

```java
var employee = people.employee(employeeId); // Optional，含状态和version
var company = people.company(companyId);
var department = people.department(departmentId); // 必须核对实际companyId
var evidence = people.applicableEmployments(employeeId, companyId, departmentId);
// evidence包含全部适用任职id/positionId/version，不只取第一项。
```

缺少员工/公司/部门及无适用任职时，A按自己操作的契约决定拒绝/不可见语义；不能把返回的事实当成管理员资格。不得向客户端暴露sourceIdentity/organizationMapping作为新的公开接口。更新岗位名称不自动授予角色；读取事实不修改任何正式数据。

本分支尚未合入main，也未推送；A的正式技术依赖以实际共同主线交付为准。A后续需要页面查询和导入数据时，仍须完成BE-10剩余HTTP/受控写端口及BE-28～30；本轮没有向本机开发/测试库写入演示人员。
