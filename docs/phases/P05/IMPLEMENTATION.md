# P05 · 实施任务

> 阶段身份与范围见 [README](README.md)，全局规则见 [08](../../08-AI-IMPLEMENTATION-TASKS.md)。本文是 P05 任务顺序与状态的正式来源。总体状态为 DRAFT 时，任务均为 NOT_STARTED，且 **T01 起禁止编码**。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立阶段包并登记 C12/NOT-05 飞书子集 | IN_PROGRESS | 用户选定 P05=飞书 + 通用 IM 框架 + 默认仅站内信、配置开启 | 本文与 [README](README.md)；[09](../../09-SCENARIO-ROADMAP.md)、[CAP03](../../capabilities/CAP03-notification.md)、阶段索引与 00 指向 P05 CURRENT/DRAFT |
| T01 | 收敛渠道配置、飞书 API 与错误分类至正式契约 | NOT_STARTED | README 待决问题全部拍板；阶段转 READY | CAP03 NOT-05、03 配置键、06 §8、07 新增验收项、S01/S02 渠道说明、术语表已更新且文档一致 |
| T02 | 记录 `baselineGitRef` 并实现渠道展开 | NOT_STARTED | 阶段 READY 且用户明确授权实施 | `baselineGitRef` 写入 README；接收人×渠道展开；默认仅 `IN_APP` 行为与 P04 回归一致 |
| T03 | 实现飞书 EXTERNAL Handler 与配置装配 | NOT_STARTED | T02 PASS | Handler 注册、凭据与映射、超时预算、错误分类；无真实密钥入库 |
| T04 | 场景去渠道硬依赖与单元/契约测试 | NOT_STARTED | T03 PASS | S01/S02 模板渠道中立；相关所有者测试通过 |
| T05 | 真库与 Mock 飞书 IT + 全量回归 | NOT_STARTED | T04 PASS | 开启/关闭飞书用例通过；飞书失败不回滚站内信；单元/打包/mysql-it/dual-process-it 证据进 DELIVERY |

任务按顺序推进。T00 只做文档。T01 只做契约收敛。T02 起才允许改生产代码，且必须先有实施授权。

## 2. 验证要求（实施后）

- 默认配置：S01/S02 仍只产生 `IN_APP` Action；收件与既有 A/M 矩阵不回归。
- `delivery-channels` 含 `FEISHU` 且映射完整：同一通知槽位产生站内信 + 飞书两条 Action，共享同一 `notification_id` 语义。
- 飞书 HTTP 超时/5xx → 可重试或按 06 进入 `UNKNOWN`（已写 `effectStartedAt` 后）；4xx 业务拒绝 → 永久失败。
- 缺映射、缺凭据却启用飞书：启动失败或该 Action 永久失败（以 T01 最终契约为准），不得静默丢弃且声称成功。
- ArchUnit：scenario 不依赖 feishu 包；kernel 无渠道 SDK。
- 不得以真实外网飞书租户作为 CI 门禁。

## 3. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | T01 契约闭合；README 待决问题清空；用户确认范围 |
| READY → IMPLEMENTING | 用户明确授权本阶段实施 |
| IMPLEMENTING → VERIFYING | T02—T04 PASS；创建 DELIVERY 并开始全量证据 |
| VERIFYING → RELEASED | T05 与 DELIVERY 通过人工验收；打 `vyyyyMMdd-P05` 标签 |

未满足门槛不得跳步或预填 PASS。
