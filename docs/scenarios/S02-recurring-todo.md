# S02 · 周期待办场景契约

> 本文是`scenarioKey=recurring_todo`的永久业务规则来源。跨场景语义见[01公共业务契约](../01-MVP-SPEC.md)，HTTP字段见[04 API契约](../04-API.md)，事务与Worker实现见[06运行协议](../06-SCHEDULING.md)，验证证据见[07验收](../07-ACCEPTANCE.md)。

| 项目 | 值 |
| --- | --- |
| 场景编号 | S02 |
| scenarioKey | `recurring_todo` |
| 首次交付阶段 | P01 |
| planningPosition | P01 |
| contractStatus | RELEASED |
| implementationStatus | VERIFIED |
| 配置schemaVersion | 1 |
| 所属模块 | `timeimprint-task-service-scenario-basic` |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md)、[notification](../capabilities/CAP03-notification.md) |
| DELIVERY证据 | [P01 DELIVERY](../phases/P01/DELIVERY.md)；Git 标签 `v20260915-P01` |

## 具体事例

本节帮助理解用途。事例不增加命令、状态或配置；与正文冲突时以正文为准。一条定义是长期规则，每一次发生各自生成一份待办。上一份没结清，下一份仍按原来的日历出现。

- 明天下午交一份材料：到期后完成或跳过，规则是一次性的，没有再下一份。
- 每天晚上10点记账：昨晚没记，今晚仍再出一份，两份各自办理。
- 每个周一交周报：上周一没交不影响本周一再出一份。
- 每月15日提交报销材料。
- 从今天起每7天备份一次手机。
- 今晚的待办先向后推迟再通知；可以推迟的次数有上限，也不能推迟到通知失效之后。
- 到期后仍未办理，会按设定间隔再催几次，直到完成、跳过或通知失效。
- 暂停一周：暂停期间不再出新的待办；暂停前已经到期的那份仍可完成或跳过，但不能再往后推迟。

只需要到点通知、不需要办理的事项使用 [S01](S01-reminder.md)。从「实际完成日」再数间隔的保养，见 [S14](S14-maintenance-follow-up.md) 规划。

## 1. 用途与边界

S02用于按日历周期产生彼此独立、需要用户结清的待办实例。一个实例未完成不阻止下一个occurrence产生。P01不区分提前激活时间与截止时间：`occurrenceAt = dueAt`，时间Signal到达时实例进入待办理状态并视为已经到期。未来“提前创建、稍后截止”属于C04扩展，不得通过隐藏字段加入schemaVersion 1。

S02使用[01](../01-MVP-SPEC.md)定义的P01共享日历规则，且每个定义只允许一个calendar触发绑定。

## 2. 配置schemaVersion 1

| 字段 | 类型 | 默认值 | 合法范围与关系 |
| --- | --- | --- | --- |
| `chaseOffsetsMinutes` | 整数数组 | `[60,240,720]` | 长度0—3；严格递增、互不重复；每项不少于1且小于`notificationExpireAfterMinutes` |
| `notificationExpireAfterMinutes` | 整数 | `1440` | 60—10080 |
| `maxSnoozeCount` | 整数 | `3` | 0—3 |

请求省略字段时使用表中默认值；显式`null`、错误类型、未知字段或违反字段关系时整体拒绝。配置必须先按schemaVersion解析为强类型对象后再参与业务计算。发布后不得改变schemaVersion 1的默认值或含义。

## 3. 实例与状态

| 场景状态 | 平台生命周期 | 含义 | 可离开方式 |
| --- | --- | --- | --- |
| `PLANNED` | `WAITING` | occurrence已经物化，尚未到期 | 时间Signal激活，或由暂停、改期、退役系统取消 |
| `PENDING` | `ACTIVE` | 已到期且可以办理 | complete或skip |
| `COMPLETED` | `TERMINAL` | 用户完成本次待办 | 不再迁移 |
| `SKIPPED` | `TERMINAL` | 用户明确跳过本次待办 | 不再迁移 |
| `CANCELLED` | `TERMINAL` | 尚未发生的实例被系统取消 | 不再迁移 |

每个occurrence先生成一个独立的`WAITING/PLANNED`实例和计划时间Signal。Signal发生时，同一TransitionPlan把实例改为`ACTIVE/PENDING`并生成通知槽位。系统取消必须使用CANCELLED，不能冒充用户SKIPPED。

实例保存标题、正文、参与人、配置、dueAt和通知规则快照；后续定义变更不能改写ACTIVE或TERMINAL实例。

## 4. 通知槽位与失效

到期Signal处理时生成一个`INITIAL`业务槽位，并按`chaseOffsetsMinutes`生成0—3个`CHASE`槽位；CHASE时间是`dueAt + offset`。所有槽位共享`expiresAt = dueAt + notificationExpireAfterMinutes`。数据库时间`L >= expiresAt`时，尚未开始成功执行的Action转为EXPIRED且不再领取。

业务槽位与技术Action Attempt分离：重试不得增加槽位。通知能力按最终接收人和渠道为每个槽位物化独立Action Job；P01只实现`IN_APP`。

定义至少包含一个`OWNER`。没有显式`RECIPIENT`时，最终接收人是OWNER；存在显式RECIPIENT时只向这些接收人发送，不自动追加OWNER。最终接收人按主体去重，最多10人；超限时创建或更新整体失败。P01公共业务接口只接受`USER`主体，local profile下OWNER和RECIPIENT必须是固定Actor，多身份和多接收人仅由test profile验证。

## 5. 实例命令

实例仅支持`complete`、`skip`和`snooze`，都必须经过requestId幂等、expectedRevision并发校验、Policy和统一TransitionPlan提交。

### 5.1 complete与skip

- complete只允许`ACTIVE/PENDING`，到期后仍可执行；reason可省略，提供时最多500个Unicode码点。
- skip只允许`ACTIVE/PENDING`；reason必填，长度1—500个Unicode码点。
- complete与skip互斥。不同requestId再次请求同一目标终态返回NoChange，不增加revision，不创建Transition或Action；请求相反终态返回`STATE_CONFLICT`。
- 进入TERMINAL后，尚未开始的INITIAL/CHASE Action全部CANCELLED；已经RUNNING的执行按既有事实闭合，已经生成的收件和Attempt永久保留。

### 5.2 snooze

- snooze只允许`ACTIVE/PENDING`，且已使用次数必须小于实例快照中的`maxSnoozeCount`。
- `snoozeUntil`必须晚于本次事务统一时间T，并严格早于实例`expiresAt`。
- 一次snooze选取最早一个仍未开始且未终结的通知，将其移动到`snoozeUntil`；同一批次其余符合条件的通知按相同时间差整体平移并保持原间隔。
- 旧批次符合条件的Action转CANCELLED，新actionGeneration创建相同数量、相同用途的Action，不新增业务槽位。任何新时间达到或超过expiresAt时整次命令拒绝。
- SUCCEEDED、RUNNING、DEAD、EXPIRED或已经CANCELLED的Action不移动、不复活；没有可移动Action时返回明确冲突，不能消耗snooze次数。

## 6. 定义控制与修改

- 暂停时，`WAITING/PLANNED`实例迁移为`TERMINAL/CANCELLED`，未来Signal为IGNORED，所有尚未开始提醒为CANCELLED；暂停期间不产生新实例。
- 暂停前已经`ACTIVE/PENDING`的实例仍可complete或skip以结清，但不允许snooze产生未来安排。
- 恢复从当前时刻后的下一合法occurrence继续，不补暂停期间历史，不重建已有PENDING实例被暂停取消的INITIAL/CHASE，也不改变其剩余snooze计数。
- 修改标题、正文、参与人或S02配置时，只更新7天窗口内尚未发生的WAITING实例快照；ACTIVE和TERMINAL实例继续使用原快照。
- 修改日历规则时增加scheduleGeneration，取消旧批次未来Signal和WAITING实例，并按新规则重建未来7天窗口。ONCE发生前只能改到另一个未来ONCE，发生后不能改期或改成循环规则。
- 退役与暂停相同地取消WAITING实例和未开始工作，并永久阻止新发生；退役前已经ACTIVE/PENDING的实例仍可complete或skip，但不能snooze。

所有控制操作同时遵循[01第6节](../01-MVP-SPEC.md)的controlGeneration父级屏障和[06运行协议](../06-SCHEDULING.md)的锁、重验与提交规则。

## 7. 停机、重试与幂等结果

正常或故障停机采用平台固定的`ALL_MISSED`策略：每个遗漏occurrence仍生成一个独立实例并推进到`ACTIVE/PENDING`。已经超过expiresAt的通知Action直接落为EXPIRED，但实例仍然PENDING并可complete或skip；不能只保留最新一次或由Worker静默合并历史。

相同providerKey与signalKey只处理一次，相同occurrenceKey不得生成第二个实例，相同Action key不得生成第二份同接收人同渠道效果。技术重试只增加Action Attempt，不增加INITIAL/CHASE槽位或snooze次数。外部执行结果UNKNOWN、DEAD和受控重驱遵循平台公共协议。

## 8. 数据、接口与验收映射

S02 schemaVersion 1不需要专有业务表，状态与快照使用公共定义、实例、参与人、Signal、Transition和Action模型；通知及收件使用notification能力表。禁止向公共表增加S02专有列，禁止在kernel中按`recurring_todo`分支。

- HTTP创建、更新、控制、complete、skip、snooze和查询形状以[04 API契约](../04-API.md)的E01—E13为准。
- 锁序、Action批次、通知键、暂停和追赶执行以[06运行协议](../06-SCHEDULING.md)为准。
- 最低业务验收为[07](../07-ACCEPTANCE.md)中的M06—M10，以及与S02相关的A05、A06、A12、A16、A21—A24、A30、A31、A36、A37、A41、A42。
- P01实现顺序和门槛以[P01 IMPLEMENTATION](../phases/P01/IMPLEMENTATION.md)的T06为准。

## 9. 变更规则

调整默认催办、有效期、snooze算法、终态冲突、暂停后可用命令或追赶结果均属于S02业务契约变更，必须先修改本文并完成API、运行、验收和当前阶段影响分析。发布后不兼容变化不得继续复用schemaVersion 1；不能仅通过代码常量或环境配置改变既有行为。
