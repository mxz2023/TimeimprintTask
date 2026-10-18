# P13 · 实施任务

> 阶段身份与范围见 [README](README.md)。当前总体状态是 IMPLEMENTING。baselineGitRef=`e0b16fb12aa3554a05ae30a43a091429384ccdae`。MyStudio 于 2026-10-18 授权实施。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立阶段包，登记站内信软删除口径 | PASS | 用户确认本期只做收件箱删除 | 本文件与 README 存在；00 与阶段索引指向 P13 |
| T01 | 把 E31/E32、`deleted_at`、排除规则与 B01—B04 写入 04、05、07、CAP03 | PASS | T00；转 READY 前完成 | 正式契约与 README 第 2、6 节一致 |
| T02 | 迁移 `deleted_at`；实现 E31/E32；E10—E12/E30 排除已删；软删后推帧 | BLOCKED | 用户将本阶段转为 IMPLEMENTING 并授权 | 代码与单元测试已写入。[DELIVERY](DELIVERY.md) 中 B01—B03 因真库脚本未能启动而为 BLOCKED |
| T03 | 断言 `inboxCount` 不因软删减少；全量真库回归 | BLOCKED | T02 | DELIVERY 中 B04 与回归未能执行；`mysql-it` 退出码 1 |
| T04 | 记录人工步骤结果并发布 | NOT_STARTED | T03；人工步骤完成 | 用户确认页面结果；标签 `v`+日期+`-P13` |

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | 2026-10-18 MyStudio 代老板确认草案范围与必要人工步骤已写明 |
| READY → IMPLEMENTING | 2026-10-18 MyStudio 授权实施；baselineGitRef=`e0b16fb12aa3554a05ae30a43a091429384ccdae` |
| IMPLEMENTING → VERIFYING | T02、T03 的自动证据写入 DELIVERY |
| VERIFYING → RELEASED | T04 与人工步骤完成；打发布标签并冻结阶段文件 |
