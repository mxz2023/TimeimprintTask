# S11 · 事件跟进规划

> 本文是S11的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-automation` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [trigger](../capabilities/CAP02-trigger.md)、[integration](../capabilities/CAP07-integration.md)、[notification](../capabilities/CAP03-notification.md) |

## 已规划内容

支持订单创建后产生处理任务、交付后产生回访任务，以及付款成功等事件自动完成任务。外部事件必须具有来源和事件ID，并处理重复、乱序、更正和取消。

## 编码前必须决定

- 外部系统、协议、事件类型、subject解析和业务关联键。
- 创建、更新、取消和完成事件的顺序及冲突策略。
- 目标不存在、重复业务键、迟到事件和重驱行为。
- 人工命令与自动事件并发时的优先级。
- scenarioKey、schema、状态、专有数据、权限和验收。

事件必须经持久化Signal进入，外部回调经integration适配；不得从Controller直接调用场景状态迁移。
