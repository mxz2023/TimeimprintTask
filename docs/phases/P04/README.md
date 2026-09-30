# P04 · S02 站内信标题

本阶段只修正 [S02](../../scenarios/S02-recurring-todo.md)（周期待办）初次提醒和催办的站内信标题与正文。2026-09-30 用户选定本范围，不取代 [09](../../09-SCENARIO-ROADMAP.md) 中仍为 NEXT_REVIEW 的 S03、S04、S05、S14。详细任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P04 |
| 排期身份 | CURRENT |
| 总体状态 | DRAFT |
| 文档基线 | 2.3 |
| 基础发布 | P03；Git 标签 `v20260917-P03` |
| 下一动作 | 用户确认本 README 范围后，才可将状态改为 READY；未授权不得编码 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |

DRAFT 只保存已确认的展示口径和任务。不创建 DELIVERY，不把任务标成实现完成。

## 2. 已确认口径

实例标题快照为「提交周报」、描述快照为「整理本周工作」时：

| 用途 | 收件标题 | 收件正文 |
| --- | --- | --- |
| `INITIAL`（初次） | 提交周报 | 整理本周工作 |
| `CHASE`（催办） | 催办：提交周报 | 整理本周工作 |

催办前缀固定为全角「催办：」，与标题快照之间没有空格。描述为空时正文为空。`purpose`（用途）仍用 `INITIAL` 或 `CHASE`。已经生成的收件不回填。稍后提醒产生的新通知沿用被平移通知上的标题和正文。`scenarioSchemaVersion`（场景配置版本）保持 1，不新增配置字段。

正式规则在 [S02 第 4 节](../../scenarios/S02-recurring-todo.md) 和 [07](../../07-ACCEPTANCE.md) 的 S02 收件说明。本表只复述，不另立一套规则。

## 3. 允许与禁止

允许改：`timeimprint-task-service-scenario-basic` 中周期待办到期组通知和稍后提醒组通知的载荷，以及对应测试。

禁止：修改 kernel（稳定内核）业务语义、公共表、HTTP 字段形状和稳定 SPI（稳定扩展接口）；改写已冻结的 P01—P03；实现 S03 及以后的场景。
