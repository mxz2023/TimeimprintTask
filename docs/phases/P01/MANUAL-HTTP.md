# P01 · 手动 HTTP 测试手册

> 本文是本地手工联调手册，**不替代** [04 API 契约](../../04-API.md)。
> 目标读者：服务已能启动，但不清楚「每条 curl 在验证系统哪块能力」。
> 当前阶段：P01 / RELEASED（标签 `v20260915-P01`）。[E01](../../04-API.md)—[E13](../../04-API.md)、[I01](../../04-API.md)—[I07](../../04-API.md) 均已挂载。

**建议阅读方式：** 先读 §0（整条故事），再按 §3 动手跑 S01；跑通后再做 §4 S02。字段对错以 04 为准，本文讲「为什么」。

可选图形化联调：仓库内 [TaskWebsite](../../TaskWebsite/README.md)（Vue 本地控制台，代理到本后端；非正式 P01 GUI 交付）。

---

## 0. 你在测什么（结合系统能力）

P01 本地版可以理解成一台「按日历办事的任务引擎」：

| 能力 | 人话 | 本手册怎么摸到 |
| --- | --- | --- |
| 场景扩展（SPI） | 系统里装了哪些「玩法」 | E01 列目录；**不读业务表** |
| 日历（[CAP01](../../capabilities/CAP01-calendar.md)） | 算「什么时候发生」 | E02 预览；E03 真正按规则落计划 |
| 任务定义 / 实例 | 「一条规则」vs「某一次发生」 | E03 建定义 → 自动生成实例 |
| Signal / Worker | 「到点了」的事件与后台处理 | 创建后有 READY Signal；process 或等 Worker |
| 场景状态机 | 提醒到点即结束；待办到点要人结清 | S01→TRIGGERED；S02→PENDING→complete/skip |
| 站内信（[CAP03](../../capabilities/CAP03-notification.md)） | 通知落到收件箱 | process 后 E10—E13 |
| 定义控制 | 暂停/恢复/退役整条规则 | E06 |
| 幂等与乐观锁 | 重复提交不乱写；并发用 revision | E03 同 requestId；E06/E09 带 expectedRevision |
| HTTP 边界 | 坏请求也有统一信封、不泄密 | §7 |

### 0.1 两条主故事（对应 §3 / §4）

**S01 提醒（[reminder](../../scenarios/S01-reminder.md)）** — 「闹钟响了就算完事」：

```text
看有没有 reminder 玩法 (E01)
  → 先试算闹钟时间对不对 (E02，不写库)
  → 真的创建一个提醒 (E03)  → 得到定义 DEF_ID、实例 INST_ID、计划 Signal SIG_ID
  → 到点处理 Signal (process / Worker)  → 实例变成 TRIGGERED（终态）
  → 站内信进收件箱 (E10)  → 标记已读 (E13)
```

**S02 周期待办（[recurring_todo](../../scenarios/S02-recurring-todo.md)）** — 「到期了还要你点完成/跳过」：

```text
同样预览/创建，但 scenarioKey=recurring_todo
  → process 后实例是 PENDING（还活着）
  → 用户 complete 或 skip (E09)  → 才到终态
```

不要对 S01 调 `complete`：场景未声明该命令，会 `COMMAND_NOT_SUPPORTED`。

### 0.2 每条请求怎么读结果

| 看什么 | 含义 |
| --- | --- |
| `code` | 英文机器码；成功固定 `OK` |
| `message` | **中文**说明「刚做了什么 / 为什么失败」（给人与 AI 编排） |
| `data` | 业务载荷；失败时多为 `null` |
| HTTP 状态 | 与 `code` 配套（如 409 冲突类） |

---

## 1. 前置条件与启动

| 项目 | 建议值 | 人话 |
| --- | --- | --- |
| 监听 | `127.0.0.1:18080` | 只在本机提供 HTTP，避免误暴露局域网 |
| 数据库 | `127.0.0.1:13306` / `timeimprint_task_local` | 业务事实都在这套 MySQL 里 |
| 身份 | `local-tenant` / `local-actor` | 本地假装成固定用户；**请求里不能改身份** |
| `requestId` | 标准小写 UUID | 写操作的幂等键；同键同摘要 → 重放首次结果 |

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
./mvnw -pl timeimprint-task-boot-loader -am -DskipTests spring-boot:run
```

| 变量 | 在做什么 |
| --- | --- |
| `JAVA_HOME` | 指定 JDK 21，给 Maven/进程用 |
| `SERVER_ADDRESS` / `SERVER_PORT` | HTTP 听哪里 |
| `DB_*` | 连哪套库、用什么账号 |
| `INSTANCE_ID` | 本进程名字（租约/诊断用）；多进程时每个不同 |
| `LOCAL_TENANT_ID` / `LOCAL_ACTOR_ID` | 本地固定租户与操作者 |
| `WORKER_ENABLED=true` | 打开后台领取 Signal/Action；`false` 则只当纯 API，到点不会自动推进 |

说明：必须加 `-am`，否则可能缺兄弟模块制品。

### 1.2 另开终端：公共变量与健康检查

**在做什么：** 确认进程活着，且能连上库（readiness），再开始业务 curl。
**为什么先做：** 后面全失败时，先排除「没起来 / 库挂了」。

```bash
BASE=http://127.0.0.1:18080
HDR=(-H 'Content-Type: application/json' -H 'Accept: application/json')
uuid() { uuidgen | tr '[:upper:]' '[:lower:]'; }

curl -sS "$BASE/actuator/health/liveness"    # 进程还在吗
curl -sS "$BASE/actuator/health/readiness"   # 可以接流量吗（含 db）
```

### 1.3 查 ID（可选 SQL）

路径里的 ID 多数来自上一步 HTTP 的 `data`；SQL 用于补查。均为库表自增主键，JSON 里常以**字符串**出现。

| 变量 | 库表.列 | 是什么 | 怎么产生 | 常用在哪 |
| --- | --- | --- | --- | --- |
| `DEF_ID` | `tt_task_definition.definition_id` | 任务**定义**（一条规则） | E03 → `data.definitionId` | E04/E06、过滤列表 |
| `INST_ID` | `tt_task_instance.instance_id` | 某次**发生**的实例 | E03 按 occurrence 物化 | E07/E09 |
| `SIG_ID` | `tt_task_signal.signal_id` | 「到点了」事件 | E03 写入计划 Signal（多为 READY） | `process`、I02 |
| `INBOX_ID` | `tt_inbox.inbox_id` | 站内收件一行 | IN_APP Action 成功后 | E11/E13 |

```text
E03 创建
  → DEF_ID
  → 物化实例 INST_ID
  → 计划 Signal SIG_ID（READY）
process / Worker
  → 改场景状态 + 产生 Action
  → IN_APP 成功 → INBOX_ID
```

**按定义取 SIG_ID（务必先有非空 DEF_ID）：**

```bash
echo "DEF_ID=[$DEF_ID]"   # 若为空，查询会 SQL 语法错误
SIG_ID=$(docker exec tit-mysql-t01 mysql -utit -ptit_local timeimprint_task_local -N -e \
  "SELECT signal_id FROM tt_task_signal
   WHERE definition_id=${DEF_ID} AND process_status='READY'
   ORDER BY signal_id DESC LIMIT 1;")
echo "SIG_ID=$SIG_ID"
```

对照最近几行：

```bash
docker exec tit-mysql-t01 mysql -utit -ptit_local timeimprint_task_local -e "
SELECT definition_id AS DEF_ID, instance_id AS INST_ID, scenario_state, lifecycle_category
FROM tt_task_instance ORDER BY instance_id DESC LIMIT 5;
SELECT signal_id AS SIG_ID, definition_id AS DEF_ID, process_status, provider_key
FROM tt_task_signal ORDER BY signal_id DESC LIMIT 5;
SELECT inbox_id AS INBOX_ID, definition_id AS DEF_ID, read_at
FROM tt_inbox ORDER BY inbox_id DESC LIMIT 5;"
```

优先：E03→`DEF_ID`；E08→`INST_ID`；E10→`INBOX_ID`。`SIG_ID` 无公开列表接口，用上式 SQL。

若本库跑过 `-Pmysql-it`，可能残留 `webhook_action` / 空 payload Action，Worker 会刷 WARN。可取消脏数据：

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

### 1.4 重置本地业务数据（联调清库）

**在做什么：** 清空 `timeimprint_task_local` 里手工/IT 留下的业务行，方便从空库重跑 §3 / §4 或 TaskWebsite。
**为什么：** 定义、Signal、Action、收件箱互相引用；只删一部分容易留下孤儿行或 Worker 继续啃脏任务。
**注意：**

- **仅本地联调库**；不要对共享/生产库执行。
- **保留** `flyway_schema_history`（表结构仍靠已执行迁移；禁止对本地联调依赖 `flyway clean`）。
- 建议先停 Task 进程（或暂时 `WORKER_ENABLED=false`），清完再启动，避免截断过程中 Worker 仍领取旧行。
- 清库后终端里的 `DEF_ID` / `INST_ID` / `SIG_ID` / `INBOX_ID` 全部作废，须重新创建。

**一键清空业务表（推荐）：**

```bash
docker exec tit-mysql-t01 mysql -utit -ptit_local timeimprint_task_local -e "
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE tt_inbox;
TRUNCATE TABLE tt_notification;
TRUNCATE TABLE tt_action_attempt;
TRUNCATE TABLE tt_action_job;
TRUNCATE TABLE tt_task_transition;
TRUNCATE TABLE tt_task_signal;
TRUNCATE TABLE tt_task_participant;
TRUNCATE TABLE tt_task_instance;
TRUNCATE TABLE tt_trigger_binding;
TRUNCATE TABLE tt_command_dedup;
TRUNCATE TABLE tt_audit_log;
TRUNCATE TABLE tt_task_definition;
SET FOREIGN_KEY_CHECKS=1;
SHOW TABLES LIKE 'tt_%';
SELECT
  (SELECT COUNT(*) FROM tt_task_definition) AS definitions,
  (SELECT COUNT(*) FROM tt_task_instance) AS instances,
  (SELECT COUNT(*) FROM tt_task_signal) AS signals,
  (SELECT COUNT(*) FROM tt_action_job) AS actions,
  (SELECT COUNT(*) FROM tt_inbox) AS inbox;
"
```

期望：各计数均为 `0`；`flyway_schema_history` 仍在（本命令未碰它）。若只想压住脏 Action 而不清全库，用上一节的 `UPDATE … MANUAL_CLEAN`。

---

## 2. 端点一览（人话）

| 编号 | 方法与路径 | 人话：在做什么 |
| --- | --- | --- |
| E01 | `GET /api/v1/task-scenarios` | 看系统装了哪些玩法（代码扩展，不是表数据） |
| E02 | `POST .../task-definitions/preview` | 试算会发生哪些时刻，**不建任务** |
| E03 | `POST /api/v1/task-definitions` | 真正创建一条任务规则（定义） |
| E04 | `GET .../task-definitions/{id}` | 看这条规则现在什么样 |
| E05 | `GET /api/v1/task-definitions` | 列出我的规则 |
| E06 | `POST .../commands/{pause\|resume\|…}` | 暂停/恢复/退役/改内容 |
| E07 | `GET .../task-instances/{id}` | 看「某一次发生」的状态 |
| E08 | `GET /api/v1/task-instances` | 列出实例 |
| E09 | `POST .../commands/{complete\|skip\|…}` | **待办**上点完成/跳过/稍后（S02） |
| E10—E13 | `/api/v1/inbox…` | 收件列表/详情/未读数/标已读 |
| I01 | `POST /internal/v1/task-signals/{provider}` | 内部投一条 Signal（联调/集成） |
| 辅助 | `POST .../task-signals/{id}/process` | **立刻**处理一条 Signal（不等 Worker） |
| I02—I07 | 内部诊断/重驱 | 排障：Signal/Action/迁移链路；DEAD 重驱 |

---

## 3. S01 提醒主路径（推荐先完整跑通）

**业务目标：** 验证「创建一次未来提醒 → 到点触发 → 站内信可看可已读」。
对应能力：日历 + reminder 场景 + IN_APP 通知。

将发生时刻设为**稍后 1–2 分钟**（便于看 Worker），或创建后马上 `process` 立刻看结果。
E03 要求 ONCE **严格晚于**创建当下；过去的时刻会 `INVALID_REQUEST`。

```bash
LOCAL_DATE=$(TZ=Asia/Shanghai date -v+2M +%Y-%m-%d)
LOCAL_TIME=$(TZ=Asia/Shanghai date -v+2M +%H:%M:%S)
echo "$LOCAL_DATE $LOCAL_TIME"
```

### 3.0 E01 场景列表（可选但建议先做）

**在做什么：** 问系统「现在支持哪些 scenarioKey」。
**为什么：** 确认 `reminder` / `recurring_todo` 已装配；避免创建时写错键。
**不是：** 用户创建的任务列表（那是 E05）。

```bash
curl -sS "$BASE/api/v1/task-scenarios" | jq .
```

期望：`code=OK`；`data.items` 含 `scenarioKey=reminder`（及 recurring_todo）。

### 3.1 E02 预览

**在做什么：** 用与创建相同的日历配置，**只算**会发生哪些 UTC 时刻，**不写库**。
**为什么：** 先确认「上海 18:30 = UTC 10:30」之类换算、ONCE/循环规则理解对不对，再真创建。
**关键参数：** `after` = 只返回严格晚于该 UTC 时间的发生；`limit` = 最多几条。

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

期望：`occurrences` ≥ 1；S01 的 `dueAt` 为 `null`（提醒无「待办截止」）；库中**无**新 `tt_task_definition`。

### 3.2 E03 创建定义

**在做什么：** 落一条真实提醒规则：写定义、物化实例、写入计划 Signal。
**为什么：** 这是业务真正开始的地方；后面所有 ID 从这里长出来。
**`requestId`：** 防重复提交；同一 UUID + 相同内容再 POST → 同一 `definitionId`（幂等）。

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

DEF_ID=$(...)   # 填 data.definitionId，勿留空
echo "DEF_ID=$DEF_ID"
```

建议立刻用**同一个** `$REQ` 再 POST 一次，确认仍是同一 `definitionId`。

### 3.3 E04 / E05 / E07 / E08 — 读模型

**在做什么：**

| 请求 | 在做什么 | 为什么 |
| --- | --- | --- |
| E04 | 读定义详情 | 看 controlState、revision、标题是否落库 |
| E05 | 定义列表 | 确认能按 scenarioKey 找到刚建的规则 |
| E08 | 实例列表 | 拿到 `INST_ID`（一次发生） |
| E07 | 实例详情 | 触发前应为 `PLANNED` + `WAITING`；看 `allowedCommands` |

```bash
curl -sS "$BASE/api/v1/task-definitions/$DEF_ID" | jq .
curl -sS "$BASE/api/v1/task-definitions?scenarioKey=reminder&limit=20" | jq .
curl -sS "$BASE/api/v1/task-instances?definitionId=$DEF_ID&limit=20" | jq .
INST_ID=...    # data.items[].instanceId
curl -sS "$BASE/api/v1/task-instances/$INST_ID" | jq .
```

期望（触发前）：`scenarioState=PLANNED`，`lifecycleCategory=WAITING`；S01 通常**没有** complete/skip。

### 3.4 处理 Signal — 让提醒「响起来」

**在做什么：** 把日历计划 Signal 推进一遍：场景扩展算迁移 → 统一提交 → 往往再跑 IN_APP Action。
**为什么：** 不 process（也不等 Worker）则实例一直停在 PLANNED，收件箱不会有信。
**辅助 `process`：** 联调「立刻看结果」；生产路径靠 Worker 到期领取。

```bash
SIG_ID=$(docker exec tit-mysql-t01 mysql -utit -ptit_local timeimprint_task_local -N -e \
  "SELECT signal_id FROM tt_task_signal
   WHERE definition_id=${DEF_ID} AND process_status='READY'
   ORDER BY signal_id DESC LIMIT 1;")
echo "SIG_ID=$SIG_ID"

curl -sS "${HDR[@]}" -X POST "$BASE/internal/v1/task-signals/$SIG_ID/process" | jq .
curl -sS "$BASE/api/v1/task-instances/$INST_ID" | jq .
```

期望：`scenarioState=TRIGGERED`，`lifecycleCategory=TERMINAL`（提醒事实已成立，不再要人点完成）。

若对 S01 误调 E09 `complete`：应 `COMMAND_NOT_SUPPORTED`（中文 message 会提示查 `allowedCommands`）。

### 3.5 E10—E13 收件箱

**在做什么：** 验证通知能力：Action 成功后用户能看见信、标已读。
**为什么：** 「提醒响了」在产品上通常体现为收件；只改实例状态不算通知闭环。
**注意：** process 后若 inbox 仍空，查 `tt_action_job` 是否 SUCCEEDED（或等 Worker 跑完 Action）。

```bash
curl -sS "$BASE/api/v1/inbox?unreadOnly=true&limit=20" | jq .     # E10 未读列表
curl -sS "$BASE/api/v1/inbox-unread-count" | jq .                 # E12 未读数
INBOX_ID=...   # data.items[].inboxId
curl -sS "$BASE/api/v1/inbox/$INBOX_ID" | jq .                    # E11 单条
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/inbox/$INBOX_ID/commands/mark-read" \
  -d "{\"requestId\":\"$(uuid)\"}" | jq .                         # E13 已读
```

期望：`readAt` 非空；同一 `requestId` 重放不改变首次已读时间。

### 3.6 E06 定义控制（pause 等）

**在做什么：** 对「整条规则」做暂停/恢复/退役（不是对单次实例点完成）。
**为什么：** 验证控制代次与乐观锁：`expectedRevision` 必须等于当前 revision。
**建议：** 另建一条定义专门练 pause，避免和正在观察的触发抢状态。

```bash
REV=1   # 以 E04 的 data.revision 为准
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions/$DEF_ID/commands/pause" -d "{
  \"requestId\": \"$(uuid)\",
  \"expectedRevision\": $REV,
  \"commandSchemaVersion\": 1,
  \"payload\": {}
}" | jq .
```

成功后 revision +1；resume/retire 换 `commandKey`，并更新 `expectedRevision`。

---

## 4. S02 周期待办主路径

**业务目标：** 验证「到期进入待办 → 用户 complete/skip 才终态」。
与 S01 的关键差别：process 后是 **PENDING/ACTIVE**（还活着），不是 TRIGGERED。

### 4.1 预览与创建

**在做什么：** 同 §3，但 `scenarioKey=recurring_todo`，并带追催等配置。
**预览差异：** 同一 ONCE 的 `occurrenceAt` 可与 S01 相同，但 `dueAt` **等于** `occurrenceAt`。

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

记下新的 `DEF_ID` / `INST_ID`，按 §1.3 取 `SIG_ID`。

### 4.2 process → PENDING，再 E09

**process 在做什么：** 到期激活待办，挂上 INITIAL/CHASE 等通知意图。
**E09 在做什么：** 模拟用户「办完了」或「本期跳过」；必须带当前 `revision`。

```bash
curl -sS "${HDR[@]}" -X POST "$BASE/internal/v1/task-signals/$SIG_ID/process" | jq .
curl -sS "$BASE/api/v1/task-instances/$INST_ID" | jq .
# 期望 PENDING + ACTIVE；allowedCommands 含 complete / skip / snooze；记下 revision → REV

REV=...
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-instances/$INST_ID/commands/complete" -d "{
  \"requestId\": \"$(uuid)\",
  \"expectedRevision\": $REV,
  \"commandSchemaVersion\": 1,
  \"payload\": {}
}" | jq .

# 或 skip（另建 PENDING 再测；payload.reason 必填）
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-instances/$INST_ID/commands/skip" -d "{
  \"requestId\": \"$(uuid)\",
  \"expectedRevision\": $REV,
  \"commandSchemaVersion\": 1,
  \"payload\": {\"reason\": \"本期不需要\"}
}" | jq .
```

期望：complete → `COMPLETED`；skip → `SKIPPED`；均为终态；`message` 说明命令结果。

---

## 5. 按 Controller 补充说明

### 5.1 定义侧 E01—E06

主路径见 §3。列表速查：

```bash
curl -sS "$BASE/api/v1/task-scenarios" | jq .
curl -sS "$BASE/api/v1/task-definitions?scenarioKey=reminder&limit=20" | jq .
```

### 5.2 实例侧 E07—E09

读状态用 E07/E08；**只有 S02 PENDING** 才用 E09 结清。

### 5.3 收件侧 E10—E13

主路径见 §3.5。身份固定为 local-actor 的收件范围。

### 5.4 内部 Signal：I01 与 process

| 接口 | 在做什么 | 何时用 |
| --- | --- | --- |
| 辅助 `process` | 立刻处理已有 Signal | 日历主路径联调（§3.4） |
| I01 `…/task-signals/event` | 手动塞一条事件类 Signal | 测非日历触发；定义须有参与人 |

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

日历计划 Signal 有 binding/instance 约束，不要用空 subject 冒充 calendar。

### 5.5 内部诊断 I02—I07

**在做什么：** 排障与验收，不是终端用户功能。
**为什么：** 公开 API 不暴露租约/payload 细节；出问题看 Signal 是否 SUCCEEDED、Action 是否 DEAD、迁移链是否写上。

```bash
curl -sS "$BASE/internal/v1/task-signals/$SIG_ID" | jq .                          # I02
curl -sS "$BASE/internal/v1/action-jobs?definitionId=$DEF_ID&limit=20" | jq .     # I03
ACTION_ID=...
curl -sS "$BASE/internal/v1/action-jobs/$ACTION_ID" | jq .                        # I04
curl -sS "$BASE/internal/v1/task-transitions?definitionId=$DEF_ID&limit=20" | jq . # I05
```

I06/I07 重驱仅在资源已是 `DEAD` 且控制代次满足时有意义；正常主路径不必做。

---

## 6. 日历规则片段（E02/E03）

| 类型 | 示例 config | 人话 |
| --- | --- | --- |
| ONCE | `localDate` + `localTime` | 只响一次 |
| DAILY | `startDate` + `localTime` | 每天 |
| WEEKLY | + `weekday` 1=周一…7=周日 | 每周某天 |
| MONTHLY | + `dayOfMonth` | 每月某日（无则日取月末） |
| EVERY_N_DAYS | + `intervalDays` | 每 N 天 |

详见 [CAP01](../../capabilities/CAP01-calendar.md)。非法组合 → `INVALID_REQUEST`，不落库。

---

## 7. 公共 HTTP 边界烟测（可选）

**在做什么：** 故意发坏请求，确认仍走统一信封、不泄露内部信息。
**为什么：** 联调/AI 客户端依赖稳定错误形态；与业务主路径互补。

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE "$BASE/api/v1/task-scenarios"
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/api/v1/no-such-path"
curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions/preview" -d '{broken' | jq .
python3 - <<'PY' | curl -sS "${HDR[@]}" -X POST "$BASE/api/v1/task-definitions/preview" --data-binary @- | jq .
print('{"scenarioKey":"reminder","pad":"' + ('x'*70000) + '"}')
PY
```

---

## 8. 建议检查清单

| 步骤 | 你在验证什么 | 通过标准 |
| --- | --- | --- |
| 1 | 进程与库就绪 | liveness/readiness 正常 |
| 2 | 日历试算 | E02 OK；S01 `dueAt=null`；S02 `dueAt=occurrenceAt`；不写库 |
| 3 | 创建与幂等 | E03 有 DEF_ID；同 requestId 重放同一 ID |
| 4 | 读模型 | E04/E07 状态正确；可读 allowedCommands |
| 5 | Signal 推进 | S01→TRIGGERED；S02→PENDING |
| 6 | 站内信 | E10—E13 未读→已读 |
| 7 | 待办结清 | 仅 S02 E09；终态 + revision |
| 8 | 定义控制 | E06 pause/resume 改变 controlState |
| 9 | 目录与诊断 | E01/E05/E08、I02—I05 为 200/`OK` |
| 10 | 边界 | 坏请求有正确 code + 中文 message |

---

## 9. 常见失败

| 现象 | 常见原因 |
| --- | --- |
| Flyway/连库失败 | 未设 `DB_PASSWORD` 或端口不是 13306 |
| requestId 非法 | 不是标准 UUID |
| E03 ONCE 被拒 | 发生时刻未严格晚于「现在」 |
| SQL 1064 在 `AND process_status` | `DEF_ID` 为空，展开成 `definition_id=` |
| `REVISION_CONFLICT` | `expectedRevision` 过期，先 E04/E07 刷新 |
| `COMMAND_NOT_SUPPORTED` | 场景无此命令（如 reminder + complete） |
| Signal 一直 READY | 未 process 且 Worker 未领到；或参与人缺失 |
| 收件为空 | Action 未成功；查 `tt_action_job` |
| Worker 刷 webhook / PAYLOAD_PARSE | mysql-it 脏数据；见 §1.3 取消或 §1.4 清库 |
| 列表里全是旧联调垃圾 | 本地库未清；见 §1.4 重置业务表 |

正式字段与错误码见 [04-API.md](../../04-API.md) 第 7 节。
