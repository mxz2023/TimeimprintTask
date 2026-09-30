# S12 · 条件监控规划

> 本文是S12的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-automation` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [trigger](../capabilities/CAP02-trigger.md)、[aggregation](../capabilities/CAP06-aggregation.md)、[integration](../capabilities/CAP07-integration.md)、[notification](../capabilities/CAP03-notification.md) |

## 具体事例

本节帮助理解规划用途，不是可编码契约。数据源、持续多久才算成立、冷却时间，仍以「编码前必须决定」为准。查数据失败不等于条件成立，也不等于已经恢复。

- 库存连续低于10件，才提醒补货；刚低于10件的一瞬间，若持续条件还没满足，先不提醒。
- 某个仓库连续三天没有更新库存记录，发出停滞提醒。
- 补货提醒发出后进入冷却期，冷却期内库存仍低，不重复刷同一条提醒。
- 库存恢复到10件以上，记下恢复；以后再次连续不足，可以重新提醒。
- 本次查询超时：保持上次已知状态，不把超时当成库存不足。
- 迟到才到的历史数据把当时的库存改了：按更正规则决定是否补发或撤回已产生的提醒。
- 操作员手工关闭这条监控后，停止后续触发，直到监控被重新打开。

每天固定时刻响一次、并不读取外部数量的提醒，使用 [S01](S01-reminder.md)。

## 已规划内容

监控库存不足、连续三天没有更新等条件，涉及数据源、阈值、持续时间、去抖、冷却期和恢复事件。数据查询失败不能当作条件成立或恢复。

## 编码前必须决定

- 数据源协议、采样/事件模式、数据时效和错误分类。
- 比较运算、持续窗口、连续性、去抖和冷却算法。
- 首次命中、持续命中、恢复、重新触发和人工关闭状态。
- 数据缺失、迟到、更正、重复和停机追赶行为。
- scenarioKey、schema、专有数据、通知、权限和验收。

条件计算由trigger/aggregation形成明确事实，再以Signal驱动场景；不得让Worker直接查询任意URL或执行用户脚本。
