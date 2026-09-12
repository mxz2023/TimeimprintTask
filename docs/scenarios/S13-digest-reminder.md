# S13 · 汇总提醒规划

> 本文是S13的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-automation` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [aggregation](../capabilities/CAP06-aggregation.md)、[notification](../capabilities/CAP03-notification.md) |

## 已规划内容

支持每日未完成摘要、团队逾期汇总等场景，按聚合窗口生成一条或有限条通知。必须定义汇总和单条提醒同时存在时的冲突、去重与降级规则。

## 编码前必须决定

- 聚合范围、窗口时区、截止水位、迟到数据和重算规则。
- 按用户、团队、场景或严重度分组及接收人权限。
- 空摘要、内容上限、分页/截断和敏感信息处理。
- 与单条提醒的抑制、合并、失败和补发策略。
- scenarioKey、schema、状态、专有数据和验收。

汇总不是Action Worker临时拼接日志；必须有确定性聚合输入、稳定业务键和可审计结果。
