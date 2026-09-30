# P04 · 实施任务

本文件只定义 P04 的任务顺序。阶段目前为 DRAFT。未获用户授权进入 IMPLEMENTING 之前，下列任务不得开始写代码。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 记录 S02 收件标题口径并建立阶段包 | PASS | 用户确定催办标题为「催办：」加实例标题 | 本文与 [README](README.md) 存在；[S02 第 4 节](../../scenarios/S02-recurring-todo.md) 与 [07](../../07-ACCEPTANCE.md) 已写明标题和正文；CURRENT 唯一 |
| T01 | 到期通知写入标题和正文 | NOT_STARTED | 阶段 READY 且用户授权实施 | `RecurringTodoScenarioExtension.processSignal` 生成的初次与催办载荷含标题、正文；催办标题为「催办：」加实例标题快照 |
| T02 | 稍后提醒保留标题和正文 | NOT_STARTED | T01 PASS | `RecurringTodoSnoozeHandler` 平移出的新通知仍带原标题和正文 |
| T03 | 真 MySQL 收件断言 | NOT_STARTED | T02 PASS | S02 集成测试断言初次标题、催办标题、正文和 `purpose`；已有空标题收件不被回填 |
| T04 | 实例命令改为先锁定义再锁实例 | NOT_STARTED | T03 PASS | `InstanceCommandService.execute` 先普通读取定义编号，再 `findByIdForUpdate` 定义，再锁实例；版本冲突仍用锁内版本号。完成、跳过、稍后提醒共用这一处 |
| T05 | 多行动作按主键升序锁定并证明不会对锁 | NOT_STARTED | T04 PASS | 取消未发通知按 `action_job_id` 升序逐行锁定。真 MySQL 下完成命令与信号到期处理交错，不出现锁等待超时；[A06](../../07-ACCEPTANCE.md) 仍为一种先后 |

任务按顺序推进。T01 起才允许改生产代码。T04 不新增业务状态，只让代码回到 [06 第 2 节](../../06-SCHEDULING.md) 已有的锁序。
