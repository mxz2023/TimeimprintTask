# S03 · 生日与纪念日

> 本文是 `scenarioKey=anniversary` 的永久业务规则。当前契约可供 [P09](../phases/P09/README.md) 编码。农历不在本期。

| 项目 | 值 |
| --- | --- |
| 场景编号 | S03 |
| scenarioKey | `anniversary` |
| 首次交付阶段 | P09 |
| planningPosition | P09 |
| contractStatus | READY_FOR_IMPLEMENTATION |
| implementationStatus | VERIFIED |
| 配置 schemaVersion | 1 |
| 所属模块 | `timeimprint-task-service-scenario-deadline` |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md) 的公历 YEARLY 与 [CAL-09](../capabilities/CAP01-calendar.md)；[notification](../capabilities/CAP03-notification.md) |
| 明确不做 | [CAL-10](../capabilities/CAP01-calendar.md) 农历、闰月、年龄以外的隐私分级 |

## 具体事例

- 妈妈的公历生日是 3 月 5 日：每年当天和前一天提醒，并写出今年多少岁。
- 结婚纪念日：写出第几周年；没有起始年时，通知里不写数字。
- 公历 2 月 29 日：平年的周年日落在 3 月 1 日，前一天仍是该日落前一天。

只响一次、又不保存「是谁、今年多少岁」时，继续使用 [S01](S01-reminder.md)。

## 1. 用途与边界

每个定义保存一个公历生日或纪念日。每年按月日前进，禁止用每 365 天代替。每个定义只有一个 `bindingKey=primary`、`providerKey=calendar`、`schemaVersion=2`、`type=YEARLY` 的绑定。`offsetDays` 固定为 `[-1, 0]`。`month`、`dayOfMonth`、`localTime`、`zoneId` 必须与 `scenarioConfig` 一致，`startDate` 必须是该月日在起始年的周年日（无起始年时由调用方给出不晚于下一次周年的日期）。

`scenarioConfig` schemaVersion 1 的字段只有：

| 字段 | 规则 |
| --- | --- |
| kind | `BIRTHDAY` 或 `ANNIVERSARY` |
| subjectName | 1—40 个字符 |
| month | 1—12 |
| dayOfMonth | 该月存在的日；2 月 29 日允许 |
| localTime | `HH:mm:ss` |
| startYear | 可省略。生日用来算周岁，纪念日用来算第几周年。有值时为 1900 至当年，且该年的周年日必须存在（2 月 29 日要求闰年） |

未知字段整体拒绝。

## 2. 实例与状态

| 场景状态 | 平台生命周期 | 含义 |
| --- | --- | --- |
| `PLANNED` | `WAITING` | 尚未到点 |
| `TRIGGERED` | `TERMINAL` | 到点并已提交通知意图 |
| `CANCELLED` | `TERMINAL` | 暂停、退役或改期取消的未发生提醒 |

没有实例业务命令。暂停、恢复、退役与 [S01](S01-reminder.md) 相同：未发生的实例取消，已触发的不改写。

## 3. 通知与修改历史

提前一天的正文以「明天是」开头；当天以「今天是」开头。有 `startYear` 时，生日写出周岁，纪念日写出第几周年；没有则不写数字。通知有效期为发生时间加 24 小时。渠道沿用已启用的站内信和飞书，不新增渠道。

改月日或 `startYear` 时，定义更新在同一事务把旧月日写入 `tt_anniversary_revision`，并按新的 YEARLY 绑定重算未来提醒。已经 `TRIGGERED` 的实例不改写。

## 4. 专有表

`tt_anniversary_subject` 每个定义一行。`tt_anniversary_revision` 记录修改前的月日。两表只由本场景的 `ScenarioDataMaterializer` 写入。
