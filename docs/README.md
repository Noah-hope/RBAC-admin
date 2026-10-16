# 项目文档与团队共享记忆

本目录是项目团队共享记忆的统一维护位置。当前状态、待办、决策、流程及各阶段交付物均在这里保存，并随对应改动提交 Git。根目录 `AGENTS.md` 提供协作入口，`README.md` 提供项目导航。

## 工作入口

- [当前状态与交接](project/MEMORY.md)：已完成事项、待办、验证结果及下一步。
- [项目流程](project/WORKFLOW.md)：需求 → 界面 → API → 数据库 → 详细设计与实现。
- [已确认决策](project/DECISIONS.md)：与建议、未决事项区分。
- [统一术语表](project/GLOSSARY.md)：统一总管理员、权限管理员、业务角色、管理范围及授权状态等概念，团队和 agent 交流先查阅。
- [目录约定](project/LAYOUT.md)：当前最小结构及资料迁移说明。
- [项目技能](project/SKILLS.md)：仓库技能入口、来源、版本与核验方式。

## 阶段资料

| 目录 | 内容 | 当前状态 |
| --- | --- | --- |
| [01-requirements](01-requirements/README.md) | 正式需求、用例、简表、图源、辅助预览与同步脚本 | V1.3 复核完成，可作为原型依据 |
| [02-ui-design](02-ui-design/README.md) | Vue 3 + TypeScript + Tailwind CSS 界面原型及设计说明 | [阶段评审有条件通过](02-ui-design/STAGE-REVIEW.md)，3 项缺口跟踪至契约冻结前 |
| [03-api](03-api/) | Apifox 契约、示例、错误及用例映射 | 可启动，契约尚未编制；先确定 Git/Apifox 同步方式 |
| [04-database](04-database/) | 实体关系、约束、索引、事务及数据字典 | 仅保留目录，尚未开始 |
| [05-detailed-design](05-detailed-design/) | 架构、流程、模块依赖和正式逻辑实现设计 | 仅保留目录，尚未开始 |

阶段状态以 `project/MEMORY.md` 为准，目录存在不代表阶段完成。阶段变化时同步更新导航。

## 需求维护

在仓库根目录运行：

```powershell
python docs/01-requirements/refresh_requirements.py
```

默认更新正文配套图源与阅读副本，保留 PNG/SVG。评审或交付需要图片时显式加 `--previews`；旧预览不作为当前需求依据。详见 [预览维护规则](project/PREVIEWS.md)。
