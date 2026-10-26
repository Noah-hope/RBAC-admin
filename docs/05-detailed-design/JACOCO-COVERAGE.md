# 后端 JaCoCo 测试报告

## 本次结果

2026-10-26 11:40:46（Asia/Shanghai）完整运行成功，Maven 耗时 2 分 58 秒。基线提交 `e557a70be1901558f08b9a3b69a00c1e6f1ff6b1`，另含本轮 `pom.xml` 覆盖率配置；正式业务源码与测试源码均未修改。

| 测试类型 | 测试组 | 测试数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 普通测试（Surefire） | 22 | 143 | 0 | 0 | 0 |
| 真实 MySQL/HTTP（Failsafe） | 27 | 436 | 0 | 0 | 0 |
| 合计 | 49 | 579 | 0 | 0 | 0 |

## 覆盖率

| 指标 | 已覆盖 / 总数 | 覆盖率 |
| --- | ---: | ---: |
| 行 | 3959 / 4137 | 95.70% |
| 分支 | 4229 / 5794 | 72.99% |
| 指令 | 54821 / 58947 | 93.00% |
| 方法 | 1602 / 1650 | 97.09% |
| 类 | 336 / 344 | 97.67% |
| 复杂度 | 3127 / 4567 | 68.47% |

统计来自 `backend/target/site/jacoco/jacoco.xml` 的顶层 counter，百分比四舍五入保留两位。HTML 默认表格展示指令/分支的整数百分比，其显示值可能与这里的四舍五入值不同；行覆盖率须使用 LINE counter。报告覆盖全部 344 个正式编译类，没有人为排除包或类；测试夹具不属于正式源码统计范围。

当前课程截图要求运行后端 JaCoCo 并检查覆盖率，未规定最低比例。本轮未增设达标阈值，不以测试通过或覆盖率较高代记负责人验收、真实外部 OA 接入或服务器部署。

## 团队共享与报告查看

团队统一从项目首页的 JaCoCo 链接进入本说明，在线查看本次测试数量、覆盖率、采集限制与复跑方法。完整明细使用[HTML 报告归档](reports/JaCoCo-2026-10-26.zip)，下载解压后打开 `report/index.html`，无需单独传给某位成员。归档的 SHA-256 见[校验文件](reports/JaCoCo-2026-10-26.zip.sha256)。

仓库直接展示本 Markdown；HTML 明细通过归档在本机浏览，没有另建公开报告站点。归档固定对应本次运行，不是自动更新的实时覆盖率。

- HTML 入口：`backend/target/site/jacoco/index.html`，可点击包、类和源码查看红/黄/绿标记。
- XML/CSV：同目录 `jacoco.xml`、`jacoco.csv`。
- 原始采集数据：`backend/target/jacoco.exec`。
- 团队共享归档：`docs/05-detailed-design/reports/JaCoCo-2026-10-26.zip`；包含完整 HTML、XML/CSV、生成时的本说明、结构化测试计数、配置与源码摘要和原始 exec。本机 `.tmp` 保留同内容副本。
- 本机完整构建日志：`backend/.tmp/jacoco-full-verify.log`；每组详细结果位于 `target/surefire-reports` 与 `target/failsafe-reports`。

`target` 与 `.tmp` 均被 Git 忽略；本次选择将上述固定归档纳入版本管理，避免只有运行者能看到完整报告。单个 HTML 文件缺少样式、图片与包/源码明细。`clean` 将删除 `target`，但不会删除 `docs` 中的共享归档。

## 统计边界

JaCoCo agent 同时挂载于 Surefire、Failsafe 的测试 JVM，追加写入一个 exec 文件，在 verify 阶段汇总。原 Mockito agent 和 `-Xshare:off` 保留；未启用 coverage 时仍按原方式运行测试，已额外运行默认 profile 的 4 项 `IdempotencyHttpTest` 验证通过。

独立子进程不继承测试 JVM agent。管理员初始化的 8 项打包命令测试及演示 CLI 测试实际通过，但其独立 Java 子进程内执行的代码未计入本次覆盖率，因此 bootstrap 包显示 0%，demo 包行覆盖率为 16.28%。这是本次采集范围的限制，不能理解为这些测试未运行；本轮保留全部源码作为分母，没有删除低覆盖包提高比例。

测试使用本轮新建的回环非 3306 MySQL8.4.8 实例，执行前由测试核对 datadir、版本和 binlog 关闭。结束核对剩余随机 schema/账号为 0/0，实例正常退出（退出码 0）。未连接或修改已有业务数据库。临时实例数据目录保留在 `.tmp` 以便审计。

日志中的预期错误响应与故障注入不等于 JUnit 失败；最终以 49 份 JUnit XML 的失败/错误/跳过均为 0 及 Maven `BUILD SUCCESS` 判定本次测试结果。

## 复跑方式

使用 JDK21、Maven Wrapper，先准备专属临时 MySQL8.4，数据目录必须位于 `backend/.tmp`，端口必须是非 3306 的回环端口，binlog 关闭；仅在本次终端设置 `RBAC_ISOLATED_MYSQL_PORT` 和 `RBAC_ISOLATED_MYSQL_DATADIR`。测试会使用临时 root 空口令创建/清理随机库和账号，严禁指向已有开发库或业务库。

在 `backend` 目录执行：

```sh
./mvnw clean verify -Pcoverage,mysql-check
```

Windows 使用 `mvnw.cmd`。仅运行普通测试可用 `./mvnw clean verify -Pcoverage`，但应明确标注未包含真实 MySQL/HTTP 测试。首次执行需要下载 JaCoCo 及 Maven 依赖。

配置与版本核实来源：[JaCoCo 官方 Maven 使用说明](https://www.jacoco.org/jacoco/trunk/doc/maven.html)、[prepare-agent 参数及 argLine 衔接](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html)、[0.8.15 正式发布记录](https://www.jacoco.org/jacoco/trunk/doc/changes.html)。
