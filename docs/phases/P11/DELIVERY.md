# P11 · 交付证据

自动证据与两步人工确认都在 2026-10-17 通过。阶段已 RELEASED。Git 标签 `v20261017-P11` 打在紧接本文档冻结之后的代码提交上。本文件此后不再改写。

## 1. 验收项

| 编号 | 结果 | 证据 |
| --- | --- | --- |
| 全量回归 | PASS | `mysql-it` Failsafe 106 项、`dual-process-it` Failsafe 7 项均为 0 失败 |
| 账号表 | PASS | `A38SchemaMysqlIT.informationSchemaListsTwelvePlatformTables`：`tt_identity_*` 计数为 0 |

不新增公开端点编号。

## 2. 命令

| 次序 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 1 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 1 | BUILD FAILURE。约 101 秒，结束于 2026-10-17T10:57:41+08:00。`InboxEventsMysqlIT.missingTokenIsJsonUnauthorized` 得到 503；`T07FixtureMysqlIT` 因 Flyway 目录不一致无法启动 |
| 2 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 1 | BUILD FAILURE。约 70 秒，结束于 2026-10-17T11:00:41+08:00。已执行的 `20260912999001` 不在其他测试的迁移目录里 |
| 3 | `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。总时间 1 分 41 秒，结束于 2026-10-17T11:18:03+08:00。Failsafe 106 项，0 失败 |
| 4 | `bash Deploy/scripts/07-run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 43 秒，结束于 2026-10-17T11:29:53+08:00。Failsafe 7 项，0 失败 |

环境变量已去掉 `FEISHU_` 与 `TIMEIMPRINT_NOTIFICATION_` 前缀。超时上限 20 分钟，没有出现退出码 124。

## 3. 人工步骤

| 步骤 | 结果 | 记录 |
| --- | --- | --- |
| 用用户服务登录，打开任务页 | PASS | 用户于 2026-10-17 确认没有问题，能进入自己的任务 |
| 退出后原令牌再访问任务接口 | PASS | 用户于 2026-10-17 确认原令牌立即失效 |
