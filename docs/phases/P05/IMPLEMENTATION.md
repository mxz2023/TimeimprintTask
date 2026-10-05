# P05 · 实施任务

> 阶段身份与范围见 [README](README.md)。总体状态 DRAFT 时，T01 起禁止编码。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立阶段包；登记飞书整体接入（出站+入站） | PASS | 用户确认整体接入口径与按钮集合 | README/本文；09、CAP03、索引与 00 指向 P05；S02 三按钮、snooze=+1h、S01 无按钮、文字回复不实现 |
| T01 | 收敛契约：含 adapter 模块写入 02；03/07/CAP03 等；阶段转 READY | NOT_STARTED | 用户认可 README §6（含 adapter 模块名） | 02 模块表 13→14；CAP03/03/06/07/S01/S02/术语表一致；阶段 READY |
| T02 | `baselineGitRef` + 创建 `timeimprint-task-adapter`（feishu 骨架）+ 渠道展开 | NOT_STARTED | READY 且用户授权实施 | 基线写入 README；adapter 可编译；notification 依赖 adapter；默认仅 IN_APP 回归 |
| T03 | adapter 飞书客户端 + notification 出站卡片 Handler | NOT_STARTED | T02 PASS | EXTERNAL 发 interactive；SDK 仅在 adapter；Mock 发信 IT |
| T04 | web 回调 + adapter 验签 → complete/skip/snooze(+1h) | NOT_STARTED | T03 PASS | 验签入口；三命令映射；3 秒内 toast；幂等；伪造回调 IT |
| T05 | 场景去渠道硬依赖、Enforcer/ArchUnit、全量回归 | NOT_STARTED | T04 PASS | 依赖门禁证明 kernel/scenario 无 adapter；mysql-it/dual-process-it；DELIVERY |

任务按序推进。T00 文档；T01 契约（含 02 模块表）；T02 起才改生产代码且须实施授权。

## 2. 验证要求（实施后）

- 默认配置：无飞书 Action、无入站装配副作用。
- 开启 FEISHU：同槽位 IN_APP + 飞书卡片 Action；卡片含约定按钮。
- 伪造完成回调 → S02 实例 TERMINAL/COMPLETED，与 HTTP 完成命令同语义。
- 伪造稍后提醒回调 → `snoozeUntil = T + 1h`，平移规则与现有 snooze 一致。
- 伪造跳过回调 → 使用固定原因「飞书卡片跳过」。
- 出站失败不回滚站内信；入站不得绕过 revision/Policy。
- scenario/kernel 无飞书 SDK、无 adapter 依赖；飞书官方坐标只出现在 adapter；CI 无外网飞书租户；无文字回复验收项。

## 3. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | T01 闭合；§6 清空；用户确认整体接入范围 |
| READY → IMPLEMENTING | 用户明确授权 |
| IMPLEMENTING → VERIFYING | T02—T04 PASS；建 DELIVERY |
| VERIFYING → RELEASED | T05 与人工验收；标签 `vyyyyMMdd-P05` |
