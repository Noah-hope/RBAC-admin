# 技术基线与设计取值

2026-10-17检索项目文档，已确认产品为 MySQL、Spring Boot、Spring Data JPA（D-028），没有具体版本约定。下列为本轮技术设计取值及建议，不表示用户此前逐项确认，也不表示已经安装/运行该后端组合。

| 项目 | 本轮取值与理由 | 验证边界 |
| --- | --- | --- |
| 数据库 | MySQL 8.4 LTS、InnoDB；草案以8.4能力为前提。本机二进制8.4.8可供以后隔离验证 | 只运行`mysql --version`/`mysqld --version`，没有启动/连接实例；上线补丁届时复核 |
| 后端建议 | Spring Boot 4.0.8 + Java 21；采用已发布4.0补丁线，现有项目没有旧后端需要迁移 | 官方系统要求最低Java17；本项目取21。官方同时列4.1.1稳定版，未因“最新”自动升级设计 |
| JPA建议 | 采用Boot 4.0.8 BOM管理的Spring Data JPA 4.0.7、Hibernate ORM 7.2.24.Final、Connector/J 9.7.0 | 官方依赖表当日可见值；不独立拼装Hibernate/Data版本；未做应用兼容性测试 |
| 标识 | 内部主键UUID v4，`CHAR(36) CHARACTER SET ascii COLLATE ascii_bin`；API同一不透明字符串 | Java UUID/明确JDBC字符映射，避免BINARY16字节顺序误配；随机聚簇写入与更宽二级索引成本保留 |
| 编码 | `VARCHAR(100)`，utf8mb4_0900_bin，显式首尾清理；角色生成`ROLE_`+UUID无连字符，数据库全局唯一 | 名称不作身份；不引入大小写折叠或拼音推断。大小写精确匹配是设计取值，初始化映射必须显式核对 |
| 时间 | `DATETIME(6)`，数据库连接UTC，Java Instant与UTC转换，API RFC3339 UTC | 不使用数据库本地时区推断任职开始；未知源日期允许NULL，另存记录时刻 |
| 版本 | 有符号BIGINT，0～9007199254740991；所有递增在事务内检查上界 | 与API安全整数一致；版本不回退、不复用 |
| 写并发 | 一期统一`mutation_guard(id=1)`行锁串行化本地状态变更事务；读采用独立RR一致快照 | 正确性优先的可评审设计；有写吞吐上限，无实测性能承诺。拆锁属于后续设计变更，不能只删除锚点 |

版本依据：[Spring Boot 4.0系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)、[4.0管理依赖表](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html)。官方页面会更新，后续创建工程前固定BOM及依赖清单并重新核验支持期；本轮不创建pom或实体。

## MySQL能力及限制

1. MySQL8.4会执行CHECK，但CHECK不能查询其他表、包含子查询或使用NOW等非确定性表达式。NULL可产生UNKNOWN并通过，所以必填组合明确写`IS NOT NULL`；不能用CHECK保证“至少一个子记录”或整个目录无环。FK动作与CHECK涉及列的限制应在后续引擎验证，草案使用默认RESTRICT，不使用级联动作。[CHECK官方说明](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html)
2. 生成列可索引。本设计用`CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END`及复合唯一键实现“仅当前关系唯一”，利用唯一键允许多个NULL保留结束历史。没有使用PostgreSQL部分索引、RLS或延迟外键。生成列只读，不能作为业务可写状态。[生成列](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html)、[索引与NULL](https://dev.mysql.com/doc/refman/8.4/en/create-index.html)
3. 外键全部显式声明，并引用完整主键/显式唯一候选键；不依赖8.4已限制的非标准非唯一引用。复合外键含NULL时不检查完整引用，因此类型分支另用CHECK保证应非空字段，跨表语义仍需事务。[外键限制](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)
4. `SELECT ... FOR UPDATE`持锁到事务结束，必须处于事务内。写入使用READ COMMITTED、锁后当前读，不能用锁前快照做最后判断。RR读在新事务建立一致快照，不能复用之前打开的旧事务回答“撤权后新查询”。[锁定读](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)、[一致性非锁定读](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html)
5. 外键即时检查，无延迟约束；目录功能与其操作的循环引用采用停用节点→操作→绑定→最终状态，在一个事务最终校验。DDL有隐式提交，不能承诺整份建表脚本可事务回滚。[隐式提交](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html)

以上能力是官方文档核实，未在本轮真实MySQL实例测试。字符集/索引长度/触发器执行顺序等必须按交接清单做真实引擎验证。

## 技能可用性

项目`.agents/skills/`没有erd-schema-designer或drawio副本。读取已列出的全局`erd-schema-designer`及drawio1.2.0技能和其XML简明规范，采用实体→关系→约束→索引顺序。技能的PostgreSQL、租户及一律软删除建议与本项目不适用，不移植。没有修改或安装项目技能。

检查PATH、默认路径、注册表及其他磁盘Program Files后没有找到draw.io Desktop；使用原生XML，不伪称客户端导入/图片导出成功。图源没有XML注释，节点/连线可编辑；只做XML结构检查。Docker客户端存在但守护进程未运行；用户随后明确只要设计，本轮不启动任何数据库，包括临时实例。
