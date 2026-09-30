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

## 具体事例

本节帮助理解规划用途，不是可编码契约。单级还是多级、或签还是会签、超时是否改办理人，仍以「编码前必须决定」为准。本文不表示已经选择工作流引擎。

- 员工请假：先到主管批准；主管超时未处理，再通知上一级。
- 报销单：主管通过后，再到财务确认，两步都通过才结束。
- 采购申请可由两名经理中任意一人批准。
- 合同需三名会签人全部同意才能通过。
- 审批人把申请退回补充材料，申请人改完后重新进入原流程。
- 申请人在审批完成前撤回申请。
- 审批被拒绝后，这一单进入拒绝结果，不能再被当成已批准。

自己把自己的待办标成完成，没有申请、批准和退回，继续使用 [S02](S02-recurring-todo.md)。

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
