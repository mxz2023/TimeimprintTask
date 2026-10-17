# P10 · 实施任务

> 阶段身份与范围见 [README](README.md)。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T01 | 把 S14 写入永久契约，并建立本阶段 | DONE | 用户确认口径 | 场景、04、05、06、07 与本目录一致 |
| T02 | `maintenance` 场景、完成命令和专有表 | DONE | T01 | `P10MaintenanceMysqlIT` 覆盖提醒、完成和重复完成 |
| T03 | 全量真库回归 | DONE | T02 | [DELIVERY](DELIVERY.md)：`mysql-it` 与 `dual-process-it` 均为退出码 0 |

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | 契约与必要人工步骤已写入；用户于 2026-10-14 确认口径 |
| READY → IMPLEMENTING | 同一天用户要求完成 S14 |
| IMPLEMENTING → VERIFYING | 2026-10-14：T02—T03 完成，真库命令见 [DELIVERY](DELIVERY.md) |
| VERIFYING → RELEASED | 2026-10-14 用户确认可以发布；标签 `v20261014-P10`（已完成） |
