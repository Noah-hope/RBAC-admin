# BE-26 直接来源关闭与统一申请失效编排

D-093实际结果（2026-10-21）：BE-26两个公开操作与统一RequestInvalidator已完成设计/TDD，完整137离线/20组＋318真实MySQL/HTTP/22组及静态追踪通过，失败/错误/跳过0；含34项本任务真实验收。覆盖逻辑0、100来源、整份失效与全部claim/outbox、原范围实质失覆盖、独立角色/来源任职、隐藏混合申请及统计回放授权、并发与第N项/审计故障回滚。随机schema/账号/测试密钥目录均0，专属MySQL子进程27740正常关闭，原MySQL84仍Running。BE-26登记IMPLEMENTED、ownerAccepted=false、mergedToMain=false，只本地任务分支提交，既有各成员验收不代确认。共13项OWNER_ACCEPTED、6项IMPLEMENTED、5项IN_PROGRESS、11项PLANNED；BE-27/30未实施，不自动合并推送或部署。交接见docs/05-detailed-design/BE-26-SOURCE-CLOSURE.md，下方IN_PROGRESS/待实现为历史。

## 实际结果与负责人交接

D-093实际结果（2026-10-21）：BE-26两个公开操作与统一RequestInvalidator已完成设计/TDD，完整137离线/20组＋318真实MySQL/HTTP/22组及静态追踪通过，失败/错误/跳过0；含34项本任务真实验收。覆盖逻辑0、100来源、整份失效与全部claim/outbox、原范围实质失覆盖、独立角色/来源任职、隐藏混合申请及统计回放授权、并发与第N项/审计故障回滚。随机schema/账号/测试密钥目录均0，专属MySQL子进程27740正常关闭，原MySQL84仍Running。BE-26登记IMPLEMENTED、ownerAccepted=false、mergedToMain=false，只本地任务分支提交，既有各成员验收不代确认。共13项OWNER_ACCEPTED、6项IMPLEMENTED、5项IN_PROGRESS、11项PLANNED；BE-27/30未实施，不自动合并推送或部署。交接见docs/05-detailed-design/BE-26-SOURCE-CLOSURE.md，下方IN_PROGRESS/待实现为历史。

完整回归：backend内显式JDK21、专属隔离MySQL环境执行 `./mvnw.cmd -q clean verify -Pmysql-check`，137项离线/20组、318项真实MySQL/HTTP/22组，失败/错误/跳过0，日志backend/.tmp/be26-full-verify.log。本任务34项在SourceClosureMySqlCheck，最终定向日志be26-final-targeted.log。jar `backend/target/backend-0.0.1-SNAPSHOT.jar` SHA256：`99e7bbb44ed8ab3886b41384c2e902cf65221fa20654b92a81f0f6bb15e62afb`。静态检查本身不运行Java/SQL/HTTP。

| TDD阶段 | 实际观察 |
| --- | --- |
| 首轮正式RED | be26-http-valid-red.log：4项断言失败、0错误，路由404；命令参数/必填历史夹具错误不作为RED |
| 集成后RED | be26-integrated-http-red.log：7项断言失败、0错误，包含输入与固定预览 |
| 整份失效RED/GREEN | be26-invalidation-red.log：10项中3项失败、0错误，旧申请仍PENDING；实现后10项通过 |
| 完整边界RED | be26-full-boundary-red.log：25项中8项失败、0错误，覆盖统一失效的五种触发及三类安全/资格预览问题 |
| 可空目标与回放RED | be26-replay-null-red.log：30项中13失败、5错误；待审批配置空employee字段导致NPE并影响后续直接端口测试，另有未知标识及版本耗尽500。不是通过证据 |
| 独立权限RED | be26-visible-replay-red.log：1失败、0错误，撤去曾披露申请所需系统后旧统计仍200，预期403 |
| 最终GREEN | 34项真实HTTP/MySQL全部通过；包含100来源、第二来源失败、已批准/失败待重试、捕获异常不能提交、混合范围隐藏及历史统计回放权限 |

中间角色归属更新被不可变触发器拒绝、DML账号安装触发器失败和过期预览时间约束均已修正为合规夹具，不作为业务RED。用户中断后一次专属实例退出导致连接失败，恢复同一专属实例后重新验证，未把该运行记为通过。原MySQL84保持Running；最终随机库、账号与测试密钥目录回查0，子进程27740正常关闭，未操作用户现有库。

两公开操作已实现：POST `/api/v1/admin/source-closure-previews` 返回200固定预览；POST `/api/v1/admin/source-closures` 返回200 APPLIED及安全计数/审计ID。读取来源基线取得准确epoch/version，保存必须使用同一规范输入与previewId，并提供UUID Idempotency-Key。预览不修改正式来源；无grant的逻辑0来源也明确展示且关闭后推进。输入systems支持1～100项、排序规范化且不允许重复。无授权401、受限会话/越范围403；未知目标404、预览过期/依赖变化409、非法输入422、存储/审计失败完整回滚。

同key重放会重新校验当前单项任命，并按持久AuthorizationBasis检查输入来源和曾实际披露的完整申请/角色范围。新任命覆盖全部事实时可重放；缩小后不能取得旧统计或不可读审计ID。隐藏混合申请实际整份失效，但不返ID/隐藏数量；完整内部审计GLOBAL，公开来源审计SCOPED且仅包含可读请求引用。

实现额外复用了当前基线已交付BE-22的RequestVisibility，故接入本分支时应包含main/7914788及其祖先；没有复制第二套申请可见性规则。ImpactPolicy已按真实失败测试修正角色部门与来源部门的独立有效任职复核。无新迁移、Maven依赖、环境变量或公开契约变更。

内部接入：`RequestInvalidator.sources(context, affectedFacts, requestId, reason)`处理来源/关系事实；`invalidate(context, Changes, requestId, reason)`统一处理grantFacts、inactiveEmployees、employmentChangedEmployees、changedScopes、roleVersions。调用方先在同一WriteContext中写业务，再调用失效端口；不独立提交。changedScopes会重读原范围对全清单的当前覆盖，原申请人离任不单独失效。角色配置只使旧授予失效，不机械取消准确撤销。返回内部Invalidated列表的auditId为GLOBAL审计，客户端披露仍由外层安全投影负责。BE-27的HTTP入口/员工状态/范围替换和BE-30实际导入仍由原任务完成。

验收重跑仅使用显式非3306专属MySQL，设置RBAC_ISOLATED_MYSQL_PORT/RBAC_ISOLATED_MYSQL_DATADIR后，在backend执行 `./mvnw.cmd -q test-compile failsafe:integration-test failsafe:verify -Pmysql-check "-Dit.test=SourceClosureMySqlCheck"`。测试自行创建、清理随机库和最小DML账号，禁止指向dev/test库。OA固定状态使用持久测试夹具，不代表真实OA或C执行器已接通；完整回归保留BE-16底层双连接关闭/执行竞争证据，本任务另验证两个HTTP连接竞争关闭。

BE-26为IMPLEMENTED，D本人验收、合并和推送尚未进行；任务分支保留。可先核对上述断言及实现，再单独记录负责人确认和主线交付，不自动开始BE-27/30。


2026-10-21实施接入：成员D明确“开始执行BE-26”。远程main/7914788已核对BE-20通过D-088交付a9b214c，含WorkflowStateWriter.invalidate(WriteContext, requestId, expectedStatusVersion, reasonCode)与ClaimService；直接依赖全部满足。复用该端口，RequestInvalidator只负责受影响事实选择、失效原因及内部结果；直接关闭使用SOURCE_EPOCH_CHANGED。下方C/BE-20未交付为准备阶段历史。本次实现、D负责人验收及主线交付分别记录，不自动合并推送或开展BE-27/30。

首轮真实RED：be26-http-valid-red.log，4项业务断言失败、0错误、0跳过，预览路由404。此前PowerShell参数错误及夹具缺必填历史引用不作为有效RED。新增实现前已记录下方11组验收行为。

接入细化：保存幂等规范输入不包含可替换的任命上下文，当前单项授权及持久AuthorizationBasis在每次回放前复核；固定预览确认仍绑定创建上下文。统一失效端口同时接受来源周期、准确关系、员工资格、原范围覆盖及角色配置版本事实，业务写入与整份申请状态/claim/outbox/完整内部审计同事务。公开来源关闭审计仅含当前可读信息，混合申请完整失效审计使用GLOBAL，不把其ID计入不可见响应。全部端口在异常时标记调用方回滚，不设独立事务或新增公开内部入口。

2026-10-20/D-093：成员D要求完成BE-29推送后继续下一项。BE-29已交付远程main，当前基线为交付记录提交86089d2，在codex/be-26-source-closure准备本设计与验收清单。

2026-10-20准备阶段：当时BE-26为PLANNED、C/BE-20尚未交付，仅设计与清单，无实现或本任务Java/SQL/HTTP测试。该限制已由上方2026-10-21远程交付核对解除；BE-30仍缺A/BE-27，不能先行替代本任务。

## 依据与接入门槛

- 需求：[UC-04直接关权子流程及UC04-D01～D05](../01-requirements/usecases-v1/UC-04_撤销员工角色.md)、[管理范围与人员权限规则](../01-requirements/usecases-v1/管理范围与人员权限规则.md)，沿用V1.3。
- 契约：[OpenAPI](../03-api/openapi.yaml)的sourceClosePreview、closeSource、SourceCloseInput/Save/Result，以及既有Preview读取；保持现有字段、状态码和错误枚举。
- 事务：[F5](CORE-FLOWS.md)及[内部端口](ARCHITECTURE.md)，使用T0/T5/T11、同一WriteContext；验收映射AT-15/16/19/32/41只登记本任务局部证据。
- 结构：[schema.sql](../04-database/schema.sql)及V1～V3；复用authorization_source、grant_baseline、role_grant、change_request/target、claim、oa_outbox、impact_preview/item、audit及http_idempotency，不因缺实现新增同义表。

接入BE-20时核对实际来源及主线交付提交、正常草稿/claim写入口和失败回滚测试。明确其事务内claim释放与状态读写端口，避免D/C各写一份不同的申请状态机。仅有空接口、DDL、Mock或文档不能算依赖已交付。BE-21～25的正式提交、OA及执行全链路各按原任务完成；本任务可使用明确标注的持久测试夹具覆盖固定申请状态，不能据此宣称真实链路已接通。

## 模块和职责

| 组件 | 本任务职责 | 复用与约束 |
| --- | --- | --- |
| 来源关闭输入解析与控制器 | 两个既有POST，严格字段/ID/长度/数值和重复systemId检查 | 复用现有身份解析、Problem、no-store及IdempotencyHttp，不直接访问写Repository |
| LifecycleCoordinator | 外层guard内复核单项范围、固定预览、全部来源版本，编排关闭、申请失效、审计和响应 | 新增业务实现放在既有架构的M10；不修改HR任职、员工全局状态或管理员任命 |
| BaselineWriter / SourceCloser | 物化未存在的逻辑0来源，按精确epoch/version关闭全部所选来源 | 已交付BE-16；无有效grant仍推进，返回完整AffectedFacts；不反调申请服务 |
| RequestInvalidator | 按完整受影响事实整份失效已提交申请，释放该申请全部claim并停止待办 | M07申请状态/claim存储与C/BE-20衔接，端口使用调用方WriteContext，不独立提交 |
| PreviewService / ImpactService | 冻结输入、依赖及安全影响，确认时重新计算并比较 | 已交付BE-17；完整内部影响与当前可披露投影分开 |
| 幂等与审计 | 原子保存完整变更和成功结果；重放先检查当前权限 | BE-04/05；不可吞掉审计异常，不向客户端泄露完整混合范围事件 |

RequestInvalidator是统一内部编排端口，本任务首先接入来源关闭；后续员工/任命/原范围变化由BE-27调用，角色配置执行由执行编排调用。返回内部失效事实供安全投影，不把调用方可见范围作为实际失效过滤条件，不使M06反向依赖M07。具体Java签名在BE-20落地后确认，不先建立无人实现的接口。

## 两个操作的流程

`POST /api/v1/admin/source-closure-previews` 返回200 Preview：

1. 严格解析employeeId、companyId、departmentId、1～100项systems及reason。拒绝重复系统、未知字段、非法版本和公司/部门不一致。总管理员用GLOBAL上下文，权限管理员仅当前一项任命，不拼接多项范围。
2. 在既有guard事务内重新解析当前身份和双版本。全部来源必须属于所选公司、部门及获准系统；角色归属与来源部门不同不自动扩大管理范围。
3. 读取逻辑来源基线及待执行申请，在同一快照构造关闭变更交ImpactService计算完整影响；预览不关闭来源、不物化grant或改变正式业务状态。
4. 通过PreviewService存储固定输入/依赖和安全投影，沿用其创建者、原单项任命、300秒有效期及既有分页读取。预览绑定完整规范输入，包括原因与全部版本。

`POST /api/v1/admin/source-closures` 返回200 SourceCloseResult：

1. 解析input和previewId、Idempotency-Key，复用既有幂等请求规范化。当前身份/范围复核必须在历史结果重放前执行；同key异输入仍拒绝。
2. 外层同一guard内重新读取当前身份、选定任命双版本、全部来源和影响依赖，确认预览属于当前调用者/上下文、操作和完整输入且未过期。依赖或输入变化返回现有PREVIEW_STALE，来源版本冲突按现有SOURCE_VERSION_CHANGED边界处理，不能使用旧预览直接覆盖。
3. 使用BaselineWriter和SourceCloser处理全部来源；先核对全清单再写。无持久来源时按BE-16逻辑0物化并推进；有历史来源时继续推进，不重置周期、不新建同键历史。
4. 把AffectedFacts交RequestInvalidator，完整失效命中的已提交申请，释放每份申请全部claim，停止其待发送/已领取outbox。全部动作与grant结束、来源推进共享WriteContext。
5. 保存成功审计及幂等HTTP结果，一次提交。返回updatedSources以及当前可见、按ID去重的grant/request计数和可读auditEventIds。任意业务、状态、claim、待办、审计或结果保存失败，整个事务回滚。

只关闭选中原部门来源；其他公司/部门/系统的独立来源、员工身份、任职、业务角色配置保持。重新授予仍需新申请批准执行；后续恢复任职或全局身份不复活旧周期和旧申请。同key重放不再次推进周期，新key仍必须重新预览并使用新的准确版本。

## 申请失效和披露规则

候选仅为PENDING、APPROVED、EXECUTION_FAILED。ROLE_GRANT按固定目标的准确来源与旧周期命中；ROLE_REVOCATION按本次实际终结的准确grantId命中，不按员工姓名、部门短名或角色归属粗略匹配。命中任一行即整份INVALIDATED，不保留或执行合法子集。配置申请不因不相关来源关闭而失效。

状态转换保持数据库三轴约束：执行状态INVALIDATED，推进status_version及存储版本，设置既有安全原因码，清除next_retry_at；投递状态、可信审批结果与固定内容保留历史。已终态申请、execution_success和固定字节/摘要不改写。DRAFT保持可修复/删除，不伪造已提交或终态；其claim生命周期沿用C/BE-20的正式规则，不能把草稿当作已提交申请批量释放。

对命中申请释放全部role/relation claim，以requestId和所有权校验为依据，不能释放后来属于其他申请的占用。对READY/LEASED待办设置STOPPED并清除租约；DONE/STOPPED历史不倒退。后续迟到OA结果只保留历史，不恢复INVALIDATED；该回调语义的完整实现由C的相应任务验证。

实际失效必须覆盖跨范围混合申请；响应只有当前单项范围可完整读取的申请才计数或披露ID。隐藏实际影响使用SCOPED/hasUndisclosedImpact=true，不能返回其数量或借全局0暗示没有影响。可见安全审计可单独记录，完整内部事件不得当作可读auditId返回。授权只要求覆盖本次选中来源及实际披露对象，不要求调用者额外管理隐藏混合申请的其他部门。

## TDD顺序与可核对断言

按仓库[test-driven-development](../../.agents/skills/test-driven-development/SKILL.md)及writing-good-tests.md实施。以下为实施前制定的验收计划，实际执行证据见上方结果；计划本身不作为RED/GREEN证据。

| 顺序 | 实际业务测试 | 应捕获的问题 |
| --- | --- | --- |
| 1 | 真实HTTP调用两个未实现路由，断言预览/保存契约 | 先观察业务断言失败；不把编译错误当RED |
| 2 | A/B部门同时授予，关闭A，仅A来源推进且grant失效；B和其他公司/系统保留 | 过宽关闭或按角色归属误关来源 |
| 3 | 来源无grant甚至尚未物化，关闭后周期仍推进；后续新请求用新版本 | 仅删除现有grant造成旧批准复权 |
| 4 | 仅待审批/已批准/失败待重试的固定授予；多目标混合申请任一命中 | 只改部分目标、漏掉无grant的旧申请或漏释放其他行claim |
| 5 | 准确撤销申请与本次结束grant匹配；不相关配置/其他来源、DRAFT和终态不变 | 按员工或角色过宽失效、草稿伪终态 |
| 6 | 全局总管理员；正常权限管理员单项范围；双版本过期、越系统/部门、受限会话、未登录 | 多任命拼权、任命失效后仍可重放 |
| 7 | 混合范围实际全部失效，但仅可见计数/ID和可读审计 | 以展示过滤裁剪实际失效、隐藏数量泄露或错误403 |
| 8 | 预览过期/他人/输入变化/空集合新增申请或grant/版本变化 | 只检查旧对象版本而漏掉新依赖 |
| 9 | 同key相同输入不再推进；同key异内容拒绝；新key配旧预览拒绝 | 重放重复写、绕过当前授权或固定预览 |
| 10 | 第N来源、第N申请、claim/outbox、成功审计/HTTP结果落库故障 | 来源、grant、申请、待办、guard及幂等结果部分提交 |
| 11 | 两个真实连接：关闭先提交与执行先提交两种顺序；并发新申请/同key | 锚点外读取、旧批准复权、双成功或占用所有权丢失 |

并发验证在执行器尚未交付时可使用真实事务及底层写端口的受控测试夹具明确标记机制范围；后续BE-25/33还须补正式执行器端到端。不得将该夹具报告为真实OA批准/执行完成。

各阶段先记录独立失败断言，再最小实现、定向GREEN及完整回归。只启用仓库临时datadir、非3306专属MySQL8.4和随机schema/最小DML账号，结束后回查资源0并正常关闭；不操作用户dev/test库。实现后记录准确测试数量、jar摘要、日志与清理结果，不沿用BE-29的137＋240冒充BE-26验证。

## 后续交接

1. D核对本任务34项断言、完整137＋318证据和自检结果，另行确认负责人验收；本轮不代记OWNER_ACCEPTED。
2. 获得本任务主线交付指令后，先核对新远程main并处理增量，必要时集成重验，再合并推送并回查SHA；本轮只本地提交。
3. A核对BE-26实际主线交付后可按依赖实施BE-27。D的BE-30仍等待BE-27及已交付BE-29，不由本任务代实现其他成员任务。
