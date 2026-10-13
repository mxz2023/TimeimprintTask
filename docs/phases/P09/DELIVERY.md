# P09 · 交付证据

> 本文只记录已经执行的命令和结果。阶段身份见 [README](README.md)。任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-10-13T18:09:41Z
阶段状态: VERIFYING。真库回归已通过。尚未 RELEASED，等待用户确认可以发布。

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `e5c3906045e01175788f4e665489884f7c9abdcd` |
| JDK | Java 21.0.12 |
| 操作系统 | macOS |
| MySQL | 容器 `tit-mysql-t01`，镜像 `mysql:9.7`，只监听 `127.0.0.1:13306` |
| 验收库 | 每次命令前由 `Deploy/scripts/06-recreate-task-acceptance-mysql.sh` 重建空库 |
| 本机凭据 | 命令环境去掉 `FEISHU_` 与 `TIMEIMPRINT_NOTIFICATION_` 前缀变量 |

## 2. 命令

命令在 MyStudio 根目录执行。下表是修复后的最终结果。更早的同名命令曾失败：覆盖率基线缺少新模块、创建后的专有数据迁移与定义版本号不一致、冲正时未清空退役时间。这些失败没有列入本表。

| 次序 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 1 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。总时间 1 分 29 秒，结束于 2026-10-14T01:58:03+08:00。boot-loader Failsafe **118** run / 0 fail / 0 error / 0 skipped，含 `P09DeadlineMysqlIT` |
| 2 | `bash Deploy/scripts/07-run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 28 秒，结束于 2026-10-14T02:09:41+08:00。Failsafe **7** run / 0 fail / 0 error / 0 skipped。`PerfGateDualProcessIT` 628.4 秒 |

## 3. 本阶段断言

| 编号 | 结论 | 证据 |
| --- | --- | --- |
| D01—D02 | PASS | `CalendarOccurrenceCalculatorTest`：平年 2 月 29 日落到 3 月 1 日；锚点偏移后可按日继续 |
| D03—D05 | PASS | `P09DeadlineMysqlIT`：纪念日修改留历史、到期续期后取消、部分支付后付清再冲正 |
