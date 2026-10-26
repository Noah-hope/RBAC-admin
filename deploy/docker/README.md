# 本项目 Swarm 操作指南

本项目使用现有集群：节点 3 构建并上传镜像，节点 2 运行前后端，新 worker 运行 MySQL。以下操作由服务器操作者执行；当前仅在本地准备文件，尚未部署。总体方案见 [部署方案](../SWARM-DEPLOYMENT.md)。

## 1. 节点和目录准备

在管理节点获取 `docker swarm join-token worker`，在新服务器执行输出的加入命令。不要重新初始化集群。确认新节点与原节点之间的 Swarm 管理、发现及 overlay 流量可达。在管理节点替换实际节点名：

```sh
docker node ls
docker node update --label-add rbac_mysql=true <新节点名>
docker node update --label-add rbac_app=true <节点2名>
docker network create --driver overlay --opt encrypted rbac-internal
```

网络已存在时先用 `docker network inspect rbac-internal` 核对驱动、范围和加密设置，不删除重建正在使用的网络。每类标签只给指定节点。原实验服务可暂停，但不要删除数据卷或目录；对 replicated 服务先记录原副本数再缩为 0。global 服务需单独处理，不能直接套用 scale。

新节点创建数据库目录；官方镜像会在首次启动时设置数据目录所有权：

```sh
sudo install -d -m 0750 /srv/rbac-mysql/data
sudo install -d -m 0700 /srv/rbac-mysql/backups
```

节点 2 创建导入目录，后端镜像固定使用 UID/GID 10001：

```sh
sudo install -d -o 10001 -g 10001 -m 0750 /srv/rbac-admin/imports
```

不要使用 `chmod a+wr`。确认现有节点 2 上没有服务占用本项目入口端口；MySQL 和后端不发布端口。

## 2. 在节点 3 测试、构建并上传

使用 JDK 21、仓库 Maven Wrapper、Node >=22.12.0 和 Docker。先选定实际源码版本，检查工作区并记录完整 SHA。示例从仓库根目录运行：

部署文件交付分支为 `codex/swarm-deployment`，同步到 Gitee 与 GitHub。节点 3 可先克隆此分支：

```sh
git clone --branch codex/swarm-deployment https://gitee.com/Noah_lzh/rbac-admin.git
cd rbac-admin
```

GitHub 地址为 `https://github.com/Noah-hope/RBAC-admin.git`，也可用相同分支克隆。分支推送及两个仓库的实际提交号以交付结果为准。节点 3 已由用户截图确认 Java 21.0.2、Maven 3.9.16、amd64；本项目仍优先用 Wrapper 固定的 Maven 3.9.11。版本查询使用 `java --version`、`javac --version`、`mvn --version`。

```sh
git rev-parse HEAD
git status --short
cd backend
./mvnw --batch-mode --no-transfer-progress clean verify
cd ../frontend
npm ci
npm run build
cd ..
```

发布前沿用 [后端部署指南](../README.md)的隔离 MySQL 检查及前端相关测试要求。真实 MySQL 测试需要专属临时实例和显式隔离参数，不对新部署业务库运行。镜像构建中的普通测试不能替代完整发布验证。

替换区域、组织和版本，按 SWR 控制台生成的登录指令登录。版本应对应已提交源码，不能将未提交修改标记成旧提交。构建机器架构须匹配节点 2：

```sh
export RBAC_BACKEND_IMAGE=swr.REGION.myhuaweicloud.com/ORGANIZATION/rbac-backend:COMMIT
export RBAC_FRONTEND_IMAGE=swr.REGION.myhuaweicloud.com/ORGANIZATION/rbac-frontend:COMMIT
docker build --pull -f backend/Dockerfile -t "$RBAC_BACKEND_IMAGE" .
docker build --pull -f frontend/Dockerfile -t "$RBAC_FRONTEND_IMAGE" .
docker push "$RBAC_BACKEND_IMAGE"
docker push "$RBAC_FRONTEND_IMAGE"
```

两个 Dockerfile 均以仓库根目录为构建上下文，使用各自 `Dockerfile.dockerignore` 排除无关资料、环境文件和本地依赖。后端镜像构建执行普通 `clean verify`；前端执行 `npm ci` 和生产构建。首次构建需要访问镜像仓库及依赖源。基础镜像标签是起始默认值，验证后记录或固定 digest，前后端最终镜像优先按 digest 发布。

## 3. 在管理节点准备 Secrets 和发布变量

将本目录无凭据文件同步到管理节点受控目录，例如 `/opt/rbac-deploy/docker/`，保持文件相对位置。配置文件使用 LF。真实凭据放在仓库外的 `/etc/rbac-deploy/`，权限仅部署操作者可读。

准备三个互不相同的数据库密码文件：`root-password`、`app-password`、`migration-password`。app/migration 密码至少 24 字符，仅使用 `A-Z a-z 0-9 _ -`，以便首次初始化安全生成 SQL；可分别生成：

```sh
umask 077
openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n' > /etc/rbac-deploy/app-password
openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n' > /etc/rbac-deploy/migration-password
openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n' > /etc/rbac-deploy/root-password
```

上级目录须先由操作者创建并授予本人访问权限。不要覆盖已有部署的密码文件；更改文件或 Secret 不会自动修改数据库账号密码。

另外准备以下文件：

| 文件 | 内容 |
| --- | --- |
| `idempotency-keys.json` | JSON 密钥环，如 `{"server-v1":"实际随机32字节以上密钥的标准Base64"}`；不是示例明文 |
| `admin-private.json` | 当前部署的 RSA 私钥 JWK Set，包含匹配的 kid |
| `business-public.json`、`oa-public.json` | 可信服务 RSA 公钥 JWK Set，不含私钥 |
| `fullchain.pem`、`privkey.pem` | 实际域名的 HTTPS 证书链和私钥 |

密钥格式及身份绑定沿用 [RSA 配置说明](../../docs/05-detailed-design/BE-06-LOCAL-SETUP.md)，不要每次发布重新生成。幂等密钥 JSON 使用标准 Base64，与数据库密码的 base64url 字符限制不同。

在管理节点创建 Secrets（固定名称对应 Stack 引用）：

```sh
docker secret create rbac_root_password /etc/rbac-deploy/root-password
docker secret create rbac_app_password /etc/rbac-deploy/app-password
docker secret create rbac_migration_password /etc/rbac-deploy/migration-password
docker secret create rbac_idempotency_keys /etc/rbac-deploy/idempotency-keys.json
docker secret create rbac_admin_jwk /etc/rbac-deploy/admin-private.json
docker secret create rbac_business_jwk /etc/rbac-deploy/business-public.json
docker secret create rbac_oa_jwk /etc/rbac-deploy/oa-public.json
docker secret create rbac_tls_cert /etc/rbac-deploy/fullchain.pem
docker secret create rbac_tls_key /etc/rbac-deploy/privkey.pem
```

Secret 已存在时先核对部署记录，不删除正在使用的 Secret。后续轮换应创建新版本名称并更新引用，同时完成对应数据库密码/密钥轮换。

复制 `swarm.env.example` 到 `/etc/rbac-deploy/swarm.env`，填写镜像、release、admin kid 和幂等 kid；初次保持两个副本数为 0。在管理节点加载变量并登录 SWR。该文件只放非敏感配置；以下使用 shell 读取本机可信文件，不读取任何 Secret：

```sh
set -a
. /etc/rbac-deploy/swarm.env
set +a
cd /opt/rbac-deploy/docker
docker stack config -c stack.yml > /etc/rbac-deploy/rendered-stack.yml
docker stack deploy --with-registry-auth -c stack.yml rbac
docker stack services rbac
docker service ps rbac_mysql --no-trunc
docker service logs --tail 100 rbac_mysql
```

默认只运行 MySQL。确认数据库任务稳定、容器健康，并核对 `rbac_admin_server`、两类账号及授权；`mysqladmin ping` 仅证明服务响应，不能替代账号/结构检查。可在新节点进入对应 MySQL 容器使用 `mysql -uroot -p` 交互检查，不在命令中写口令。

首次空目录初始化自动创建目标库和 app/migrator 账号；不创建业务表。已有数据目录不会重跑初始化脚本，也不会重设密码。初始化中途失败要检查已有对象与数据后处理，不自动清空重来。迁移账号仅拥有目标 schema 的完整权限；运行账号只有 SELECT/INSERT/UPDATE/DELETE。MySQL 配置开启 `log_bin_trust_function_creators` 支持项目触发器，不给应用全局管理权限。

## 4. 显式迁移并检查退出结果

前后端保持 0 副本。确保管理节点已有 SWR 登录状态，再创建一次性迁移服务：

```sh
docker service create --name rbac-migrate --with-registry-auth \
  --constraint 'node.labels.rbac_app == true' --network rbac-internal \
  --restart-condition none --limit-memory 768M \
  --env JAVA_TOOL_OPTIONS='-Xms64m -Xmx384m' \
  --env RBAC_MIGRATION_URL=jdbc:mysql://mysql:3306/rbac_admin_server \
  --env RBAC_MIGRATION_USER=rbac_migrator \
  --secret source=rbac_migration_password,target=migration-password,uid=10001,gid=10001,mode=0400 \
  "$RBAC_BACKEND_IMAGE" migrate
docker service ps rbac-migrate --no-trunc
docker service logs rbac-migrate
```

服务创建成功不代表迁移成功。等待任务结束后取任务 ID，再检查状态及退出码：

```sh
docker service ps -q rbac-migrate
docker inspect <任务ID> --format '{{json .Status}}'
```

只有任务 `State=complete` 且 `ContainerStatus.ExitCode=0`、日志和 Flyway history 核对成功，才移除一次性服务并继续：

```sh
docker service rm rbac-migrate
```

非零退出停止发布，保留检查结果；不启用自动重试，不自动 clean/repair。失败任务保留时，下次也不能直接重复使用同名 service create。

## 5. 首次管理员初始化

仅目标库尚无总管理员时执行。准备仓库外的 `bootstrap-password`（12～128 字符，按项目密码规则，单行），创建临时 Secret：

```sh
docker secret create rbac_bootstrap_password /etc/rbac-deploy/bootstrap-password
docker service create --name rbac-bootstrap --with-registry-auth \
  --constraint 'node.labels.rbac_app == true' --network rbac-internal \
  --restart-condition none --limit-memory 768M \
  --env JAVA_TOOL_OPTIONS='-Xms64m -Xmx384m' \
  --env RBAC_BOOTSTRAP_URL=jdbc:mysql://mysql:3306/rbac_admin_server \
  --env RBAC_BOOTSTRAP_USER=rbac_app \
  --env RBAC_BOOTSTRAP_CONFIRM_DATABASE=rbac_admin_server \
  --env RBAC_BOOTSTRAP_ADMIN_LOGIN=initial.admin \
  --secret source=rbac_app_password,target=app-password,uid=10001,gid=10001,mode=0400 \
  --secret source=rbac_bootstrap_password,target=bootstrap-password,uid=10001,gid=10001,mode=0400 \
  "$RBAC_BACKEND_IMAGE" bootstrap
docker service ps rbac-bootstrap --no-trunc
docker service logs rbac-bootstrap
```

按迁移相同方式 inspect 实际任务，要求 complete、退出码 0 且输出 `BOOTSTRAP_CREATED`；核对初始化账号及审计。完成后删除一次性服务和临时 Secret，并受控移除仓库外临时口令文件：

```sh
docker service rm rbac-bootstrap
docker secret rm rbac_bootstrap_password
```

普通应用从不自动初始化管理员。已有管理员时跳过本节，重复初始化也不会重置口令。

## 6. 启动前后端及验证

在 `/etc/rbac-deploy/swarm.env` 将 `RBAC_BACKEND_REPLICAS` 改为 1，重新加载并部署。先检查后端任务健康及日志；然后将 `RBAC_FRONTEND_REPLICAS` 改为 1，重新加载并部署：

```sh
set -a
. /etc/rbac-deploy/swarm.env
set +a
docker stack deploy --with-registry-auth -c stack.yml rbac
docker stack services rbac
docker stack ps rbac --no-trunc
docker service logs --tail 100 rbac_backend
```

必须同时更新配置文件，避免下一次 Stack 发布将手工 scale 的副本数恢复为 0。按阶段重复以上命令，而不是提前同时开启两个副本数。

浏览器使用证书匹配的域名访问节点 2。默认入口为 HTTP 80 跳转 HTTPS 443，使用 host 发布模式，入口只在运行 Nginx 的节点 2 上。若 80/443 被实验服务占用，可先暂停对应服务，或接入原统一入口；如自定义 HTTPS 端口，还需同步修改 `nginx.conf` 的跳转目标，不能仅改端口映射。

验证前端 SPA 刷新、未登录拒绝、登录/首次改密、实际业务流程、文件导入和审计。`/actuator/` 不对浏览器公开；后端健康由容器内探针检查。后端只连接本项目 overlay，Nginx 覆盖转发头，容器使用 framework 代理头处理；不要给后端发布端口或随意接入不可信服务。

节点 2 初始预算：后端上限 1GiB、堆最大 512MiB，前端上限 128MiB；新节点 MySQL 上限 1GiB、buffer pool 256MiB。均为起始配置，不是实测容量结论。观察 `docker stats`、OOM、启动与导入峰值，再调整；不要在 2G 运行节点构建镜像。更新采取 stop-first，存在短暂停机，避免新旧 JVM 同时占用内存。

## 7. 更新、备份和故障处理

更新前保存上次镜像 digest、配置版本及副本数。需要迁移时先关闭前端入口/停止后端，并在变量文件保持副本数 0，备份 MySQL 和导入文件，再显式执行一次性迁移；成功后按顺序恢复。没有数据库变化时可直接更新镜像，但仍核对业务与任务健康。

MySQL、导入目录和有效密钥需要协调备份，备份副本保存到另一台机器；验证恢复到隔离环境。在线复制 MySQL 数据目录不是可靠的逻辑备份。节点故障时本地数据不会自动迁移；固定节点服务会等待恢复，不能把空目录挂到其他机器冒充恢复。

日志由 Docker json-file 驱动轮转（每容器 10MiB × 3），通过 `docker service logs` 查看。不要使用全局 prune、删除数据库目录或不加区分移除卷。回退应用前核对 schema 兼容性；`docker service rollback` 不会恢复数据库结构、文件或密钥。

## 本地验证记录（2026-10-26）

已执行：

| 检查 | 结果 |
| --- | --- |
| `python deploy/docker/test_entrypoint.py` | 先因入口不存在取得真实 RED；实现后 5 项通过，覆盖 Secret 原文读取、缺失拒绝、模式选择及初始化确认值 |
| `docker stack config -c deploy/docker/stack.yml`（填入虚拟非敏感变量） | Docker CLI 原生解析通过；核对三服务、初始应用 0 副本、MySQL/后端无发布端口 |
| `backend/mvnw.cmd --offline verify`（JDK21） | 143 项普通测试，失败/错误/跳过均 0；JAR 构建成功 |
| `frontend` 中 `npm run build` | TypeScript 类型检查与 Vite 生产构建通过 |
| 文档本地链接及 `git diff --check` | 通过 |

Git Bash 在 Windows 沙箱内受命名对象权限限制，入口测试在沙箱外运行通过。首次后端普通检查中 3 项文件持久化测试报不可用；沙箱外重跑全部通过，没有为绕过限制修改业务代码。构建原始日志保存在本机忽略目录 `.tmp/docker-backend-verify.log`。

上传前再次执行本地 JDK21 `--offline clean verify`：143 项普通测试全部通过，失败/错误/跳过均 0，重新构建可执行 JAR；日志位于本机忽略目录 `.tmp/node3-backend-clean-verify.log`。

当前 Windows 有 Docker CLI，但 Docker Desktop Linux 引擎未运行，因此尚未执行镜像构建、容器 Nginx 检查、真实 MySQL 初始化、Secrets 挂载或 Swarm 实际部署。本轮未重跑隔离 MySQL/HTTP 全量检查，未推送镜像；源码 Git 交付以实际分支提交及远程回查为准，这些本地检查不代表服务器验收。

参考：[Stack 发布](https://docs.docker.com/engine/swarm/stack-deploy/)、[Swarm 服务与挂载](https://docs.docker.com/engine/swarm/services/)、[MySQL 官方镜像初始化](https://hub.docker.com/_/mysql/)。
