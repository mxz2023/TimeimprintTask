# 09 · 场景、能力与技术演进路线

> 阅读入口与当前状态见[00开发导航](00-READING-ORDER.md)。本文只维护规划排序、跨项关系和技术候选；场景详细内容见[场景索引](scenarios/README.md)，能力详细内容见[能力索引](capabilities/README.md)。

版本2.3；前：[实施治理](08-AI-IMPLEMENTATION-TASKS.md)，后：[技术评审](10-TECHNICAL-REVIEW.md)。本文中的`NEXT_REVIEW`和建议顺序都不是实施授权，也不表示已经创建下一阶段。

## 1. 当前、接下来评审与长期规划

| 层级 | 场景 | 能力 | 含义 |
| --- | --- | --- | --- |
| 已发布P01 | [S01](scenarios/S01-reminder.md)、[S02](scenarios/S02-recurring-todo.md) | calendar的CAL-01—CAL-05、notification的NOT-01—NOT-03 | P01 RELEASED（标签 `v20260915-P01`）；场景/能力项 VERIFIED |
| 已发布P02 | 不新增场景 | 不新增能力 | 工程结构与测试镜像 RELEASED（标签 `v20260917-P02`）；人工验收 2026-09-17 |
| 已发布P03 | 不新增场景 | 不新增能力 | Jackson 3 原生迁移 RELEASED（标签 `v20260917-P03`）；人工验收 2026-09-17；X05 VERIFIED |
| 已发布P04 | 不新增场景 | 不新增能力 | [P04](phases/P04/README.md) RELEASED（标签 `v20261003-P04`）：S02 站内信标题，以及实例命令改为先锁定义再锁实例；人工验收 2026-10-03 |
| CURRENT | 不新增场景 | notification 的 NOT-05（飞书整体接入）/ C12 | [P05](phases/P05/README.md) CURRENT / IMPLEMENTING：出站卡片 + 入站命令；adapter 模块；按 T02 编码 |
| NEXT_REVIEW | [S03](scenarios/S03-anniversary.md)、[S04](scenarios/S04-deadline-management.md)、[S05](scenarios/S05-payment-management.md)、[S14](scenarios/S14-maintenance-follow-up.md) | CAL-09、CAL-10、CAL-13、被选场景需要的trigger/integration能力 | 不自动进入实施；由用户另行决定是否进入下一阶段 |
| BACKLOG | S06—S13、S15—S17 | 其余OUTLINE能力项、C/X候选 | 已规划并保留，尚未进入近期细化 |

P01已完成稳定内核、S01/S02、五种基础日历、IN_APP通知、恢复和双进程验证并RELEASED。P02已完成工程结构与测试镜像并RELEASED（标签`v20260917-P02`）。P03已完成Jackson 3原生迁移并RELEASED（标签`v20260917-P03`）。P04已完成S02站内信标题和实例命令锁序并RELEASED（标签`v20261003-P04`）。当前 CURRENT 为 [P05](phases/P05/README.md)（IMPLEMENTING）：飞书整体接入（出站卡片 + 入站命令）；微信/钉钉/Telegram 等仍属 C12 后续。NEXT_REVIEW 中的业务场景须另建阶段并授权。

## 2. S01—S17总览

| 编号 | 场景 | 排期位置 | 契约状态 | 实现状态 | 详细规划或契约 |
| --- | --- | --- | --- | --- | --- |
| S01 | 通用提醒 | P01 | RELEASED | VERIFIED | [S01](scenarios/S01-reminder.md) |
| S02 | 周期待办 | P01 | RELEASED | VERIFIED | [S02](scenarios/S02-recurring-todo.md) |
| S03 | 生日与纪念日 | NEXT_REVIEW | OUTLINE | NOT_STARTED | [S03](scenarios/S03-anniversary.md) |
| S04 | 到期管理 | NEXT_REVIEW | OUTLINE | NOT_STARTED | [S04](scenarios/S04-deadline-management.md) |
| S05 | 缴费管理 | NEXT_REVIEW | OUTLINE | NOT_STARTED | [S05](scenarios/S05-payment-management.md) |
| S06 | 会议管理 | BACKLOG | OUTLINE | NOT_STARTED | [S06](scenarios/S06-meeting-management.md) |
| S07 | 团队共享任务 | BACKLOG | OUTLINE | NOT_STARTED | [S07](scenarios/S07-shared-team-task.md) |
| S08 | 轮值与交接 | BACKLOG | OUTLINE | NOT_STARTED | [S08](scenarios/S08-duty-handover.md) |
| S09 | 审批与升级 | BACKLOG | OUTLINE | NOT_STARTED | [S09](scenarios/S09-approval-escalation.md) |
| S10 | 目标与打卡 | BACKLOG | OUTLINE | NOT_STARTED | [S10](scenarios/S10-goal-checkin.md) |
| S11 | 事件跟进 | BACKLOG | OUTLINE | NOT_STARTED | [S11](scenarios/S11-event-follow-up.md) |
| S12 | 条件监控 | BACKLOG | OUTLINE | NOT_STARTED | [S12](scenarios/S12-condition-monitoring.md) |
| S13 | 汇总提醒 | BACKLOG | OUTLINE | NOT_STARTED | [S13](scenarios/S13-digest-reminder.md) |
| S14 | 保养与复查 | NEXT_REVIEW | OUTLINE | NOT_STARTED | [S14](scenarios/S14-maintenance-follow-up.md) |
| S15 | 资源预约 | BACKLOG | OUTLINE | NOT_STARTED | [S15](scenarios/S15-resource-reservation.md) |
| S16 | 外部日历同步 | BACKLOG | OUTLINE | NOT_STARTED | [S16](scenarios/S16-external-calendar-sync.md) |
| S17 | 智能创建入口 | BACKLOG | OUTLINE | NOT_STARTED | [S17](scenarios/S17-intelligent-creation.md) |

S17保留S编号用于需求追踪，但默认不是独立业务状态机，不注册普通ScenarioExtension。

## 3. C01—C19总览

详细边界和状态见[能力域索引](capabilities/README.md)。C14、C15、C19属于跨域平台或产品候选，不伪装成业务能力模块。

| 编号 | 能力或候选 | 主要归属 | 排期位置 | 实现状态 |
| --- | --- | --- | --- | --- |
| C01 | 扩展周期 | calendar | BACKLOG | NOT_STARTED |
| C02 | 工作日历 | calendar | BACKLOG | NOT_STARTED |
| C03 | 业务时间计时 | calendar | BACKLOG | NOT_STARTED |
| C04 | 相对时间与多阶段提醒 | calendar | NEXT_REVIEW | NOT_STARTED |
| C05 | 时间与条件组合 | trigger | BACKLOG | NOT_STARTED |
| C06 | 串行与并行协作 | workflow | BACKLOG | NOT_STARTED |
| C07 | 自动完成 | trigger | BACKLOG | NOT_STARTED |
| C08 | 时区扩展 | calendar | BACKLOG | NOT_STARTED |
| C09 | 提醒节制 | notification | BACKLOG | NOT_STARTED |
| C10 | 发生例外 | calendar | BACKLOG | NOT_STARTED |
| C11 | 有效区间与结束条件 | calendar | NEXT_REVIEW | NOT_STARTED |
| C12 | 多IM渠道 | notification | P05 | NOT_STARTED |
| C13 | 身份与权限 | collaboration | BACKLOG | NOT_STARTED |
| C14 | 缓存接入 | 跨域平台候选 | BACKLOG | NOT_STARTED |
| C15 | 前端与用户操作界面 | 产品接入候选 | BACKLOG | NOT_STARTED |
| C16 | 通知委托与多接收人 | collaboration | BACKLOG | NOT_STARTED |
| C17 | 全天日期任务 | calendar | BACKLOG | NOT_STARTED |
| C18 | 通知数据保留与删除 | notification | BACKLOG | NOT_STARTED |
| C19 | 幂等与审计数据生命周期 | 跨域平台候选 | BACKLOG | NOT_STARTED |

### 3.1 跨域候选的已知边界

- C14缓存：当前不创建cache模块，不预设模块名称或JimDB/Redis实现。出现真实性能问题后先确定缓存对象、键、故障回退和多副本失效，再审核是否需要缓存。
- C15前端：未来提供创建/预览/管理、待办操作、收件与未读界面；端类型、交互、登录和权限必须在实施前确定。P01 API不表示已有页面。
- C19数据生命周期：未来定义幂等保证期限、过期请求重放、审计保留、归档/清理批次、并发安全及首次响应副本中的敏感内容。P01不清理不等于永久保留。

## 4. 八个能力域与场景组合

| 场景 | 场景族 | 主要能力域 |
| --- | --- | --- |
| S01、S02 | basic | calendar、notification |
| S03、S04 | deadline | calendar、notification |
| S05 | deadline | calendar、trigger、notification、integration |
| S06 | collaboration | calendar、collaboration、notification、integration |
| S07 | collaboration | collaboration、notification |
| S08 | collaboration | calendar、collaboration、workflow、notification |
| S09 | workflow | collaboration、workflow、calendar、notification |
| S10 | basic | calendar、aggregation、notification |
| S11 | automation | trigger、integration、notification |
| S12 | automation | trigger、aggregation、integration、notification |
| S13 | automation | aggregation、notification |
| S14 | deadline | calendar、notification；完成事件驱动时增加trigger |
| S15 | collaboration | calendar、collaboration、integration、notification |
| S16 | automation | integration、trigger、calendar |
| S17 | 不新增场景模块 | intelligence及目标场景的全部能力 |

目标模块边界见[02](02-AI-CODING-GUIDE.md)，每个能力域当前和未来范围见[能力索引](capabilities/README.md)。模块名称是所有权规划，不表示模块已经创建；同一场景族先在模块内按包隔离，只有独立数据、生命周期、所有权或发布节奏形成后才拆模块。

## 5. 技术候选X01—X05

| 编号 | 技术候选 | 保留目的与实施前问题 | 状态 |
| --- | --- | --- | --- |
| X01 | MQ消息队列 | 未来跨服务事件、吞吐或积压需要；先验证MySQL队列瓶颈和外部接入，再比较MQ、Outbox及运维成本 | BACKLOG / OUTLINE / NOT_STARTED |
| X02 | 工作流引擎 | 支撑S09/C06的分支、汇合、退回和升级；先明确流程复杂度，再选择自有有限状态机或引擎 | BACKLOG / OUTLINE / NOT_STARTED |
| X03 | 动态加载插件 | 运行中增加/升级场景代码；需评估代码信任、依赖隔离、迁移、卸载和回滚 | BACKLOG / OUTLINE / NOT_STARTED |
| X04 | 工程结构与测试镜像 | 业务功能优先、技术职责次级的包结构；测试包镜像与每个顶层生产类型的所有者测试 | P02 / READY_FOR_IMPLEMENTATION / VERIFIED |
| X05 | Jackson 3原生迁移 | 在P02黄金契约基础上迁移到Boot 4 BOM管理的Jackson 3.1.5；保持HTTP、持久化JSON、幂等与哈希兼容 | P03 / READY_FOR_IMPLEMENTATION / VERIFIED |

现有MySQL持久化队列不等于MQ；稳定扩展注册不等于动态加载；存在workflow能力域不等于已经选择流程引擎。

## 6. 建议演进顺序

1. P01 的 S01、S02、稳定内核、时间Signal、IN_APP通知Action及双进程验证已完成并 RELEASED（标签 `v20260915-P01`）。
2. 完成P02的X04工程结构与测试镜像，形成可靠的所有者测试和JSON黄金基线。
3. P02发布后以独立阶段优先迁移X05 Jackson 3，不与包移动混合。
4. 从NEXT_REVIEW的S03/S04/S05/S14中由用户选择真实需求；先补齐相关calendar、trigger或integration能力契约。
5. P05 收敛并交付飞书 IM（C12 子集）与可配置投递渠道列表后，再按需接入其他 IM 键。
6. 团队身份和权限明确后再评审S07/S08/S09；其余场景按依赖和真实价值推进。

顺序只用于减少依赖跳跃，不是自动排期。没有用户选择、完整READY契约、CURRENT阶段和实施授权时，不得创建代码、空模块、空表、空Bean或假集成。

## 7. 状态与保留规则

S/C/X编号永久保留，不删除、不复用、不因重新排期丢失。取消、合并或废止必须保留原因和替代链接。新增规划先建立有内容的OUTLINE文档或跨域候选条目；尚未决定的内容明确写入“编码前必须决定”，不得由AI补造。

状态变更时同步更新场景/能力索引、对应文档、本路线图和受影响阶段。部分实现只能在条目内逐项记录，整体不得提前标VERIFIED。每个阶段DELIVERY必须列出新增/变更编号、已验证范围、剩余范围及下一次评审入口。

### 7.1 历史决策

| 日期 | 编号 | 决策 | 结果 |
| --- | --- | --- | --- |
| 2026-09-10 | S01、S02 | 底层对象调整为通用定义、实例、Signal、TransitionPlan和Action | 2026-09-12复审纳入2.0契约 |
| 2026-09-12 | S01、S02 | 本地固定身份但首期交付完整稳定核心；确认S02首版默认与控制规则 | 契约READY，工程NOT_STARTED，未授权编码 |
| 2026-09-12 | S01—S17、C01—C19、X01—X03 | 规划迁入场景/能力永久目录并采用三维状态 | 文档基线2.2；没有新增实现或P02范围 |
| 2026-09-15 | P01、S01、S02、CAL-01—CAL-05、NOT-01—NOT-03 | 用户人工验收通过；阶段 RELEASED | Git 标签 `v20260915-P01`；implementationStatus=VERIFIED |
| 2026-09-16 | X04、X05 | 用户要求先收敛工程结构、测试镜像和Jackson 3迁移方案 | 文档基线2.3；P02只实施X04，X05排在P02发布后的独立阶段 |
| 2026-09-17 | P02、X04 | 用户人工验收通过；阶段 RELEASED | Git 标签 `v20260917-P02`；X04 implementationStatus=VERIFIED |
| 2026-09-17 | P03、X05 | 用户要求建阶段；P03 为 CURRENT / READY | 对应 Jackson 3；尚未授权实施 |
| 2026-09-17 | P03、X05 | 用户人工验收通过；阶段 RELEASED | Git 标签 `v20260917-P03`；X05 implementationStatus=VERIFIED |
| 2026-10-02 | P04 | 用户确认催办标题与实例命令锁序；阶段改为 READY | 尚未授权实施 |
| 2026-10-02 | P04 | 用户授权实施；阶段改为 IMPLEMENTING | 按 T01—T05 编码，未建 DELIVERY |
| 2026-10-02 | P04 | 用户授权开始全量验收；阶段改为 VERIFYING | 单元测试与打包通过；真库全量 92 项中 2 项就绪失败，未 RELEASED |
| 2026-10-02 | P04 | 用户要求处理遗留 event 信号并重跑 | 信号改为 IGNORED 后真库 92 项通过；双进程 7 项中性能门槛失败，未 RELEASED |
| 2026-10-03 | P04 | 用户要求性能库与测试逻辑一致 | 性能门槛测量前暂停外来绑定并推迟到期行；`PerfGateDualProcessIT` 2 项通过；未 RELEASED |
| 2026-10-03 | P04 | 用户人工验收通过；阶段 RELEASED | Git 标签 `v20261003-P04`；DELIVERY/IMPLEMENTATION/README 冻结 |
| 2026-10-06 | P05、C12、NOT-05 | 用户选定飞书 IM 为 P05；要求通用 IM 框架、默认仅站内信、配置开启飞书 | 阶段包 DRAFT / CURRENT；微信/钉钉/Telegram 预留键，不实现 |
| 2026-10-06 | P05 | 用户要求飞书整体接入：出站消息 + 入站卡片/文字命令同步 | README/NOT-05 扩为出站 interactive 与入站 card.action.trigger；文字回复为辅路径 |
| 2026-10-06 | P05 | 用户确认：同期交付出站卡片+入站按钮；S02 三按钮；稍后=+1h；文字回复不实现 | S01 无按钮；§6 其余项采用已采纳默认，待 T01 写入正式契约 |
| 2026-10-06 | P05 | 用户要求第三方 SDK 由通用 Maven 模块统一管理；notification 依赖该模块用飞书 | 模块名暂定 `timeimprint-task-adapter`；T01 修订 02 模块表 13→14 |
| 2026-10-06 | P05、T01 | 用户同意 §6 默认并执行 T01；阶段 READY | 02/03/06/07/CAP03 已写入；NOT-05 READY_FOR_IMPLEMENTATION；未授权编码 |
| 2026-10-06 | P05 | 用户审核全部 Tx 通过并授权实施；阶段 IMPLEMENTING | baselineGitRef=`9d9dc87…`；从 T02 起编码 |

## 8. 下一场景进入实施的检查

1. 用户从NEXT_REVIEW或BACKLOG明确选择范围。
2. 场景文档从OUTLINE进入DRAFT，确定scenarioKey、配置、状态、命令、参与人、触发、Action、专有数据和异常行为。
3. 所依赖能力项达到READY_FOR_IMPLEMENTATION；能力域存在但能力项仍OUTLINE时不能编码。
4. 完成INV-01—INV-09、公共API、公共表、稳定SPI和兼容性影响分析。
5. 建立阶段README、IMPLEMENTATION及验收编号；只有该阶段成为唯一CURRENT且获得用户授权后开始编码。
6. 完成后以DELIVERY真实证据更新implementationStatus；没选中的规划继续保留。

自动交接检查必须核对S01—S17、C01—C19、X01—X05连续且每个编号具有唯一主要入口。检查只证明规划未丢失，不代表候选已经批准或实现。
