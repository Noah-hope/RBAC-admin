# 需求预览维护规则

确认日期：2026-10-15。用户选择“按需生成 PNG”。PNG/SVG 是可再生成的阅读附件，正文与原生 drawio 图源是日常维护对象。

## 日常修改

先修改正式详细用例及受影响的生成器/图源，再在项目根目录运行：

```powershell
python docs/01-requirements/refresh_requirements.py
```

该命令更新可编辑图、课程简表、预览入口和按用例阅读副本，执行生成器已有结构检查，**不写入 PNG/SVG**。原始用例图保留手工布局，只更新明确需要调整的文字。若仅修改正文而无生成器变化，可运行 `python docs/01-requirements/sync_usecase_folders.py`。

副本同步器检测手工修改后会停止；先将有效修改合并回正式文件。相同内容不重复写入。

## 评审或交付导出

需要当前图片时显式运行：

```powershell
python docs/01-requirements/refresh_requirements.py --previews
```

只有全部图生成成功，才将 `docs/01-requirements/preview-status.json` 的预览版本更新为图源版本，再同步副本。完整刷新会重绘全部 PNG/SVG；不在每次需求改动时执行。单个生成器也默认不导出图片，完整交付以统一命令为准。

## 版本与阅读

当前正文需求基线为 V1.3（公司—部门—岗位，部门平级）。2026-10-24 已按当前用例更新 `usecases-v1` 的四页 drawio 及四组 PNG/SVG，原路径覆盖旧图并同步按用例阅读副本。其他类型图仍保留 V1.1 预览，新增子流程图片可能尚未生成，不能据旧图片设计功能。

`preview-status.json` 的 `preview_revision` 表示尚未单独刷新的目录版本；`folder_previews` 记录已单独导出的目录版本和日期。各目录 README 使用自身版本，需求总览及按用例阅读副本提示混合版本；单目录刷新不得把全部图标为当前版本。完整 `--previews` 刷新成功后仍以统一版本为准。

更新需求版本时同时调整 `preview-status.json` 的图源版本，保留原预览版本，直到显式导出完成。日常编辑不重编码旧 PNG；用户要求导出当前图片时直接覆盖对应 PNG/SVG，不额外保留旧图副本，历史可由 Git 追溯。脱离版本说明单独分发的旧图片不能作为当前需求依据。

现有脚本使用 Pillow 与原生 XML 生成本地辅助预览，不宣称为 draw.io Desktop 的正式导出。脚本依赖 Python、Pillow 和 Windows 微软雅黑字体；统一入口失败时停止后续步骤，不标记为完成。
