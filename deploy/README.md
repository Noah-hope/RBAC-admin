# RBAC1 Docker Swarm 部署

保留两个独立后端，同时部署到两个 Worker，共用一个生产 MySQL 数据库、一对 JWT 密钥和一个前端。前端通过部署变量选择后端，不按请求随机混用两种实现。RBAC0 不在本次部署范围内。

前端、两个后端和 MySQL 全部作为 Docker Swarm 服务运行在服务器上；本机只编译应用和构建镜像，不运行正式服务。本目录分别提供数据库 Stack 和应用 Stack，应用更新不触碰数据库服务。不自动配置已有网关或初始化集群。以下命令是手工部署步骤，须替换示例仓库、节点名和域名后执行。

## 1. 文件与五台服务器的分工

| 服务器 | 角色 | 节点标签 |
| --- | --- | --- |
| Manager | 管理集群，在这里执行部署脚本 | 不安排本项目应用容器 |
| 数据库 Worker | MySQL 容器、持久化数据卷 | `rbac.role=database`，仅此一台 |
| 前端 Worker | Nginx 页面与 API 代理 | `rbac.role=frontend` |
| 结构化后端 Worker | `rbac1-structured` | `rbac.role=structured` |
| DDD 后端 Worker | `rbac1-ddd` | `rbac.role=ddd` |

两个后端在容器内统一监听 8080，仅通过 overlay 网络访问，不发布后端公网端口。前端默认通过 Swarm ingress 发布 8088，供现有网关转发；生产网关负责域名和 HTTPS。ingress 端口可以通过任一可达的 Swarm 节点访问，前端标签只决定容器运行位置。

| 文件 | 用途 |
| --- | --- |
| `rbac1-structured/Dockerfile` | Java 21 运行镜像，复制结构化 JAR |
| `rbac1-ddd/Dockerfile` | Java 21 运行镜像，复制 DDD JAR |
| `frontend/Dockerfile` | Nginx 镜像，复制编译后的 dist |
| `frontend/nginx/default.conf.template` | 前端路由回退与 API 转发 |
| `deploy/stack.yml` | 服务、节点约束、网络、Secret、内存和更新配置 |
| `deploy/mysql-stack.yml` | 独立 MySQL 服务、数据库节点约束和持久化卷 |
| `deploy/.env.example` | 不含密码的部署变量示例 |
| `deploy/deploy.sh` | 默认部署应用；加 `--database` 部署数据库 |

## 2. 本机构建应用

在仓库根目录执行。需要 JDK 21、Maven、项目兼容的 Node.js/npm。先按照两个后端 README 安装课程提供的 `cn.edu.xmu:clonefactory:0.0.1-SNAPSHOT`，再构建。Docker 不参与 Maven 编译，也不需要访问个人 Maven 仓库。

```powershell
mvn -f rbac1-structured/pom.xml clean package
mvn -f rbac1-ddd/pom.xml clean package
```

DDD 打包时引用 `rbac1-structured/src/main/resources/db/migration`，因此构建时保留整个仓库目录。两种镜像最终都含相同的 V1、V2 迁移。不要直接使用旧 target 中的 JAR。

前端在 `frontend` 目录执行：

```powershell
npm ci
npm test
npm run build
```

生成的后端文件分别位于各模块 `target/*-SNAPSHOT.jar`，前端产物位于 `frontend/dist`。生产镜像不运行 Vite，`RBAC_API_TARGET` 只用于本地开发。

## 3. 构建并推送三个镜像

本机须安装并启动 Docker Desktop，使用 Linux 容器；也可以把 JAR、dist 和 Dockerfile 上传到安装 Docker 的 Linux 构建机执行构建。构建架构应与目标 Worker 一致，当前例子按 x86_64/amd64 节点编写。不要因为本机是 ARM 就直接推送 ARM 单架构镜像给 amd64 集群。

在仓库根目录执行，替换镜像仓库路径：

```powershell
docker login registry.example.com
docker build --platform linux/amd64 -t registry.example.com/your-project/rbac1-structured:1.0.0 ./rbac1-structured
docker build --platform linux/amd64 -t registry.example.com/your-project/rbac1-ddd:1.0.0 ./rbac1-ddd
docker build --platform linux/amd64 -t registry.example.com/your-project/rbac-frontend:1.0.0 ./frontend
docker push registry.example.com/your-project/rbac1-structured:1.0.0
docker push registry.example.com/your-project/rbac1-ddd:1.0.0
docker push registry.example.com/your-project/rbac-frontend:1.0.0
```

Java 镜像默认 `eclipse-temurin:21-jre-alpine`，前端默认 `nginx:stable-alpine`。如果构建机不能访问这些仓库，可使用 `--build-arg JAVA_IMAGE=...` 或 `--build-arg NGINX_IMAGE=...` 替换为可信镜像源；Java 基础镜像须提供 Java 21、Alpine/apk，Nginx 基础镜像须保留官方模板入口。正式发布可把基础镜像固定到已验证的版本或 digest。每次应用发布使用新的版本标签。

## 4. 确认节点与网络

在 Manager 执行下列只读命令，先核对旧集群 Docker 版本、架构和网络，不自动升级引擎：

```bash
docker node ls
docker node inspect $(docker node ls -q) --format '{{.Description.Hostname}} | {{.Description.Platform.Architecture}} | {{.Description.Engine.EngineVersion}}'
docker network ls
```

将下面三个示例节点名换成 `docker node ls` 中的实际 Worker：

```bash
docker node update --label-add rbac.role=frontend frontend-node
docker node update --label-add rbac.role=structured structured-node
docker node update --label-add rbac.role=ddd ddd-node
docker node update --label-add rbac.role=database database-node
```

默认复用外部 overlay 网络 `javaee-proxy`。如果没有合适网络，再创建专用网络并将其名称填入 `.env`：

```bash
docker network create --driver overlay rbac-net
```

MySQL 容器与应用均加入同一个外部 overlay 网络，默认数据库服务名为 `rbac-db_mysql`。网关也需要连通前端。跨节点的 Swarm 网络端口须按现有集群规则互通。

## 5. 准备生产数据库和 Secret

使用官方 `mysql:8.4` 镜像，不需要为 MySQL 编写 Dockerfile，也不需要在服务器宿主机安装 MySQL。MySQL 首次启动空数据卷时，自动创建 `MYSQL_DATABASE`（默认 `rbac1`）和 `RBAC1_DB_USERNAME`（默认 `rbac1_app`），并授予该账号目标库权限。后端通过这个账号执行 Flyway 建表及业务读写，不使用 root。

数据保存在数据库节点的 Docker 卷 `rbac-db_mysql_data`（前缀取自 `DATABASE_STACK_NAME`），挂载至容器 `/var/lib/mysql`，重建容器不丢失数据。local 卷不跨服务器复制，因此只能给指定数据库节点设置 `rbac.role=database`；数据库节点故障时不会自动把本地数据迁移到另一台。不要改数据库 Stack 名、删除数据卷或把同一标签随意移给空节点。定期备份数据库数据，生产数据不会自动同步到本机开发库。

JWT 密钥只生成一次。在可信构建机执行下列命令可生成密钥；然后安全传给 Manager，或直接在具备 JDK 21 的 Manager 执行：

```bash
java rbac1-structured/tools/GenerateJwtKeys.java .local/deploy-keys
```

两个后端使用同一对密钥。不要重新部署时重新生成，不把私钥复制进镜像或 Git。

在 Manager 使用 Bash 创建 Secret。分别设置 MySQL root 密码、后端专用数据库账号密码和首次总管理员密码，它们是三种不同凭据：

```bash
read -rsp 'MySQL root 密码: ' root_password; printf '\n'
printf '%s' "$root_password" | docker secret create rbac_mysql_root_password_v1 -
unset root_password

read -rsp '后端数据库账号密码: ' db_password; printf '\n'
printf '%s' "$db_password" | docker secret create rbac_db_password_v1 -
unset db_password

read -rsp '首次管理员密码: ' admin_password; printf '\n'
printf '%s' "$admin_password" | docker secret create rbac_admin_password_v1 -
unset admin_password

docker secret create rbac_jwt_private_v1 .local/deploy-keys/jwt-private.pem
docker secret create rbac_jwt_public_v1 .local/deploy-keys/jwt-public.pem
docker secret ls
```

Secret 名已存在时不重复创建。Secret 内容变更使用新名称，例如 `_v2`，再修改 `.env` 对应名称，不在命令行中打印密码。MySQL 镜像的建库、建用户和密码变量只对首次空数据卷初始化生效；已有数据时修改 Secret 不会自动修改数据库密码，轮换时须先在数据库中正确修改账号密码，再同步 Secret 和应用配置。

Stack 将数据库、管理员密码分别挂载为 `/run/secrets/RBAC1_DB_PASSWORD`、`/run/secrets/RBAC1_ADMIN_PASSWORD`；Spring 通过 `configtree:/run/secrets/` 导入，与当前 application.yml 的占位符对应。公私钥以 PEM 文件挂载，非 root 后端进程 UID 10001 可读。无需修改业务配置或约定不存在的 `*_FILE` 环境变量。

MySQL 服务单独挂载 root Secret 和相同的数据库账号密码 Secret，通过官方镜像支持的 `MYSQL_ROOT_PASSWORD_FILE`、`MYSQL_PASSWORD_FILE` 读取；root Secret 不挂给后端。MySQL 没有发布宿主机 3306 端口，数据库管理可在数据库节点或 Manager 使用 `docker exec` 进入实际容器执行，不开放公网数据库端口。

## 6. 填写配置并检查

把仓库中的部署文件准备到 Manager，复制示例：

```bash
cp deploy/.env.example deploy/.env
```

至少修改以下内容：

| 变量 | 填写内容 |
| --- | --- |
| `STRUCTURED_IMAGE`、`DDD_IMAGE`、`FRONTEND_IMAGE` | 已推送的三个镜像地址及版本 |
| `DATABASE_STACK_NAME` | 默认 `rbac-db`，首次部署后保持稳定，影响服务名和卷名 |
| `MYSQL_IMAGE`、`MYSQL_DATABASE` | 默认 `mysql:8.4`、`rbac1`，数据库也在服务器容器中运行 |
| `RBAC_NETWORK` | 已存在且可连通数据库的 overlay 网络名 |
| `RBAC1_DB_URL` | 示例使用双引号，由数据库 Stack 名和库名生成，默认主机为 `rbac-db_mysql` |
| `RBAC1_DB_USERNAME` | 生产专用账号 |
| `RBAC1_JWT_ISSUER` | 稳定的正式签发方地址，课程系统配置同一个值 |
| `RBAC1_CORS_ALLOWED_ORIGINS` | 正式前端来源；跨域调用时加入课程前端的精确来源 |
| `FRONTEND_PORT` | 可用的入口端口，默认 8088，不与现有网关争用 80/443 |

`.env` 是受信任的 Bash 变量赋值文件，脚本会 source 它；不要放入从不可信来源复制的命令。不要在文件中保存密码，带 `&`、空格等字符的值需加引号。Docker Stack 不自动读取 `.env`，应使用下面的脚本。

```bash
bash deploy/deploy.sh --check deploy/.env
bash deploy/deploy.sh --check --database deploy/.env
```

这只用 Docker CLI 检查变量展开和 Stack 格式，不连接服务器部署服务，也不确认镜像、Secret、网络或 MySQL 实际存在。

## 7. 先部署 MySQL，再部署应用

先在 Manager 提交数据库 Stack：

```bash
bash deploy/deploy.sh --database deploy/.env
docker stack services rbac-db
docker service ps rbac-db_mysql --no-trunc
docker service logs -f rbac-db_mysql
```

确认 MySQL 初始化完成、容器健康后再部署应用。数据库初始化只创建库和账号，18 张业务表由后端启动时的 Flyway 迁移创建。数据库服务使用真实的本地认证查询 `SELECT 1` 做健康检查。不要因为服务刚创建就假定数据库已就绪；Swarm 不使用 Compose 的启动依赖保证就绪顺序。

数据库 Stack 只在数据库配置需要变化时重新部署。更换 MySQL 镜像版本前先备份并检查升级路径；不能把镜像标签改回旧版本当作数据库回滚。

首次使用空库，保持 `FRONTEND_BACKEND=structured`、`DDD_REPLICAS=0`。先只启动结构化后端，避免两种实现同时初始化总管理员。使用部署权限账号登录镜像仓库，再提交部署：

```bash
docker login registry.example.com
bash deploy/deploy.sh deploy/.env
docker stack services rbac
docker service ps rbac_structured --no-trunc
docker service logs -f rbac_structured
```

`rbac` 来自 `STACK_NAME`，改名后同步替换检查命令。确认 Flyway 迁移成功、后端健康、使用配置账号可以登录。健康检查使用公钥接口，仅证明后端可响应，不代表数据库查询一定正常。

将 `.env` 中 `DDD_REPLICAS` 改为 `1`，再次执行脚本并检查：

```bash
bash deploy/deploy.sh deploy/.env
docker service ps rbac_ddd --no-trunc
docker service logs -f rbac_ddd
```

两个后端均有 Flyway，迁移记录共用同一数据库；第二个启动时读取已经应用的版本，不重复建表，也不覆盖已存在的管理员密码。

此时四个业务服务都在服务器容器中：MySQL 在数据库节点，前端在前端节点，两个后端各在对应节点；Manager 负责调度。

当前两个后端各一副本、使用 stop-first 更新，更新期间可能短暂不可用；这样不要求单台后端节点同时容纳两个 JVM。它们是两种实现，不是自动互相接管的高可用副本。内存上限默认各 1024M、预留 512M，前端上限 128M，按服务器实测调整。

## 8. 前端入口与实现切换

让现有网关把正式 RBAC 域名转发至 Swarm 发布端口，例如 8088，保留原始路径和 Authorization 头。入口的 `/auth/*`、`/api/v1/*`、`/.well-known/jwks.json` 全部进入同一个前端 Nginx，由它转发给选定的后端。不要与课程系统网关上同名 `/auth` 路径混在同一域名规则中。网关应覆盖客户端提供的转发头；本配置不依赖转发头计算 JWT issuer。

默认 `FRONTEND_BACKEND=structured`。需要验证 DDD 时，先确认 DDD 服务正常且 `DDD_REPLICAS=1`，改为 `FRONTEND_BACKEND=ddd` 并重新运行部署脚本。脚本使用带 Stack 前缀的服务名，避免共享网络中其他项目的 backend 服务重名。Nginx 启动时解析目标服务，首次部署尚未就绪的瞬时代理错误应结合服务状态检查。

切换无需重建前端镜像；账号、业务数据、会话保存在同一个数据库，JWT issuer 和密钥保持相同。正式网关对外地址不随实现切换改变。两套服务没有改变现有 API 契约。

验证清单：

- 前端登录页正常，刷新 `/roles` 等页面路由不出现 404。
- `GET /.well-known/jwks.json` 返回公钥集合，而不是前端 HTML。
- 登录、查询当前用户、修改角色授权、退出均正常；退出后的令牌访问受保护接口返回 401。
- 分别通过两种后端读写同一条业务数据，确认字段和有效权限一致。
- 按 `rbac1-structured/README.md` 的「课程系统统一身份接入」登记课程应用、角色码映射和目录权限，再联调课程系统。

## 9. 更新与回滚

重新编译、测试、构建并推送新版本镜像，再更新 `.env` 中对应的镜像标签并运行部署脚本。不要在服务器直接替换容器中的 JAR。应用更新失败时 Swarm 按配置尝试回滚服务；也可把镜像标签改回上一版本再部署。应用回滚不会撤销已经执行的数据库迁移，后续迁移需保持回滚版本的数据兼容性。

容器内文件日志属于临时数据，不作为持久化备份；使用 `docker service logs` 查看控制台日志。容器标准输出设置了大小和份数限制。

## 10. 本地文件验证

以下步骤不会部署集群，也不要求 Docker 引擎运行：

```bash
bash -n deploy/deploy.sh
bash deploy/deploy.sh --check deploy/.env.example
bash deploy/deploy.sh --check --database deploy/.env.example
```

Windows 使用 Git Bash 运行脚本。本仓库 `.gitattributes` 将脚本和 Nginx 模板固定为 LF。完整的 `docker build`、`nginx -t`、容器连通性和 MySQL 初始化、持久化验收仍需要实际 Docker 引擎。
