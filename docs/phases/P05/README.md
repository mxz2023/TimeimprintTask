# P05 · 飞书 IM 通知渠道（通用 IM 框架首渠）

本阶段在已发布的站内信（`IN_APP`）之上，引入**可配置的多渠道投递框架**，并实现第一个外部 IM 渠道：**飞书**。默认行为不变：只发站内信；通过运行配置把 `FEISHU` 加入投递渠道列表后，才为每个通知槽位额外创建飞书 Action。微信、钉钉、Telegram 等不在本期实现，但渠道键、扩展点与配置形状必须允许后续按同模式接入，并由同一配置列表选择启用哪些渠道。

本阶段对应 [09](../../09-SCENARIO-ROADMAP.md) 的 [C12](../../09-SCENARIO-ROADMAP.md)（多 IM 渠道）之飞书子集，能力项为 [CAP03](../../capabilities/CAP03-notification.md) 的 NOT-05。不取代 NEXT_REVIEW 中的 S03/S04/S05/S14。详细任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P05 |
| 排期身份 | CURRENT |
| 总体状态 | DRAFT |
| 文档基线 | 2.3 |
| 基础发布 | P04；Git 标签 `v20261003-P04` |
| 工程状态 | NOT_STARTED |
| Git发布标签 | 未打；发布时使用 `vyyyyMMdd-P05` |
| 下一动作 | 细化 NOT-05 / 配置与验收至 READY；**禁止编码**，直至用户授权实施 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | 实施进入 VERIFYING 前不得创建 `DELIVERY.md` |

## 2. 已确认产品口径

| 口径 | 结论 |
| --- | --- |
| 本期实现渠道 | 仅飞书 IM；不实现微信、钉钉、Telegram、京 ME、邮件 |
| 通用性 | 投递按稳定 `channelKey` 列表展开；场景不绑定具体 IM；后续渠道以同包内新 Handler + 配置项接入 |
| 默认行为 | `delivery-channels` 默认为仅 `IN_APP`；与 P01—P04 观察行为一致 |
| 开启飞书 | 配置把 `FEISHU` 加入 `delivery-channels`，并提供飞书应用凭据与用户映射后生效 |
| 多渠选择 | 同一配置列表可同时包含多个渠道；本期合法组合为 `[IN_APP]` 或 `[IN_APP, FEISHU]`（`IN_APP` 必须保留；禁止只发飞书而关闭站内信，除非后续阶段另批） |
| 场景范围 | 不新增场景；S01/S02 继续产生通知意图，由平台按启用渠道展开 Action |
| 身份 | 仍为本地固定 Actor；平台 `recipientId` → 飞书 `open_id` 由配置映射表完成 |

## 3. 目标与范围

P05 必须完成（进入 READY 后写入正式契约，授权后编码）：

1. **渠道展开**：通知模板 Action 按「最终接收人 × 启用渠道」展开为独立 Action Job；`actionKey` 规范输入含 `channelKey`；各渠道失败相互隔离。
2. **通用渠道契约**：在 `capability-notification` 内定义稳定 `channelKey`、执行模式、超时、用户地址解析、错误分类与结果物化边界；场景模块不得依赖飞书 SDK 或具体渠道 Handler。
3. **飞书 Handler**：`EXTERNAL` 模式；调用前写 `effectStartedAt`；事务外调飞书开放接口；记录受理号；可重试 / 永久失败 / `UNKNOWN` 分类明确；关闭 SDK 隐藏重试或纳入超时预算。
4. **配置**：投递渠道列表、飞书凭据、超时、接收人映射；缺映射或未启用时不得假装发送；仓库不提交真实密钥。
5. **验收**：默认仅站内信回归；开启飞书后双渠并存；飞书失败不影响已提交站内信与 S01 终态；WireMock/夹具证明 EXTERNAL 协议，不宣称生产飞书账号已验收。

## 4. 允许与禁止

允许（READY 且授权后）：

- `timeimprint-task-service-capability-notification` 内新增渠道包（如 `im` / `feishu`）、渠道展开与飞书 `ActionHandler`。
- 将接收人展开改为「接收人 × 渠道」；场景模板改为渠道中立（去掉对 `InAppNotificationHandler` 的硬依赖）。
- [03](../../03-INTEGRATION-CONTRACTS.md) 增加通知渠道配置键；[06](../../06-SCHEDULING.md) / [CAP03](../../capabilities/CAP03-notification.md) / [07](../../07-ACCEPTANCE.md) 补飞书与多渠规则；术语表补充 `channelKey` / `FEISHU` 等。
- 测试：单元、契约、真库 IT（Mock 飞书 HTTP）、必要的双进程回归。

禁止：

- 实现微信、钉钉、Telegram、京 ME、邮件或其他 NOT-05 未批准渠道的生产 Handler。
- 修改 kernel 业务语义、公共表 DDL、公开 HTTP 字段形状（E01—E13 收件模型仍属站内信）。
- 在业务状态事务中调用飞书；把飞书 SDK 引入 kernel / scenario 模块。
- 默认开启飞书；用空 Bean、假回执或仓库内真实凭据宣称完成。
- 改写已 RELEASED 的 P01—P04；把 S03 及以后拉进本期。
- 未授权实施时编码或创建 `DELIVERY.md`。

## 5. 影响与不变量

| 类别 | 判断 |
| --- | --- |
| 变化类型 | 兼容能力扩展（08 §5），非核心模型变化；不改公共表与稳定 SPI 签名语义 |
| 场景三维 | S01/S02 保持 RELEASED / VERIFIED；契约仅补充「渠道由投递配置展开」 |
| 能力三维 | NOT-01—NOT-03 不变；NOT-05（飞书子集）进入 DRAFT → 目标 READY → 实施后 VERIFIED |
| C12 | 排期改为 P05；本期只验证飞书；其余 IM 仍 OUTLINE |
| INV | 重点 [INV-05](../../02-AI-CODING-GUIDE.md)（声明式迁移）、[INV-08](../../02-AI-CODING-GUIDE.md)（接入不污染内核）、[INV-09](../../02-AI-CODING-GUIDE.md)（模块克制，渠道先包内隔离） |

## 6. 编码前必须决定（DRAFT 待收敛）

以下条目须在转 READY 前写入 CAP03 / 03 / 07，不得留到编码时猜测：

1. 飞书调用的具体 OpenAPI（例如机器人单聊发消息）路径、请求字段与成功受理号字段名。
2. 文本消息格式：纯文本还是富文本；标题与正文如何拼进飞书消息。
3. `delivery-channels` 是否允许不含 `IN_APP`（当前产品口径：**不允许**；若改变须用户重批）。
4. 缺 `recipient-map` 时：该飞书 Action 记 `PERMANENT_FAILURE`，还是创建前直接跳过（倾向：创建 Action 并永久失败，便于投递汇总可见）。
5. 测试夹具用 WireMock 还是进程内 Fake HTTP；禁止依赖外网真实飞书租户作为门禁。

本文件在 DRAFT 期间可随契约细化修订；转为 READY 后禁止扩大范围；RELEASED 后冻结。
