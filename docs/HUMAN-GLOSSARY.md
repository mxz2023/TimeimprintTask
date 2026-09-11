# 中文术语与标识对照（人工查阅）

本表对应2.0文档，帮助非技术读者理解英文标识；正式行为仍以01—10为准。同一组数据库字段采用合并说明，避免逐行重复。

## 1. 平台与工程

| 标识 | 中文含义 |
| --- | --- |
| JoyTask / joytask | 通用任务平台及工程名前缀 |
| Java / JDK 17 | 开发语言 / Java开发工具包版本 |
| Spring Boot | 应用启动、配置和装配框架 |
| MyBatis | Java与MySQL之间的数据访问映射框架 |
| MySQL 8.4 | 一期目标关系型数据库版本 |
| Maven / POM / Wrapper | 构建工具 / 工程配置 / 固定构建入口 |
| API / DTO | 应用接口 / 请求响应传输对象 |
| SQL / DDL / Flyway | 数据操作语言 / 表结构语句 / 数据库版本迁移工具 |
| SPI / extension | 稳定扩展接口 / 基于接口接入的新能力或场景 |
| Maven Enforcer / ArchUnit | 依赖规则检查 / Java架构边界测试 |
| Unit / Contract / IT | 单元测试 / 接口契约测试 / 集成测试 |
| mysql-it / dual-process-it | 真MySQL测试配置 / 两个真实Java进程的测试配置 |
| PASS / FAIL / BLOCKED / NOT_RUN | 通过 / 失败 / 被条件阻塞 / 尚未执行 |

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
| ScenarioExtension | 场景配置、初始状态、迁移和投影的扩展契约 |
| TriggerProvider | 将时间、事件、条件或依赖转换为Signal的扩展契约 |
| TaskCommandHandler | 处理强类型业务命令并返回TransitionPlan的扩展契约 |
| ActionHandler | 执行通知、Webhook或同步等动作的扩展契约 |

## 3. 状态和执行语义

| 标识 | 中文含义 |
| --- | --- |
| ACTIVE / PAUSED / RETIRED | 定义可运行 / 已暂停 / 已永久退役 |
| WAITING / ACTIVE / TERMINAL | 实例等待发生 / 正在办理 / 已到业务终态 |
| PENDING / COMPLETED / SKIPPED | 周期待办待处理 / 已完成 / 已跳过 |
| WAITING_APPROVAL / APPROVED / REJECTED | 未来审批夹具的待审批 / 已批准 / 已拒绝 |
| READY / RUNNING / RETRY_WAIT | 技术工作待领取 / 执行中 / 等待重试 |
| SUCCEEDED / DEAD | 技术执行成功 / 尝试耗尽或永久失败 |
| CANCELLED / EXPIRED | 因业务屏障取消 / 超过有效期 |
| UNKNOWN | 外部副作用已开始但结果无法确认，不自动重发 |
| LOCAL_TRANSACTIONAL | 效果写本地能力表，并与Action结果同事务提交 |
| EXTERNAL | 调用不能随本地事务回滚的外部系统 |
| POLICY_BLOCKED | 副作用开始前被父状态或策略阻断，可安全退回 |
| effectStartedAt / effect_started_at | 外部调用前写入的开始证据 |
| executionToken / execution_token | Worker本次租约所有权令牌；旧令牌不能回写 |
| leaseUntil / lease_until | 当前租约截止时间 |
| revision | 资源乐观锁版本；工程POM中同名词另指制品版本 |
| CAS | 带旧版本/令牌条件更新，影响0行表示已失去提交资格 |

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
| Asia/Shanghai | 一期业务时区，即北京时间 |
| occurrenceAt / occurrence_at | 规则名义发生时间 |
| occurrenceKey / occurrence_key | 一次发生的稳定去重标识 |
| dueAt / due_at | 待办实例截止时间 |
| scheduleGeneration / schedule_generation | 时间规则批次号；修改规则时递增 |
| cursor / cursor_json / next_fire_at | 分页或规划推进位置 / 持久化规划游标 / 下一候选时间 |
| availableAt / available_at | Action最早可领取时间 |
| nextAttemptAt / next_attempt_at | 技术失败后下一次可重试时间 |
| expiresAt / expires_at | 动作有效期截止时间 |
| requestId / request_id | 客户端幂等请求标识 |
| signalKey / signal_key | 某来源下Signal的稳定去重标识 |
| actionKey / action_key | 某处理器下Action的稳定去重标识 |
| payloadHash / requestHash / configHash / snapshotHash | 用于识别相同键是否对应相同内容的摘要 |
| INITIAL / CHASE / ESCALATION | 首次提醒 / 催办 / 升级提醒用途 |

## 5. API与安全

| 标识 | 中文含义 |
| --- | --- |
| `/api/v1` | 面向业务调用方的公开接口前缀 |
| `/internal/v1` | 仅可信内部网络使用的诊断和Signal接口前缀 |
| E01—E13 / I01—I05 | 04中的公开端点编号 / 内部端点编号 |
| application/json | HTTP请求采用的JSON媒体类型 |
| traceId / trace_id | 一次请求或审计链路的追踪标识 |
| X-Debug-Actor-Id | 仅本地和测试环境允许的调试身份头 |
| UNAUTHENTICATED / FORBIDDEN | 未认证 / 已认证但无权限 |
| RESOURCE_NOT_FOUND / EXTENSION_NOT_FOUND | 资源不可见或不存在 / 扩展未装配 |
| IDEMPOTENCY_CONFLICT / REVISION_CONFLICT / STATE_CONFLICT | 幂等内容冲突 / 版本冲突 / 当前状态不允许操作 |
| COMMAND_NOT_SUPPORTED / UNSUPPORTED_SCHEMA_VERSION | 场景不支持命令 / 结构版本不支持 |
| RETRY_LATER / INTERNAL_ERROR | 稍后以相同requestId重试 / 未预期内部错误 |

## 6. 数据表与关键字段组

| 标识 | 中文含义 |
| --- | --- |
| jt_task_definition | 任务定义公共表 |
| jt_trigger_binding | 定义与触发器绑定及规划游标表 |
| jt_task_instance | 一次任务实例及不可变场景快照表 |
| jt_task_participant | 定义或实例参与主体表 |
| jt_task_signal | Signal接收、去重、租约和处理结果表 |
| jt_task_transition | 定义或实例的版本化迁移历史表 |
| jt_action_job | 待执行Action及租约、结果表 |
| jt_action_attempt | Action每次执行尝试和外部副作用证据表 |
| jt_command_dedup | 命令请求幂等结果表 |
| jt_audit_log | 安全审计摘要表 |
| jt_notification | 通知内容和来源迁移表，属于通知能力 |
| jt_inbox | 接收人的站内信和首次已读时间表，属于通知能力 |
| tenant_id / definition_id / instance_id | 租户 / 任务定义 / 任务实例标识 |
| principal_type / principal_id / role_code | 参与主体类型 / 标识 / 角色 |
| control_state / lifecycle_category / terminal_at | 定义控制状态 / 实例生命周期类别 / 终态时间 |
| payload_json / config_json / scenario_snapshot_json | 版本化Signal载荷 / 配置 / 场景快照JSON |
| status / process_status / result_code | Action状态 / Signal处理状态 / 结果原因 |
| attempt_count / max_attempts / attempt_no | 已消耗尝试数 / 上限 / 永不复用的尝试序号 |
| created_at / updated_at / occurred_at / received_at / processed_at | 创建 / 更新 / 发生 / 接收 / 处理时间 |
| transition_id / from_revision / to_revision | 迁移标识 / 迁移前版本 / 迁移后版本 |
| read_at | 收件第一次被标记已读的时间 |
| provider_reference | 外部渠道受理号或可用于查证的引用 |
| safe_summary / result_summary / outcome_summary | 已脱敏且受大小限制的诊断摘要 |

所有`uk_*`表示唯一索引，用于防重复；所有`ix_*`表示普通查询索引。字段的精确类型、可空性、索引列顺序和约束以05为准。

## 7. 模块名称

| 模块 | 中文职责 |
| --- | --- |
| joytask-common | 无业务状态的通用工具 |
| joytask-api | 稳定对外接口和DTO |
| joytask-service-kernel | 纯Java任务领域内核 |
| joytask-service-extension-api | 场景、触发、命令、动作和策略扩展契约 |
| joytask-service-application | 用例、权限、幂等、事务和迁移提交编排 |
| joytask-service-runtime | Planner、Signal和Action后台运行时 |
| joytask-service-storage-mysql | MySQL存储适配器和平台公共迁移 |
| joytask-service-capability-calendar | 日历规则能力 |
| joytask-service-capability-notification | 通知意图、站内信及未来渠道适配 |
| joytask-service-scenario-basic | 一期reminder与recurring_todo场景 |
| joytask-api-gateway | 对外API实现与DTO/错误转换 |
| joytask-web | HTTP Controller和统一Web错误处理 |
| joytask-boot-loader | 启动、配置、装配、迁移加载和集成测试入口 |
| joytask-service-capability-trigger/collaboration/workflow/aggregation/integration/intelligence | 未来触发、协作、流程、汇总、集成、智能能力模块 |
| joytask-service-scenario-deadline/collaboration/workflow/automation | 未来到期、协作、流程、自动化场景模块 |

当前没有任何cache模块。C14只表示未来可能存在的缓存需求，尚未决定技术、名称或模块位置。
