# P06 · 实施任务

> 阶段身份与范围见 [README](README.md)。阶段已 RELEASED（2026-10-10；标签 `v20261010-P06`）；本文冻结，不再改写。决策见已接受的 [ADR-0001](../../decisions/ADR-0001-multi-user-identity.md)。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立阶段包，登记多用户账号范围 | PASS | 用户指定 P06 为多用户 | 本文件与 README 存在；00 与阶段索引指向 P06 |
| T01 | 用户接受 ADR-0001，并把账号契约写入 01—07、02 模块表与 CAP04 账号子集；阶段转 READY | PASS | 用户明确接受 ADR-0001 | 正式契约与 ADR 一致；COL-01 账号子集为 READY_FOR_IMPLEMENTATION；阶段 READY |
| T02 | 记录 baselineGitRef；新建 identity 专有表、短信替身、手机号注册与密码登录 | PASS | 阶段 READY 且用户另行授权实施 | [DELIVERY](DELIVERY.md) U01—U03、U08。测试使用 capture，不调用腾讯云 |
| T03 | 微信登录、绑定、解绑和绑定手机号合并；令牌构造 ActorContext | PASS | T02 PASS | [DELIVERY](DELIVERY.md) U04—U07、U09、U10 |
| T04 | 管理员列表、改密、忘记密码、退出；旧测试改为登录；全量回归 | PASS | T03 PASS | [DELIVERY](DELIVERY.md) 第 4 节与第 5 节 |

任务按序推进。T00—T01 只改文档。T02 起才改生产代码，且必须另有实施授权。

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | T01 PASS，且 ADR-0001 已是 ACCEPTED（2026-10-10 已完成） |
| READY → IMPLEMENTING | 用户明确授权本阶段实施（2026-10-10 已完成） |
| IMPLEMENTING → VERIFYING | T02—T03 PASS，且 [DELIVERY](DELIVERY.md) 中 U01—U10 不再是 NOT_RUN |
| VERIFYING → RELEASED | T04 与用户人工验收；标签 `v20261010-P06`（已完成） |
