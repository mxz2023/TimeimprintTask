# P12 · 实施任务

> 阶段身份与范围见 [README](README.md)。当前总体状态是 RELEASED（已发布）。baselineGitRef=`8240ee977d08c57a9e544d9398e693912173e0e9`。本文件此后不再改写。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T01 | 把验收库名改成 `db_timeimprint_task` 与 `db_timeimprint_task_perf`，并把验收脚本放进本仓库 | PASS | 2026-10-17 用户授权实施 | [K01](README.md)（库名）在源码和 [07](../../07-ACCEPTANCE.md) 中一致 |
| T02 | 用本仓库脚本跑全量真库回归 | PASS | T01 | [DELIVERY](DELIVERY.md)：`mysql-it` 与 `dual-process-it` 退出码 0 |

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | 2026-10-17 用户确认 README 第 5 节「本期无此类步骤」 |
| READY → IMPLEMENTING | 2026-10-17 用户授权实施。baselineGitRef=`8240ee977d08c57a9e544d9398e693912173e0e9` |
| IMPLEMENTING → VERIFYING | 2026-10-17：T01、T02 完成，真库命令见 [DELIVERY](DELIVERY.md) |
| VERIFYING → RELEASED | 2026-10-18 用户确认可以发布。标签 `v20261018-P12` |
