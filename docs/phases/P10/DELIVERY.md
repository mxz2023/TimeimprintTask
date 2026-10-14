# P10 · 交付证据

自动证据于 2026-10-14 通过。本期无真实外部系统或界面步骤，用户已在批准口径时确认。阶段仍是 VERIFYING，发布须用户确认。

## 1. 验收项

| 编号 | 结果 | 证据 |
| --- | --- | --- |
| N01 | PASS | `P10MaintenanceMysqlIT.remindThenCompleteAdvancesFromTodayAndIgnoresRepeat`：创建后有 `tt_maintenance_subject` 与 `PLANNED` 实例 |
| N02 | PASS | 同一次测试：完成后下一次为当天加 3 天，完成历史 1 行；同一实例再次 `complete` 仍是 1 行，并出现下一轮等待实例 |
| N03 | PASS | 同一次测试：信号处理后生命周期为 `WAITING`，场景状态为 `REMINDED` |

## 2. 命令

| 次序 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 1 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 1 | BUILD FAILURE。总时间 35.767 秒，结束于 2026-10-14T12:59:52+08:00。`StructureQualityGateTest.currentTreePassesQ01ToQ06` 失败：缺少 `MaintenanceSubjectMaterializerTest`。尚未进入 Failsafe。随后补上该所有者测试 |
| 2 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。总时间 1 分 31 秒，结束于 2026-10-14T13:02:18+08:00。boot-loader Failsafe **119** run / 0 fail / 0 error / 0 skipped，含 `P10MaintenanceMysqlIT` 1 项 |
| 3 | `bash Deploy/scripts/07-run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 29 秒，结束于 2026-10-14T13:28:28+08:00。Failsafe **7** run / 0 fail / 0 error / 0 skipped。`PerfGateDualProcessIT` 630.0 秒 |

环境变量已去掉 `FEISHU_` 与 `TIMEIMPRINT_NOTIFICATION_` 前缀。超时上限 20 分钟，本次没有出现退出码 124。
