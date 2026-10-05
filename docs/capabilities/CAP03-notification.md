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
| NOT-05 | 可配置多IM渠道框架；P05仅飞书 | P05 | DRAFT | NOT_STARTED | C12；阶段 [P05](../phases/P05/README.md) |
| NOT-06 | 委托和多接收人增强 | BACKLOG | OUTLINE | NOT_STARTED | C16，主要协作规则归collaboration |
| NOT-07 | 通知保留、删除和合规清理 | BACKLOG | OUTLINE | NOT_STARTED | C18 |

## P01正式范围

场景在TransitionPlan中声明通知意图，notification在同一业务事务保存`tt_notification`并按最终接收人和渠道创建独立Action Job。P01只装配`IN_APP`，由LOCAL_TRANSACTIONAL ActionHandler在结果事务中原子写入`tt_inbox`、闭合Attempt并标记Action成功。P05 起投递渠道由运行配置的`delivery-channels`决定，默认仍仅`IN_APP`；详见下文 NOT-05。

没有显式RECIPIENT时接收人回退到OWNER；存在显式RECIPIENT时不自动追加OWNER。最终接收人去重后最多10人。通知接收、渠道受理、渠道送达、用户已读和场景完成是不同事实，notification不能替场景修改业务终态。

Action key必须稳定、定长且不泄露接收人；重试只增加Attempt，不增加业务通知槽位。过期、取消、DEAD、UNKNOWN和终态Action资格严格服从06；S01的24小时有效期和S02的槽位/有效期由各场景契约拥有，不能成为所有通知的全局默认值。

## NOT-05 DRAFT（P05：通用 IM 框架 + 飞书）

### 目标与例子

- 场景只声明渠道中立的通知意图（标题、正文、purpose、槽位、接收人待展开）；平台按配置的投递渠道列表，为每个最终接收人×渠道创建独立 Action Job。
- 默认 `delivery-channels = [IN_APP]`，行为与 P01—P04 一致。
- 配置为 `[IN_APP, FEISHU]` 且飞书凭据与映射齐全时，同一通知槽位同时产生站内信 Action 与飞书 IM Action；共享同一条 `tt_notification` 内容事实。
- 飞书失败不能回滚已提交的站内信，也不能改变 S01「提醒已触发」或 S02 实例业务状态。

### 稳定渠道键

| 渠道键 | 执行模式 | P05 |
| --- | --- | --- |
| `IN_APP` | `LOCAL_TRANSACTIONAL` | 已有；必须保留在默认投递列表 |
| `FEISHU` | `EXTERNAL` | 本期实现 |
| `WECHAT` / `DINGTALK` / `TELEGRAM` 等 | `EXTERNAL` | 仅预留键名与扩展点；禁止本期实现 |

后续 IM 接入时：在 `capability-notification` 内新增渠道包与 Handler，把渠道键加入允许枚举与配置校验，并写入投递列表即可；不得改 kernel，不得让场景依赖具体渠道 SDK。

### 配置选择（运行时）

- 投递列表由运行配置拥有（键名在 [03](../03-INTEGRATION-CONTRACTS.md) 于 T01 定稿），不是场景 `scenarioConfig` 字段；P05 不改 S01/S02 `schemaVersion`。
- 列表顺序不表示优先级；各渠道并行独立 Action。
- P05 合法列表：`[IN_APP]` 或 `[IN_APP, FEISHU]`。缺少 `IN_APP` 的配置拒绝启动。
- `FEISHU` 出现在列表中时，必须同时具备应用凭据与「平台 recipientId → 飞书 open_id」映射配置；否则启动失败或该 Action 永久失败（T01 二选一并写入 03/07，倾向创建 Action 后 `PERMANENT_FAILURE` 以便投递汇总可见）。

### 展开与幂等

- Action key 规范输入至少含：instanceId、purpose、slotIndex、actionGeneration、recipient、**channelKey**。
- Handler 键与渠道对应：`in_app_notification`（已有）、`feishu_im_notification`（P05）；未来渠道各自稳定 handlerKey，不得复用。
- 业务槽位不因渠道数增加；技术重试只增加 Attempt。

### 飞书渠道边界（编码前须定稿）

- 调用必须在事务外；先提交 `effectStartedAt`。
- 每次调用携带可关联平台 `actionKey` 的幂等信息（或飞书支持的幂等头）；记录受理号到 Attempt/结果。
- 区分：可重试错误、永久错误、已开始但结果不明的 `UNKNOWN`。
- SDK/HTTP 客户端隐藏重试必须关闭或纳入 `timeoutSeconds` 预算。
- 飞书不写入 `tt_inbox`；站内已读与飞书送达是不同事实。
- 仓库与测试夹具不得提交真实 app_secret；CI 只用 Mock/Fake HTTP。

### 明确不在 NOT-05 / P05

- 微信、钉钉、Telegram、京 ME、邮件的生产实现。
- 按任务定义覆盖投递渠道（若需要，另立契约与 schemaVersion）。
- 飞书异步送达回调、已读回执、互动卡片按钮办结任务。
- C09 节制/静默/降级。

### 待决（转 READY 前关闭）

见 [P05 README §6](../phases/P05/README.md)。未关闭前不得编码。

## 其他未来规划边界

- C09必须区分业务提醒节制与技术重试，并定义免打扰、允许时段、频控、汇总及渠道降级顺序。
- C12 在 P05 只落地飞书子集；其余 IM 仍按上表预留键接入，不得用空 Bean 或假回执宣称完成。
- C16的委托授权和“谁有权完成”主要归collaboration；notification只负责接收人投影与投递，不能从收到通知推导办理权限。
- C18必须定义保留期限、用户主动删除、合规清理、审计痕迹和删除后的未读计数；P01“不随业务操作自动删除”不等于永久保留。

OUTLINE 能力项不能创建空渠道Bean、假回执或真实外部凭据配置。NOT-05 在 DRAFT 期间只允许文档细化。

## 数据、失败与演进

notification拥有通知和收件能力表及其迁移；公共Action表仍由平台拥有。网络渠道必须声明EXTERNAL并在事务外调用，IN_APP保持LOCAL_TRANSACTIONAL。新增渠道优先作为notification内部实现，只有独立发布、重大SDK冲突、安全隔离或独立所有权成立时才拆模块。

通知schema和渠道payload必须版本化。仍有未终结Action引用的旧版本必须可读；否则应用不得就绪。已经产生的通知、收件和Attempt不能因场景修改或渠道升级被覆盖。
