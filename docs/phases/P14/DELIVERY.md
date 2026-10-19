# P14 · 交付证据

本文只记录已经执行的命令和结果。阶段身份见 [README](README.md)，任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

自动证据于 2026-10-19 通过。第 6 节必要人工步骤尚未执行。阶段是 VERIFYING，不是 RELEASED。未打标签。

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `06c3d1089a539aa72ad89402c6dc24fc2e7177da` |
| 分支 | `cursor/p14-time-window-491f` |
| 代码提交 | `25bd5a6` |
| JDK | OpenJDK 21.0.12.1 |
| 操作系统 | Linux |
| MySQL | `tit-mysql-t01`，镜像 `mysql:9.7.2`，库 `db_timeimprint_task` |
| 命令环境 | 当前环境没有 `FEISHU_*` 与 `TIMEIMPRINT_NOTIFICATION_*` |

## 2. 命令

| 次序 | 命令 | 退出码 | 记录 |
| --- | --- | --- | --- |
| 1 | `bash scripts/run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。结束于 2026-10-19T00:09:08Z。Failsafe 114 项，0 失败。含 `P14TimeWindowMysqlIT` 5 项 |
| 2 | `bash scripts/run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 41 秒，结束于 2026-10-19T00:22:58Z。Failsafe 7 项，0 失败 |

## 3. 验收项

| 编号 | 结果 | 说明 |
| --- | --- | --- |
| L01 | PASS | `P14TimeWindowMysqlIT.l01NullWindowMatchesSchema1`：schema 1 与 schema 3 且 `timeWindow=null` 的发生键相同 |
| L02 | PASS | `l02RejectsEachReasonWithoutWritingAndRejectsOtherScenarios`：各 `TW_*` 原因键拒绝且不写定义；S03、S04、S05、S14 传 schema 3 返回 `UNSUPPORTED_SCHEMA_VERSION` |
| L03 | PASS | 同文件 `l03l04l06FiltersWorkdayWindowAndPreviewMatches`：工作日 ALLOW 只留下周一至周五 09:30 |
| L04 | PASS | 同上：非工作日在 DENY 工作日时段时保留周六、周日 |
| L05 | PASS | `CnMainlandWorkdayCalendarTest`、`WorkdayCalendarHealthIndicatorTest`：2026 法定日与调休、缺 2027 年告警且 readiness 仍为 UP、坏文件为 DOWN |
| L06 | PASS | 同上真库用例：预览发生键与库中 7 天窗口一致；段外不写 Signal |
| L07 | PASS | `l07UpgradeNoChangeAndDowngrade`：1 升到 3 并设时段后 `scheduleGeneration` 加一；仅升到 3 且 `timeWindow=null` 为 NoChange；降回 `TW_DOWNGRADE` |
| L08 | PASS | `l08CapsSyncWritesAndDoesNotExhaustALaterHit`：同步最多 100、Planner 每轮不超过 100、下一次在 7 天外时不标耗尽。50 毫秒上限由 `CalendarWindowedPreviewTest` 覆盖 |
| L09 | PASS | 上表两条全量命令退出码均为 0 |

## 4. 人工步骤

尚未执行。完成 [README 第 6 节](README.md) 之前不得把本阶段标为 RELEASED，也不得打标签。
