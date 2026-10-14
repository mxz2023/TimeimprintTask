# S14 · 保养与复查

> 本文是 `scenarioKey=maintenance` 的永久业务规则。当前契约可供 [P10](../phases/P10/README.md) 编码。外部完成回传不在本期。

| 项目 | 值 |
| --- | --- |
| 场景编号 | S14 |
| scenarioKey | `maintenance` |
| 首次交付阶段 | P10 |
| planningPosition | P10 |
| contractStatus | READY_FOR_IMPLEMENTATION |
| implementationStatus | VERIFIED |
| 配置 schemaVersion | 1 |
| 所属模块 | `timeimprint-task-service-scenario-deadline` |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md) 的 `ANCHOR_OFFSETS`；[notification](../capabilities/CAP03-notification.md) |
| 明确不做 | [CAP02](../capabilities/CAP02-trigger.md) 外部完成、[CAL-10](../capabilities/CAP01-calendar.md) 农历、[CAL-13](../capabilities/CAP01-calendar.md) 结束条件、按自然月进位、跳过命令 |

## 具体事例

- 9 月 2 日换完滤芯，间隔 90 天：下一次是 12 月 1 日。本期把「三个月」写成调用方给出的天数，例如 90。
- 9 月 2 日完成、间隔 3 天：下一次是 9 月 5 日。
- 原计划 9 月 2 日，实际 9 月 6 日才完成：下一次从 9 月 6 日起算。
- 到期日只提醒。未完成不自动产生下一次。
- 对同一实例再执行一次 `complete`，不新增完成历史，也不再推进。

每周一固定保养、与上次完成日无关的待办，继续使用 [S02](S02-recurring-todo.md)。

## 1. 配置

每个定义一条持续保养对象，只有一个 calendar 绑定：`schemaVersion=2`，`type=ANCHOR_OFFSETS`，`dailyAfterAnchor=false`，`offsetDays=[0]`。`anchorDate` 等于 `nextDueDate`，`localTime` 与配置一致，`zoneId` 为 `Asia/Shanghai`。

`scenarioConfig` 字段只有：

| 字段 | 规则 |
| --- | --- |
| subjectName | 1—40 个字符 |
| intervalDays | 1—3650 的自然日 |
| nextDueDate | `yyyy-MM-dd`，当前这一轮的到期日 |
| localTime | `HH:mm:ss` |

未知字段整体拒绝。`update` 可以改 `subjectName` 和 `localTime`，并同步绑定时刻。`update` 不得修改 `intervalDays` 或 `nextDueDate`；这样的请求整笔回滚。下一次日期只由 `complete` 写入。

## 2. 状态与命令

主体保持 `ACTIVE`。暂停和退役沿用平台控制：暂停取消未发生实例，已记下的完成日和下一次日期保留；恢复后按当前锚点重建窗口。退役后不再产生提醒。

| 场景状态 | 平台生命周期 | 含义 |
| --- | --- | --- |
| `PLANNED` | `WAITING` | 未到点 |
| `REMINDED` | `WAITING` | 到期日提醒已提交，这一轮仍待完成 |
| `COMPLETED` | `TERMINAL` | 这一轮已完成 |
| `CANCELLED` | `TERMINAL` | 暂停或退役取消的未发生实例 |

实例命令只有 `complete`（commandSchemaVersion 1，载荷为空对象）。定义必须是 `ACTIVE`，实例必须是 `PLANNED` 或 `REMINDED`。完成日是命令时刻在北京时间的日期。下一次到期日等于完成日加 `intervalDays`。同一实例再次 `complete` 返回不变更。不提供跳过命令；未完成的轮次保持原到期日。

到期日信号只把 `PLANNED` 改为 `REMINDED`，生命周期仍是 `WAITING`，并提交一条站内信。正文为「今天该保养：」加对象名。已经终态的实例不改写。

## 3. 专有表

`tt_maintenance_subject` 每个定义一行。`tt_maintenance_completion` 按实例追加完成日、完成前到期日和下一次到期日。同一实例最多一行。预览的 `dueAt` 等于该次 `occurrenceAt`。

完成命令在同一事务更新专有表、定义上的 `scenarioConfig` 和日历绑定锚点，并按新锚点重建未来窗口。这不新增平台公共表列。
