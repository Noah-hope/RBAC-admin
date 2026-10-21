# 本机申请审批演示

演示适配器仅模拟OA受理，不代表真实外部OA接通。审批结果走同一个可信`/api/v1/oa/final-results`入口；没有后台管理员批准、强制执行或公开服务token签发入口。

## 准备

先完成[后端本机配置](../docs/05-detailed-design/BE-06-LOCAL-SETUP.md)和[管理员初始化](README.md)，确认应用、初始化工具指向同一测试schema，正常应用使用DML账号。`oa-signing.json`是本机OA私钥，`oa-trust.json`是后端已配置的对应公钥集，均沿用身份配置中的文件，不放进Git。

按照[环境模板](demo.env.example)在IDE独立运行配置或受控外置环境中填写变量。模板不会自动加载，不把口令/token放进命令参数或共享文档。选择真实目标schema并填写完全一致的`RBAC_DEMO_CONFIRM_DATABASE`。初始服务ID使用新的UUID，subject在库中唯一。工具拒绝覆盖既有ID/subject。

## 登记和签发

使用同一构建jar；在项目根目录执行以下主类命令（IDE可直接填主类和程序参数）：

```sh
java -Dloader.main=com.adgn.backend.demo.DemoServiceApplication \
  -cp backend/target/backend-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher configure-demo-service

java -Dloader.main=com.adgn.backend.demo.DemoServiceApplication \
  -cp backend/target/backend-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher issue-demo-service-token
```

两次命令都需要显式`RBAC_DEMO_MODE=demo`。登记只插入指定OA或BUSINESS身份，不改角色/授权/申请。签发前重新检查身份当前启用状态、类型和绑定，以及私钥与配置公钥一致。私钥支持单JWK和身份配置生成的JWK Set；多密钥时用`RBAC_DEMO_SIGNING_KEY_ID`明确选择。token有效期5分钟，写入`RBAC_DEMO_OUTPUT`的新文件，不输出到控制台，不覆盖已有文件。支持POSIX权限的系统创建0600文件；Windows使用仅本人可访问的目录。重签时另选新输出路径，过期文件由本机受控清理。

## 运行申请链路

1. 正常应用显式启用`demo` profile，例如IDE的`spring.profiles.active=dev,demo`。`rbac.workflow.scheduling-enabled`默认true，启动及每30秒扫描持久待办；隔离测试可显式关闭。普通`dev/server`不会装配演示适配器。真实适配器可实现`OaSubmissionPort`替换；未配置适配器时安全记投递失败并退避，不假装已受理。当前提交要求库中恰有一个已启用OA身份；多实例配置未明确时503拒绝选择。
2. 管理员按正式API创建角色/编辑配置草稿，或建立明确授予/撤销清单。POST`/api/v1/admin/change-requests/{id}/previews`传`expectedDraftVersion`，查看`effectiveContent`、无变化分类和影响。
3. POST同ID的`/submissions`，传`expectedDraftVersion`、刚取得的`previewId`和新的UUID `Idempotency-Key`。202表示固定内容与可靠待办已提交，有效权限尚未变更；全部无变化200且保留草稿、不投OA。预览过期或基线变化需重新预览。
4. 等待发送器，GET申请详情确认`deliveryStatus=LINKED`，`oaApprovalId=DEMO-<实际requestId>`。把本次真实GET响应保存到受控文件，如`.tmp/live-request.json`。不要使用文档示例ID或猜测摘要。
5. 设置`RBAC_DEMO_BASE_URL`及`RBAC_DEMO_CALLBACK_TOKEN_FILE`，从项目根目录执行：

```sh
python3 tools/demo_oa_result.py --request .tmp/live-request.json --decision APPROVED
# 驳回另一份实际申请时使用 --decision REJECTED
```

脚本从实际详情复制固定版本/摘要/可靠审批号，只生成新eventId和当前决定时间；不跟随重定向。HTTP仅允许本机，远程需HTTPS。输出仅安全接收状态。脚本每次生成新eventId，相同决定仍由服务端最终结果防重；要验证同eventId重放/异内容冲突，可在Apifox保存并重发同一回调body，凭据仍从本地环境配置读取。
6. 202/`executionStatus=PENDING`只证明可靠批准。等待执行器后再次GET详情，确认`EXECUTED/SUCCEEDED`；查看审计、配置历史或实际grant。失败显示`EXECUTION_STORAGE_FAILURE`与下次重试时刻；故障恢复后仍复核整份固定内容。`INVALIDATED`不会复活，也不能通过人工投递重试绕过。

原提交人/任命撤销不等于申请自动失效；执行只核对原scope当前覆盖及持久失效状态。回调身份、最终决定、固定清单均不可用新eventId替换。真实权限检查接口BE-18、完整系统验收BE-33及服务器部署BE-34仍按各自负责人任务接入。
