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
| NOT-05 | 可配置多IM渠道 + 飞书整体接入（出站卡片与入站命令） | P05 | DRAFT | NOT_STARTED | C12；阶段 [P05](../phases/P05/README.md) |
| NOT-06 | 委托和多接收人增强 | BACKLOG | OUTLINE | NOT_STARTED | C16，主要协作规则归collaboration |
| NOT-07 | 通知保留、删除和合规清理 | BACKLOG | OUTLINE | NOT_STARTED | C18 |

## P01正式范围

场景在TransitionPlan中声明通知意图，notification在同一业务事务保存`tt_notification`并按最终接收人和渠道创建独立Action Job。P01只装配`IN_APP`，由LOCAL_TRANSACTIONAL ActionHandler在结果事务中原子写入`tt_inbox`、闭合Attempt并标记Action成功。P05 起投递渠道由运行配置的`delivery-channels`决定，默认仍仅`IN_APP`；详见下文 NOT-05。

没有显式RECIPIENT时接收人回退到OWNER；存在显式RECIPIENT时不自动追加OWNER。最终接收人去重后最多10人。通知接收、渠道受理、渠道送达、用户已读和场景完成是不同事实，notification不能替场景修改业务终态。

Action key必须稳定、定长且不泄露接收人；重试只增加Attempt，不增加业务通知槽位。过期、取消、DEAD、UNKNOWN和终态Action资格严格服从06；S01的24小时有效期和S02的槽位/有效期由各场景契约拥有，不能成为所有通知的全局默认值。

## NOT-05 DRAFT（P05：通用 IM 框架 + 飞书整体接入）

### 目标与例子

- 场景只声明渠道中立的通知意图；平台按投递渠道列表展开「接收人 × 渠道」Action Job。
- 默认 `delivery-channels = [IN_APP]`，与 P01—P04 一致。
- 配置 `[IN_APP, FEISHU]` 且凭据/映射齐全时：同槽位站内信 + 飞书**交互卡片**；共享 `tt_notification` 内容事实。
- 用户在飞书上点卡片按钮（或可选文字回复）后，外层适配把动作映射为平台已有命令（S02：`complete` / `skip` / `snooze` 等），走与 HTTP 相同的命令管道；**不能**从「收到飞书消息」直接改公共表。
- 飞书出站失败不能回滚站内信，也不能单方面改写 S01 终态；入站命令成功才推进业务状态。

### 稳定渠道键

| 渠道键 | 执行模式 | P05 |
| --- | --- | --- |
| `IN_APP` | `LOCAL_TRANSACTIONAL` | 已有；必须保留在默认投递列表 |
| `FEISHU` | `EXTERNAL` | 本期实现出站；并启用同应用入站命令桥 |
| `WECHAT` / `DINGTALK` / `TELEGRAM` 等 | `EXTERNAL` | 仅预留键名与「出站/入站」扩展点；禁止本期实现 |

后续 IM：同模块内新增渠道包（出站 Handler + 可选入站命令桥），加入允许枚举与配置；不得改 kernel；场景不得依赖具体渠道 SDK。

### 出站（消息）

- 飞书主路径：`POST /open-apis/im/v1/messages?receive_id_type=open_id`，`msg_type=interactive`（S02 含完成/跳过/稍后提醒按钮；S01 仅展示）。
- `tenant_access_token` 取自 app_id/app_secret；调用前写 `effectStartedAt`；`uuid`（≤50）由平台 `actionKey` 导出；受理号记 `message_id`。
- 错误分类：可重试 / 永久失败 / `UNKNOWN`；关闭 SDK 隐藏重试或纳入 `timeoutSeconds`。
- 飞书不写入 `tt_inbox`。

### 入站（同步命令）

- **主路径（P05 交付）**：订阅回调 `card.action.trigger`；须在 **3 秒内**响应（toast 和/或更新卡片）。官方说明见[卡片回传交互](https://open.feishu.cn/document/feishu-cards/card-callback-communication?lang=zh-CN)。
- S02 卡片固定三按钮，映射：
  - 完成 → `complete`
  - 跳过 → `skip`（原因固定为「飞书卡片跳过」，1—500 码点合规）
  - 稍后提醒 → `snooze`，`snoozeUntil = 业务时间 T + 1 小时`（无选择器）；仍受 S02 既有 snooze 约束
- S01 卡片无按钮；不接受对 S01 实例的飞书命令回调。
- 从 `action.value` 解析 `commandKey`、`instanceId`、`definitionId`、发信时 `revision`（作 `expectedRevision`）；用 `operator.open_id` 映射平台 Actor；调用 application 实例命令服务。
- **文字回复**：可经 `im.message.receive_v1` 扩展，**P05 不实现、不验收**。
- 验签、加密、Verification Token 留在 web/gateway；重复 `event_id` 幂等。
- 入站桥是外层适配，不是第二套业务状态机；权限与终态规则仍由场景 + Policy 拥有。

### 配置选择（运行时）

- 投递列表与飞书凭据、映射、回调验签材料由运行配置拥有（键名于 T01 写入 03）；不是场景 `scenarioConfig`；P05 不改 S01/S02 `schemaVersion`。
- P05 合法列表：`[IN_APP]` 或 `[IN_APP, FEISHU]`。缺 `IN_APP` 拒绝启动。
- 启用 `FEISHU` 时须同时具备出站凭据/映射与入站回调配置；缺映射倾向：创建出站 Action 后 `PERMANENT_FAILURE`。

### 展开与幂等

- Action key 至少含：instanceId、purpose、slotIndex、actionGeneration、recipient、**channelKey**。
- Handler：`in_app_notification`、`feishu_im_notification`；未来渠道各自稳定键。
- 业务槽位不因渠道数增加；技术重试只增加 Attempt。
- 入站命令另用平台 `requestId`（可由 `event_id` 派生）保证与 HTTP 命令同一幂等语义。

### 明确不在本期

- 微信、钉钉、Telegram、京 ME、邮件生产实现。
- 按任务定义覆盖投递渠道（须另立契约）。
- 飞书异步「已读/送达」回执产品化（与命令回调不同）。
- C09 节制/静默/降级。

### 待决

产品切片与按钮集合已确认，见[P05 README §2](../phases/P05/README.md)。其余工程默认见[P05 README §6](../phases/P05/README.md)；用户认可后由 T01 写入 03/07 并转 READY。未转 READY 前不得编码。

## 其他未来规划边界

- C09必须区分业务提醒节制与技术重试，并定义免打扰、允许时段、频控、汇总及渠道降级顺序。
- C12 在 P05 只落地飞书子集（含入站命令桥）；其余 IM 仍按上表预留键接入，不得用空 Bean 或假回执宣称完成。
- C16的委托授权和“谁有权完成”主要归collaboration；notification只负责接收人投影与投递，不能从收到通知推导办理权限；飞书入站必须先完成身份映射再走 Policy。
- C18必须定义保留期限、用户主动删除、合规清理、审计痕迹和删除后的未读计数；P01“不随业务操作自动删除”不等于永久保留。

OUTLINE 能力项不能创建空渠道Bean、假回执或真实外部凭据配置。NOT-05 在 DRAFT 期间只允许文档细化。

## 数据、失败与演进

notification拥有通知和收件能力表及其迁移；公共Action表仍由平台拥有。网络渠道必须声明EXTERNAL并在事务外调用，IN_APP保持LOCAL_TRANSACTIONAL。新增渠道优先作为notification内部实现，只有独立发布、重大SDK冲突、安全隔离或独立所有权成立时才拆模块。

通知schema和渠道payload必须版本化。仍有未终结Action引用的旧版本必须可读；否则应用不得就绪。已经产生的通知、收件和Attempt不能因场景修改或渠道升级被覆盖。
