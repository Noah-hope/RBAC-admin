# 初始化、迁移与验证说明

本轮交付SQL文本草案，**不执行**。用户明确不需要真实建库，本轮没有连接已有或临时数据库、启动服务、迁移资料或运行查询计划。

## 草案使用边界

[schema.sql](schema.sql)先建全部空表，再添加显式外键，解决目录/权限及申请/历史的循环依赖。[immutability.sql](immutability.sql)随后定义不可变/追加历史、身份稳定和申请状态转换触发器。后者含mysql客户端DELIMITER指令；未来迁移器需识别完整CREATE TRIGGER而不是按分号粗拆，不能直接把DELIMITER当服务端SQL发送。

未给出CREATE DATABASE、数据库用户/密码、连接地址、真实人员、默认管理员凭据或实际初始化授权。schema.sql仅含SET连接字符集/UTC及表/外键草案；没有DROP、TRUNCATE、禁用FK或INSERT IGNORE。不得把能被通用解析器解析当作MySQL接受全部语句或业务正确的证明。

## 后续经批准环境中的初始化顺序（本次未执行）

1. 详细设计和正式跨阶段评审通过后，固定MySQL8.4补丁、SQL mode、连接UTC及框架BOM；建立独立测试环境，核对目标实例/库和备份策略，禁止借现有生产连接测试草案。
2. 使用独立迁移身份建空schema，核对DDL返回及SHOW CREATE TABLE、CHECK、FK、生成列、索引和触发器；所有表InnoDB，严格模式，不关FK完成“导入”。MySQL DDL会隐式提交，失败后明确记录已应用语句，不能声称整体自动回滚。
3. 部署受控初始化`mutation_guard(id=1,revision=0)`。建立服务身份绑定；受控初始化总管理员（可不关联员工）、独立随机临时凭据/专用密码摘要/强制改密。全部秘密通过部署配置交付，不写入Git或草案。检查当前/唯一总管理员自锁防护与恢复通道。
4. 先导组织/岗位，再员工/任职；源映射明确，先小样本预检，之后按API1000行批次提交。初始化任职当前有效使用明确EXPLICIT_ACTIVE，不编造起始日期。不从文件缺行推断离职。
5. 登记业务系统时同事务唯一SYSTEM_ACCESS；教师清单的“全部”、空白I340、“系统管理后台”、本人限定映射仍须人工核对，不生成宽泛授权。角色/员工授权走各自OA流程，初始化组织不绕开审批授予。

## 演进规则

- 当前无已部署数据库，schema.sql不是某个已有库的ALTER迁移。未来将通过评审的草案转换为固定编号迁移，再选迁移工具；本轮不提前决定Flyway/Liquibase或创建工程。
- 先扩展再回填再约束：新增NOT NULL列需明确旧值来源；已提交规范字节不重算，历史JSON保留schemaVersion，读取按版本适配；不得批量用当前名称覆写旧审批/审计。
- 稳定UUID/code、sourceEpoch/relationVersion、长期sourceBatchId/OA防重和成功执行凭据不可因归档重置。历史移库须同时保留唯一性/查询/引用及回放依据，方案未确认前不做物理历史清理。
- 唯一性变更先输出冲突清单，禁止INSERT IGNORE、REPLACE、关闭FK或自动归并员工解决冲突；触发器变更需要独立审查，应用账号不能随意删除触发器。
- 备份恢复以同一提交点恢复员工/任职、来源周期、grant/baseline、固定申请、OA关联/最终结果、success、审计和批次防重。不能只恢复grant表到旧时刻造成旧权限/旧批准复活。恢复后先暂停写入/发送/执行，核对外部同requestId关联、已执行凭据及会话失效依据，再恢复；必要时推进会话周期强制重新登录。
- RPO/RTO、备份加密/演练、binlog/PITR、密钥轮换、文件及审计保留期是部署/运维待确认项，本轮没有已测恢复承诺。唯一总管理员受控恢复没有公开未认证API。

## 本轮可复现静态检查

文档生成只需Python标准库：

```powershell
python docs/04-database/schema_model.py
```

静态校验需PyYAML6.0.3及sqlglot27.28.1，安装到个人隔离临时环境；不改原型依赖。示例命令中的路径自行选择，不含任何数据库连接：

```powershell
python -m venv "$env:TEMP/rbac-db-static-env"
& "$env:TEMP/rbac-db-static-env/Scripts/python.exe" -m pip install PyYAML==6.0.3 sqlglot==27.28.1
& "$env:TEMP/rbac-db-static-env/Scripts/python.exe" docs/04-database/validate_design.py
python docs/04-database/validate_handoff.py --baseline 2b78430
git -c core.whitespace=cr-at-eol diff --check
```

本轮实际依赖通过临时pip --target加载，准确结果见[VALIDATION.md](VALIDATION.md)。PyMySQL也曾安装到临时目录，但用户澄清只需设计后未使用，没有连接/执行入口。生成器和校验器均不导入数据库驱动、不启动数据库、不执行SQL。

已有需求生成文件使用CRLF，格式检查显式允许cr-at-eol，未改全仓库行尾。validate_handoff需要本地仍可访问指定基线，校验227阅读副本/源摘要、现有图片字节及需求图源，不是应用或数据库测试。

## 留待真实引擎和实现阶段的验证矩阵（均未运行）

| 场景 | 必须观察的结果 |
| --- | --- |
| 空schema应用DDL/触发器、真实SHOW CREATE | 8.4接受类型/长度/命名、复合FK/生成列/CHECK组合；触发器按预期触发，无静默降级 |
| ACTIVE唯一槽与多段历史 | 同来源同角色两个ACTIVE失败；A/B来源各ACTIVE允许；多条已结束历史允许 |
| NULL/类型/归属/环 | 必填分支NULL拒绝、跨公司系统FK拒绝；跨表FUNCTION语义与无环事务拒绝；启停例外合法 |
| 双执行器/重复最终结果 | 唯一success、一份业务变更、一条实际成功事件；不同event不绕过，矛盾结果冲突 |
| 来源不存在时关闭与申请竞争 | 0基线物化和推进串行；旧分配不能插入；来源无grant时同样生效 |
| 关闭/全局停用/资格缩小与执行两种先后 | 执行先则随后失效；关闭先旧执行整份失效；不得部分提交或恢复后复活 |
| 多项兼岗/同批结束再新增/仅改名 | 按Q-01事件规则与既有兼岗例外，检查来源/关系/任命/会话及旧申请；不能只查一个employmentId |
| 批次第N项/审计写入故障 | 业务全部回滚；失败记录另事务；成功/未知提交不能被后续失败记录覆盖 |
| 预览新增目标/无变化分类/权限缩小 | 幻读依赖检测409，统计/分页同快照，不泄露隐藏ID/数量 |
| JWT撤销与并发写 | 账号/员工停用、改密、最后资格丧失后旧JWT立即拒绝；恢复不复会话；当前写按锚点先后提交 |
| JPA映射 | CHAR UUID、generated只读、@Version与业务版本分别推进、bulk更新affectedRows、集合变更父版本、回滚异常及事务边界 |
| 大批次/宽索引/真实数据规模 | 记录锁等待/undo/语句大小、EXPLAIN/统计与页扫描成本；验证后再谈性能或拆锁 |

未来测试必须针对MySQL8.4，不能仅用H2或静态SQL解析替代。对应用行为按项目TDD技能先验收行为、运行失败测试再实现，本轮设计校验不冒充正式业务TDD证据。
