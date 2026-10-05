# 通用能力域索引

本索引是平台通用能力域、能力项状态和正式能力文档的唯一入口。能力域表示可被多个场景复用的机制；能力域存在不表示其规划范围已经实现。

## 状态读法

每个能力域和能力项必须同时记录三个互不替代的状态：

- `planningPosition`：`Pxx`、`NEXT_REVIEW`、`BACKLOG`或`NONE`，说明排期位置。
- `contractStatus`：`OUTLINE`、`DRAFT`、`READY_FOR_IMPLEMENTATION`、`RELEASED`或`DEPRECATED`，说明规则成熟度。
- `implementationStatus`：`NOT_STARTED`、`IN_PROGRESS`、`VERIFIED`或`DEPRECATED`，说明真实实现证据。

只有阶段`DELIVERY.md`中的可复现通过证据可以支持`VERIFIED`。`READY_FOR_IMPLEMENTATION`只说明规则可以编码，不说明代码存在。

## 能力域总览

| 能力编号 | 能力标识 | 能力域 | 目标模块 | 排期位置 | 契约状态 | 实现状态 | 当前批准范围 | 未来规划 | 正式文档 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| CAP01 | `calendar` | 日历与时间 | `timeimprint-task-service-capability-calendar` | P01 | RELEASED | VERIFIED | 五种基础公历规则、北京时间、预览与滚动规划 | C01—C04、C08、C10、C11、C17及农历 | [CAP01](CAP01-calendar.md) |
| CAP02 | `trigger` | 外部/条件/依赖触发 | `timeimprint-task-service-capability-trigger` | BACKLOG | OUTLINE | NOT_STARTED | 无 | C05、C07，服务S05、S11、S12等 | [CAP02](CAP02-trigger.md) |
| CAP03 | `notification` | 通知与收件 | `timeimprint-task-service-capability-notification` | P01 | RELEASED | VERIFIED | 通知意图、IN_APP、收件、接收人投影、Action执行 | C09、C12（P05 飞书子集 DRAFT）、C18及C16的通知部分 | [CAP03](CAP03-notification.md) |
| CAP04 | `collaboration` | 协作与主体 | `timeimprint-task-service-capability-collaboration` | BACKLOG | OUTLINE | NOT_STARTED | 无；P01公共Participant与本地身份不是本能力已实现 | C13、C16，服务S06—S09、S15 | [CAP04](CAP04-collaboration.md) |
| CAP05 | `workflow` | 流程编排 | `timeimprint-task-service-capability-workflow` | BACKLOG | OUTLINE | NOT_STARTED | 无 | C06及S08、S09 | [CAP05](CAP05-workflow.md) |
| CAP06 | `aggregation` | 聚合与统计 | `timeimprint-task-service-capability-aggregation` | BACKLOG | OUTLINE | NOT_STARTED | 无 | S10、S12、S13的计数、窗口和摘要 | [CAP06](CAP06-aggregation.md) |
| CAP07 | `integration` | 外部系统集成 | `timeimprint-task-service-capability-integration` | BACKLOG | OUTLINE | NOT_STARTED | 无 | S11、S15、S16及第三方回调/日历 | [CAP07](CAP07-integration.md) |
| CAP08 | `intelligence` | 智能理解与建议 | `timeimprint-task-service-capability-intelligence` | BACKLOG | OUTLINE | NOT_STARTED | 无；Spring AI版本基线不等于本能力实现 | S17自然语言解析、解释和建议 | [CAP08](CAP08-intelligence.md) |

CAP01 的 CAL-01—CAL-05 与 CAP03 的 NOT-01—NOT-03 已随 [P01 DELIVERY](../phases/P01/DELIVERY.md) 验收为 `VERIFIED`（标签 `v20260915-P01`）。能力域状态仅覆盖上述已批准范围；表中其余 OUTLINE 能力项与 C01—C19 规划项仍未实现。

## C01—C19主要归属

| 编号 | 名称 | 主要归属 | 排期位置 | 契约状态 | 实现状态 |
| --- | --- | --- | --- | --- | --- |
| C01 | 扩展周期 | [calendar](CAP01-calendar.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C02 | 工作日历 | [calendar](CAP01-calendar.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C03 | 业务时间计时 | [calendar](CAP01-calendar.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C04 | 相对时间与多阶段提醒 | [calendar](CAP01-calendar.md) | NEXT_REVIEW | OUTLINE | NOT_STARTED |
| C05 | 时间与条件组合 | [trigger](CAP02-trigger.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C06 | 串行与并行协作 | [workflow](CAP05-workflow.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C07 | 自动完成 | [trigger](CAP02-trigger.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C08 | 时区扩展 | [calendar](CAP01-calendar.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C09 | 提醒节制 | [notification](CAP03-notification.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C10 | 发生例外 | [calendar](CAP01-calendar.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C11 | 有效区间与结束条件 | [calendar](CAP01-calendar.md) | NEXT_REVIEW | OUTLINE | NOT_STARTED |
| C12 | 多IM渠道 | [notification](CAP03-notification.md) | P05 | DRAFT | NOT_STARTED |
| C13 | 身份与权限 | [collaboration](CAP04-collaboration.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C14 | 缓存接入 | [09跨域平台候选](../09-SCENARIO-ROADMAP.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C15 | 前端与用户操作界面 | [09产品接入候选](../09-SCENARIO-ROADMAP.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C16 | 通知委托与多接收人 | [collaboration](CAP04-collaboration.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C17 | 全天日期任务 | [calendar](CAP01-calendar.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C18 | 通知数据保留与删除 | [notification](CAP03-notification.md) | BACKLOG | OUTLINE | NOT_STARTED |
| C19 | 幂等与审计数据生命周期 | [09跨域平台候选](../09-SCENARIO-ROADMAP.md) | BACKLOG | OUTLINE | NOT_STARTED |

主要归属只指定规则维护入口，不禁止能力之间建立明确依赖。C14、C15、C19不是独立业务能力模块，强行归入八个能力域会造成错误依赖，因此保留在09并标明跨域性质。

## AI使用门禁

实现某场景前，必须读取其场景文档和全部依赖能力文档。只有所需能力项达到`READY_FOR_IMPLEMENTATION`、被CURRENT阶段选择且获得用户实施授权时才能编码。遇到`OUTLINE`能力必须先细化规则；遇到能力文档中没有的机制，不能直接在场景或kernel里补特例。

能力发布后，文档继续作为当前行为来源。兼容扩展增加能力项或schemaVersion；需要改变稳定内核、公共表或公共API语义时按08创建ADR。
