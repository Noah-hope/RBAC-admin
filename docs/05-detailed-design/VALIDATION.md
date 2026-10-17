# 详细设计与环境准备验证记录

2026-10-17；实施计划修订1.2/D-039，延续D-038先基座后分派。本轮从main/169556d干净工作区创建codex/foundation-environment，只记录用户环境与本人确认安排。没有创建正式工程或连接数据库，没有合并推送。

## 实际工具检查与用户确认

用户确认已有云服务器，后续可Docker部署数据库，目前先用本机MySQL；JDK21已下载、IDE可引用；基座由本人确认无需别人评审。本次只读Get-Command实际确认java/javac均未在PATH，JAVA_HOME未配置，没有运行该本地JDK。完整来源区分见[ENVIRONMENT.md](ENVIRONMENT.md)。

没有修改环境变量、查询数据库服务版本、连接/创建schema/执行SQL或访问云服务器。不能把用户确认的环境存在写成服务版本、Docker部署或构建已验证。永久JAVA_HOME/PATH不是硬性门槛，后续可使用IDE或显式JDK运行构建。

## 设计检查

复用此前临时隔离PyYAML6.0.3运行以下设计维护命令，未安装后端依赖：

```powershell
python docs/05-detailed-design/design_plan.py
python docs/05-detailed-design/validate_design.py
git -c core.whitespace=cr-at-eol diff --check
```

本轮静态校验核对：

- 13模块、35任务、41项未运行验收、67入站及1出站操作追踪、全部43表覆盖和依赖DAG完整。
- BE-01为IN_PROGRESS且只表示环境准备，BE-02～35仍PLANNED。基座PREPARING，门槛NOT_EVALUATED、验收证据为空，业务30项未分派。
- 5项基座reviewPolicy=OWNER_ACCEPTANCE，reviewRequired=false、确认人为USER、reviewerSlot不适用、单列独立评审工时为0。30项业务继续INDEPENDENT_REVIEW和原评审估算，豁免没有扩展到业务。
- 各任务原技术依赖、操作、模块、AT、开发估算、表/T/I与9416980一致；只有D-039明确取消的基座单列评审工时改变。重新核算总量78/145.5/239和阶段容量；本人自检/确认已含原开发估算，未声称实测工作量。
- 业务启动门槛、未分派人员状态、输入模型/参数/响应与上游契约一致；规范化生成、Python语法、本地链接与决策编号唯一性通过，实际数量见[validation.json](validation.json)。
- 两份六页图源与原技术图逐字节相同，结构/端点/几何检查通过；未重新绘制、Node修复或桌面呈现，静态检查不替代视觉验收。
- 需求、API1.2.0、数据库1.1及原Apifox导出摘要不变。没有backend/deploy/tools/.github，也没有业务构建、RED/GREEN、MySQL/JPA/并发/OA/恢复或服务器实测。

本报告PASS只证明准备记录和计划结构一致，不能替代[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)要求的真实结果及用户本人确认。免除他人评审不免除TDD、自检、事务/约束验证或交接证据。

## 历史及限制

1.0/1.1曾核实版本官方依据、生成图源并运行静态校验，未运行实际框架组合。本轮不改变技术选型，不重复查询；新增D-039后共39条决策。原估算79.25/148/244含单列基座独立评审，本轮按用户确认去除1.25/2.5/5，保留开发及业务评审。阶段容量仍不是日历承诺。

本次没有强制用户配置全局环境变量、没有把普通本机数据库当可随意改动的业务环境；后续先明确本机服务版本、受控账号与本项目独立开发/测试schema。旧的服务器未购买、所有环境未确定等说法只作为历史，当前以用户确认记录为准。
