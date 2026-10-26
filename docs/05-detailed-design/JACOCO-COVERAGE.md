# 后端 JaCoCo 覆盖率报告

## 测试结果

运行完成时间：2026-10-26 22:10:49（Asia/Shanghai）。Maven 完整运行耗时4分45秒。

| 测试类型 | 测试组 | 测试数量 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 普通测试（Surefire） | 24 | 163 | 0 | 0 | 0 |
| 真实 MySQL/HTTP（Failsafe） | 27 | 439 | 0 | 0 | 0 |
| 合计 | 51 | **602** | **0** | **0** | **0** |

## 覆盖率

| 指标 | 已覆盖 / 总数 | 覆盖率 |
| --- | ---: | ---: |
| 行 | 4075 / 4137 | **98.50%** |
| 分支 | 4440 / 5796 | **76.60%** |
| 指令 | 56227 / 58950 | **95.38%** |
| 方法 | 1625 / 1650 | **98.48%** |
| 类 | 343 / 344 | **99.71%** |

统计包含全部344个正式编译类，没有配置包或类排除规则。测试夹具不属于正式源码报告。百分比由 XML 顶层 counter 计算，四舍五入保留两位；HTML 首页默认展示指令、分支的整数百分比，不能把指令覆盖率当作行覆盖率。

## 查看完整报告

下载[完整 HTML 报告](reports/JaCoCo-2026-10-26.zip)，先解压整个压缩包，再进入 report 文件夹，用浏览器打开 index.html。点击包名、类名可查看源码执行标记：绿色为已执行、黄色为部分执行、红色为未执行。

归档的 SHA-256 见[校验文件](reports/JaCoCo-2026-10-26.zip.sha256)。包内包含 HTML/XML/CSV、测试计数、源码与配置指纹、父子进程及合并后的 exec 数据和文件校验清单。结果对应上述运行时间及包内源码指纹，不会随代码变更自动更新。

## 测试与采集范围

- 覆盖输入校验、身份与权限边界、角色/来源、申请审批、执行恢复、导入、审计、事务回滚和并发等现有自动化场景。
- Surefire/Failsafe 的测试 JVM 与48个独立 Java 子进程均采集执行记录，每个子进程写独立 exec，在 post-integration-test 合并后生成报告。
- 管理员初始化及演示命令通过真实退出码、数据库状态、JWT、禁止覆盖和输出脱敏断言；演示能力不代表真实外部 OA 已接通。
- 测试使用专属回环非3306的 MySQL8.4.8，核对实例身份后创建随机库与账号。结束时剩余随机库/账号0/0，实例正常退出0，未操作已有业务数据库。
- 未启用 coverage 的默认运行也通过163项普通测试，用于验证默认入口；重复执行不计入602项的数量。
- 尚有62行及1356个分支未覆盖。覆盖率表示代码执行范围，不等于业务正确率，也不替代负责人确认或服务器部署验收。

测试断言、故障复现及采集验证见[验证说明](JACOCO-IMPROVEMENT.md)。

## 复跑方式

使用 JDK21 和 Maven Wrapper。准备专属临时 MySQL8.4：数据目录位于 backend/.tmp，端口为非3306的回环端口，关闭 binlog；在本次终端设置 RBAC_ISOLATED_MYSQL_PORT 和 RBAC_ISOLATED_MYSQL_DATADIR。严禁指向现有开发库或业务库。

在 backend 目录执行：

```sh
./mvnw clean verify -Pcoverage,mysql-check
```

Windows 使用 mvnw.cmd。只运行普通测试可用 ./mvnw clean verify -Pcoverage，应明确该范围未包含真实MySQL/HTTP测试。

- HTML/XML/CSV：backend/target/site/jacoco/。
- 父进程：backend/target/jacoco.exec；子进程：backend/target/jacoco-subprocess/*.exec；合并记录：backend/target/jacoco-merged.exec。
- JUnit XML：backend/target/surefire-reports/、backend/target/failsafe-reports/。
- 完整日志：backend/.tmp/jacoco-improved-full-verify.log。

clean 会删除 target，正式报告使用 clean 完整运行，避免旧采集文件混入。docs 中的固定归档不受 clean 影响。

参考：[JaCoCo agent](https://www.jacoco.org/jacoco/trunk/doc/agent.html)、[Maven 参数衔接](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html)、[执行记录合并](https://www.jacoco.org/jacoco/trunk/doc/merge-mojo.html)。
