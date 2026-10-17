# 详细设计验证记录

日期：2026-10-17（Asia/Shanghai）。从`codex/database-design/bd26465`干净工作区原地建立`codex/detailed-design`，无worktree、不合并main。用户授权本轮设计/分解/估算，不是业务实现或真实部署。

## 实际范围

读取项目入口、MEMORY/WORKFLOW/DECISIONS/GLOSSARY/LAYOUT、需求与数据流、API和数据库交接。本轮采用可用drawio技能生成原生可编辑模块/流程图，读取项目TDD/writing-good-tests用于验收交接。没有项目drawio副本；PATH/常见路径本次复核未见Desktop，沿用此前全链路定位无Desktop的事实，使用XML方案；未进行客户端布局/图片导出验收。

官方技术依据于本轮读取：Boot4.0系统要求/BOM/初始化，Maven3.9.11，Apache POI/CSV发布，Spring Security密码编码、OWASP密码存储、GitHub Issue/PR及保护流程，链接在对应工程/协作文档。仅核实文档能力与可用版本，不安装后端依赖或运行组合。图源遵循drawio XML简明规则并进行修复检查。

设计生成与检查可通过已准备的临时PyYAML6.0.3路径运行，未创建仓库工具环境或修改原型依赖：

```powershell
python docs/05-detailed-design/design_plan.py
python docs/05-detailed-design/validate_design.py
git -c core.whitespace=cr-at-eol diff --check
```

需要PyYAML；本轮复用先前隔离临时依赖，没有下载后端库。生成器只写本目录文档、JSON和drawio，静态校验不执行SQL/HTTP。检查记录见[validation.json](validation.json)，包括实际通过项及数量、摘要和限制；不能只因文件存在宣布完成。

## 结果的含义

通过项覆盖13模块、35后端Issue草案、41未运行验收（含1前端后置）、68操作精确追踪；模块/任务依赖无未知节点/环，主责唯一，API输入模型/参数/成功响应与权威契约一致，数据库表及T/I来自原映射；估算/评审/槽位负载总和一致；六页图源ID/端点/几何有效；本地链接、Python语法、生成确定性及上游权威文件摘要检查。实际最终数量以机器报告为准。

本轮实际生成后独立执行Node修复检查，两份drawio无机械修复差异；再运行校验器验证生成确定性。六页共59节点、57连线，矩形不重叠且端点存在；新设计/共享导航167处本地链接有效，2份设计维护脚本语法通过。首次设计自查36条决策编号唯一，主分支/远程交接复核新增D-037后为37条。图中批准/驳回、成功/失效/失败重试和导入回滚采用条件分支，自动执行由内部扫描进入；文本/结构检查仍不等于客户端视觉验收。

完整后端容量估算是设计判断：79.25/148/244人时，并非性能或开发实测。容量下界不等于真实完成日期。35个任务全PLANNED，remoteIssueNumber/remotePrNumber为NULL；41项验收全DESIGNED_NOT_RUN，不写成RED/GREEN测试通过。

35张任务卡均补充acceptanceScope，区分本任务负责部分与完整系统场景；校验器检查边界非空。人工复核了BE-01准备、BE-02骨架、BE-03基础映射、BE-31演示受理及BE-32包装不提前依赖全系统完成，完整证据由BE-33/34/35汇总。此项为设计检查，不是任务验收通过。

没有创建Java工程、实体、迁移/部署程序、真实Issue/PR或服务器；没有运行应用构建/测试、启动/连接MySQL、执行SQL/DDL、做并发/恢复/性能/OA/HTTP集成或部署验收。未改正式需求、原型、API1.2.0、数据库1.1或原始Apifox导出。此前API/数据库验证结果不冒充详细设计的运行证据。

主分支/远程交接复核（同日）：按用户后续明确授权合并并首次推送main，远程回查通过，事实见MEMORY/D-037。新增仓库HTTPS地址时，原本机路径检测把https尾部的`s:/`误判为盘符；修正为只匹配独立盘符，保留Windows绝对路径检查。更新共享状态后重新执行静态校验，业务/数据库验收仍未运行。
