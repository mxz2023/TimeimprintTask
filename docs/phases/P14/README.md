# P14 · 提醒时间段

本阶段为提醒增加「时间段」：一条提醒可以选「只在这些时间段提醒」或「这些时间段不提醒」，工作日与非工作日各设一套多段时间，支持跨午夜。工作日按中国大陆法定节假日与调休判定。落在允许范围外的那次发生直接跳过，不补发。九种日历规则全部适用。网页表单由 Website 同期阶段负责，接口以本文第 3 节为草案。

老板于 2026-10-19 确认需求，并经 MyStudio 锁定第 8 节 Q0–Q7。同日 MyStudio 授权进入 IMPLEMENTING。自动证据已写入 [DELIVERY](DELIVERY.md)，总体状态为 VERIFYING。第 6 节必要人工步骤仍须在发布前由人完成。未打标签，未标 RELEASED。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P14 |
| 排期身份 | CURRENT |
| 总体状态 | VERIFYING |
| 文档基线 | 2.3 |
| 基础发布 | P13；Git 标签 `v20261018-P13` |
| baselineGitRef | `06c3d1089a539aa72ad89402c6dc24fc2e7177da`（2026-10-19 授权实施时的 develop HEAD；与开立时相同，develop 未前进） |
| 变化分类 | 兼容能力扩展（[08](../../08-AI-IMPLEMENTATION-TASKS.md) 第 5 节）：新增 calendar `schemaVersion=3` 与能力项，不改 kernel 业务语义、公共表或稳定 SPI |
| 目标场景 | [S01](../../scenarios/S01-reminder.md) `reminder`、[S02](../../scenarios/S02-recurring-todo.md) `recurring_todo` |
| 目标能力项 | [CAP01](../../capabilities/CAP01-calendar.md) 的 CAL-07（工作日与节假日日历，C02 子集：仅中国大陆法定日历）与新增 CAL-17（发生时间段过滤） |
| 工程状态 | VERIFYING |
| 下一动作 | 完成 [第 6 节](#6-必要人工步骤) 必要人工步骤后再考虑发布。未做完不得标 RELEASED，也不得打标签 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | [DELIVERY](DELIVERY.md)。只记录自动证据；人工步骤尚未做 |

P01—P13 已冻结，本阶段不得改写它们。

## 2. 方案口径

### 2.1 放在哪一层

时间段是 calendar 触发配置的一部分，在「算发生」时过滤，而不是在通知层拦截。

| 方案 | 做法 | 结论 |
| --- | --- | --- |
| A. calendar 过滤（采用） | 预览、创建、修改与 Planner 共用的发生计算先算出原规则的候选发生，再按时间段丢弃不允许的候选。被丢弃的发生不物化实例、计划 Signal、Action 或收件 | 「跳过不补发」天然成立；高频规则不会因窄时段产生大量无用行；E02 预览直接显示真实提醒时间；停机追赶（ALL_MISSED）同样不补段外发生 |
| B. Signal 处理时判定 | 照常物化，到点处理 Signal 时判断，段外把实例转 CANCELLED | 每分钟一次加窄时段时每天上千条无用实例，挤占每轮 100 次的规划额度；还要改 S01/S02 状态含义。不采用 |
| C. 通知层免打扰 | 归入 [CAP03](../../capabilities/CAP03-notification.md) NOT-04 / C09 | C09 指延迟、汇总与降级，与「跳过不补发」不同。NOT-04 仍为 BACKLOG，不在本期 |

### 2.2 数据模型

不新增数据库表，不需要 Flyway 迁移。时间段随日历配置保存在 `tt_trigger_binding.config_json`，`schema_version=3`。节假日数据是 calendar 模块内置的只读资源文件（见 2.6），不入库。若实施中发现必须改表，立即停止并回到文档评审。

calendar `schemaVersion=3` = `schemaVersion=2` 的九种类型及其原字段 + 必填字段 `timeWindow`：

| 字段 | 类型 | 取值与约束 |
| --- | --- | --- |
| `timeWindow` | 对象或 null | 必须出现。null 表示不限时段，发生结果与同字段的 `schemaVersion=2` 完全相同 |
| `timeWindow.mode` | 字符串 | `ALLOW`（只在这些时间段提醒）或 `DENY`（这些时间段不提醒）。大小写精确匹配。沿用 Website P05 草案的取值 |
| `timeWindow.workdaySegments` | 数组 | 工作日使用的时间段，0—8 段 |
| `timeWindow.nonWorkdaySegments` | 数组 | 非工作日使用的时间段，0—8 段 |
| 段对象 | 对象 | 精确为 `start, end`，不允许其他字段 |
| `start` | 字符串 | `HH:mm`，`00:00`—`23:59` |
| `end` | 字符串 | `HH:mm`，`00:01`—`23:59`，或 `24:00`（当天结束）。`end` 早于 `start` 表示跨午夜到次日；不接受 `00:00` 作为结束（请写 `24:00`），不接受 `start = end` |

`timeWindow` 对象恰好三个字段（`mode, workdaySegments, nonWorkdaySegments`）。日类型固定按中国大陆法定节假日与调休判定；将来支持企业日历时另增 schemaVersion；未知字段、缺字段、类型错一律拒绝。段数组须按 `start` 严格升序（规范化不改变数组顺序，网页提交前自行排序）。

### 2.3 校验

| 规则 | 不通过时的原因键 |
| --- | --- |
| `timeWindow` 缺失、不是对象或 null、字段多或少、段对象字段不对 | `TW_FIELD` |
| `mode` 不是 `ALLOW`/`DENY` | `TW_MODE` |
| `start`/`end` 不是 `HH:mm`、越界、`end=00:00` 或 `start=24:00` | `TW_TIME_FORMAT` |
| `start = end` | `TW_ZERO_LENGTH` |
| 单个数组超过 8 段 | `TW_TOO_MANY` |
| 数组未按 `start` 严格升序 | `TW_ORDER` |
| 同一数组内两段在 24 小时环上重叠（首尾相接如 `09:00—12:00` 与 `12:00—14:00` 允许）；跨午夜段按环计算 | `TW_OVERLAP` |
| `ALLOW` 且两个数组都为空（永远不提醒） | `TW_ALLOW_EMPTY` |
| `DENY` 且两个数组都为空（应改传 null） | `TW_DENY_EMPTY` |
| E03/E06：从事务时间起 400 天内按规则与时间段没有任何一次允许的发生（例如 ONCE 恰在段外） | `TW_NO_OCCURRENCE` |
| E06 把已是 3 的绑定改回 1 或 2 | `TW_DOWNGRADE` |

全部返回 HTTP 400、`code=INVALID_REQUEST`、`data=null`。`message` 以原因键加全角冒号开头，后接中文说明，例如 `TW_OVERLAP：工作日时间段 09:00—12:00 与 11:00—13:00 重叠`。网页按冒号前的原因键匹配提示；冒号后的文字可能调整，不作为契约。这不改变信封结构：现有 `ApiMessages.error` 对含中文的详情原样输出。场景不接受 `schemaVersion=3` 时（S03、S04、S05、S14，见 2.8）返回既有 `UNSUPPORTED_SCHEMA_VERSION`。E02 预览执行同样的字段校验，但 `TW_NO_OCCURRENCE` 不报错，只返回空的 `occurrences`。

### 2.4 判定逻辑

设候选发生的业务本地时间为 `d`（日期）与 `τ`（含秒的时刻），时区固定 `Asia/Shanghai`。

1. 日类型 `type(x)`：日期 `x` 在该年数据的 `holidays` 中为非工作日；在 `workdays`（调休上班）中为工作日；否则周一至周五为工作日，周六、周日为非工作日。该年没有数据时按周一至周五回退（见 2.6）。
2. `segs(x)`：`type(x)` 为工作日取 `workdaySegments`，否则取 `nonWorkdaySegments`。
3. 段覆盖用左闭右开 `[start, end)`。`τ` 被覆盖，当且仅当以下任一成立：
   - `segs(d)` 中有不跨午夜的段（`end > start` 或 `end=24:00`）满足 `start ≤ τ < end`；
   - `segs(d)` 中有跨午夜段满足 `τ ≥ start`（当晚部分）；
   - `segs(d−1)` 中有跨午夜段满足 `τ < end`（次日凌晨部分）。
4. 跨午夜段整段归属**开始那一天**的日类型。次日凌晨的部分按前一天的那套时间段算，不再看次日的日类型。
5. `ALLOW`：被覆盖才保留。`DENY`：被覆盖就丢弃。因此某一套为空时：`ALLOW` 下该日类型一律不提醒，`DENY` 下该日类型不受限制。不同日类型的段互不冲突，覆盖取并集。

例子（工作日段 `22:00—07:00`，模式 `ALLOW`）：周五 23:00 保留；周六 06:00 保留（属于周五那段）；周六 23:00 改看非工作日那套；国庆假期中的周四按非工作日；调休上班的周日按工作日。

候选发生仍由原规则产生，时间段只做删减：不移动、不合并、不补发，`occurrenceKey`、`occurrenceAt`、signalKey 规则不变。`EVERY_N_HOURS`/`EVERY_N_MINUTES` 的锚点不因跳过而漂移。

### 2.5 规划与计算

- 预览、创建、修改、恢复与 Planner 调用同一实现，过滤先于计数：7 天视野内同步最多写 100 次是「保留下来的」100 次，原五种日级规则仍每天至多一次。
- 一次计算最多向前搜索 400 天。小时、分钟规则须直接跳到下一段的起点再对齐锚点，不得逐分钟枚举；单次计算在一分钟规则加窄时段、跨 400 天时也须在 50 毫秒内完成。
- Planner 推进 `next_fire_at` 与 `exhausted`：原规则耗尽才标 `exhausted`；仅因 400 天内找不到允许的发生，不标耗尽，`next_fire_at` 设为搜索终点，到时再算。
- 修改时间段沿用既有「日历配置变化」：`scheduleGeneration` 加一，旧批次未来 Signal 转 IGNORED、WAITING 实例转 CANCELLED，新批次重建 7 天窗口；已触发历史不变。
- 暂停期间不产生实例；恢复从当前时刻后的下一次**允许的**发生继续。

### 2.6 节假日数据：来源与维护

| 维度 | 内置年度数据（推荐） | 管理接口导入 |
| --- | --- | --- |
| 形态 | calendar 模块资源文件 `calendar/cn-mainland-workday/{yyyy}.json`，随制品发布，启动时加载到内存 | 新增内部端点把某年数据写入新表 |
| 每年维护 | 国务院办公厅发布次年安排后（通常 10—12 月），开一个小维护阶段加一个文件，走评审、测试、发布 | 运维调用接口导入，不需要发版 |
| 可追溯 | 在 Git 中，每份文件写明来源文号与链接，测试可重现 | 数据在库里，本地与生产可能不一致；须另做审计 |
| 新增面 | 无新表、无新端点、无新 `timeimprint.*` 配置键，不动 Deploy | 新表与迁移、内部鉴权、校验与审计，CAP01 写明「没有能力专有数据前不建节假日表」 |
| 临时调整 | 须发版 | 可即时导入 |

采用内置年度数据（Q2）。本期只随制品发布 2026 年文件；2027 年安排公布后另开维护阶段录入（Q6）。文件格式：

```json
{
  "region": "CN_MAINLAND",
  "year": 2026,
  "source": "国务院办公厅关于2026年部分节假日安排的通知（文号与链接实施时照原文填写）",
  "holidays": ["2026-01-01"],
  "workdays": []
}
```

上面只示意格式，不是真实数据。实施时按官方通知逐日录入，`holidays` 是放假日期（含落在周末的放假日），`workdays` 是调休上班的周末日期。启动校验：年份一致、日期都在该年、两表不相交、无重复、`workdays` 只能是周六或周日；任一文件不合法则 readiness 不就绪。

缺年份时：日期所在年份没有数据，按周一至周五回退，并在健康详情与日志中标出缺失年份；每年 12 月 1 日起若次年数据仍缺，健康详情给出警告但不置为不就绪。

已物化的 7 天窗口不因数据文件更新而重算（Q3）：新数据只影响尚未物化的发生。正常年度更新在 12 月中旬前发布即无影响；临时调整若落在已物化的 7 天内，仍按旧数据。

### 2.7 兼容

| 对象 | 结论 |
| --- | --- |
| `schemaVersion=1/2` 的绑定 | 解码、预览、规划结果逐字节不变；以黄金用例回归 |
| 已有提醒 | 不设时段即行为不变。E06 `update` 允许把绑定从 1 或 2 升到 3（目前代码禁止改版本，本期放开且只许升到 3）；升到 3 且 `timeWindow=null`、其余字段相同时视为 NoChange，库中仍保持原版本，不增 revision 或 `scheduleGeneration` |
| 已投递记录 | 已有实例、Signal、Action、收件、Transition 都不改写；加时段只影响新批次 |
| 被跳过的发生 | 不记录：不产生实例、Signal、Action、通知或收件，也没有「跳过」日志行 |
| 统计 | 被跳过的发生不进入任何计数：E08 列表、`DeliverySummary`（含 `totalCount`、`inboxCount`、`unreadInboxCount`）、E12 未读与 E30 帧都看不到。E02 预览只列出保留的发生，网页据此展示「接下来提醒时间」 |
| 停机追赶 | ALL_MISSED 仍追赶，但段外的历史发生同样不物化 |
| 启动版本核对 | A35 只读检查须接受 `schemaVersion=3`。回退到 P14 之前的制品时，库中已有 3 号绑定会让 readiness 不就绪；回退前须先把这些定义改回或退役（写入 03 回滚说明） |
| 飞书 | 渠道不变；被跳过的发生不发飞书。保留下来的发生照常发 |

### 2.8 场景范围

S01、S02 接受 `schemaVersion` 1、2、3（Q0）。新建时网页统一传 3（不设时段传 `timeWindow: null`）。S03、S04、S05、S14 本期不接受 3，保持 2，传入 3 返回 `UNSUPPORTED_SCHEMA_VERSION`（Q7）。S02 中时间段只过滤「发生」（整个实例不生成）；保留下来的实例上的 CHASE 催办与稍后提醒不受时间段限制（Q1）。

## 3. 接口草案（供 Website 对齐）

不新增端点（下一个公开编号 E33 本期不占用）。变化只在 E02、E03、E06 的 `triggerBindings[].config` 与回显的 `TriggerBindingView.config`。

创建（E03）只在工作日 9—12 点、14—18 点每 30 分钟提醒，非工作日不提醒：

```json
{
  "bindingKey": "primary",
  "providerKey": "calendar",
  "schemaVersion": 3,
  "config": {
    "type": "EVERY_N_MINUTES",
    "startDate": "2026-10-20",
    "intervalMinutes": 30,
    "localTime": "08:00:00",
    "zoneId": "Asia/Shanghai",
    "timeWindow": {
      "mode": "ALLOW",
      "workdaySegments": [{"start": "09:00", "end": "12:00"}, {"start": "14:00", "end": "18:00"}],
      "nonWorkdaySegments": []
    }
  }
}
```

每天 8 点提醒，但工作日夜间 22:00—次日 07:00、非工作日 23:00—次日 09:00 不提醒：

```json
{
  "bindingKey": "primary",
  "providerKey": "calendar",
  "schemaVersion": 3,
  "config": {
    "type": "DAILY",
    "startDate": "2026-10-20",
    "localTime": "08:00:00",
    "zoneId": "Asia/Shanghai",
    "timeWindow": {
      "mode": "DENY",
      "workdaySegments": [{"start": "22:00", "end": "07:00"}],
      "nonWorkdaySegments": [{"start": "23:00", "end": "09:00"}]
    }
  }
}
```

不设时段：`"schemaVersion": 3` 且 `"timeWindow": null`；或继续传 1/2 不带该字段。

| 接口 | 变化 |
| --- | --- |
| E02 预览 | 接受 3；`occurrences` 只含保留的发生；字段校验同 2.3（`TW_NO_OCCURRENCE` 除外） |
| E03 创建 | 接受 3；校验失败按 2.3 返回 |
| E06 `update` | payload 仍为完整替换；绑定可从 1/2 升到 3，不可从 3 降回；修改 `timeWindow` 属于日历配置变化 |
| E04/E05/E06 回显 | `TriggerBindingView.schemaVersion` 为 3，`config.timeWindow` 原样回显（字段顺序按规范化排序）；`nextFireAt` 为下一次允许的发生 |
| E07/E08 | 无新字段 |

### 3.1 对 Website P05 草案（PR #3）的答复

| 问题 | Task 的答复 |
| --- | --- |
| 字段名与层级 | 不放在与 `triggerBindings` 平级，改放在 `triggerBindings[0].config.timeWindow`，并把该绑定的 `schemaVersion` 设为 3。原因：E03 请求与 E06 `update` 的六个字段都是精确集合，顶层加字段会改公共请求结构；时间段是「哪些发生保留」的日历语义，必须与规则同一处计算，预览、创建、Planner 才能一致。内部字段：`mode` 沿用 `ALLOW`/`DENY`；时间沿用 `HH:mm`；两套数组按现有命名习惯（列表用复数名词，如 `offsetDays`、`triggerBindings`）定名为 `workdaySegments`、`nonWorkdaySegments`，段对象沿用 `{start, end}` |
| 更新时不带该字段 | E06 `update` 是完整替换。`schemaVersion=3` 的 config 必须带 `timeWindow` 键，缺失返回 `TW_FIELD`，服务端既不保留也不静默清空。显式清空：传 `"timeWindow": null`（绑定仍为 3）。对已是 3 的绑定改传 1/2 的 config 返回 `TW_DOWNGRADE` |
| 每套最多段数 | 8，与 Website 一致；超出返回 `TW_TOO_MANY` |
| 某一套为空 | `ALLOW`：该日类型一律不提醒。`DENY`：该日类型不受限制。两套都空：`ALLOW` 返回 `TW_ALLOW_EMPTY`，`DENY` 返回 `TW_DENY_EMPTY`（不限时段请传 null）。与 Website「两套合计至少一段」一致 |
| 节假日数据缺某年 | 创建与更新不报错，没有专用错误码。调度与预览对该年日期按周一至周五回退判定，健康详情与日志标出缺失年份；每年 12 月 1 日起次年仍缺则健康详情告警（不置不就绪）。`TW_NO_OCCURRENCE` 的判断同样使用回退结果 |

与 Website 前端校验的对照：

| 规则 | 是否一致 |
| --- | --- |
| `start ≠ end` | 一致（`TW_ZERO_LENGTH`） |
| 同一套不重叠、端点相接允许 | 一致（`TW_OVERLAP`）。注意服务端按 24 小时环判断，跨午夜段（如 `22:00—07:00`）与同一套中的 `06:00—09:00` 视为重叠，前端请同样处理 |
| 两套合计至少一段 | 一致（`TW_ALLOW_EMPTY` / `TW_DENY_EMPTY`） |
| 服务端额外规则 | 结束不接受 `00:00`，当天结束写 `24:00`（`TW_TIME_FORMAT`）；数组须按 `start` 严格升序，前端提交前排序即可（`TW_ORDER`）；400 天内没有任何允许的发生时创建/更新拒绝（`TW_NO_OCCURRENCE`，前端无法预判，请展示服务端提示，或先调 E02 预览） |

网页提示文案建议：跨午夜段显示为「22:00 至次日 07:00」，并说明「跨午夜部分按开始那天是否工作日计算」；模式说明「段外的提醒直接跳过，不补发」。

## 4. 允许与禁止

允许（仅在本阶段转为 IMPLEMENTING 之后）：

- `timeimprint-task-service-capability-calendar`：配置解码 `schemaVersion=3`、时间段过滤与搜索、日类型判定、内置 `calendar/cn-mainland-workday/*.json` 资源与加载校验。
- `timeimprint-task-service-storage-mysql` 中创建、更新、控制与 Planner 适配器调用新计算的部分，及 `next_fire_at`/`exhausted` 推进；E06 绑定版本 1/2→3 的升级规则。
- `timeimprint-task-service-application` 的预览与写入校验；`timeimprint-task-service-scenario-basic` 中 S01/S02 接受版本 3。
- `timeimprint-task-boot-loader` 的 readiness/健康详情（节假日数据校验与缺年份提示、A35 接受 3）。
- 上述模块的测试；修订 [01](../../01-MVP-SPEC.md)、[03](../../03-INTEGRATION-CONTRACTS.md)、[04](../../04-API.md)、[06](../../06-SCHEDULING.md)、[07](../../07-ACCEPTANCE.md)、[09](../../09-SCENARIO-ROADMAP.md)、[CAP01](../../capabilities/CAP01-calendar.md)、[能力索引](../../capabilities/README.md)、[S01](../../scenarios/S01-reminder.md)、[S02](../../scenarios/S02-recurring-todo.md)、[HUMAN-GLOSSARY](../../HUMAN-GLOSSARY.md) 中与本期一致的部分。

禁止：

- 改写已 RELEASED 的 P01—P13。
- 新增或修改 Flyway 迁移、公共表或通知表；新增 `timeimprint.*` 配置键或 Compose 环境变量；改 User、Deploy、Wiki。
- 改变 `schemaVersion=1/2` 的任何发生结果；改 kernel 业务语义、S01/S02 状态含义、S03—S05/S14。
- 为被跳过的发生写任何行，或把它补发到段内。
- DRAFT 或 READY 期间改代码、测试、配置、迁移或部署脚本。

## 5. 不变量

本阶段不改变 [INV-01](../../02-AI-CODING-GUIDE.md)—[INV-09](../../02-AI-CODING-GUIDE.md)。calendar 仍只把发生转换为计划 Signal，不改变场景状态、不生成通知；同一 definition、binding、scheduleGeneration、controlGeneration 与 occurrenceKey 仍最多一组事实。

## 6. 必要人工步骤

自动测试可证明解码、判定与规划。下列步骤须在 Website 同期阶段可用后，由人用真实登录账号在网页完成，并在飞书确认（AGENTS.md：投递结果变化须在飞书真实收到）。做完并说明看到的结果前，不得标为 RELEASED。

1. 新建一条每 N 分钟提醒，模式「只在这些时间段提醒」，时段设为接下来约 10 分钟。确认段内到点在站内信与飞书都收到，段结束后的到点不再收到，之后也不补发。
2. 新建一条提醒，模式「这些时间段不提醒」，时段覆盖接下来一次到点。确认那一次站内信和飞书都没有收到，离开时段后也不补发；下一次段外到点正常收到。
3. 打开一条已有的旧提醒加上时间段并保存。确认已经收到的历史还在，预览中的后续时间按时间段变化。
4. 在网页预览中各看一个法定假日与一个调休上班日（例如 2026 年国庆前后），确认按工作日/非工作日分开套用时间段。

## 7. 验收编号

正式通过标准将在 T01 写入 [07](../../07-ACCEPTANCE.md) 第 6.9 节。下表与第 2 节同一口径。

| 编号 | 要求 |
| --- | --- |
| L01 | `schemaVersion=1/2` 的解码、E02 预览与 Planner 结果与 P13 黄金用例逐字节一致；`schemaVersion=3` 且 `timeWindow=null` 与同字段 2 的发生相同 |
| L02 | 2.3 每个原因键至少一条拒绝用例：400、`INVALID_REQUEST`、`message` 以原因键开头、不写任何数据；S03/S04/S05/S14 传 3 返回 `UNSUPPORTED_SCHEMA_VERSION` |
| L03 | 九种日历类型各在 `ALLOW` 与 `DENY` 下多段过滤，E02 与真库 Planner 产生的发生一致 |
| L04 | 跨午夜段按开始那天的日类型判定，含周五夜至周六凌晨、节前夜至假日凌晨、`24:00` 边界与左闭右开 |
| L05 | 日类型：法定假日、调休上班、普通周末、普通工作日、缺年份回退；坏数据文件 readiness 不就绪 |
| L06 | 段外发生不产生实例、Signal、Action、通知、收件；停机追赶后同样没有；`DeliverySummary` 与 E12 不受影响 |
| L07 | E06：1/2 升到 3 并设时段后 `scheduleGeneration` 加一、窗口按新规则重建、已触发历史不变；仅升到 3 且 `timeWindow=null` 为 NoChange；3 降回报 `TW_DOWNGRADE` |
| L08 | 一分钟规则加窄时段：同步写入不超过 100、Planner 补齐 7 天、400 天内无允许发生时不标耗尽；单次计算不超过 50 毫秒 |
| L09 | 本机 `scripts/run-task-acceptance.sh` 全量真库回归（`mysql-it`、`dual-process-it`）退出码 0 |

## 8. 已锁定的决定

老板于 2026-10-19 经 MyStudio 锁定。下表是本期口径，不再待决。

| 编号 | 决定 |
| --- | --- |
| Q0 | [S01](../../scenarios/S01-reminder.md) 提醒与 [S02](../../scenarios/S02-recurring-todo.md) 待办都可用时间段，都接受 calendar `schemaVersion=3`。九种日历规则都适用 |
| Q1 | 时间段只过滤发生。催办与稍后提醒不受时间段限制 |
| Q2 | 节假日使用内置年度文件，不建管理接口，不入库 |
| Q3 | 已经物化的 7 天窗口不因节假日数据更新而重算 |
| Q4 | 缺年份时按周一到周五回退，并在健康详情与日志标出 |
| Q5 | 被跳过的发生不记用户可见记录，也不进入任何计数 |
| Q6 | 本期只内置 2026 年节假日文件。2027 年安排公布后另开维护阶段 |
| Q7 | 生日、到期、缴费、保养不加时间段。S03、S04、S05、S14 传入 `schemaVersion=3` 返回 `UNSUPPORTED_SCHEMA_VERSION` |
