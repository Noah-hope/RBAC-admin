# JaCoCo 覆盖率测试操作指南

助教第一阶段现场检查使用，所有命令均在**仓库根目录**的 macOS 终端执行。测试使用内嵌 H2 与临时密钥，**无需启动 MySQL**；使用 `clean test` 会清空旧构建并现场生成最新 HTML 报告。

## 现场执行命令

### 1. 运行 RBAC1 结构化后端并打开报告

```bash
mvn -f rbac1-structured/pom.xml clean test && open rbac1-structured/target/site/jacoco/index.html
```

### 2. 运行 RBAC1 DDD 后端并打开报告

```bash
mvn -f rbac1-ddd/pom.xml clean test && open rbac1-ddd/target/site/jacoco/index.html
```

### 3. 一次运行两套后端并同时打开两份报告

```bash
mvn -f rbac1-structured/pom.xml clean test && mvn -f rbac1-ddd/pom.xml clean test && open rbac1-structured/target/site/jacoco/index.html rbac1-ddd/target/site/jacoco/index.html
```

> 提示：
> - 执行期间等待出现 `BUILD SUCCESS` 即可，浏览器会自动打开对应 HTML 报告（如教室网络较慢或断网，在 `mvn` 后加 `-o` 离线运行，例如 `mvn -o -f rbac1-ddd/pom.xml clean test`）。
> - 报告文件位置：`rbac1-structured/target/site/jacoco/index.html`、`rbac1-ddd/target/site/jacoco/index.html`。

## 报告查看说明

| 项目 | 说明 |
| --- | --- |
| `Total`（表尾） | 当前模块整体覆盖率汇总 |
| `Missed Instructions` / `Cov.` | 指令覆盖率（最常用总览指标） |
| `Missed Branches` / `Cov.` | 分支覆盖率（`if` / `switch` 等条件分支） |
| 点进包与类源码 | 绿色为完全覆盖，黄色为分支部分覆盖，红色为未覆盖 |
| 排除项 | 仅在 `pom.xml` 中排除了编译期自动生成的 `cn/edu/xmu/clonefactory/**`，未排除任何业务源码 |
