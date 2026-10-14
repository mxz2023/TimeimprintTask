# P09 · 实施任务

> 阶段身份与范围见 [README](README.md)。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T01 | 把 YEARLY、CAL-09 与 S03—S05 写入永久契约，并建立本阶段 | DONE | 用户批准 P09 计划 | 场景、CAP01、04、05、07 与本目录一致 |
| T02 | calendar schemaVersion 2：`YEARLY` 与 `ANCHOR_OFFSETS` | DONE | T01 | 所有者测试覆盖平年 2 月 29 日与锚点偏移 |
| T03 | `scenario-deadline` 模块实现三个场景 | DONE | T02 | `P09DeadlineMysqlIT` 覆盖创建、续期、取消、部分支付、付清和冲正 |
| T04 | 全量真库回归 | DONE | T03 | [DELIVERY](DELIVERY.md)：`mysql-it` 与 `dual-process-it` 均为退出码 0 |

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | 契约与必要人工步骤已写入；用户于 2026-10-14 批准计划 |
| READY → IMPLEMENTING | 同一天用户要求按计划实施 |
| IMPLEMENTING → VERIFYING | 2026-10-14：T02—T04 完成，真库命令见 [DELIVERY](DELIVERY.md) |
| VERIFYING → RELEASED | 2026-10-14 用户确认可以发布；标签 `v20261014-P09`（已完成） |
