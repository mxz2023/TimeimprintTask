# S05 · 缴费管理

> 本文是 `scenarioKey=payment` 的永久业务规则。当前契约可供 [P09](../phases/P09/README.md) 编码。没有外部支付协议，不启用 trigger 或 integration。

| 项目 | 值 |
| --- | --- |
| 场景编号 | S05 |
| scenarioKey | `payment` |
| 首次交付阶段 | P09 |
| planningPosition | P09 |
| contractStatus | READY_FOR_IMPLEMENTATION |
| implementationStatus | VERIFIED |
| 配置 schemaVersion | 1 |
| 所属模块 | `timeimprint-task-service-scenario-deadline` |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md) 的 [CAL-09](../capabilities/CAP01-calendar.md) 与已有 DAILY 算法；[notification](../capabilities/CAP03-notification.md) |
| 明确不做 | 真实支付渠道、回调、支付结果未知、部分冲正以外的外部对账 |

## 具体事例

- 房租在应付日前按所选天数提醒；过了应付日仍未付清则每天催办。
- 只付了一部分：保留未付余额，不当作结清。
- 同一 `requestId` 的确认只入账一次。
- 冲正后已付金额归零，账期回到待处理，并恢复之后的催办。

没有金额和支付状态的周期提醒继续使用 [S01](S01-reminder.md) 或 [S02](S02-recurring-todo.md)。

## 1. 配置与日历

`scenarioConfig` 字段：

| 字段 | 规则 |
| --- | --- |
| amountFen | 正整数，人民币分，最大 100000000000 |
| periodLabel | 1—40 个字符 |
| dueDate | `yyyy-MM-dd` |
| offsetDays | `{30,7,1,0}` 的非空子集 |
| localTime | `HH:mm:ss` |

创建时已付金额为 0。金额字段不得写入平台公共表。

唯一绑定为 calendar `schemaVersion=2`、`type=ANCHOR_OFFSETS`。`anchorDate` 等于 `dueDate`。`offsetDays` 为配置中的非负天数取负（0 保持 0）。`dailyAfterAnchor=true`：锚点日之后每个自然日继续发生，直到付清或取消。这是日历规则，不是场景私有调度器。

## 2. 状态与命令

主体状态：`UNPAID`、`PARTIAL`、`PAID`、`CANCELLED`、`OVERDUE`。冲正记入 `tt_payment_event.event_type=REVERSE`，账期回到 `UNPAID`；若当前时间已晚于应付日的当地时刻，则回到 `OVERDUE`。不长期停留在名为 REVERSED 的状态。本期没有「支付结果未知」。

实例命令（commandSchemaVersion 1）：

| 命令 | 载荷 | 结果 |
| --- | --- | --- |
| confirmPayment | `{ "amountFen": <正整数> }` | 累加已付。超过未付余额则整笔拒绝。达到应付金额则主体 `PAID`，定义退役，取消尚未发生的提醒。未付清则为 `PARTIAL`，催办继续 |
| reverse | `{}` | 已付归零并写冲正事件。若定义已退役，则恢复为 `ACTIVE` 并重建未来窗口 |
| cancel | `{}` | 主体 `CANCELLED`，定义退役，取消尚未发生的提醒 |

同一命令 `requestId` 由平台幂等保证只作用一次。

应付日之前的发生只发提醒。应付日及之后的发生发催办，并把仍未结清的主体标为 `OVERDUE`。`PAID` 与 `CANCELLED` 不再产生新的催办。

## 3. 通知

标题用定义标题。未付清时正文包含账期、应付金额和已付金额（元，两位小数）。有效期为发生时间加 24 小时。

## 4. 专有表

`tt_payment_subject` 每个定义一行，保存应付、已付和状态。`tt_payment_event` 记录确认、冲正和取消。预览的 `dueAt` 等于该次 `occurrenceAt`。
