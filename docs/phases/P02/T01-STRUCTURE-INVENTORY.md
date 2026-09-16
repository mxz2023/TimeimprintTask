# P02 T01 · 结构基线与类型清单

本文件是实施证据，不是长期业务权威。阶段结束时结论并入 DELIVERY；Jackson 记录仅供 X05 基线。

## 1. 基线

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `0c29c8a09f45644e68596730a6f610703390fb61` |
| 记录时机 | 用户授权实施后、首次生产源码变更前 |
| 生成日期 | 2026-09-16 |
| 扫描范围 | 13 个 Maven 模块 `src/main/java` 与 `src/test/java` |
| 对照契约 | [02 §4.1–4.2](../../02-AI-CODING-GUIDE.md)、[P02 README](README.md)、[IMPLEMENTATION T01](IMPLEMENTATION.md) |

授权时工作区另有未提交的 2.3/P02 文档变更；基线提交指向写代码前的 Git 对象。本清单不修改生产 Java。

## 2. 总览

| 观察项 | 数量 |
| --- | --- |
| Maven 模块 | 13 |
| 生产 Java（不含 package-info） | 226 |
| package-info 默认豁免 | 12 |
| 顶层生产类型 | 226 |
| 测试 Java | 63 |
| boot-loader 测试 | 55 |
| 无测试源码模块 | 7 |
| 路径与 package 不一致 | 0 |
| Q01 已合规类型 | 1 |
| Q01 未合规类型 | 225 |
| 所有者测试 OK（同模块同包） | 4 |
| 所有者测试 MISSING | 222 |
| 辅助/端到端测试（非所有者映射） | 59 |
| 大类 ≥400 行 | 4 |
| 中等类 300–399 行 | 5 |
| MyBatis XML namespace | 12 |
| Spring/反射敏感生产文件 | 1 |
| Jackson 生产 Java 文件（X05 基线） | 49 |

### 2.1 包形态分布

| 形态 | 类型数 | 含义 |
| --- | --- | --- |
| `tech-first` | 144 | 技术职责在前（如 service/definition），需迁到业务功能.技术职责 |
| `unknown-segment` | 50 | 首段既非批准业务也非批准技术（如 domain/repo/actor） |
| `flat-root` | 17 | 落在模块根包，缺少业务/技术两级 |
| `biz-only` | 8 | 仅有业务段，缺少技术职责段 |
| `exception-tech` | 7 | common/boot-loader 例外技术分包 |

### 2.2 无测试源码模块

- `timeimprint-task-common`
- `timeimprint-task-service-runtime`
- `timeimprint-task-service-storage-mysql`
- `timeimprint-task-service-capability-notification`
- `timeimprint-task-service-scenario-basic`
- `timeimprint-task-gateway`
- `timeimprint-task-web`

## 3. 分模块摘要

| 模块 | 生产Java | 顶层类型 | 测试Java | 所有者OK | 所有者缺失 | Q01合规 |
| --- | --- | --- | --- | --- | --- | --- |
| `timeimprint-task-common` | 3 | 3 | 0 | 0 | 3 | 0 |
| `timeimprint-task-domain` | 32 | 32 | 2 | 1 | 31 | 0 |
| `timeimprint-task-service-kernel` | 21 | 21 | 1 | 0 | 21 | 0 |
| `timeimprint-task-service-extension-api` | 36 | 36 | 1 | 0 | 36 | 0 |
| `timeimprint-task-service-application` | 58 | 58 | 2 | 1 | 57 | 0 |
| `timeimprint-task-service-runtime` | 7 | 7 | 0 | 0 | 7 | 0 |
| `timeimprint-task-service-storage-mysql` | 40 | 40 | 0 | 0 | 40 | 0 |
| `timeimprint-task-service-capability-calendar` | 3 | 3 | 2 | 2 | 1 | 0 |
| `timeimprint-task-service-capability-notification` | 7 | 7 | 0 | 0 | 7 | 0 |
| `timeimprint-task-service-scenario-basic` | 3 | 3 | 0 | 0 | 3 | 0 |
| `timeimprint-task-gateway` | 1 | 1 | 0 | 0 | 1 | 0 |
| `timeimprint-task-web` | 11 | 11 | 0 | 0 | 11 | 0 |
| `timeimprint-task-boot-loader` | 4 | 4 | 55 | 0 | 4 | 1 |

机器可读明细见同目录 [T01-STRUCTURE-INVENTORY.json](T01-STRUCTURE-INVENTORY.json)。

## 4. 大类与多职责候选

行数只是审查信号。T02 先建特征测试，T03 再按变化原因决定是否拆分。

### 4.1 ≥400 行

| 类型 | 行数 | 模块 | 当前包 | 推断目标包 |
| --- | --- | --- | --- | --- |
| `TaskGateway` | 614 | `timeimprint-task-gateway` | `cn.net.mxz.timeimprint.task.gateway` | `cn.net.mxz.timeimprint.task.gateway.shared.gateway` |
| `DefinitionCommandService` | 496 | `timeimprint-task-service-application` | `cn.net.mxz.timeimprint.task.service.application.service` | `cn.net.mxz.timeimprint.task.service.application.definition.service` |
| `TransitionPlanCommitterImpl` | 489 | `timeimprint-task-service-storage-mysql` | `cn.net.mxz.timeimprint.task.service.storage.mysql.committer` | `cn.net.mxz.timeimprint.task.service.storage.mysql.transition.committer` |
| `ActionWorker` | 471 | `timeimprint-task-service-runtime` | `cn.net.mxz.timeimprint.task.service.runtime` | `cn.net.mxz.timeimprint.task.service.runtime.action.worker` |

### 4.2 300–399 行（次级候选）

| 类型 | 行数 | 模块 | 当前包 | 推断目标包 |
| --- | --- | --- | --- | --- |
| `DefinitionUpdatePortImpl` | 383 | `timeimprint-task-service-storage-mysql` | `cn.net.mxz.timeimprint.task.service.storage.mysql.repo` | `cn.net.mxz.timeimprint.task.service.storage.mysql.definition.<tech?>` |
| `ActionJobRepositoryImpl` | 362 | `timeimprint-task-service-storage-mysql` | `cn.net.mxz.timeimprint.task.service.storage.mysql.repo` | `cn.net.mxz.timeimprint.task.service.storage.mysql.action.<tech?>` |
| `SignalProcessingService` | 361 | `timeimprint-task-service-application` | `cn.net.mxz.timeimprint.task.service.application.service` | `cn.net.mxz.timeimprint.task.service.application.signal.service` |
| `RecurringTodoCommandHandler` | 345 | `timeimprint-task-service-scenario-basic` | `cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo` | `cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo.command` |
| `RecurringTodoScenarioExtension` | 343 | `timeimprint-task-service-scenario-basic` | `cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo` | `cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo.extension` |

## 5. 所有者测试现状

### 5.1 已合格（同模块同包，命名符合 02）

| 生产类型 | 所有者测试 | 模块 |
| --- | --- | --- |
| `ApiMessages` | `ApiMessagesTest` | `timeimprint-task-domain` |
| `TransitionPlanWriteValidator` | `TransitionPlanWriteValidatorTest` | `timeimprint-task-service-application` |
| `CalendarConfigParser` | `CalendarConfigParserTest` | `timeimprint-task-service-capability-calendar` |
| `CalendarOccurrenceCalculator` | `CalendarOccurrenceCalculatorTest` | `timeimprint-task-service-capability-calendar` |

### 5.2 缺失统计（按模块）

| 模块 | 缺失数 |
| --- | --- |
| `timeimprint-task-service-application` | 57 |
| `timeimprint-task-service-storage-mysql` | 40 |
| `timeimprint-task-service-extension-api` | 36 |
| `timeimprint-task-domain` | 31 |
| `timeimprint-task-service-kernel` | 21 |
| `timeimprint-task-web` | 11 |
| `timeimprint-task-service-runtime` | 7 |
| `timeimprint-task-service-capability-notification` | 7 |
| `timeimprint-task-boot-loader` | 4 |
| `timeimprint-task-common` | 3 |
| `timeimprint-task-service-scenario-basic` | 3 |
| `timeimprint-task-service-capability-calendar` | 1 |
| `timeimprint-task-gateway` | 1 |

`package-info.java` 共 12 个，按 02 默认豁免，不要求所有者测试。本清单无其他豁免申请。

### 5.3 辅助 / 端到端测试归属

共 59 个测试类型未映射为任一生产类型的唯一所有者测试（含 boot-loader 验收 IT、夹具与少数契约测试）。它们保留为辅助或纵向证据，**不能**替代模块内所有者测试。完整列表见 JSON `auxiliary_or_e2e_tests`。

## 6. 迁移敏感点

### 6.1 MyBatis XML namespace

| 资源路径 | namespace |
| --- | --- |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/TaskInstanceMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.instance.mapper.TaskInstanceMapper` |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/ActionJobMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper.ActionJobMapper` |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/TaskSignalMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.signal.mapper.TaskSignalMapper` |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/TaskParticipantMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.participant.mapper.TaskParticipantMapper` |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/CommandDedupMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.command.mapper.CommandDedupMapper` |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/TaskTransitionMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.transition.mapper.TaskTransitionMapper` |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/AuditLogMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.audit.mapper.AuditLogMapper` |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/TaskDefinitionMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.definition.mapper.TaskDefinitionMapper` |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/ActionAttemptMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper.ActionAttemptMapper` |
| `timeimprint-task-service-storage-mysql/src/main/resources/mapper/TriggerBindingMapper.xml` | `cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.mapper.TriggerBindingMapper` |
| `timeimprint-task-service-capability-notification/src/main/resources/mapper/NotificationMapper.xml` | `cn.net.mxz.timeimprint.task.service.capability.notification.notification.mapper.NotificationMapper` |
| `timeimprint-task-service-capability-notification/src/main/resources/mapper/InboxMapper.xml` | `cn.net.mxz.timeimprint.task.service.capability.notification.inapp.mapper.InboxMapper` |

### 6.2 Spring 扫描 / 反射敏感生产文件

- `timeimprint-task-service-storage-mysql/src/main/java/cn/net/mxz/timeimprint/task/service/storage/mysql/config/MybatisStorageConfig.java`

### 6.3 Jackson 2 基线（仅记录，P02 禁止迁移）

- 含 Jackson/ObjectMapper 引用的生产 Java 文件数：49
- 含 jackson/fasterxml 字样的 POM：
  - `timeimprint-task-domain/pom.xml`
  - `timeimprint-task-service-application/pom.xml`
  - `timeimprint-task-service-runtime/pom.xml`
  - `timeimprint-task-service-storage-mysql/pom.xml`
  - `timeimprint-task-service-capability-notification/pom.xml`
  - `timeimprint-task-service-scenario-basic/pom.xml`
  - `timeimprint-task-web/pom.xml`
- 文件级列表见 JSON `jackson_prod_files`。P02 只允许建立黄金样例，不得改依赖、import、Mapper 或 MVC 转换器。

## 7. 当前包 → 目标包（按模块抽样规则）

目标包由 02 模块映射与类型名启发式推断，供 T04 批次使用；推断不确定时标记 `<tech?>` / `<biz?>`，实施前人工确认。

### `timeimprint-task-common`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.common` | `exception-tech` | 3 | `BusinessClock`, `Sha256`, `SystemUtcBusinessClock` | `cn.net.mxz.timeimprint.task.common.time` |

### `timeimprint-task-domain`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.domain.view` | `tech-first` | 18 | `ActionJobDiagnosticView`, `AttemptSummary`, `CommandMetadataView` 等18个 | `cn.net.mxz.timeimprint.task.domain.shared.view` |
| `cn.net.mxz.timeimprint.task.domain.request` | `tech-first` | 10 | `CreateTaskDefinitionRequest`, `DefinitionCommandRequest`, `InstanceCommandRequest` 等10个 | `cn.net.mxz.timeimprint.task.domain.shared.request` |
| `cn.net.mxz.timeimprint.task.domain` | `flat-root` | 4 | `ApiErrorCodes`, `ApiMessages`, `ApiResponse` 等4个 | `cn.net.mxz.timeimprint.task.domain.shared.<tech?>` |

### `timeimprint-task-service-kernel`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.service.kernel.domain.plan` | `unknown-segment` | 10 | `ActionJobIntent`, `DefinitionControlTransition`, `InstanceStateTransition` 等10个 | `cn.net.mxz.timeimprint.task.service.kernel.shared.<tech?>` |
| `cn.net.mxz.timeimprint.task.service.kernel.domain.mutation` | `unknown-segment` | 3 | `JsonPayload`, `ScenarioDataMutation`, `ScenarioMutationPayload` | `cn.net.mxz.timeimprint.task.service.kernel.transition.model` |
| `cn.net.mxz.timeimprint.task.service.kernel.domain.identity` | `unknown-segment` | 2 | `TaskDefinitionId`, `TaskInstanceId` | `cn.net.mxz.timeimprint.task.service.kernel.definition.identity` |
| `cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot` | `unknown-segment` | 2 | `TaskDefinitionSnapshot`, `TaskInstanceSnapshot` | `cn.net.mxz.timeimprint.task.service.kernel.definition.model` |
| `cn.net.mxz.timeimprint.task.service.kernel.domain.state` | `unknown-segment` | 2 | `ControlState`, `LifecycleCategory` | `cn.net.mxz.timeimprint.task.service.kernel.shared.state` |
| `cn.net.mxz.timeimprint.task.service.kernel.domain.revision` | `unknown-segment` | 1 | `Revisions` | `cn.net.mxz.timeimprint.task.service.kernel.transition.revision` |
| `cn.net.mxz.timeimprint.task.service.kernel.port` | `unknown-segment` | 1 | `BusinessClock` | `cn.net.mxz.timeimprint.task.service.kernel.shared.<tech?>` |

### `timeimprint-task-service-extension-api`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.service.extension.registry` | `tech-first` | 13 | `ActionHandlerKey`, `ActionHandlerRegistry`, `ExtensionRegistry` 等13个 | `cn.net.mxz.timeimprint.task.service.extension.action.registry` |
| `cn.net.mxz.timeimprint.task.service.extension.context` | `tech-first` | 11 | `ActionExecutionContext`, `ActionExecutionResult`, `ActionJobView` 等11个 | `cn.net.mxz.timeimprint.task.service.extension.shared.context` |
| `cn.net.mxz.timeimprint.task.service.extension.spi` | `tech-first` | 6 | `ActionHandler`, `Policy`, `ScenarioDataMaterializer` 等6个 | `cn.net.mxz.timeimprint.task.service.extension.action.spi` |
| `cn.net.mxz.timeimprint.task.service.extension.action` | `biz-only` | 2 | `ActionExecutionMode`, `ActionHandlerOutcome` | `cn.net.mxz.timeimprint.task.service.extension.action.<tech?>` |
| `cn.net.mxz.timeimprint.task.service.extension.policy` | `biz-only` | 2 | `PolicyDecision`, `PolicyPhase` | `cn.net.mxz.timeimprint.task.service.extension.policy.<tech?>` |
| `cn.net.mxz.timeimprint.task.service.extension.command` | `biz-only` | 1 | `CommandScope` | `cn.net.mxz.timeimprint.task.service.extension.command.<tech?>` |
| `cn.net.mxz.timeimprint.task.service.extension.result` | `tech-first` | 1 | `HandlerResult` | `cn.net.mxz.timeimprint.task.service.extension.shared.result` |

### `timeimprint-task-service-application`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.service.application.port` | `tech-first` | 20 | `ActionJobExecutionPort`, `ActionJobRepository`, `CommandDedupRepository` 等20个 | `cn.net.mxz.timeimprint.task.service.application.shared.port` |
| `cn.net.mxz.timeimprint.task.service.application.model` | `tech-first` | 11 | `ActionJobRecord`, `AttemptRecord`, `CreateDefinitionCommand` 等11个 | `cn.net.mxz.timeimprint.task.service.application.shared.model` |
| `cn.net.mxz.timeimprint.task.service.application.service` | `tech-first` | 10 | `CreateTaskDefinitionService`, `DefinitionCommandService`, `InboxService` 等10个 | `cn.net.mxz.timeimprint.task.service.application.shared.service` |
| `cn.net.mxz.timeimprint.task.service.application.actor` | `unknown-segment` | 5 | `ActorContext`, `ActorContextProvider`, `LocalActorContextProvider` 等5个 | `cn.net.mxz.timeimprint.task.service.application.shared.<tech?>` |
| `cn.net.mxz.timeimprint.task.service.application.limit` | `tech-first` | 4 | `CreateWriteLimitsValidator`, `PlatformLimits`, `TransitionPlanWriteValidator` 等4个 | `cn.net.mxz.timeimprint.task.service.application.shared.limit` |
| `cn.net.mxz.timeimprint.task.service.application.validation` | `tech-first` | 3 | `LocalParticipantCreateValidator`, `ParticipantCreateValidator`, `TestParticipantCreateValidator` | `cn.net.mxz.timeimprint.task.service.application.shared.validation` |
| `cn.net.mxz.timeimprint.task.service.application.exception` | `unknown-segment` | 1 | `ApplicationException` | `cn.net.mxz.timeimprint.task.service.application.shared.<tech?>` |
| `cn.net.mxz.timeimprint.task.service.application.paging` | `tech-first` | 1 | `PageCursors` | `cn.net.mxz.timeimprint.task.service.application.shared.paging` |
| `cn.net.mxz.timeimprint.task.service.application.recipient` | `unknown-segment` | 1 | `RecipientRules` | `cn.net.mxz.timeimprint.task.service.application.inbox.<tech?>` |
| `cn.net.mxz.timeimprint.task.service.application.registry` | `tech-first` | 1 | `ExtensionRegistryImpl` | `cn.net.mxz.timeimprint.task.service.application.extension.registry` |
| `cn.net.mxz.timeimprint.task.service.application.runtime` | `unknown-segment` | 1 | `RuntimeAdmission` | `cn.net.mxz.timeimprint.task.service.application.shared.<tech?>` |

### `timeimprint-task-service-runtime`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.service.runtime` | `flat-root` | 6 | `ActionLeaseReaper`, `ActionWorker`, `RuntimeBeans` 等6个 | `cn.net.mxz.timeimprint.task.service.runtime.action.<tech?>` |
| `cn.net.mxz.timeimprint.task.service.runtime.worker` | `tech-first` | 1 | `QueueWorkers` | `cn.net.mxz.timeimprint.task.service.runtime.action.worker` |

### `timeimprint-task-service-storage-mysql`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.service.storage.mysql.repo` | `unknown-segment` | 15 | `ActionJobRepositoryImpl`, `CommandDedupRepositoryImpl`, `DefinitionControlPortImpl` 等15个 | `cn.net.mxz.timeimprint.task.service.storage.mysql.definition.<tech?>` |
| `cn.net.mxz.timeimprint.task.service.storage.mysql.mapper` | `tech-first` | 10 | `ActionAttemptMapper`, `ActionJobMapper`, `AuditLogMapper` 等10个 | `cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper` |
| `cn.net.mxz.timeimprint.task.service.storage.mysql.row` | `tech-first` | 10 | `ActionAttemptRow`, `ActionJobRow`, `AuditLogRow` 等10个 | `cn.net.mxz.timeimprint.task.service.storage.mysql.action.row` |
| `cn.net.mxz.timeimprint.task.service.storage.mysql` | `flat-root` | 3 | `RowMapper`, `SpringTransactionBoundary`, `StorageTime` | `cn.net.mxz.timeimprint.task.service.storage.mysql.shared.mapper` |
| `cn.net.mxz.timeimprint.task.service.storage.mysql.committer` | `tech-first` | 1 | `TransitionPlanCommitterImpl` | `cn.net.mxz.timeimprint.task.service.storage.mysql.transition.committer` |
| `cn.net.mxz.timeimprint.task.service.storage.mysql.config` | `unknown-segment` | 1 | `MybatisStorageConfig` | `cn.net.mxz.timeimprint.task.service.storage.mysql.shared.configuration` |

### `timeimprint-task-service-capability-calendar`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.service.capability.calendar` | `flat-root` | 3 | `CalendarConfigParser`, `CalendarOccurrenceCalculator`, `CalendarTriggerProvider` | `cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration` |

### `timeimprint-task-service-capability-notification`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.service.capability.notification.mapper` | `tech-first` | 2 | `InboxMapper`, `NotificationMapper` | `cn.net.mxz.timeimprint.task.service.capability.notification.inapp.mapper` |
| `cn.net.mxz.timeimprint.task.service.capability.notification.row` | `tech-first` | 2 | `InboxRow`, `NotificationRow` | `cn.net.mxz.timeimprint.task.service.capability.notification.inapp.row` |
| `cn.net.mxz.timeimprint.task.service.capability.notification.handler` | `tech-first` | 1 | `InAppNotificationHandler` | `cn.net.mxz.timeimprint.task.service.capability.notification.inapp.handler` |
| `cn.net.mxz.timeimprint.task.service.capability.notification.port` | `tech-first` | 1 | `NotificationMaterializationPort` | `cn.net.mxz.timeimprint.task.service.capability.notification.notification.port` |
| `cn.net.mxz.timeimprint.task.service.capability.notification.repo` | `unknown-segment` | 1 | `NotificationMaterializationAdapter` | `cn.net.mxz.timeimprint.task.service.capability.notification.notification.adapter` |

### `timeimprint-task-service-scenario-basic`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo` | `biz-only` | 2 | `RecurringTodoCommandHandler`, `RecurringTodoScenarioExtension` | `cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo.command` |
| `cn.net.mxz.timeimprint.task.service.scenario.basic.reminder` | `biz-only` | 1 | `ReminderScenarioExtension` | `cn.net.mxz.timeimprint.task.service.scenario.basic.reminder.extension` |

### `timeimprint-task-gateway`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.gateway` | `flat-root` | 1 | `TaskGateway` | `cn.net.mxz.timeimprint.task.gateway.shared.gateway` |

### `timeimprint-task-web`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.web.controller` | `tech-first` | 6 | `HttpBoundaryProbeController`, `InboxController`, `InternalDiagnosticController` 等6个 | `cn.net.mxz.timeimprint.task.web.shared.controller` |
| `cn.net.mxz.timeimprint.task.web.config` | `unknown-segment` | 4 | `Jackson2WebConfig`, `RequestBodySizeFilter`, `ShutdownWriteRejectFilter` 等4个 | `cn.net.mxz.timeimprint.task.web.shared.filter` |
| `cn.net.mxz.timeimprint.task.web.error` | `tech-first` | 1 | `ApiExceptionHandler` | `cn.net.mxz.timeimprint.task.web.shared.error` |

### `timeimprint-task-boot-loader`

| 当前包 | 形态 | 类型数 | 示例类型 | 推断目标包（众数） |
| --- | --- | --- | --- | --- |
| `cn.net.mxz.timeimprint.task.boot` | `exception-tech` | 3 | `LoopbackAddressEnvironmentPostProcessor`, `ShutdownAdmissionLifecycle`, `TimeImprintTaskApplication` | `cn.net.mxz.timeimprint.task.boot.bootstrap` |
| `cn.net.mxz.timeimprint.task.boot.health` | `exception-tech` | 1 | `LiveSchemaReadinessIndicator` | `cn.net.mxz.timeimprint.task.boot.health` |

## 8. T01 完成判定

- [x] 已记录不可变 `baselineGitRef`
- [x] 13 模块生产/测试类型、包路径、所有者测试与大类清单已生成
- [x] 生产/测试包不一致、无测试模块、跨模块测试归属已列出
- [x] Spring/MyBatis/反射与 Jackson 2 敏感点已记录（Jackson 不迁移）
- [x] 本任务未修改生产源码、依赖、API、DDL 或 SPI

下一任务：[T02 建立行为安全网](IMPLEMENTATION.md)（补齐所有者测试、大类特征测试与 JSON 黄金样例）。
