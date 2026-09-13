# 04 · 通用任务平台 API 契约

> 阅读入口与阶段状态见[00开发导航](00-READING-ORDER.md)。本文是HTTP请求、响应、读模型、错误和接口幂等的正式来源；不代表生产身份系统或接口实现已经完成。

版本2.2；前：[环境与装配](03-INTEGRATION-CONTRACTS.md)，后：[数据库](05-DATABASE.md)。本文定义任务定义、任务实例、场景命令、Signal、Action诊断和站内收件API。公共业务语义以01为准，场景专有语义以[场景目录](scenarios/README.md)为准，能力范围以[能力目录](capabilities/README.md)为准，扩展边界以02为准。

## 1. API边界与身份

公开路径前缀为`/api/v1`，内部可信路径前缀为`/internal/v1`。公开HTTP只通过`timeimprint-task-web → timeimprint-task-gateway → timeimprint-task-service-application`进入平台，不直接暴露Mapper、Action领取、租约、执行令牌或场景内部表。

网关必须通过ActorContextProvider生成ActorContext。首期local profile只使用配置中的固定tenantId和actorId，请求头及请求体都不得覆盖；test profile可以启用`X-Debug-Actor-Id`及受控测试租户切换以验证权限隔离。其他profile必须接入正式可信身份提供器，缺失时公开API不得就绪；任何生产请求体都不接受userId、ownerId或tenantId来声明当前身份。资源查询和命令授权始终使用ActorContext独立判断。

`/internal/v1`在local profile只允许本机受信入口，在未来生产环境只允许受信服务或运维身份访问，并与公开API使用不同鉴权策略和网络入口。未完成真实身份集成前，生产安全不能标为通过。

## 2. 通用协议

- GET用于读取，POST用于创建和命令；一期不提供DELETE和通用PATCH。
- JSON媒体类型为`application/json`；请求体最大64KiB；未知字段、重复JSON键和非法类型返回400。
- ID在JSON中使用字符串；UUID使用标准小写36字符；时间使用ISO-8601 UTC秒精度，例如`2026-09-10T10:30:00Z`。
- revision、generation、version、count、limit和weekday/day/interval等数值使用JSON整数；不得用字符串或小数表示。
- title为1—200个Unicode码点，不允许控制字符或换行；description/body最大4000码点，允许`\n`，拒绝其他控制字符。
- 所有文本作为纯文本保存和返回，不执行HTML、模板或脚本，也不自动trim或改变大小写。
- `scenarioKey`、`providerKey`、`commandKey`和`handlerKey`使用小写字母开头，后续只允许小写字母、数字、短横线和下划线，最长64字符。
- 所有写操作必须包含requestId；修改已有定义或实例的命令必须包含expectedRevision。
- 响应中本契约列出的字段始终出现：不存在的可空标量序列化为null，空数组为`[]`，空扩展对象为`{}`；不得通过省略字段表达null或空集合。

统一响应：

```json
{
  "code": "OK",
  "message": "已查询任务实例详情",
  "traceId": "01J7...",
  "data": {}
}
```

`code`为稳定英文机器码（成功恒为`OK`，失败见第7节）。`message`必须为简体中文可读说明，面向调用方与AI编排：成功时说明本接口刚完成的动作及关键结果要点（例如创建了哪个定义、执行了哪个命令、revision/场景状态如何变化、是否幂等重放）；失败时说明拒绝原因与可执行的下一步（例如应改用`allowedCommands`中的命令、应先刷新revision）。禁止仅返回`OK`、`success`、`error`等无语义占位词。`code=OK`表示当前HTTP操作已成功提交，不等于外部通知已送达或用户已读。错误响应同样使用该信封，data为null；不得返回SQL、堆栈、凭据、executionToken或其他用户资源信息。

## 3. 公共请求与响应对象

### 3.1 Calendar schemaVersion 1

S01/S02首期必须且只能传一个`bindingKey="primary"`、`providerKey="calendar"`、`schemaVersion=1`的trigger binding。config是以下五种互斥对象之一，表中字段全部必填且不允许额外字段：

| 类型 | 配置精确字段 | 语义 |
| --- | --- | --- |
| ONCE | `type, localDate, localTime, zoneId` | 在本地日期时间发生一次 |
| DAILY | `type, startDate, localTime, zoneId` | 从startDate起每个自然日发生 |
| WEEKLY | `type, startDate, weekday, localTime, zoneId` | 从startDate起每周在weekday发生 |
| MONTHLY | `type, startDate, dayOfMonth, localTime, zoneId` | 从startDate起每月发生；无该日取月末 |
| EVERY_N_DAYS | `type, startDate, intervalDays, localTime, zoneId` | 以startDate为固定锚点每N个自然日发生 |

`localDate/startDate`格式固定`yyyy-MM-dd`；`localTime`固定`HH:mm:ss`且必须有秒；`zoneId`一期只能是`Asia/Shanghai`；`weekday`为ISO 1—7（周一至周日）；`dayOfMonth`为1—31；`intervalDays`为1—3650。startDate是包含边界，算法只返回同时满足规则且严格晚于`after`的发生；它可以早于当前日期，不能因重启改写。ONCE在E03/E06提交判定时必须严格晚于事务时间T，E02预览若已不晚于after则返回空列表。

occurrenceAt由本地日期时间按zoneId转换为UTC整秒，occurrenceKey固定为其UTC格式`yyyyMMdd'T'HHmmss'Z'`；唯一性范围还包含definition、binding、scheduleGeneration和controlGeneration。预览、创建和Planner必须调用同一规范化与计算实现。规范化只展开S02默认值、统一日期/时间格式和对象键顺序，不trim文本、不改变数组顺序，也不替调用方猜测缺失的日历字段。

E02请求精确为`scenarioKey, scenarioSchemaVersion, triggerBindings, scenarioConfig, after, limit`，不含requestId；limit为1—100，after为UTC整秒。PreviewResult精确返回`scenarioKey, scenarioSchemaVersion, normalizedTriggerBindings, normalizedScenarioConfig, occurrences`；每个occurrence含`occurrenceKey, occurrenceAt, dueAt`，S01的dueAt为null，S02的dueAt等于occurrenceAt。预览不返回definitionId或写入任何幂等记录。

### 3.2 创建任务定义

```json
{
  "requestId": "c38aaf21-d8d4-4e67-a077-34855877b220",
  "scenarioKey": "recurring_todo",
  "scenarioSchemaVersion": 1,
  "title": "提交周报",
  "description": "整理本周工作",
  "participants": [
    {"principalType": "USER", "principalId": "zhangsan", "roleCode": "OWNER"},
    {"principalType": "USER", "principalId": "zhangsan", "roleCode": "RECIPIENT"}
  ],
  "triggerBindings": [
    {
      "bindingKey": "primary",
      "providerKey": "calendar",
      "schemaVersion": 1,
      "config": {"type": "WEEKLY", "startDate": "2026-09-01", "weekday": 5, "localTime": "18:00:00", "zoneId": "Asia/Shanghai"}
    }
  ],
  "scenarioConfig": {
    "chaseOffsetsMinutes": [60, 240, 720],
    "notificationExpireAfterMinutes": 1440,
    "maxSnoozeCount": 3
  }
}
```

CreateTaskDefinitionRequest恰好包含上述字段；除`description`可为null、S02的scenarioConfig三个字段可省略并展开默认值外，其余字段必填。ParticipantInput精确包含`principalType, principalId, roleCode`，sourceCode由平台固定写为DIRECT，调用方不能提交metadata或来源。S01 schemaVersion 1的scenarioConfig必须是空对象。scenarioConfig、trigger config和command payload在HTTP边界是JSON对象，但进入场景或能力前必须按key + schemaVersion转换为已注册的强类型对象；找不到类型、版本不兼容或校验失败时不得持久化定义。

S02 schemaVersion 1只接受上述三个场景配置字段：`chaseOffsetsMinutes`为0—3个严格递增且不重复的整数，每项范围1至`notificationExpireAfterMinutes - 1`；`notificationExpireAfterMinutes`范围60—10080，默认1440；`maxSnoozeCount`范围0—3，默认3。默认催办偏移为`[60,240,720]`。S01/S02都至少有一个OWNER；RECIPIENT去重后最多10个。缺省RECIPIENT时由场景把OWNER投影为接收人，显式提供RECIPIENT时不自动追加OWNER。首期公开接口的主体类型只接受USER；local profile中OWNER和RECIPIENT的principalId必须等于固定actor，test profile才允许构造多身份用例。

### 3.3 定义update的完整替换语义

E06 `commandKey=update`固定使用commandSchemaVersion 1，payload必须完整提供`scenarioSchemaVersion, title, description, participants, triggerBindings, scenarioConfig`六个字段且不允许其他字段。description用null显式清空；其余字段不能为null或省略。scenarioKey、definitionId、controlState、createdAt和createdBy不可更新；scenarioSchemaVersion一期必须等于当前版本，未来版本升级使用另行批准的迁移命令，不能借update静默升级。

update是“可变定义快照整体替换”，不是JSON merge、字段级PATCH或数组增量操作。participants整体替换定义级关系；triggerBindings按bindingKey整体替换；scenarioConfig整体替换并先展开默认值。update允许ACTIVE或PAUSED定义：ACTIVE时按01同步更新/重建未来窗口，PAUSED时只保存新定义快照、增加必要版本并保持无未来窗口，待resume从恢复时刻之后规划。规范化后的六个字段全部不变时返回NoChange，revision、scheduleGeneration、Transition和Action均不变。任一字段真实变化时definition revision只增加1；只有calendar配置变化时对应binding的scheduleGeneration增加1，标题、description、participants或S02配置变化不增加scheduleGeneration。更新不得覆盖ACTIVE/TERMINAL实例快照。

pause、resume、retire的payload必须是空对象。pause允许ACTIVE，resume允许PAUSED，retire允许ACTIVE或PAUSED；目标状态已经相同且expectedRevision仍匹配时返回NoChange。RETIRED不可resume或update。DefinitionCommandRequest外层始终为`requestId, expectedRevision, commandSchemaVersion, payload`。

### 3.4 场景命令

```json
{
  "requestId": "a7b40754-ff07-45eb-938c-d253d671de01",
  "expectedRevision": 3,
  "commandSchemaVersion": 1,
  "payload": {"reason": "本周无需执行"}
}
```

定义级稳定命令`update/pause/resume/retire`由application处理通用控制语义；实例级命令由场景声明，例如S02的`complete/skip/snooze`。实例commandKey只能路由到`(scenarioKey, INSTANCE, commandKey, commandSchemaVersion)`唯一TaskCommandHandler，平台不得按具体场景写if/switch，也不得回退给ScenarioExtension。

定义级和实例级命令统一返回CommandResultView：resourceType、resourceId、resourceRevision、changed、resourceSnapshot和scenarioResult。Applied时changed=true；NoChange时changed=false。resourceSnapshot分别是TaskDefinitionView或TaskInstanceView；scenarioResult允许场景返回经版本化、已脱敏的附加结果，没有附加结果时为空对象。API不能因commandKey不同改变最外层响应结构。

S02 `complete`的payload只允许可选`reason`，长度0—500个Unicode码点；`skip`必须提供长度1—500的`reason`；`snooze`必须提供UTC秒精度的`snoozeUntil`且不接受其他字段。成功snooze的scenarioResult返回`snoozeCount`、`actionGeneration`和新的剩余提醒时间列表；超过次数、没有可移动Action、时间不在合法窗口或平移后达到失效边界时返回STATE_CONFLICT且不写数据。

### 3.5 公开读模型

公开响应不得使用“至少返回”或由实现自行增减首期字段。字段固定如下；新增字段必须先更新契约和契约测试。

| 视图 | 精确字段 |
| --- | --- |
| ScenarioMetadataView | `scenarioKey, displayName, contractVersion, supportedScenarioSchemaVersions, definitionCommands, instanceCommands, requiredCapabilities` |
| ParticipantView | `principalType, principalId, roleCode, sourceCode` |
| TriggerBindingView | `bindingId, bindingKey, providerKey, schemaVersion, config, bindingState, scheduleGeneration, nextFireAt, exhausted, revision` |
| TaskDefinitionView | `definitionId, scenarioKey, scenarioSchemaVersion, title, description, controlState, controlGeneration, revision, participants, triggerBindings, scenarioConfig, allowedCommands, createdAt, updatedAt, pausedAt, retiredAt` |
| TaskInstanceView | `instanceId, definitionId, scenarioKey, scenarioSchemaVersion, lifecycleCategory, scenarioState, revision, titleSnapshot, descriptionSnapshot, participants, occurrenceAt, dueAt, allowedCommands, scenarioProjection, deliverySummary, createdAt, updatedAt, terminalAt` |
| CommandResultView | `resourceType, resourceId, resourceRevision, changed, resourceSnapshot, scenarioResult` |
| InboxView | `inboxId, notificationId, actionJobId, definitionId, instanceId, scenarioKey, purpose, title, body, readAt, createdAt` |

CommandMetadataView精确包含`commandKey, supportedCommandSchemaVersions`；版本数组升序。S01/S02的definitionCommands固定声明update、pause、resume、retire四项及commandSchemaVersion 1，具体某一时刻是否可用由TaskDefinitionView.allowedCommands表达；instanceCommands来自已注册TaskCommandHandler。requiredCapabilities是按capabilityKey字典序排列的字符串数组。

allowedCommands只返回当前Actor在响应生成时、基于当前revision可尝试的commandKey，是界面提示而不是后续授权承诺。scenarioConfig、config、scenarioProjection和scenarioResult都是已按版本投影且脱敏的JSON对象；无附加结果返回空对象，不返回null。S01 schemaVersion 1的scenarioProjection只有`displayState`一个字段，值取PLANNED、TRIGGERED、CANCELLED之一；S02只有`snoozeCount, maxSnoozeCount, actionGeneration, chaseOffsetsMinutes, notificationExpireAfterMinutes`五个字段，值来自实例快照。complete/skip的scenarioResult为空对象，snooze使用3.4规定的结果。不存在的通用时间返回null。

所有响应数组有稳定顺序：participants按roleCode、principalType、principalId升序；triggerBindings按bindingKey升序；allowedCommands按commandKey升序；occurrences按occurrenceAt、occurrenceKey升序；attempts按attemptNo升序。实现不得依赖数据库未声明顺序。

DeliverySummary固定返回`deliveryState, totalCount, readyCount, runningCount, retryWaitCount, succeededCount, deadCount, cancelledCount, expiredCount, unknownCount, inboxCount, unreadInboxCount, updatedAt`。计数覆盖该instance全部Action；控制代次失配但尚未物理清理的READY/RETRY_WAIT或未开始副作用的RUNNING Action等效计入cancelledCount，不再计入原技术状态；已提交effectStartedAt的EXTERNAL RUNNING仍计入runningCount直至按实际结果闭合。deliveryState按以下顺序唯一计算：totalCount=0为`NOT_SCHEDULED`；存在UNKNOWN为`UNKNOWN`；存在READY/RUNNING/RETRY_WAIT为`IN_PROGRESS`；全部SUCCEEDED为`DELIVERED`；有SUCCEEDED且同时有DEAD/CANCELLED/EXPIRED为`PARTIALLY_DELIVERED`；无SUCCEEDED且有DEAD为`FAILED`；其余只要有EXPIRED为`EXPIRED`；最后为`CANCELLED`。updatedAt取相关Action.updatedAt、Inbox.createdAt和Inbox.readAt中的最大非null值；totalCount=0时为null。这里的DELIVERED仅表示Handler成功，站内信可由inboxCount证明，外部渠道最终送达仍以未来渠道回执为准；用户是否阅读只由unreadInboxCount/readAt表达。

I01的SignalAcceptedView固定返回`signalId, duplicated, processStatus, receivedAt`；首次接受duplicated=false，幂等重放为true。I02/I06的SignalDiagnosticView固定返回`signalId, definitionId, instanceId, providerKey, signalKey, schemaVersion, processStatus, attemptCount, maxAttempts, nextAttemptAt, leaseOwner, leaseUntil, resultCode, resultSummary, occurredAt, receivedAt, processedAt, parentSignalId, redriveNo`。I03/I04/I07的ActionJobDiagnosticView固定返回`actionJobId, definitionId, instanceId, transitionId, handlerKey, executionMode, schemaVersion, targetType, storedStatus, effectiveStatus, attemptCount, maxAttempts, availableAt, expiresAt, nextAttemptAt, leaseOwner, leaseUntil, outcomeCode, outcomeSummary, completedAt, parentActionJobId, redriveNo, attempts`；storedStatus是表中原值。控制代次失配时，READY/RETRY_WAIT以及尚未开始副作用的RUNNING返回effectiveStatus=CANCELLED；已经提交effectStartedAt的EXTERNAL RUNNING仍返回RUNNING直至按实际结果闭合；其他情况effectiveStatus等于storedStatus。Attempt摘要只含`attemptNo, startedAt, finishedAt, effectStarted, outcome, errorClass, errorCode, providerReference, safeSummary`。I05的TransitionDiagnosticView固定返回`transitionId, definitionId, instanceId, sourceType, sourceKey, commandKey, fromControlState, toControlState, fromLifecycle, toLifecycle, fromScenarioState, toScenarioState, fromRevision, toRevision, actorType, actorId, traceId, createdAt`，不返回summaryJson原文。I03的`status`过滤参数匹配storedStatus，I03/I05使用3.6的Page结构。所有内部视图都不返回payload、hash、executionToken或通知正文。

### 3.6 分页

列表请求使用`limit`和`cursor`，默认20、最大100。cursor是不透明base64url字符串，绑定ActorContext、端点、过滤条件、排序字段和上一页边界；不能跨身份、租户、端点或过滤条件复用。非法、过期或不匹配的cursor返回INVALID_CURSOR。

统一分页data：

```json
{"items": [], "nextCursor": null, "hasMore": false, "asOf": "2026-09-10T10:30:00Z"}
```

定义默认按updatedAt降序、definitionId降序；实例默认按occurrenceAt升序、instanceId升序，历史终态查询可明确选择terminalAt降序；收件默认按createdAt降序、inboxId降序。所有排序必须有ID作为稳定次序。分页采用READ COMMITTED下的弱一致性keyset语义：`asOf`是响应生成时间而不是数据库快照。不可变排序字段的列表在并发新增下不得重漏；definition的updatedAt在翻页期间变化时允许项目移出本次遍历，调用方需要完整刷新时应从第一页重新开始。

## 4. 公开端点

| 编号 | 方法与路径 | 请求重点 | 返回 |
| --- | --- | --- | --- |
| E01 | GET `/api/v1/task-scenarios` | cursor、limit | Page<ScenarioMetadataView> |
| E02 | POST `/api/v1/task-definitions/preview` | scenarioKey、版本、triggerBindings、scenarioConfig、after、limit | 规范化配置和未来发生预览；不写业务数据 |
| E03 | POST `/api/v1/task-definitions` | CreateTaskDefinitionRequest | TaskDefinitionView |
| E04 | GET `/api/v1/task-definitions/{definitionId}` | 路径ID | TaskDefinitionView |
| E05 | GET `/api/v1/task-definitions` | scenarioKey、controlState、participantRole、cursor、limit | Page<TaskDefinitionView> |
| E06 | POST `/api/v1/task-definitions/{definitionId}/commands/{commandKey}` | DefinitionCommandRequest | CommandResultView，内含TaskDefinitionView |
| E07 | GET `/api/v1/task-instances/{instanceId}` | 路径ID | TaskInstanceView |
| E08 | GET `/api/v1/task-instances` | definitionId、scenarioKey、lifecycleCategory、scenarioState、participantRole、from、to、cursor、limit | Page<TaskInstanceView> |
| E09 | POST `/api/v1/task-instances/{instanceId}/commands/{commandKey}` | InstanceCommandRequest | CommandResultView，内含TaskInstanceView |
| E10 | GET `/api/v1/inbox` | unreadOnly、scenarioKey、cursor、limit | Page<InboxView> |
| E11 | GET `/api/v1/inbox/{inboxId}` | 路径ID | InboxView |
| E12 | GET `/api/v1/inbox-unread-count` | 无 | unreadCount、asOf |
| E13 | POST `/api/v1/inbox/{inboxId}/commands/mark-read` | requestId | InboxView；重复读取保留首次readAt |

E01只返回已装配且允许公开的ScenarioMetadataView，不提供运行时上传代码。E02可在不创建定义的情况下调用，但仍要执行场景和触发配置的完整强类型校验。

E06的update严格遵守3.3完整替换语义；pause、resume、retire遵循01。定义控制命令不得路由TaskCommandHandler。E09先检查场景是否声明并唯一注册commandKey，再检查ActorContext、生命周期、场景状态和revision。allowedCommands只是界面提示，服务端每次仍重新鉴权和校验。

当前没有已发布客户端或历史实现，因此首期只实现本章E01—E13，不提供额外兼容路径。未来若增加提醒友好入口，只能在gateway转换成上述通用命令，不能建立第二套状态和事务。

## 5. 内部端点

| 编号 | 方法与路径 | 用途 |
| --- | --- | --- |
| I01 | POST `/internal/v1/task-signals/{providerKey}` | 可信触发器提交时间、事件、条件或依赖Signal |
| I02 | GET `/internal/v1/task-signals/{signalId}` | 诊断Signal接收、去重和处理结果 |
| I03 | GET `/internal/v1/action-jobs` | 按状态、handlerKey、时间和资源ID查询动作积压 |
| I04 | GET `/internal/v1/action-jobs/{actionJobId}` | 查询动作及attempt摘要，不返回敏感payload或token |
| I05 | GET `/internal/v1/task-transitions` | 按definitionId或instanceId查询迁移链路 |
| I06 | POST `/internal/v1/task-signals/{signalId}/commands/redrive` | 对DEAD Signal创建有审计关联的新Signal，不修改原行 |
| I07 | POST `/internal/v1/action-jobs/{actionJobId}/commands/redrive` | 仅对LOCAL_TRANSACTIONAL的DEAD Action创建有审计关联的新Action |

I01请求精确包含`requestId, signalKey, schemaVersion, occurredAt, subject, payload`且不允许额外字段；subject精确包含`definitionId`和可选`instanceId`。definitionId必须直接定位一个当前Actor可提交Signal的定义，instanceId存在时必须属于该definition；首期不接受外部业务键解析、无目标、延迟解析或广播Signal。唯一性绑定providerKey + signalKey；同键同摘要返回首次signalId，同键不同摘要返回冲突。Signal成功接收只表示持久化事实已经提交，不表示场景迁移或Action已经成功。

I06/I07请求包含requestId、expectedStatus=`DEAD`和1—500码点的reason。服务先解析最初的正常根记录；每个根对象最多重驱3次，新行的parentId始终指向该根记录并保存递增redriveNo，不原地重置任何历史行的状态、次数或executionToken。来源记录的definitionControlGeneration必须仍等于定义当前controlGeneration，并重新通过当前控制状态、实例生命周期和Policy屏障；控制代次已变化或业务资格已失效时拒绝重驱，需走显式补偿业务。I07还拒绝EXTERNAL、UNKNOWN及任何可能已经开始外部副作用的Action。两个端点只在受信内部入口启用并写Audit；一期仍不提供Action领取、确认或任意修改次数端点。

## 6. 幂等、并发与校验顺序

E03、E06、E09、E13、I01、I06和I07使用持久化幂等。幂等唯一键至少绑定tenant/actor、operation、requestId；Signal另有providerKey + signalKey业务去重。请求摘要包含默认值展开后的规范对象和expectedRevision/expectedStatus，不含requestId；对象键排序，数组保序，时间归一UTC，文本不trim。

处理顺序固定为：

1. HTTP格式、身份、路径和基础字段校验。
2. 写入PROCESSING幂等行或等待相同唯一键的事务结束并核对摘要；相同请求返回首次COMPLETED结果，不同摘要冲突。同步命令取得command dedup行锁后，才按06顺序锁definition/instance；PROCESSING不得单独提交。
3. 资源归属、扩展存在性、schemaVersion和强类型payload校验。
4. 锁定目标并重验控制状态、生命周期、Policy和expectedRevision。
5. 定义控制命令由application计算计划，实例业务命令由唯一TaskCommandHandler计算计划，Signal由ScenarioExtension计算计划；平台统一校验并原子提交业务事实、Action、Transition、审计和幂等结果。

创建、更新、命令、重驱和规划必须在写入前执行03的事务规模校验。超过参与人、接收人、触发绑定、发生、Action、ScenarioDataMutation、单JSON、计划总字节、预计变更行数或事务时间上限统一返回INVALID_REQUEST或使后台本轮安全缩小；不得静默截断，也不得返回成功后再异步补齐本应原子创建的内容。

确定性成功或业务拒绝可以提交为可重放的COMPLETED幂等结果；数据库死锁、锁等待超时、技术异常或提交结果未知时不得保留PROCESSING占位或返回伪成功。客户端以相同requestId重试；平台通过幂等记录判断返回首次结果或继续安全处理。重复同目标终态命令是否成功由场景声明，但不得重复递增revision、产生Action或审计。

## 7. 错误码

| 响应状态码 | 错误码 | 说明 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | JSON、字段、文本、时间、key或组合校验失败 |
| 400 | INVALID_CURSOR | 游标格式、版本、身份或过滤条件不匹配 |
| 400 | UNSUPPORTED_SCHEMA_VERSION | 场景、触发器、命令或Signal版本不受支持 |
| 401 | UNAUTHENTICATED | 没有可信调用身份 |
| 403 | FORBIDDEN | 身份存在但没有执行该操作的权限 |
| 404 | RESOURCE_NOT_FOUND | 资源不存在或对当前Actor不可见 |
| 404 | EXTENSION_NOT_FOUND | scenario/provider/handler未装配或不可用 |
| 409 | IDEMPOTENCY_CONFLICT | 同一幂等键对应不同请求摘要 |
| 409 | REVISION_CONFLICT | expectedRevision与当前版本不一致 |
| 409 | STATE_CONFLICT | 当前控制状态、生命周期或场景状态不允许操作 |
| 409 | COMMAND_NOT_SUPPORTED | 当前场景未声明该commandKey |
| 413 | REQUEST_TOO_LARGE | 请求体超过64KiB |
| 415 | UNSUPPORTED_MEDIA_TYPE | 媒体类型不支持 |
| 429 | POLICY_REJECTED | 频控或其他可重试政策暂时拒绝 |
| 500 | INTERNAL_ERROR | 未预期错误；响应不泄露内部信息 |
| 503 | RETRY_LATER | 数据库临时不可用、锁重试耗尽或提交结果待确认 |

同一资源对无权Actor统一返回404还是403由安全策略决定，但同一端点必须一致并通过防枚举测试。外部Action的DEAD或UNKNOWN不是创建命令的HTTP 500；创建命令成功只说明状态和Action意图已经提交。

正常停机一旦readiness转为REFUSING_TRAFFIC，尚未进入幂等事务的新写请求统一返回503 RETRY_LATER；已经进入事务的请求按06完成或整体回滚。客户端必须以原requestId重试，服务不得在停机窗口返回无幂等记录的伪成功。

## 8. 首期场景示例

创建S01时使用scenarioKey=`reminder`，calendar绑定提供五种时间规则；每次发生由平台产生实例并创建notification Action。客户端从TaskInstanceView看到“提醒已触发”，从InboxView查看站内信是否已生成。

创建S02时使用scenarioKey=`recurring_todo`。完成：

```http
POST /api/v1/task-instances/101/commands/complete
Content-Type: application/json

{"requestId":"a7b40754-ff07-45eb-938c-d253d671de01","expectedRevision":1,"commandSchemaVersion":1,"payload":{}}
```

跳过使用commandKey=`skip`并在payload提供必填reason；稍后提醒使用commandKey=`snooze`并提供未来的snoozeUntil。首期S02的occurrenceAt与dueAt相同，INITIAL在dueAt执行，默认CHASE在其后60、240、720分钟执行，所有动作统一在dueAt后1440分钟失效。所有命令都进入同一个E09管道，不为每个场景动作新增Controller方法。
