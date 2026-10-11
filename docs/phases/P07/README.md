# P07 · 微信扫码与小时分钟间隔

本阶段做两件事：网页微信扫码可以换到真实身份；[S01](../../scenarios/S01-reminder.md)（通用提醒）和 [S02](../../scenarios/S02-recurring-todo.md)（周期待办）可以按小时或按分钟重复。按小时或按分钟物化的 S02 实例不支持稍后提醒。决策见已接受的 [ADR-0002](../../decisions/ADR-0002-wechat-qr-and-subday-interval.md)。执行规则在 [01](../../01-MVP-SPEC.md)—[07](../../07-ACCEPTANCE.md)。

任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P07 |
| 排期身份 | CURRENT |
| 总体状态 | IMPLEMENTING |
| 文档基线 | 2.3 |
| 基础发布 | P06；Git 标签 `v20261010-P06` |
| baselineGitRef | `95e40961b7123bcc1193807022a2db3ee4fe6d04`（授权实施前的 HEAD） |
| 工程状态 | IMPLEMENTING |
| Git发布标签 | 未发布 |
| 人工验收 | 未进行 |
| 下一动作 | 按 [IMPLEMENTATION](IMPLEMENTATION.md) 完成 T02、T03 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | 尚未创建。验收通过前不得预填 PASS |

用户于 2026-10-11 指定本阶段范围并要求实现。P01—P06 已冻结，不得改写。

## 2. 已接受口径

| 口径 | 结论 |
| --- | --- |
| 微信扫码 | `live` 模式下，`appType=web` 的 [E25](../../04-API.md) 返回扫码地址和一次性 state。回调向微信换 openid。测试继续用 `capture` |
| 移动端扫码 | `ios` / `android` 不返回扫码地址 |
| 飞书扫码 | 仍不实现 |
| 小时间隔 | `EVERY_N_HOURS`，`intervalHours` 为 1—168，锚点不漂移 |
| 分钟间隔 | `EVERY_N_MINUTES`，`intervalMinutes` 为 1—1440，锚点不漂移 |
| 原五种规则 | 发生结果与 7 天整窗写入不变 |
| 密间隔窗口 | 7 天视野内同步最多写入 100 次，其余由 Planner 补齐 |
| 稍后 | 仅小时和分钟规则物化的 S02 实例拒绝稍后；飞书卡片不放该按钮。完成、跳过、催办保留 |
| 内核 | 不改 kernel 语义和平台公共表 |

## 3. 允许与禁止

允许：

- 在 adapter 增加访问微信开放平台的客户端，在 identity 增加 oauth state 专有表。
- 在 calendar 增加两种规则，并让 S02 对这两种实例拒绝稍后。
- 验收范围是 [W01](../../07-ACCEPTANCE.md)—[W04](../../07-ACCEPTANCE.md) 与 [H01](../../07-ACCEPTANCE.md)—[H04](../../07-ACCEPTANCE.md)。

禁止：

- 改写已 RELEASED 的 P01—P06。
- 修改 kernel 业务语义或平台公共表。
- 实现飞书扫码，或把微信密钥写入仓库。
- 为小时和分钟间隔提供稍后，或改变原五种日历规则的发生结果。
- 在验收完成前创建或预填 `DELIVERY.md`。
