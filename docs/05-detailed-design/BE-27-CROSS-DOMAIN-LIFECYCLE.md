# BE-27 员工状态与管理范围替换

D-100（2026-10-21）：A明确“交付BE-27，推送远程”，授权推送任务分支、创建main目标PR并合并、同步本地主线及实际交付记录。已核对验证来源72daaac、最新main/8ad17e4、完整139普通＋406真实MySQL/HTTP及18辅助结果、jar摘要和资源清理；代码/测试未变，沿用既有证据并做静态核对，不重跑Java/SQL。交付不代记本人验收，BE-27保持IMPLEMENTED、ownerAccepted=false；保留任务分支和无关.codeartsdoer，不强推/部署/实施BE-30。实际PR和合并SHA在成功后补；下方不推送/合并为此前边界。

2026-10-21：A要求更新本地并开始开发，已快进至main/8ad17e4，分支codex/be-27-cross-domain-invalidation。实现、负责人确认、主线交付分别记录；本轮不推送、合并或部署。

## 依据和边界

沿用UC-07、UC-10、管理范围与人员权限规则V1.3、OpenAPI的employeeStatePreview/setEmployeeState/scopeEditPreview/replaceScope，以及数据库T6/T7/T9。直接依赖BE-07/08/09/26均已主线交付，其他成员验收不代确认。

四个入口只允许当前总管理员。预览固定五分钟且绑定操作者、操作、目标、规范输入和依赖集合；确认与写入使用同一guard事务。复用BE-05幂等、BE-17安全预览、BE-16 SourceCloser、BE-26 RequestInvalidator及C状态机，不复制申请状态转换或来源更新规则，不新增迁移、环境变量或API字段。

## 模块与事务

- lifecycle/EmployeeStateWriter：受控内部写端口供HTTP及后续BE-30调用。接收现有WriteContext、准确员工和版本；防止当前/唯一可用总管理员自锁。停用推进员工版本、全部已物化来源周期、结束ACTIVE授权和任命、推进关联账号会话周期并撤销会话，整份失效旧授予/撤销申请；恢复仅改员工状态和版本。失败标记外层回滚。
- lifecycle/ScopeReplacementService：验证完整公司/部门/系统/账号集合。新增系统必须可用，保留已登记旧系统不因停用阻止移除；名单去重，新增和换部门后的任命必须符合账号/员工/任职资格。未变任命保留ID和版本；移除REVOKED，新部门失资格者INVALIDATED；重新加入创建新ID。实质变化才推进scopeVersion；无变化不更新业务版本。
- lifecycle/LifecycleService：外层当前总管理员、规范输入、固定预览确认、幂等结果与审计。两个控制器适配既有四个路径。内部写端口不启动新事务、不接收JWT绕过写上下文。
- 范围替换后仅原scopeId实质失覆盖的已提交整份申请失效；名单变化或无关系统增加不使合法申请失效。失去最后有效任命才推进sessionEpoch并撤销会话；有其他合法任命保留会话，旧选择因版本变化拒绝。范围调整不改业务授权。
- 依赖摘要包含准确业务版本以及来源、关系、任命、会话、任职、原范围申请集合，含空集合，防止预览后新增目标漏处理。所有写入/审计/claim/outbox释放/幂等成功同事务；存储异常整份回滚。

## TDD验收清单

先真实HTTP/MySQL RED，再实现并完整回归：员工停用/恢复与NO_CHANGE；多部门多系统授权及空来源推进；旧申请整份失效/claim和outbox取消；全部任命会话失效及恢复不复权；当前/唯一总管理员保护；范围完整替换、空集合和NO_CHANGE；保留/撤销/失效/重新任命ID；仍有其他资格保留会话；失去最后资格撤销会话；原范围实质失覆盖与扩回不复活，名单替换不取消合法申请；预览绑定/过期/版本与集合变化；幂等内容冲突/成功回放/当前资格；非法/缺失/重复字段；非总管理员和未登录拒绝；第N项及审计故障回滚；两个连接并发变更只成功一个版本。

## 验证记录

完整 `./mvnw.cmd -q clean verify -Pmysql-check` 已通过：139项普通/21组、406项真实MySQL/HTTP/26组，失败/错误/跳过均0，其中CrossDomainLifecycleMySqlCheck为31项。本轮日志backend/.tmp/be27-full-verify.log，汇总backend/.tmp/be27-full-evidence.json；18项辅助工具测试与设计静态校验通过。构建包SHA256：`17b62120d8f32933c0e478063b3d9ffa715a9f2e59e74864960a4253d6171209`。

专属随机库/账号/BE-27密钥目录均0，专属MySQL已正常关闭，原MySQL84仍Running，见backend/.tmp/be27-cleanup.log。BE-27为IMPLEMENTED、ownerAccepted=false、mergedToMain=false；没有进行本机人工演示，不自行记为OWNER_ACCEPTED。共有13项OWNER_ACCEPTED、13项IMPLEMENTED、5项IN_PROGRESS、4项PLANNED；其他负责人验收保持。

| TDD阶段 | 实际观察 |
| --- | --- |
| 首轮有效RED | be27-valid-red.log：8项断言失败、0错误，四入口未实现返回404 |
| 首批实现及边界扩展 | 修正员工夹具初始版本、固定申请冻结时机、JSON语义比较、既有STOPPED状态及合法预览过期时间；未把夹具错误作为通过证据 |
| 版本与固定ID RED | be27-details-red.log：来源版本耗尽返回500而非409、新任命ID未沿用预览；对应修正后通过 |
| 完整审计 RED | be27-audit-red.log：1失败、0错误，缺准确grant/appointment ID；补完整事实及审计主体后通过 |
| 定向GREEN | be27-targeted-green.log：29项通过；故障注入的预期500日志不代表测试失败 |
| 内部操作者 RED | be27-port-red.log：2项中1失败、0错误，SERVICE同ID可冒用内部ACCOUNT端口；添加actor kind校验。业务查询停用及恢复后拒绝已通过 |
| 最终完整GREEN | 139普通＋406真实MySQL/HTTP，其中本任务31项全部通过；无失败/错误/跳过 |

辅助工具首轮受默认沙箱临时目录权限影响，设置进程级TEMP/TMP指向仓库忽略目录后完整18项重跑通过，未改工具实现。既有演示脚本需要python3，本轮仅使用忽略目录内临时入口指向实际Python，不修改永久环境变量或用户配置。测试仅使用非3306的本任务专属MySQL8.4实例、随机schema和DML账号，不连接用户现有dev/test库。不会把静态检查或演示OA等同真实服务器/外部OA验收。

## 本机接口核对

使用已经启用身份与幂等的BackendApplication和正常总管理员登录token，无需新密钥或新环境变量。Apifox使用本地环境保存token，不保存共享凭据；可选X-Request-Id留空时取消勾选，或提供一个合法非空值。写请求每次新操作使用新的UUID Idempotency-Key。

| 操作 | 方法与路径 | 输入/结果 |
| --- | --- | --- |
| 员工状态预览 | POST /api/v1/admin/employees/{employeeId}/state-previews | EmployeeStateInput，200固定预览 |
| 员工状态保存 | PUT /api/v1/admin/employees/{employeeId}/state | input原样＋previewId，200 EmployeeStateResult |
| 范围替换预览 | POST /api/v1/admin/management-scopes/{scopeId}/previews | ScopeEdit，必须完整提供systemIds/accountIds，200固定预览 |
| 范围替换保存 | PUT /api/v1/admin/management-scopes/{scopeId} | input原样＋previewId，200 ScopeResult |

先GET读取目标员工或范围当前version，不能照抄示例版本。选择已有测试员工，避免当前总管理员关联员工；调用员工预览时提交expectedVersion、status=INACTIVE及reason，使用返回id和完全相同input确认。核对员工INACTIVE、授权/任命失效、旧后台token被拒绝；再用最新version预览并确认ACTIVE，旧授权/任命/会话仍不恢复。无演示数据时以专属随机数据自动测试为证据，不为了手工演示直接改业务库造数据。

范围操作先GET读取当前version、公司、部门、系统集合及ACTIVE任命。完整替换时移除一个系统或账号，确认预览中的影响后保存；检查保留任命ID不变、移除任命持久结束。范围重新扩大不复活失效申请，重新任命创建新ID。仍有其他有效任命的账号保留会话，但旧上下文头须换为最新appointmentVersion/scopeVersion；失去最后有效资格的账号需重新任命并重新登录。

相同Idempotency-Key与相同输入再次发送，返回原成功结果且不重复推进版本；不同内容复用key返回409。预览超过五分钟或依赖变化需重新预览。没有实质变化返回NO_CHANGE，不推进业务版本。

## BE-30 接入

通过容器取得EmployeeStateWriter，在已由总管理员授权的整批WriteContext中调用apply(context, employeeId, new LifecycleInput.EmployeeState(expectedVersion, status, reason), requestId)。它再次检查ACCOUNT操作者、当前总管理员及员工准确版本，沿用本事务推进来源/关系/任命/会话并失效申请及追加审计；返回实际员工版本及影响计数。禁止为每个员工重新创建CommandBoundary或在批次中独立提交；即使捕获此端口异常，外层事务也不能提交。

该端口只处理员工全局状态；结束任职后的适用资格重算、导入冻结计划校验、整批历史及失败恢复仍属于BE-30。恢复状态不恢复任职、授权、任命或会话。BE-27本地实现尚未共同主线交付前，不将D的BE-30正式依赖记为已交付。
