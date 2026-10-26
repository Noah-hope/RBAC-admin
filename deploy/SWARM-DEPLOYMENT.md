# Docker Swarm 部署方案与实施顺序

日期：2026-10-26。状态：已按用户确认的节点分工编写前后端 Dockerfile、Stack、Secrets 入口及 MySQL/Nginx 配置；未连接云服务器、未执行部署。逐步操作见 [容器部署操作指南](docker/README.md)。本文件中的命令用于后续服务器操作，不能当作部署成功证据。

## 已确认的环境与分工

复用已有 Swarm，不创建新集群，不再次执行 `docker swarm init`。新购 MySQL 服务器作为 worker 加入现有集群。保留实验的 `javaee-proxy`，本项目配置使用独立的加密 overlay 网络 `rbac-internal`。演示期间可暂停原实验服务，保留数据及原副本数。

| 位置 | 本项目职责 |
| --- | --- |
| 现有管理节点 | 节点标签、Stack 发布、服务状态检查 |
| 节点 1 | 保留原实验数据库 |
| 节点 2 | 前端 Nginx、后端 Java 服务，初期各一个副本 |
| 节点 3 | 获取代码、编译、测试、构建前后端镜像并推送 SWR |
| 新服务器 | 本项目 MySQL 单副本，标签 `rbac_mysql=true` |
| 华为云 SWR | 保存带版本标签的前后端镜像 |

部署配置使用应用节点标签 `rbac_app=true`，仅标记节点 2；服务器端尚未创建标签及网络。Stack 名称为 `rbac`。新节点仅添加本项目标签，不沿用原实验数据库的 `mysql=yes` 标签。加入前还需检查现有服务是否有约束：没有约束的旧服务可能被调度到新节点，标签不会自动隔离其他服务。

## 第一步：核对集群并加入新服务器

在现有管理节点查看节点及获取 worker 加入命令：

```sh
docker node ls
docker swarm join-token worker
```

在新服务器运行输出的加入命令。使用现有集群实际可达的管理节点地址；加入令牌按凭据保管。确认服务器之间的 Swarm 管理、节点发现和 overlay 数据通信链路已放通，不能仅凭网络名称判断互通。

加入后，在管理节点替换下面两个节点名再执行：

```sh
docker node update --label-add rbac_mysql=true <新服务器节点名>
docker node update --label-add rbac_app=true <节点2实际节点名>
docker node inspect <新服务器节点名> --format '{{json .Spec.Labels}}'
docker node inspect <节点2实际节点名> --format '{{json .Spec.Labels}}'
```

最终 MySQL 调度约束为 `node.labels.rbac_mysql == true`，前后端约束为 `node.labels.rbac_app == true`。每个角色仅标记其目标服务器。

## 第二步：准备存储、网络和访问入口

以下为部署默认目录；后端镜像 UID/GID 为 10001。挂载前必须创建并配置所有权，不使用所有人可写权限。

| 节点 | 宿主机目录 | 用途及容器目标 |
| --- | --- | --- |
| 新服务器 | `/srv/rbac-mysql/data` | MySQL 数据，挂载到 `/var/lib/mysql` |
| 新服务器 | `/srv/rbac-mysql/backups` | 备份输出；还需保存异机副本，不能仅依赖同机磁盘 |
| 节点 2 | `/srv/rbac-admin/imports` | 导入文件，可写挂载到 `/var/lib/rbac-admin/imports` |
| 管理节点 | `/etc/rbac-deploy/` | 仓库外的受限凭据源文件；TLS/JWK 通过 Secrets 分发，无需额外部署环境文件 |

后端 server profile 会校验导入目录为已存在、绝对、可写路径。设置 `RBAC_IMPORT_DIRECTORY=/var/lib/rbac-admin/imports`。导入文件必须与数据库一起纳入备份恢复，不能存入容器临时层。

部署采用 Swarm Secrets，JWK 文件变量指向 `/run/secrets/` 实际路径。新增容器入口将对应模式需要的数据库口令、HMAC 密钥从 Secret 文件按原文读取后导出给 Java，不使用 source/eval。应用原业务配置不变，运行服务不持有迁移、root 或初始化凭据。

前后端构建产物放入镜像，无需挂载源码、JAR 或前端 dist。运行日志输出标准输出和标准错误，在部署配置中设置轮转上限。MySQL 数据和节点 2 导入目录均为本地持久化，不会因加入 Swarm 自动复制；因此初期使用固定节点、单副本。节点故障时服务不可用，恢复前必须恢复数据，不能把空目录当作故障转移。

建议使用本项目独立 overlay 网络，前后端通过服务名通信，后端通过 MySQL 服务名连接数据库。MySQL 初期不发布宿主机 3306，后端不发布 8080；只发布 Nginx 访问入口。管理工具访问数据库另行使用受控通道。

发布前核对集群已有服务占用的端口。配置选用 host 发布模式配合节点约束，仅在节点 2 发布 Nginx 的 80/443。域名、实际 HTTPS 证书及是否存在端口冲突仍需服务器操作者填写核对；自定义 HTTPS 端口须同时调整 Nginx 跳转。

## 第三步：编写并验证容器部署文件

已准备以下文件：

| 文件 | 职责 |
| --- | --- |
| [后端 Dockerfile](../backend/Dockerfile) 与同目录 `Dockerfile.dockerignore` | Java 21 运行镜像、非 root 运行、排除凭据和临时文件 |
| [前端 Dockerfile](../frontend/Dockerfile) 与同目录 `Dockerfile.dockerignore` | Node 构建前端，Nginx 提供静态文件 |
| [容器 Nginx 配置](docker/nginx.conf) | SPA 路由、API 原路径代理、HTTPS 与上传限制 |
| [Swarm Stack](docker/stack.yml) | 镜像、节点约束、网络、挂载、Secrets、资源和日志限制 |
| [固定参数 Stack](docker/stack.yml)及[操作指南](docker/README.md) | 直接编辑 YAML 配置，再创建数据库、独立迁移、管理员初始化和启动应用 |

当前后端默认监听 `127.0.0.1`，Stack 通过 `SERVER_ADDRESS=0.0.0.0` 覆盖以允许 Nginx 容器访问，不发布宿主机 8080。容器配置使用 framework 代理头处理，Nginx 覆盖转发头，仅允许本项目受控服务接入专用网络。

旧宿主机代理示例已归档到 `deploy/legacy-systemd/nginx.conf.example`，使用 `127.0.0.1:8080` 且根路径返回 404。当前容器使用 `deploy/docker/nginx.conf`，通过后端服务名代理并提供前端静态页面。

后端使用节点 3 已安装的 Java 21 和 Maven 执行 clean verify，默认 Dockerfile 仅复制已生成 JAR，并复用本地 SWR 的 openjdk:21-alpine 基础镜像；不再使用容器下载构建 JDK/Maven或 apt。前端要求 Node >=22.12.0，依赖通过锁文件安装，其 Node/Nginx 基础镜像网络访问另行核对。构建机器和目标节点的 CPU 架构须匹配。镜像应使用明确版本或源码提交号，并记录实际 digest，不依赖可覆盖的 `latest`。

先执行项目规定的后端普通与隔离 MySQL 检查、前端构建与相关检查，再构建和推送镜像。不得照抄实验的跳过测试命令作为发布验证。`docker stack deploy` 不负责构建镜像，Stack 中引用预先推送到 SWR 的镜像。

## 第四步：首次云端部署顺序

1. 核对实际节点、系统和 Docker 版本、CPU 架构、资源余量、网络以及入口端口；对齐源码版本与镜像 digest。
2. 准备数据目录、Secrets 与配置；在构建节点及管理节点按需要登录 SWR。
3. 先部署 MySQL 并验证就绪，创建目标 schema、独立迁移账号和受限应用账号。MySQL 使用项目已验证的 8.4 系列，具体补丁版本与镜像 digest 在实施时锁定。
4. 使用相同后端版本的一次性迁移入口执行 Flyway；确认成功退出及迁移记录。迁移失败停止后续操作，不自动 clean、repair 或清库。
5. 仅首次且目标库没有总管理员时，执行独立管理员初始化入口，确认成功后移除临时凭据。初始化不是普通应用启动步骤。
6. 启动后端并检查容器内健康与日志，然后启动前端 Nginx、验证实际访问入口。不得依赖 Stack 的服务声明顺序保证 MySQL 和迁移已完成。
7. 验证登录、首次改密、业务读写、文件导入与持久化；执行服务重建及隔离备份恢复检查后记录真实结果。

完成网络、挂载、Secrets、镜像及 YAML 固定参数核对后，在管理节点的 `deploy/docker` 同步目录运行。无需 export 或加载 swarm.env。默认应用副本数为 0，先部署 MySQL，再按操作指南分别执行迁移、初始化和启用前后端：

```sh
docker stack deploy --with-registry-auth -c stack.yml rbac
docker stack services rbac
docker stack ps rbac --no-trunc
docker service logs --tail 100 rbac_backend
```

服务名为 `rbac_mysql`、`rbac_backend`、`rbac_frontend`。Stack 发布返回不代表所有服务就绪。管理节点操作说明见 [Docker stack deploy](https://docs.docker.com/reference/cli/docker/stack/deploy/)，预构建镜像要求见 [Deploy a stack](https://docs.docker.com/engine/swarm/stack-deploy/)，调度与挂载见 [Swarm services](https://docs.docker.com/engine/swarm/services/)。

## 更新与回退

更新流程为测试、构建新版本、推送 SWR、备份、按需独立迁移、更新应用、检查业务。持久目录与有效密钥保持连续。回退应用前检查旧镜像与当前数据库结构的兼容性；服务回滚不会自动回滚数据库和导入文件。

初始资源配置为节点 2 后端 1GiB 上限（Java 堆 512MiB）、前端 128MiB，新节点 MySQL 1GiB 上限（buffer pool 256MiB）。使用 stop-first 更新，接受短暂停机；实际容量须实测。

正式操作前还需填写：新节点与节点 2 的实际节点名、Docker/OS 与架构、SWR 区域及组织、镜像版本、域名和端口、证书来源及备份去向。当前文档不代表 BE-34 或实际服务器验收完成。
