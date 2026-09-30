# S06 · 会议管理规划

> 本文是S06的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-collaboration` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md)、[collaboration](../capabilities/CAP04-collaboration.md)、[notification](../capabilities/CAP03-notification.md)、[integration](../capabilities/CAP07-integration.md) |

## 具体事例

本节帮助理解规划用途，不是可编码契约。本平台是会议本身的记录来源，还是只同步外部会议，仍以「编码前必须决定」为准。

- 明天10:00项目评审：参会人在09:50收到会前提醒。
- 会议改到11:00后，会前提醒改到10:50，原先09:50的提醒取消。
- 会议取消后，尚未发出的会前提醒一并取消。
- 组织者、参会人分别记录；参会人可以接受或拒绝，接受不等于因此获得会议的修改权。
- 一场会议有多名参会人，改期时按同一规则重新计算各自的提醒。
- 连续每周的例会中，只取消其中一次，其余周次仍按原计划。

自己记「10点开会」并且不保存参会人和改期历史时，可以直接使用 [S01](S01-reminder.md)。

## 已规划内容

支持多人会议、参会人、改期、取消和会前提醒。例如会议10:00开始、提前10分钟应在09:50通知；改到11:00后重新计算为10:50，并按规则取消旧提醒。简单个人会前提醒可以直接使用S01，不必建立完整会议对象。

## 编码前必须决定

- 本平台是会议事实来源还是外部会议的提醒副本。
- 组织者、参会人、可见性、接受/拒绝及批量变更规则。
- 开始/结束/取消状态、改期冲突、时区和单次例外。
- 外部会议ID、同步方向、资源预约关系、通知回执和权限。
- scenarioKey、schema、专有数据、命令及验收。

不得为了会前提醒提前实现完整会议系统，也不得将参会人直接等同通知接收人。
