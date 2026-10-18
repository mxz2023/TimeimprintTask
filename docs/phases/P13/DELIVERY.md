# P13 · 交付证据

本文只记录已经执行的命令和结果。阶段身份见 [README](README.md)，任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

阶段仍是 IMPLEMENTING。没有人工步骤结果，不得标为 RELEASED。[B01](../../07-ACCEPTANCE.md)—[B04](../../07-ACCEPTANCE.md) 的真库结论是 BLOCKED，不是 PASS。

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `e0b16fb12aa3554a05ae30a43a091429384ccdae` |
| JDK | Java 21.0.12.1 |
| 操作系统 | Linux |
| MySQL | 未启动。本环境没有 Docker，也没有 MyStudio 的 `Deploy/` |
| 命令环境 | 准备去掉 `FEISHU_` 与 `TIMEIMPRINT_NOTIFICATION_` 前缀；脚本在此之前退出 |

## 2. 命令

| 次序 | 命令 | 退出码 | 记录 |
| --- | --- | --- | --- |
| 1 | `./mvnw -q -pl timeimprint-task-domain,timeimprint-task-service-application,timeimprint-task-service-storage-mysql,timeimprint-task-gateway,timeimprint-task-web,timeimprint-task-service-capability-notification,timeimprint-task-boot-loader -am test -DskipITs` | 0 | Surefire 283 个报告，453 项，0 失败，0 错误，0 跳过。含 `InboxServiceTest`、`InboxRepositoryImplTest`、`TaskQueryServiceTest` |
| 2 | `bash scripts/run-task-acceptance.sh mysql-it` | 1 | 立即失败：`找不到身份实现、入口或应用配置，无法对照 actor_key`。脚本把 MyStudio 根目录算在本仓库的上一级，那里没有 `Deploy/docker/timeimprint-task/entrypoint.sh`。没有创建 `tit-mysql-t01`，没有进入 Maven |
| 3 | `bash scripts/run-task-acceptance.sh dual-process-it` | 未执行 | `mysql-it` 未通过，按仓库规则停止 |

## 3. 验收项

| 编号 | 结果 | 说明 |
| --- | --- | --- |
| B01 | BLOCKED | 用例在 `InboxSoftDeleteMysqlIT.deleteHidesRowKeepsFirstTimeAndInboxCount`。真库没有跑起来 |
| B02 | BLOCKED | 用例在 `InboxSoftDeleteMysqlIT.clearReadRemovesOnlyOwnReadRows`。真库没有跑起来 |
| B03 | BLOCKED | 同进程推送的真库用例在 `InboxSoftDeleteMysqlIT.softDeletePushesInboxChangedWithoutTitle`。推送失败不回滚删除由 `InboxServiceTest.pushFailureDoesNotUndoDelete` 在单元测试中覆盖，不能代替真库 |
| B04 | BLOCKED | `inboxCount` 仍按成功 Action 计数，`TaskQueryServiceTest.inboxCountStaysOnSucceededActionsWhileUnreadExcludesDeleted` 锁定该公式。真库前后对比没有执行 |

## 4. 人工步骤

未执行。网页二次确认须在 Website 同期阶段可用后由人完成。
