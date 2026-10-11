# P07 · 交付证据

> 本文只记录已经执行的命令和结果。
> 阶段身份见 [README](README.md)，任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-10-11T11:36:16Z
阶段状态: VERIFYING。人工验收尚未进行，不得据此标 RELEASED。[W01](../../07-ACCEPTANCE.md)—[W04](../../07-ACCEPTANCE.md) 与 [H01](../../07-ACCEPTANCE.md)—[H04](../../07-ACCEPTANCE.md) 为 PASS。

---

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `95e40961b7123bcc1193807022a2db3ee4fe6d04`（授权实施前的 HEAD，见 [README](README.md)） |
| 证据所测代码 | 提交 `0d1e8f8`（`feat(P07): 支持网页微信扫码与小时分钟间隔`）。成功的两次命令跑在该提交写入前的同一工作区 |
| JDK | Amazon Corretto 21.0.12（`openjdk version "21.0.12" 2026-07-21 LTS`） |
| 操作系统 | macOS |
| MySQL 镜像 | `mysql:9.7.2`（容器 `tit-mysql-t01`，只监听 `127.0.0.1:13306`） |
| 验收库 | 每次命令前由 `Deploy/scripts/06-recreate-task-acceptance-mysql.sh` 重建空库 `timeimprint_task_local` 与 `timeimprint_task_perf` |
| 防卡死上限 | 单次 20 分钟（1200 秒）；超时退出码 124 |
| 基础发布 | P06；Git 标签 `v20261010-P06` |

## 2. 验证命令

| 次序 | 命令 | 退出码 | 结果摘要 |
| --- | --- | --- | --- |
| 1 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 1 | BUILD FAILURE。总时间 32.056 秒，结束于 2026-10-11T11:22:28Z。Surefire 在 `StructureQualityGateTest.currentTreePassesQ01ToQ06` 失败：包名 `materialization` 不在允许列表；缺少 `WeChatLoginConfigTest`。尚未进入 Failsafe。随后把快照类移入 `calculation` 包并补上所有者测试，再跑第 2 次 |
| 2 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。总时间 58.023 秒，结束于 2026-10-11T11:24:13Z。Surefire 各模块合计 **431** run / 0 fail / 0 error / 0 skipped。Failsafe **112** run / 0 fail / 0 error / 0 skipped，含 `WeChatQrMysqlIT` 3 项与 `AccountAcceptanceMysqlIT` 10 项 |
| 3 | `bash Deploy/scripts/07-run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 24 秒，结束于 2026-10-11T11:36:16Z。Failsafe **7** run / 0 fail / 0 error。`PerfGateDualProcessIT`（`timeimprint-task-boot-loader` 模块）629.0 秒；预热与三次正式均为 inbox=1000、backlog=0、passed=true |

三条命令都在 MyStudio 仓库执行。执行前从环境中去掉了 `FEISHU_` 与 `TIMEIMPRINT_NOTIFICATION_` 前缀的变量。顺序执行，没有并发。微信自动化使用 `live` 夹具或 `capture`，没有访问微信开放平台。

## 3. W01—W04 与 H01—H04

| 编号 | 验收项 | 结论 | 证据 |
| --- | --- | --- | --- |
| [W01](../../07-ACCEPTANCE.md) | 扫码配置 | PASS | `WeChatQrMysqlIT.w01ConfigIssuesQrConnectWithoutSecretAndRejectsMobileAppType`：`appType=web` 的授权地址含 `qrconnect` 与 appid，响应不含 secret；`ios` 与 `android` 为 `INVALID_REQUEST` |
| [W02](../../07-ACCEPTANCE.md) | state | PASS | `WeChatQrMysqlIT.w02RejectsBadExpiredAndReusedStateWithoutCallingWeChat`：错误、过期、第二次使用的 state 为 `INVALID_REQUEST`，且不调用换票 |
| [W03](../../07-ACCEPTANCE.md) | 换票 | PASS | `WeChatQrMysqlIT.w03LiveStubOpenIdLogsInWithoutAccessTokenInResponse`：夹具 openid 登录成功；响应不含访问令牌 |
| [W04](../../07-ACCEPTANCE.md) | 捕获模式 | PASS | `AccountAcceptanceMysqlIT.u05WeChatFindsSameOpenIdAndRejectsSecondBind`：`capture` 把 code 当作 openid。`ArchitectureRulesTest.noFeishuScanLoginSurface`：飞书扫码仍无端点 |
| [H01](../../07-ACCEPTANCE.md) | 小时间隔 | PASS | `CalendarOccurrenceCalculatorTest.everyNHoursAndMinutesPreviewFromFixedAnchor`、`everyNHoursRejectsOutOfRangeInterval`；`CalendarConfigParserTest.parsesSubDayIntervalsAndRejectsIllegalBounds`。原五种规则仍由 `CalendarOccurrenceCalculatorTest.dailyStrictlyAfter` 与 `CalendarFiveRulesPreviewMysqlIT` 覆盖。小时发生算法在 Surefire，不是单独的真库建定义用例 |
| [H02](../../07-ACCEPTANCE.md) | 分钟间隔 | PASS | 同上 `everyNHoursAndMinutesPreviewFromFixedAnchor` 断言每 15 分钟的 occurrenceKey；`intervalMinutes=1441` 被解析器拒绝 |
| [H03](../../07-ACCEPTANCE.md) | 不支持稍后 | PASS | `RecurringTodoCommandHandlerTest.snoozeRejectedWhenSnoozeNotAllowed`：`STATE_CONFLICT` 且消息含「不支持稍后」。`FeishuCardBuilderTest.actionCardOmitsSnoozeWhenDisallowed`：卡片只有「完成」和「跳过」。HTTP 与飞书入站共用该命令处理器；没有单独的飞书伪造回调真库用例去点小时规则的稍后 |
| [H04](../../07-ACCEPTANCE.md) | 日规则稍后 | PASS | `S02RecurringTodoMysqlIT.s02SnoozeShiftsReadyActions` 与 `FeishuCardActionMysqlIT.snoozeCallbackShiftsToEventTimePlusOneHourAndReplayDoesNotSnoozeTwice`。`FeishuCardBuilderTest.actionCardHasThreeCallbackButtonsInOrder` 仍含「稍后提醒」。第 2 次 mysql-it 的既有创建用例仍通过，原五种规则的 7 天整窗写入没有被这次改动打失败 |

[T02](IMPLEMENTATION.md)、[T03](IMPLEMENTATION.md) 与 [T04](IMPLEMENTATION.md) 据此标 PASS。

## 4. 人工验收

尚未进行。自动化没有调用真实微信开放平台。`live` 模式面向真实用户时，仍须配置网页应用的 app id、app secret 与 redirect uri，这些值不得写入仓库。
