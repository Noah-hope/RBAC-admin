# 部署与初始化操作

当前项目复用既有 Docker Swarm 集群：节点 3 编译打包并上传 SWR，节点 2 部署前端 Nginx 和 Java 后端，新 worker 部署 MySQL。数据库节点标签为 `rbac_mysql=true`，应用节点标签为 `rbac_app=true`。

## 当前部署入口

| 资料 | 用途 |
| --- | --- |
| [Swarm 部署方案](SWARM-DEPLOYMENT.md) | 节点分工、网络、存储和实施顺序 |
| [Swarm 逐步操作指南](docker/README.md) | 节点准备、镜像构建上传、Secrets、迁移、初始化及启动 |
| [首次数据库配置](docker/mysql.yml) | 仅部署 MySQL，只需三个数据库 Secrets |
| [后端单独启动](docker/backend.yml) | 密钥准备后与 mysql.yml 一起部署，前端和TLS可后续准备 |
| [完整 Stack 配置](docker/stack.yml) | 后续添加前端；与分阶段部署使用相同 Stack 名称 rbac |
| [Stack 中的固定配置](docker/stack.yml) | 直接修改镜像、目录、副本数、端口及 key id，无需额外环境文件 |
| [后端 Dockerfile](../backend/Dockerfile) / [前端 Dockerfile](../frontend/Dockerfile) | 在节点 3 构建镜像，构建上下文为仓库根目录 |

默认先运行 MySQL，前后端保持 0 副本；显式迁移、首次管理员初始化成功后，再依次启用后端和前端。真实账号密码、密钥及证书放在仓库外，通过 Secrets 提供，不写进镜像。

按用户要求，当前部署不依赖 swarm.env、shell export 或 YAML 变量替换；YAML 的 environment 部分仅用于向现有应用传入固定配置。后端已使用上传成功的 d8bd624 镜像，无需因本次配置调整重新构建。用户已确认MySQL8.4.8与后端均为1/1、后端健康UP，密钥生成及管理员初始化已成功。前端镜像与HTTPS证书仍待准备；[前端构建步骤](docker/README.md#节点3构建前端镜像)使用DaoCloud获取Node/Nginx并通过npmmirror安装锁定依赖。

部署文件已完成文档所列本地检查，云端进度来自用户提供的输出；前端、真实登录/首次改密和重启恢复仍待验证，不据后端健康UP宣布完整服务器验收通过。

## 本机初始化与演示

本机 IDEA/JAR 的管理员初始化和登录排查说明保留在 [旧指南的本机初始化章节](legacy-systemd/README.md#本机先验证管理员初始化)。迁移和初始化仍是独立入口，不随普通应用启动自动执行；本机使用方式不受旧服务器方案归档影响。

申请、OA 受理和批准执行的受控本机演示见 [OA 演示步骤](DEMO-OA.md)，变量见 [演示模板](demo.env.example)。这些资料仍在使用，演示适配器不代表真实 OA 接通。

## 旧方案归档

2026-10-26 按用户要求，将 systemd 单机直接运行 JAR 的三个服务模板、三份环境变量模板、宿主机 Nginx 模板及原操作说明归档到 [legacy-systemd](legacy-systemd/README.md)。模板内容保持，不再作为当前部署路径；后续无需继续维护该方案，除非明确恢复使用。

## 任务归属

A 当前进行的是 **BE-34「实际服务器部署、重启与演示验收」的部署准备**：确定目标节点、Docker/Swarm 包装、网络、持久化和发布流程。真实目标服务器部署、迁移、HTTPS、重启持久化及隔离恢复检查仍待执行，本地文件准备不能代替 BE-34 验收。

**BE-35「交接、任务证据与发布收尾」**在 BE-34 完成后汇总发布说明、提交/产物/验收证据和已知限制。两项负责人均为 A，定义见 [任务清单](../docs/05-detailed-design/TASKS.md)。本次归档不改变既有任务或负责人的验收状态。

后端密钥及无前端启动步骤见 [操作指南](docker/README.md#后端单独启动管理员初始化成功之后)。密钥生成工具位于 [GenerateDeploymentKeys.java](docker/GenerateDeploymentKeys.java)，只为本次实验部署生成初始密钥，不自动替换已有密钥或代表真实OA/业务系统已对接。
