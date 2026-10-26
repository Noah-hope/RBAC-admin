# 顶层数据流图

当前版本为一页简图：权限系统整体、四类外部实体、八条主要数据流。连线只概括数据类型，不展开业务步骤或字段。

用户确认保留此简化版。样式：系统深蓝底白字（#17365D）、外部实体灰底（#D9D9D9）、箭头线宽3；全部文字统一宋体加粗（SimSun，bold）。后续项目设计图默认沿用该字体与字重。旧版本和业务详页不再保留。PNG使用本机simsun.ttc加描边模拟粗体；原生图源与SVG采用宋体bold。

- [可编辑 drawio](系统边界级数据流图.drawio)
- [本地预览](顶层数据流图预览.html)
- [PNG](系统边界级数据流图.png)／[SVG](系统边界级数据流图.svg)

| 外部实体 | 提供给权限系统的数据 | 从权限系统获得的数据 |
| --- | --- | --- |
| 总管理员 | 登录、配置、授权、人事及查询资料 | 身份、管理结果及审计资料 |
| 权限管理员 | 登录及范围内管理、查询资料 | 身份、范围内资料及办理结果 |
| OA 系统 | 审批关联、批准／驳回结果 | 权限变更申请、结果接收反馈 |
| 业务系统后端 | 员工、公司及权限查询条件 | 有效权限集合或校验结果 |

HR 文件由总管理员上传，不增加独立 HR 接口；普通员工通过业务系统使用权限。身份、管理范围与业务权限相互独立。OA 接收成功不等于权限执行生效。校验失败等错误包含在相应办理或调用结果中。

原业务详页和逐字段说明已移除。当前已新增[第一层总图](../第一层数据流图.drawio)，顶层简图保持；P1～P7处理分图已补齐，链接见[需求总入口](../../README.md)；各分图保留一个整体处理。

用户最新交付约定：后续绘图只生成drawio，不生成PNG/SVG/HTML。下方旧生成命令会同时刷新辅助预览，保留为历史维护说明，后续不直接运行；现有图片未在第一层绘制中更新。

生成与结构检查：

```powershell
python docs/01-requirements/dataflows/context/generate_context.py
python docs/01-requirements/dataflows/context/test_context.py
```

官方 drawio 查看器的本地渲染检查：

```powershell
node docs/01-requirements/dataflows/context/verify_drawio.cjs .tmp/drawio-render-check/viewer-static.min.js .tmp/drawio-render-check/simple.png
```

图源为原生 XML；SVG/PNG 为同源辅助预览。原生图源通过本地 Edge 与官方查看器检查，覆盖 100%／28% 缩放下的横排、无重叠和无越界，不等同于桌面客户端验证。
