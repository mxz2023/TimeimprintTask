# S15 · 资源预约规划

> 本文是S15的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-collaboration` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md)、[collaboration](../capabilities/CAP04-collaboration.md)、[integration](../capabilities/CAP07-integration.md)、[notification](../capabilities/CAP03-notification.md) |

## 具体事例

本节帮助理解规划用途，不是可编码契约。本平台是否负责检查会议室冲突，仍以「编码前必须决定」为准。

- 预约明天下午2点到3点的第一会议室，开始前提醒预约人。
- 预约改到下午4点后，提醒改到新的开始时间之前，旧提醒取消。
- 预约取消后，这场开始前提醒也取消。
- 同一场预约通知预约人和受邀参与者，管理员可以查看但不能因此自动变成参与者。
- 两个人同时预约同一间会议室的重叠时段：若本场景负责冲突检查，后一个预约不能成功占用。
- 外部系统里的预约号同步过来后改期，本平台按外部编号更新同一场预约。
- 预约结束后资源释放，下一段预约可以再次使用同一资源。

只给自己记「2点去会议室」又不保存资源占用时，使用 [S01](S01-reminder.md)。

## 已规划内容

支持会议室等资源预约开始前提醒，并处理预约变更、取消、资源和参与人。平台是否负责资源可用性与冲突检查尚未决定。

## 编码前必须决定

- 本平台是预约事实来源、同步副本还是只负责提醒。
- 资源模型、时间区间、冲突检测、并发预占和释放规则。
- 预约者、参与人、管理员权限以及变更/取消通知。
- 外部预约ID、同步方向、来源优先级和失败结果。
- scenarioKey、schema、状态、专有数据和验收。

若只需要个人资源提醒应优先复用S01；没有明确授权时不得把平台扩展成完整资源预订系统。
