# 本项目 Swarm 操作指南

本项目使用现有集群：节点 3 构建并上传镜像，节点 2 运行前后端，新 worker 运行 MySQL。以下操作由服务器操作者执行；用户已确认 MySQL、后端达到1/1，后端健康接口返回UP，前端尚待构建与部署。总体方案见 [部署方案](../SWARM-DEPLOYMENT.md)。

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

后端使用节点 3 已安装的 JDK 21 和 Maven 3.9.16 打包，Docker 仅封装已生成 JAR。前端可直接在 Docker 的 Node 构建阶段打包，节点3宿主机无需另装 Node；选择在宿主机执行 npm 命令时才需 Node >=22.12.0。先选定实际源码版本，检查工作区并记录完整 SHA。

部署文件已按用户要求纳入 `main`，保留来源分支 `codex/swarm-deployment`。节点 3 从实际已推送成功的仓库克隆 `main`：

```sh
git clone --branch main https://gitee.com/Noah_lzh/rbac-admin.git
cd rbac-admin
```

Gitee 与 GitHub 的 main/0f2880f 已在用户完成认证后回查一致，后续提交仍分别核验。GitHub 地址为 `https://github.com/Noah-hope/RBAC-admin.git`。节点 3 已由用户截图确认 Java 21.0.2、Maven 3.9.16、amd64，且执行 `mvn clean verify` 于 2026-10-26 14:33 成功生成可执行 JAR。版本查询使用 `java --version`、`javac --version`、`mvn --version`；Wrapper 仍可选，但本次使用服务器已安装的 Maven，避免额外下载 Maven 发行包。

```sh
git rev-parse HEAD
git status --short
cd backend
mvn --batch-mode --no-transfer-progress clean verify
cd ../frontend
npm ci
npm run build
cd ..
```

发布前沿用 [后端部署指南](../README.md)的隔离 MySQL 检查及前端相关测试要求。真实 MySQL 测试需要专属临时实例和显式隔离参数，不对新部署业务库运行。上述普通测试不能替代完整发布验证。

当前组织为华北四 javaee-adgn，按 SWR 控制台生成的登录指令登录。节点 3 已成功构建并推送 rbac-backend:d8bd624，镜像 ID 为 7f274485d01b（约460MB），构建日志已确认 Java 21.0.6 和 wget 可用；以下命令用于复现封装/上传，无需因为只修改 YAML 再次构建。后续业务源码变化时直接将命令和 YAML 中的标签改成实际新版本。构建机器架构须匹配节点 2：

```sh
docker build --pull=false -f backend/Dockerfile -t rbac-backend:d8bd624 .
docker tag rbac-backend:d8bd624 swr.cn-north-4.myhuaweicloud.com/javaee-adgn/rbac-backend:d8bd624
docker push swr.cn-north-4.myhuaweicloud.com/javaee-adgn/rbac-backend:d8bd624
```

两个 Dockerfile 均以仓库根目录为构建上下文。根目录 `.dockerignore` 兼容节点 3 的旧版构建器，另外保留各自的 `Dockerfile.dockerignore`，仅允许必要产物/前端构建输入进入上下文，排除凭据和临时文件。

后端 Dockerfile 不再运行 Maven、不再访问 Docker Hub 或 apt；直接复制 `backend/target/backend-0.0.1-SNAPSHOT.jar` 和启动脚本，使用节点 3 已有的 `swr.cn-north-4.myhuaweicloud.com/oomall-javaee/openjdk:21-alpine`。构建时检查 java 和 wget 可用，健康检查使用 wget。若需换成其他可访问且含 Java21、POSIX shell、wget 的镜像，使用 `--build-arg JAVA_RUNTIME_IMAGE=实际镜像`。不添加 `--pull`，保持使用已确认的本地基础镜像。基础镜像验证后记录/固定 digest；镜像封装并不替代 Maven 测试。

JAR 是 Maven 产物，SWR 中保存的是 Docker 镜像。节点 3 只需把 JAR 封装进 Java 基础镜像并上传一次，节点 2 仅拉取运行。已有 `productdemooap` 是实验应用镜像，不等于本项目镜像。JAR 若已对应相同业务源码并构建成功，仅更新部署文件不必重新编译；业务源码改变后必须重新打包。

前端 Dockerfile 已改用 DaoCloud 的 Node22/Nginx 镜像地址，npm 依赖按 package-lock.json 从 npmmirror 获取，在构建阶段执行 npm ci/build；最终镜像只保留 Nginx 与 dist 网页文件。完整步骤见文末“节点3构建前端镜像”。本机生产构建通过，云端基础镜像拉取、Docker构建及SWR上传仍待操作者执行；成功后再将 stack.yml 的 frontend.image 中 pending 改成真实标签。

## 3. 在管理节点准备 Secrets 和检查 YAML

将本目录无凭据文件同步到管理节点受控目录，例如 `/opt/rbac-deploy/docker/`，保持文件相对位置。配置文件使用 LF。真实凭据放在仓库外的 `/etc/rbac-deploy/`，权限仅部署操作者可读。

准备三个互不相同的数据库密码文件：`root-password`、`app-password`、`migration-password`。app/migration 密码至少 24 字符，仅使用 `A-Z a-z 0-9 _ -`，以便首次初始化安全生成 SQL；可分别生成：

```sh
umask 077
openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n' > /etc/rbac-deploy/app-password
openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n' > /etc/rbac-deploy/migration-password
openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n' > /etc/rbac-deploy/root-password
```

上级目录须先由操作者创建并授予本人访问权限。不要覆盖已有部署的密码文件；更改文件或 Secret 不会自动修改数据库账号密码。

仅启动数据库时到此只需三个数据库密码 Secret；先按下文“数据库先启动”完成部署。准备后端和前端阶段，再准备以下文件：

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

直接编辑 `stack.yml`，无需创建 swarm.env、export 或 source。当前固定配置如下：

| 配置位置 | 当前值或操作 |
| --- | --- |
| backend.image | 已上传的 SWR javaee-adgn/rbac-backend:d8bd624 |
| mysql.image | 已推送的 swr.cn-north-4.myhuaweicloud.com/javaee-adgn/mysql:8.4.8；不要使用实验镜像 latest（实测为9.2.0） |
| frontend.image | 标记为 pending，尚未发布；发布后直接填真实标签再启用 |
| mysql.volumes / backend.volumes | /srv/rbac-mysql/data 与 /srv/rbac-admin/imports |
| backend.deploy.replicas / frontend.deploy.replicas | 初始均为 0；完成对应准备后分别改为 1 |
| backend.environment.RBAC_ADMIN_KEY_ID | admin-v1；必须与 admin-private.json 中真实 signing kid 一致，不一致直接修改 YAML |
| RBAC_IDEMPOTENCY_CURRENT_KEY_ID | server-v1，与 idempotency-keys.json 的键名一致 |
| frontend.ports | 节点2的80/443；冲突时调整实际端口及 Nginx HTTPS 跳转 |
| configs 名称 | rbac_mysql_config_v1 / rbac_mysql_init_v1；内容修改后直接改为 v2 等新版本名 |

YAML 的 environment 部分是应用已有的配置接口，其值已直接写在 YAML，不需要操作者另外在终端设置。Secrets 仍只通过文件读取，不能把口令或私钥写进 YAML。首次仅数据库使用 mysql.yml，只引用三个数据库 Secrets。后续使用完整 stack.yml 时，须准备它引用的全部外部 Secrets，包括0副本应用的密钥/证书。

在管理节点登录 SWR 后，第一次仅部署数据库使用 mysql.yml：

```sh
cd /opt/rbac-deploy/docker
docker stack config -c mysql.yml > /etc/rbac-deploy/rendered-mysql.yml
docker stack deploy --with-registry-auth -c mysql.yml rbac
docker stack services rbac
docker service ps rbac_mysql --no-trunc
docker service logs --tail 100 rbac_mysql
```

默认只运行 MySQL。确认数据库任务稳定、容器健康，并核对 `rbac_admin_server`、两类账号及授权；`mysqladmin ping` 仅证明服务响应，不能替代账号/结构检查。可在新节点进入对应 MySQL 容器使用 `mysql -uroot -p` 交互检查，不在命令中写口令。

首次空目录初始化自动创建目标库和 app/migrator 账号；不创建业务表。已有数据目录不会重跑初始化脚本，也不会重设密码。初始化中途失败要检查已有对象与数据后处理，不自动清空重来。迁移账号仅拥有目标 schema 的完整权限；运行账号只有 SELECT/INSERT/UPDATE/DELETE。MySQL 配置开启 `log_bin_trust_function_creators` 支持项目触发器，不给应用全局管理权限。


### 数据库先启动：当前操作

用户已在 rbac-node 从 DaoCloud 拉取官方 mysql:8.4.8 并通过 mysqld --version 确认 8.4.8/x86_64，随后确认推送到 javaee-adgn/mysql:8.4.8。新节点数据/备份目录已创建，应用运行和数据库初始化尚未验收。

mysql.yml 仅包含数据库，复用完整 stack.yml 中同名 mysql 服务、Config 名称、网络和三个数据库 Secrets；使用相同 Stack 名称 rbac，后续切换到完整 stack.yml 即可添加前后端。当前不要使用 --prune；数据库运行后也不要用这份数据库文件更新已部署的完整应用。数据库配置变更时两份 YAML 同步修改，并核对其 mysql 服务完全一致。

管理节点先确认 rbac-node 已有 rbac_mysql=true 标签、rbac-internal overlay 已创建，新节点的 /srv/rbac-mysql/data 已准备。随后更新仓库并从 deploy/docker 目录操作。密码文件创建仅执行一次，以下子 shell 禁止覆盖已有文件，48字符随机十六进制满足初始化脚本的字符及长度要求：

```sh
install -d -m 0700 /etc/rbac-deploy
(
  set -euC
  umask 077
  openssl rand -hex 24 > /etc/rbac-deploy/root-password
  openssl rand -hex 24 > /etc/rbac-deploy/app-password
  openssl rand -hex 24 > /etc/rbac-deploy/migration-password
)
docker secret create rbac_root_password /etc/rbac-deploy/root-password
docker secret create rbac_app_password /etc/rbac-deploy/app-password
docker secret create rbac_migration_password /etc/rbac-deploy/migration-password
```

若文件或 Secret 已存在先核对并复用，不覆盖、不自动删除，也不要把口令内容贴到聊天。管理节点使用 SWR 控制台提供的登录命令认证，再执行：

```sh
docker stack deploy --with-registry-auth -c mysql.yml rbac
docker service ps rbac_mysql --no-trunc
docker service logs --tail 100 rbac_mysql
```

期望服务在 rbac-node 上运行，随后核对健康、目标库及账号。运行状态不是业务表已迁移，后端和前端仍未启用。mysql.yml 不发布3306，后续迁移/应用通过 overlay 访问。

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
  swr.cn-north-4.myhuaweicloud.com/javaee-adgn/rbac-backend:d8bd624 migrate
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
  swr.cn-north-4.myhuaweicloud.com/javaee-adgn/rbac-backend:d8bd624 bootstrap
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

直接编辑 stack.yml，将 services.backend.deploy.replicas 从 0 改为 1 并部署。检查后端健康后，确认真实前端镜像和 TLS 已就绪，将 frontend.image 的 pending 换成真实已上传标签，再将 services.frontend.deploy.replicas 改为 1，重新部署：

```sh
docker stack deploy --with-registry-auth -c stack.yml rbac
docker stack services rbac
docker stack ps rbac --no-trunc
docker service logs --tail 100 rbac_backend
```

副本数以 YAML 为准，避免下一次 Stack 发布覆盖手工 scale。按阶段编辑 YAML 并重复以上部署命令。

浏览器使用证书匹配的域名访问节点 2。默认入口为 HTTP 80 跳转 HTTPS 443，使用 host 发布模式，入口只在运行 Nginx 的节点 2 上。若 80/443 被实验服务占用，可先暂停对应服务，或接入原统一入口；如自定义 HTTPS 端口，还需同步修改 `nginx.conf` 的跳转目标，不能仅改端口映射。

验证前端 SPA 刷新、未登录拒绝、登录/首次改密、实际业务流程、文件导入和审计。`/actuator/` 不对浏览器公开；后端健康由容器内探针检查。后端只连接本项目 overlay，Nginx 覆盖转发头，容器使用 framework 代理头处理；不要给后端发布端口或随意接入不可信服务。

节点 2 初始预算：后端上限 1GiB、堆最大 512MiB，前端上限 128MiB；新节点 MySQL 上限 1GiB、buffer pool 256MiB。均为起始配置，不是实测容量结论。观察 `docker stats`、OOM、启动与导入峰值，再调整；不要在 2G 运行节点构建镜像。更新采取 stop-first，存在短暂停机，避免新旧 JVM 同时占用内存。

## 7. 更新、备份和故障处理

更新前保存上次镜像 digest、配置版本及副本数。需要迁移时先关闭前端入口/停止后端，并在 YAML 保持副本数 0，备份 MySQL 和导入文件，再显式执行一次性迁移；成功后按顺序恢复。没有数据库变化时可直接更新镜像，但仍核对业务与任务健康。

MySQL、导入目录和有效密钥需要协调备份，备份副本保存到另一台机器；验证恢复到隔离环境。在线复制 MySQL 数据目录不是可靠的逻辑备份。节点故障时本地数据不会自动迁移；固定节点服务会等待恢复，不能把空目录挂到其他机器冒充恢复。

日志由 Docker json-file 驱动轮转（每容器 10MiB × 3），通过 `docker service logs` 查看。不要使用全局 prune、删除数据库目录或不加区分移除卷。回退应用前核对 schema 兼容性；`docker service rollback` 不会恢复数据库结构、文件或密钥。

## 本地验证记录（2026-10-26）

已执行：

| 检查 | 结果 |
| --- | --- |
| `python deploy/docker/test_entrypoint.py` | 先因入口不存在取得真实 RED；实现后 5 项通过，覆盖 Secret 原文读取、缺失拒绝、模式选择及初始化确认值 |
| `docker stack config -c deploy/docker/stack.yml`（无需任何部署变量） | Docker CLI 原生解析通过；核对三服务、初始应用 0 副本、MySQL/后端无发布端口 |
| `backend/mvnw.cmd --offline verify`（JDK21） | 143 项普通测试，失败/错误/跳过均 0；JAR 构建成功 |
| `frontend` 中 `npm run build` | TypeScript 类型检查与 Vite 生产构建通过 |
| 文档本地链接及 `git diff --check` | 通过 |

Git Bash 在 Windows 沙箱内受命名对象权限限制，入口测试在沙箱外运行通过。首次后端普通检查中 3 项文件持久化测试报不可用；沙箱外重跑全部通过，没有为绕过限制修改业务代码。构建原始日志保存在本机忽略目录 `.tmp/docker-backend-verify.log`。

上传前再次执行本地 JDK21 `--offline clean verify`：143 项普通测试全部通过，失败/错误/跳过均 0，重新构建可执行 JAR；日志位于本机忽略目录 `.tmp/node3-backend-clean-verify.log`。

本地 Windows 有 Docker CLI，但 Docker Desktop Linux 引擎未运行；本机未运行镜像、Nginx、MySQL 初始化、Secrets 挂载或 Swarm 实际部署，也未重跑隔离 MySQL/HTTP 全量检查。用户随后已在节点3完成后端镜像构建上传，见后文；数据库和服务运行仍待云端验证。源码 Git 交付以实际分支提交及远程回查为准，这些本地检查不代表服务器验收。

参考：[Stack 发布](https://docs.docker.com/engine/swarm/stack-deploy/)、[Swarm 服务与挂载](https://docs.docker.com/engine/swarm/services/)、[MySQL 官方镜像初始化](https://hub.docker.com/_/mysql/)。

## 节点 3 现成 JAR 的后端镜像封装

用户已在节点 3 生成 JAR，更新 main 的部署文件后从仓库根目录执行（先确认 git status 没有需要保留的源码修改）：

```sh
cd /root/rbac-admin
git pull --ff-only origin main
ls -lh backend/target/backend-0.0.1-SNAPSHOT.jar
docker run --rm --entrypoint /bin/sh swr.cn-north-4.myhuaweicloud.com/oomall-javaee/openjdk:21-alpine -c 'java -version && command -v wget'
docker build --pull=false -f backend/Dockerfile -t rbac-backend:d8bd624 .
docker images rbac-backend
```

用户已通过节点3截图确认上述封装成功、Java21/wget可用，且镜像已推送华北四 javaee-adgn 组织。这里只记录镜像构建与推送，应用启动、数据库连接、Secrets挂载和Swarm业务运行仍待实际验证。本机Docker引擎未运行，不能将服务器截图当作本机容器测试。

2026-10-26配置简化：按用户要求将镜像、目录、端口、key id、副本数及Config版本直接写入stack.yml，删除swarm.env.example。固定参数不依赖终端环境；保留应用必需的YAML environment配置和Secret入口。只修改部署配置，无需重新编译后端或重新上传已发布镜像。

本次新增 mysql.yml 已通过无需环境文件的 Docker CLI 原生解析，并与 stack.yml 的 mysql/Config/网络/Secrets 做一致性核对；仅本地配置检查，没有连接云端或代记数据库启动成功。

## 后端单独启动：管理员初始化成功之后

云端证据：MySQL 8.4.8 已在 rbac-node 达到1/1并就绪；迁移任务在 course-node2 完成，日志显示三项迁移成功且schema到v3；管理员初始化输出 BOOTSTRAP_CREATED，账号为 initial.admin，密码由用户自行指定，首次登录必须改密。以下后端操作仍待实际执行，不据此前置成功宣布服务部署/BE-34整体验收通过。

### 密钥是什么

管理员RSA私钥用于签发登录JWT；业务系统和OA的公钥用于验证它们提交的令牌。HMAC密钥用于幂等请求的摘要校验。它们与数据库密码、管理员登录密码用途不同，由应用读取，登录用户无需手动填写。

当前为独立实验部署，工具生成 admin-v1、business-v1、oa-v1 三套RSA2048密钥和 server-v1 的32字节HMAC密钥。业务/OA私钥只用于后续受控模拟调用，由部署操作者保管，不提供给普通后端；接入真实系统时，应使用真实调用方提供的公钥，生成实验密钥不代表完成外部系统接入。现有部署的密钥必须保留，不能在每次更新/重启时重新生成。

### 管理节点生成实验部署密钥

更新仓库后从 deploy/docker 目录操作。GenerateDeploymentKeys.java 是使用JDK标准库的独立Java源程序，无需Maven或额外第三方包。下例在已上传的后端镜像中运行此工具，覆盖容器入口，不启动后端；禁止网络，只挂载只读工具和独立的输出目录：

```sh
install -d -m 0700 /etc/rbac-deploy/identity
docker run --rm --network none --user 0:0 --memory 512m \
  --mount type=bind,source=/root/rbac-admin/deploy/docker,target=/tools,readonly \
  --mount type=bind,source=/etc/rbac-deploy/identity,target=/output \
  --entrypoint java \
  swr.cn-north-4.myhuaweicloud.com/javaee-adgn/rbac-backend:d8bd624 \
  -Xmx256m /tools/GenerateDeploymentKeys.java /output
```

预期输出 KEY_SETUP_CREATED。要求镜像包含JDK的源文件启动编译器模块，尚需在云端实际核对；若提示 compiler 不可用，保留输出目录现状，将原始报错用于定位，可在已有JDK21的节点3运行同一个工具后受控转移生成文件，而不是重新打业务镜像。

工具要求绝对输出路径，拒绝非空目录或符号链接，不覆盖任何已有文件，不打印密钥。Linux新目录0700、文件0600。生成的6份JWK和1份HMAC JSON应长期受控保存；文件已存在时复用并核对，禁止为了绕过拒绝而删除旧部署密钥。工具本身不接触数据库、不注册服务身份或创建管理员。

创建后端需要的4个Secrets（数据库应用密码Secret已存在）：

```sh
docker secret create rbac_admin_jwk /etc/rbac-deploy/identity/admin-private.json
docker secret create rbac_business_jwk /etc/rbac-deploy/identity/business-public.json
docker secret create rbac_oa_jwk /etc/rbac-deploy/identity/oa-public.json
docker secret create rbac_idempotency_keys /etc/rbac-deploy/identity/idempotency-keys.json
```

admin-public.json、business-private.json、oa-private.json不挂载给后端。新文件中的kid与backend.yml的admin-v1/server-v1匹配；使用已有密钥时直接修改YAML到真实kid，不重生成替代。

### 应用节点准备文件目录

在 course-node2 上执行：

```sh
install -d -o 10001 -g 10001 -m 0750 /srv/rbac-admin/imports
```

### 管理节点启动后端

backend.yml只含后端且副本数为1，无需前端镜像、TLS证书；与mysql.yml合并到现有同名rbac Stack。当前不会发布后端8080，外部网页入口待后续Nginx提供。

```sh
docker stack deploy --with-registry-auth -c mysql.yml -c backend.yml rbac
docker service ps rbac_backend --no-trunc
docker service logs --tail 100 rbac_backend
```

要求 backend.yml 与 stack.yml 的后端配置保持一致（仅初始副本数不同）。后续切换完整stack.yml时，先将其backend副本数从0改为1，避免把已启用后端缩回0。数据库配置沿用mysql.yml，三阶段均使用rbac栈，不使用prune。

后端就绪后，在 course-node2 上检查容器内健康：

```sh
docker exec "$(docker ps -q --filter label=com.docker.swarm.service.name=rbac_backend)" \
  wget -q -O - http://127.0.0.1:8080/actuator/health
```

预期status为UP。还需后续通过真实访问入口验证登录、首次改密、业务读写、导入、重启与恢复；健康UP不能替代这些验收。

### 归档并清理一次性任务

管理节点先保存迁移/初始化日志，确认任务Complete、退出码0，且初始化输出BOOTSTRAP_CREATED后再移除临时服务：

```sh
umask 077
docker service logs rbac-migrate > /etc/rbac-deploy/migration-success.log 2>&1
docker service logs rbac-bootstrap > /etc/rbac-deploy/bootstrap-success.log 2>&1
docker service rm rbac-migrate rbac-bootstrap
docker secret rm rbac_bootstrap_password
```

若Secret暂时仍被使用，等待服务移除完成后再清理。管理员首次改密前保留自己设定的登录密码；删除临时Secret不影响数据库中已保存的密码哈希。常驻MySQL和后端不移除，不删除数据目录或已有有效签名密钥。

### 本阶段工具验证

独立工具先以缺少实现取得4项真实RED，随后Windows JDK21下4项全部通过：RSA私钥CRT关系/公钥字段及一致性、三组独立密钥、HMAC标准Base64与32字节要求、已存在文件和重跑不覆盖。未在Windows声称验证Linux权限，也尚未运行镜像内源文件编译器；这些云端检查留实际执行。本次没有重建后端业务JAR或更改其源码。

## 节点3构建前端镜像

2026-10-26后续云端输出已确认：实验密钥工具在管理节点运行成功，后端和MySQL均达到1/1，course-node2容器内的健康接口返回status=UP。迁移和bootstrap一次性任务此前均已成功，用户已获移除命令，是否实际移除尚未回报。以下继续构建前端，不重复数据库初始化、后端打包或密钥生成。

### 更新代码并拉取基础镜像

在 course-node3 执行，先确认当前为main且工作区没有需要保留的源码修改。origin沿用该节点实际配置的Gitee远程：

```sh
cd /root/rbac-admin
git status --short
git branch --show-current
git pull --ff-only origin main
git rev-parse HEAD
docker pull m.daocloud.io/docker.io/library/node:22-bookworm-slim
docker pull m.daocloud.io/docker.io/library/nginx:stable-alpine
docker run --rm --network none m.daocloud.io/docker.io/library/node:22-bookworm-slim node --version
docker run --rm --network none m.daocloud.io/docker.io/library/nginx:stable-alpine nginx -v
```

Node版本应满足package.json要求的>=22.12.0。DaoCloud地址沿用此前成功获取MySQL的[官方前缀映射规则](https://github.com/DaoCloud/public-image-mirror#使用方法)，两种基础镜像在该节点的实际可达性仍以拉取输出为准；不修改Docker daemon或重启集群。Node22和nginx stable标签会更新，拉取后记录实际digest，不将它们当作永久固定内容：

```sh
docker image inspect --format '{{json .RepoDigests}}' m.daocloud.io/docker.io/library/node:22-bookworm-slim
docker image inspect --format '{{json .RepoDigests}}' m.daocloud.io/docker.io/library/nginx:stable-alpine
```

### 构建并上传

继续在仓库根目录执行，末尾的点不可省略。宿主机无需安装Node，Docker构建阶段会执行npm ci和npm run build：

```sh
docker build --pull=false -f frontend/Dockerfile -t rbac-frontend:0.1.0 .
docker images rbac-frontend
docker tag rbac-frontend:0.1.0 swr.cn-north-4.myhuaweicloud.com/javaee-adgn/rbac-frontend:0.1.0
docker push swr.cn-north-4.myhuaweicloud.com/javaee-adgn/rbac-frontend:0.1.0
```

仅在构建成功后执行tag/push。若SWR登录已过期，按控制台指令重新登录，不在文档或聊天中提供真实登录口令。0.1.0对应当前frontend/package.json版本，是首次前端镜像标签；后端仍使用其原有d8bd624标签。后续内容变更应使用新标签并保存源码完整SHA及push输出的digest。

Dockerfile默认从registry.npmmirror.com安装锁定依赖（现有锁文件也引用该源），不修改依赖版本或业务代码；Node/npm仅在构建阶段使用，最终镜像继承Nginx启动入口并包含静态网页与现有代理配置。根目录白名单.dockerignore兼容节点3旧构建器，排除本机node_modules、.env、证书、密钥和临时文件。构建上下文还可包含后端已生成JAR，但前端Dockerfile不复制它进入最终镜像。

Nginx配置仍要求运行时提供TLS证书Secrets，并通过rbac-internal访问backend:8080。构建镜像不需要把证书或私钥放入镜像。上传成功后再准备与实际访问地址匹配的HTTPS证书，更新stack.yml前端镜像与副本数，同时保持已运行后端副本数为1，随后部署到节点2；当前pending/0副本的完整Stack不应直接用于上线。

### 本轮验证边界

独立干净main工作区中，Node22.19.0/npm10.9.3执行npm ci（npmmirror、沿用package-lock）成功安装71个包，npm run build通过vue-tsc和Vite生产打包，生成dist/index.html及CSS/JS；前端源文件和锁文件未修改。业务实现沿用既有28项前端测试证据，本轮只改镜像源与构建命令，不重跑业务测试。git diff --check与文档本地链接检查通过。

本机Docker Desktop Linux引擎仍未运行，未声称Docker镜像构建、镜像内Nginx配置检查、TLS或实际前后端联调通过；这些需节点3构建及节点2后续运行验证。健康UP只证明本次后端探针成功，BE-34/35的完整验收仍开放。
