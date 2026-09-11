# 04 · 通用任务平台 API 契约

> 人工审核与阶段准入以[00审核台账](00-READING-ORDER.md)为准。本文2.0已于2026-09-10 20:56完成技术可行性审核并通过；这不代表身份系统或接口实现已经完成。

版本2.0；前：[环境与装配](03-INTEGRATION-CONTRACTS.md)，后：[数据库](05-DATABASE.md)。本文定义任务定义、任务实例、场景命令、Signal、Action诊断和站内收件API。业务语义以01为准，扩展边界以02为准。

## 1. API边界与身份

公开路径前缀为`/api/v1`，内部可信路径前缀为`/internal/v1`。公开API只通过`joytask-web → joytask-api-gateway → joytask-service-application`进入平台，不直接暴露Mapper、Action领取、租约、执行令牌或场景内部表。

网关必须从可信认证接入生成ActorContext。生产请求体不接受userId、ownerId或tenantId来声明当前身份；资源查询和命令授权使用ActorContext独立判断。首期本地测试可以在`local`和测试profile启用`X-Debug-Actor-Id`，其他profile出现该头必须拒绝或忽略，不能把调试头当作生产认证方案。

`/internal/v1`只允许受信服务或运维身份访问，必须与公开API使用不同鉴权策略和网络入口。未完成真实身份集成前，生产安全不能标为通过。

## 2. 通用协议

- GET用于读取，POST用于创建和命令；一期不提供DELETE和通用PATCH。
- JSON媒体类型为`application/json`；请求体最大64KiB；未知字段、重复JSON键和非法类型返回400。
- ID在JSON中使用字符串；UUID使用标准小写36字符；时间使用ISO-8601 UTC秒精度，例如`2026-09-10T10:30:00Z`。
- title为1—200个Unicode码点，不允许控制字符或换行；description/body最大4000码点，允许`\n`，拒绝其他控制字符。
- 所有文本作为纯文本保存和返回，不执行HTML、模板或脚本，也不自动trim或改变大小写。
- `scenarioKey`、`providerKey`、`commandKey`和`handlerKey`使用小写字母开头，后续只允许小写字母、数字、短横线和下划线，最长64字符。
- 所有写操作必须包含requestId；修改已有定义或实例的命令必须包含expectedRevision。

统一响应：

```json
{
  "code": "OK",
  "message": "success",
  "traceId": "01J7...",
  "data": {}
}
```

`code=OK`表示当前HTTP操作已成功提交，不等于外部通知已送达或用户已读。错误响应同样使用该信封，data为null；不得返回SQL、堆栈、凭据、executionToken或其他用户资源信息。

## 3. 公共请求与响应对象

### 3.1 创建任务定义

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
      "providerKey": "calendar",
      "schemaVersion": 1,
      "config": {"type": "WEEKLY", "weekday": 5, "localTime": "18:00:00", "zoneId": "Asia/Shanghai"}
    }
  ],
  "scenarioConfig": {"extraReminderCount": 3}
}
```

scenarioConfig、trigger config和command payload在HTTP边界是JSON对象，但进入场景或能力前必须按key + schemaVersion转换为已注册的强类型对象。找不到类型、版本不兼容或校验失败时不得持久化定义。

TaskDefinitionView至少返回：definitionId、scenarioKey、scenarioSchemaVersion、title、description、controlState、revision、participants、triggerBindings、scenarioConfig、createdAt和updatedAt。敏感场景字段可以由场景投影策略脱敏；公共API不返回内部类名或存储表名。

### 3.2 场景命令

```json
{
  "requestId": "a7b40754-ff07-45eb-938c-d253d671de01",
  "expectedRevision": 3,
  "commandSchemaVersion": 1,
  "payload": {"reason": "本周无需执行"}
}
```

定义级稳定命令包括`update`、`pause`、`resume`、`retire`；实例级命令由场景声明，例如S02的`complete`、`skip`、`snooze`，未来审批的`approve`、`reject`。commandKey只能路由到当前scenarioKey注册的处理器，平台不得按具体场景写if/switch。

TaskInstanceView至少返回：instanceId、definitionId、scenarioKey、lifecycleCategory、scenarioState、revision、titleSnapshot、descriptionSnapshot、participants、occurrenceAt、dueAt、allowedCommands、scenarioProjection、createdAt、updatedAt和terminalAt。不存在的通用时间字段返回null；场景专有展示数据只进入scenarioProjection。

定义级和实例级命令统一返回CommandResultView：resourceType、resourceId、resourceRevision、resourceSnapshot和scenarioResult。resourceSnapshot分别是TaskDefinitionView或TaskInstanceView；scenarioResult允许场景返回经版本化、已脱敏的附加结果，没有附加结果时为空对象。API不能因commandKey不同改变最外层响应结构。

### 3.3 分页

列表请求使用`limit`和`cursor`，默认20、最大100。cursor是不透明base64url字符串，绑定ActorContext、端点、过滤条件、排序字段和上一页边界；不能跨身份、租户、端点或过滤条件复用。非法、过期或不匹配的cursor返回INVALID_CURSOR。

统一分页data：

```json
{"items": [], "nextCursor": null, "hasMore": false, "asOf": "2026-09-10T10:30:00Z"}
```

定义默认按updatedAt降序、definitionId降序；实例默认按occurrenceAt升序、instanceId升序，历史终态查询可明确选择terminalAt降序；收件默认按createdAt降序、inboxId降序。所有排序必须有ID作为稳定次序。

## 4. 公开端点

| 编号 | 方法与路径 | 请求重点 | 返回 |
| --- | --- | --- | --- |
| E01 | GET `/api/v1/task-scenarios` | 可选status、cursor、limit | 场景元数据、配置版本、支持命令和所需能力 |
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

E01只返回已装配且允许公开的场景元数据，不提供运行时上传代码。E02可在不创建定义的情况下调用，但仍要执行场景和触发配置的完整强类型校验。

E06的update只影响未来实例并创建新revision；pause、resume、retire遵循01。E09先检查场景是否声明commandKey，再检查ActorContext、生命周期、场景状态和revision。allowedCommands只是界面提示，服务端每次仍重新鉴权和校验。

旧1.2的`/plugins/*`、`/plans/*`、`/instances/complete`等路径不进入2.0首期。当前没有已发布客户端或实现，因此不承担旧路径兼容；未来若增加提醒友好API，只能在api-gateway转换成上述通用命令，不能建立第二套状态和事务。

## 5. 内部端点

| 编号 | 方法与路径 | 用途 |
| --- | --- | --- |
| I01 | POST `/internal/v1/task-signals/{providerKey}` | 可信触发器提交时间、事件、条件或依赖Signal |
| I02 | GET `/internal/v1/task-signals/{signalId}` | 诊断Signal接收、去重和处理结果 |
| I03 | GET `/internal/v1/action-jobs` | 按状态、handlerKey、时间和资源ID查询动作积压 |
| I04 | GET `/internal/v1/action-jobs/{actionJobId}` | 查询动作及attempt摘要，不返回敏感payload或token |
| I05 | GET `/internal/v1/task-transitions` | 按definitionId或instanceId查询迁移链路 |

I01请求至少包含requestId、signalKey、schemaVersion、occurredAt、subject和payload。唯一性绑定providerKey + signalKey；同键同摘要返回首次接收结果，同键不同摘要返回冲突。Signal成功接收只表示持久化事实已经提交，不表示场景迁移或Action已经成功。

一期不提供Action领取、确认、任意重置次数或强制重放HTTP端点；Worker直接使用runtime与storage端口。未来人工重放必须有独立权限、审计和幂等契约，不能通过HTTP暴露executionToken。

## 6. 幂等、并发与校验顺序

E03、E06、E09、E13和I01使用持久化幂等。幂等唯一键至少绑定tenant/actor、operation、requestId；Signal另有providerKey + signalKey业务去重。请求摘要包含默认值展开后的规范对象和expectedRevision，不含requestId；对象键排序，数组保序，时间归一UTC，文本不trim。

处理顺序固定为：

1. HTTP格式、身份、路径和基础字段校验。
2. 幂等键与摘要查询；相同请求返回首次确定结果，不同摘要冲突。
3. 资源归属、扩展存在性、schemaVersion和强类型payload校验。
4. 锁定目标并重验控制状态、生命周期、Policy和expectedRevision。
5. 场景计算TransitionPlan，平台校验并原子提交业务事实、Action、Transition、审计和幂等结果。

数据库死锁、锁等待超时或提交结果未知时，不得返回伪成功。客户端以相同requestId重试；平台通过幂等记录判断返回首次结果或继续安全处理。重复同目标终态命令是否成功由场景声明，但不得重复递增revision、产生Action或审计。

## 7. 错误码

| HTTP | code | 说明 |
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

## 8. 首期场景示例

创建S01时使用scenarioKey=`reminder`，calendar绑定提供五种时间规则；每次发生由平台产生实例并创建notification Action。客户端从TaskInstanceView看到“提醒已触发”，从InboxView查看站内信是否已生成。

创建S02时使用scenarioKey=`recurring_todo`。完成：

```http
POST /api/v1/task-instances/101/commands/complete
Content-Type: application/json

{"requestId":"a7b40754-ff07-45eb-938c-d253d671de01","expectedRevision":1,"commandSchemaVersion":1,"payload":{}}
```

跳过使用commandKey=`skip`并在payload提供reason；稍后提醒使用commandKey=`snooze`并提供snoozeUntil。所有命令都进入同一个E09管道，不为每个场景动作新增Controller方法。
