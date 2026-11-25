---
name: uml-class-sequence-diagrams
description: 在 RBAC 权限管理系统中根据当前代码生成、重画、审查 UML 类图和顺序图时使用（结构化实现与 DDD 实现都适用），尤其强调 Service 居中、类框与连线不重叠、execution specification 对齐、MetaClass lifeline、异常回复路径和静态方法调用。包图只适用文末的包图简要规则；不用于 ER 图、流程图或普通 UI 原型。
metadata:
  short-description: 生成和审查严格的 UML 类图、顺序图
---

# UML 类图与顺序图

## 开始前

1. 先读取当前实现代码和用户提供的参考图。图必须表达**现有代码**，不能按想象补类或方法。
2. 阅读项目根目录的 `AGENTS.md`。对照需求和所画用例的实际调用链，确认关系方向、事务边界和异常抛出位置。
3. 按所画实现选择代码来源，业务语义参考 `docs/01-requirements/RBAC1需求说明.md`：
   - RBAC1 面向对象（DDD）版：`rbac1-ddd/src/main/java/com/rbac/rbac1ddd/`。目标类是应用服务（`application.*.*ApplicationService`），周边是聚合根、领域服务、仓储接口及其 JPA 实现。
   - RBAC1 结构化（面向功能）版：`rbac1-structured/src/main/java/com/rbac/rbac1/`。目标类是 `service.*Service`，周边是 Controller、DTO/VO、实体（`entity`）和 Spring Data 接口（`mapper`）；业务规则写在 Service 里，实体只有字段，类图不要为实体臆造业务方法。
4. 老师检查时会对比「结构化设计与 DDD 设计不同的 API」。同一个用例的两种实现应各画一张类图和顺序图，参与者和消息按各自代码画，不要为了对齐而互相照搬。

## RBAC 项目约定

- 统一编辑 `docs/design/项目设计图.drawio`。保留已有页面，类图和顺序图按图型集中排列，页面按版本、实现类型、图型和用例命名，例如 `RBAC1-DDD-类图-角色继承配置`、`RBAC1-结构化-顺序图-角色继承配置`。新增页面后同步更新文件开头 `<mxfile>` 的 `pages` 数量。
- 遵循项目字体规范。类图标题、类名和关系标签用黑体，类框内成员与方法用仿宋。顺序图标题用黑体，参与者和消息文字用仿宋。
- Java 类名、字段名和方法名使用源码中的标识符。类图可以裁剪成员，顺序图可以聚焦子用例，范围在图内写明。通过 ID 保存的关系应按源码展示字段，Java 角色类之间不得因业务角色继承而画成泛化。
- 优先复用已有图页或生成脚本。图页可在 diagrams.net 中编辑。绘制后打开实际图页检查布局，有导出要求时再生成对应格式。

## 类图规则

### 布局

- 每张类图以目标 Service 类为**几何中心和视觉中心**。
- 端口、Repository、领域对象、Entity、Adapter 分布在 Service 上下左右，不从页面一侧堆叠。
- 任意两个类框不得重叠，边框之间保留清晰留白。
- 依赖线不得穿过任何非端点类框。
- 长类名、长方法签名不得被裁切；必要时增加画布或缩短已展示的方法列表。

### UML 表达

- 依赖：虚线 + 空心箭头。
- 实现：虚线 + 空心三角；箭头必须从实现类指向接口，空心三角落在接口一端，例如 `JpaRoleRepository → RoleRepository`。
- 普通关联：实线 + 空心箭头，并在关联端清楚标注 role name。
- 对象字段形成 association，必须标注 role name。
- Association role name 写在箭头上方；垂直或斜向连线写在箭头左侧或左上方，不得压在线或类框上。
- 方法参数、局部变量和静态方法调用形成 dependency。
- 接口类标注 `«interface»`。
- **dependency line 上不写任何文字**。依赖关系由 Service 字段、方法参数或返回类型表达。
- 不为 Repository → Entity 强行画长关联线；优先在 Repository 方法签名中写出实体参数和返回类型。
- 类框应分为名称、属性、操作三个区。成员和方法只保留与当前模块有关的内容。

### 生成后自检

- 类框两两不重叠。
- 每条依赖/实现线不穿过非端点类框。
- Service 位于中心区域，周边留白均衡。
- 不存在回折、倒退、重复拐点或不必要的 U 形线。
- 如现有脚本支持，加入 `assertNoOverlap()` 和 `assertEdgesClearOfBoxes()`；生成失败时先修布局，不关闭检查。

## 顺序图规则

### Lifeline

- 最左侧调用者为 `obj: 调用者`。
- 普通对象 lifeline box 使用单行：`objectName: ClassName`。
- 静态方法所在 lifeline box 使用 stereotype：

```text
«MetaClass»
: ClassName
```

- 类名过长时自动减小字号，不能溢出 lifeline box。
- 不要用两个独立文本框分别写 object name 和 class name。

### 消息与执行条

- 同步调用：实线 + 实心箭头。
- 回复或异常：虚线 + 空心箭头。
- 同步消息按顺序编号；回复/异常不编号。
- 返回值用 `变量 = 方法(...)` 表达时，不再重复画普通返回线。
- 调用者保留覆盖整个同步调用的 execution specification。
- 被调用方每次独立同步调用都有**自己的短 execution specification**；不得把连续多次调用合并成一根长条。
- execution specification 顶边与进入消息同高。
- 消息箭头终点落在执行条**顶边靠近消息来向的边缘**：
  - 从左向右发送：落顶边左端点。
  - 从右向左发送：落顶边右端点。
- 箭头不要落在 lifeline 中心、执行条内部或执行条侧边中部。
- execution specification 底边不得压到 `opt/alt` 边框，至少保留明显间距。
- reply/exception 必须从发送方 execution specification 发出，并落到接收方 execution specification 边缘，不能从 lifeline 直接画。

### 异常路径

- 未被 `catch` 的异常不要逐段返回。
- 从实际抛出异常的 execution specification 直接返回 caller execution specification。
- 异常抛出前，来源执行条必须仍处于激活状态；不能出现执行条已结束、箭头从 lifeline 上冒出的情况。
- 未捕获异常通常不需要额外的 Service 转发箭头。

### 控制片段

- 条件分支使用 `opt` 或 `alt`。
- 片段内只放该分支实际发生的消息或异常。
- `loop` 框必须完整框住循环体对应的全部消息，以及这些消息在被调用方产生的所有 execution specification，包括嵌套自调用和静态方法调用的 execution specification。
- 循环前的查询、校验、编号计算等 execution specification 必须留在 `loop` 框外；循环后的批量保存、结果转换和返回等 execution specification 也必须在框外。
- 每轮循环中被调用方的 execution specification 必须各自完整位于 `loop` 框内，不得只把消息箭头框进去而把执行条留在框外。
- 表示整个公共方法调用的 caller 长 execution specification 可以跨越 `loop` 框；它不属于单轮循环体。除这类外层执行条外，循环体 execution specification 不得跨越 `loop` 的上边界或下边界。
- `loop` 框应绘制在消息和 execution specification 下层，使用透明或极浅填充，不能遮挡编号、文字、箭头和执行条。
- 片段边框不得穿过、切断或遮挡属于片段内容的 execution specification。
- 插入 `opt/alt/loop` 后重新检查所有消息、执行条和框线的几何关系。

## 包图简要规则

- 页面命名为 `RBAC1-DDD-包图-后端分层` 或 `RBAC1-结构化-包图-后端分层`，包名与源码包一致。
- 用包框表示分层，依赖用虚线空心箭头，方向与代码中的 import 方向一致；DDD 版依赖方向为接口层 → 应用层 → 领域层 ← 基础设施层，结构化版为 controller → service → mapper/entity。
- 包框之间不重叠，连线不穿过非端点包框，dependency line 上不写文字。

## 推荐工作流

1. 从代码列出参与者、字段、方法、异常和调用顺序。
2. 按图型安排参与者和连线，给长签名、条件标签和执行条预留空间。
3. 写入统一图稿并检查类框重叠、连线穿框、lifeline 溢出和执行条端点。
4. 打开实际图页，检查中心位置、留白、箭头端点、执行条和控制片段。

## 参考

- 需要逐项验收时读取 [UML 验收清单](references/checklist.md)。
