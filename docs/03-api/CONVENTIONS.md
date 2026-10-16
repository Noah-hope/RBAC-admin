# 公共 API 约定

## 版本、结构与标识

契约版本 1.1.0（API评审修订、尚未上线），路径 `/api/v1`。JSON 使用 camelCase，UTF-8，时间用带时区 RFC 3339，响应统一 UTC，界面按 Asia/Shanghai 显示；时间筛选 `[from,to)`。未知字段拒绝，禁止把缺字段解释为清空。字符串先去首尾空白后检查长度；业务名称允许中文；编码规则见模型。ID 为后端生成的不透明字符串（1～64 字符），示例 `emp_demo_01`，不是物理主键类型承诺。公司、部门、岗位、任职、角色、关系、范围、任命、申请等分别有 ID，不能解析名称/编码推断权限。

员工 `employeeKey` 是来源系统稳定用户名映射，首次导入不生成拼音账号；内部关联使用 `employeeId`。角色 code 全局唯一、后端生成且不可改；目录和权限 code 在所属系统内唯一。所有列表先做范围过滤，再分页计数。

`employeeKey` 是平台统一且全局唯一的员工标识，当前采用正式材料的唯一用户名；`sourceSystem` 记录导入来源，不能因来源不同创建另一个同键员工。冲突批次阻断，来源用户名变化须显式旧新映射。业务调用传这个统一标识，不按姓名匹配。密码字段原样处理，不适用普通显示字符串的首尾空白清理，也不做Unicode规范化或截断。

系统新登记的code最长93字符，为自动建立的`:access`权限编码预留长度；其他业务编码最长100字符。这是技术长度约束，不将编码解析为管理资格。

| 资源 | 关联与稳定性 |
| --- | --- |
| 公司、部门、岗位、任职 | 部门仅归属一公司，无部门父级；岗位归属部门；任职关联员工与岗位，可多项；组织更名不改变 ID/code |
| 系统、目录、权限 | 目录可有同系统父级；FUNCTION_OPERATION 绑定功能，SYSTEM_ACCESS 不绑定功能；父目录不授予权限 |
| 角色 | 固定公司/可选部门/系统；状态仅 UNCONFIGURED、EFFECTIVE，无角色启停；metadataVersion 与 configurationVersion 分开 |
| 授权来源、关系 | 来源键为员工＋公司＋部门＋系统，sourceEpoch 独立；关系键为员工＋角色＋来源公司＋部门，relationVersion 包括未曾授予/已结束的历史防重依据 |
| 管理范围、任命 | 范围 ID/version 与人员任命 ID/version 分开；任命失效不删除范围和原申请依据 |
| 申请 | draftVersion 管编辑，submittedVersion/contentDigest 管固定审批内容，statusVersion 管投递/结果/执行变化，不混用 |

## 三类入口与会话

| 入口 | 认证与授权 |
| --- | --- |
| `/admin/*`（登录除外） | AdminJwt。每请求校验签名、固定算法 RS256、iss、aud=rbac-admin、exp、jti、sid、凭据版本和当前账号/员工/资格；不能把 JWT 内管理范围当授权事实 |
| `/integration/*` | BusinessJwt，仅受信业务后端，aud=rbac-business、绑定唯一 systemId；员工来自业务后端已认证身份；浏览器不得持有该服务凭据 |
| `/oa/*` | OaJwt，aud=rbac-oa-callback、绑定 OA 实例，仅可信最终结果；管理员 JWT、业务 JWT 不可替代 |

三种认证方案互不通用。服务 JWT 由受控部署配置的签发方提供；不增加公开自注册、服务令牌签发或后台配置密钥接口。iss、密钥轮换、真实 OA 地址在部署集成时提供；本期模拟调用方也走同样校验。

管理员 access JWT 有效期 15 分钟；受限改密 JWT 5 分钟，仅允许本人改密和退出；一期不提供 refresh token，过期重新登录。同账号可有多个会话。客户端只在内存持有 access token，HTTPS Bearer 传输，不写 URL/持久化浏览器存储。登录、改密及所有认证响应 `Cache-Control: no-store`。服务 JWT 最大 5 分钟，校验当前服务身份状态及绑定；允许时钟偏差至多 60 秒。期限是本轮技术设计值，可按契约变更流程调整，不是原需求新增业务规则。

`X-Admin-Appointment-Id`：权限管理员每个带“范围”标记的请求必须指定一项当前有效任命；后端从任命取得唯一 scopeId/companyId/departmentId/systemIds，不接受拼接。总管理员省略该头按全局资格处理，不接受伪造操作者或 originalAuthority。切换是逐请求选择，没有扩大范围的切换接口。我的任命列表分页返回，避免登录响应内无界展开。

权限管理员同时传 `X-Admin-Appointment-Version` 和 `X-Admin-Scope-Version`，值取我的任命列表（列表包含当前公司/部门/系统集合）。缺少所需上下文头返回422；上下文已变409 CONTEXT_CHANGED，重新读取选择，不暗中使用旧页面范围。总管理员全局请求省略这三个头。

账号停用、本人改密、密码重置撤销全部旧会话；重新启用不复活。退出只撤销当前 sid，重复或已过期的退出统一 204；无效 token 不用于识别其他会话。任命撤销/范围版本变化使引用旧 appointmentVersion/scopeVersion 的管理上下文不可继续使用，返回 409 CONTEXT_CHANGED 并重新读取任命；剩余任命仍可选择。失去全部资格或员工停用则全部会话立即失效并返回 401 SESSION_REVOKED。不能仅等待 JWT 过期或异步清缓存；与写事务并发须在提交前复核。

## 请求约束、分页、排序

列表统一 `page` 从 1 起、`pageSize` 默认 20，允许 10/20/50/100。响应 `{items,page,pageSize,total}`，total 仅为当前可见范围。显式越范围筛选 403，猜测越范围详情与不存在统一 404。所有列表都支持分页；受完整快照语义约束的权限结果、预览中的最多 100 条选中明细不作为列表接口分页，预览员工明细另分页读取。

`q` 为名称/编码前缀或包含检索，不接收 SQL/表达式；ID 筛选精确匹配。默认业务列表 `createdAt DESC,id DESC`；组织 `code ASC,id ASC`；目录同父级 `sortOrder ASC,code ASC,id ASC`（整数，允许并列），层次由 parentId 重建，与部门平级无关。审计及申请事件 `occurredAt ASC,id ASC`，审计主列表降序。分页是当前状态查询，不保证跨页并发变更快照；导出不在一期范围。

授权/撤销一次至多 100 条明确明细，一申请一个来源公司，可多系统；总管理员可跨该公司多个部门，权限管理员仍限一任命覆盖全部。禁止员工×角色隐式笛卡尔积。目录一次至多 100 项、HR 一批至多 1000 条、原文件至多 10 MiB（解压内容至多 50 MiB），超限拒绝，不拆批部分执行。具体上限为本轮防无界请求设计值，不是性能承诺。

写请求要求原因 1～500 字符。密码 12～128 字符，允许 Unicode 和空格，不截断、不要求从浏览器传密码哈希；确认值必须一致，不得与旧密码相同；密码相关字段只写，不在示例保存任何真实或示范凭据。登录失败按账号/IP 基础限速，超过阈值 429+Retry-After；初始建议登录每 IP 每分钟 10 次，其他后台每账号每分钟 120 次、业务每调用方每分钟 600 次、回调每 OA 实例每分钟 120 次，部署可收紧，不能把限速视为授权。

## 返回、错误与幂等

成功返回具体资源或明确结果，不统一塞进 `success:true`。201 表示创建，202 表示固定申请已提交/回调可靠接收而非业务生效；200 包含 APPLIED/NO_CHANGE 或预览；204 退出/草稿删除。创建及异步接受返回 Location；saveCatalog/closeSource是直接变更结果，返回200，不伪造不存在的变更查询地址；全部响应回传 X-Request-Id，敏感数据不缓存。客户端可传 X-Request-Id（1～64 安全字符），服务端可生成；不作为防重或身份凭据。

所有改变业务状态的请求带 UUID `Idempotency-Key`，本人改密/登录/退出例外；仅预览的 POST 无需该头。修改已有资源还带模型中 expectedVersion，缺失 422，陈旧 409 VERSION_CONFLICT；不用时间戳或状态版本替代配置版本。不同时再要求 If-Match，避免两套并发协议。重复请求优先通过当前身份/范围检查，再回放原安全响应；不能绕过已取消资格。

同调用主体＋方法＋规范路径＋key 在至少 7 天内唯一：相同规范内容返回原 HTTP 结果并带 Idempotent-Replayed=true；不同内容 409 IDEMPOTENCY_CONFLICT，处理中 409 REQUEST_IN_PROGRESS+Retry-After。请求内容摘要包含版本和目标；密码仅对受保护摘要参与比较，幂等存储不保存明文密码或完整认证请求。7 天后禁止客户端盲目重发新建请求，先按返回 ID 查询；申请执行、OA 最终结果、sourceBatchId 防重不随此窗口过期。

错误使用 `application/problem+json`，字段 type（稳定 URN）、title、status、code、detail、requestId、errors[]（JSON Pointer/行号），不返回 SQL、堆栈、凭据。批量错误只返回当前可见目标；任一越权整批拒绝，不泄露其存在性。

| HTTP | code 范畴与处理 |
| --- | --- |
| 400 | MALFORMED_REQUEST：JSON/参数语法错误 |
| 401 | AUTHENTICATION_FAILED、SESSION_REVOKED：重新认证，登录失败统一提示 |
| 403 | FORBIDDEN、SYSTEM_DISABLED、PASSWORD_CHANGE_REQUIRED：无资格/错入口/停用系统，不伪装成无业务权限 |
| 404 | NOT_FOUND：不存在或不可见；详情一致 |
| 409 | VERSION_CONFLICT、CONTEXT_CHANGED、STATE_CONFLICT、CHANNEL_OCCUPIED、RELATION_OCCUPIED、IDEMPOTENCY_CONFLICT、REQUEST_IN_PROGRESS、CALLBACK_CONFLICT、CONTENT_MISMATCH、PREVIEW_STALE、SOURCE_BATCH_CONFLICT、SELF_LOCKOUT：重新读取/预览；不得强行覆盖 |
| 413/415 | PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE |
| 422 | VALIDATION_ERROR、EMPTY_CONFIGURATION、INVALID_OWNERSHIP、DIRECTORY_CYCLE、IMPORT_BLOCKED：修正字段/归属/批次 |
| 429 | RATE_LIMITED：按 Retry-After 重试，不新建业务申请 |
| 500 | DATA_INTEGRITY_ERROR：完整性损坏，禁止默认放行 |
| 503 | DEPENDENCY_UNAVAILABLE：数据库/会话校验不可用，重试同一请求，不假称无权限或成功 |

相同配置、全部已授予/已撤销的提交返回 200 NO_CHANGE，不提交 OA，不占用新的通道；部分无变化只在提交前明确排除并冻结实际清单。批准后绝不再删减批次。失败写入是否落地不能用 HTTP 超时猜测，查原资源或使用同 key 重试。

OA 关联暂未可靠落地时使用409 OA_LINK_PENDING及Retry-After。申请 INVALIDATED 的failureCode允许 SCOPE_NO_LONGER_COVERS、SOURCE_EPOCH_CHANGED、RELATION_VERSION_CHANGED、ROLE_CONFIGURATION_CHANGED、EMPLOYEE_INACTIVE、NO_APPLICABLE_EMPLOYMENT、PERMISSION_UNAVAILABLE；可重试失败使用 EXECUTION_STORAGE_FAILURE。它们是状态原因，不以200状态伪装为本次已经执行成功。

## 演进与规范依据

1.0.0尚未上线、未导入Apifox；本轮1.1.0是设计评审修订编号，不代表对首版完全兼容。具体迁移差异见[评审报告](REVIEW.md)，已有首版生成客户端需重新生成并适配两项201→200及新增响应字段。公开稳定发布后的规则如下。

兼容新增可选字段/错误码以次版本发布；客户端忽略未知响应字段但提交请求仍严格校验。字段删除、含义改变、必填新增及鉴权变化走新主路径 `/api/v2`，保留 v1 迁移窗口（上线前另定具体日期），文档明确弃用，不静默复用权限编码。

本契约采用 [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0.html)、[Problem Details RFC 9457](https://www.rfc-editor.org/info/rfc9457/) 及 [JWT 安全建议 RFC 8725](https://www.rfc-editor.org/rfc/rfc8725.html)。这些是描述与认证技术依据；本项目业务规则以正式用例为准。
