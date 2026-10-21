# 发布证据检查（不自动发布）

在仓库根目录使用 Python 3 标准库执行：

```sh
python -m unittest discover -s backend/acceptance -p 'test_*.py' -v
python backend/acceptance/verify_release.py --evidence docs/05-detailed-design/BE-33-35-BUILD-EVIDENCE.json
```

第一条是工具自身行为测试，不是项目发布验收。第二条只读任务/验收/操作清单、构建包、主代码/测试代码/发布与演示辅助工具和 Maven 构建配置摘要，以及两组 Maven XML 报告，退出 0 表示所给证据齐全且一致，退出 2 表示不能发布。当前实际证据应退出 2；不得为了“绿色”删去未完成项、把跳过测试计为成功，或把准备状态改为验收。

构建证据里的 baseCommit 是本轮已知基线，源码/测试摘要精确约束实际文件；不冒称包只含 baseCommit。候选包、报告在 backend/target（忽略目录），摘要清单不是二进制交付。换机器或重新构建后，需要按 deploy/README.md 配置专属非业务 MySQL、完整执行 backend 的 `clean verify -Pmysql-check`，人工复核完整 suite/无失败跳过后重新登记包及每份 XML 的摘要。旧报告缺失、源码或测试修改、报告/包变化都会拒绝通过，不能沿用旧结论。不要在现有业务库运行隔离测试。

server/endToEnd/ownerHandoff 默认 verified=false。后续真实证据须以仓库内相对路径/sha256 引用 JSON，并包含 environment=TARGET_SERVER、匹配包 artifactSha256/主代码 sourceSha256、checksPassed=true 和明确 reviewedBy。JSON 声明与摘要只能证明一致性，不能独立证明人、服务器或检查真实发生；必须由各任务负责人核对可信的外部验收记录。不得填写密码、令牌、私钥、真实会话或无关个人信息。

所有任务必须真实实现、本人确认并主线交付，40 项后端 AT（不含前端 AT-40，含 AT-41）须逐项 VERIFIED。当前的局部证据/基础环境演练不满足此条件，特别不能将本机 demo 受理与持久工作器测试等同真实 OA 或目标服务器验收。工具不连接服务器、不创建 Issue/PR/tag/release、不部署，不代记负责人确认，不修改共享任务状态。

最新集成结果、缺口及复跑基线见 [BE-33/35 准备记录](../../docs/05-detailed-design/BE-33-35-INTEGRATION-HANDOFF.md)。
