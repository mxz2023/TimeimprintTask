# P12 · 交付证据

自动证据于 2026-10-17 通过。本期没有需要亲手完成的外部步骤。阶段保持 VERIFYING（验证中），发布前不再改实现。

## 1. 验收项

| 编号 | 结果 | 说明 |
| --- | --- | --- |
| K01 | PASS | 默认 JDBC 与集成测试使用 `db_timeimprint_task`、`db_timeimprint_task_perf`。真库日志中的库是 `db_timeimprint_task` |
| K02 | PASS | 本仓库 `scripts/run-task-acceptance.sh` 先重建空库。`mysql-it` 与修正后的 `dual-process-it` 退出码均为 0，都没有超过 20 分钟 |

## 2. 命令

环境变量已去掉 `FEISHU_` 与 `TIMEIMPRINT_NOTIFICATION_` 前缀。超时上限 20 分钟，没有出现退出码 124。

| 次序 | 命令 | 退出码 | 记录 |
| --- | --- | --- | --- |
| 1 | `bash scripts/run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。总时间 1 分 25 秒，结束于 2026-10-17T22:13:30+08:00。Failsafe 106 项，0 失败 |
| 2 | `bash scripts/run-task-acceptance.sh dual-process-it` | 1 | BUILD FAILURE。总时间 11 分 31 秒，结束于 2026-10-17T22:25:32+08:00。`DualClaimMysqlIT` 因本机 `DB_JDBC_URL` 仍指向 `timeimprint_task_local` 而无法连接 |
| 3 | `bash scripts/run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 26 秒，结束于 2026-10-17T22:38:41+08:00。Failsafe 7 项，0 失败。脚本已忽略调用方导出的 `DB_JDBC_URL` |
