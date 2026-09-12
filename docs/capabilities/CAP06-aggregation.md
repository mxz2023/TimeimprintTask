# CAP06 · aggregation · 聚合与统计能力

> 本文保存`capabilityKey=aggregation`的已规划范围。目前没有进入实施阶段，不是可编码契约。

| 项目 | 值 |
| --- | --- |
| 能力编号 | CAP06 |
| 目标模块 | `timeimprint-task-service-capability-aggregation` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 主要场景 | S10、S12、S13 |
| DELIVERY证据 | 无 |

## 已规划能力项

| 能力项 | 内容 | 关联 | 状态 |
| --- | --- | --- | --- |
| AGG-01 | 周期目标计数、达标和周期结算 | S10 | BACKLOG / OUTLINE / NOT_STARTED |
| AGG-02 | 连续条件、窗口统计和恢复判断 | S12 | BACKLOG / OUTLINE / NOT_STARTED |
| AGG-03 | 多任务汇总、逾期统计和摘要 | S13 | BACKLOG / OUTLINE / NOT_STARTED |

已明确：每周运动三次不同于三个固定日程；连续三天无更新需要窗口状态；每日未完成摘要不能与单条提醒无规则地重复发送。

待细化：窗口边界和时区、补打卡、撤销、迟到数据、重算、统计一致性、成员范围、摘要去重、快照与实时查询、专有数据表和保留周期。

本能力不得直接把统计结果写成任意场景终态。聚合结果需要驱动业务时，通过持久化Signal交给场景决策；只读统计与产生业务事实必须明确分开。
