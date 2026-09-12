# S14 · 保养与复查规划

> 本文是S14的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-deadline` |
| planningPosition | NEXT_REVIEW |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md)、[notification](../capabilities/CAP03-notification.md)；完成事件驱动时增加[trigger](../capabilities/CAP02-trigger.md) |

## 已规划内容

覆盖换滤芯完成后90天再提醒、检查完成后安排复查等业务。下一次以实际完成时间为基准，例如9月2日完成、间隔3天得到9月5日；它不同于固定9月1/4/7日循环。

可以评估在S02基础上兼容扩展，但不能破坏S02固定时间锚点和历史实例语义。

## 编码前必须决定

- 完成事件来自人工命令还是外部系统，以及重复/更正/撤销。
- 间隔按自然日、工作日还是精确时长，完成时间取哪一事实。
- 延迟完成、跳过、未完成、暂停和退役是否产生下一次。
- 一条持续定义还是完成后创建新定义/实例链，以及历史关联。
- scenarioKey、schema、状态、专有数据、通知和验收。

进入实施前必须明确是新增场景还是S02兼容版本；任何选择都要保持旧S02数据和行为不变。
