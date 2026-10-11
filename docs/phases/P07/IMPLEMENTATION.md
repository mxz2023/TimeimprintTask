# P07 · 实施任务

> 阶段身份与范围见 [README](README.md)。决策见已接受的 [ADR-0002](../../decisions/ADR-0002-wechat-qr-and-subday-interval.md)。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立阶段包，登记微信扫码与小时/分钟间隔 | PASS | 用户指定 P07 | 本文件与 README 存在；00 与阶段索引指向 P07 |
| T01 | 把契约写入 01—07、CAP01、S01、S02 与 ADR-0002 | PASS | T00 | 正式契约与 ADR-0002 一致；阶段为 IMPLEMENTING |
| T02 | 网页扫码配置、一次性 state、live 换票；capture 保持不访问微信 | IN_PROGRESS | T01 PASS | [W01](../../07-ACCEPTANCE.md)—[W04](../../07-ACCEPTANCE.md) |
| T03 | `EVERY_N_HOURS` / `EVERY_N_MINUTES`；这两种 S02 实例拒绝稍后且飞书卡片无稍后按钮 | IN_PROGRESS | T01 PASS | [H01](../../07-ACCEPTANCE.md)—[H04](../../07-ACCEPTANCE.md) |
| T04 | 全量真库回归并记录 DELIVERY | NOT_STARTED | T02 与 T03 PASS | DELIVERY 中的命令、退出码和失败测试名 |

T02 与 T03 都依赖 T01，彼此不阻塞。T04 必须等二者的验收项都有真实测试。

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | T01 PASS |
| READY → IMPLEMENTING | 用户于 2026-10-11 要求实现本阶段 |
| IMPLEMENTING → VERIFYING | T02、T03 PASS，且 DELIVERY 中 W01—W04、H01—H04 不再是 NOT_RUN |
| VERIFYING → RELEASED | T04 与用户人工验收 |
