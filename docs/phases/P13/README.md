# P13 · 站内信软删除

本阶段为收件箱增加软删除：单条删除与清空已读。列表、未读计数与 SSE 变化帧排除已删行；实例投递汇总的 `inboxCount` 仍计入已删行。不硬删，不改 notification / Action。网页确认与按钮由 Website 同期阶段负责。

MyStudio 于 2026-10-18 代老板确认草案并授权实施：DRAFT → READY → IMPLEMENTING。任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P13 |
| 排期身份 | CURRENT |
| 总体状态 | IMPLEMENTING |
| 文档基线 | 2.3 |
| 基础发布 | P12；Git 标签 `v20261018-P12` |
| baselineGitRef | `e0b16fb12aa3554a05ae30a43a091429384ccdae`（授权实施前的 HEAD） |
| 工程状态 | IMPLEMENTING |
| Git发布标签 | （发布时填写） |
| 人工验收 | 未开始 |
| 下一动作 | 完成 T01 契约写入后实现 E31/E32 与迁移；可联调时回报 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | 实施中按任务写入 [DELIVERY](DELIVERY.md) |

P01—P12 已冻结，本阶段不得改写它们。

## 2. 已接受口径

| 口径 | 结论 |
| --- | --- |
| 删除方式 | 软删。`tt_inbox` 增加 `deleted_at`（DATETIME(0) NULL）。首次删除写入 UTC 整秒；已删行再次删除视为幂等成功，不改写首次 `deleted_at` |
| 单条删除 | 新增公开命令：`POST /api/v1/inbox/{inboxId}/commands/delete`（拟定编号 E31）。鉴权与 E10—E13 相同；须带 `requestId` 做持久化幂等 |
| 清空已读 | 新增公开命令：`POST /api/v1/inbox/commands/clear-read`（拟定编号 E32）。只软删当前登录接收人、且 `read_at` 非空、且尚未删除的收件；须带 `requestId` |
| 列表与详情 | E10 不返回已删行。E11 对已删或无权的 `inboxId` 返回 404（与无权一致，不泄露存在性） |
| 未读 | E12 的 `unreadCount` 排除已删行；已删未读不再计入 |
| SSE | E30 的 `inbox-changed` 中 `unreadCount` / `latestInboxId` 按排除已删后的水位计算。软删提交成功后向该接收人已打开连接推帧；推送失败不回滚删除 |
| 投递汇总 | `DeliverySummary.inboxCount` **不**因软删减少；已删行仍证明曾投递站内信。`unreadInboxCount` 排除已删 |
| 内核与外键 | 不硬删 `tt_inbox` 行；不改 `tt_notification`、Action、Attempt、Transition；不改 IN_APP 写入路径以外的业务语义 |
| 跨仓 | User / Deploy 不动。Website 另开阶段对接上述 API（二次确认 UI） |

## 3. 允许与禁止

允许（仅在本阶段转为 IMPLEMENTING 之后）：

- `tt_inbox` 增加 `deleted_at` 的 Flyway 迁移，及按需调整收件查询索引（查询条件须排除已删）。
- 实现 E31、E32；E10—E12、E30 查询与水位排除已删；软删提交后同进程推 `inbox-changed`。
- 修订 [04](../../04-API.md)、[05](../../05-DATABASE.md)、[07](../../07-ACCEPTANCE.md)、[CAP03](../../capabilities/CAP03-notification.md) 中与本期一致的正式契约与验收编号。
- 为本期 API 与查询语义补充集成测试；全量真库回归。

禁止：

- 改写已 RELEASED 的 P01—P12。
- 物理删除收件行，或级联删除 notification / Action。
- 改变 `DeliverySummary.inboxCount` 的既有计入规则（含已删行仍计入）。
- 改 kernel 业务语义、平台公共表（除 `tt_inbox.deleted_at`）、场景专有表，或 User / Deploy / Wiki。
- DRAFT 或 READY 期间改代码、测试、配置、迁移或部署脚本。

## 4. 必要人工步骤

自动测试可证明 API、计数与 SSE 水位。下列步骤须在 Website 同期阶段可用后，由人在真实页面完成；做完并说明结果前，不得将本阶段标为 RELEASED。

1. 登录网页工作台，对一条站内信执行删除（经二次确认），确认列表不再显示该条，未读点与实时提示正确。
2. 对已读站内信执行一键清空已读（经二次确认），确认已读项从列表消失，未读项仍在。

## 5. 不变量

本阶段不改变 [INV-01](../../02-AI-CODING-GUIDE.md)—[INV-09](../../02-AI-CODING-GUIDE.md)。站内信仍在 Action 结果事务中写入；软删只改接收人可见性，不撤销投递事实。

## 6. 验收编号（草案，转 READY 前写入 07）

| 编号 | 要求 |
| --- | --- |
| B01 | E31 软删成功后 E10/E11/E12 不再暴露该行；重复删除幂等；`deleted_at` 保留首次时间 |
| B02 | E32 只软删当前接收人的已读未删行；未读行保留 |
| B03 | 软删后 E30 推送 `inbox-changed`，`unreadCount`/`latestInboxId` 排除已删 |
| B04 | 含已删收件的实例上，`DeliverySummary.inboxCount` 不因软删减少 |
