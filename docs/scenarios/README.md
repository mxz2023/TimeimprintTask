# 场景规划与契约索引

本索引是S01—S17规划位置、契约成熟度、实现状态和永久文档入口的唯一来源。规划文件用于防止需求丢失；只有达到READY、进入CURRENT阶段并获得实施授权的场景才能编码。

## 状态读法

- `planningPosition`：`Pxx`表示已被该阶段选择，`NEXT_REVIEW`表示下一批优先细化候选，`BACKLOG`表示长期保留。
- `contractStatus`：`OUTLINE`只保存规划，`READY_FOR_IMPLEMENTATION`表示规则足够编码，`RELEASED`表示已随阶段交付冻结。
- `implementationStatus`：`NOT_STARTED`、`IN_PROGRESS`、`VERIFIED`或`DEPRECATED`；只有阶段DELIVERY证据可支持VERIFIED。

## 全部场景

| 编号 | 场景 | 场景标识 | 场景族或目标模块 | 排期位置 | 契约状态 | 实现状态 | 主要能力 | 永久文档 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S01 | 通用提醒 | `reminder` | basic / `scenario-basic` | P01 | RELEASED | VERIFIED | calendar、notification | [S01](S01-reminder.md) |
| S02 | 周期待办 | `recurring_todo` | basic / `scenario-basic` | P01 | RELEASED | VERIFIED | calendar、notification | [S02](S02-recurring-todo.md) |
| S03 | 生日与纪念日 | 未确定 | deadline / `scenario-deadline` | NEXT_REVIEW | OUTLINE | NOT_STARTED | calendar、notification | [S03](S03-anniversary.md) |
| S04 | 到期管理 | 未确定 | deadline / `scenario-deadline` | NEXT_REVIEW | OUTLINE | NOT_STARTED | calendar、notification | [S04](S04-deadline-management.md) |
| S05 | 缴费管理 | 未确定 | deadline / `scenario-deadline` | NEXT_REVIEW | OUTLINE | NOT_STARTED | calendar、trigger、notification、integration | [S05](S05-payment-management.md) |
| S06 | 会议管理 | 未确定 | collaboration / `scenario-collaboration` | BACKLOG | OUTLINE | NOT_STARTED | calendar、collaboration、notification、integration | [S06](S06-meeting-management.md) |
| S07 | 团队共享任务 | 未确定 | collaboration / `scenario-collaboration` | BACKLOG | OUTLINE | NOT_STARTED | collaboration、notification | [S07](S07-shared-team-task.md) |
| S08 | 轮值与交接 | 未确定 | collaboration / `scenario-collaboration` | BACKLOG | OUTLINE | NOT_STARTED | calendar、collaboration、workflow、notification | [S08](S08-duty-handover.md) |
| S09 | 审批与升级 | 未确定 | workflow / `scenario-workflow` | BACKLOG | OUTLINE | NOT_STARTED | collaboration、workflow、calendar、notification | [S09](S09-approval-escalation.md) |
| S10 | 目标与打卡 | 未确定 | basic / `scenario-basic` | BACKLOG | OUTLINE | NOT_STARTED | calendar、aggregation、notification | [S10](S10-goal-checkin.md) |
| S11 | 事件跟进 | 未确定 | automation / `scenario-automation` | BACKLOG | OUTLINE | NOT_STARTED | trigger、integration、notification | [S11](S11-event-follow-up.md) |
| S12 | 条件监控 | 未确定 | automation / `scenario-automation` | BACKLOG | OUTLINE | NOT_STARTED | trigger、aggregation、integration、notification | [S12](S12-condition-monitoring.md) |
| S13 | 汇总提醒 | 未确定 | automation / `scenario-automation` | BACKLOG | OUTLINE | NOT_STARTED | aggregation、notification | [S13](S13-digest-reminder.md) |
| S14 | 保养与复查 | 未确定 | deadline / `scenario-deadline` | NEXT_REVIEW | OUTLINE | NOT_STARTED | calendar、notification；可能需要trigger | [S14](S14-maintenance-follow-up.md) |
| S15 | 资源预约 | 未确定 | collaboration / `scenario-collaboration` | BACKLOG | OUTLINE | NOT_STARTED | calendar、collaboration、integration、notification | [S15](S15-resource-reservation.md) |
| S16 | 外部日历同步 | 未确定 | automation / `scenario-automation` | BACKLOG | OUTLINE | NOT_STARTED | integration、trigger、calendar | [S16](S16-external-calendar-sync.md) |
| S17 | 智能创建入口 | 不适用；默认不注册场景 | 不新增场景模块 | BACKLOG | OUTLINE | NOT_STARTED | intelligence及目标场景能力 | [S17](S17-intelligent-creation.md) |

S01/S02 已随 [P01 DELIVERY](../phases/P01/DELIVERY.md) 验收为 `VERIFIED`（标签 `p01`）。其余场景在进入 CURRENT 阶段并获授权前不得编码；S03/S04/S05/S14 只是下一批优先评审对象，不是 P02 范围或实施许可。

READY或RELEASED场景的`Sxx-<scenario-key>.md`是其业务含义、默认配置和行为边界的唯一正式来源。OUTLINE文件是长期规划入口，明确保存已知内容与未知问题，但不是可编码契约。跨场景公共规则写入01—03，通用能力规则写入[capabilities](../capabilities/README.md)，HTTP线协议写入04，数据与运行实现规则写入05—06，可执行证据写入07。

场景规则变更时，必须先修改对应场景文档并记录兼容性判断，再同步能力、API、数据、运行、验收和当前阶段任务。已发布场景不得原地改变既有schemaVersion或历史实例含义；不兼容变化使用新schemaVersion、迁移策略或新scenarioKey。

## 场景进入实施的条件

新场景必须同时满足：

1. 在本索引拥有稳定S编号和独立规划文档；scenarioKey必须在进入READY前确定。
2. 被用户选入唯一CURRENT阶段的README。
3. contractStatus已经变为READY_FOR_IMPLEMENTATION，并列出配置schema、状态、命令、参与人、触发、Action、专有数据和异常行为。
4. 完成对INV-01—INV-09、公共API、公共表、SPI及能力依赖的影响分析。
5. 在当前阶段IMPLEMENTATION和验收中具有明确任务及用例编号。

场景发布后，其契约继续作为当前行为来源；阶段DELIVERY只证明某个版本已经实现，不替代场景契约。场景废止或被替代时保留编号，在本索引标记并链接替代项，禁止复用旧编号或scenarioKey。
