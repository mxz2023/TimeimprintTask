# 05 · 通用任务平台数据库契约

> 阅读入口与阶段状态见[00开发导航](00-READING-ORDER.md)。本文是表、字段、约束、索引和跨表不变量的正式来源；实际DDL仍须由03固定的MySQL镜像集成测试验证。

版本2.3；前：[API](04-API.md)，后：[运行时](06-SCHEDULING.md)。本文定义MySQL 9.7 LTS逻辑结构、所有权、唯一键、索引和跨表不变量。具体Flyway SQL必须逐项实现本文并由真库测试证明，不能仅依据文档声称可运行。

## 1. 存储原则

- InnoDB、utf8mb4、utf8mb4_bin；业务时间使用DATETIME(0)保存UTC。
- ID使用BIGINT UNSIGNED自增，对外转换成字符串；revision上限为9007199254740991。
- 公共表只保存跨场景稳定事实；场景字段进入场景专有表或版本化scenario JSON。专有表只能由注册的ScenarioDataMaterializer在平台迁移事务中通过本场景存储适配器写入。
- JSON只用于扩展配置、快照、动作载荷和安全摘要；关键状态、时间、归属、版本、租约和查询条件必须是独立列。
- JSON写入前必须通过key + schemaVersion转换和校验；数据库JSON类型不是业务校验替代品。
- 不物理级联删除业务历史。外键默认RESTRICT；清理、归档和数据保留另行审核。
- 所有写入显式提供created_at/updated_at，不依赖`ON UPDATE CURRENT_TIMESTAMP`形成隐式业务时间。
- 当前不创建cache表、旧plan/plugin表或未来场景空表。

## 2. 表与所有权

### 2.1 平台公共表：storage-mysql所有

| 表 | 稳定职责 |
| --- | --- |
| `tt_task_definition` | 任务定义、场景配置、控制状态和revision |
| `tt_trigger_binding` | 定义绑定的触发器、配置版本、时间游标和规则批次 |
| `tt_task_instance` | 一次发生或办理实例、通用生命周期、场景状态和快照 |
| `tt_task_participant` | 定义级或实例级参与主体及角色 |
| `tt_task_signal` | 时间、事件、条件、依赖等持久化输入及去重处理状态 |
| `tt_task_transition` | 命令/Signal引起的前后状态、版本和安全摘要 |
| `tt_action_job` | 通知、Webhook、同步等待执行动作及通用技术状态 |
| `tt_action_attempt` | Action Job每次技术执行证据 |
| `tt_command_dedup` | API命令requestId、请求摘要和首次确定结果 |
| `tt_audit_log` | 管理与业务操作的安全审计索引 |

### 2.2 通知能力表：capability-notification所有

| 表 | 稳定职责 |
| --- | --- |
| `tt_notification` | 一条业务通知的内容、用途和来源迁移 |
| `tt_inbox` | IN_APP接收人的可查询收件及首次已读时间 |

外部飞书、京ME、邮件投递仍由`tt_action_job/tt_action_attempt`记录技术执行，不为每个渠道创建平台公共表。渠道确有专有回执数据时，只能在notification能力自己的迁移中增加专有表。

### 2.3 身份表：timeimprint-task-identity所有

这些表不是平台公共表。kernel 与任务仓储不得读写它们。

| 表 | 稳定职责 |
| --- | --- |
| `tt_identity_user` | 账号、密码摘要、昵称、停用状态、管理员标记和授权时间 |
| `tt_identity_verification_code` | 短信验证码及创建时间；不进入 HTTP 响应 |
| `tt_identity_session` | 会话令牌摘要；不保存明文令牌 |
| `tt_identity_social_account` | 第三方身份；本期仅微信写入，飞书等 provider 预留 |

## 3. 权威字段定义

所有VARCHAR长度同时接受API码点校验；数据库长度只作为物理上限。所有JSON列NOT NULL时使用显式JSON对象，不使用字符串`"{}"`冒充对象。

### 3.1 `tt_task_definition`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `definition_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 定义标识 |
| `tenant_id` | VARCHAR(64) NOT NULL | 租户；首期本地使用固定测试租户 |
| `scenario_key` | VARCHAR(64) NOT NULL | 场景稳定key |
| `scenario_schema_version` | INT UNSIGNED NOT NULL | 场景配置版本 |
| `title` | VARCHAR(200) NOT NULL | 标题 |
| `description` | VARCHAR(4000) NULL | 纯文本说明 |
| `scenario_config_json` | JSON NOT NULL | 已校验场景配置 |
| `scenario_config_hash` | BINARY(32) NOT NULL | 规范化SHA-256 |
| `control_state` | VARCHAR(16) NOT NULL | ACTIVE/PAUSED/RETIRED |
| `control_generation` | BIGINT UNSIGNED NOT NULL | 控制代次，初始1；暂停、恢复、退役真实生效时+1 |
| `revision` | BIGINT UNSIGNED NOT NULL | 初始1，每次真实变更+1 |
| `created_by` / `updated_by` | VARCHAR(128) NOT NULL | Actor主体标识，不含凭据 |
| `created_at` / `updated_at` | DATETIME(0) NOT NULL | UTC业务时间 |
| `paused_at` / `retired_at` | DATETIME(0) NULL | paused_at为最近一次进入PAUSED的时间，resume后保留；retired_at为进入RETIRED的时间 |

CHECK要求revision与control_generation都在1至9007199254740991；PAUSED时paused_at非空；ACTIVE或PAUSED时retired_at为空；RETIRED时retired_at非空。paused_at在首次暂停前为空，resume后保留最近暂停时间；完整暂停历史由Transition记录，不试图在单行保存全部区间。

唯一键`uk_definition_tenant_id(tenant_id,definition_id)`供租户安全复合关联校验；列表索引`ix_definition_list(tenant_id,updated_at,definition_id)`和`ix_definition_filter(tenant_id,scenario_key,control_state,updated_at,definition_id)`分别支持默认列表与场景/控制状态过滤。所有按definitionId读取的SQL仍必须同时带tenant_id，不能仅依赖全局自增ID。

### 3.2 `tt_trigger_binding`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `trigger_binding_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 绑定标识 |
| `definition_id` | BIGINT UNSIGNED NOT NULL FK | 所属定义 |
| `binding_key` | VARCHAR(64) NOT NULL | 定义内稳定名称 |
| `provider_key` | VARCHAR(64) NOT NULL | calendar/event/condition/dependency等 |
| `schema_version` | INT UNSIGNED NOT NULL | 配置版本 |
| `config_json` / `config_hash` | JSON NOT NULL / BINARY(32) NOT NULL | 已校验配置及摘要 |
| `binding_state` | VARCHAR(16) NOT NULL | ACTIVE/PAUSED/RETIRED |
| `schedule_generation` | BIGINT UNSIGNED NOT NULL | 时间规则批次，初始1 |
| `next_fire_at` | DATETIME(0) NULL | 下一次候选时间；非时间触发可空 |
| `cursor_json` | JSON NOT NULL | provider自有、已版本化的规划游标 |
| `exhausted` | BOOLEAN NOT NULL DEFAULT FALSE | 是否无后续计划发生 |
| `revision` | BIGINT UNSIGNED NOT NULL | 乐观锁版本 |
| `created_at` / `updated_at` | DATETIME(0) NOT NULL | UTC |

唯一键`uk_trigger_definition_binding(definition_id,binding_key)`；索引`ix_trigger_due(provider_key,binding_state,next_fire_at,trigger_binding_id)`用于时间规划，`ix_trigger_live_schema(provider_key,binding_state,schema_version,trigger_binding_id)`用于启动期版本核对。修改时间规则增加schedule_generation，不复用旧游标。

### 3.3 `tt_task_instance`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `instance_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 实例标识 |
| `definition_id` | BIGINT UNSIGNED NOT NULL FK | 所属定义 |
| `trigger_binding_id` | BIGINT UNSIGNED NULL FK | 人工创建实例可空 |
| `schedule_generation` | BIGINT UNSIGNED NULL | 触发实例所属规则批次 |
| `definition_control_generation` | BIGINT UNSIGNED NOT NULL | 创建实例时的定义控制代次快照 |
| `occurrence_key` | VARCHAR(160) NULL | provider内稳定发生标识 |
| `occurrence_at` / `due_at` | DATETIME(0) NULL | 通用发生/截止时间；不适用时为空 |
| `lifecycle_category` | VARCHAR(16) NOT NULL | WAITING/ACTIVE/TERMINAL |
| `scenario_state` | VARCHAR(64) NOT NULL | 场景状态，平台不解释枚举 |
| `scenario_schema_version` | INT UNSIGNED NOT NULL | 快照结构版本 |
| `scenario_snapshot_json` / `snapshot_hash` | JSON NOT NULL / BINARY(32) NOT NULL | 场景快照及摘要 |
| `title_snapshot` | VARCHAR(200) NOT NULL | 历史标题 |
| `description_snapshot` | VARCHAR(4000) NULL | 历史说明 |
| `revision` | BIGINT UNSIGNED NOT NULL | 乐观锁版本 |
| `terminal_at` | DATETIME(0) NULL | 进入TERMINAL的时间 |
| `created_at` / `updated_at` | DATETIME(0) NOT NULL | UTC |

触发实例要求trigger_binding_id、schedule_generation、occurrence_key同时非空；人工实例三者同时为空。唯一键`uk_instance_occurrence(definition_id,trigger_binding_id,schedule_generation,definition_control_generation,occurrence_key)`阻止同一控制代次重复物化，也允许恢复后在新代次重建尚未执行的计划。TERMINAL要求terminal_at非空，其他生命周期要求为空。

列表索引固定包括`ix_instance_definition(definition_id,occurrence_at,instance_id)`、`ix_instance_lifecycle(definition_id,lifecycle_category,occurrence_at,instance_id)`和`ix_instance_scenario(definition_id,scenario_state,updated_at,instance_id)`。跨租户与参与人过滤必须先通过definition/participant限定，不允许只按不透明scenario_state扫描全表。

### 3.4 `tt_task_participant`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `participant_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 关系标识 |
| `definition_id` | BIGINT UNSIGNED NOT NULL FK | 始终关联定义 |
| `instance_id` | BIGINT UNSIGNED NULL FK | 空表示定义级，非空表示实例级 |
| `instance_scope_id` | BIGINT UNSIGNED GENERATED ALWAYS AS COALESCE(instance_id,0) STORED | 解决NULL唯一性，仅用于索引 |
| `principal_type` | VARCHAR(32) NOT NULL | USER/GROUP/SERVICE等 |
| `principal_id` | VARCHAR(128) NOT NULL | 主体标识 |
| `role_code` | VARCHAR(64) NOT NULL | OWNER/ASSIGNEE/COLLABORATOR等 |
| `source_code` | VARCHAR(64) NOT NULL | DIRECT/RULE/ORGANIZATION等来源 |
| `metadata_json` | JSON NOT NULL | 已校验非敏感扩展信息 |
| `created_at` / `updated_at` | DATETIME(0) NOT NULL | UTC |

唯一键`uk_participant_scope(definition_id,instance_scope_id,principal_type,principal_id,role_code)`。数据库无法仅靠普通外键证明instance属于同一definition，application在同一锁事务中检查，集成测试必须覆盖错配回滚。

一期participant表只保存当前有效关系，不在同一行模拟有效期历史。定义参与人更新采用“锁内删除不再存在的关系、插入新增关系、保留未变关系”，历史变化由Transition/Audit及已经复制到实例级的参与人快照证明；因此同一主体移除后可以再次加入，不与唯一键矛盾。主体查询索引`ix_participant_principal(principal_type,principal_id,role_code,definition_id,instance_scope_id)`用于“我的定义/实例”入口。

### 3.5 `tt_task_signal`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `signal_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | Signal标识 |
| `tenant_id` | VARCHAR(64) NOT NULL | 去重范围 |
| `definition_id` | BIGINT UNSIGNED NOT NULL FK | 入READY前已解析且唯一的目标定义 |
| `trigger_binding_id` / `instance_id` | BIGINT UNSIGNED NULL FK | 计划Signal可关联触发；实例级Signal关联实例 |
| `definition_control_generation` | BIGINT UNSIGNED NOT NULL | 创建Signal时的定义控制代次快照 |
| `parent_signal_id` | BIGINT UNSIGNED NULL FK | 人工重驱时始终指向最初正常根Signal；正常Signal为空 |
| `redrive_no` | INT UNSIGNED NOT NULL DEFAULT 0 | 重驱序号；正常Signal为0，人工重驱为1至3 |
| `provider_key` / `signal_key` | VARCHAR(64) / VARCHAR(160) NOT NULL | 来源和来源内唯一key |
| `schema_version` | INT UNSIGNED NOT NULL | payload结构版本 |
| `occurred_at` / `received_at` | DATETIME(0) NOT NULL | 来源发生与平台接收时间 |
| `payload_json` / `payload_hash` | JSON NOT NULL / BINARY(32) NOT NULL | 已校验载荷及摘要 |
| `process_status` | VARCHAR(16) NOT NULL | READY/RUNNING/RETRY_WAIT/SUCCEEDED/IGNORED/DEAD |
| `attempt_count` / `max_attempts` | INT UNSIGNED NOT NULL | 技术尝试计数及上限 |
| `next_attempt_at` | DATETIME(0) NOT NULL | 下次可领取时间 |
| `lease_owner` / `lease_until` / `execution_token` | VARCHAR(64) / DATETIME(0) / CHAR(36) NULL | RUNNING时必须同时存在；owner来自INSTANCE_ID，仅用于诊断 |
| `result_code` / `result_summary` | VARCHAR(64) / VARCHAR(1000) NULL | 安全结果摘要 |
| `processed_at` | DATETIME(0) NULL | SUCCEEDED/IGNORED/DEAD终结时间 |
| `created_at` / `updated_at` | DATETIME(0) NOT NULL | UTC |

唯一键`uk_signal_source(tenant_id,provider_key,signal_key)`和`uk_signal_redrive_root(parent_signal_id,redrive_no)`；时间计划Signal的signal_key必须包含definition、trigger、scheduleGeneration、controlGeneration与occurrenceKey的规范化摘要，人工重驱则包含根Signal标识和redrive_no。MySQL允许唯一键中多个NULL，因此正常Signal不受redrive唯一键互相冲突。领取索引`ix_signal_claim(process_status,next_attempt_at,signal_id)`、租约索引`ix_signal_lease(process_status,lease_until,signal_id)`、资源诊断索引`ix_signal_definition(definition_id,created_at,signal_id)`；`ix_signal_live_schema(process_status,provider_key,schema_version,signal_id)`用于启动期验证所有未终结载荷版本仍可读取。

跨字段规则固定为：`provider_key=calendar`的正常计划Signal必须同时填写trigger_binding_id和instance_id，其他provider是否允许这两项由其schema契约明确；只要任一引用存在，就必须与definition_id属于同一资源链。正常Signal要求parent_signal_id为空且redrive_no=0；重驱Signal要求parent_signal_id非空、redrive_no为1至3，并继承根Signal的tenant、definition、provider及schema版本。上述规则由CHECK覆盖可表达部分，其余由application锁内校验和真库测试证明。

### 3.6 `tt_task_transition`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `transition_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 迁移标识 |
| `definition_id` | BIGINT UNSIGNED NOT NULL FK | 所属定义 |
| `instance_id` | BIGINT UNSIGNED NULL FK | 定义级迁移可空 |
| `instance_scope_id` | BIGINT UNSIGNED GENERATED AS COALESCE(instance_id,0) STORED | 唯一范围 |
| `source_type` / `source_key` | VARCHAR(16) / VARCHAR(160) NOT NULL | COMMAND/SIGNAL/SYSTEM及来源key |
| `command_key` | VARCHAR(64) NULL | 命令迁移填写 |
| `from_control_state` / `to_control_state` | VARCHAR(16) NULL | 定义级迁移的控制状态前后值 |
| `from_lifecycle` / `to_lifecycle` | VARCHAR(16) NULL | 定义级迁移可空 |
| `from_scenario_state` / `to_scenario_state` | VARCHAR(64) NULL | 实例场景状态变化 |
| `from_revision` / `to_revision` | BIGINT UNSIGNED NOT NULL | 目标资源版本 |
| `actor_type` / `actor_id` | VARCHAR(32) / VARCHAR(128) NOT NULL | USER/SERVICE/SYSTEM及主体 |
| `summary_json` | JSON NOT NULL | 不超过UTF-8 16KiB的安全摘要 |
| `trace_id` | VARCHAR(64) NOT NULL | 链路标识 |
| `created_at` | DATETIME(0) NOT NULL | UTC |

唯一键`uk_transition_revision(definition_id,instance_scope_id,to_revision)`；to_revision必须等于from_revision+1。字段组合固定为：definition初始迁移的instance_id为空、from_revision=0、to_revision=1、from_control_state为空、to_control_state=ACTIVE，lifecycle/scenario值全空；definition后续迁移的instance_id为空、control前后值必填、lifecycle/scenario值全空；instance初始迁移的instance_id非空、from_revision=0、to_revision=1、control值全空、from lifecycle/scenario为空且to值必填；instance后续迁移的instance_id非空、control值全空、lifecycle/scenario前后值全部必填。定义内容更新即使controlState不变，也写相同的from/to controlState。无状态变化的NoChange、读取或幂等重放不写Transition。

source_type只允许COMMAND/SIGNAL/SYSTEM。COMMAND迁移必须填写command_key；SIGNAL和SYSTEM迁移的command_key必须为空。source_key在COMMAND时是requestId，在SIGNAL时是signalId的十进制字符串，在SYSTEM时是平台定义的稳定系统事件键，禁止随机生成；重复来源是否允许产生迁移仍由资源revision和对应幂等键共同约束。

### 3.7 `tt_action_job`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `action_job_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 动作标识 |
| `tenant_id` | VARCHAR(64) NOT NULL | 租户 |
| `definition_id` / `instance_id` / `transition_id` | BIGINT UNSIGNED NOT NULL FK | 来源链路 |
| `definition_control_generation` | BIGINT UNSIGNED NOT NULL | 创建Action时的定义控制代次快照 |
| `parent_action_job_id` | BIGINT UNSIGNED NULL FK | 人工重驱时始终指向最初正常根Action；正常Action为空 |
| `redrive_no` | INT UNSIGNED NOT NULL DEFAULT 0 | 重驱序号；正常Action为0，人工重驱为1至3 |
| `handler_key` / `action_key` | VARCHAR(64) / VARCHAR(200) NOT NULL | 处理器及稳定幂等key |
| `execution_mode` | VARCHAR(24) NOT NULL | LOCAL_TRANSACTIONAL/EXTERNAL |
| `schema_version` | INT UNSIGNED NOT NULL | payload结构版本 |
| `target_type` / `target_id` | VARCHAR(32) / VARCHAR(128) NULL | 接收人、端点或资源目标 |
| `payload_json` / `payload_hash` | JSON NOT NULL / BINARY(32) NOT NULL | 已校验载荷及摘要 |
| `available_at` / `expires_at` | DATETIME(0) NOT NULL / NULL | 可执行时间和可选业务期限 |
| `status` | VARCHAR(16) NOT NULL | READY/RUNNING/RETRY_WAIT/SUCCEEDED/DEAD/CANCELLED/EXPIRED/UNKNOWN |
| `attempt_count` / `max_attempts` | INT UNSIGNED NOT NULL | 已消耗技术尝试数和上限；政策阻断可安全退还计数 |
| `next_attempt_at` | DATETIME(0) NOT NULL | 下次可领取时间 |
| `lease_owner` / `lease_until` / `execution_token` | VARCHAR(64) / DATETIME(0) / CHAR(36) NULL | RUNNING时必须同时存在；owner来自INSTANCE_ID，仅用于诊断 |
| `outcome_code` / `outcome_summary` | VARCHAR(64) / VARCHAR(1000) NULL | 安全执行结果 |
| `completed_at` | DATETIME(0) NULL | 终结时间 |
| `created_at` / `updated_at` | DATETIME(0) NOT NULL | UTC |

唯一键`uk_action_key(tenant_id,handler_key,action_key)`和`uk_action_redrive_root(parent_action_job_id,redrive_no)`；action_key使用可读用途短前缀加规范化业务身份的SHA-256 Base64URL摘要，不直接拼接接收人等可能超长或敏感的数据，人工重驱的规范输入额外包含根Action标识与redrive_no。MySQL允许唯一键中多个NULL，因此正常Action不受redrive唯一键互相冲突。领取索引`ix_action_claim(status,next_attempt_at,available_at,action_job_id)`；租约索引`ix_action_lease(status,lease_until,action_job_id)`；资源诊断索引`ix_action_instance(instance_id,created_at,action_job_id)`；`ix_action_live_schema(status,handler_key,schema_version,action_job_id)`用于启动期验证未终结动作版本。

target_type与target_id必须同时为空或同时非空；一期IN_APP通知Action必须同时非空且target_type=USER。正常Action要求parent_action_job_id为空且redrive_no=0；重驱Action要求parent_action_job_id非空、redrive_no为1至3，并继承根Action的tenant、definition、instance、transition、handler、executionMode及schema版本。target、payload、actionKey按重驱契约重新生成，但不得借重驱改变原业务语义。

### 3.8 `tt_action_attempt`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `attempt_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 尝试标识 |
| `action_job_id` | BIGINT UNSIGNED NOT NULL FK | 所属动作 |
| `attempt_no` | INT UNSIGNED NOT NULL | 从1递增 |
| `execution_token` | CHAR(36) NOT NULL | 与本次领取一致 |
| `started_at` / `finished_at` | DATETIME(0) NOT NULL / NULL | 执行区间 |
| `effect_started_at` | DATETIME(0) NULL | EXTERNAL调用前持久化；非空表示不能假设副作用未发生 |
| `outcome` | VARCHAR(32) NULL | SUCCEEDED/RETRYABLE_FAILURE/PERMANENT_FAILURE/UNKNOWN/POLICY_BLOCKED |
| `error_class` / `error_code` | VARCHAR(128) / VARCHAR(64) NULL | 分类信息，不存堆栈 |
| `provider_reference` | VARCHAR(200) NULL | 外部受理号等非敏感引用 |
| `safe_summary` | VARCHAR(1000) NULL | 去敏摘要 |

唯一键`uk_attempt_no(action_job_id,attempt_no)`和`uk_attempt_token(action_job_id,execution_token)`。attempt_no是从1递增且永不复用的领取序号；政策阻断可以退还action.attempt_count，但不能删除Attempt或复用attempt_no。每次RUNNING必须有且只有一条对应attempt；合法离开RUNNING时attempt必须同事务闭合，UNKNOWN也必须填写finished_at。

### 3.9 `tt_command_dedup`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `dedup_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 记录标识 |
| `tenant_id` / `actor_id` | VARCHAR(64) / VARCHAR(128) NOT NULL | 幂等范围 |
| `operation` | VARCHAR(200) NOT NULL | 规范化端点和commandKey |
| `request_id` | CHAR(36) NOT NULL | 客户端幂等ID |
| `request_hash` | BINARY(32) NOT NULL | 规范化请求摘要 |
| `process_status` | VARCHAR(16) NOT NULL | PROCESSING/COMPLETED；提交后只允许COMPLETED |
| `result_code` | VARCHAR(64) NULL | 首次确定结果；COMPLETED时必填 |
| `resource_type` / `resource_id` | VARCHAR(32) / VARCHAR(64) NULL | 结果资源 |
| `resource_revision` | BIGINT UNSIGNED NULL | 首次结果版本 |
| `response_json` | JSON NULL | 可安全重放的响应data，最大64KiB；COMPLETED时必填 |
| `created_at` | DATETIME(0) NOT NULL | UTC |
| `updated_at` | DATETIME(0) NOT NULL | UTC |

唯一键`uk_command_request(tenant_id,actor_id,operation,request_id)`。事务先插入PROCESSING行；唯一键竞争者等待首个事务结束，首个事务回滚后竞争者重新插入，首个事务提交后竞争者读取COMPLETED结果。确定性成功或业务拒绝都在同一事务把行改为COMPLETED并保存首次响应；技术异常、死锁或整笔回滚不留下行。数据库中不得提交PROCESSING行。相同key不同request_hash返回冲突。

### 3.10 `tt_audit_log`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `audit_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 审计标识 |
| `tenant_id` | VARCHAR(64) NOT NULL | 租户范围 |
| `resource_type` / `resource_id` | VARCHAR(32) / VARCHAR(64) NOT NULL | 被审计资源 |
| `definition_id` / `instance_id` / `transition_id` | BIGINT UNSIGNED NULL FK | 可选业务链路；存在时必须同链 |
| `event_type` | VARCHAR(64) NOT NULL | 稳定审计事件 |
| `actor_type` / `actor_id` | VARCHAR(32) / VARCHAR(128) NOT NULL | USER/SERVICE/SYSTEM及主体 |
| `trace_id` | VARCHAR(64) NOT NULL | 调用链路 |
| `detail_json` | JSON NOT NULL | 安全摘要，规范化UTF-8不超过16KiB |
| `created_at` | DATETIME(0) NOT NULL | UTC |

索引`ix_audit_resource(tenant_id,resource_type,resource_id,created_at,audit_id)`、`ix_audit_definition(definition_id,created_at,audit_id)`和`ix_audit_trace(tenant_id,trace_id,audit_id)`。detail_json不得保存title/body全文、场景敏感payload、密码、数据库连接凭据、访问令牌或executionToken；超限不能静默截断，必须使所属写事务失败。

### 3.11 `tt_notification`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `notification_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 通知标识 |
| `tenant_id` | VARCHAR(64) NOT NULL | 租户范围 |
| `definition_id` / `instance_id` / `transition_id` | BIGINT UNSIGNED NOT NULL FK | 来源链路且必须一致 |
| `title` | VARCHAR(200) NOT NULL | 纯文本通知标题 |
| `body` | VARCHAR(4000) NULL | 纯文本正文 |
| `purpose` | VARCHAR(32) NOT NULL | INITIAL/CHASE/ESCALATION等能力语义 |
| `created_at` | DATETIME(0) NOT NULL | UTC |

索引`ix_notification_instance(instance_id,created_at,notification_id)`和`ix_notification_transition(transition_id,notification_id)`。同一Transition可创建多个用途通知；实际接收人去重由Action actionKey与inbox唯一键保证，平台内核不解释purpose。

### 3.12 `tt_inbox`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `inbox_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 收件标识 |
| `tenant_id` | VARCHAR(64) NOT NULL | 必须与notification/action一致的租户范围 |
| `notification_id` / `action_job_id` | BIGINT UNSIGNED NOT NULL FK | 来源通知与IN_APP Action |
| `definition_id` / `instance_id` | BIGINT UNSIGNED NOT NULL FK | 为租户安全分页保留的来源链路 |
| `recipient_type` / `recipient_id` | VARCHAR(32) / VARCHAR(128) NOT NULL | 接收主体 |
| `read_at` | DATETIME(0) NULL | 第一次标记已读时间 |
| `created_at` | DATETIME(0) NOT NULL | UTC |

唯一键`uk_inbox_action(action_job_id)`保证一次IN_APP动作最多一条收件；唯一键`uk_inbox_notification_recipient(notification_id,recipient_type,recipient_id)`防止同通知向同接收人重复入箱。全部收件分页使用`ix_inbox_recipient(tenant_id,recipient_type,recipient_id,created_at,inbox_id)`，未读查询和计数使用`ix_inbox_unread(tenant_id,recipient_type,recipient_id,read_at,created_at,inbox_id)`，实例投递汇总使用`ix_inbox_instance(instance_id,created_at,inbox_id)`。

IN_APP处理器在一个短事务中核对tenant/definition/instance链路，插入inbox、闭合attempt并把action状态改为SUCCEEDED；任一步失败全部回滚。查询收件不自动标记已读；mark-read仅在read_at为空时写入首次时间。

### 3.13 `tt_identity_user`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `user_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 内部主键 |
| `actor_key` | VARCHAR(128) NOT NULL | 公开用户标识；ActorContext 与参与人 principalId 使用它，不用手机号 |
| `tenant_id` | VARCHAR(64) NOT NULL | 单一租户 |
| `phone_number` | VARCHAR(15) NULL | 大陆 11 位；未绑定微信临时账号可空 |
| `username` | VARCHAR(150) NOT NULL | 登录名；手机号注册时等于手机号 |
| `password_hash` | VARCHAR(128) NULL | 密码摘要；不可用密码为空。禁止明文 |
| `nickname` | VARCHAR(30) NOT NULL | 昵称 |
| `account_status` | VARCHAR(16) NOT NULL | ACTIVE / DISABLED |
| `is_admin` | TINYINT(1) NOT NULL | 管理员 |
| `authorization_verified_at` | DATETIME(0) NULL | 一次性授权通过时间 |
| `created_at` / `last_login_at` | DATETIME(0) NOT NULL / DATETIME(0) NULL | UTC |

唯一键`uk_identity_user_phone(tenant_id,phone_number)`与`uk_identity_user_username(tenant_id,username)`。手机号为空的多行不受手机号唯一约束互相冲突。

### 3.14 `tt_identity_verification_code`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `verification_code_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 记录标识 |
| `phone_number` | VARCHAR(15) NOT NULL | 接收手机号 |
| `code` | VARCHAR(6) NOT NULL | 六位验证码；只用于校验，不得写入日志或 HTTP 响应 |
| `created_at` | DATETIME(0) NOT NULL | UTC；超过 10 分钟失效 |

索引`ix_identity_code_phone(phone_number,created_at,verification_code_id)`。只有短信通道确认成功后才插入。

### 3.15 `tt_identity_session`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `session_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 会话标识 |
| `user_id` | BIGINT UNSIGNED NOT NULL FK | 所属用户 |
| `token_hash` | BINARY(32) NOT NULL | 令牌的 SHA-256 |
| `created_at` | DATETIME(0) NOT NULL | UTC |

唯一键`uk_identity_session_token(token_hash)`。退出登录或进入新的授权挑战时删除该用户会话。同一用户可复用尚未删除的令牌。

### 3.16 `tt_identity_social_account`

| 字段 | 类型与约束 | 说明 |
| --- | --- | --- |
| `social_account_id` | BIGINT UNSIGNED PK AUTO_INCREMENT | 绑定标识 |
| `user_id` | BIGINT UNSIGNED NOT NULL FK | 所属用户 |
| `provider` | VARCHAR(32) NOT NULL | 本期写入 `wechat`；`feishu` 仅预留 |
| `app_type` | VARCHAR(32) NOT NULL | web / ios / android |
| `openid` | VARCHAR(128) NOT NULL | 应用内身份 |
| `unionid` | VARCHAR(128) NULL | 跨应用兜底 |
| `nickname` | VARCHAR(64) NOT NULL | 第三方昵称 |
| `avatar_url` | VARCHAR(500) NOT NULL | 头像地址，可空字符串 |
| `raw_json` | JSON NOT NULL | 不含访问令牌的原始资料 |
| `created_at` / `updated_at` | DATETIME(0) NOT NULL | UTC |

唯一键`uk_identity_social(provider,app_type,openid)`。索引`ix_identity_social_union(provider,unionid)`。绑定冲突和手机号合并必须在同一事务内完成。

## 4. 状态与字段组合约束

Flyway必须使用CHECK约束验证可枚举的公共技术状态。场景状态不建立全局CHECK；由scenarioKey + schemaVersion的处理器验证。

| 对象状态 | 必须存在 | 必须为空 |
| --- | --- | --- |
| Signal/Action READY、RETRY_WAIT | next_attempt_at | lease_owner、lease_until、execution_token、processed/completed_at |
| Signal/Action RUNNING | lease_owner、lease_until、execution_token、对应attempt（Action） | processed/completed_at |
| Signal SUCCEEDED、IGNORED、DEAD | processed_at、result_code | lease_owner、lease_until、execution_token |
| Action SUCCEEDED、DEAD、CANCELLED、EXPIRED、UNKNOWN | completed_at、outcome_code | lease_owner、lease_until、execution_token |
| Instance TERMINAL | terminal_at | 无 |
| Instance WAITING、ACTIVE | 无 | terminal_at |

attempt_count不得超过max_attempts；expires_at为空表示没有通用期限，非空时必须不早于available_at。definition_control_generation必须大于0，且Worker只执行与当前definition.control_generation一致的工作。正常Signal/Action要求parent为空且redrive_no=0；人工重驱要求parent非空且redrive_no在1至`MAX_MANUAL_REDRIVES`之间。execution_mode=LOCAL_TRANSACTIONAL时effect_started_at必须为空；EXTERNAL调用前必须先提交effect_started_at。UNKNOWN是终止自动重试的显式状态，只有经过独立对账或人工审核才能产生新的补偿Action，不能直接把原行改回READY。

Signal和Action的租约三元组`lease_owner/lease_until/execution_token`必须全空或全非空，且只有RUNNING允许全非空。Action的target二元组必须全空或全非空；终结状态的结果码与完成时间必须同时落库。Transition的source/command组合、定义级与实例级状态列组合，以及Signal/Action正常行与重驱行组合，均须在首个迁移中用CHECK覆盖数据库能够表达的部分，不能只依赖Java枚举。

## 5. 跨表不变量与锁边界

- instance.trigger_binding_id存在时，它必须属于同一个definition，且schedule_generation与生成时绑定批次一致；instance、Signal、Action的definition_control_generation必须记录创建时定义代次。
- participant.instance_id存在时，它必须属于同一个definition。
- transition、action、notification引用的definition/instance必须属于同一资源链。
- notification、inbox和action中的tenant_id必须与来源definition相同；inbox中的notification/action/definition/instance必须属于同一链。数据库外键负责存在性，application在锁内负责这些冗余归属一致性。
- 时间窗口规划只能创建WAITING Instance和计划Signal，不能预创建Action。正常Action只能由实际处理Command或Signal所得的已提交Transition创建；受控人工重驱引用原Action和原Transition，是唯一不新增业务Transition的技术派生例外。action_key由场景/能力按稳定业务身份产生，不能使用随机UUID规避去重。
- Action执行前比较当前definition.control_generation；代次不一致时终结为CANCELLED。实例已经TERMINAL时，只有“使它进入该终态的同一Transition”产生的Action仍可执行，所有更早Transition遗留的未终结Action都取消。
- Action终态不能决定TaskInstance场景终态；需要反馈业务结果时，处理器产生新的去重Signal进入迁移管道。
- 同步命令事务先取得`tt_command_dedup`唯一键对应的行锁，再按definition → trigger binding → instance → participant → transition → action/notification → audit顺序加锁和写入；其他事务不得持有definition/instance锁后再请求command dedup锁。
- Signal领取事务只锁候选Signal并立即提交。处理事务先普通读取父标识，再按definition → trigger binding → instance → Signal顺序加锁并进入迁移管道；不得先持有Signal锁再请求父资源锁，也不得持锁调用外部服务。
- Planner先普通扫描候选主键，再按每个候选的definition → trigger binding顺序加锁；禁止依赖“先锁trigger再补锁definition”的SKIP LOCKED扫描。一次需要锁同类多行时按主键升序。
- Worker领取只锁队列表并立即提交；业务执行和结果提交不得长期持有候选扫描事务。
- 暂停、恢复、退役真实生效时在同一事务增加definition.control_generation；异步清理即使延迟，也不能让旧代次Signal或Action恢复执行。恢复在新代次重建未来计划。
- Signal仅允许从DEAD人工重驱；LOCAL_TRANSACTIONAL Action仅允许从DEAD人工重驱。服务锁定最初正常根行，以根ID和redrive_no保证并发唯一；重驱创建指向根的新行，保留全部历史行不变。来源代次必须仍等于定义当前control_generation，并重新通过控制状态、实例生命周期和Policy屏障；EXTERNAL和UNKNOWN Action禁止直接重驱，须先对账或走显式补偿业务。

所有跨表应用级不变量必须有真库集成测试故意构造错配并证明整笔事务回滚。不能因为存在外键就省略同definition、同revision和同transition链路检查。03的单次事务对象数、JSON字节数、TransitionPlan总字节数、总变更行数和事务超时上限由application在写入前验证，并在事务中累计实际受影响行数；存储实现不得通过分批提交把一个TransitionPlan拆成部分成功。

## 6. 查询与执行计划要求

T03在代表性数据规模下保存EXPLAIN证据，至少覆盖：

- 按Actor参与角色分页查询定义和实例。
- 按definition、scenario、生命周期、场景状态和时间范围分页实例。
- trigger到期扫描。
- Signal READY/RETRY_WAIT领取和RUNNING租约回收。
- Action READY/RETRY_WAIT领取和RUNNING租约回收。
- 收件列表、未读计数和迁移/审计链路查询。

队列查询必须从状态和时间组成的复合索引开始，并以主键形成稳定顺序。SKIP LOCKED只用于多Worker领取候选；Signal/Action最终技术状态提交使用主键 + RUNNING + executionToken条件更新，业务资源迁移另使用definition/instance revision。执行计划因统计信息变化可以改变，验收保存SQL、数据量、参数类别、EXPLAIN FORMAT=JSON和实际扫描行数，不以“出现索引名”作为唯一通过条件。

## 7. 迁移与验证

一期平台迁移位于storage-mysql，notification和scenario-basic仅在确有专有表时提供迁移。首期scenario-basic没有必要专有表，S01/S02的`chaseOffsetsMinutes`、`notificationExpireAfterMinutes`、`maxSnoozeCount`、`snoozeCount`和`actionGeneration`可由版本化场景配置/快照、通用实例、Transition、Action及notification表表达，因此不得创建空场景表。

平台与通知的 Flyway 迁移必须创建上述 12 张表、外键、CHECK、唯一键和索引。身份四表由 identity 模块的后续迁移创建，不并入这 12 张表。除明确声明可空的业务关联外，所有FK使用`ON DELETE RESTRICT ON UPDATE RESTRICT`；不得依赖数据库级联清理历史。真库测试使用information_schema逐项核对表名、字段类型、可空性、默认值、生成列、注释、字符集、排序规则、索引顺序、外键动作和CHECK，并实际插入非法组合证明约束生效。

必须验证：空库迁移、重复启动、两个进程并发启动、坏迁移不就绪、已执行迁移不被覆盖、JSON合法性与字节上限、UTC整秒、revision/control_generation上限、幂等冲突、重复Signal、重复发生、重复Action、旧代次拒绝执行、人工重驱次数和父链、旧token回写失败、事务总变更行数、IN_APP原子提交及审计16KiB边界。

归档、分区、物理删除、冷热数据和C18/C19保留期限未确定，不进入一期DDL。任何清理只能在后续迁移和保留契约批准后实施，不能用级联删除隐藏历史。
