# 项目共享技能

四个技能保存在仓库 `.agents/skills/`，与文档一起提交 Git。开展项目工作优先读取这些项目副本，团队不依赖某台机器的全局安装路径。技能是执行方法，项目状态、业务决策和团队共享记忆仍只维护在 `docs/`。

| 技能 | 项目入口 | 用途与来源 |
| --- | --- | --- |
| grill-me | [SKILL.md](../../.agents/skills/grill-me/SKILL.md) | 用户指定的需求访谈入口；复制现有本机技能快照，委托 `grilling` |
| grilling | [SKILL.md](../../.agents/skills/grilling/SKILL.md) | 分轮追问和确认；复制现有本机技能快照，与委托入口一并保留 |
| frontend-design | [SKILL.md](../../.agents/skills/frontend-design/SKILL.md) | 前端设计；来自 [Anthropic 官方仓库](https://github.com/anthropics/skills/tree/main/skills/frontend-design) 的已安装快照，保留 `LICENSE.txt` |
| test-driven-development | [SKILL.md](../../.agents/skills/test-driven-development/SKILL.md) | 后续正式业务逻辑实现；来自 [obra/superpowers 固定版本](https://github.com/obra/superpowers/tree/bb92a77741419a4ab5f06e711a283343f1ada0c3/skills/test-driven-development)，保留 `writing-good-tests.md` 与同版本根目录 MIT 许可证 |

`grill-me` 是委托入口，实际访谈前同时读取同级 `grilling/SKILL.md`；不把入口描述当成已完成访谈。正式业务逻辑按 TDD 先编写并运行失败测试，再最小实现、验证和重构；编写或修改测试时读取测试指南。

以上技能以原文件复制，未改写技能正文。原技能目录含有的 `agents/openai.yaml` 配套元数据一并保留。TDD 许可证从固定上游版本补齐。

## 版本与核验

[skills-manifest.json](skills-manifest.json) 记录原始来源、已知上游版本、文件大小及 SHA-256。TDD 固定版本为 `bb92a77741419a4ab5f06e711a283343f1ada0c3`；其余现有安装未留可验证的上游提交，因此只记录本地快照及摘要，不猜测版本或来源。

后续更新技能时，检查上游变化、许可证与配套依赖，更新该清单及受影响项目规则后一并提交 Git。仓库技能不写入凭据或真实会话；不自动同步覆盖本机全局技能。
