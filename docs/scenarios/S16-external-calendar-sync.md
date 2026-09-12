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

## 已规划内容

同步外部会议或日程的创建、变更和删除，保留外部ID、增量同步水位、循环例外和来源优先级。需要区分“同步外部事实”和“为外部事件建立本地提醒副本”。

## 编码前必须决定

- 目标日历系统、认证方式、单向/双向范围和可信来源。
- 外部ID、版本/ETag、增量游标、全量补偿和删除语义。
- 本地与外部同时修改时的冲突、回环抑制和来源优先级。
- 周期例外、时区、参会人、权限、限流和UNKNOWN处理。
- 是否需要ScenarioExtension、scenarioKey、专有数据、API和验收。

没有真实第三方协议和测试环境时不得实现假同步；同步失败不能静默改写本地业务事实。
