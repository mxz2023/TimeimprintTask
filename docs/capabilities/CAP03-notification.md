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
| DELIVERY证据 | [P01 DELIVERY](../phases/P01/DELIVERY.md)；Git 标签 `p01` |

## 能力项

| 能力项 | 内容 | 排期位置 | 契约状态 | 实现状态 | 关联 |
| --- | --- | --- | --- | --- | --- |
| NOT-01 | 版本化通知意图与接收人投影 | P01 | RELEASED | VERIFIED | S01、S02 |
| NOT-02 | IN_APP渠道和站内收件 | P01 | RELEASED | VERIFIED | S01、S02 |
| NOT-03 | Action Job、Attempt、失效和受控重试映射 | P01 | RELEASED | VERIFIED | S01、S02 |
| NOT-04 | 提醒节制、静默、频控、汇总与降级 | BACKLOG | OUTLINE | NOT_STARTED | C09 |
| NOT-05 | 飞书、京ME、邮件等多IM渠道 | BACKLOG | OUTLINE | NOT_STARTED | C12 |
| NOT-06 | 委托和多接收人增强 | BACKLOG | OUTLINE | NOT_STARTED | C16，主要协作规则归collaboration |
| NOT-07 | 通知保留、删除和合规清理 | BACKLOG | OUTLINE | NOT_STARTED | C18 |

## P01正式范围

场景在TransitionPlan中声明通知意图，notification在同一业务事务保存`tt_notification`并按最终接收人和渠道创建独立Action Job。P01只装配`IN_APP`，由LOCAL_TRANSACTIONAL ActionHandler在结果事务中原子写入`tt_inbox`、闭合Attempt并标记Action成功。

没有显式RECIPIENT时接收人回退到OWNER；存在显式RECIPIENT时不自动追加OWNER。最终接收人去重后最多10人。通知接收、渠道受理、渠道送达、用户已读和场景完成是不同事实，notification不能替场景修改业务终态。

Action key必须稳定、定长且不泄露接收人；重试只增加Attempt，不增加业务通知槽位。过期、取消、DEAD、UNKNOWN和终态Action资格严格服从06；S01的24小时有效期和S02的槽位/有效期由各场景契约拥有，不能成为所有通知的全局默认值。

## 未来规划边界

- C09必须区分业务提醒节制与技术重试，并定义免打扰、允许时段、频控、汇总及渠道降级顺序。
- C12各渠道必须分别定义用户映射、幂等键、超时、受理/送达回执、可重试错误、永久错误和UNKNOWN；P01站内信不表示外部IM已经支持。
- C16的委托授权和“谁有权完成”主要归collaboration；notification只负责接收人投影与投递，不能从收到通知推导办理权限。
- C18必须定义保留期限、用户主动删除、合规清理、审计痕迹和删除后的未读计数；P01“不随业务操作自动删除”不等于永久保留。

以上未来能力均为OUTLINE，不能创建空渠道Bean、假回执或真实外部凭据配置。

## 数据、失败与演进

notification拥有通知和收件能力表及其迁移；公共Action表仍由平台拥有。网络渠道必须声明EXTERNAL并在事务外调用，IN_APP保持LOCAL_TRANSACTIONAL。新增渠道优先作为notification内部实现，只有独立发布、重大SDK冲突、安全隔离或独立所有权成立时才拆模块。

通知schema和渠道payload必须版本化。仍有未终结Action引用的旧版本必须可读；否则应用不得就绪。已经产生的通知、收件和Attempt不能因场景修改或渠道升级被覆盖。
