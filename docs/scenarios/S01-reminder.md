# S01 · 通用提醒场景契约

> 本文是`scenarioKey=reminder`的永久业务规则来源。跨场景语义见[01公共业务契约](../01-MVP-SPEC.md)，HTTP字段见[04 API契约](../04-API.md)，事务与Worker实现见[06运行协议](../06-SCHEDULING.md)，验证证据见[07验收](../07-ACCEPTANCE.md)。

| 项目 | 值 |
| --- | --- |
| 场景编号 | S01 |
| scenarioKey | `reminder` |
| 首次交付阶段 | P01 |
| planningPosition | P01 |
| contractStatus | RELEASED |
| implementationStatus | VERIFIED |
| 配置schemaVersion | 1 |
| 所属模块 | `timeimprint-task-service-scenario-basic` |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md)、[notification](../capabilities/CAP03-notification.md) |
| DELIVERY证据 | [P01 DELIVERY](../phases/P01/DELIVERY.md)；Git 标签 `v20260915-P01` |

## 具体事例

本节帮助理解用途。事例不增加命令、状态或配置；与正文冲突时以正文为准。一期每个定义只用一条日历规则：一次性、每天、每周、每月或每 N 天。到点只发通知，不要求打勾。

- 明天下午3点取快递：只响这一次，响过就结束。
- 每天早上8点喝水。
- 每周三晚上7点倒垃圾。
- 每月1日查看银行卡余额：只通知，不记录你是否已经查看。
- 从今天起每3天浇花。
- 吃药时间只发给登记的家属；没有单独登记接收人时，才发给所有者本人。
- 同一时刻发给最多10名接收人，每人一条站内信。
- 出差期间暂停每天喝水：暂停中不补已经错过的次数，恢复后从下一时刻继续。
- 把每天8点改成9点：还没响的按新时间，已经响过的记录保持原样。

需要你完成、跳过或稍后提醒的事项使用 [S02](S02-recurring-todo.md)。带周岁、农历或续期历史的日子使用对应的后续场景规划，不写入本场景。

## 1. 用途与边界

S01用于在一个或多个日历发生点向接收人发出提醒。提醒到点即形成业务事实，不要求用户确认完成，也不提供完成、跳过、催办或稍后提醒命令。需要办理闭环的事项使用[S02](S02-recurring-todo.md)，不能在S01中临时增加完成状态。

S01使用[01](../01-MVP-SPEC.md)定义的P01共享日历规则，且每个定义只允许一个calendar触发绑定。`scenarioConfig`的schemaVersion固定为1，强类型配置为空对象；未知字段整体拒绝，不能作为未评审功能的透传入口。

## 2. 实例与状态

| 场景状态 | 平台生命周期 | 含义 | 可离开方式 |
| --- | --- | --- | --- |
| `PLANNED` | `WAITING` | occurrence已经物化，提醒尚未发生 | 时间Signal触发，或由暂停、改期、退役系统取消 |
| `TRIGGERED` | `TERMINAL` | 提醒发生时间已到，通知意图已经与迁移原子提交 | 不再迁移 |
| `CANCELLED` | `TERMINAL` | 尚未发生的提醒被系统取消 | 不再迁移 |

每个occurrence先生成一个独立的`WAITING/PLANNED`实例和计划时间Signal。时间Signal发生时，同一个TransitionPlan把实例改为`TERMINAL/TRIGGERED`并生成通知意图；界面可表述为“提醒已触发”。`CANCELLED`不表示提醒已发送，也不能用来覆盖已经TRIGGERED的历史。

实例保存定义标题、正文、参与人、配置和时间语义快照。历史实例不随定义修改而改写。

## 3. 通知与接收人

每个occurrence只有一个业务`INITIAL`通知槽位。通知能力按最终接收人和**启用投递渠道**物化独立Action Job；P01—P04只装配`IN_APP`。P05起投递渠道由运行配置选择，默认仍仅`IN_APP`；配置加入`FEISHU`后同一槽位额外产生飞书Action，规则见[CAP03 NOT-05](../capabilities/CAP03-notification.md)与[P05](../phases/P05/README.md)。该终态迁移自身产生的Action允许继续执行，更早迁移遗留的未终结Action在实例TERMINAL后取消。

所有S01通知固定`expiresAt = occurrenceAt + 24小时`。数据库时间`L >= expiresAt`且Action尚未开始成功执行时，Action转为`EXPIRED`且不得生成陈旧收件；实例仍保留TRIGGERED事实，渠道失败或过期不得反向改写场景终态。

定义至少包含一个`OWNER`。没有显式`RECIPIENT`时，最终接收人是OWNER；存在显式RECIPIENT时只向这些接收人发送，不自动追加OWNER。最终接收人按主体去重，最多10人；超限时创建或更新整体失败。P01公共业务接口只接受`USER`主体，local profile下OWNER和RECIPIENT必须是固定Actor，多身份和多接收人仅由test profile验证。

## 4. 命令与定义控制

S01不支持实例业务命令；对S01实例调用complete、skip、snooze或其他未注册命令必须返回明确的不支持错误，不能静默NoChange。启用飞书时出站卡片仅展示标题与正文，**不挂按钮**；飞书入站不得对 S01 实例执行业务命令。

定义支持创建、预览、读取、修改、暂停、恢复和退役，并遵循以下专有结果：

- 暂停或退役时，尚未发生的`WAITING/PLANNED`实例迁移为`TERMINAL/CANCELLED`；未来Signal为`IGNORED`，未开始Action为`CANCELLED`。已经TRIGGERED的实例及事实不变。
- 暂停期间不产生实例；恢复从当前时刻后的下一合法occurrence继续，不补暂停期间历史，也不重建被取消通知。
- 修改标题、正文或参与人时，只更新7天窗口内尚未发生的WAITING实例快照；TERMINAL实例不变。
- 修改日历规则时增加scheduleGeneration，取消旧批次未来Signal和WAITING实例，并按新规则重建未来7天窗口。ONCE发生前只能改到另一个未来ONCE，发生后不能改期或改成循环规则。

所有控制操作同时遵循[01第6节](../01-MVP-SPEC.md)的controlGeneration父级屏障和[06运行协议](../06-SCHEDULING.md)的锁、重验与提交规则。

## 5. 停机、重试与幂等结果

正常或故障停机采用平台固定的`ALL_MISSED`策略：每个遗漏occurrence仍生成独立的`TERMINAL/TRIGGERED`历史实例。尚在24小时有效期内的通知可按正常规则执行；已超过有效期的通知只落`EXPIRED` Action，不生成收件。

相同providerKey与signalKey只处理一次，相同occurrenceKey不得生成第二个实例，相同Action key不得生成第二份同接收人同渠道效果。技术重试只增加Action Attempt，不增加业务通知槽位。外部执行结果UNKNOWN、DEAD和受控重驱遵循平台公共协议，不能制造新的提醒业务事实。

## 6. 数据、接口与验收映射

S01不需要专有业务表，状态与快照使用公共定义、实例、参与人、Signal、Transition和Action模型；通知及收件使用notification能力表。禁止向公共表增加S01专有列，禁止在kernel中按`reminder`分支。

- HTTP创建、更新、控制和查询形状以[04 API契约](../04-API.md)的E01—E13为准。
- 时间规划、Action key、收件写入、暂停与追赶执行以[06运行协议](../06-SCHEDULING.md)为准。
- 最低业务验收为[07](../07-ACCEPTANCE.md)中的M01—M05，以及与S01相关的A01、A03、A07、A11、A12、A18、A22、A24、A26、A28、A30、A31、A36、A37、A41、A42。
- P01实现顺序和门槛以[P01 IMPLEMENTATION](../phases/P01/IMPLEMENTATION.md)的T02与T05为准。

## 7. 变更规则

调整24小时有效期、支持实例命令、改变追赶结果或改变状态含义均属于S01业务契约变更，必须先修改本文并完成API、运行、验收和当前阶段影响分析。发布后不兼容变化不得继续复用schemaVersion 1；不能仅通过代码常量或环境配置改变既有行为。
