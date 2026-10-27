# 项目文档与团队共享记忆

本目录是项目团队共享记忆的统一维护位置。当前状态、待办、决策、流程及各阶段交付物均在这里保存，并随对应改动提交 Git。根目录 `AGENTS.md` 提供协作入口，`README.md` 提供项目导航。

2026-10-26 当前设计图状态：数据流图与数据库ER图已更新并完成原生渲染检查。数据流图包括[顶层简图](01-requirements/dataflows/context/系统边界级数据流图.drawio)、[第一层横向总览及七个子页](01-requirements/dataflows/第一层数据流总览.drawio)、第一层分区总图及七个详细分图；[数据库ER图](04-database/database-erd.drawio)共四页，使用中文及英文表名、跨表直线和同表自关联回线。后续绘图仅交付drawio。本次只同步设计资料状态，不改变业务验收、部署或负责人确认状态。

实施历史摘录（2026-10-19/D-058）：用户A已继续[BE-07账号管理](05-detailed-design/BE-07-ACCOUNTS.md)，在含已确认BE-06祖先的分支实现，已按D-059本人确认，并由D-060合入推送main/ff19d17；当时其他28项仍待实现，后续状态见共享记忆。

## 工作入口

- [当前状态与交接](project/MEMORY.md)：已完成事项、待办、验证结果及下一步。
- [项目流程](project/WORKFLOW.md)：需求 → 界面 → API → 数据库 → 详细设计与实现。
- [已确认决策](project/DECISIONS.md)：与建议、未决事项区分。
- [统一术语表](project/GLOSSARY.md)：统一总管理员、权限管理员、业务角色、管理范围及授权状态等概念，团队和 agent 交流先查阅。
- [目录约定](project/LAYOUT.md)：当前最小结构及资料迁移说明。
- [项目技能](project/SKILLS.md)：仓库技能入口、来源、版本与核验方式。
- [项目设计说明书PDF](../output/pdf/集中权限管理系统项目设计说明书.pdf)：当前1.2版35页，七章正文及附录A接口清单、B具名模块追踪，12张图直接内嵌，ER按正文页宽展示；黑体粗标题、仿宋正文及全黑文字。[AsciiDoc源文件](06-project-document/项目设计说明书.adoc)及相邻images目录可继续编辑。
- [项目设计说明书HTML阅读版](../output/html/集中权限管理系统项目设计说明书.html)：同一AsciiDoc源文件转换，目录可点击，12张图内嵌，可离线用浏览器打开；保留黑体粗标题、仿宋正文及黑色文字。运行 `node docs/06-project-document/export_html.cjs` 可单独更新，PDF构建时也会同步生成。

- [后续开发如何复用基座](05-detailed-design/FOUNDATION-USAGE.md)：本机启动、公共边界和团队配合的简明说明。
- [基座交接核对](05-detailed-design/FOUNDATION-REVIEW.md)：F-01～06结果及BE-01剩余事项。

## 阶段资料

| 目录 | 内容 | 当前状态 |
| --- | --- | --- |
| [01-requirements](01-requirements/README.md) | 正式需求、用例、简表、图源、辅助预览与同步脚本 | V1.3保持；顶层及第一层数据流图已补齐并检查，横向总览含P1～P7子页；独立分图补充校验读取 |
| [02-ui-design](02-ui-design/README.md) | Vue 3 + TypeScript + Tailwind CSS 界面原型及设计说明 | 可作为 API 输入；按 D-029，3 项原型补齐后续跟踪，不阻塞 API |
| [03-api](03-api/README.md) | OpenAPI 契约、示例、状态/错误、追踪及数据库交接 | Git1.2.0正式跨阶段修订完成；Apifox1.2.0指定客户端导出核心一致已核对；两处元数据差异及MCP限制保留 |
| [04-database](04-database/README.md) | ER图、完整字典、约束/索引、事务/JPA/幂等、DDL草案与验证 | 数据库1.1及43表结构保持；四页ER展示已更新并原生渲染检查，45跨表直线＋1自关联回线；原设计评审结论及业务验收状态保持 |
| [05-detailed-design](05-detailed-design/README.md) | 后端模块/流程/部署、两阶段任务与基座交接 | 模块与当前P1～P7处理对照已同步；A～D原任务分工保持，实际实现、主线交付、本人确认及部署状态见共享记忆和各任务证据 |
| [06-project-document](06-project-document/项目设计说明书.adoc) | AsciiDoc项目设计说明书、引用PNG及构建脚本 | 当前1.2版35页PDF，七章正文＋附录A/B、12图内嵌；第五章采用直白解释和示例，其他正文及67操作清单保持；不替代各阶段权威基线或业务验收 |

阶段状态以 `project/MEMORY.md` 为准，目录存在不代表阶段完成。阶段变化时同步更新导航。

## 需求维护

在仓库根目录运行：

```powershell
python docs/01-requirements/refresh_requirements.py
```

默认更新正文配套图源与阅读副本，保留已有 PNG/SVG。当前约定仅交付drawio，不运行图片导出；数据流图与ER图按各自现有入口维护，不能用旧预览覆盖当前图源。详见 [预览维护规则](project/PREVIEWS.md)。
