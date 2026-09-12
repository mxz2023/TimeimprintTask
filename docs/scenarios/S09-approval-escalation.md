# S09 · 审批与升级规划

> 本文是S09的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`，也不表示已经决定引入工作流引擎。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-workflow` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [collaboration](../capabilities/CAP04-collaboration.md)、[workflow](../capabilities/CAP05-workflow.md)、[calendar](../capabilities/CAP01-calendar.md)、[notification](../capabilities/CAP03-notification.md) |

## 已规划内容

支持申请审批、批准、拒绝、退回和超时通知/升级，例如员工申请→主管审批→财务确认。审批状态、办理人和升级层级属于场景专有业务，工作时长可能依赖业务时间能力。

## 编码前必须决定

- 单级/多级、或签/会签、条件分支和汇合规则。
- approve、reject、return、withdraw等命令、权限及终态冲突。
- 超时按自然时间还是业务时间，升级是否改变办理人或只通知。
- 流程版本、运行中变更、撤回、补偿和专有审批表。
- 使用自有有限状态机还是工作流引擎X02。
- scenarioKey、schemaVersion、API和验收。

现有ApprovalFixture只证明扩展边界，不是S09产品实现或规则来源。
