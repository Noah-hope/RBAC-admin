# 持久化、版本与事务适配

## 实体和读取边界

物理结构以[数据库字典](../04-database/DATA-DICTIONARY.md)、[schema_model.py](../04-database/schema_model.py)及DDL为准，不重建另一套表。M01拥有账号/会话，M02组织/员工/任职/源映射，M03范围/任命，M04目录，M05角色/配置历史，M06来源/baseline/grant/任职证据，M07申请/目标/claim，M08关联/回调/最终结果/outbox，M09执行凭据/尝试，M10导入元数据/计划/报告；M00管理guard/HTTP幂等/审计追加，M11管理预览及只读投影。数据库拥有权不是允许绕过应用编排的写权限。

API DTO、固定审批快照、JPA实体和查询DTO分别建模。单向关联/显式ID优先，避免双向实体图、跨模块级联persist/remove、EAGER整树加载或控制器直接序列化实体。复合外键和生成槽以迁移定义，Hibernate只validate；`ddl-auto=update/create`不用于部署。JSON列保存结构快照而非JPA实体引用，固定规范字节用byte[]完整保存和校验。

M11通过同一DataSource/JPA事务绑定连接执行参数化SQL投影，返回不可变record；资格/范围/有效权限/审计各有查询文件和维护人，不集中为巨型共享类。原生查询的全路径仍由固定SQL构造，q/filter不能作为SQL片段。批量获取祖先/权限/角色/来源并在内存检测，不为每个grant单独递归懒加载；性能只作设计推演，后续EXPLAIN/真实数据验证。

## 标识与版本

- 内部UUID用Java UUID及显式CHAR/JDBC字符映射，36位ASCII原样进出；复合键用值对象/EmbeddedId，equals/hashCode只依据稳定键，不用可变名称或集合。数据库VARCHAR业务code不自动转大小写；源用户名、sourceSystem/sourceKey和内部ID分开。
- 应用预分配UUID时明确Persistable.isNew或显式persist策略，避免Spring Data save按已有ID误判成merge；用真实MySQL/JPA测试插入和并发更新。生成的ACTIVE/进入权限槽`insertable=false,updatable=false`，不从请求赋值。
- `row_version`仅存储级@Version字段；API metadata/configuration/draft/submitted/status/scope/appointment/source/relation等版本显式读取和推进，普通API version也不能统一映射成一个全局版本。只存在row_version的实体用它处理并发；没有该列的表不得擅自添加列，按现有业务版本/锚点/CAS控制。
- CAS更新同时包括稳定键、预期状态和对应业务版本，影响行数必须精确。乐观锁异常到外层整事务回滚；不能捕获后继续提交批量剩余项。bulk JPQL/原生更新后明确clear/refresh或全部使用同一路径，避免旧实体flush覆盖新版本。
- 每次递增先检查安全整数上界9007199254740991，不允许溢出回零。role_grant旧记录版本只反映该次关系生命周期，永久baseline继续推进，新授予新grantId；旧grant版本不随新grant重写。
- Java Instant与UTC DATETIME(6)明确转换，写入统一微秒精度；源日期未知保留NULL。submittedAt一旦固定，重建摘要使用原字符串/原规范字节，不经当前默认格式重新序列化。

## 事务、锁和错误记录

外层TransactionTemplate持有READ_COMMITTED写事务，第一条业务协调锁必须是guard；取当前资格/范围后再锁目标。数据库已定义顺序：账号/员工→范围/任命→组织→系统/目录→角色→来源四元键→baseline→grant→申请→批次，同类型按稳定键排序。一个事务内的全部Repository/JDBC操作共用EntityManager/连接；写端口只做MANDATORY操作，不新开事务。

读HTTP开启全新的REPEATABLE_READ只读事务，在第一次资格/数据读前建立一致快照；同一HTTP不拆身份与结果到不同快照，open-in-view关闭。读操作不锁guard；预览创建/清理写入预览表时依T0，纯读取预览仍做当前资格复核。

锁等待设计5秒，超时/死锁整事务回滚，HTTP503可重试、后台退避；不可把数据完整性错误吞成NO_CHANGE。外部密码运算/文件解析/OA调用不持guard。连接/事务边界必须通过两个真实连接观察提交先后验证，内存同步和H2不能证明MySQL行为。

执行/导入失败记录放在独立服务，在原TransactionTemplate抛出并完成回滚后调用；新事务重新查success/批次状态/租约栅栏，再决定是否记录失败。不要在rollback-only事务中写失败审计，不在成功竞争后覆盖失败状态。afterCommit回调仅作快速唤醒，数据库扫描保证恢复。

## 资格丧失的两种粒度

某员工/公司/来源部门从有适用任职变为没有时，推进该部门相关已物化系统来源周期，含零grant但待申请的来源。若部门角色所属部门与来源部门不同，丧失**角色部门**资格而来源部门仍有适用任职，只终结受影响角色的各来源grant/推进对应baseline并失效旧申请；不把仍合法的其他角色/整个来源一起关闭，也不更换来源部门。Q-01的END最后适用任职后ADD不抵消旧事件；ADD后END且始终有资格则按明确顺序保留，不在执行时重排。

员工全局停用与无任职不是同一状态操作；恢复员工身份不恢复任职、grant、任命和旧会话。后台账号停用不改变员工业务授权。资格读取、原范围覆盖和来源周期在不同模块中的语义必须一致。

## JPA验证清单

BE-03在隔离真实MySQL验证43表与41触发器；BE-04验证guard持锁到提交与读快照；域任务验证自己的实体/生成列/业务版本。BE-33再覆盖跨模块原子失败及重启。当前没有pom/实体/迁移程序，也没有JPA或MySQL实测结果。迁移和恢复计划见[DEPLOYMENT](DEPLOYMENT.md)。
