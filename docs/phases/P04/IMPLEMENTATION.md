# P04 · 实施任务

本文件只定义 P04 的任务顺序。阶段目前为 IMPLEMENTING。代码按下列顺序修改，完成证据通过前不得把任务标成 PASS。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 记录 S02 收件标题口径并建立阶段包 | PASS | 用户确定催办标题为「催办：」加实例标题 | 本文与 [README](README.md) 存在；[S02 第 4 节](../../scenarios/S02-recurring-todo.md) 与 [07](../../07-ACCEPTANCE.md) 已写明标题和正文；CURRENT 唯一 |
| T01 | 到期通知写入标题和正文 | PASS | 阶段 READY 且用户授权实施 | `RecurringTodoScenarioExtension.processSignal` 生成的初次与催办载荷含标题、正文；催办标题为「催办：」加实例标题快照。`RecurringTodoScenarioExtensionTest` 3 项通过 |
| T02 | 稍后提醒保留标题和正文 | PASS | T01 PASS | `RecurringTodoSnoozeHandler` 平移出的新通知仍带原标题和正文。`RecurringTodoCommandHandlerTest` 覆盖复制已有标题，缺标题时不按快照重算 |
| T03 | 真 MySQL 收件断言 | PASS | T02 PASS | `S02RecurringTodoMysqlIT` 2 项通过：初次标题、催办标题、正文和 `purpose`；已清空标题的收件在完成后仍为空 |
| T04 | 实例命令改为先锁定义再锁实例 | PASS | T03 PASS | `InstanceCommandService.execute` 先普通读取定义编号，再锁定义，再锁实例。06 已补这一句。交错结果见 T05 |
| T05 | 多行动作按主键升序锁定并证明不会对锁 | PASS | T04 PASS | 取消未发通知按 `action_job_id` 升序逐行锁定。`A06CompleteNotificationMysqlIT` 2 项通过，完成命令与信号到期交错未出现锁等待超时 |

任务按顺序推进。T01 起才允许改生产代码。T04 不新增业务状态，只让代码回到 [06 第 2 节](../../06-SCHEDULING.md) 已有的锁序。
