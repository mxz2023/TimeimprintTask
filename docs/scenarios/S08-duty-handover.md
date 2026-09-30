# S08 · 轮值与交接规划

> 本文是S08的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-collaboration` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [calendar](../capabilities/CAP01-calendar.md)、[collaboration](../capabilities/CAP04-collaboration.md)、[workflow](../capabilities/CAP05-workflow.md)、[notification](../capabilities/CAP03-notification.md) |

## 具体事例

本节帮助理解规划用途，不是可编码契约。班次来源、跨日和未完成事项如何移交，仍以「编码前必须决定」为准。

- 今夜甲值班：到点通知甲，并在交班前给出交接提醒。
- 夜班跨过零点，仍算同一班，不在零点拆成两件无关任务。
- 甲交班时还有两件巡检没做完，把这两件交给接班的乙。
- 临时换班：原定甲值班改成丙，通知和未完成事项跟着新的值班人走。
- 巡检到点没人完成，记为漏检，并按规则通知升级。
- 节假日值班表与平日不同，当班责任人按当天的排班确定。
- 乙拒收交接时，事项仍留在原责任人名下，并留下拒收记录。

固定发给某一个人、不随班次更换责任人的待办，继续使用 [S02](S02-recurring-todo.md)。

## 已规划内容

覆盖值班、巡检、白夜班交接等场景，包括排班、动态责任人、换班、跨日班次和未完成事项交接。

## 编码前必须决定

- 排班来源、班次时区、跨日边界、节假日和临时换班。
- 责任人在实例创建时还是发生时确定，人员缺失时如何处理。
- 巡检完成、漏检、交接、拒收和升级状态。
- 未完成事项复制、转移还是引用原实例，以及幂等和审计。
- scenarioKey、schema、专有数据、权限、通知和验收。

动态人员解析应复用collaboration，步骤推进只在出现稳定共性后使用workflow；不得把轮值算法写入kernel。
