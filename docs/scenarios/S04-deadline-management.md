# S04 · 到期管理规划

> 本文是S04的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-deadline` |
| planningPosition | NEXT_REVIEW |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md)、[notification](../capabilities/CAP03-notification.md) |

## 已规划内容

管理合同、证件、订阅和保修等有明确到期日的业务对象，支持到期前多阶段提醒、续期、改期和取消。它不同于S01普通提醒：到期对象、有效期限和续期历史属于持续业务事实。

## 编码前必须决定

- 对象类型、必填字段、到期/过期/已续期/已取消状态。
- 提前30/7/1天等时间点是否固定、可配置以及错过后的处理。
- 续期创建新对象还是更新同一对象，历史如何保留。
- 改期、暂停、恢复、退役对已生成提醒和到期状态的影响。
- 所有者、接收人、权限、专有表、scenarioKey、schema和验收。

所需CAL-09/CAL-13目前是OUTLINE。进入实施前必须先完成能力契约，不得把提前提醒逻辑直接写成S04专用调度分支。
