# CAP01 · calendar · 日历与时间能力

> 本文是`capabilityKey=calendar`的永久能力入口。P01详细HTTP字段以[04](../04-API.md)为准，规划、锁和恢复以[06](../06-SCHEDULING.md)为准，验证以[07](../07-ACCEPTANCE.md)为准。

| 项目 | 值 |
| --- | --- |
| 能力编号 | CAP01 |
| 目标模块 | `timeimprint-task-service-capability-calendar` |
| planningPosition | P01 |
| contractStatus | RELEASED |
| implementationStatus | VERIFIED |
| P01使用场景 | [S01](../scenarios/S01-reminder.md)、[S02](../scenarios/S02-recurring-todo.md) |
| DELIVERY证据 | [P01 DELIVERY](../phases/P01/DELIVERY.md)；Git 标签 `v20260915-P01` |

能力域整体状态取当前已批准能力项的状态，不表示下表所有未来能力已经READY或实现。

## 能力项

| 能力项 | 内容 | 排期位置 | 契约状态 | 实现状态 | 关联 |
| --- | --- | --- | --- | --- | --- |
| CAL-01 | ONCE一次性时间 | P01 | RELEASED | VERIFIED | S01、S02 |
| CAL-02 | DAILY每日 | P01 | RELEASED | VERIFIED | S01、S02 |
| CAL-03 | WEEKLY每周 | P01 | RELEASED | VERIFIED | S01、S02 |
| CAL-04 | MONTHLY每月，缺失日期取月末 | P01 | RELEASED | VERIFIED | S01、S02 |
| CAL-05 | EVERY_N_DAYS固定锚点每N日 | P01 | RELEASED | VERIFIED | S01、S02 |
| CAL-15 | EVERY_N_HOURS固定锚点每N小时 | P07 | RELEASED | VERIFIED | S01、S02 |
| CAL-16 | EVERY_N_MINUTES固定锚点每N分钟 | P07 | RELEASED | VERIFIED | S01、S02 |
| CAL-17 | 发生时间段过滤：按工作日与非工作日两套时间段删减候选发生 | P14 | RELEASED | VERIFIED | S01、S02 |
| CAL-06 | 扩展周期：公历每年已进入 P09；季度、每N时间单位的其余部分、月末仍未做 | P09（仅 YEARLY）/ BACKLOG（其余） | READY_FOR_IMPLEMENTATION（仅 YEARLY）/ OUTLINE（其余） | VERIFIED（仅 YEARLY） | C01、S03 |
| CAL-07 | 工作日与节假日日历：中国大陆法定子集已进入 P14；企业日历仍未做 | P14（仅中国大陆法定日历）/ BACKLOG（企业日历） | RELEASED（仅中国大陆法定子集）/ OUTLINE（企业日历） | VERIFIED（仅中国大陆法定子集）/ NOT_STARTED（企业日历） | C02 |
| CAL-08 | 业务时间计时 | BACKLOG | OUTLINE | NOT_STARTED | C03、S09 |
| CAL-09 | 相对锚点日的天数偏移；可选在锚点之后按自然日继续 | P09 | READY_FOR_IMPLEMENTATION | VERIFIED | C04、S04、S05 |
| CAL-10 | 农历与周年换算 | NEXT_REVIEW | OUTLINE | NOT_STARTED | S03 |
| CAL-11 | 多时区与夏令时 | BACKLOG | OUTLINE | NOT_STARTED | C08 |
| CAL-12 | 排除、额外和单次改期 | BACKLOG | OUTLINE | NOT_STARTED | C10 |
| CAL-13 | endAt、最多N次等结束条件 | NEXT_REVIEW | OUTLINE | NOT_STARTED | C11 |
| CAL-14 | 全天日期语义 | BACKLOG | OUTLINE | NOT_STARTED | C17 |

## P01正式范围

CAL-01—CAL-05使用一个`bindingKey=primary`、`providerKey=calendar`、`schemaVersion=1`的触发绑定。时区固定`Asia/Shanghai`，精确到秒；预览、创建和Planner复用同一规范化及计算实现，使用严格afterExclusive边界。ONCE必须在判定时间之后；DAILY、WEEKLY、MONTHLY和EVERY_N_DAYS保留原始startDate锚点，不随实际执行或重启漂移。

[CAL-15](CAP01-calendar.md) 与 [CAL-16](CAP01-calendar.md) 使用同一绑定和 schemaVersion 1。锚点是 startDate 加 localTime，再按 `intervalHours`（1—168）或 `intervalMinutes`（1—1440）前进。7 天视野内同步最多物化 100 次，其余由 Planner 补齐。由这两种规则物化的 S02 实例 `snoozeAllowed=false`。原五种规则的发生结果不变。证据见 [P07 DELIVERY](../phases/P07/DELIVERY.md)，标签 `v20261011-P07`。

创建ACTIVE定义时原子物化未来7天内的WAITING实例和时间Signal，不预建Action。Planner按有界批次滚动补窗；相同definition、binding、scheduleGeneration、controlGeneration和occurrenceKey只能产生一组事实。停机采用ALL_MISSED追赶，暂停恢复不补暂停区间。完整字段和边界不在本文复制，以01、04和06为准。

能力通过`TriggerProvider`把日历发生转换成持久化Signal，不直接改变场景状态，不生成通知，不决定实例业务终态。具体Signal结果由场景契约负责。

## P09 正式范围

calendar `schemaVersion=2` 在 schemaVersion 1 的七种类型之外增加两种。schemaVersion 1 不接受这两种类型，也不改变已有七种规则的发生结果。

| 类型 | 配置精确字段 | 语义 |
| --- | --- | --- |
| YEARLY | `type, startDate, month, dayOfMonth, offsetDays, localTime, zoneId` | 每年在该月日发生。2 月 29 日在平年落到 3 月 1 日，再按 `offsetDays` 平移。禁止用每 365 天代替 |
| ANCHOR_OFFSETS | `type, anchorDate, offsetDays, dailyAfterAnchor, localTime, zoneId` | 锚点日加上每个偏移各发生一次。`dailyAfterAnchor=true` 时，锚点日之后每个自然日继续发生 |

`offsetDays` 为 1—4 个不重复整数，范围 -366—366。`month` 为 1—12，`dayOfMonth` 为该月存在的日，2 月 29 日除外允许。`zoneId` 仍只能是 `Asia/Shanghai`。下一次发生若在 7 天窗口之外，绑定不标记为耗尽，`nextFireAt` 指向窗口之后的下一次，由 Planner 到点再物化。锚点或偏移变更沿用定义更新：取消尚未发生的 Signal，按新规则重算。

[CAL-13](CAP01-calendar.md) 本期不实现。

## P14 正式范围

[CAL-17](CAP01-calendar.md) 与 [CAL-07](CAP01-calendar.md) 的中国大陆法定子集已随 [P14](../phases/P14/README.md) 验收为 VERIFIED（标签 `v20261021-P14`）。不新增表，不新增 `timeimprint.*` 配置键。

`schemaVersion=3` 在 schemaVersion 2 的九种类型上增加必填 `timeWindow`。HTTP 字段、原因键与升级规则以 [04 §3.1.2](../04-API.md) 为准。`timeWindow=null` 时发生结果与同字段的 schemaVersion 2 相同。`schemaVersion=1/2` 的发生结果不变。

日类型 `type(x)`：日期 `x` 在该年数据的 `holidays` 中为非工作日；在 `workdays`（调休上班）中为工作日；否则周一至周五为工作日，周六、周日为非工作日。该年没有数据时按周一至周五回退。工作日取 `workdaySegments`，非工作日取 `nonWorkdaySegments`。

段覆盖为左闭右开 `[start, end)`。候选时刻 `τ` 落在日期 `d` 上被覆盖，当且仅当：当天不跨午夜的段满足 `start ≤ τ < end`；当天跨午夜段满足 `τ ≥ start`；或前一天的跨午夜段满足 `τ < end`。跨午夜段整段归属开始那一天的日类型。`ALLOW` 保留被覆盖的发生，`DENY` 丢弃被覆盖的发生。某一套为空时：`ALLOW` 下该日类型一律不提醒，`DENY` 下该日类型不受限制。

时间段只删减候选，不移动、不合并、不补发。`occurrenceKey`、`occurrenceAt` 与 signalKey 规则不变。小时与分钟规则的锚点不因跳过而漂移。被丢弃的发生不产生实例、计划 Signal、Action 或收件，也不进入计数。S02 已保留实例上的催办与稍后提醒不再看时间段。

节假日数据是模块资源 `calendar/cn-mainland-workday/{yyyy}.json`，字段为 `region=CN_MAINLAND`、`year`、`source`、`holidays`、`workdays`。本期只发布 2026 年文件。启动校验与缺年行为见 [03](../03-INTEGRATION-CONTRACTS.md)。已物化的 7 天窗口不因替换该文件而重算。搜索、`next_fire_at` 与 `exhausted` 见 [06](../06-SCHEDULING.md)。验收为 [L01](../07-ACCEPTANCE.md)—[L09](../07-ACCEPTANCE.md)。

企业日历、临时导入接口和「数据更新后重建已物化窗口」不在本期。

## 未来规划边界

- C01：每年不能用每365天替代；2月29日、季度与月末必须先确定规则。
- C02：中国大陆法定日历与缺年回退已由 P14 写明。企业日历仍须另定来源与缺失行为后才能编码。
- C03：必须定义营业时段、午休、跨日和外部日历不可用时的计时结果。
- C04：到期前后多个时间点必须覆盖最早计划窗口，并定义业务时间变化后的整体重算和取消。
- S03农历：必须决定闰月、无对应日期和公历转换版本；当前不属于C01已支持范围。
- C08：必须决定固定时区、随用户旅行以及夏令时缺失/重复时间。
- C10：必须区分仅改单次、修改本次以后和snooze；单次例外不能改写基础规则锚点。
- C11：endAt和次数边界必须定义是否包含、skip是否计数以及已物化实例处理；P01已有startDate不表示C11完成。
- C17：日期任务不能默认为00:00，必须另定提醒、截止、展示和跨时区语义。

以上条目都是已保存规划，不足以编码。进入阶段前必须把选中能力项升级为READY并补充schema、错误、迁移及验收。

## 数据、失败与演进

calendar拥有触发配置解码和纯时间计算，不拥有场景专有状态。持久化绑定、Signal和游标使用公共平台表；除非未来出现真正能力专有数据，不得提前创建节假日、农历或例外表。P14 的法定工作日数据是只读资源文件，不是表。

新增时间规则优先增加兼容schemaVersion和能力项。改变P01 schemaVersion 1已有发生结果属于不兼容变化，必须保留旧版本读取与规划能力或提供正式迁移，不能原地改算法。
