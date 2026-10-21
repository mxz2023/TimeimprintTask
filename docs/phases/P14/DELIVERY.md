# P14 · 交付证据

本文只记录已经执行的命令和结果。阶段身份见 [README](README.md)，任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

自动证据于 2026-10-19 通过。老板于 2026-10-21 确认 [README 第 6 节](README.md) 人工步骤通过。阶段已 RELEASED。Git 标签 `v20261021-P14`。本文件此后不再改写。

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `06c3d1089a539aa72ad89402c6dc24fc2e7177da` |
| 分支 | `cursor/p14-time-window-491f`（发布前）；合入 `develop` 后打标签 |
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

老板于 2026-10-21 确认 [README 第 6 节](README.md) 四步均已通过（真实登录账号；站内信与飞书一致）。

| 步骤 | 结果 | 说明 |
| --- | --- | --- |
| 1 ALLOW 段内提醒 | PASS | 每 N 分钟 +「只在这些时间段提醒」：段内到点站内信与飞书均收到；段外不再收到且不补发 |
| 2 DENY 段外跳过 | PASS | 「这些时间段不提醒」盖住下一次到点：该次双端均无；不补发；下一次段外到点正常收到 |
| 3 旧规则升级 | PASS | 已有提醒加时间段后历史保留，预览后续时间按新时段变化 |
| 4 假日与调休预览 | PASS | 预览中法定假日与调休上班日分别套用工作日/非工作日时段 |

备注：网页表单交互已由 TimeImprintWebsite P05 标签 `v20261020-P05` 验收；本表只记录 Task 端到端投递与预览。
