# P13 · 交付证据

本文只记录已经执行的命令和结果。阶段身份见 [README](README.md)，任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

阶段于 2026-10-18 转入 VERIFYING。自动证据已通过；人工步骤未完成前不得标为 RELEASED。

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `e0b16fb12aa3554a05ae30a43a091429384ccdae` |
| 真库验证提交 | `2799f91a6ffdab580aaf321ae0262c8a78b88e0b`（分支 `cursor/p13-inbox-soft-delete-f721`） |
| JDK | OpenJDK 21.0.12 LTS |
| 操作系统 | macOS (Darwin) |
| MySQL | 本机 `tit-mysql-t01`（`scripts/run-task-acceptance.sh` 启动；库 `db_timeimprint_task`） |
| 命令环境 | 去掉本机泄漏的 `FEISHU_*` 与 `TIMEIMPRINT_NOTIFICATION_*` 后再跑；与 develop 上同一飞书配置污染无关 |

## 2. 命令

| 次序 | 命令 | 退出码 | 记录 |
| --- | --- | --- | --- |
| 1 | `./mvnw -q -pl … -am test -DskipITs`（云端 agent） | 0 | Surefire 通过（含 `InboxServiceTest`、`InboxRepositoryImplTest`、`TaskQueryServiceTest`） |
| 2 | `env -u FEISHU_* -u TIMEIMPRINT_NOTIFICATION_* bash scripts/run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。结束于 2026-10-18T19:23:49+08:00。Failsafe **109** 项，0 失败；含 `InboxSoftDeleteMysqlIT` 3 项 |
| 3 | `env -u FEISHU_* -u TIMEIMPRINT_NOTIFICATION_* bash scripts/run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间约 11 分 31 秒，结束于 2026-10-18T20:36:43+08:00。Failsafe **7** 项，0 失败 |

## 3. 验收项

| 编号 | 结果 | 说明 |
| --- | --- | --- |
| B01 | PASS | `InboxSoftDeleteMysqlIT.deleteHidesRowKeepsFirstTimeAndInboxCount`：软删后列表/详情/未读隐藏该行；重复删除幂等且保留首次 `deleted_at`；同测断言 `inboxCount` |
| B02 | PASS | `InboxSoftDeleteMysqlIT.clearReadRemovesOnlyOwnReadRows`：只软删本接收人已读未删行，未读保留 |
| B03 | PASS | `InboxSoftDeleteMysqlIT.softDeletePushesInboxChangedWithoutTitle`：软删后同进程推 `inbox-changed`。推送失败不回滚由 `InboxServiceTest.pushFailureDoesNotUndoDelete` 覆盖 |
| B04 | PASS | 同上真库用例断言 `DeliverySummary.inboxCount` 不因软删减少；公式另由 `TaskQueryServiceTest.inboxCountStaysOnSucceededActionsWhileUnreadExcludesDeleted` 锁定 |

## 4. 人工步骤

未执行。须在 Website 同期阶段可用、本机部署就绪后，由人在真实页面完成 README 第 4 节两步；确认结果前不得 RELEASED。
