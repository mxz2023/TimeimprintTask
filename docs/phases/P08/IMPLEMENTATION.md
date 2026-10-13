# P08 · 实施任务

> 阶段身份与范围见 [README](README.md)。总体状态为 VERIFYING。baselineGitRef 为 `8bbf257dff0f60b7cfee40002b8c4ef80cdfeb08`。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立阶段包，登记站内信实时提示 | PASS | 用户于 2026-10-12 确认使用 SSE | 本文件与 README 存在；00 与阶段索引指向 P08 |
| T01 | 把 E30、超时和 [R01](../../07-ACCEPTANCE.md)—[R04](../../07-ACCEPTANCE.md) 写入 03、04、07、CAP03、09 | PASS | T00 | 正式契约与 README 第 2 节一致 |
| T02 | 实现 E30：提交后同进程通知，另一进程 5 秒水位核对 | PASS | 用户于 2026-10-12 授权实施 | [DELIVERY](DELIVERY.md) 中 [R01](../../07-ACCEPTANCE.md)—[R04](../../07-ACCEPTANCE.md) 均为 PASS |
| T03 | 工作台保持连接；Nginx 只对该路径关闭缓冲 | PASS | 用户于 2026-10-12 授权实施 | 2026-10-13 用户在已打开的本地工作台看到新信。代理配置见 [DELIVERY](DELIVERY.md) 第 3 节 |
| T04 | 全量真库回归并记录 DELIVERY | PASS | T02 与 T03 的证据已写入 DELIVERY | [DELIVERY](DELIVERY.md) 第 8、9 次命令退出码 0，人工步骤已完成 |

T02 与 T03 都依赖用户授权，彼此不阻塞。T04 的回归与人工步骤已完成。发布标签尚未打上。

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | 用户于 2026-10-12 确认本草案（已完成）。未授权前仍禁止编码 |
| READY → IMPLEMENTING | 用户于 2026-10-12 授权实施。baselineGitRef 已记录（已完成） |
| IMPLEMENTING → VERIFYING | T02、T03 PASS，且 [DELIVERY](DELIVERY.md) 中 [R01](../../07-ACCEPTANCE.md)—[R04](../../07-ACCEPTANCE.md) 不再是 NOT_RUN（已完成） |
| VERIFYING → RELEASED | T04 通过，且用户完成 README 第 4 节的人工步骤并说明看到的结果。两项证据都已在 DELIVERY；标签尚未打上，因此仍停在 VERIFYING |
