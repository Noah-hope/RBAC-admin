# 后端 JaCoCo 测试报告

## 最新结果：采集与边界测试改进

2026-10-26 22:10:49（Asia/Shanghai）完整运行通过，耗时 **4分45秒**。基于 `c22e922ded55c4616cc6c3ed86598304b4967b67`，新增子进程采集、23项测试，并最小修复 SafeResult 接受空白 JSON 的问题。首次运行报告与原归档保留，供前后对照。

| 测试类型 | 测试组 | 本次数量 | 首次数量 | 失败 / 错误 / 跳过 |
| --- | ---: | ---: | ---: | --- |
| 普通测试（Surefire） | 24 | 163 | 143 | 0 / 0 / 0 |
| 真实 MySQL/HTTP（Failsafe） | 27 | 439 | 436 | 0 / 0 / 0 |
| 合计 | 51 | **602** | 579 | **0 / 0 / 0** |

未启用 coverage 的默认 `clean verify` 也完整通过，163项普通测试无失败/错误/跳过，不依赖子进程 agent；该次与上述 coverage 运行是两次验证，不把重复执行计为新增测试。

## 覆盖率对比

| 指标 | 首次运行 | 改进后 | 改进后已覆盖 / 总数 |
| --- | ---: | ---: | ---: |
| 行 | 95.70% | **98.50%** | 4075 / 4137 |
| 分支 | 72.99% | **76.60%** | 4440 / 5796 |
| 指令 | 93.00% | **95.38%** | 56227 / 58950 |
| 方法 | 97.09% | **98.48%** | 1625 / 1650 |
| 类 | 97.67% | **99.71%** | 343 / 344 |

全部344个正式编译类保持在统计范围内，没有新增包或类排除规则。测试夹具不属于正式源码报告。数值读取自 XML 顶层 counter，百分比四舍五入保留两位；HTML 的整数展示与这里的小数可能不同。修复增加了空 JSON 文档判断，因此指令/分支总数略有变化，不通过删除代码提高覆盖率。

提升同时来自子进程采集修复和新增测试，不把原本已经执行但未采集的代码说成新增业务测试。覆盖率仍未达到100%，不据此声称所有路径已验证或系统没有缺陷。

## 团队查看方式

仓库可直接阅读本说明；完整明细下载[最新完整 HTML 报告](reports/JaCoCo-2026-10-26-improved.zip)，解压后用浏览器打开 `report/index.html`，不要只复制一个 HTML 文件。归档 SHA-256 见[校验文件](reports/JaCoCo-2026-10-26-improved.zip.sha256)。

归档包括完整 HTML/XML/CSV、结构化测试计数、被测源码与配置摘要、父进程/48个子进程及合并后的原始 exec，以及包内 SHA-256 清单。报告是本次运行快照，后续代码变化需要重新运行并归档。

首次记录：579项通过，行95.70%、分支72.99%、指令93.00%，耗时2分58秒。原[首次完整归档](reports/JaCoCo-2026-10-26.zip)及其[校验文件](reports/JaCoCo-2026-10-26.zip.sha256)保持原样。首次来源 `a5aa7cb` 已合入主线 `fb0134e`，该次交付补录为 `c22e922`；不能将本次602项结果倒记到首次交付。

## 本轮改进内容

1. **补全采集。**测试专用 JavaSubprocess 工厂从测试 JVM 获取当前 JaCoCo agent，每个 Java 子进程写独立 exec。现有凭据环境清理和 Mockito agent 均保留，不把 Mockito 或凭据通过新工厂转交给子进程。Maven 在 post-integration-test 合并父子进程记录，verify 生成报告。
2. **管理员初始化。**新增系统库、错误登录名、空账号配置、嵌入 URL 凭据、URL 片段及端口边界检查，断言退出码、无账号/审计写入和输出脱敏。bootstrap 包原为0%行覆盖，本次为75/79行（94.94%）。
3. **演示命令。**新增非法配置不写服务/审计、BUSINESS服务与系统绑定、真实 JWT 验证、停用后禁止再次签发及不落盘检查。demo 包行覆盖从16.28%升为42/43行（97.67%）；仍是显式演示能力，不表示真实外部 OA 已接通。
4. **输入和文件边界。**新增 Unicode 长度、版本安全整数、分页溢出、完整 UUID、布尔输入、搜索转义、上传大小/类型、损坏 XLSX、关系与单元格结构、文件元数据和发布完整性检查。
5. **幂等响应与摘要。**新增状态/Location边界、嵌套敏感字段别名、错误 JSON、请求元数据、输入/返回数组防御性复制、密钥配置与独立 HMAC 测试向量断言。
6. **实际缺陷修复。**空字符串或空白串经 JSON 解析成为 MissingNode，原校验未拒绝。先以真实失败测试复现，再增加 isMissingNode 拒绝；合法 JSON 字面量 null 与204空响应保持原设计。

实现顺序、RED/GREEN和测试有效性证据见[改进记录](JACOCO-IMPROVEMENT.md)。除上述一处输入校验外，未更改业务授权、API契约或数据库迁移。

## 验证与剩余边界

- 子进程采集测试先失败（应有1个记录、实际0），接入后通过；本次48个子进程文件均非空，已实际合并，没有 class/execution mismatch 警告。
- 空白 JSON 回归先真实失败（1失败、0错误），最小修复后通过。新增的既有行为回归用例不冒充新功能的首次 RED。
- 临时移除分页溢出保护、请求正文防御性复制时，各有1项新增断言失败、0错误；两处临时变异均已恢复，未进入交付实现。
- 真实数据库使用本轮新建回环非3306的MySQL8.4.8，测试核对实例身份后再创建随机库/账号。完成后剩余随机库/账号0/0、专属实例正常退出0；未操作已有业务数据库。
- 日志中的预期错误响应、配置拒绝及故障注入不等于最终 JUnit 失败。以51份 JUnit XML和 Maven BUILD SUCCESS确认本次结果。
- 尚有62行、1356个分支未覆盖，包含普通 Web main入口、依赖失败处理和防御性状态判断；未通过调用私有构造器、制造无意义断言或排除低覆盖类追求满分。剩余分支应结合运行条件逐项分析，不能只按数字推断业务遗漏。
- 覆盖率不替代负责人确认、目标服务器部署与真实外部服务联调。

## 复跑与原始文件

使用JDK21及Maven Wrapper，先准备专属临时MySQL8.4，数据目录必须位于 `backend/.tmp`，端口必须为非3306的回环端口，关闭binlog；仅在本次终端设置 `RBAC_ISOLATED_MYSQL_PORT` 和 `RBAC_ISOLATED_MYSQL_DATADIR`。严禁指向现有开发库或业务库。

在 backend 目录执行：

```sh
./mvnw clean verify -Pcoverage,mysql-check
```

Windows使用 `mvnw.cmd`。只运行普通测试可用 `./mvnw clean verify -Pcoverage`，该报告应明确标记未包含真实MySQL/HTTP检查。默认无采集验证为 `./mvnw clean verify`。

- 最终HTML/XML/CSV：`backend/target/site/jacoco/`。
- 父进程记录：`backend/target/jacoco.exec`；子进程记录：`backend/target/jacoco-subprocess/*.exec`；合并记录：`backend/target/jacoco-merged.exec`。
- 完整日志：`backend/.tmp/jacoco-improved-full-verify.log`；默认运行日志：`backend/.tmp/jacoco-improved-default-verify.log`。
- JUnit XML：`backend/target/surefire-reports/`、`backend/target/failsafe-reports/`。

`clean`会删除target，固定归档保存在docs，不受影响。为避免旧采集记录混入，正式覆盖率报告使用clean完整运行。

配置核实来源：[JaCoCo agent](https://www.jacoco.org/jacoco/trunk/doc/agent.html)、[Maven参数衔接](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html)、[exec合并](https://www.jacoco.org/jacoco/trunk/doc/merge-mojo.html)。

## 最新交付记录

改进来源 [`b40bc20`](https://github.com/Noah-hope/RBAC-admin/commit/b40bc20e2061f63a43a500177a0ac473a8b70c99) 已合入主线 [`42431f5`](https://github.com/Noah-hope/RBAC-admin/commit/42431f578cdbb76ed6d841663be961e6a18fd6ff)，合并树等于核验来源；主线与任务分支已原子推送，两个完整SHA回查一致。接入的最新部署配置未改变被测源码、测试和pom，保留本次602项结果；交付补录仅修改文档，不重跑Java/MySQL、不部署。
