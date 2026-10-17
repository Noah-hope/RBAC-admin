# 详细设计与分工修订验证记录

日期：2026-10-17（Asia/Shanghai）；当前修订1.1/D-038。1.0技术输入bd26465，本轮从main/9416980干净工作区原地建立codex/foundation-first-plan，不建worktree、不合并或推送。用户本轮授权更新先基座后分派的文档，不是实现、建库、部署或创建远程事项。

## 本轮实际检查

运行设计生成器及静态校验器：

```powershell
python docs/05-detailed-design/design_plan.py
python docs/05-detailed-design/validate_design.py
git -c core.whitespace=cr-at-eol diff --check
```

复用此前临时隔离PyYAML6.0.3，未下载后端依赖或创建仓库工具环境。生成器只维护本目录设计资料，不执行SQL/HTTP或生成业务工程；机器结果见[validation.json](validation.json)。

- 13模块、35任务、41验收（40后端/1前端后置）、67入站和1出站追踪完整，逻辑及技术依赖DAG无环。
- BE-01～05归用户，phase=FOUNDATION、ownerSlot=USER；BE-06～35为BUSINESS、UNASSIGNED，ownerSlot=null，评审人均未登记。suggested字段与实际归属分开。
- 所有业务操作的Owner仍为空，与对应任务一致；基座验收和实际分派两项启动条件完整，基座不能反向依赖业务，当前门槛NOT_EVALUATED且证据为空。
- 独立对照9416980，35项原技术依赖、操作、模块、AT、估算、表/T/I保持相同；只调整阶段、分派和明确基座验收范围。输入模型/参数/成功响应仍精确对应权威API。
- 原总工作量79.25/148/244人时保留；重新核算5/30阶段开发、评审和两阶段容量下界。业务人员未分派，businessPersonLoad=null，不沿用旧A～D个人负载。
- 生成确定性、2份维护脚本语法、本地链接及决策编号唯一性通过。当前182处本地链接、38条决策，数量也由机器报告复算。
- 两份图源与9416980逐字节一致；6页59节点57连线的端点/几何检查继续通过。本轮未重绘、未再次执行Node修复或桌面导出；静态结构不等于视觉验收。
- 需求用例、OpenAPI1.2.0、数据库1.1及Apifox原始导出的权威摘要仍与原基线一致，没有改业务含义。

另对本轮23份新增/修改Markdown实际扫描本机路径与成对代码块，通过；DECISIONS的D-001及LAYOUT迁移说明中发现的旧存放前缀已按用户既有约定去除，保留仓库/迁移事实，未重写导入快照或Git历史。

结果仅证明设计资料结构与分工状态一致。35任务仍PLANNED、41验收仍DESIGNED_NOT_RUN；F-01～06是后续基座交接定义，没有一次真实基座验收结果。计划字段的USER不是后台账号或JWT主体。

## 验证限制及历史

没有建立backend/deploy/tools/.github，没有应用构建、业务RED/GREEN测试、MySQL/JPA/并发/故障/OA/HTTP/性能/备份或部署实测。基座交接规则见[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)，不能用本报告PASS替代其真实证据。实际Owner、Reviewer、远程Issue/PR及新个人排期需在对应阶段登记。

1.0历史：曾用可用drawio技能及XML规则生成六页图源、执行Node修复检查；未找到draw.io Desktop，未做客户端视觉验收。技术版本依据当时官方资料核实，但未运行框架组合；本轮未改变选型，不重复版本查询。首次静态自查为36条决策、主分支交接复核37条，本轮新增D-038后为38条。此前修正过本机路径检测把https尾部误判为盘符的问题；检查继续支持HTTPS链接并拒绝独立Windows盘符路径。

预算40～72人时与完整工作量仍有缺口，基座单人先行会增加阶段等待；容量下界不是日历或如期承诺。外部讨论附件未改写或纳入权威需求，Obsidian旧笔记作为带日期快照保留，实时决定以本仓库MEMORY/DECISIONS为准。
