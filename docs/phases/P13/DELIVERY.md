# P13 · 交付证据

本文只记录已经执行的命令和结果。阶段身份见 [README](README.md)，任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

阶段于 2026-10-18 转入 VERIFYING。自动证据已通过。老板同日确认删除功能人工三步通过；确认交互 UI 由 Website P03 改弹窗，完成前本阶段保持 VERIFYING、不得 RELEASED。

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

老板于 2026-10-18 在本机入口（`http://localhost:8080/`，版本 `20261018003`）完成查看，结论如下。

| 步骤 | 结果 | 说明 |
| --- | --- | --- |
| 删一条（含未读） | PASS | 列表不再显示该条；未读/角标行为正常。老板确认删除功能没有问题 |
| 一键清空已读 | PASS | 已读消失、未读保留。老板确认删除功能没有问题 |
| 投递摘要 `inboxCount` | PASS | 老板确认三个人工检查均已查看，删除功能没有问题（不因软删把页面上的投递计数自行减一） |

备注：老板对「删除确认」交互不满意（内联确认，要求改为弹窗）。该 UI 属 Website P03，Task API/软删语义不改。Website 弹窗改完并重建前端后，由 MyStudio 收口合并；此前不标 RELEASED。
