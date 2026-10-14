# P09 · 生日、到期与缴费

本阶段实现 [S03](../../scenarios/S03-anniversary.md)（公历生日与纪念日）、[S04](../../scenarios/S04-deadline-management.md)（到期管理）和 [S05](../../scenarios/S05-payment-management.md)（人工确认缴费）。农历和真实支付渠道不在本期。

任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P09 |
| 排期身份 | RELEASED |
| 总体状态 | RELEASED |
| 文档基线 | 2.3 |
| 基础发布 | P08；Git 标签 `v20261014-P08` |
| baselineGitRef | `e5c3906045e01175788f4e665489884f7c9abdcd` |
| 工程状态 | RELEASED |
| Git发布标签 | `v20261014-P09`（打在本 RELEASED 文档冻结提交上） |
| 人工验收 | 2026-10-14 用户确认可以发布。本期无真实外部系统或界面步骤，已在批准计划时确认 |
| 下一动作 | 无。本阶段冻结；下一阶段须用户另选范围 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | [DELIVERY](DELIVERY.md)：[D01](../../07-ACCEPTANCE.md)—[D05](../../07-ACCEPTANCE.md) 为 PASS；[D06](../../07-ACCEPTANCE.md) 由全量回归覆盖 |

用户于 2026-10-14 批准 P09 计划并要求按该计划实施，同日确认可以发布。阶段已 RELEASED（标签 `v20261014-P09`）。本 README、IMPLEMENTATION、DELIVERY 此后不再改写。P01—P08 已冻结，不得改写。

## 2. 已接受口径

| 口径 | 结论 |
| --- | --- |
| S03 | 只做公历。2 月 29 日在平年落到 3 月 1 日。提前 1 天和当天提醒 |
| S04 | 到期前从 30/7/1/0 天中选择。续期更新同一对象并保留旧到期日。到期当天未续期则过期 |
| S05 | 人工确认缴费。部分支付保留余额。付清或取消后停止催办。冲正后恢复催办 |
| 日历 | schemaVersion 2 增加 `YEARLY` 与 `ANCHOR_OFFSETS`。schemaVersion 1 的已有规则发生结果不变 |
| 结束条件 | 不实现 [CAL-13](../../capabilities/CAP01-calendar.md) |
| 内核与公共表 | 不改 kernel 业务语义，不改平台公共表 |

## 3. 允许与禁止

允许：

- 在 `timeimprint-task-service-capability-calendar` 增加 schemaVersion 2 的公历周年和锚点偏移。
- 新增模块 `timeimprint-task-service-scenario-deadline` 及其专有表。
- 为付清、冲正、取消把定义控制与未来窗口的既有语义接到实例命令之后。
- 验收范围是 [D01](../../07-ACCEPTANCE.md)—[D06](../../07-ACCEPTANCE.md)。

禁止：

- 改写已 RELEASED 的 P01—P08。
- 修改 kernel 业务语义或平台公共 DDL。
- 实现农历、真实支付、trigger 模块、integration 模块或 [S14](../../scenarios/S14-maintenance-follow-up.md)。
- 把金额、证件或纪念日的专有字段写入公共任务表。

## 4. 必要人工步骤

本期无真实外部系统或界面步骤。用户已于 2026-10-14 批准本阶段计划时确认这句话。自动证据通过后即可记录交付；发布仍须用户确认可以发布。

## 5. 不变量

本阶段不改变 [INV-01](../../02-AI-CODING-GUIDE.md)—[INV-09](../../02-AI-CODING-GUIDE.md)。专有数据只在迁移事务中由本场景物化器写入。
