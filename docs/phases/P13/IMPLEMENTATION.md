# P13 · 实施任务

> 阶段身份与范围见 [README](README.md)。当前总体状态是 VERIFYING。baselineGitRef=`e0b16fb12aa3554a05ae30a43a091429384ccdae`。MyStudio 于 2026-10-18 授权实施；同日真库自动证据通过后转入 VERIFYING。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立阶段包，登记站内信软删除口径 | PASS | 用户确认本期只做收件箱删除 | 本文件与 README 存在；00 与阶段索引指向 P13 |
| T01 | 把 E31/E32、`deleted_at`、排除规则与 B01—B04 写入 04、05、07、CAP03 | PASS | T00；转 READY 前完成 | 正式契约与 README 第 2、6 节一致 |
| T02 | 迁移 `deleted_at`；实现 E31/E32；E10—E12/E30 排除已删；软删后推帧 | PASS | 用户将本阶段转为 IMPLEMENTING 并授权 | 代码在 PR 分支；B01—B03 真库 PASS，见 [DELIVERY](DELIVERY.md) |
| T03 | 断言 `inboxCount` 不因软删减少；全量真库回归 | PASS | T02 | B04 PASS；本机 `mysql-it` 109、`dual-process-it` 7 均退出码 0，见 [DELIVERY](DELIVERY.md) |
| T04 | 记录人工步骤结果并发布 | IN_PROGRESS | T03；人工步骤完成 | 人工功能三步已记入 [DELIVERY](DELIVERY.md)；确认 UI 待 Website；标签待合并后打 |

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | 2026-10-18 MyStudio 代老板确认草案范围与必要人工步骤已写明 |
| READY → IMPLEMENTING | 2026-10-18 MyStudio 授权实施；baselineGitRef=`e0b16fb12aa3554a05ae30a43a091429384ccdae` |
| IMPLEMENTING → VERIFYING | T02、T03 的自动证据写入 DELIVERY |
| VERIFYING → RELEASED | T04 与人工步骤完成；打发布标签并冻结阶段文件 |
