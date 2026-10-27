# 后端 JaCoCo 覆盖率报告

## 测试结果

运行完成时间：2026-10-27T09:29:51+08:00（Asia/Shanghai）。Maven 完整运行耗时 06:08 min。

| 测试类型 | 测试组 | 测试数量 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 普通测试（Surefire） | 28 | 179 | 0 | 0 | 0 |
| 真实 MySQL/HTTP（Failsafe） | 28 | 454 | 0 | 0 | 0 |
| 合计 | 56 | **633** | **0** | **0** | **0** |

## 覆盖率

| 指标 | 已覆盖 / 总数 | 覆盖率 |
| --- | ---: | ---: |
| 行 | 4126 / 4137 | **99.73%** |
| 分支 | 4507 / 5796 | **77.76%** |
| 指令 | 56641 / 58950 | **96.08%** |
| 方法 | 1640 / 1650 | **99.39%** |
| 类 | 344 / 344 | **100.00%** |

统计包含全部 344 个正式编译类，没有配置包或类排除规则。测试夹具不属于正式源码报告。百分比由 XML 顶层 counter 计算，四舍五入保留两位；HTML 首页默认展示指令、分支的整数百分比，不能把指令覆盖率当作行覆盖率。

**当前行覆盖率为 99.73%，尚未达到100%，剩余 11 行。** 未通过删除防御逻辑、排除类或修改统计范围制造满覆盖；剩余路径见[测试与采集验证](JACOCO-IMPROVEMENT.md)。

## 查看完整报告

直接阅读本页即可查看总结果。查看源码明细：下载[完整 HTML 报告](reports/JaCoCo-2026-10-27.zip)，先解压整个压缩包，再进入 report 文件夹，用浏览器打开 index.html。点击包名、类名可查看源码执行标记：绿色为已执行、黄色为部分执行、红色为未执行。不要只发送 index.html，必须保留完整目录。

归档的 SHA-256 见[校验文件](reports/JaCoCo-2026-10-27.zip.sha256)。包内包含 HTML/XML/CSV、JUnit结果摘要XML、测试计数、源码与配置指纹、父子进程及合并后的 exec 数据和文件校验清单。结果对应上述运行时间及包内源码指纹，不会随代码变更自动更新。

## 测试与采集范围

- 覆盖身份与权限边界、错误响应与脱敏、角色/来源、申请审批与执行、导入、审计、事务回滚和并发等自动化场景。
- 实际启动打包后的 Web 与迁移程序，检查 HTTP 健康、迁移历史、退出行为和无隐式管理员创建；JPA 实际加载任命实体。
- 加密提供者缺失、JDBC初始化失败等故障在专属子 JVM 中验证；不修改测试主进程的全局安全提供者。
- Surefire/Failsafe 的测试 JVM 与 53 个独立 Java 子进程均采集执行记录，每个子进程写独立 exec，在 post-integration-test 合并后生成报告。
- 测试使用专属回环非3306的 MySQL8.4.8，核对实例身份后创建随机库与账号。结束时剩余随机库/账号0/0，实例正常退出0，未操作已有业务数据库。
- 未启用 coverage 的默认 clean verify 也通过 179 项普通测试，用于验证默认入口；重复执行不计入 633 项数量。
- 仍有 1289 个分支未覆盖。覆盖率表示代码执行范围，不等于业务正确率，不替代负责人确认、真实外部OA对接或服务器部署验收。

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
- 完整日志：backend/.tmp/jacoco-line-full-verify.log。

clean 会删除 target，正式报告使用 clean 完整运行，避免旧采集文件混入。docs 中的固定归档不受 clean 影响。

参考：[JaCoCo agent](https://www.jacoco.org/jacoco/trunk/doc/agent.html)、[Maven 参数衔接](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html)、[执行记录合并](https://www.jacoco.org/jacoco/trunk/doc/merge-mojo.html)。
