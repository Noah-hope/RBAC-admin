# BE-10：提供给 A 的组织与任职事实

2026-10-19，成员 D 授权优先完成 A 所依赖的数据能力。任务分支 `codex/be-10-people`，基线 `5966834`。本次交付范围是 BE-08/09 所需的内部读取端口，不将完整 BE-10 或跨域验收记为完成。

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
