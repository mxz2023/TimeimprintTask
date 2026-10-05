# 中文术语与标识对照（人工查阅）

本表对应2.3文档，帮助非技术读者理解英文标识；正式行为仍以01—10及场景/能力永久文档为准。同一组数据库字段采用合并说明，避免逐行重复。

## 1. 平台与工程

| 标识 | 中文含义 |
| --- | --- |
| TimeImprintTask / timeimprint-task | 通用任务平台及工程名前缀 |
| mxz（历史） | 曾用于项目自定义Java类名前缀；现已取消，新代码不再使用 |
| Java 21 LTS / JDK 21 | 开发语言长期支持版本 / Java开发工具包版本 |
| Spring AI 2.0.x | AI应用框架稳定版本线；实施时固定具体补丁版，不表示首期已启用AI业务能力 |
| Spring Boot 4.0.8 | 应用启动、配置和装配框架的当前固定版本 |
| MyBatis Starter 4.0.1 | Java与MySQL之间的数据访问映射框架版本 |
| MySQL 9.7 LTS / 9.7.3 image | 一期目标数据库长期支持版本线 / 固定的官方MySQL Server Docker镜像补丁制品；镜像版本不要求与SELECT VERSION()字符串相同 |
| Maven / POM / Wrapper | 构建工具 / 工程配置 / 固定构建入口 |
| API / DTO | 应用接口 / 请求响应传输对象 |
| G01 | T02开始业务实现前的编码契约冻结门槛，不是独立实施阶段 |
| P01 / P02 / P03 | 第一期 / 第二期 / 第三期阶段编号；编号递增且不复用 |
| CURRENT / DRAFT / IMPLEMENTING / VERIFYING / RELEASED | 当前唯一开发阶段 / 规则编写中 / 实施中 / 全量验证中 / 已形成发布基线 |
| READY_FOR_IMPLEMENTATION | 场景契约已经具备实施条件，但不表示场景代码已完成 |
| planningPosition | 排期位置；回答条目属于当前阶段、下一批评审还是长期规划 |
| contractStatus | 契约成熟度；回答规则只是提纲、正在细化、可编码还是已经发布 |
| implementationStatus | 实现状态；回答工程尚未开始、正在实施、已经验证或已经废止 |
| OUTLINE / DRAFT | 已保存规划但不足以编码 / 正在细化完整规则且仍禁止编码 |
| NEXT_REVIEW / BACKLOG / NONE | 下一批优先细化但未获实施授权 / 长期保留 / 已取消、合并或废止并保留去向 |
| NOT_STARTED / IN_PROGRESS / VERIFIED / DEPRECATED | 尚未开始 / 已授权实施但未完成验收 / 有DELIVERY通过证据 / 不再推荐新增使用但保留兼容 |
| ADR / PROPOSED / ACCEPTED / SUPERSEDED / REJECTED | 核心架构决策记录 / 提议中 / 已接受 / 已被后续决策替代 / 已拒绝 |
| baselineGitRef | 当前阶段开始写代码前记录的不可变Git提交或标签，用于比较阶段前后差异 |
| SQL / DDL / Flyway | 数据操作语言 / 表结构语句 / 数据库版本迁移工具 |
| SPI / extension | 稳定扩展接口 / 基于接口接入的新能力或场景 |
| Maven Enforcer / ArchUnit | 依赖规则检查 / Java架构边界测试 |
| Unit / Contract / IT | 单元测试 / 接口契约测试 / 集成测试 |
| business-first package / 业务优先包 | 模块内先按业务功能分包，再按技术职责分层；不是先按controller/service/repository横向堆放 |
| test mirror / 测试镜像 | 测试类与被测生产类使用完全相同的Java包路径 |
| owner test / 所有者测试 | 一个顶层生产类型对应的主要测试入口；负责证明其核心行为或契约，不等于只能有一个测试文件 |
| characterization test / 特征测试 | 重构前记录现有可观察行为的测试，用于证明拆分或移动没有无意改变行为 |
| golden contract / 黄金契约 | 以发布基线的输入输出样例固定兼容行为，升级底层实现时逐项对比 |
| Jackson 3.1.5 / JsonMapper | P02之后专项迁移的JSON库版本 / 生产代码统一注入的Jackson 3 JSON Mapper类型 |
| canonical JSON / 规范化JSON | 由项目显式规定属性、空值、数字和编码规则的稳定JSON表示，用于哈希、幂等和动作键，不依赖库默认顺序 |
| mysql-it / dual-process-it | 真MySQL测试配置 / 两个真实Java进程的测试配置 |
| PASS / FAIL / BLOCKED / NOT_RUN | 通过 / 失败 / 被条件阻塞 / 尚未执行 |
| REVIEWED / READY（T00） / ENV_PENDING / OUT_OF_SCOPE | 文档已评审 / 文档具备实施条件 / 环境待实测或注入 / 不属于当前交付范围；T00的READY不等于队列状态READY |

## 2. 核心业务对象

| 标识 | 中文含义 |
| --- | --- |
| TaskDefinition / definition | 任务定义；描述长期配置、场景、参与人和触发绑定 |
| TaskInstance / instance | 一次具体发生、办理或跟进 |
| Participant | 与定义或实例有关的参与主体 |
| owner / assignee / collaborator | 所有者 / 办理人 / 协作者 |
| approver / follower / recipient | 审批人 / 关注人 / 通知接收人 |
| Signal | 时间、事件、条件、依赖或外部输入形成的持久化触发事实 |
| TransitionPlan | 场景纯计算得到的声明式状态变化计划，由平台统一提交 |
| Transition | 已经提交的状态变化历史 |
| Action / Action Job | 状态变化产生的可靠待执行动作 |
| Action Attempt / attempt | 一次Action技术执行尝试，不等于一次业务催办 |
| Policy | 权限、频控、静默、合规和执行资格策略 |
| ActorContext | 可信接入层构造的租户、调用主体和来源上下文 |
| ActorContextProvider | 根据当前运行环境提供ActorContext的接入接口；首期local实现返回配置中的固定身份 |
| ScenarioExtension | 场景配置、初始状态、迁移和投影的扩展契约 |
| ScenarioDataMutation | TransitionPlan中声明的强类型场景专有数据变更，不包含任意SQL或脚本 |
| ScenarioDataMaterializer | 在平台事务内把ScenarioDataMutation写入所属场景专有表的注册物化器 |
| TriggerProvider | 将时间、事件、条件或依赖转换为Signal的扩展契约 |
| TaskCommandHandler | 处理强类型业务命令并返回TransitionPlan的扩展契约 |
| ActionHandler | 执行通知、Webhook或同步等动作的扩展契约 |
| Applied / NoChange / Rejected | 扩展计算后明确表示已产生变化 / 无需变化 / 业务拒绝的三类结果 |
| contractVersion / registration key | Java扩展契约大版本 / 用于保证扩展路由唯一的组合注册键；不等同schemaVersion |
| policyKey / phase / order | 策略标识 / 执行阶段 / 同阶段稳定执行顺序 |
| INV-01—INV-09 | 02中跨阶段稳定的核心架构不变量；修改或删除必须按核心模型变化重新评审 |

## 3. 状态和执行语义

| 标识 | 中文含义 |
| --- | --- |
| ACTIVE / PAUSED / RETIRED | 定义可运行 / 已暂停 / 已永久退役 |
| WAITING / ACTIVE / TERMINAL | 实例等待发生 / 正在办理 / 已到业务终态 |
| PENDING / COMPLETED / SKIPPED | 周期待办待处理 / 已完成 / 已跳过 |
| PLANNED / TRIGGERED / CANCELLED（场景状态） | 已规划尚未发生 / 提醒已触发 / 尚未发生的实例被系统取消；不等同用户跳过 |
| WAITING_APPROVAL / APPROVED / REJECTED | 未来审批夹具的待审批 / 已批准 / 已拒绝 |
| READY / RUNNING / RETRY_WAIT | 技术工作待领取 / 执行中 / 等待重试 |
| PROCESSING / COMPLETED（command dedup） | 幂等请求在当前事务中处理 / 已保存可稳定重放的确定结果；PROCESSING不得单独提交 |
| SUCCEEDED / DEAD | 技术执行成功 / 尝试耗尽或永久失败 |
| CANCELLED / EXPIRED | 因业务屏障取消 / 超过有效期 |
| UNKNOWN | 外部副作用已开始但结果无法确认，不自动重发 |
| LOCAL_TRANSACTIONAL | 效果写本地能力表，并与Action结果同事务提交 |
| EXTERNAL | 调用不能随本地事务回滚的外部系统 |
| POLICY_BLOCKED | 副作用开始前被父状态或策略阻断，可安全退回 |
| effectStartedAt / effect_started_at | 外部调用前写入的开始证据 |
| executionToken / execution_token | Worker本次租约所有权令牌；旧令牌不能回写 |
| executionTokenFingerprint | 原始executionToken做SHA-256后的前12位小写十六进制，仅供日志关联，不能用于CAS |
| leaseUntil / lease_until | 当前租约截止时间 |
| leaseOwner / lease_owner | 领取工作进程的INSTANCE_ID，仅用于诊断，不代替executionToken所有权校验 |
| storedStatus / effectiveStatus | 数据库保存的队列状态 / 结合当前控制代次、生命周期和副作用开始证据推导的对外有效状态 |
| revision | 资源乐观锁版本；工程POM中同名词另指制品版本 |
| controlGeneration / control_generation | 定义暂停、恢复或退役时递增的控制代次，用于让旧异步工作立即失效 |
| definitionControlGeneration / definition_control_generation | 实例、Signal或Action创建时记录的定义控制代次快照 |
| CAS | 带旧版本/令牌条件更新，影响0行表示已失去提交资格 |
| redrive / redriveNo | 对允许类型的DEAD技术工作创建关联新行后重新执行 / 该根对象的人工重驱序号 |
| parentSignalId / parentActionJobId | 人工重驱新行指向原Signal或Action的历史关联 |

## 4. 场景、触发与时间

| 标识 | 中文含义 |
| --- | --- |
| scenarioKey / scenario_key | 场景稳定标识，如reminder、recurring_todo |
| scenarioState / scenario_state | 场景自定义状态；内核保存但不解释枚举 |
| schemaVersion / schema_version | 配置或消息结构版本 |
| commandKey / command_key | 动态业务命令标识，如complete、skip、snooze、approve |
| providerKey / provider_key | Signal来源提供者标识 |
| handlerKey / handler_key | Action处理器标识 |
| ONCE / DAILY / WEEKLY / MONTHLY / EVERY_N_DAYS | 一次 / 每天 / 每周 / 每月 / 每N个自然日 |
| localDate / startDate / localTime / weekday / dayOfMonth / intervalDays | 一次日期 / 循环锚点日期 / 本地时刻 / ISO星期1—7 / 月份日期1—31 / N日间隔1—3650 |
| Asia/Shanghai | 一期业务时区，即北京时间 |
| occurrenceAt / occurrence_at | 规则名义发生时间 |
| occurrenceKey / occurrence_key | 一次发生的稳定去重标识 |
| dueAt / due_at | 待办实例截止时间 |
| chaseOffsetsMinutes | S02各次催办相对dueAt的分钟偏移列表；首版默认60、240、720 |
| notificationExpireAfterMinutes | S02提醒从dueAt起可执行的分钟数；首版默认1440 |
| maxSnoozeCount / snoozeCount | S02允许稍后提醒的最大次数 / 当前实例已经使用的次数 |
| actionGeneration | S02提醒动作批次号；每次成功snooze后递增，用于隔离新旧Action |
| bindingKey / binding_key | 一个定义内部稳定识别触发绑定的名称；S01/S02固定为primary |
| scheduleGeneration / schedule_generation | 时间规则批次号；修改规则时递增 |
| cursor / cursor_json / next_fire_at | 分页或规划推进位置 / 持久化规划游标 / 下一候选时间 |
| availableAt / available_at | Action最早可领取时间 |
| nextAttemptAt / next_attempt_at | 技术失败后下一次可重试时间 |
| expiresAt / expires_at | 动作有效期截止时间 |
| requestId / request_id | 客户端幂等请求标识 |
| signalKey / signal_key | 某来源下Signal的稳定去重标识 |
| actionKey / action_key | 某处理器下Action的稳定去重标识 |
| timeoutSeconds | Action处理器声明的单次执行总超时预算，必须小于租约减安全余量 |
| payloadHash / requestHash / configHash / snapshotHash | 用于识别相同键是否对应相同内容的摘要 |
| INITIAL / CHASE / ESCALATION | 首次提醒 / 催办 / 升级提醒用途 |
| channelKey / delivery-channels | 通知投递渠道稳定键（如 IN_APP、FEISHU）/ 运行配置中的启用渠道列表；默认仅 IN_APP |
| FEISHU / feishu_im_notification | 飞书 IM 渠道键 / 飞书通知 ActionHandler 键（P05） |
| interactive / card.action.trigger | 飞书交互卡片消息类型 / 卡片回传交互回调（入站命令主路径） |
| im.message.receive_v1 | 飞书接收消息事件（文字回复辅路径） |
| open_id | 飞书用户标识；由配置把平台 recipientId 映射到该值 |
| ALL_MISSED | 非暂停停机恢复时按顺序补齐每个漏掉的发生事实；过期通知只记录EXPIRED，不补发收件 |
| MAX_PARTICIPANTS_PER_SCOPE / MAX_RECIPIENTS_PER_INSTANCE | 单个定义或实例参与人上限50 / 单个实例最终接收人上限10 |
| MAX_TRIGGER_BINDINGS_PER_DEFINITION / MAX_OCCURRENCES_PER_WRITE_TX | 单个定义触发绑定上限8 / 单笔写事务发生总数上限100；S01/S02另限一个calendar绑定 |
| MAX_ACTIONS_PER_TRANSITION / MAX_ACTIONS_PER_WRITE_TX | 单个迁移计划Action上限100 / 单笔写事务Action总数上限500 |
| MAX_SCENARIO_MUTATIONS_PER_TRANSITION / MAX_SCENARIO_MUTATION_BYTES | 单个迁移计划场景专有变更上限32 / 规范化UTF-8载荷合计上限65536字节 |
| MAX_JSON_VALUE_BYTES / MAX_TRANSITION_PLAN_BYTES | 单个JSON值上限65536字节 / 一个规范化迁移计划总上限1048576字节 |
| MAX_MUTATED_ROWS_PER_WRITE_TX / BUSINESS_TX_TIMEOUT_SECONDS | 单笔写事务总变更行上限2000 / 业务事务默认超时5秒 |
| ACTION_LEASE_SAFETY_SECONDS / MAX_MANUAL_REDRIVES | Action租约必须预留的默认安全余量5秒 / 单个根对象最多人工重驱3次 |

## 5. API与安全

| 标识 | 中文含义 |
| --- | --- |
| `/api/v1` | 面向业务调用方的公开接口前缀 |
| `/internal/v1` | 仅可信内部网络使用的诊断和Signal接口前缀 |
| PreviewResult / ScenarioMetadataView | 时间规则规范化与发生预览结果 / 已装配场景的公开能力说明 |
| TaskDefinitionView / TaskInstanceView / InboxView | 定义公开视图 / 实例公开视图 / 站内收件视图 |
| SignalDiagnosticView / ActionJobDiagnosticView | 内部Signal安全诊断视图 / 内部Action安全诊断视图 |
| E01—E13 / I01—I07 | 04中的公开端点编号 / 内部端点编号 |
| keyset pagination / asOf | 以稳定排序键继续翻页的弱一致分页 / 响应生成时间，不代表数据库快照时间 |
| DeliverySummary / deliveryState | 实例全部Action、入箱和未读数量汇总 / 按固定优先级得出的执行状态，不等同用户已读 |
| NOT_SCHEDULED / IN_PROGRESS / PARTIALLY_DELIVERED / DELIVERED / FAILED | 尚无Action / 仍有待处理动作 / 部分成功 / Handler全部成功 / 无成功且存在失败 |
| application/json | HTTP请求采用的JSON媒体类型 |
| traceId / trace_id | 一次请求或审计链路的追踪标识 |
| X-Debug-Actor-Id | 仅test profile允许的调试身份头；local profile使用配置中的固定身份，不允许请求覆盖 |
| SERVER_ADDRESS / SHUTDOWN_GRACE_SECONDS | HTTP监听地址 / 正常停机等待已开始工作的总宽限秒数 |
| liveness / readiness / graceful shutdown | 进程存活状态 / 是否可接收流量 / 先拒绝新工作再等待已开始工作闭合的优雅停机 |
| UNAUTHENTICATED / FORBIDDEN | 未认证 / 已认证但无权限 |
| RESOURCE_NOT_FOUND / EXTENSION_NOT_FOUND | 资源不可见或不存在 / 扩展未装配 |
| IDEMPOTENCY_CONFLICT / REVISION_CONFLICT / STATE_CONFLICT | 幂等内容冲突 / 版本冲突 / 当前状态不允许操作 |
| COMMAND_NOT_SUPPORTED / UNSUPPORTED_SCHEMA_VERSION | 场景不支持命令 / 结构版本不支持 |
| RETRY_LATER / INTERNAL_ERROR | 稍后以相同requestId重试 / 未预期内部错误 |

## 6. 数据表与关键字段组

| 标识 | 中文含义 |
| --- | --- |
| tt_task_definition | 任务定义公共表 |
| tt_trigger_binding | 定义与触发器绑定及规划游标表 |
| tt_task_instance | 一次任务实例及不可变场景快照表 |
| tt_task_participant | 定义或实例参与主体表 |
| tt_task_signal | Signal接收、去重、租约和处理结果表 |
| tt_task_transition | 定义或实例的版本化迁移历史表 |
| tt_action_job | 待执行Action及租约、结果表 |
| tt_action_attempt | Action每次执行尝试和外部副作用证据表 |
| tt_command_dedup | 命令请求幂等结果表 |
| tt_audit_log | 安全审计摘要表 |
| tt_notification | 通知内容和来源迁移表，属于通知能力 |
| tt_inbox | 接收人的站内信和首次已读时间表，属于通知能力 |
| tenant_id / definition_id / instance_id | 租户 / 任务定义 / 任务实例标识 |
| principal_type / principal_id / role_code | 参与主体类型 / 标识 / 角色 |
| control_state / lifecycle_category / terminal_at | 定义控制状态 / 实例生命周期类别 / 终态时间 |
| payload_json / config_json / scenario_snapshot_json | 版本化Signal载荷 / 配置 / 场景快照JSON |
| status / process_status / result_code | Action状态 / Signal处理状态 / 结果原因 |
| attempt_count / max_attempts / attempt_no | 已消耗尝试数 / 上限 / 永不复用的尝试序号 |
| created_at / updated_at / occurred_at / received_at / processed_at | 创建 / 更新 / 发生 / 接收 / 处理时间 |
| transition_id / from_revision / to_revision | 迁移标识 / 迁移前版本 / 迁移后版本 |
| from_control_state / to_control_state | 定义级迁移前后的控制状态；实例级迁移为空 |
| read_at | 收件第一次被标记已读的时间 |
| provider_reference | 外部渠道受理号或可用于查证的引用 |
| safe_summary / result_summary / outcome_summary | 已脱敏且受大小限制的诊断摘要 |

所有`uk_*`表示唯一索引，用于防重复；所有`ix_*`表示普通查询索引。字段的精确类型、可空性、索引列顺序和约束以05为准。

## 7. 模块名称

| 模块 | 中文职责 |
| --- | --- |
| timeimprint-task-common | 无业务状态的通用工具 |
| timeimprint-task-domain | HTTP请求/响应DTO、统一信封与错误码；不含Controller |
| timeimprint-task-service-kernel | 纯Java任务领域内核 |
| timeimprint-task-service-extension-api | 场景、触发、命令、动作和策略扩展契约 |
| timeimprint-task-service-application | 用例、权限、幂等、事务和迁移提交编排 |
| timeimprint-task-service-runtime | Planner、Signal和Action后台运行时 |
| timeimprint-task-service-storage-mysql | MySQL存储适配器和平台公共迁移 |
| timeimprint-task-service-capability-calendar | 日历规则能力 |
| timeimprint-task-service-capability-notification | 通知意图、站内信及可配置 IM 渠道适配（P05 起含飞书） |
| timeimprint-task-service-scenario-basic | 一期reminder与recurring_todo场景 |
| timeimprint-task-gateway | 接入编排：ActorContext、DTO/错误转换；不含 Controller |
| timeimprint-task-web | HTTP Controller和统一Web错误处理 |
| timeimprint-task-boot-loader | 启动、配置、装配、迁移加载和集成测试入口 |
| timeimprint-task-service-capability-trigger/collaboration/workflow/aggregation/integration/intelligence | 未来触发、协作、流程、汇总、集成、智能能力模块 |
| timeimprint-task-service-scenario-deadline/collaboration/workflow/automation | 未来到期、协作、流程、自动化场景模块 |

当前没有任何cache模块。C14只表示未来可能存在的缓存需求，尚未决定技术、名称或模块位置。
