# 后端部署、初始化与验收设计

当前服务器未购买、MySQL未配置，本轮没有部署目标、安装、连接或SQL执行。下列是BE-01/03/32/34的执行清单和设计取值，命令用于后续实现的说明，不是当前可运行脚本或已部署证据。后端可部署的验收同时要求业务链路、持久化、健康、重启和恢复，前端不作为前置。

## 目标形态与准备

拟定演示目标：一台Linux宿主、JDK21、单实例可执行jar、MySQL8.4、systemd进程管理、HTTPS反向代理。数据库可同机仅监听本地或在独立受控网络；不要求容器/Kubernetes。服务器OS/CPU架构/资源、域名或证书方式、网络和购买预算由BE-01确认，本轮不指定云厂商/付费规格。容器镜像可作为后续包装选项，当前主验收产物是jar，不能因Docker未就绪阻断所有开发。

目标目录设计：`/opt/rbac-admin/releases/<commit>/app.jar`不可变产物，`/etc/rbac-admin/`外置配置和密钥文件，`/var/lib/rbac-admin/imports/`持久文件，运行日志由systemd/受控轮转接收。应用以专用非root身份运行；访问文件只允许其受控目录，不把客户端文件名直接拼路径。API只经HTTPS代理暴露，数据库端口不对公网开放。具体系统命令在OS确认后由BE-32提供。

按D-038，BE-01先记录基座开发所需共同main、用户JDK/构建及隔离MySQL实际条件、独立评审方式。真实成员的业务分派留基座验收后；目标主机/OS/架构、部署数据库补丁与连接、磁盘/证书/部署操作者在BE-32/34落实。缺目标服务器只阻塞相应部署验收，不阻断具备隔离环境的基座实施，不能用空健康接口宣称已部署。

## 环境配置与产物

| 配置组 | 设计内容 |
| --- | --- |
| 数据库 | JDBC URL、schema、运行账号、独立迁移账号；口令通过外置环境/受限文件提供；连接UTC、InnoDB、utf8mb4、RC写/RR读按程序边界 |
| Spring | profile显式demo/server/migrate/CLI；open-in-view=false、Hibernate ddl-auto=validate；普通运行flyway.enabled=false、迁移模式单独启用 |
| JWT | admin私钥及可信kid、三类issuer/audience、业务/OA可信公钥和绑定、服务身份状态；受控CLI的模拟服务私钥不作为公开资源 |
| 幂等 | HMAC当前keyId及窗口内旧密钥、7天最小完成保留、60秒租约；密钥不存DB正文、不写日志 |
| 文件/任务 | 受控上传持久目录、10MiB/解压50MiB/1000行限制；OA/executor扫描与退避；同一guard参与全部维护写 |
| 网络/观测 | app绑定本地8080作为设计默认，代理443；健康仅状态，日志含requestId/安全业务ID，不含token/密码/文件全文 |

必要配置缺失应启动失败；默认配置不包含可用密码或宽泛服务身份。不得将完整Environment、JWT、Authorization或原始导入内容记录进异常响应/日志。版本信息只公开安全构建标识，不公开环境秘密。

构建拟采用`./mvnw verify`（Windows为mvnw.cmd），产物与commit、依赖锁定清单、测试报告、SHA256共同保存。单元/HTTP/MySQL集成失败不发布，不能以`skipTests`产物关闭任务。当前仓库尚无Wrapper和pom，此命令本轮未执行。

## 迁移与初始化顺序

1. 在隔离MySQL8.4审查数据库草案及实际SQL mode/字符集/时区；备份当前目标DB、文件与密钥配置的对应关系，确认恢复点。空库也记录版本和schema。
2. BE-03将已审查schema草案转为V1版本化表/约束迁移；依次CREATE全部表再ALTER外键，保留即时FK、不关闭检查、不自动清库。JPA只validate，不承担建库或结构升级。
3. V2不可变性触发器使用完整CREATE TRIGGER语句；如用Java migration，去掉mysql客户端DELIMITER后逐条提交完整语句，不能按触发器体内部的分号盲目切割。必须在真实MySQL/Flyway验证41触发器编译与保护，静态解析不够。
4. V3只初始化`mutation_guard(id=1)`。Flyway schema history控制一次执行；应用运行时发现guard缺失必须失败，不静默重建。结构迁移不嵌入明文密码、真实人员或猜测授权。
5. 用高权限迁移身份运行独立非Web migrate模式，成功退出后验证schema history/约束/触发器，再切换无DDL权限的运行身份。migrate模式不启动HTTP/调度器，运行进程不持迁移口令。
6. 受控bootstrap-admin命令只在明确初始化条件下创建初始总管理员，密码来自受控输入、哈希后持久，必须首次改密；空员工关联只用于此部署身份。不按每次启动重置、不预置固定口令。
7. demo配置由受控命令登记示例服务身份公钥/绑定；示例组织员工通过正式导入，角色配置/授予通过演示OA流程。未知清单映射留待核对，不能用“全部”默认放行。

MySQL DDL有隐式提交，迁移中断可能留下已建对象。失败后停止应用升级，读取Flyway和实际结构诊断，修复脚本/恢复隔离验证后重新计划；禁止自动clean、直接repair校验和或盲目重跑所有DDL。发布脚本必须先停止业务写入再迁移，不能假设全份DDL能ROLLBACK。依据：[MySQL隐式提交](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html)、[Boot迁移支持](https://docs.spring.io/spring-boot/how-to/data-initialization.html)。

## 启动、重启与回退

正常server/demo模式在校验schema版本、guard、必要密钥/文件目录后启动，恢复扫描可靠PENDING待发送、APPROVED/FAILED可重试项。网络回调可能重复，不能重置事件/最终结果/success或周期。文件与DB在应用进程外持久化，重启不依赖内存Map恢复业务真相。

应用回退：停止新流量/调度，保留持久状态，选择与当前schema兼容的前一产物并用同一密钥/identity/文件，启动后检查健康及恢复扫描。若schema不兼容，不直接换旧jar；执行预先验证的恢复/修复方案。数据库回退不是删除迁移记录，备份恢复也需要保留/核对外部已受理OA和本地成功历史，避免重放旧批准。

备份演练恢复到**独立隔离环境**，核对employee/source/baseline/grant、最终结果/success、账号sessionEpoch、审计及原文件一致；不得用恢复员工状态代替备份恢复。课堂演示最低要求每次结构变更前有可恢复备份；保留周期、RPO/RTO和长期归档尚未由用户确认，在服务器方案确定时记录，不假称有生产运维SLA。

## 可部署完成条件

- BE-34记录真实服务器版本、提交、jar校验和、迁移历史、健康和HTTPS调用结果。
- 全部67入站契约有HTTP覆盖，演示OA关联/最终回调/实际执行链路可演示；原型Mock不能充当后端结果。
- 重启后账号/任命/来源周期/授权/文件/防重连续，至少执行一次从批准记录恢复和一次数据隔离恢复演练。
- 关键越权、重复回调、同key异内容、来源0基线、关权/停用与授权两个顺序、批量第N项失败和恢复不复权通过真实MySQL测试。
- 未完成/未运行项保留在Issue和发布说明；仅jar能启动或本地Mock200不能记为整个RBAC0完成。
