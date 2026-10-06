# P05 · 飞书整体接入（出站通知 + 入站命令）

本阶段把**飞书**作为第一个外部 IM 渠道**整体接入**：不仅投递通知，还要在消息触达后，用飞书卡片按钮（及可选文字回复）驱动平台已有命令（如完成、跳过、稍后提醒）。架构上拆成两半、同一阶段契约：

| 半边 | 职责 | 主要归属 |
| --- | --- | --- |
| 第三方 SDK | 飞书（及未来微信/钉钉等）客户端、令牌、验签与 HTTP 调用封装 | 新建通用模块 `timeimprint-task-adapter` |
| 出站消息 | 可配置多渠投递；调用 adapter 发交互卡片 | [CAP03](../../capabilities/CAP03-notification.md) NOT-05（`capability-notification` 依赖 adapter） |
| 入站命令 | webhook 入口 → 用 adapter 验签 → 映射平台命令 | `web`/`gateway` + application 命令桥；验签能力来自 adapter |

默认行为不变：只发站内信。配置把 `FEISHU` 加入投递渠道后，才出站发飞书；入站 webhook 仅在飞书接入启用时装配。微信、钉钉、Telegram **本期不实现**，但必须落在同一 `adapter` 模块的包扩展点上，供后续接入。

### 模块落点（已确认）

| 模块 | P05 职责 |
| --- | --- |
| **`timeimprint-task-adapter`（新建）** | 通用第三方 SDK 宿主：本期仅 `feishu` 包（token、发消息、回调验签辅助）；未来 `wechat`/`dingtalk`/`telegram` 等同级包；**不含**业务命令与 TransitionPlan |
| `timeimprint-task-service-capability-notification` | 依赖 adapter；渠道展开、`feishu_im_notification` Handler、卡片业务载荷（按钮与 value）；不直接声明飞书官方 SDK 坐标（由 adapter 拥有） |
| `timeimprint-task-service-application` | 接收人×渠道展开；入站命令桥调用现有实例命令 |
| `timeimprint-task-web` / `gateway` | 回调 HTTP 入口与编排；验签调用 adapter |
| `timeimprint-task-boot-loader` | 装配 adapter + notification；配置键 |
| `timeimprint-task-service-runtime` | 沿用 EXTERNAL 执行路径 |

禁止：`kernel`、`scenario-basic` 依赖 `adapter`；在 notification 内另引一份飞书 SDK。

对应 [09](../../09-SCENARIO-ROADMAP.md) 的 [C12](../../09-SCENARIO-ROADMAP.md) 飞书子集。不取代 NEXT_REVIEW 的 S03/S04/S05/S14。任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

官方依据（编码前定稿时再锁字段）：

- 发消息：[发送消息](https://open.feishu.cn/document/server-docs/im-v1/message/create?lang=zh-CN)（`im/v1/messages`，`msg_type=interactive`）
- 令牌：[tenant_access_token](https://open.feishu.cn/document/server-docs/authentication-management/access-token/tenant_access_token_internal)
- 卡片回传：[card.action.trigger](https://open.feishu.cn/document/feishu-cards/card-callback-communication?lang=zh-CN)（须 **3 秒内**响应）
- 文字回复（可选辅路径）：[im.message.receive_v1](https://open.feishu.cn/document/server-docs/im-v1/message/events/receive?lang=zh-CN)

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P05 |
| 排期身份 | CURRENT |
| 总体状态 | IMPLEMENTING |
| 文档基线 | 2.3 |
| 基础发布 | P04；Git 标签 `v20261003-P04` |
| baselineGitRef | `9d9dc87f494a4738cf948b4fc38f14b947d456a2`（T01 READY 提交；授权实施起点） |
| 工程状态 | IN_PROGRESS |
| Git发布标签 | 未打；发布时使用 `vyyyyMMdd-P05` |
| 下一动作 | [T02](IMPLEMENTATION.md) PASS；继续 [T03](IMPLEMENTATION.md)（出站卡片 Handler） |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | 进入 VERIFYING 前不得创建 `DELIVERY.md` |

## 2. 已确认产品口径

| 口径 | 结论 |
| --- | --- |
| 接入形态 | **整体接入**：出站消息 + 入站命令同步；不是「只发不收」 |
| 本期实现渠道 | 仅飞书；不实现微信、钉钉、Telegram、京 ME、邮件 |
| 交付切片 | **同期交付**出站交互卡片 + 入站 `card.action.trigger` 按钮；文字回复 `im.message.receive_v1` **只保留契约、P05 不实现** |
| 通用性 | 出站按 `channelKey` 列表展开；入站按「渠道命令桥」映射到平台 `commandKey`；第三方 SDK 统一进 `timeimprint-task-adapter`，notification 只依赖 adapter 使用飞书 |
| 模块策略 | **新建** `timeimprint-task-adapter`（P05 起模块数 13→14）；飞书官方 SDK 与 HTTP 封装只属于 adapter；后续其他三方 SDK 先入该模块再被业务能力依赖 |
| 默认行为 | `delivery-channels` 默认仅 `IN_APP`；与 P01—P04 一致 |
| 开启飞书 | 配置加入 `FEISHU` + 凭据/映射/回调验签后，出站与入站同时可用 |
| 多渠选择 | 合法组合 `[IN_APP]` 或 `[IN_APP, FEISHU]`；`IN_APP` 必须保留 |
| S02 卡片按钮 | 固定三个：**完成**（`complete`）、**跳过**（`skip`）、**稍后提醒**（`snooze`） |
| S02 稍后提醒 | 无时间选择器；点击时取业务时间 `T + 1 小时` 作为 `snoozeUntil`；仍须满足既有 snooze 规则（`T < snoozeUntil < expiresAt`、次数上限等），否则 toast 拒绝 |
| S01 卡片 | **仅展示**标题/正文，**无按钮**（S01 无实例命令） |
| 场景范围 | 不新增场景；S01/S02 通知意图由配置展开；入站命令复用现有实例命令语义 |
| 身份 | 本地固定 Actor；`recipientId` ↔ 飞书 `open_id` 配置映射；入站以 `operator.open_id` 反查平台主体 |

## 3. 目标与范围

### 3.1 出站消息

1. 接收人 × 启用渠道展开 Action；`actionKey` 含 `channelKey`；渠道失败隔离。
2. 飞书 Handler 为 `EXTERNAL`：先 `effectStartedAt`，再调 `im/v1/messages`；`uuid` 幂等（≤50）；受理号 `message_id`。
3. **消息形态为交互卡片**（`msg_type=interactive`）：展示标题/正文；S02 挂上述三按钮；S01 无按钮。
4. 配置：投递列表、凭据、超时、映射；仓库无真实密钥。

### 3.2 入站命令

1. 对外 HTTP 回调入口（开发者服务器 webhook；验签/加密按飞书规范）；**不得**把飞书 SDK 放进 kernel。
2. **本阶段主路径**：订阅 `card.action.trigger`；从 `action.value` 解析命令；调用与 HTTP 相同的应用层命令管道；**3 秒内**返回 toast / 更新卡片。
3. **文字回复**：契约注明未来可用 `im.message.receive_v1`，**P05 不编码、不验收**。
4. 入站成功只表示平台命令已提交；与站内信已读、飞书送达是不同事实。
5. 重复回调必须幂等（`event_id` / 平台 `requestId` 派生）。

### 3.3 验收边界

- 默认仅站内信回归；开启飞书后出站卡片 + 入站模拟回调可驱动 S02 完成/跳过/稍后+1h。
- 飞书出站失败不回滚站内信、不改写 S01 终态。
- CI 用 Mock HTTP / 伪造回调，不依赖真实飞书租户。

## 4. 允许与禁止

允许（READY 且授权后）：

- **新建** Maven 模块 `timeimprint-task-adapter`（及根 POM / Enforcer / ArchUnit / boot-loader 装配）；期内只实现 `feishu` 包。
- `capability-notification` 增加对 adapter 的依赖；渠道展开、飞书 Handler、卡片业务载荷。
- `web`/`gateway` 飞书回调 Controller + 编排；验签走 adapter；application 命令桥调用现有实例命令。
- T01 起同步修订 [02](../../02-AI-CODING-GUIDE.md) 模块表（13→14）与「渠道可抽至 adapter」说明；03 配置键；06/CAP03/07/术语表；S01/S02 补充飞书卡片与按钮说明。
- 测试：单元、契约、真库 IT（Mock 飞书发信与回调）、必要双进程回归。

禁止：

- 在 `capability-notification`（或 web）中直接引入飞书官方 SDK 坐标，绕过 adapter。
- 其他 IM 的生产实现；在业务状态事务中调飞书网络。
- 飞书 SDK / webhook 进入 kernel 或 scenario 模块；adapter 依赖 kernel/scenario/application。
- 默认开启飞书；仓库真实密钥；空 Bean 假回执。
- 实现文字回复入站、日期时间选择器 snooze、或非约定按钮。
- 改写已 RELEASED 的 P01—P04；把 S03+ 拉进本期。
- 未授权编码或预建 `DELIVERY.md`。
- 用飞书回调绕过 Policy、revision、幂等或锁序。

## 5. 影响与不变量

| 类别 | 判断 |
| --- | --- |
| 变化类型 | 兼容能力扩展 + 新增 adapter 模块；[02](../../02-AI-CODING-GUIDE.md) 模块表已改为 14；不改公共表与稳定 SPI 签名语义 |
| 场景 | S01/S02 RELEASED/VERIFIED；已补充飞书卡片与按钮语义 |
| 能力 | NOT-05 `READY_FOR_IMPLEMENTATION`；C12 飞书子集 |
| INV | [INV-05](../../02-AI-CODING-GUIDE.md)、[INV-08](../../02-AI-CODING-GUIDE.md)、[INV-09](../../02-AI-CODING-GUIDE.md)（含 adapter 例外） |

## 6. 已锁定工程默认（T01 已写入正式契约）

下列项已于 T01 落入 [02](../../02-AI-CODING-GUIDE.md)、[03 §3.1](../../03-INTEGRATION-CONTRACTS.md)、[06 §8](../../06-SCHEDULING.md)、[07 F01—F10](../../07-ACCEPTANCE.md)、[CAP03 NOT-05](../../capabilities/CAP03-notification.md)；READY 后禁止扩大范围。

| 项 | 锁定值 |
| --- | --- |
| 出站 API | `POST /open-apis/im/v1/messages?receive_id_type=open_id`；`msg_type=interactive`；`uuid`←`actionKey` 摘要（≤50）；受理号 `message_id` |
| `action.value` | `commandKey`、`instanceId`、`definitionId`、发信时 `revision`；snooze 点击时算 `T+1h` |
| revision 冲突 | 卡片 revision 作 `expectedRevision`；冲突 toast |
| 跳过原因 | 「飞书卡片跳过」 |
| 缺映射 | 创建 Action → `PERMANENT_FAILURE` |
| 回调 | `POST /callbacks/v1/feishu/card-action`；HTTP webhook；验签在 adapter |
| 测试 | WireMock + 伪造 `card.action.trigger`；[F01](../../07-ACCEPTANCE.md)—[F10](../../07-ACCEPTANCE.md) |
| 模块名 | `timeimprint-task-adapter` |

本文件在 READY 期间仅允许修正笔误；扩大范围须用户重批。RELEASED 后冻结。
