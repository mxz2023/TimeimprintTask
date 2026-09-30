# S05 · 缴费管理规划

> 本文是S05的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-deadline` |
| planningPosition | NEXT_REVIEW |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md)、[trigger](../capabilities/CAP02-trigger.md)、[notification](../capabilities/CAP03-notification.md)、[integration](../capabilities/CAP07-integration.md) |

## 具体事例

本节帮助理解规划用途，不是可编码契约。账单来源、部分支付和外部支付事件如何对应，仍以「编码前必须决定」为准。

- 每月房租5000元：应付日前提醒，未付则继续催办，确认付清后停止催办。
- 水电费账单带着金额和账期，付清后记为已支付。
- 宽带月费逾期后继续提醒，直到支付或取消该账期。
- 物业费只付了一部分：保留未付余额，不把部分支付当成已结清。
- 同一笔支付通知到了两次：只结清一次。
- 支付结果暂时查不到：保持未知，不把它当成「还没付」或「已经付」。
- 一笔已经入账的费用被冲正后，账期重新变为待处理，并恢复后续催办。

没有金额和支付状态、只是「每月1日看一眼余额」时，继续使用 [S01](S01-reminder.md) 或 [S02](S02-recurring-todo.md)。

## 已规划内容

覆盖房租、水电、宽带和物业费等账期任务，保存金额、账期和支付状态；缴费成功事件可以自动结清并停止后续催办。时间与条件组合必须保证状态查询失败不能等同“尚未缴费”。

## 编码前必须决定

- 账单来源、金额/币种、账期、应付日和专有数据模型。
- 未支付、部分支付、已支付、冲正、取消和逾期状态及命令。
- 支付事件的来源ID、重复、乱序、更正和关联失败处理。
- 提前/逾期催办、失效、暂停恢复以及人工完成与事件完成冲突。
- 外部接口、权限、scenarioKey、schemaVersion和验收。

不得在没有真实支付协议时虚构集成，也不得把支付金额等字段加入公共任务表。
