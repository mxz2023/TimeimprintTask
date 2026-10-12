# P08 · 实施任务

> 阶段身份与范围见 [README](README.md)。总体状态为 DRAFT。用户确认草案前禁止编码。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立阶段包，登记站内信实时提示 | PASS | 用户于 2026-10-12 确认使用 SSE | 本文件与 README 存在；00 与阶段索引指向 P08 |
| T01 | 把 E30、超时和 [R01](../../07-ACCEPTANCE.md)—[R04](../../07-ACCEPTANCE.md) 写入 03、04、07、CAP03、09 | PASS | T00 | 正式契约与 README 第 2 节一致；阶段仍为 DRAFT |
| T02 | 实现 E30：提交后同进程通知，另一进程 5 秒水位核对 | NOT_STARTED | 阶段 READY 且用户授权实施 | 实施后写入的 DELIVERY 中 R01—R04 不再是 NOT_RUN |
| T03 | 工作台保持连接；Nginx 只对该路径关闭缓冲 | NOT_STARTED | 阶段 READY 且用户授权实施 | 网页在不刷新时更新未读点；代理配置与 03 一致 |
| T04 | 全量真库回归并记录 DELIVERY | NOT_STARTED | T02 与 T03 PASS | DELIVERY 记录 `mysql-it` 与 `dual-process-it` 的退出码 |

T02 与 T03 都依赖用户授权，彼此不阻塞。T04 必须等二者完成。DELIVERY 在 T04 开始前不得创建。

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | 用户确认本草案。T01 已完成，确认前仍禁止编码 |
| READY → IMPLEMENTING | 用户明确授权实施，并先记录 baselineGitRef |
| IMPLEMENTING → VERIFYING | T02、T03 PASS，且 DELIVERY 中 R01—R04 不再是 NOT_RUN |
| VERIFYING → RELEASED | T04 通过，且用户完成 README 第 4 节的人工步骤并说明看到的结果 |
