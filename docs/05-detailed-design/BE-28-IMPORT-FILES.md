# BE-28 文件上传、不可变存储与解析

D-076（2026-10-20）：用户已要求先合并和推送，授权交付后再人工验收。已接入最新main/1f7886e及B/BE-17/12；D原实施编号D-073与B重复，调整为D-075，原验证事实不变。本轮完整集成验证及实际合并推送成功后追加；下方未授权/未交付及102＋159为此前实施记录。当前不自动开始BE-29/30。

2026-10-20 12:59（Asia/Shanghai），集成后的完整 `clean verify -Pmysql-check` 通过：110离线＋178真实MySQL/HTTP，失败/错误/跳过均0。日志 `backend/.tmp/be28-integration-full.log`，同时覆盖BE-28及最新B/BE-17/12，不只是新增检查。jar SHA256 `e283b5cc6837bff5b48a19e640ef288a530dc7f17e55349118f10c7b76d2b6d4`。随机schema/账号均0，BE-28临时密钥/文件目录均0，专属PID6964正常关闭，用户原MySQL84仍Running。实际交付前后只更新文档/追踪，不再改业务代码；机器验证不代记D人工验收。

2026-10-20，成员D授权开始BE-28。基线main/93acc63，分支codex/be-28-import-files。依据UC-10、Git API1.2.0的uploadImportFile/getImportFile、T0/T8/T9/T11、import_file现有结构和AT-26。不调整需求/接口/迁移，不实现BE-29预检或BE-30整批提交；不读取真实人事资料。

## 模块与流程

- imports/ImportFileParser：JDK严格UTF-8 CSV和安全StAX/ZIP读取XLSX，无新增Maven依赖。只返回原始文本及行号，不执行公式/宏，不进行组织/员工映射；保留前导零、字符串及空单元格。CSV支持BOM、标准引号/双引号、逗号及引号内换行。XLSX按workbook及relationships定位工作表，支持sharedStrings/inlineStr/数值/布尔文本；不根据dimension伪造区域，不计算日期或公式值。BE-29明确选择工作表，上传不默认选择第一个。
- imports/ImportFileStore：复用rbac.storage.import-directory（server配置已有）；本机默认backend工作目录下.tmp/imports，可用RBAC_IMPORT_DIRECTORY覆盖。暂存/正式文件均位于配置根目录，文件名由服务端随机UUID生成，不使用原名拼接路径。先force持久化文件，再原子发布不可覆盖的正式文件；不暴露storage_ref/绝对路径。读取重新核对存在、大小和SHA256；缺文件503、损坏或非法存储引用500，不把损坏当不存在。
- imports/ImportFileService：读边界复核当前非受限总管理员；无资格上传403、元信息详情404。校验/解析在guard事务外；幂等摘要固定原始文件SHA256、大小、安全显示名及工作表列表，不依赖multipart随机boundary。reserve后发布文件，再complete同事务写import_file、全局审计和安全幂等结果。资格在写事务再复核，重放也复核当前资格和持久文件。DB/审计失败不得提交半份元数据；可能已提交但结果未知时不删除正式文件，孤立文件留后续受控核查，不伪造成功。
- web/imports/controller：严格单个file multipart part，UUID Idempotency-Key，201+Location/no-store；GET返回ImportFile六个契约字段。安全错误复用现有Problem。配置multipart单文件10MiB/请求11MiB（包含协议封装），超限413，避免容器默认1MiB先行拒绝合法文件。

## 文件边界

原文件1～10MiB，XLSX实际累计解压不超过50MiB；每张工作表最多1000条非空数据行（另允许1行表头），最多100张工作表。1000源行整批提交仍由BE-29/30验证，不将上传通过当作预检或导入成功。解析器提供选定工作表的不可变原始行读取端口，供后续预检在核验文件SHA后调用。

扩展名仅.csv/.xlsx，MIME分别校验常用CSV、XLSX或通用二进制类型并结合实际内容；拒绝XLS/XLSM/OLE/非ZIP伪XLSX、空/非表格CSV、无效UTF-8、宏/加密/公式/外部关系、DTD/外部实体、重复ZIP项/路径穿越、未知或重复工作表映射。数据单元格以等号开头的公式文本也拒绝，负数字面值保持文本。工作表/关系只解析包内受控路径，不下载任何外部内容。

ZIP额外资源保护：最多2048包项；最多100万单元格，限制畸形稀疏索引到Excel标准行/列范围。限制实际解压字节而非仅信任声明大小，逐项核验CRC；不将压缩包解压到任意目录。安全解析器实现仅依赖JDK，合法性由手工构造的真实CSV/XLSX及攻防边界测试验证。

## TDD与验证计划

先离线RED：CSV转义/BOM/多行/空位/UTF-8错误、1000/1001源行、XLSX共享与内联字符串/真实工作表顺序、公式/宏/外部实体/路径/重复项/解压超限、读取的不可变性和摘要。再存储RED：原子持久化、重启后读取、缺失/损坏/非法ref、失败不发布引用。再真实HTTP/MySQL RED：总管理员资格、受限及普通账号、multipart形态/MIME/超限、幂等一致/冲突/重放再鉴权、元数据/审计/幂等原子、无组织员工副作用、文件缺失/损坏。

每组确认因缺失目标能力失败，再实现并GREEN；最终完整默认构建及专属临时MySQL回归。测试使用backend/.tmp独立datadir、非3306端口、随机schema/账号和专属存储目录；结束清理并回查。现有用户项目库、IDE密码/密钥不读取。实现、负责人验收和主线交付分别记录；本次新任务不从BE-10特定合并授权推断自动合并推送。

## 执行证据

2026-10-20，先后观察以下真实RED（日志在本机忽略目录，不将日志中的临时标识当团队凭据）：

| 组 | RED事实 | 实现及最终验证 |
| --- | --- | --- |
| 解析 | 首10项为6失败/4错误，目标解析器尚未实现 | 实现CSV/ZIP/StAX；补根节点与共享字符串关系的3项检查后，13项中1失败/1错误，定位到错误根节点误接受及合法包内相对路径误拒绝。修正关系定位、拼音注释和命名空间缺失处理后13项全通过 |
| 存储 | 4项为1失败/3错误，目标存储尚未实现 | 持久化、原子发布、读取复核、失败清理及禁止重复发布4项全通过 |
| 真实HTTP/MySQL | 9项均失败，目标路由404 | 实现两路由及事务；首轮8项通过，另1项是测试将MySQL JSON键重排误判为差异，改为结构比较后9项全通过，未为此放宽生产行为 |

最终显式JDK21执行 `./mvnw.cmd -q clean verify -Pmysql-check`（工作目录backend），Surefire **102项/17组**、Failsafe **159项/14组**，失败/错误/跳过均0。包括本任务17项离线和9项真实检查，以及A/B共同主线既有全部回归。真实检查涵盖当前总管理员/受限账号、普通权限管理员详情隐藏、幂等边界无关及改名/改内容冲突、10MiB与容器默认1MiB边界、审计注入故障全事务回滚/同key恢复、缺失及损坏文件、应用重启后继续读取；不把这些夹具当真实HR已接通。

构建产物 `backend/target/backend-0.0.1-SNAPSHOT.jar`，SHA256 `06e07615da74feb219b2f815cff95e56d3be7785af58cebff78f591d6995a7cb`。本机证据为 `backend/.tmp/be28-parser-red.log`、`be28-store-red.log`、`be28-http-red.log`、`be28-parser-edges-red.log`、`be28-targeted-green.log` 和 `be28-full-verify.log`；忽略日志不纳入Git，长期交接以上述真实结果和可重复执行测试为准。

隔离MySQL8.4.8仅监听127.0.0.1:23328，复用仓库临时datadir而非已有开发/测试库。结束核查随机schema及账号均0，BE-28临时密钥/存储目录均0；专属PID3652正常关闭，用户原MySQL84仍Running。`design_plan.py`刷新和`validate_design.py`静态校验通过，契约/数据库设计/迁移未修改；静态校验本身不运行Java或SQL。BE-28为IMPLEMENTED，ownerAccepted=false，mergedToMain=false，未推送或部署。

## 本机验收与后续接入

使用现有数据库、RSA身份和HMAC幂等配置启动应用；BE-28没有新迁移或密钥。IDE运行配置可增加 `RBAC_IMPORT_DIRECTORY` 指向私有持久目录，默认 `.tmp/imports` 相对backend工作目录。该目录需可写，禁止放在静态资源/公开下载目录；保留目录才能在应用重启后读取已有文件。存储文件与数据库须配套备份，孤立文件核查/保留策略留部署运维，不提供公开删除入口。

1. 使用已完成首次改密的总管理员登录；Apifox本地环境保存accessToken，不写入共享文档。
2. `POST /api/v1/admin/import-files`：Bearer认证、一个新的UUID `Idempotency-Key`；Body选form-data，仅一个名为`file`的文件字段，选择UTF-8 CSV或XLSX，让客户端生成multipart边界。
3. 例如CSV正文为 `employeeKey,name` 换行 `007,测试员工`。响应201，应只有id/fileName/sha256/sizeBytes/sheetNames/createdAt，并含Location和no-store；CSV工作表名为`CSV`，XLSX返回工作簿中的工作表名列表。
4. 用响应id调用 `GET /api/v1/admin/import-files/{fileId}`，应200且相同安全元数据；同key同名同文件再次上传应201、Idempotent-Replayed=true及相同id，修改文件名或内容后沿用同key应409。
5. 普通权限管理员上传403、详情404；未登录401；临时改密账号403。上传合法只代表文件已保存和解析，不会修改员工/任职，也不会生成预检批次或完成导入。

首次本机验收可按下面的最小流程逐项观察，不需要重新运行全部自动化测试：

- 启动IDE中原有BackendApplication，确认连接目标开发库与已有管理员所在库一致。没有初始化过后台总管理员时，先按[本机初始化步骤](../../deploy/README.md)创建一次；MySQL的rbac_app/root不是后台登录账号。
- Apifox调用 `POST http://localhost:8080/api/v1/admin/sessions`，Auth选无认证，JSON为 `{"loginName":"本机总管理员登录名","password":"本机后台密码"}`。保存返回的accessToken到本地环境；若mode为PASSWORD_CHANGE_ONLY，先按[登录/首次改密说明](BE-06-LOCAL-SETUP.md)完成改密，再重新登录取得NORMAL会话。
- 在本机准备UTF-8文件 `.tmp/be28-acceptance.csv`，内容为上文两行示例。上传请求Auth选Bearer Token，输入accessToken本身；Headers添加Idempotency-Key，值可在PowerShell运行 `[guid]::NewGuid().ToString()` 获取。Body选form-data，字段名file、类型File，选择CSV，文件MIME选text/csv；Content-Type及boundary交给Apifox生成，不手工填JSON或multipart边界。

| 操作 | 应观察到的结果 |
| --- | --- |
| 首次上传示例CSV | 201，记录id，sheetNames为["CSV"]，不出现服务器存储路径 |
| GET同一个id（使用相同Bearer认证） | 200，id/sha256/sizeBytes与上传相同 |
| 原请求同key、同名、同文件重发 | 201，相同id，响应头Idempotent-Replayed为true |
| 改动CSV内容，沿用刚才的key上传 | 409；使用新key才是新上传 |
| 去掉上传请求的认证后重发 | 401 |
| 重新启动应用后GET原id | 200；不要删除或更换存储目录 |

以上结果核对后，由D明确确认BE-28验收通过；机器测试和先合并推送不代替这项确认。上传与幂等验证会留下相应文件/元数据/审计，仍不会创建员工或任职。

BE-29可复用 `ImportFileService.readSheet(fileId, sheetName, token)`，必须明确选择sheetName，并由本任务复核当前总管理员、文件存在与SHA256，返回不可变原始文本行及源行号；BE-29仍自行完成模板映射、冻结计划/差异和业务依赖检查。BE-30负责整批正式变更及跨域失效，本任务不提前承担其写入语义。
