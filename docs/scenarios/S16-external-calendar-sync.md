# S16 · 外部日历同步规划

> 本文是S16的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-automation` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [integration](../capabilities/CAP07-integration.md)、[trigger](../capabilities/CAP02-trigger.md)、[calendar](../capabilities/CAP01-calendar.md) |

## 具体事例

本节帮助理解规划用途，不是可编码契约。同步哪一个日历、单向还是双向、两边同时修改时以谁为准，仍以「编码前必须决定」为准。没有真实外部协议时不实现同步。

- 外部日历新建一场会议，本平台按外部编号出现同一场。
- 外部把会议从10:00改到11:00，本平台更新这一场，保留原来的外部编号。
- 外部删除这场会议，本平台按删除语义处理，不留下看起来仍有效的本地副本。
- 每周例会中只例外取消下周三这一次，其余周次继续同步。
- 增量同步记住上次同步到哪里；中断后从水位继续，缺漏的部分用全量补偿补齐。
- 本地和外部同时改了同一场：按事先约定的来源优先级留下一个结果，并避免改动在两边来回复制。
- 同步暂时失败时，保持本地已确认的事实，不把失败写成会议已被取消或已被改期。

本平台自己创建并保管的会议，使用 [S06](S06-meeting-management.md) 的规划；只为自己记一个时间点，使用 [S01](S01-reminder.md)。

## 已规划内容

同步外部会议或日程的创建、变更和删除，保留外部ID、增量同步水位、循环例外和来源优先级。需要区分“同步外部事实”和“为外部事件建立本地提醒副本”。

## 编码前必须决定

- 目标日历系统、认证方式、单向/双向范围和可信来源。
- 外部ID、版本/ETag、增量游标、全量补偿和删除语义。
- 本地与外部同时修改时的冲突、回环抑制和来源优先级。
- 周期例外、时区、参会人、权限、限流和UNKNOWN处理。
- 是否需要ScenarioExtension、scenarioKey、专有数据、API和验收。

没有真实第三方协议和测试环境时不得实现假同步；同步失败不能静默改写本地业务事实。
