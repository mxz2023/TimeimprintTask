# 02 · 通用任务平台架构与编码约束

> 人工审核与阶段准入以[00审核台账](00-READING-ORDER.md)为准。本文2.0已于2026-09-10 20:23通过人工审核；当前不包含cache模块，这只批准架构契约，不代表放行工程实施。

版本 2.0；先读[平台业务契约](01-MVP-SPEC.md)，下一份为[环境契约](03-INTEGRATION-CONTRACTS.md)。本文定义稳定底座、扩展边界和目标工程结构；01 定义平台语义与首期业务切片，09 记录未来场景和能力规划。

## 1. 架构目标

JoyTask 采用“稳定内核 + 可插拔能力”的 Java 模块化单体。所有 Maven 模块由一个 Spring Boot 应用装配和部署，不把模块拆分等同于微服务。

架构必须保证：

1. 已规划的新场景只增加场景模块、能力实现、场景专有表和装配声明，不修改 `joytask-service-kernel` 的业务语义和公共表结构。
2. 内核只保存跨场景长期稳定的事实：任务身份、定义版本、实例生命周期类别、参与主体、迁移版本和审计关联。
3. 提醒、审批、缴费、会议等业务状态由场景定义，内核不得维护场景状态枚举或按 `scenarioKey` 编写分支。
4. 时间、事件、条件和依赖统一转换为持久化 Signal；通知、Webhook、同步和自动执行统一转换为 Action Job。
5. 扩展只返回声明式 `TransitionPlan`，不能直接更新公共任务表、提交事务或在状态事务中执行外部网络调用。
6. 模块数量按“稳定边界和独立变化原因”控制。通知渠道、日历算法和相近小场景先在所属模块内按包隔离，不默认一个实现一个 Maven 模块。

“新增场景不改底层”特指不修改内核语义、公共表和既有扩展契约。根 POM、`joytask-boot-loader` 装配清单、新模块迁移目录以及新增兼容契约可以变化，但必须通过架构检查与文档审核。

## 2. 当前一期模块

一期建立 13 个平级 Maven 模块：

| 模块 | 职责 | 允许直接项目依赖 |
| --- | --- | --- |
| `joytask-common` | 无业务含义的工具、基础技术类型 | 无 |
| `joytask-api` | 对外接口、请求/响应 DTO、响应信封 | common |
| `joytask-service-kernel` | 任务定义、实例、参与人、稳定生命周期和迁移计划值对象；纯 Java | common |
| `joytask-service-extension-api` | Scenario、Trigger、Command、Action、Policy 扩展契约和注册描述 | kernel、common |
| `joytask-service-application` | 用例编排、权限、幂等、事务、迁移计划校验和原子提交 | kernel、extension-api、common |
| `joytask-service-runtime` | Signal、触发规划、Action Job 领取、租约、重试和恢复 | kernel、extension-api、application、common |
| `joytask-service-storage-mysql` | Repository 实现、MyBatis Mapper/XML、查询、锁协议和平台迁移 | kernel、application、runtime、common |
| `joytask-service-capability-calendar` | 一次性、日/周/月/N 日规则和时间计算能力 | extension-api、kernel、common |
| `joytask-service-capability-notification` | 通知动作、投递、尝试、收件箱、通知策略和渠道实现 | extension-api、kernel、common |
| `joytask-service-scenario-basic` | 通用提醒、周期待办及其强类型配置、命令和投影 | extension-api、kernel、calendar、common |
| `joytask-api-gateway` | 实现对外 API、构造 ActorContext、DTO 转换和错误映射 | api、application、kernel、common |
| `joytask-web` | HTTP Controller、过滤器、请求限制和统一异常处理 | api、gateway、common |
| `joytask-boot-loader` | 启动、配置、模块装配、迁移加载和集成测试入口 | 选择全部运行时实现 |

一期不创建cache、AI、工作流、外部IM或未来场景模块。缓存能力及其工程形态暂不确定，后续根据真实性能需求单独设计和审批；不能用空接口、空Bean、空表或固定假数据声称完成了扩展能力。

## 3. 未来目标模块

未来模块按能力域和场景族扩展，而不是按每个渠道或单个场景扩展：

```text
joytask
├── joytask-common
├── joytask-api
├── joytask-service-kernel
├── joytask-service-extension-api
├── joytask-service-application
├── joytask-service-runtime
├── joytask-service-storage-mysql
├── joytask-service-capability-calendar
├── joytask-service-capability-trigger
├── joytask-service-capability-notification
├── joytask-service-capability-collaboration
├── joytask-service-capability-workflow
├── joytask-service-capability-aggregation
├── joytask-service-capability-integration
├── joytask-service-capability-intelligence
├── joytask-service-scenario-basic
├── joytask-service-scenario-deadline
├── joytask-service-scenario-collaboration
├── joytask-service-scenario-workflow
├── joytask-service-scenario-automation
├── joytask-api-gateway
├── joytask-web
└── joytask-boot-loader
```

当前目标结构为23个模块，不包含cache模块。缓存仍是未来能力候选，但在实际需求和技术方案确定前不预设模块名称、依赖位置或产品实现。未来结构是边界规划，不是要求一期创建全部目录。

### 3.1 能力模块边界

| 模块 | 聚合的能力 |
| --- | --- |
| `capability-calendar` | 公历、周期、工作日、节假日、农历、相对时间 |
| `capability-trigger` | 外部事件、条件和任务依赖触发；一期时间触发由calendar与runtime完成 |
| `capability-notification` | 站内信、飞书、京ME、邮件、静默、频控、降级 |
| `capability-collaboration` | 人员解析、参与人、协作者、转交、轮值 |
| `capability-workflow` | 串行、并行、会签、分支、汇合、退回、补偿 |
| `capability-aggregation` | 多任务汇总、进度、窗口统计和摘要 |
| `capability-integration` | Webhook、外部事件、回调和第三方日历同步 |
| `capability-intelligence` | 自然语言解析、日期理解、配置建议和解释 |

站内信、飞书、京ME、邮件首先是 `capability-notification` 内部渠道包。只有出现独立发布、重大 SDK 冲突、单独安全隔离或独立团队所有权时，才把某个渠道提取为新的适配器模块；提取前后保持同一个 `NotificationChannel` 契约。

### 3.2 场景模块边界

| 模块 | 聚合的场景 |
| --- | --- |
| `scenario-basic` | 单次提醒、周期提醒、个人待办、周期待办 |
| `scenario-deadline` | 到期、缴费、续约、保养、复查和逾期处理 |
| `scenario-collaboration` | 团队任务、会议协作、轮值、交接和资源参与 |
| `scenario-workflow` | 审批、会签、复核、退回和升级 |
| `scenario-automation` | 外部事件、条件监控、依赖联动和自动完成 |

同一场景族先按包隔离。只有独立业务数据、生命周期、所有权或发布节奏已经形成，才拆出新的平级 `joytask-service-scenario-*` 模块。

## 4. 依赖规则

依赖方向只允许向稳定契约收敛：

```text
web → api-gateway → application → kernel
             │             └── extension-api
             └── api

runtime ────────────────→ application / kernel / extension-api
capability-* ───────────→ extension-api / kernel
scenario-* ─────────────→ extension-api / kernel / 已批准的 capability API
storage-* ──────────────→ kernel/application/runtime 中的存储端口
boot-loader ────────────→ 选择并装配全部运行实现
```

强制规则：

- kernel 不依赖 Spring、MyBatis、HTTP、具体能力或具体场景。
- extension-api 不依赖任何场景、能力、存储或接入模块。
- 场景模块不得依赖 storage 模块、其他场景模块或具体通知渠道。
- 能力模块不得反向依赖场景模块。
- 跨场景协作通过 Signal 或公开能力契约完成，不允许直接调用另一个场景的内部类或 Mapper。
- `boot-loader` 是组合根，可以依赖运行实现，但不得承载业务规则。
- 父 POM 用 Maven Enforcer 检查依赖收敛和禁用依赖，ArchUnit 检查生产源码边界；仅靠包命名不算隔离完成。

## 5. 五类扩展契约

| 契约 | 责任 | 明确禁止 |
| --- | --- | --- |
| `ScenarioExtension` | 配置版本、初始状态、允许命令、状态迁移、投影和专有数据声明 | 更新公共表、调用外部网络 |
| `TriggerProvider` | 把时间、事件、条件或依赖转换成去重 Signal | 直接改变任务业务状态 |
| `TaskCommandHandler` | 处理 complete、skip、approve、reject、assign 等强类型命令并返回 TransitionPlan | 接受未经校验的任意 Map、直接提交事务 |
| `ActionHandler` | 执行 notification、webhook、sync 等外部或内部动作 | 自行无限重试、绕过 Action Job 状态机 |
| `Policy` | 权限、频控、静默、执行资格和合规约束 | 隐式修改场景状态或伪造成功 |

扩展注册使用稳定 key、契约版本和配置 schemaVersion。重复 key、处理器缺失或版本不兼容时启动失败。扩展配置在接入边界完成强类型转换；版本升级必须提供兼容读取或显式迁移，不能让任意 JSON/Map 进入业务深处。

## 6. 状态与事务边界

平台维护四条相互独立的状态轴：

1. 定义控制状态：`ACTIVE / PAUSED / RETIRED`，只表示是否接受新输入。
2. 实例生命周期类别：`WAITING / ACTIVE / TERMINAL`，用于通用查询、并发屏障和保留策略。
3. 场景业务状态：由场景声明，例如 `WAITING_APPROVAL / APPROVED / REJECTED`；内核只保存代码和值，不解释枚举。
4. 动作技术状态：`READY / RUNNING / RETRY_WAIT / SUCCEEDED / DEAD / CANCELLED / EXPIRED / UNKNOWN`。

命令或 Signal 的状态事务固定为：去重检查 → 锁定并重验 → 调用场景纯计算 → 得到 TransitionPlan → 校验平台不变量 → 原子写入状态、参与人、动作意图、迁移记录、审计和幂等结果 → 提交。

TransitionPlan 至少可声明：实例新生命周期类别、场景新状态、参与人变更、待创建 Action Job、后续 Signal/触发绑定变化和安全审计摘要。平台有权拒绝非法计划；扩展不能绕过 revision、权限、终态和所有权检查。

任何 HTTP、IM、文件、模型调用或其他不可回滚副作用都不得出现在状态事务中。ActionHandler必须声明`LOCAL_TRANSACTIONAL`或`EXTERNAL`执行模式：本地模式只在Action结果事务中写能力自有表；外部模式先持久化副作用开始证据，再在事务外调用。Action Worker以MySQL租约领取，用executionToken和revision提交结果；外部调用超时或崩溃后无法判断结果时记录`UNKNOWN`，不能直接当作失败重复发送。

## 7. 数据所有权

公共表只保存平台事实，建议包括：

```text
jt_task_definition
jt_task_instance
jt_task_participant
jt_trigger_binding
jt_task_signal
jt_action_job
jt_action_attempt
jt_task_transition
jt_command_dedup
jt_audit_log
```

通知投递、收件箱等由通知能力拥有；审批、缴费等专有事实由对应场景模块拥有，例如 `jt_approval_instance`、`jt_payment_subject`。专有表通过 definitionId/instanceId 关联公共身份，不向公共表增加 `approvalLevel`、`paymentAmount`、`meetingRoom` 等字段。

公共平台迁移归 `storage-mysql`；场景和能力专有迁移随所属模块提供，由 boot-loader 显式加载，版本号在整个应用内唯一。迁移一旦在共享环境执行不得原地重写。

## 8. API与身份边界

通用 API 以任务定义、任务实例、命令、Signal 和 Action Job 为资源；提醒、待办等易用接口可作为通用命令的友好包装，但不得形成第二套状态模型。

网关从可信接入信息构造 `ActorContext`，包含调用主体、租户、来源、授权范围和 traceId。请求体中的 userId 不能作为长期可信身份依据。资源归属、办理权限和管理权限由 Policy 决定；“收到通知”不自动等于“有权完成任务”。

所有写操作包含 requestId；修改已有资源时包含 expectedRevision。幂等记录绑定调用方、操作、请求摘要和首次确定结果；相同 requestId 不同内容必须冲突。

## 9. Spring、Java与交付纪律

- 编译 release=17，依赖版本仍以03当前基线为候选；03完成2.0修订和实测前不得宣称环境已确认。
- 自有应用对象使用构造器注入；第三方对象和组合装配使用 `@Bean`。事务必须经过 Spring 代理边界。
- 时间使用可注入 Clock；数据库租约使用数据库 UTC 时间。业务时间精确到秒。
- Mapper 使用 XML 参数化 SQL；动态排序使用白名单；禁止用内存仓储替代 MySQL。
- 日志关联 traceId、definitionId、instanceId、signalId、transitionId、actionJobId 和 executionToken，但不输出正文、密码、凭据或令牌值。
- 新场景验收必须证明 kernel 源码和公共平台 DDL 零修改；若做不到，必须说明缺失的跨场景稳定语义并重新审核契约，不能静默修改底层。
- 首次进入项目读00—10；当前2.0文档未全部通过前，不得使用旧1.2接口、DDL或任务计划开始编码。
