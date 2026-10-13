# P08 · 交付证据

> 本文只记录已经执行的命令和结果。
> 阶段身份见 [README](README.md)，任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-10-13T16:25:21Z
阶段状态: RELEASED（2026-10-13 用户确认页面可以收到通知；2026-10-14 冻结；Git 标签 `v20261014-P08`；本文此后不再改写）。[R01](../../07-ACCEPTANCE.md)—[R04](../../07-ACCEPTANCE.md) 为 PASS。

---

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `8bbf257dff0f60b7cfee40002b8c4ef80cdfeb08`（授权实施前的 HEAD，见 [README](README.md)） |
| 证据所测代码 | 第 8、9 行跑在 `InboxEventsMysqlIT.otherProcessInsertReachesOpenStreamWithinPoll` 写入提交之前的同一工作区。更早的第 6、7 行还不包含该用例 |
| JDK | Java 21.0.12（测试进程日志） |
| 操作系统 | macOS |
| MySQL 镜像 | `mysql:9.7.2`（容器 `tit-mysql-t01`，只监听 `127.0.0.1:13306`） |
| 验收库 | 每次命令前由 `Deploy/scripts/06-recreate-task-acceptance-mysql.sh` 重建空库 `timeimprint_task_local` 与 `timeimprint_task_perf` |
| 本机凭据 | 命令环境去掉 `FEISHU_` 与 `TIMEIMPRINT_NOTIFICATION_` 前缀变量 |

人工看到通知的页面走另一套库：容器 `mystudio-local-task-mysql`（宿主机端口 3308，库名同为 `timeimprint_task_local`）。它不是 `tit-mysql-t01`。

## 2. 命令

命令在 MyStudio 根目录执行。下表按时间排列。第 6、7 行覆盖列表过滤之后的工作区，是本文引用的回归结果。更早的第 4、5 行是事件流刚写完时的通过记录，随后被第 6、7 行覆盖。

| 次序 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 1 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 1 | BUILD FAILURE。约 50 秒，开始于 2026-10-12T14:57:41Z。`StructureQualityGateTest` 失败：`common.inbox` 与 `web.inbox.stream` 不在允许包名内，且缺少 `CommitAfterTest`。尚未进入 Failsafe。随后把唤醒移到 `common.wake`、事件枢纽留在 `web.inbox.controller` 并补上所有者测试 |
| 2 | 同上 | 1 | BUILD FAILURE。约 60 秒，开始于 2026-10-12T15:01:09Z。`InboxEventHub`（`timeimprint-task-web` 模块）有两个公开构造器，Spring 回退到不存在的无参构造。随后只保留一个带 `@Autowired` 的生产构造器 |
| 3 | 同上 | 1 | BUILD FAILURE。约 105 秒，开始于 2026-10-12T15:02:46Z。`InboxEventsMysqlIT` 等待 `: ping`，线上是 `:ping`。随后心跳注释写成带前导空格的 ` ping` |
| 4 | 同上 | 0 | BUILD SUCCESS。总时间 1 分 25 秒，结束于 2026-10-12T15:06:51Z。`InboxEventsMysqlIT` 2 项通过。此工作区尚无实例列表过滤 |
| 5 | `bash Deploy/scripts/07-run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 27 秒，结束于 2026-10-12T15:18:42Z。与第 4 行同一工作区 |
| 6 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。总时间 1 分 26 秒，结束于 2026-10-12T22:54:21Z。boot-loader Failsafe **114** run / 0 fail / 0 error / 0 skipped，含 `InboxEventsMysqlIT` 2 项（25.34 秒） |
| 7 | `bash Deploy/scripts/07-run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 23 秒，结束于 2026-10-12T23:06:09Z。Failsafe **7** run / 0 fail / 0 error。`PerfGateDualProcessIT`（`timeimprint-task-boot-loader` 模块）628.1 秒。这是既有性能门槛，没有单独断言另一进程的站内信变化帧 |
| 8 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。总时间 1 分 32 秒，结束于 2026-10-13T16:00:14Z。boot-loader Failsafe **115** run / 0 fail / 0 error / 0 skipped，含 `InboxEventsMysqlIT` 3 项（30.39 秒） |
| 9 | `bash Deploy/scripts/07-run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 26 秒，结束于 2026-10-13T16:25:21Z。Failsafe **7** run / 0 fail / 0 error。`PerfGateDualProcessIT` 629.1 秒。没有为站内信再起第二个 JVM |

## 3. 验收项

| 编号 | 名称 | 结论 | 证据 |
| --- | --- | --- | --- |
| [R01](../../07-ACCEPTANCE.md) | 鉴权与隔离 | PASS | `InboxEventsMysqlIT.missingTokenIsJsonUnauthorized`：无效 Bearer 返回 JSON `UNAUTHENTICATED`，响应体不含 `inbox-changed`。`streamFollowsOwnInboxAndHeartbeat`：本连接的 `unreadCount` 等于该接收人的 SQL 计数，帧里没有 `title`。没有另开第二个接收人的连接做反例 |
| [R02](../../07-ACCEPTANCE.md) | 同进程提交后推送 | PASS | `InAppNotificationHandlerTest` 在插入后唤醒，重复插入不唤醒。`InboxServiceTest` 只在首次已读后唤醒。`InboxEventHubTest` 只读匹配的接收人。真库 IT 检查的是连接打开时的第一帧和心跳，没有在连接打开后再插入一行。页面在不刷新时出现新信，见第 4 节 |
| [R03](../../07-ACCEPTANCE.md) | 超时与心跳 | PASS | `InboxEventsMysqlIT` 在 30 秒内收到 `: ping`。30 分钟上限是 `InboxEventHub`（`timeimprint-task-web` 模块）的 `MAX_AGE_MILLIS`，由 `InboxEventHubTest` 锁定常量，没有真的等待 30 分钟。`/api/v1/inbox/events` 的 `proxy_buffering off` 与其余 `/api/v1/` 的 `proxy_read_timeout 120s` 写在 MyStudio `Deploy/docker/nginx/nginx.conf` 与 `Deploy/docker/timeimprint-task/nginx.conf`（提交 `929aa01`），没有用一次超过 120 秒的其它接口请求去打代理 |
| [R04](../../07-ACCEPTANCE.md) | 断开与另一进程 | PASS | `InboxEventsMysqlIT.otherProcessInsertReachesOpenStreamWithinPoll`：没有连接时直接插入一行，打开后只有一帧当前快照。连接保持期间再次直接插入，且不调用 `InboxWake`（`timeimprint-task-common` 模块），6 秒内收到未读数变大的 `inbox-changed`，帧里没有 `title`。断开后 [E12](../../04-API.md)（未读数）与 SQL 计数一致。水位常量是 5 秒；6 秒是调度余量。没有另起第二个 JVM，另一进程对本进程的可见效果就是这次直接插入 |

## 4. 人工步骤

2026-10-13 用户在本地工作台（`http://localhost:8080`）确认可以正常收到通知。当时页面已经打开，没有靠刷新取信。该页面连的是容器 `mystudio-local-task-mysql`（端口 3308），任务进程是 `mystudio-local-task`，网页镜像含提交 `9d82bad` 的帧解析。

这一步满足 [README](README.md) 第 4 节的页面观察。自动证据与这一步都已完成，阶段已 RELEASED。
