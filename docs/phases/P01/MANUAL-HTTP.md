# P01 · 手动 HTTP 测试手册

> 本文是本地手工联调的操作手册，**不替代** [04 API 契约](../../04-API.md)。字段语义、错误码与幂等规则以 04 为准。
> 当前阶段：P01 / VERIFYING。公开 [E01](../../04-API.md)—[E13](../../04-API.md) 与内部 [I01](../../04-API.md)—[I07](../../04-API.md) **均已挂载**。
> 建议顺序：§1 启动 → §3 S01 主路径 → §4 S02 主路径 → §5 按需抽测 Controller → §7 边界烟测。

## 1. 前置条件

| 项目 | 建议值 |
| --- | --- |
| 监听 | `127.0.0.1:18080`（与 `application.yml` 默认一致；也可改 `SERVER_PORT`） |
| 数据库 | `jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?...` |
| 账号 | `tit` / `tit_local`（与本地 `tit-mysql-t01` 容器一致时） |
| 身份 | local profile 固定 `LOCAL_TENANT_ID=local-tenant`、`LOCAL_ACTOR_ID=local-actor`；**请求体/头不得覆盖身份** |
| `requestId` | 标准 UUID 小写（`8-4-4-4-12`） |

### 1.1 启动

```bash
export JAVA_HOME="/Users/gemini/Library/Java/JavaVirtualMachines/corretto-21.0.12/Contents/Home"
export SERVER_ADDRESS=127.0.0.1
export SERVER_PORT=18080
export DB_JDBC_URL='jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC'
export DB_USERNAME=tit
export DB_PASSWORD=tit_local
export INSTANCE_ID=local-1
export LOCAL_TENANT_ID=local-tenant
export LOCAL_ACTOR_ID=local-actor
export WORKER_ENABLED=true

cd /Users/gemini/Code/MyStudio/TimeImprintTask
# 一条命令：-am 编译依赖模块；父工程/库模块已默认 skip spring-boot:run
./mvnw -pl timeimprint-task-boot-loader -am -DskipTests spring-boot:run
```

或先打包再跑 jar：

```bash
./mvnw -pl timeimprint-task-boot-loader -am -DskipTests package
java -jar timeimprint-task-boot-loader/target/timeimprint-task-boot-loader-*.jar
```

说明：只跑 `./mvnw -pl timeimprint-task-boot-loader spring-boot:run`（不加 `-am`）时，若尚未 `package`/`install`，会报兄弟模块 `0.1.0-SNAPSHOT` 找不到。

### 1.2 另开终端：公共变量与健康检查

```bash
BASE=http://127.0.0.1:18080
HDR=(-H 'Content-Type: application/json' -H 'Accept: application/json')
uuid() { uuidgen | tr '[:upper:]' '[:lower:]'; }

curl -sS "$BASE/actuator/health/liveness"
curl -sS "$BASE/actuator/health/readiness"
```

统一响应信封：`code` 为英文稳定码；`message` 为**简体中文动作说明**（见 [04-API](../../04-API.md) §2），成功须说明本接口做了什么，失败须说明原因与下一步。示例：

```json
{
  "code": "OK",
  "message": "已完成发生预览且未写入业务数据，scenarioKey=reminder，occurrences=1",
  "traceId": "...",
  "data": {}
}
```

### 1.3 查 ID（可选 SQL）

```bash
docker exec -it tit-mysql-t01 mysql -utit -ptit_local timeimprint_task_local -e "
SELECT definition_id, instance_id, scenario_state, lifecycle_category FROM tt_task_instance ORDER BY instance_id DESC LIMIT 5;
SELECT signal_id, definition_id, process_status, provider_key FROM tt_task_signal ORDER BY signal_id DESC LIMIT 5;
SELECT inbox_id, definition_id, read_at FROM tt_inbox ORDER BY inbox_id DESC LIMIT 5;"
```

注意：若本库同时跑过 `-Pmysql-it`，可能残留 `webhook_action` 或空 payload Action，本地 Worker 会刷 WARN。手工联调脏数据可取消：

```sql
UPDATE tt_action_job
SET status='CANCELLED', outcome_code='MANUAL_CLEAN', outcome_summary='local dirty queue from IT',
    lease_owner=NULL, lease_until=NULL, execution_token=NULL, completed_at=UTC_TIMESTAMP()
WHERE status IN ('READY','RETRY_WAIT','RUNNING')
  AND (handler_key='webhook_action'
       OR (handler_key='in_app_notification'
           AND (payload_json IS NULL OR payload_json='{}'
                OR JSON_EXTRACT(payload_json,'$.notificationId') IS NULL)));
```

---

## 2. 端点与 Controller 一览

| 契约编号 | 方法与路径 | Controller | 说明 |
| --- | --- | --- | --- |
| E01 | `GET /api/v1/task-scenarios` | `MxzTaskDefinitionController` | 场景列表 |
| E02 | `POST /api/v1/task-definitions/preview` | 同上 | 预览发生，不写业务 |
| E03 | `POST /api/v1/task-definitions` | 同上 | 创建定义 |
| E04 | `GET /api/v1/task-definitions/{definitionId}` | 同上 | 查定义 |
| E05 | `GET /api/v1/task-definitions` | 同上 | 定义列表 |
| E06 | `POST /api/v1/task-definitions/{definitionId}/commands/{commandKey}` | 同上 | `update` / `pause` / `resume` / `retire` |
| E07 | `GET /api/v1/task-instances/{instanceId}` | `MxzTaskInstanceController` | 查实例 |
| E08 | `GET /api/v1/task-instances` | 同上 | 实例列表 |
| E09 | `POST /api/v1/task-instances/{instanceId}/commands/{commandKey}` | 同上 | S02：`complete` / `skip` / `snooze` |
| E10 | `GET /api/v1/inbox` | `MxzInboxController` | 收件列表 |
| E11 | `GET /api/v1/inbox/{inboxId}` | 同上 | 单条收件 |
| E12 | `GET /api/v1/inbox-unread-count` | 同上 | 未读数 |
| E13 | `POST /api/v1/inbox/{inboxId}/commands/mark-read` | 同上 | 标记已读 |
| I01 | `POST /internal/v1/task-signals/{providerKey}` | `MxzInternalSignalController` | 投递 Signal |
| （辅助） | `POST /internal/v1/task-signals/{signalId}/process` | 同上 | **非 04 正式编号**；手动推进 |
| I02 | `GET /internal/v1/task-signals/{signalId}` | `MxzInternalDiagnosticController` | Signal 诊断 |
| I03 | `GET /internal/v1/action-jobs` | 同上 | Action 列表 |
| I04 | `GET /internal/v1/action-jobs/{actionJobId}` | 同上 | Action 详情 |
| I05 | `GET /internal/v1/task-transitions` | 同上 | 迁移链路 |
| I06 | `POST /internal/v1/task-signals/{signalId}/commands/redrive` | 同上 | Signal 重驱 |
| I07 | `POST /internal/v1/action-jobs/{actionJobId}/commands/redrive` | 同上 | Action 重驱 |

---

## 3. S01 提醒 · ONCE 主路径（推荐先跑通）

将 `localDate` / `localTime` 换成**稍后 1–2 分钟**的北京时间，便于观察 Worker；若要立刻触发，创建后直接 `process` Signal。
E03 要求 ONCE 严格晚于创建事务时间；已过去的本地时刻会 `INVALID_REQUEST`。

生成「约两分钟后」的本地日期时间（macOS）：

```bash
LOCAL_DATE=$(TZ=Asia/Shanghai date -v+2M +%Y-%m-%d)
LOCAL_TIME=$(TZ=Asia/Shanghai date -v+2M +%H:%M:%S)
echo "$LOCAL_DATE $LOCAL_TIME"
```

### 3.1 E02 预览

`after`：只返回 `occurrenceAt` **严格晚于**该 UTC 时刻的发生；不含 requestId，不写库。

```bash
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions/preview" -d "{
  \"scenarioKey\": \"reminder\",
  \"scenarioSchemaVersion\": 1,
  \"scenarioConfig\": {},
  \"after\": \"2026-09-01T00:00:00Z\",
  \"limit\": 10,
  \"triggerBindings\": [{
    \"bindingKey\": \"primary\",
    \"providerKey\": \"calendar\",
    \"schemaVersion\": 1,
    \"config\": {
      \"type\": \"ONCE\",
      \"localDate\": \"$LOCAL_DATE\",
      \"localTime\": \"$LOCAL_TIME\",
      \"zoneId\": \"Asia/Shanghai\"
    }
  }]
}" | jq .
```

期望：`code=OK`，`message` 含「预览」「occurrences」；`data.occurrences` 至少 1 条；S01 的 `dueAt` 为 `null`；库中无新定义。

### 3.2 E03 创建

```bash
REQ=$(uuid)
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions" -d "{
  \"requestId\": \"$REQ\",
  \"scenarioKey\": \"reminder\",
  \"scenarioSchemaVersion\": 1,
  \"title\": \"手动测试提醒\",
  \"description\": \"S01 ONCE\",
  \"scenarioConfig\": {},
  \"participants\": [
    {\"principalType\": \"USER\", \"principalId\": \"local-actor\", \"roleCode\": \"OWNER\"}
  ],
  \"triggerBindings\": [{
    \"bindingKey\": \"primary\",
    \"providerKey\": \"calendar\",
    \"schemaVersion\": 1,
    \"config\": {
      \"type\": \"ONCE\",
      \"localDate\": \"$LOCAL_DATE\",
      \"localTime\": \"$LOCAL_TIME\",
      \"zoneId\": \"Asia/Shanghai\"
    }
  }]
}" | jq .

# 记下 data.definitionId → DEF_ID
DEF_ID=...
```

同 `requestId` 再发一次应得幂等重放（同一 `definitionId`）。`message` 应说明「已创建或幂等重放」。

### 3.3 E04 / E05 / E07 / E08

```bash
curl -sS "$BASE/api/v1/task-definitions/$DEF_ID" | jq .                                    # E04
curl -sS "$BASE/api/v1/task-definitions?scenarioKey=reminder&limit=20" | jq .               # E05

# 从 SQL 或 E08 取 instance_id → INST_ID
curl -sS "$BASE/api/v1/task-instances?definitionId=$DEF_ID&limit=20" | jq .                 # E08
INST_ID=...
curl -sS "$BASE/api/v1/task-instances/$INST_ID" | jq .                                      # E07
# 期望 scenarioState=PLANNED, lifecycleCategory=WAITING；allowedCommands 通常为空（S01 无实例业务命令）
```

### 3.4 处理 Signal（Worker 或手动）

```bash
# SQL 取 signal_id → SIG_ID（process_status=READY）
SIG_ID=...
curl -sS "${HDR[@]}" -X POST "$BASE/internal/v1/task-signals/$SIG_ID/process" | jq .
curl -sS "$BASE/api/v1/task-instances/$INST_ID" | jq .
# 期望 scenarioState=TRIGGERED, lifecycleCategory=TERMINAL
```

对 S01 实例调用 `complete` 应返回 `COMMAND_NOT_SUPPORTED`（中文 message 提示查 `allowedCommands`）。`complete`/`skip` 属于 S02。

### 3.5 E10—E13 收件箱

（需 Action `in_app_notification` 成功后才有收件；`process` 后若仍为空，查 `tt_action_job`。）

```bash
curl -sS "$BASE/api/v1/inbox?unreadOnly=true&limit=20" | jq .                               # E10
curl -sS "$BASE/api/v1/inbox-unread-count" | jq .                                           # E12

INBOX_ID=...   # data.items[].inboxId
curl -sS "$BASE/api/v1/inbox/$INBOX_ID" | jq .                                              # E11

curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/inbox/$INBOX_ID/commands/mark-read" \
  -d "{\"requestId\":\"$(uuid)\"}" | jq .                                                   # E13
# 期望 readAt 非空；同 requestId 重放保留首次 readAt
```

### 3.6 E06 定义控制

建议用**尚未 process / 另建**的定义练习 pause，避免与观察触发抢状态。

```bash
REV=1   # 来自 E04 的 data.revision
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions/$DEF_ID/commands/pause" -d "{
  \"requestId\": \"$(uuid)\",
  \"expectedRevision\": $REV,
  \"commandSchemaVersion\": 1,
  \"payload\": {}
}" | jq .

# resume / retire 同路径，换 commandKey；每次成功后 revision 递增，下次 expectedRevision 必须更新
```

---

## 4. S02 周期待办 · ONCE → PENDING → complete/skip

### 4.1 预览与创建

S02 预览中同一 ONCE 的 `occurrenceAt` 可与 S01 相同，但 `dueAt` **等于** `occurrenceAt`（S01 为 `null`）。

```bash
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions/preview" -d "{
  \"scenarioKey\": \"recurring_todo\",
  \"scenarioSchemaVersion\": 1,
  \"scenarioConfig\": {
    \"chaseOffsetsMinutes\": [60, 240, 720],
    \"notificationExpireAfterMinutes\": 1440,
    \"maxSnoozeCount\": 3
  },
  \"after\": \"2026-09-01T00:00:00Z\",
  \"limit\": 5,
  \"triggerBindings\": [{
    \"bindingKey\": \"primary\",
    \"providerKey\": \"calendar\",
    \"schemaVersion\": 1,
    \"config\": {
      \"type\": \"ONCE\",
      \"localDate\": \"$LOCAL_DATE\",
      \"localTime\": \"$LOCAL_TIME\",
      \"zoneId\": \"Asia/Shanghai\"
    }
  }]
}" | jq .

REQ=$(uuid)
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions" -d "{
  \"requestId\": \"$REQ\",
  \"scenarioKey\": \"recurring_todo\",
  \"scenarioSchemaVersion\": 1,
  \"title\": \"提交周报\",
  \"description\": \"S02 手动测试\",
  \"scenarioConfig\": {
    \"chaseOffsetsMinutes\": [60, 240, 720],
    \"notificationExpireAfterMinutes\": 1440,
    \"maxSnoozeCount\": 3
  },
  \"participants\": [
    {\"principalType\": \"USER\", \"principalId\": \"local-actor\", \"roleCode\": \"OWNER\"}
  ],
  \"triggerBindings\": [{
    \"bindingKey\": \"primary\",
    \"providerKey\": \"calendar\",
    \"schemaVersion\": 1,
    \"config\": {
      \"type\": \"ONCE\",
      \"localDate\": \"$LOCAL_DATE\",
      \"localTime\": \"$LOCAL_TIME\",
      \"zoneId\": \"Asia/Shanghai\"
    }
  }]
}" | jq .
```

### 4.2 Signal → PENDING，再 E09

```bash
# 取 DEF_ID / INST_ID / SIG_ID 后 process
curl -sS "${HDR[@]}" -X POST "$BASE/internal/v1/task-signals/$SIG_ID/process" | jq .
curl -sS "$BASE/api/v1/task-instances/$INST_ID" | jq .
# 期望 scenarioState=PENDING, lifecycleCategory=ACTIVE；记下 revision → REV；allowedCommands 含 complete/skip/snooze

REV=...
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-instances/$INST_ID/commands/complete" -d "{
  \"requestId\": \"$(uuid)\",
  \"expectedRevision\": $REV,
  \"commandSchemaVersion\": 1,
  \"payload\": {}
}" | jq .
# message 应说明 complete 已执行及 scenarioState

# 或跳过（另建一条 PENDING 实例再测；reason 必填）
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-instances/$INST_ID/commands/skip" -d "{
  \"requestId\": \"$(uuid)\",
  \"expectedRevision\": $REV,
  \"commandSchemaVersion\": 1,
  \"payload\": {\"reason\": \"本期不需要\"}
}" | jq .
```

---

## 5. 按 Controller 速查

### 5.1 `MxzTaskDefinitionController` · E01—E06

见 §3.1—3.3、§3.6；列表：

```bash
curl -sS "$BASE/api/v1/task-scenarios" | jq .                                               # E01
curl -sS "$BASE/api/v1/task-definitions?scenarioKey=reminder&limit=20" | jq .               # E05
```

### 5.2 `MxzTaskInstanceController` · E07—E09

见 §3.3、§4.2。

### 5.3 `MxzInboxController` · E10—E13

见 §3.5。

### 5.4 `MxzInternalSignalController` · I01 + process

日历路径：创建定义时已写入计划 Signal，用辅助 `process`（§3.4）。
事件类可手动投（定义须有 OWNER/RECIPIENT）：

```bash
curl -sS "${HDR[@]}" -X POST "$BASE/internal/v1/task-signals/event" -d "{
  \"requestId\": \"$(uuid)\",
  \"signalKey\": \"manual-event-$(uuid)\",
  \"schemaVersion\": 1,
  \"occurredAt\": \"2026-09-12T16:00:00Z\",
  \"subject\": {\"definitionId\": $DEF_ID, \"instanceId\": $INST_ID},
  \"payload\": {}
}" | jq .
```

`providerKey=calendar` 的计划 Signal 另有约束（须带 binding/instance），不要用空 subject 冒充。

### 5.5 `MxzInternalDiagnosticController` · I02—I07

```bash
curl -sS "$BASE/internal/v1/task-signals/$SIG_ID" | jq .                                    # I02
curl -sS "$BASE/internal/v1/action-jobs?definitionId=$DEF_ID&limit=20" | jq .               # I03
ACTION_ID=...
curl -sS "$BASE/internal/v1/action-jobs/$ACTION_ID" | jq .                                  # I04
curl -sS "$BASE/internal/v1/task-transitions?definitionId=$DEF_ID&limit=20" | jq .          # I05

# I06 / I07 仅 DEAD 且满足控制代次等条件时可成功
curl -sS "${HDR[@]}" -X POST "$BASE/internal/v1/task-signals/$SIG_ID/commands/redrive" -d "{
  \"requestId\": \"$(uuid)\",
  \"expectedStatus\": \"DEAD\",
  \"reason\": \"manual redrive\"
}" | jq .
curl -sS "${HDR[@]}" -X POST "$BASE/internal/v1/action-jobs/$ACTION_ID/commands/redrive" -d "{
  \"requestId\": \"$(uuid)\",
  \"expectedStatus\": \"DEAD\",
  \"reason\": \"manual redrive\"
}" | jq .
```

---

## 6. 日历规则片段（E02/E03 的 `triggerBindings[].config`）

| 类型 | 示例 config |
| --- | --- |
| ONCE | `{"type":"ONCE","localDate":"2026-09-13","localTime":"10:00:00","zoneId":"Asia/Shanghai"}` |
| DAILY | `{"type":"DAILY","startDate":"2026-09-08","localTime":"09:00:00","zoneId":"Asia/Shanghai"}` |
| WEEKLY | `{"type":"WEEKLY","startDate":"2026-09-08","weekday":5,"localTime":"09:00:00","zoneId":"Asia/Shanghai"}` |
| MONTHLY | `{"type":"MONTHLY","startDate":"2026-09-01","dayOfMonth":31,"localTime":"09:00:00","zoneId":"Asia/Shanghai"}` |
| EVERY_N_DAYS | `{"type":"EVERY_N_DAYS","startDate":"2026-09-01","intervalDays":3,"localTime":"09:00:00","zoneId":"Asia/Shanghai"}` |

字段名以 [CAP01](../../capabilities/CAP01-calendar.md) 与 `MxzCalendarConfigParser` 为准：`ONCE` 用 `localDate`；循环规则用 `startDate`；`WEEKLY` 的 `weekday` 为 1=周一…7=周日。非法组合应返回 `INVALID_REQUEST` 且不落库。

同一套 calendar ONCE：S01/S02 的 `occurrenceAt` 可相同；差别在 `dueAt`（S01=`null`，S02=发生时刻）与后续实例命令。

---

## 7. 公共 HTTP 边界烟测（可选）

对应 [07](../../07-ACCEPTANCE.md) §5 / DELIVERY 公共边界证据；抽测即可。

```bash
# 错误方法 / 未知路径
curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE "$BASE/api/v1/task-scenarios"
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/api/v1/no-such-path"

# 畸形 JSON
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions/preview" -d '{broken' | jq .

# >64KiB（应 REQUEST_TOO_LARGE / 中文 message）
python3 - <<'PY' | curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions/preview" --data-binary @- | jq .
print('{"scenarioKey":"reminder","pad":"' + ('x'*70000) + '"}')
PY
```

期望：仍为统一信封（含 `code`/`message`/`traceId`/`data`）；不泄露 SQL、堆栈、密码、连接串。

---

## 8. 建议检查清单

| 步骤 | 操作 | 通过标准 |
| --- | --- | --- |
| 1 | liveness / readiness | HTTP 200，readiness 含 db UP |
| 2 | E02 S01/S02 | OK；中文 message；有 occurrences；无写库；S01 `dueAt=null`，S02 `dueAt=occurrenceAt` |
| 3 | E03 + 同 requestId 重放 | 同 definitionId |
| 4 | E04 / E07 | 状态符合场景；可读 `allowedCommands` |
| 5 | process Signal | S01→TRIGGERED；S02→PENDING |
| 6 | E10—E13 | 未读→已读，重放稳定 |
| 7 | E09 complete/skip（仅 S02） | 终态 + revision 变化；中文 message 说明命令结果 |
| 8 | E06 pause/resume | controlState 变化 |
| 9 | E01/E05/E08、I02—I05 | 200 + `code=OK`（已挂载，不是 404） |
| 10 | 边界烟测（§7） | 错误方法/未知路径/畸形 JSON/>64KiB 有正确 code 与中文 message |

---

## 9. 常见失败

| 现象 | 常见原因 |
| --- | --- |
| 启动 Flyway 连库失败 | 未设 `DB_PASSWORD` 或端口不是 13306 |
| `INVALID_REQUEST` / requestId | 不是标准 UUID |
| E03 ONCE 被拒 | 本地发生时刻未严格晚于创建事务时间 |
| `REVISION_CONFLICT` | `expectedRevision` 过期 |
| `COMMAND_NOT_SUPPORTED` | 场景未声明该命令（例如对 reminder 调 complete） |
| `no recipients` / Signal 一直 READY | 创建时未加 OWNER/RECIPIENT（测试毒数据） |
| 收件为空 | Signal 未处理，或 Action 未 SUCCEEDED；查 `tt_task_signal` / `tt_action_job` |
| Worker 刷 `webhook_action` / `PAYLOAD_PARSE_ERROR` | mysql-it 脏数据；见 §1.3 清理 |

正式字段与错误码全集见 [04-API.md](../../04-API.md) 第 7 节。
