# BE-01 环境与协作准备核对

更新：2026-10-18/D-050。本文件保存当前环境事实，排障历史以 Git 和各 BE 实施记录为准。整体门槛及待办见[交接核对](FOUNDATION-REVIEW.md)，操作见[后端运行说明](../../backend/README.md)。

## 已核对的本机技术准备

| 项目 | 当前证据 | 使用边界 |
| --- | --- | --- |
| 工程与 IDE | 单 Maven 工程 `backend`，包 `com.adgn.backend`，YAML；用户 IDEA 已成功运行 | 沿用项目根 Git；项目、Maven 导入/运行和应用 JDK 均选21 |
| JDK / Maven / Boot | 实测 Corretto21.0.12.1、Wrapper3.3.4/Maven3.9.11、Boot4.0.8；构建和打包通过 | 使用 IDE 或会话显式 JDK，不要求永久修改 JAVA_HOME/PATH；个人安装位置不进入共享文档 |
| 本机 MySQL | 用户日志/截图为8.4.8、3306；开发库 `rbac_admin_dev` 与测试库 `rbac_admin_test` 分开 | 数据库身份由 URL 和账号确定，不按 Navicat/IDE 显示名称推断 |
| 测试库 | 用户 V1～V3 success均1、guard(1,0)、再次迁移 up to date；普通应用连接/启动/健康已确认 | 用户库检查不等于全部业务测试；不重复清库 |
| 开发库 | 15:44用户截图显示 V1～V3 success均1、guard(1,0)；普通应用日志连接开发库、Hikari/JPA/Tomcat启动、健康UP | 已从此前空库状态完成初始化；运行日志没有展示账号，不据此推断实际用户名或迁移执行者 |
| 运行账号 | 用户截图：`rbac_app@localhost` 对两个库仅 SELECT/INSERT/UPDATE/DELETE，另有 USAGE | 普通应用保持 DML 账号，不以 root 运行 |
| 迁移账号 | 用户截图：`rbac_migrator@localhost` 对两个库有DML及CREATE/DROP/REFERENCES/INDEX/ALTER/TRIGGER；没有截图所示全局SUPER或GRANT OPTION | 具备库内DDL授权不等于绕过 binlog 的触发器额外限制 |
| 触发器初始化策略 | 用户只读查询 `log_bin=1`、`log_bin_trust_function_creators=0`；迁移成功历史已确认 | 沿用管理员受控初始化方案，独立迁移配置使用实际授权身份；agent未修改用户库/全局参数，不声称普通迁移账号已独立成功执行全套迁移 |
| 幂等启用 | D-049用户确认健康UP，并单独明确开关true；配置失败测试已有证据 | agent不读取IDE密钥；同库实例密钥环须一致，独立本机库可各自生成 |
| 自动化隔离 | 最新64项离线＋42项专属MySQL8.4.8检查通过，实例身份/datadir写前核验、随机schema/账号清理及专属进程关闭已有记录 | 临时datadir位于backend/.tmp，回环非3306端口，binlog关闭；不指向已有开发/测试库；不是用户binlog环境的完全复现 |

agent本轮只读复核既有报告、产物及用户日志/截图记录，未连接已有数据库、执行SQL或读取凭据，也未重新运行Java测试。健康UP只证明相应运行准备，不证明后续业务授权和端到端行为。

## BE-01 还剩什么

1. 本人核对[整体交付报告](FOUNDATION-REVIEW.md)并确认 BE-01 收尾及基座整体范围；BE-02～05单项确认沿用。当前BE-01仍IN_PROGRESS。
2. 安排实际主线交付。当前分支 `codex/foundation-environment` 含公共实现，本地 main 为169556d、尚无本次基座；远程访问本轮未重新核验，不自动合并/推送。
3. 登记团队实际账号并核验仓库访问/PR权限。业务分派时补实际Owner、真实Issue与依赖，由负责人自检确认，不设独立评审人；当前30项仍未分派，不将A～D建议组当作真实人员。
4. CI/保护规则按实际托管能力落实；云端Docker数据库、部署/备份恢复由后续交付任务验证，不反向要求本机基座先完成云端部署。

不再要求反复提供健康截图、重建已经迁移成功的库或为验收填充正式业务数据。团队接入步骤见[简明使用指南](FOUNDATION-USAGE.md)。

## 已解决的 Wrapper 排障

初始工程引用Maven发行版3.10.0时，IDE导入出现Guice/MissingImplementation及JVM目标5提示；后来将发行版固定回既定3.9.11，保留Wrapper脚本3.3.4及JDK21，构建和IDE运行恢复。没有完整复现IDE内部根因，不能把时间上的恢复夸大为所有IDE均不支持某版本。最新配置与验证见[BE-02实施记录](BE-02-IMPLEMENTATION.md)。
