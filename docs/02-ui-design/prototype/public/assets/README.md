# 原型本地视觉资源

日期：2026-10-16。Material 3 原型使用 Roboto、Noto Sans SC 和 Google Material Symbols Outlined；不加载第三方字体 CDN 或图标组件库。

- [manifest.json](manifest.json) 保存每个文件的下载地址、字节数和 SHA-256，以及旧图标名称到 Material Symbols 的映射。资源版本以保存的文件及摘要为准，仓库分支地址不表示自动追踪上游最新文件。
- `symbols/` 中为 49 个官方 SVG，来自 [google/material-design-icons](https://github.com/google/material-design-icons)；许可见 [symbols-LICENSE.txt](symbols-LICENSE.txt)。页面用 `UiIcon` 封装 CSS mask，不显示图标名称连字。
- `fonts/` 中为 Google Fonts 提供的 Roboto 拉丁字符子集及 Noto Sans SC 中文子集，合计 3 个 WOFF2 文件；字重范围 400～700。许可见 [roboto-OFL.txt](roboto-OFL.txt)和 [noto-sans-sc-OFL.txt](noto-sans-sc-OFL.txt)。
- Noto Sans SC 当前子集覆盖生成时原型源码中的 564 个中文字符；新增姓名或文案未覆盖的字符使用本机 sans-serif 回退。后续大量增加文案时应重新生成官方字体子集，并同步 `src/fonts.css`、资源文件和清单摘要。
- 当前清单包含 55 个资源/许可证文件，共 207,807 字节（不含清单和本文）。运行时只访问同源 `/assets/`；没有真实业务凭据。

资源导入/替换时先核对官方来源和对应许可，保留文件摘要，检查 SVG 的使用名称、字体覆盖及浏览器实际加载结果。本文记录资源维护方式；项目状态仍以 `docs/project/MEMORY.md` 为准。
