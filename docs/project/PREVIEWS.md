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

当前正文和 drawio 为 V1.3（公司—部门—岗位，部门平级）；保留图片来自 V1.1，新增的离岗关权、后台账号启停、全局员工启停图页尚无图片。图片可能包含已替代的管理规则，不能据其设计功能。各 README 与 HTML 入口显示版本提示，尚未生成的图片显示文字占位。

更新需求版本时同时调整 `preview-status.json` 的图源版本，保留原预览版本，直到显式导出完成。旧 PNG 不添加水印、不重编码，保持字节不变；脱离版本说明单独分发的图片不能作为当前需求依据。

现有脚本使用 Pillow 与原生 XML 生成本地辅助预览，不宣称为 draw.io Desktop 的正式导出。脚本依赖 Python、Pillow 和 Windows 微软雅黑字体；统一入口失败时停止后续步骤，不标记为完成。
