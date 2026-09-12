# CAP02 · trigger · 外部、条件与依赖触发能力

> 本文保存`capabilityKey=trigger`的已规划范围。目前没有进入实施阶段，不是可编码契约。

| 项目 | 值 |
| --- | --- |
| 能力编号 | CAP02 |
| 目标模块 | `timeimprint-task-service-capability-trigger` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 主要场景 | S05、S11、S12，及S14完成后触发 |
| DELIVERY证据 | 无 |

## 已规划能力项

| 能力项 | 内容 | 关联 | 状态 |
| --- | --- | --- | --- |
| TRG-01 | 外部事件标准化、目标解析和去重 | S11 | BACKLOG / OUTLINE / NOT_STARTED |
| TRG-02 | 周期检查与条件组合 | C05、S05、S12 | BACKLOG / OUTLINE / NOT_STARTED |
| TRG-03 | 任务完成或依赖结果触发后续工作 | C07、S11、S14 | BACKLOG / OUTLINE / NOT_STARTED |

已明确：外部事实必须先持久化为Signal；来源、事件ID、乱序、重复和取消需要显式规则；状态查询失败不能当作条件成立；自动完成必须停止后续提醒且保持幂等。P01的calendar时间Signal由calendar与runtime完成，不表示本能力域已经实现。

待细化：providerKey和payload schema、外部业务键解析、轮询水位、条件去抖/冷却、乱序策略、目标不存在、取消和更正事件、依赖汇合、权限与重驱范围。没有这些决定不得实现S05、S11、S12的触发部分。

本能力应实现TriggerProvider或使用稳定Signal接入契约，不得直接改写任务状态、调用场景内部类或绕过Signal去重。若需要广播、延迟目标解析或跨任务依赖，应先扩展能力契约和验收，不得在Worker中写场景特例。
