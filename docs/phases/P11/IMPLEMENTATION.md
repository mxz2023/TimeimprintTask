# P11 · 实施任务

> 阶段身份与范围见 [README](README.md)。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T01 | 删除五张 `tt_identity_*` 和只写这些表的代码 | PASS | 用户授权实施 | 迁移后库中没有这些表；任务令牌仍向用户服务核对 |
| T02 | 全量真库回归 | PASS | T01 | [DELIVERY](DELIVERY.md)：`mysql-it` 与 `dual-process-it` 退出码 0 |

不新增公开端点编号。验收沿用既有全量回归，并确认 `tt_identity_*` 不存在。

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → IMPLEMENTING | 2026-10-17 用户确认不做数据复制，并授权实施 |
| IMPLEMENTING → VERIFYING | 2026-10-17：T01、T02 完成，真库命令见 [DELIVERY](DELIVERY.md) |
| VERIFYING → RELEASED | 2026-10-17 用户确认两步人工结果。标签 `v20261017-P11` |
