# 需求分析导入记录

- 导入日期：2026-10-15（Asia/Shanghai）。
- 用户指定来源：`D:\NoahProject\codexProject\ooad`。
- 实际导入资料：来源下全部有效 `docs/`，复制至当前仓库 `docs/`。
- 需求基线日期：2026-10-13。
- 文件数：396；总大小：39,073,839 字节。
- 完整性：复制后逐文件比对 SHA-256，与来源一致；未修改或移动来源文件。
- [逐文件导入清单](import-manifest.json)保存相对路径、大小和导入时摘要。

## 导入范围

完整保留需求总入口、`usecases-v1/`、`usecase-tables-v1/`、`flows-v1/`、`sequences-v1/`、`activities-v1/`、`dataflows-v1/`、`按用例分类/` 及 `sync_usecase_folders.py`，包括 Markdown、drawio、预览、映射和维护脚本。

来源根目录 `AGENTS.md` 中的权威来源、同步副本及不恢复历史等规则已合并至本仓库 `AGENTS.md`，不是直接覆盖新项目协作规则。

未导入 `.export-staging/`、`.tmp_excel_libs/`、`.tool-install-review/` 及根目录导出/清洗辅助脚本；它们不属于来源声明的当前需求基线。

原需求中的外部课程资料绝对路径作为来源引用保留，本次未复制那些路径下的原始 Excel 等材料。图形生成脚本的字体等环境依赖沿用原有设置；初始化没有重新生成图。

## 后续维护

当前仓库为后续项目维护位置，不建立与旧目录的自动双向同步。修改本项目正式用例或图后，通过 `python docs/01-requirements/sync_usecase_folders.py` 更新本项目阅读副本。

导入清单是首次导入的历史快照；后续正常修改以 Git 为依据，不把历史摘要用作禁止修改的约束。`.gitattributes` 关闭自动换行转换，以保留导入字节及现有同步清单的摘要。

导入文档保留原有 CRLF 和课程简表的 12 处末尾空行。首次提交的格式检查对新增项目文件执行，既有资料以完整性校验为准，避免为了格式整理改变导入原文和同步摘要。

2026-10-15 目录整理后，上述已导入需求的当前位置为 `docs/01-requirements/`，共享记忆仍在 `docs/project/`。导入清单中的旧目标路径仅描述首次导入，不修改其路径或摘要；定位当前需求时将原 `docs/` 前缀替换为 `docs/01-requirements/`。
