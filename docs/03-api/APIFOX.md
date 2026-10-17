# Apifox 同步与验证记录

项目ID：`8934976`；目标模块：默认模块。令牌只使用本机环境变量 `APIFOX_ACCESS_TOKEN`，不进入仓库、截图、示例或命令输出。该变量是Apifox工具凭据，不能作为正式业务API的管理员/业务系统/OA令牌。

## 权威来源与同步方向

本轮确定：Git中的 [openapi.yaml](openapi.yaml) 是权威接口契约，Apifox用于接口展示、协作、Mock和调试。同步方向为 **Git校验后的契约 → 人工文件导入Apifox → MCP刷新和回读核对**。远程编辑只能先导出、比较、合并回Git并评审，然后重新校验/导入，不能两处各自演进，也不直接用导出覆盖本地权威文件。

当前MCP实际暴露三个工具：`read_project_oas`、`read_project_oas_ref_resources`、`refresh_project_oas`（运行时带后缀）。未提供创建、修改或导入工具；不把“有令牌”和“可读取”当作已能写入。也不在本任务暗中换用未验证的第三方写入MCP。

## 人工导入操作

1. 切换项目8934976，确认默认模块；首次导入前导出远程备份，核对当前没有其他成员新增内容。
2. 按 [Apifox手动导入说明](https://docs.apifox.com/manual-import) 在项目设置的导入入口选择OpenAPI/Swagger文件，选中本目录 `openapi.yaml`。具体界面随客户端版本可能变化，以导入预览为准。
3. 检查导入预览中的路径、方法、模型、示例和认证方案；后续更新按方法＋路径匹配更新，保留已存在接口关联，不能使用会删除无关内容的整项目覆盖。发现远程独有接口先停下比较，不擅自删除。
4. OpenAPI 3.1 的 webhooks 是出站 `oaSubmission`，不是权限中心入站路径；若当前Apifox版本未显示webhooks，在项目说明关联本契约出站章节，记录能力缺口，不能宣称完整导入。若客户端不支持3.1字段，不静默改成3.0丢失const/null/约束，先记录转换差异再提供衍生导入文件。
5. 导入后记录契约SHA-256、info.version、Git提交、操作者、时间、项目/模块、导入模式及实际接口数。此处Git提交是导入所依据版本，不能用尚未发生的提交号冒充。

## MCP回读与语义比较

导入后调用refresh，再read；所有 `$ref` 路径用read_project_oas_ref_resources分批读取，包括paths、schemas、responses、securitySchemes，不能只数根文档路径就宣布一致。平台可能拆分文件、重排键、添加x-apifox信息或下载时间，不能以字节一致作为唯一标准。

必须比较：每个方法＋路径及operationId、请求/响应类型/必填/枚举/null约束、状态码、分页/筛选和版本头、三类鉴权绑定、模型引用、示例以及出站OA契约。对接口清单逐项比对，抽查ROLE_GRANT、ROLE_REVOCATION、预览及回调重复冲突响应。导出OpenAPI并运行本地校验也是必要检查，不能只靠Apifox界面显示成功。

差异分为平台元数据（可忽略）、展示性差异（记录）、语义缺失/改变（阻断同步完成）。回读验证通过后再在本文件追加同步记录和对应Git提交；保留历史，不覆盖原先“未同步”事实。

## 本轮实际结果

| 项目 | 已验证事实 |
| --- | --- |
| 启动读取 | 2026-10-16调用refresh成功，服务端下载时间`2026-10-16T08:39:47.047Z`（北京时间16:39）；OpenAPI3.1.0，模块版本1.0.0 |
| 模块内容 | paths={}、webhooks={}；默认模块0接口，没有声称整个Apifox账号/其他模块为空 |
| 回读 | 调用read成功，返回同一次下载快照；这里只验证本轮空模块可读，不是本地新契约的回读 |
| 收尾刷新 | 2026-10-16T09:06:16.900Z（北京时间17:06）再次刷新成功，默认模块paths/webhooks仍为空 |
| 远程写入 | 未进行；MCP无写入/导入工具，本轮未操作Apifox桌面界面导入 |
| 契约同步 | **待人工导入及完整回读核对**。本地OpenAPI设计校验和Mock不替代远程同步完成 |

本地契约文件SHA和接口数量以 [VALIDATION.md](VALIDATION.md) 最终验证记录为准。尚未同步不影响本次本地API设计交付，但正式数据库/实现消费契约时需明确使用Git版本，不从空Apifox项目推断没有设计接口。

## API评审修订检查（1.1.0）

本次以269d269为评审基线；启动刷新下载时间2026-10-16T14:47:23.323Z、收尾2026-10-16T15:12:56.488Z，均为UTC（北京时间22:47、23:12）。read根文档成功，三项组件引用schemas/responses/securitySchemes均回读为{}，paths/webhooks仍空。当前MCP仍仅读文档、读引用及刷新；没有写入/导入工具，也未操作客户端导入。

待导入文件为本目录openapi.yaml **1.1.0**：56个路径、67个入站操作、1个出站OA webhook、129个模型。应以本次评审修订提交及VALIDATION中的SHA为导入基线；不要继续导入269d269的首版。现已准备好文件及前述人工步骤，**实际导入和导入后的完整回读核对仍未完成**。2.64.1 lint的启发式告警及3.1/null/oneOf在Apifox客户端的实际呈现均需记录，不能为显示方便静默弱化边界或改成不等价的3.0文件。


## 用户导入后的完整 MCP 回读（2026-10-16）

用户在本聊天确认已手动导入；客户端版本、具体导入选项和完成时刻未提供，不作推定。基线为471892c中的1.1.0，文件SHA-256仍为ec7ef9ef1ab046bb61cec9728c423c32f58311c850860be8ef54a9cf3a4798fd。MCP刷新下载时间2026-10-16T15:41:12.514Z（北京时间23:41），根文档及递归发现的全部203个外部引用文件均读取成功，无未读取外部引用。按引用映射恢复组件名，比较全部56路径/67操作、129模型、11公共响应、4认证方案及参数；忽略对象键顺序、平台扩展、参数排列、默认false、单示例转命名示例等等价表示。原始差异数不等于问题数。

结论：**已发生人工导入，完整MCP回读完成，但语义一致性未通过，不能宣布同步完成。** 路径＋方法、operationId、响应状态码集合均一致；129模型、必填/枚举/null/oneOf及示例已纳入比较，模型结构差异集中在Appointment的三个字段及引用它的响应。证据摘要见[APIFOX-READBACK.json](APIFOX-READBACK.json)。

| 编号 | 回读差异及影响 | 后续处理 |
| --- | --- | --- |
| AS-01 | Appointment.companyId、departmentId及systemIds.items由字符串标识（含长度/格式约束）变成type:object、properties:{}，导致字符串示例与远程模型矛盾 | 客户端核查这三个字段；若完整导出也损坏，按Git修复为字符串标识后再次回读，不能把Git改成对象 |
| AS-02 | POST /api/v1/admin/sessions由security:[]变成AdminJwt，登录出现先有JWT的循环；DELETE /api/v1/admin/sessions/current丢失原security中的空对象备选 | 分别恢复匿名登录及退出的原认证约定；其余65项有效认证绑定一致，不能把继承改显式视为越权 |
| AS-03 | 全部11项公共错误响应的媒体类型由application/problem+json变为application/json，影响所有引用它们的错误响应 | 保留Problem模型、状态码和示例值，恢复媒体类型，不将其视为纯展示差异 |
| AS-04 | 上传文件multipart请求Schema丢失additionalProperties:false，远程放宽未知字段约束；另生成file为空字符串的示例 | 恢复未知字段拒绝，核对文件示例的展示用途 |
| AS-05 | webhooks为空，缺少出站oaSubmission/deliverOaSubmission；相关模型和OaDeliveryJwt仍在 | 核查客户端对webhooks的支持；出站契约继续以Git为准，不伪造管理员入站接口。若平台不支持，需明确接受并记录该工具缺口，仍不能称完整一致同步 |
| AS-06 | 根标题默认模块、版本1.0.0；servers为空，license/tags及项目/需求/设计扩展部分未回读 | 设置模块版本1.1.0并记录Git基线；占位服务器未部署，不擅填实际地址。保留Git导航及设计说明 |

客户端单文件OpenAPI导出尚未提供，因此上述是MCP可见差异，不能断言都由客户端导入器造成；可能涉及导入、平台存储或MCP导出层。下一步先从默认模块导出OpenAPI3.1到本目录apifox-export.json或apifox-export.yaml（只含契约，不含调试凭据），再与Git及MCP快照交叉核对，定位差异后修复远程或记录平台限制。本轮MCP无写入工具，未擅自修改远程或本地权威契约。

本地validate_contract.py复跑PASS：67操作、1142引用、120模型示例、789媒体示例、43Schema场景；未重复Mock或宣称业务实现测试通过。数据库输入仍使用Git1.1.0；正式同步交接条件尚未满足，本轮未进入数据库设计。

## 客户端导出交叉核对

用户随后提供[apifox-export.json](apifox-export.json)，实际为OpenAPI3.1.0，SHA-256为74c159c93270359d2017f2ce6c7c2d1ce73e97fe9d535a89282c7e19fa3d2625。这是远程导出证据，不是第二份权威契约。使用[compare_apifox_export.py](compare_apifox_export.py)逐项比较，结果摘要见[APIFOX-EXPORT-DIFF.json](APIFOX-EXPORT-DIFF.json)。脚本依赖沿用VALIDATION中的PyYAML/jsonschema/openapi-spec-validator，运行方式为python docs/03-api/compare_apifox_export.py；生成差异摘要，出现差异不表示脚本执行失败，也不代表同步通过。

**修正AS-05的定位：客户端导出含oaSubmission webhook及deliverOaSubmission，68条等于67个入站操作加1个出站操作。出站webhook没有被误转为入站路径；缺失发生在MCP文档暴露层，不能继续要求用户重新导入该webhook。** 出站请求、成功响应和认证绑定一致，错误响应同样受AS-03媒体类型转换影响。

AS-01～04和AS-06在客户端导出中也存在，排除“仅MCP回读改变”这一解释；仍无法仅凭导出区分客户端导入、平台存储与公共导出逻辑。无多余/缺失入站操作，operationId及响应码集合全部一致。129模型的必填、枚举、可空、oneOf及示例已比较；组件模型差异仍集中于Appointment三个标识和它的引用展开。有效认证差异仅登录及退出两项；其他65项一致。

导出通过OpenAPI结构校验，但120个模型示例中119个通过，Appointment示例因companyId/departmentId/systemIds中的字符串被错误要求为对象而失败。这再次说明结构lint不能证明契约同步正确。本地Git契约保持原样且仍为1.1.0；本轮未修改远程、未进行业务实现测试。

需要在Apifox端核查并修复：

1. Appointment.companyId和departmentId，以及systemIds的items恢复type:string、minLength:1、maxLength:64、pattern:`^[A-Za-z0-9][A-Za-z0-9_-]*$`。systemIds仍为数组且uniqueItems:true。不要将Company/Department/Application整个对象作为字段类型。
2. 登录POST /api/v1/admin/sessions恢复security:[]，不继承AdminJwt；退出DELETE /api/v1/admin/sessions/current恢复security:[{AdminJwt:[]},{}]。如果客户端无法表达退出的认证备选，记录工具限制，不能静默改Git。
3. 11项公共错误响应恢复application/problem+json，引用它们的接口及出站webhook应随之正确导出；不能只修改响应示例。
4. 上传POST /api/v1/admin/import-files的multipart/form-data对象恢复additionalProperties:false，保留file必填和binary格式。
5. 模块版本调整为1.1.0；标题、标签说明及设计元数据与Git差异记录为展示/导航差异，占位servers不是已部署地址。

修复后再次导出同一路径并刷新MCP，比较客户端导出和MCP可覆盖部分；MCP不暴露webhook的限制单独保留。当前状态为“人工导入及双通道核对完成，实质差异待修复”，不是同步完成。正式同步交接仍未满足，本轮不进入数据库设计。

## 人工修复后再次导出核对（2026-10-17）

用户重新导出默认模块全部OpenAPI3.1 JSON；本次文件SHA-256为b35f59f8d4973e3a293da223f02ab6fe711cbef073149b102afc9df320a41dbc。比较脚本重新运行，摘要已更新；旧文件及摘要保留在Git历史7d9bed2。67个入站操作、56路径、129模型、1个出站webhook，路径/方法、operationId及响应状态码集合一致；OpenAPI结构通过，120个模型示例全部通过。

已确认修复AS-01、AS-02中的登录部分、AS-03：Appointment三个字段完整恢复字符串及长度/格式/唯一约束；登录security:[]；11项公共错误媒体类型及出站webhook错误响应均恢复application/problem+json。比较未发现组件及webhook的结构差异。说明文字/标签/扩展的差异由比较规则单独忽略，不代表所有文字逐字一致。

剩余：退出DELETE /api/v1/admin/sessions/current仍只有AdminJwt，缺少Git中的空对象备选；上传multipart根对象仍缺additionalProperties:false并多一个空文件示例；根版本1.0.0/标题默认模块，license及占位servers差异。共70个比较条目含继承认证变显式等重复/等价表示，不是70项业务缺陷。原约束继续以Git为准，没有为了工具一致而改业务契约。

MCP重新刷新时间2026-10-16T16:12:04.652Z（北京时间2026-10-17 00:12），56路径、webhooks仍为空；定向读取登录/当前会话及模型/响应索引成功。本轮客户端完整导出已比较，MCP只定向复核，不冒充再次读取全部203引用。上传字段Schema弹窗和生成模型代码弹窗都不是multipart根对象编辑器，尚未核实客户端是否支持根约束及退出可选认证，不能直接定性为平台完全不支持。

状态：已修复三类主要差异，完整一致同步仍未完成；后续核查剩余两项语义差异及模块版本，工具限制若无法修复需明确记录和确认。未开始数据库/正式代码，本轮仅维护导出证据、比较摘要及共享记忆。

## OAS自定义覆盖首次验证与修正（2026-10-17）

客户端找到“导出/备份API规格”的配置入口，支持标题、版本及OAS自定义覆盖。用户按本聊天提供的局部覆盖导出后，文件SHA-256为e350c34855b941e49201ed4d417c85623b7507c40b781ce3e6467a6dd55efc00，标题/版本已正确为RBAC权限中心API/1.1.0，退出security及上传additionalProperties也出现。然而实际结果表明路径条目被整体替换：当前会话GET丢失，退出和上传丢失operationId、参数、响应，上传file结构丢失；只剩66个入站操作。此导出不能用于实现或宣布同步完成。此前提供局部JSON时未核实合并粒度，现明确纠正，不能继续反复使用同一局部配置。

比较脚本补充缺失responses的诊断，避免遇到损坏导出直接KeyError；结构库检查通过和120模型示例通过不代表操作内容完整，缺失操作/operationId/响应的独立检查已明确失败。摘要反映真实导出，未手改导出掩盖问题。

已从Git1.1.0原样提取两个完整路径条目生成[APIFOX-EXPORT-OVERRIDE.json](APIFOX-EXPORT-OVERRIDE.json)：含当前会话GET和DELETE的完整定义、上传POST完整定义及标题/版本，引用现有组件，不复制整个项目。按已观察到的整路径替换方式在本地模拟合并后，67个操作、operationId和响应码恢复一致，两路径等于Git原件，OpenAPI结构通过。此为待用户应用的导出适配配置，不是更改业务契约或已经验证的远程修复。

下一步：在文本编辑器打开上述覆盖文件，复制全部内容替换客户端“OAS自定义覆盖”编辑框；保存后从同一条API规格配置以OpenAPI3.1/JSON/全部导出，覆盖apifox-export.json再核对。该操作只补齐指定导出配置，不能保证通用导出入口或MCP会应用同一配置。本轮未再次刷新MCP、未改远程、未进入数据库；权威openapi.yaml保持原样。

## 完整路径覆盖后的客户端验证通过（2026-10-17）

最新导出SHA-256为3441b617d7e22d5fdb4a75c194a6494028bbf0009079bdc236ed48515c27a361。标题正确、info.version为1.1.0、OpenAPI3.1；56路径/67入站操作/129模型/1出站webhook完整。当前会话GET恢复，退出及上传完整定义恢复；路径方法、operationId、有效认证、响应码集合一致，无缺失responses。退出可选认证、multipart文件必填/binary及根additionalProperties:false均正确。组件及出站webhook结构无差异。120模型示例、导出文档直接定义的104个媒体示例全部通过；104是不重复展开组件引用的数量，不与Git的789次展开检查混同。

比较器现在计算有效继承认证，避免误报继承转显式；保留properties等映射中的字段名及示例原值，避免description等业务字段被当说明删除；增加媒体示例和缺失响应检查。排除说明/标签/扩展、参数顺序及等价示例包装后，剩余三处差异：license元数据缺失、未选环境时占位servers为空、登录请求合成示例值不同但仍符合模型。它们不改变字段/权限/状态约定，仍保留记录，导出不作为反向覆盖Git的来源。

**指定API规格配置的客户端导出已通过核心契约一致性核对，采用完整两路径覆盖作为导出适配。** APIFOX-EXPORT-OVERRIDE.json由Git1.1.0派生；两路径变更必须同步更新覆盖配置并再验证，不能独立演进。

MCP本轮刷新2026-10-16T16:33:20.465Z（北京时间2026-10-17 00:33），定向读取两路径成功，GET/DELETE/POST均存在；但仍为模块版本1.0.0、退出仅AdminJwt、上传无根约束、webhooks为空，未使用上述自定义导出配置。本轮未再次读取全部203引用。不能把客户端导出通过扩大为MCP或平台原生模型完全一致。

后续消费以Git或这条已验证的指定导出为准；仅依赖当前MCP时须结合Git补齐。无需继续重复修复界面。工具链限制保留，API内容可准备数据库阶段交接；本轮未开始数据库设计、未更改权威契约、未测试真实业务实现。

## 正式跨阶段评审后的1.2.0同步待办（2026-10-17）

Git权威契约已从1.1.0修订为1.2.0：GrantLine.employmentIds移除任意100上限，SourceCloseResult新增必填visibility/hasUndisclosedImpact并明确可见计数及审计ID；67入站＋1出站、129模型不变。接口/Schema校验和本地完整两路径覆盖配置派生检查通过，详细证据见[正式评审](../04-database/CONSISTENCY-REVIEW.md)及[验证记录](VALIDATION.md)。

**尚未人工导入1.2.0，未操作远程或重新核查MCP。** 既有apifox-export.json及比较摘要保留为1.1.0真实导出证据，不手改伪造新结果。APIFOX-EXPORT-OVERRIDE.json仅更新派生版本，完整路径仍逐项等于Git；后续按原已验证配置导入新Git文件、应用本地完整覆盖并导出，再核对本次Schema差异。无需重复排查旧MCP暴露差异，也不称工具链完全一致。

## 1.2.0重新导入后的客户端导出核对（2026-10-17）

用户已在默认模块重新导入并使用指定配置导出，原样保存apifox-export.json，SHA-256为`852c787ec244417bc92268f87013352f7c1d7c745079996849536049b3628475`。截图中仍有rbac-admin/1.0.0的默认规格卡片；该卡片版本不能替代实际导出info.version，**不需要删除该规格**，继续保留并使用已经配置自定义覆盖的指定规格。

实际文件为OpenAPI3.1、info.version=1.2.0，56路径/67入站/129模型/1出站完整，operationId、响应码及webhook一致。本次GrantLine.employmentIds已无错误100上限，SourceCloseResult新增两字段、必填和互斥分支已同步。平台在外层已限定object的oneOf分支补type:object，与Git语义等价；比较器仅在这个外层类型上下文归一化，无外层类型时仍保留差异。四项边界检查覆盖等价显式类型、无外层类型的真实收紧、ID字符串变对象及布尔枚举变化。

**本次核心一致性未通过，不将重新导入记为同步完成。** 120模型示例中119通过，Appointment示例失败；104媒体示例通过。两项实际回归：

| 位置 | 导出问题 | 具体处理 |
| --- | --- | --- |
| POST /api/v1/admin/sessions | 登录继承AdminJwt，Git为security:[] | 已将此完整路径加入本地APIFOX-EXPORT-OVERRIDE.json；当前覆盖共三条完整路径（登录、当前会话GET/DELETE、文件上传）。将文件全部替换到原配置后再导出；此本地适配尚未在客户端验证 |
| components.schemas.Appointment | companyId/departmentId为object，systemIds.items也为object | 客户端模型Appointment中将这三处改为string，minLength=1、maxLength=64、pattern=^[A-Za-z0-9][A-Za-z0-9_-]*$；systemIds仍为array且uniqueItems=true。修正基础模型，引用处应随之恢复，下一次导出核对，不能直接手改导出证据 |

本轮导入设置说明此前没有充分处理登录匿名认证的特殊情况，现补入完整路径覆盖以保留Git语义；不是要求修改Git认证规则。Appointment回归来自重新导出中的实际字段变化，不是再次排查旧MCP差异。

比较摘要APIFOX-EXPORT-DIFF.json现保留63处差异：61处阻断路径（任命字段及引用展开60处、登录认证1处）和2处既有元数据/占位servers差异。它们对应两项核心问题，不是61项不同业务缺陷。比较器新增coreContractConsistent=false、阻断/展示路径分类；只要存在核心差异或无效示例，命令退出1，避免将脚本运行结束误当核对通过。没有关闭严格字段/认证检查。

用户在客户端完成上述两项后，再从同一指定规格导出覆盖apifox-export.json，重新运行compare_apifox_export.py。尚未执行远程写入、刷新或重复调查MCP；没有改动权威openapi.yaml、原型、数据库或业务实现。既有1.1.0导出及当时通过证据保留于Git历史，本次新文件不覆写那些历史事实。
