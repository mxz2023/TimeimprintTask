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

## 已规划内容

表达“每周运动三次”“学习目标”等周期目标，支持打卡、周期计数、达标/未达标、补打卡和周期结算。它不同于三个固定时间待办，完成次数属于周期聚合事实。

## 编码前必须决定

- 周期窗口、目标次数、计数单位、时区和结算时间。
- 打卡、撤销、补打卡、重复提交和迟到数据规则。
- 达标后是否停止提醒、未达标如何进入下一周期。
- 个人/团队范围、进度查询、专有数据和保留规则。
- scenarioKey、schema、状态、命令、通知和验收。

计数和窗口逻辑应进入aggregation能力或场景专有数据，不得用多个S02实例数量临时拼接成目标结果。
