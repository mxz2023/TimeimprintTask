# CAP03 · notification · 通知与收件能力

> 本文是`capabilityKey=notification`的永久能力入口。业务触发由场景决定，HTTP收件模型以[04](../04-API.md)为准，数据以[05](../05-DATABASE.md)为准，执行协议以[06](../06-SCHEDULING.md)为准。

| 项目 | 值 |
| --- | --- |
| 能力编号 | CAP03 |
| 目标模块 | `timeimprint-task-service-capability-notification` |
| planningPosition | P01 |
| contractStatus | RELEASED |
| implementationStatus | VERIFIED |
| P01使用场景 | [S01](../scenarios/S01-reminder.md)、[S02](../scenarios/S02-recurring-todo.md) |
| DELIVERY证据 | [P01 DELIVERY](../phases/P01/DELIVERY.md)；Git 标签 `v20260915-P01` |

## 能力项

| 能力项 | 内容 | 排期位置 | 契约状态 | 实现状态 | 关联 |
| --- | --- | --- | --- | --- | --- |
| NOT-01 | 版本化通知意图与接收人投影 | P01 | RELEASED | VERIFIED | S01、S02 |
| NOT-02 | IN_APP渠道和站内收件 | P01 | RELEASED | VERIFIED | S01、S02 |
| NOT-03 | Action Job、Attempt、失效和受控重试映射 | P01 | RELEASED | VERIFIED | S01、S02 |
| NOT-04 | 提醒节制、静默、频控、汇总与降级 | BACKLOG | OUTLINE | NOT_STARTED | C09 |
| NOT-05 | 可配置多IM渠道 + 飞书整体接入（出站卡片与入站命令） | P05 | READY_FOR_IMPLEMENTATION | NOT_STARTED | C12；阶段 [P05](../phases/P05/README.md) |
| NOT-06 | 委托和多接收人增强 | BACKLOG | OUTLINE | NOT_STARTED | C16，主要协作规则归collaboration |
| NOT-07 | 通知保留、删除和合规清理 | BACKLOG | OUTLINE | NOT_STARTED | C18 |

## P01正式范围

场景在TransitionPlan中声明通知意图，notification在同一业务事务保存`tt_notification`并按最终接收人和渠道创建独立Action Job。P01只装配`IN_APP`，由LOCAL_TRANSACTIONAL ActionHandler在结果事务中原子写入`tt_inbox`、闭合Attempt并标记Action成功。P05 起投递渠道由运行配置的`delivery-channels`决定，默认仍仅`IN_APP`；详见下文 NOT-05。

没有显式RECIPIENT时接收人回退到OWNER；存在显式RECIPIENT时不自动追加OWNER。最终接收人去重后最多10人。通知接收、渠道受理、渠道送达、用户已读和场景完成是不同事实，notification不能替场景修改业务终态。

Action key必须稳定、定长且不泄露接收人；重试只增加Attempt，不增加业务通知槽位。过期、取消、DEAD、UNKNOWN和终态Action资格严格服从06；S01的24小时有效期和S02的槽位/有效期由各场景契约拥有，不能成为所有通知的全局默认值。

## NOT-05（P05：通用 IM 框架 + 飞书整体接入）

> 契约状态：`READY_FOR_IMPLEMENTATION`。规则以本文与[03 §3.1](../03-INTEGRATION-CONTRACTS.md)、[06 §8](../06-SCHEDULING.md)、[07 F01—F10](../07-ACCEPTANCE.md)、[P05](../phases/P05/README.md)为准。

### 目标与例子

- 场景只声明渠道中立的通知意图；平台按投递渠道列表展开「接收人 × 渠道」Action Job。
- 默认 `delivery-channels = [IN_APP]`，与 P01—P04 一致。
- 配置 `[IN_APP, FEISHU]` 且凭据/映射齐全时：同槽位站内信 + 飞书**交互卡片**；共享 `tt_notification` 内容事实。
- 用户在飞书上点卡片按钮后，外层适配把动作映射为平台已有命令（S02：`complete` / `skip` / `snooze`），走与 HTTP 相同的命令管道；**不能**从「收到飞书消息」直接改公共表。
- 飞书出站失败不能回滚站内信，也不能单方面改写 S01 终态；入站命令成功才推进业务状态。

### 稳定渠道键

| 渠道键 | 执行模式 | P05 |
| --- | --- | --- |
| `IN_APP` | `LOCAL_TRANSACTIONAL` | 已有；必须保留在默认投递列表 |
| `FEISHU` | `EXTERNAL` | 本期实现出站；并启用同应用入站命令桥 |
| `WECHAT` / `DINGTALK` / `TELEGRAM` 等 | `EXTERNAL` | 仅预留键名与「出站/入站」扩展点；禁止本期实现 |

后续 IM：在 **`timeimprint-task-adapter`** 内新增渠道包（如 `wechat`）封装第三方 SDK；`capability-notification` 只依赖 adapter 使用其客户端，场景不得依赖 adapter 或具体 SDK。

### 出站（消息）

- 飞书主路径：经 **`timeimprint-task-adapter`** 调用 `POST /open-apis/im/v1/messages?receive_id_type=open_id`，`msg_type=interactive`（S02 含完成/跳过/稍后提醒按钮；S01 仅展示）。
- `tenant_access_token`、HTTP 超时与 SDK 隐藏重试策略由 adapter 拥有；notification 只传业务载荷并解释结果。
- 调用前写 `effectStartedAt`；`uuid`（≤50）由平台 `actionKey` 导出；受理号记 `message_id`。
- 错误分类：可重试 / 永久失败 / `UNKNOWN`。
- 飞书不写入 `tt_inbox`。缺`recipient-map`时仍创建 Action，执行`PERMANENT_FAILURE`。

### 入站（同步命令）

- **主路径（P05 交付）**：`POST /callbacks/v1/feishu/card-action`；订阅`card.action.trigger`；**3 秒内**响应。
- S02：完成→`complete`；跳过→`skip`（原因「飞书卡片跳过」）；稍后提醒→`snooze`且`snoozeUntil=T+1h`。
- S01：无按钮；拒绝对 S01 的飞书命令。
- `action.value`含`commandKey`、`instanceId`、`definitionId`、发信时`revision`（作`expectedRevision`）；冲突 toast，不静默覆盖。
- **文字回复**（`im.message.receive_v1`）：契约保留，**P05 不实现、不验收**。
- 验签在 adapter；路由在 web；`event_id`派生`requestId`幂等。

### 配置

键名与约束见[03 §3.1](../03-INTEGRATION-CONTRACTS.md)。投递列表不是场景`scenarioConfig`；不改 S01/S02 `schemaVersion`。

### 展开与幂等

- Action key 至少含：instanceId、purpose、slotIndex、actionGeneration、recipient、**channelKey**。
- Handler：`in_app_notification`、`feishu_im_notification`。
- 业务槽位不因渠道数增加；技术重试只增加 Attempt。

### 明确不在本期

- 微信、钉钉、Telegram、京 ME、邮件生产实现；文字回复入站；按定义覆盖投递渠道；飞书已读/送达回执产品化；C09。

## 其他未来规划边界

- C09必须区分业务提醒节制与技术重试，并定义免打扰、允许时段、频控、汇总及渠道降级顺序。
- C12 在 P05 只落地飞书子集（含入站命令桥）；其余 IM 仍按上表预留键接入，不得用空 Bean 或假回执宣称完成。
- C16的委托授权和“谁有权完成”主要归collaboration；notification只负责接收人投影与投递，不能从收到通知推导办理权限；飞书入站必须先完成身份映射再走 Policy。
- C18必须定义保留期限、用户主动删除、合规清理、审计痕迹和删除后的未读计数；P01“不随业务操作自动删除”不等于永久保留。

OUTLINE 能力项不能创建空渠道Bean、假回执或真实外部凭据配置。

## 数据、失败与演进

notification拥有通知和收件能力表及其迁移；公共Action表仍由平台拥有。网络渠道必须声明EXTERNAL并在事务外调用，IN_APP保持LOCAL_TRANSACTIONAL。第三方官方 SDK 与底层 HTTP/验签封装优先落在通用模块 `timeimprint-task-adapter`；notification（及 web 回调）依赖 adapter，不各自引入 SDK。只有独立发布节奏或所有权要求时再拆更细适配器模块。

通知schema和渠道payload必须版本化。仍有未终结Action引用的旧版本必须可读；否则应用不得就绪。已经产生的通知、收件和Attempt不能因场景修改或渠道升级被覆盖。
