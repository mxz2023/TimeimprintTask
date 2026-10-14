# S04 · 到期管理

> 本文是 `scenarioKey=deadline` 的永久业务规则。当前契约可供 [P09](../phases/P09/README.md) 编码。

| 项目 | 值 |
| --- | --- |
| 场景编号 | S04 |
| scenarioKey | `deadline` |
| 首次交付阶段 | P09 |
| planningPosition | P09 |
| contractStatus | READY_FOR_IMPLEMENTATION |
| implementationStatus | VERIFIED |
| 配置 schemaVersion | 1 |
| 所属模块 | `timeimprint-task-service-scenario-deadline` |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md) 的 [CAL-09](../capabilities/CAP01-calendar.md)；[notification](../capabilities/CAP03-notification.md) |
| 明确不做 | [CAL-13](../capabilities/CAP01-calendar.md)。取消和续期是本场景生命周期，不是日历 `endAt` |

## 具体事例

- 护照 2027-03-01 到期：从 30、7、1、0 天里选出若干次提醒。
- 续期后仍能看到上一次到期日，未来提醒按新到期日重算。
- 到期日当天仍未续期则标为已过期，不再继续提醒。

没有证件对象的一次性提醒继续使用 [S01](S01-reminder.md)。

## 1. 配置

每个定义一个到期对象，只有一个 calendar 绑定：`schemaVersion=2`，`type=ANCHOR_OFFSETS`，`dailyAfterAnchor=false`。`anchorDate` 等于 `dueDate`。`offsetDays` 是 `{30,7,1,0}` 的非空子集，写成负数或 0（30 天前提醒为 `-30`），1—4 个，不允许重复。

`scenarioConfig` 字段：

| 字段 | 规则 |
| --- | --- |
| subjectType | `DOCUMENT`、`CONTRACT`、`SUBSCRIPTION`、`WARRANTY` |
| dueDate | `yyyy-MM-dd` |
| offsetDays | 与绑定中的偏移一致，但是非负天数 `30/7/1/0` |
| localTime | `HH:mm:ss` |

## 2. 状态与命令

主体状态：`ACTIVE`、`EXPIRED`、`CANCELLED`。

| 场景状态 | 平台生命周期 | 含义 |
| --- | --- | --- |
| `PLANNED` | `WAITING` | 未到点 |
| `TRIGGERED` | `TERMINAL` | 提前提醒已发出 |
| `EXPIRED` | `TERMINAL` | 到期日当天的实例；同时把主体标为 `EXPIRED` |
| `CANCELLED` | `TERMINAL` | 未发生提醒被取消 |

实例命令只有 `cancel`（commandSchemaVersion 1，载荷为空对象）：主体变为 `CANCELLED`，定义退役，尚未发生的提醒取消。

续期不是单独命令。调用定义 `update`，给出新的 `dueDate` 和对应绑定。同一事务把旧到期日写入 `tt_deadline_renewal`，主体保持 `ACTIVE`，并重算未来提醒。已经终态的实例不改写。

到期日当天的信号若主体仍是 `ACTIVE`，实例进入 `EXPIRED`，主体进入 `EXPIRED`，不再生成更晚的提醒（绑定没有锚点之后的每日发生）。

## 3. 专有表

`tt_deadline_subject` 每个定义一行。`tt_deadline_renewal` 追加旧到期日与新到期日。暂停和退役沿用平台控制语义。
