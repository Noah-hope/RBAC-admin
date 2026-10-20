# BE-09 创建管理范围及任命、创建预览

状态：A 已按自动化测试证据确认（D-082），OWNER_ACCEPTED，来源41931f4；本机演示尚未进行，尚未主线交付。基线 main/a860dc8，分支 codex/be-09-scope-creation。

2026-10-20：A明确“测试通过即可”，接受已有112离线＋205真实MySQL/HTTP证据；不再将演示账号准备作为本任务负责人确认前置。已fetch核对远程main/072585e新增BE-16，但本任务分支尚未整合；后续主线交付前应接入并回归。实施编号原D-079因与远程D/BE-16冲突改为D-081，本次确认编号D-082；保留原实现与证据。本轮仅文档/静态追踪，未推送/PR/合并。

## 范围与依据

遵循 UC-07 V1.3、ScopeInput/ScopeCreateSave/ScopeResult 契约、既有 V1～V3 表及 T0/T6/T11；技术依赖 BE-05/08/17 已交付主线，B/D 人工验收状态不代确认。本任务只实现 POST management-scope-previews 与 POST management-scopes；编辑/撤销范围留 BE-27。

## 处理与架构

Controller → ScopeCreationService → 资格事实读取 + BE-17 PreviewService + JPA 创建仓储。复用 BE-08 查询及单项任命上下文；不增加公共空包、迁移或新依赖。范围及系统集合、任命通过 Spring Data JPA 持久化，技术 row_version 与初始为 1 的业务版本分离；组织/任职通过 D 的 OrganizationReader，安全账号及系统事实为事务内 JDBC 读取。

1. 输入必须完整：公司、部门、无重复的系统/账号集合、原因；明确空集合合法，缺失不是清空，不设未经确认的人数限制。集合按 ID 排序后摘要。
2. 每次写在 CommandBoundary 取得 guard 后重验当前总管理员（非首次改密限制会话）；部门属于公司，新增系统已登记且启用；每个候选账号可用、不是总管理员、关联 ACTIVE 员工且在精确部门具有 ACTIVE 任职。
3. 预览只保存 5 分钟的安全投影、候选范围/任命 UUID、输入及资格依赖摘要，不写范围、任命、业务角色或成功业务审计。复用 BE-17 的分页与当前创建者检查；任命目标明确计数，业务授权影响为零。
4. 保存带 UUID Idempotency-Key、原 input 与 previewId。幂等先验当前总管理员、再检查回放；成功回放不重新依赖已过期预览。首次执行重新读取资格依赖并确认预览/创建者/输入/时效；相关依赖改变则 PREVIEW_STALE，不能用预览跳过当前检查。无关人员变化不使预览失效。
5. 使用预览绑定 ID 一次创建范围、系统集合、独立任命和安全成功审计，显式 flush；数据库/审计失败全部回滚且幂等不标成功。一个预览换新键重复创建拒绝；同键同内容只回放，不重复任命/审计。返回 201、Location、ScopeResult，不返回凭据。

## 验收行为与 TDD 顺序

先用真实隔离 MySQL/HTTP 检查证明新端点缺失，再实现。覆盖：完整成功及 BE-08 可读/候选可登录；空集合、多管理员/同部门多范围；非总管理员、受限/失效会话；缺字段/重复集合/错误部门/未知或停用系统/无效账号与员工/精确任职；预览输入、创建者、失效与相关变化；幂等冲突/并发/过期回放；审计失败整批回滚及无业务授权副作用。保存前的资格变化由 guard 排队后的当前事实决定。

验证仅使用 backend/.tmp 下新建专属非 3306 MySQL8.4，核对实例 datadir 后才写随机库与随机账号；未访问已有开发/测试库。

## 实际验证记录（2026-10-20）

采用项目 test-driven-development 技能及 writing-good-tests 约定，先设计并编写真实 HTTP 测试，再实现与修正。

| 阶段 | 实际结果 |
| --- | --- |
| 初始 RED | 新预览应为 200，实际 404；1 项真实测试失败，证明端点缺失。普通 112 项仍通过。 |
| 实现及契约 RED | 首轮检查发现依赖快照中的 Java Long/JSON integer 节点直接比较使合法预览误判失效，改为规范 JSON 的 SHA-256 资格集合摘要；预览目标契约断言再次先失败（期望 CONFIGURATION_CHANGE、实际 CREATE），随后修正为既有枚举。 |
| 测试夹具纠正 | 过期夹具同步回移 created_at，满足 expires_at>created_at；账号停用测试保持 401，并对齐现有 SESSION_REVOKED 错误码。未改动身份机制或数据库约束来通过测试。 |
| 最终完整 GREEN | `backend` 下执行 `mvnw.cmd -Pmysql-check clean verify`，112 普通＋205 真实 MySQL/HTTP 检查全部通过，失败/错误/跳过均 0；其中新增 ScopeCreationMySqlCheck 12 项。 |
| 事务和权限 | 预览不写正式范围/任命/成功审计；创建后候选可登录并查询自己的任命；空集合及同部门多范围合法。重复/歧义输入、不合资格、异创建者、过期、依赖变化与 guard 前已提交的撤任职均拒绝；同键并发只创建一次、成功回放不受预览后续过期影响且仍重验当前身份。 |
| 全部回滚 | 注入 audit_event 写入失败，已 flush 的 JPA 范围、系统集合、任命及成功审计全部回滚，幂等保留 IN_PROGRESS 而非成功；不创建业务来源、grant 或 OA 申请。 |
| 静态与清理 | 设计追踪与链接校验通过。随机库/账号 0/0，BE-09 临时密钥目录 0，专属 mysqld 正常关闭；原 MySQL84 仍 Running。 |

完整构建产物 SHA-256：`276089926f40ad05d16f0fd775d29e5e9a7e93e83f69086292e3f87b1ccc84ad`。日志和 XML 报告保留本机忽略目录 `backend/.tmp/`，不包含真实业务凭据的共享示例。mysql-check 只允许已核验的专属 MySQL8.4（显式 RBAC_ISOLATED_MYSQL_PORT / RBAC_ISOLATED_MYSQL_DATADIR，非 3306、datadir 在 backend/.tmp），不能把现有 dev/test URL 代入。

AT-08/31/37 仅补本任务的创建、预览及接口证据；BE-27 编辑/撤销与申请失效、BE-33 跨域验收仍开放。本轮未推送分支、创建 PR、合并 main 或部署服务器，也不代记 B/D 本人验收。

## A 本机如何验收

D-083主线集成验证：按A本轮PR/合并授权，已将main/072585e（含BE-16）保留历史合入任务分支1b92b88，Java无冲突，共享文档/任务追踪保留双方事实。完整 `mvnw.cmd -Pmysql-check clean verify` 通过112普通＋227真实MySQL/HTTP检查，失败/错误/跳过0；其中BE-09新增12项、BE-16内部22项均通过，BE-09代码与A确认的41931f4一致。构建产物SHA-256为 `2010e15e4859b91b20f871a81dc84c177b625aa9d89b65bd06a3ca81d450101d`。静态校验通过，随机库/账号0/0、BE-09密钥目录0，专属实例已关闭，原MySQL84仍Running。A确认及D各任务人工验收状态保持，实际PR/主线结果成功后补录。

使用原 BackendApplicationTest（或你固定用于验收的运行配置），确认数据库 URL 与账号、组织人员数据属于同一个库。沿用 BE-06/07 的 RSA 与幂等 HMAC 配置，不需要重新生成密钥；启用身份及幂等配置后重启。不要把 BootstrapTestAdmin 配置当普通应用启动。

1. 在 Apifox 用总管理员的新密码调用 `POST /api/v1/admin/sessions`，登录请求 Auth 选“不使用鉴权”，得到 `mode=NORMAL` 的 `accessToken`。下面所有请求使用 Bearer Token，字段只填 token 本体。可保留本机环境的 `{{bearerToken}}`，值不保存到共享接口示例；可选 `X-Request-Id` 不填就取消勾选。
2. 从已实现的公司、部门、系统、员工/任职及账号查询取得真实 ID。部门必须属于所选公司；系统须启用；候选须是启用的权限管理员账号、关联 ACTIVE 员工且在所选精确部门有 ACTIVE 任职。需要后台账号时先用 BE-07 创建；测试库没有组织人员时请先与 D 准备合法测试数据，不能拿别库 ID 使用。也可先以两个空集合验证完整创建流程。
3. 调用 `POST /api/v1/admin/management-scope-previews`，Body 选择 JSON。下例占位 ID 要替换；暂时没有候选/系统时对应改为 `[]`，两个集合字段都必须保留。

```json
{
  "companyId": "replace_company_id",
  "departmentId": "replace_department_id",
  "systemIds": ["replace_enabled_system_id"],
  "accountIds": ["replace_candidate_account_id"],
  "reason": "本机验证新增管理范围"
}
```

应返回 200、`id`、`contentDigest`、`expiresAt`；`impact.visibleAppointmentCount` 等于候选人数，业务授权员工影响为 0。此时查询范围列表不应新增记录。可用 `GET /api/v1/admin/previews/{id}/targets` 看固定任命目标。

4. 五分钟内调用 `POST /api/v1/admin/management-scopes`，Headers 手动添加一次 `Idempotency-Key`，值为新 UUID，例如在 PowerShell 执行 `[guid]::NewGuid().ToString()`（不是 RSA 密钥，不是 token）。Body 中 `input` 原样使用预览内容，`previewId` 填刚才响应的 `id`：

```json
{
  "input": {
    "companyId": "replace_company_id",
    "departmentId": "replace_department_id",
    "systemIds": ["replace_enabled_system_id"],
    "accountIds": ["replace_candidate_account_id"],
    "reason": "本机验证新增管理范围"
  },
  "previewId": "replace_preview_id"
}
```

应返回 201、`Location`、`outcome=APPLIED`、`scope.version=1`、任命人数及非空 `auditEventIds`。用 `GET /api/v1/admin/management-scopes/{scope.id}` 与其 `/appointments` 核对系统集合、ACTIVE 名单及两种版本。查询有可选 Params 空行时取消勾选，不发送空值。

5. 保持同一 Body 和 Idempotency-Key 再发一次，应返回同一结果与 `Idempotent-Replayed: true`，范围及任命数量不增加。修改 Body 又复用原键应返回 409；真正的新操作需重新预览并换新键。预览超时/资格变化返回 `PREVIEW_STALE` 时也重新预览及换键。
6. 候选使用自己的登录名/密码登录（若 `PASSWORD_CHANGE_ONLY` 则先按 BE-06 改密并重新登录），`GET /api/v1/admin/my-appointments` 应能看到新任命。该账号调用创建范围应被拒绝 403；它没有因此自动获得任何业务系统角色或进入权限。

本机演示无需故意停用账号或破坏审计表；这些回滚、竞态和失效边界由专属数据库自动测试验证。负责人已按自动化证据确认（D-082），上述步骤保留供后续演示账号齐备时使用；本任务 PR/主线交付待办理。
