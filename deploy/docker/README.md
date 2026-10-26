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

后端使用节点 3 已安装的 JDK 21 和 Maven 3.9.16 打包，Docker 仅封装已生成 JAR。前端构建另需 Node >=22.12.0。先选定实际源码版本，检查工作区并记录完整 SHA。

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

前端 Dockerfile 仍需要可访问的 Node/Nginx 基础镜像并在构建中执行 npm ci/build；其网络适配尚待确认，不能用后端 Java 镜像代替。前端构建/上传应在解决基础镜像访问后执行，成功后将 stack.yml 的 frontend.image 中 pending 改成真实标签。

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
