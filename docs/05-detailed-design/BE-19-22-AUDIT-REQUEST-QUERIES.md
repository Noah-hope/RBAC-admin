# BE-19/22 审计与申请读取

D-090（2026-10-21）：C明确“确认，然后继续完成剩下的任务”，接受BE-19/22既有137离线＋284真实MySQL/HTTP证据；人工演示未进行。来源cc88885f2c97df838720fc2f7f0e524d72e56351已no-ff合入main/cf27f7f910bf5e904c89542f56e3f56fddd884d9，合并树等于验证来源，main及任务分支原子推送且远程完整SHA回查一致。两项OWNER_ACCEPTED、mergedToMain=true。后续按BE-21→23→24→25→31连续实施，依赖沿同一任务分支继承，各项实现、本人确认、主线交付分开记录；不代其他成员验收、不部署。共13项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、12项PLANNED。下方待确认/待交付为历史。

## 范围与计划

2026-10-21：C用户要求继续其他任务。本轮从共同main/09abaa2建立BE-19-22-审计与申请查询，连续实现BE-19的listAudit/getAudit及BE-22的listChangeRequests/getChangeRequest/listChangeEvents。两个任务复用完整申请可见谓词，状态与验证分别登记；不修改已交付BE-20业务、不实现提交/OA/执行或自动确认负责人验收。本轮未授权自动合并推送，完成后提供具体交接。

依据UC-02/03/04/07/08/11、OpenAPI1.2.0、T9/T11、I-11/12/19和AT-17/18/19/32/34/37的本任务局部。先详细设计→真实HTTP/MySQL RED→纯读取实现→权限/历史/分页/异常验证→完整回归与资源清理→追踪和本地提交。

## 结构与事务

web/audit与web/change读取控制器→AuditQueryService/ChangeQueryService→各自JDBC投影；query/change/RequestVisibility统一整份当前单项范围SQL，读取当前角色固定归属、change_target及scope_system。沿用ReadBoundary在主库新RR快照中完成当前JWT/任命/双版本和所有总数/明细查询。不打开写事务、不追加查询成功审计、不改变草稿、固定申请、claim或原事件。

- 总管理员全局可查；权限管理员必须在当前一项任命内完整覆盖所有申请目标、来源部门及角色可见归属；公司级共享角色配置只全局，部门内共享角色授予/撤销可见。不得拼接任命，不依赖原提交者当前在岗或原scope身份。
- SQL先过滤完整可见申请再COUNT/分页，不能先分页后裁剪。空目标/损坏投影不默认授权。详情隐藏与不存在404，显式越范围筛选403。
- 审计SCOPED按事件产生时固定公司/来源部门/系统，再检查其关联的现存申请是否整份可见；GLOBAL始终仅总管理员。已删除草稿的事件用固定事件来源授权，不依赖已删除目标。混合不可安全拆分事件不得按可见目标裁剪。
- 审计主列表occurredAt DESC/id DESC；申请列表createdAt DESC/id DESC；申请时间线occurredAt ASC/id ASC。from含、to不含，要求带时区且前后合法；稳定标识筛选精确匹配，q为转义编号检索。
- 所有七种申请状态使用完整契约投影，固定原资格/摘要/时间和三轴分开输出；APPROVED及EXECUTION_FAILED才retryable。公开失败码按CONVENTIONS白名单；存储错误或坏结构返回500/503，不能伪装为空或猜测修复。
- 审计详情仅输出白名单历史差异，不直接暴露event_snapshot/原回调/异常。使用事件时snapshot中的版本、审批编号及变更前后值，不从当前业务状态倒推；未知敏感字段不进入响应。字段及值长度、差异数量遵守AuditDifference契约。
- 时间线先完整授权申请，再按同一审计可见谓词过滤事件与total。内部事件名显式映射公共ChangeEvent枚举，message为安全固定说明，不透传原始失败信息。GLOBAL混合事件不因已知申请ID而下放。

## 验证场景

使用专属MySQL8.4非3306实例、随机schema与DML账号，真实HTTP证明缺路由的RED，再实现。覆盖五路由、全局/当前单项/同范围协作、共享角色与跨部门/系统混合目标、原提交者离任、申请范围变化、删除后审计历史、显式过滤、权限后的total、稳定分页、时区半开区间、原始敏感快照隐藏、完整状态分支、时间线事件映射、查询无写入、JWT入口隔离和数据库故障。失败不改既有库与业务状态。实际测试和清理结果在结束后补充。

## 实际实现与交接

| 任务 | 接口 | 交付 |
| --- | --- | --- |
| BE-19 | GET /api/v1/admin/audit-events | 当前可见事件列表及精确筛选、半开时间区间、安全总数与降序分页 |
| BE-19 | GET /api/v1/admin/audit-events/{auditEventId} | 公共历史差异与事件时元数据，隐藏原始快照和未公开字段 |
| BE-22 | GET /api/v1/admin/change-requests | 整份可见申请列表、公司/系统/编号及三轴状态筛选 |
| BE-22 | GET /api/v1/admin/change-requests/{requestId} | 七种完整状态模型，固定原资格/摘要/提交版本及服务端重试标志 |
| BE-22 | GET /api/v1/admin/change-requests/{requestId}/events | 完整当前授权后的安全时间线，公共事件枚举及固定安全说明 |

五接口均只接受后台身份，使用当前一项任命/双版本，敏感响应no-store；同名重复查询参数与非法分页/状态/时间422，显式越范围筛选403，详情隐藏/不存在404。未新增写入口、数据库迁移或依赖，不改BE-20业务与其他成员模块。

RequestVisibility对内容中的kind/company、目标数量、每行明确角色/来源/员工/历史grant和输入版本与change_target逐项比对。缺失或错配投影不能缩减整份授权；受限查询不放行，总管理员详情返回500 DATA_INTEGRITY_ERROR。比较输入与固定投影，不机械比较当前业务版本，故来源关闭、任职结束或角色配置变动不会抹掉历史读权限。关联审计同时检查change_request_id与REQUEST/CHANGE_REQUEST主体；删除记录可按audit_subject定位，已删除申请的来源授权仍由事件固定来源决定。

AuditProjection按明确公开字段投影before/after及现有role/system/scope/draft/targets/snapshot形状。嵌套角色名称取历史快照，不查询当前名称。密码、令牌、私钥、手机号及未知字段不展开；不把原SQL/异常透传为失败码。每个公开差异字段最长200、前后值最长2000字符，最多100项；过长值或摘要数量限制有明确截断说明，不声称接口提供无损原始快照或审计导出。当前事件没有保存的历史版本/审批编号保持null，不以当前状态猜测补齐。

申请读取不实现BE-21的RFC8785固定提交或BE-25执行完整性检查；本任务读取固定摘要字段并保护响应结构，不从当前名称重建固定字节。可信OA关联/结果和执行成功记录在部分读取测试中由专属持久夹具建立，不能据此声称真实提交、OA、执行器或外部服务已接通。

## 可执行验收步骤

1. 沿用现有身份与数据库配置启动后端，使用实际后台账号登录；测试夹具仅在专属实例运行，不写已有开发库。
2. 通过已交付BE-20创建角色/草稿，保存roleId、draftId和当前任命/双版本。读取申请详情及时间线，确认DRAFT、未提交字段为空、DRAFT_SAVED事件及no-store。
3. 查询当前公司审计列表，读取新事件详情，确认可见总数、角色历史名称与允许公开的差异；同范围其他管理员可协作查询，跨部门/系统详情与不存在统一404。
4. 以总管理员查询跨部门混合草稿；仅覆盖部分的权限管理员不应看到该申请、关联事件或其数量，显式请求该申请筛选返回403。
5. 删除未提交草稿后，申请详情404，但原范围内审计仍可按changeRequestId筛选到创建/删除事件；历史不被重新归属或覆盖。
6. 原提交人离任不抹掉已提交申请；当前查看资格失效或上下文版本变化仍按现有401/409规则拒绝。正式OA各状态须在BE-21/23/24/25实际交付后联调，不能把本轮读取夹具当作全链路验收。

## 实际验证记录

2026-10-21，macOS/Corretto JDK21，官方MySQL8.4.8 ARM便携实例127.0.0.1:23319，backend/.tmp/be19-22-mysql-data，禁用binlog。原隔离夹具实际核对datadir/version/binlog后创建随机schema与最小DML账号，没有连接已有开发/测试库。

TDD过程：be19-22-red.log的9项真实HTTP测试全部断言失败、错误0，原因是尚无五个读取路由（404/405）；实现后9项通过。be19-22-subject-time-red.log的11项中2项断言失败：REQUEST主体关联绕过整份申请可见性、按删除草稿编号漏掉删除事件，随后修正。be19-22-projection-red.log的14项中1项断言失败：删除混合草稿的目标投影后原被误判可见，补SQL内容/投影一致性后受限404、全局500。补测因故意损坏数据留下坏行导致后续全库查询出现预期完整性错误，已将独立测试限制到各自测试公司，未放宽生产校验。be19-22-historical-name-red.log的19项中1项断言失败：嵌套草稿快照中的原角色名未展开，补公开snapshot容器映射后通过，且当前角色更名不会改写历史展示。

最终命令：在backend使用显式JDK21与专属RBAC_ISOLATED_MYSQL_PORT/RBAC_ISOLATED_MYSQL_DATADIR运行 `sh ./mvnw -q verify -Pmysql-check`，日志be19-22-release-verify.log。前序也运行过clean verify，最终报告为 **137项离线/20组＋284项真实MySQL/HTTP/21组**，失败/错误/跳过均0。本轮新增 **19项真实HTTP/MySQL检查**，覆盖五接口、完整当前范围/混合事件/主体关联、共享角色、删除历史、原提交人离任、七状态及安全失败、分页与时间边界、只读无副作用、存储故障、损坏投影/原资格、敏感快照屏蔽、摘要限额与历史名称。

jar SHA-256：`c9bbbf6ccbd42bf67574dce8f052e11fdd8351c1a91f3439e1017b713ab4059d`。设计静态追踪校验PASS。测试结束回查随机schema/账号0/0，测试密钥目录0，专属MySQL PID10911正常关闭；临时datadir和便携下载随后清理。Maven XML报告在backend/target，详细日志在忽略的backend/.tmp。

BE-19与BE-22分别登记IMPLEMENTED、ownerAccepted=false、mergedToMain=false。负责人本机人工验收未进行，不自动标记OWNER_ACCEPTED；仅本地任务分支提交，无推送/合并/PR/部署。当前C共8项：BE-20已确认交付，BE-19/22本轮实现待确认，其余BE-21/23/24/25/31仍PLANNED。AT-18仅读取固定字段局部，不代替JCS向量/真实提交；AT-32不代替BE-26真实关权；端到端交叉验证仍由BE-33汇总。

交接校正：本轮读取按权威CONVENTIONS校验公开失败码，因此同步BE-20文档的来源关闭示例为SOURCE_EPOCH_CHANGED；SOURCE_CLOSED是内部事件概念，不应写成公开failureCode。仅修正文档示例，BE-20生产实现和已有验证事实保持不变。
