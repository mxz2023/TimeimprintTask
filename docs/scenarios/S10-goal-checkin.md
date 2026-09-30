# S10 · 目标与打卡规划

> 本文是S10的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-basic` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md)、[aggregation](../capabilities/CAP06-aggregation.md)、[notification](../capabilities/CAP03-notification.md) |

## 具体事例

本节帮助理解规划用途，不是可编码契约。周期窗口、补打卡和达标后是否停止提醒，仍以「编码前必须决定」为准。

- 每周运动三次：周一和周三各打卡一次，周末结算这周还差一次。
- 本周学习目标是累计5小时：每次记录时长，到周末汇总是否达标。
- 漏记了昨天的运动，在规则允许的时间内补打卡，计入本周次数。
- 同一次打卡重复提交，只计一次。
- 本周已达标，按约定决定本周剩余时间是否还继续提醒。
- 本周未达标，下一周重新计数，不清掉本周已经形成的结果。
- 查看「这周完成了2/3」这种进度，依据的是周期内的合计，而不是待办条数。

「周一、周三、周五各办一件运动待办」仍是三次独立办理，使用 [S02](S02-recurring-todo.md)。

## 已规划内容

表达“每周运动三次”“学习目标”等周期目标，支持打卡、周期计数、达标/未达标、补打卡和周期结算。它不同于三个固定时间待办，完成次数属于周期聚合事实。

## 编码前必须决定

- 周期窗口、目标次数、计数单位、时区和结算时间。
- 打卡、撤销、补打卡、重复提交和迟到数据规则。
- 达标后是否停止提醒、未达标如何进入下一周期。
- 个人/团队范围、进度查询、专有数据和保留规则。
- scenarioKey、schema、状态、命令、通知和验收。

计数和窗口逻辑应进入aggregation能力或场景专有数据，不得用多个S02实例数量临时拼接成目标结果。
