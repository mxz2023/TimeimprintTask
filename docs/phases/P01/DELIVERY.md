# P01 · 交付证据

> 本文记录P01已经验证的实现证据。只有经过真实命令执行、退出码为零且测试通过的条目才能记录于此。
> 阶段身份与范围见 [README](README.md)，任务状态见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-09-15
阶段状态: RELEASED（2026-09-15 用户人工验收通过；Git 标签 `p01`；本文此后不再改写）

---

## 1. 环境

| 项目 | 值 |
| --- | --- |
| JDK | Amazon Corretto 21.0.12 (`openjdk version "21.0.12" 2026-07-21 LTS`) |
| Maven Wrapper | 3.3.2 |
| Spring Boot | 4.0.8 |
| Spring AI BOM | 2.0.1 |
| MyBatis Starter | 4.0.1 |
| MySQL 镜像 | `mysql:9.7.2` |
| MySQL 镜像 Digest | `sha256:29abb0a179982e4a8928138bfc7f918af9eda64e7eeb1b1d084c1720a20159e6` |
| MySQL SELECT VERSION() | `9.7.2` |
| MySQL @@version_comment | `MySQL Community Server - GPL` |
| MySQL 事务隔离级别 | `READ-COMMITTED` |
| MySQL 镜像架构 | arm64 |
| 完整环境证据 | [T01-ENV-EVIDENCE.txt](T01-ENV-EVIDENCE.txt) |

---

## 2. T01 · 13模块与环境基线

**状态: PASS**

```
命令: ./mvnw -q test
退出码: 0
命令: ./mvnw -q package
退出码: 0
```

验证内容:
- 13个平级Maven模块编译通过（`ModuleBaselineTest.approvedModulesExist` PASS）
- ArchUnit规则验证kernel无Spring/MyBatis/Web依赖（`ArchitectureRulesTest.kernelMustRemainPureJava` PASS）
- API契约测试通过（`ApiContractTest` PASS）
- 扩展SPI契约测试通过（`ExtensionSpiContractTest` PASS）
- Calendar算法纯函数测试通过（`CalendarOccurrenceCalculatorTest` PASS）
- TransitionPlan契约测试通过（`TransitionPlanContractTest` PASS）
- Flyway表集合测试通过（`FlywaySchemaInformationSchemaTest.contractListsTwelveTables` PASS）
- 模块基线测试通过（`ModuleBaselineTest.approvedModulesExist` PASS）

---

## 3. T02 · S01 ONCE最小纵向闭环

**状态: PASS**

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify
退出码: 0
测试: S01OnceMysqlIT#s01OnceVerticalLoop PASS
```

验证内容:
- MySQL 127.0.0.1:13306 数据库 timeimprint_task_local，用户 tit
- Flyway 迁移：10张平台公共表 + 2张通知能力表，共12张，零迁移错误
- E02 预览：reminder ONCE 返回1条occurrence，不写业务数据 ✓
- E03 创建定义：`requestId` 幂等锁 + calendar ONCE trigger ✓
- 创建后断言无Action（信号未处理）✓
- `tt_task_instance` 存在PLANNED/WAITING实例 ✓
- `tt_task_signal` 存在READY信号 ✓
- `gateway.processSignal(signalId)` 处理信号 → TRIGGERED/TERMINAL ✓
- E07 读回：`scenarioState=TRIGGERED, lifecycleCategory=TERMINAL` ✓
- `tt_action_job` 有1条SUCCEEDED站内信Action ✓
- `tt_inbox` 有1条收件记录 ✓
- E10 收件箱列表：unreadOnly=true 返回≥1条 ✓
- E12 未读计数：unreadCount≥1 ✓
- E11 查询单条收件 ✓
- E13 标记已读：readAt非null ✓

---

## 4. T07 + T08 · 测试夹具、内核完整性与S02纵向闭环

**状态: PASS**

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify
退出码: 0（全部7条IT测试 PASS，0 Failures，0 Errors）
  S01OnceMysqlIT#s01OnceVerticalLoop                  PASS
  S02BasicMysqlIT#s02RecurringTodoOncePlanToPendingToCompleted   PASS
  S02BasicMysqlIT#s02RecurringTodoDailyPlanToPendingToSkipped    PASS
  S02RecurringTodoMysqlIT#s02DailyBasicLoop            PASS
  T07FixtureMysqlIT#approvalFixtureRegistersAndMaterializes      PASS
  T07FixtureMysqlIT#eventTriggerFixtureIsRegistered    PASS
  T07FixtureMysqlIT#webhookActionFixtureIsRegistered   PASS
```

### 4.1 Kernel + Platform DDL 零变更断言

- SHA-256基线记录在 [BASELINE-SHA256.txt](BASELINE-SHA256.txt)（"Kernel production sources"和"Platform public Flyway DDL"节）
- 基线在T07夹具加入前生成（2026-09-12）
- 单元测试 `T07KernelIntegrityTest#kernelAndPlatformDdlMatchBaseline` 验证所有文件哈希与基线一致

### 4.2 三个测试夹具

| 夹具 | 实现类 | 接口 | 位置 |
| --- | --- | --- | --- |
| ApprovalFixture | `ApprovalFixture` + `ApprovalDataMaterializer` | `ScenarioExtension` + `ScenarioDataMaterializer` | boot-loader test sources |
| EventTriggerFixture | `EventTriggerFixture` | `TriggerProvider` | boot-loader test sources |
| WebhookActionFixture | `WebhookActionFixture` | `ActionHandler` | boot-loader test sources |

夹具均通过稳定扩展SPI装配，不修改kernel生产源码或平台公共DDL。

### 4.3 E09 实例级命令接口

- `InstanceCommandService` 实现 complete/skip/snooze 命令管道（dedup → 父级锁 → 子级锁 → 执行Handler → 提交 → 返回CommandResultView）
- `TaskInstanceController#executeCommand` 实现E09端点
- `RecurringTodoCompleteHandler` / `RecurringTodoSkipHandler` / `RecurringTodoSnoozeHandler` 已注册（`service-scenario-basic`）

### 4.4 S02 纵向闭环（S02BasicMysqlIT）

| 场景 | 流程 | 结果 |
| --- | --- | --- |
| ONCE | 预览→创建→PLANNED→processSignal→PENDING→E09 complete→COMPLETED | PASS |
| DAILY | 创建→PLANNED（取最早实例）→processSignal→PENDING→E09 skip→SKIPPED | PASS |

### 4.5 快照 / snooze / 五规则预览补齐

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=S01OnceMysqlIT,S02RecurringTodoMysqlIT,S02BasicMysqlIT,CalendarFiveRulesPreviewMysqlIT,T07FixtureMysqlIT,DualClaimMysqlIT
退出码: 0
Failsafe: Tests run: 9, Failures: 0
```

- Signal 推进 PENDING 时写入 `scenario_snapshot_json`（`REPLACE_INSTANCE_SNAPSHOT`）
- E09 snooze：旧 READY 批次 CANCELLED、新 `actionGeneration`、返回 `scenarioResult`；`s02SnoozeShiftsReadyActions` PASS
- CAL-01—CAL-05 E02 预览矩阵：`CalendarFiveRulesPreviewMysqlIT` PASS
- Signal 同步路径只执行 `availableAt <= now` 的 LOCAL_TRANSACTIONAL；未到期 Action 留给 ActionWorker（S01 IT 已覆盖）
- MANUAL-HTTP 日历字段与 `CalendarConfigParser` 对齐（`startDate` / `weekday`）

### 4.6 A05 / A36 状态机与并发冲突

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A05A36StateConcurrencyMysqlIT,S02RecurringTodoMysqlIT,S02BasicMysqlIT,S01OnceMysqlIT
退出码: 0
Failsafe: Tests run: 9, Failures: 0
```

- E09 校验 `expectedRevision`（不匹配 → `REVISION_CONFLICT`）
- A05：complete 同 requestId 重放 / 不同 requestId 同终态 NoChange；相反终态 `STATE_CONFLICT`；skip 原因必填与长度；并发 complete 仅一方变更
- A36：S02 PLANNED→PENDING→COMPLETED/SKIPPED；pause 将 WAITING 置 `TERMINAL/CANCELLED`（非 SKIPPED）且 `terminalAt` 有值；S01 PLANNED→TRIGGERED 且拒绝 complete

### 4.7 A02 / A23 创建幂等与暂停退役命令边界

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A02A23CreatePauseMysqlIT,S01OnceMysqlIT
退出码: 0
Failsafe: Tests run: 4, Failures: 0
```

- A02：同 requestId 重放返回同一 definitionId；同键不同摘要 `IDEMPOTENCY_CONFLICT`；并发同键收敛为一条定义且无 PROCESSING 残留
- A23：PAUSE/RETIRE 后 PENDING 仍可 complete/skip，snooze → `STATE_CONFLICT`；WAITING → `CANCELLED`（非 SKIPPED）

### 4.8 A01 / A37 日历边界与配置校验

```
命令: ./mvnw -q -pl timeimprint-task-service-capability-calendar test \
  -Dtest=CalendarOccurrenceCalculatorTest,CalendarConfigParserTest
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A01A37CalendarMysqlIT,CalendarFiveRulesPreviewMysqlIT,DefinitionUpdateMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 5, Failures: 0
```

- A01：after 严格排除、月末/闰年钳制、EVERY_N_DAYS 锚点不漂移、JVM 默认时区不影响结果（单测）
- A37：schemaVersion 1 字段白名单；缺字段/多余字段/非法 weekday·zone·localTime → `INVALID_REQUEST`；E02 预览与 E03 创建同 `occurrenceKey`
- A37 / A24：E06 `update` 六字段完整替换；`description=null` 清空；规范化无变化 → NoChange（revision/scheduleGeneration 不变）；仅日历配置变化 → `scheduleGeneration+1` 且旧 WAITING→CANCELLED 并重建窗口；标题变化不升 scheduleGeneration；ACTIVE 快照不被覆盖

### 4.9 A17 / A18 / A21 与 M01—M10 相对时间纵向切片

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A17A18A21MysqlIT,MMatrixMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 8, Failures: 0
```

- A17：同 Signal 重复处理后仅 1 条 inbox；mark-read 重放保留首次 `readAt`
- A18：`scenarioSchemaVersion!=1` → `UNSUPPORTED_SCHEMA_VERSION`；过去 ONCE / 未知日历字段 / reminder 非空 scenarioConfig → `INVALID_REQUEST` 且不落定义
- A21：S02 缺省展开为 60/240/720、1440、3，Signal 后 1 INITIAL+3 CHASE；非法偏移/有效期/snooze/未知字段拒绝且不落数据
- M01—M10（相对时间切片，非固定 2026-09-08 业务钟）：S01 五规则窗口间距与 ONCE 耗尽入箱；S02 默认催办时点、双 PENDING 不互阻、WEEKLY/MONTHLY31/EVERY_N_DAYS 创建与间距断言

### 4.10 A12 暂停跨周期再恢复

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A12ResumeMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 1, Failures: 0
```

- pause / resume 各递增 `controlGeneration`；resume 清空 `pausedAt`
- 暂停跨多个 DAILY 周期后再 resume：新 WAITING 仅含 resume 时刻之后的 occurrence，不补发暂停区间
- 暂停前已 PENDING 的实例保持 PENDING，Action 数量不被 resume 重建

### 4.11 A07 / A13 / A28 控制屏障与幂等冲突

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A07A13A28MysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 3, Failures: 0
```

- A07：pause 先提交则后续 Signal 为 `IGNORED` 且不迁移；Signal 先 Applied 后 pause 保留 PENDING、Transition 与 Action 行
- A13：同 `requestId` 不同 snooze 摘要 → `IDEMPOTENCY_CONFLICT`；同 revision 并发 complete 仅一条 `changed=true`
- A28：pause/resume 递增 `controlGeneration`；旧代次 READY 经 ActionWorker 屏障转 `CANCELLED/CONTROL_BARRIER`；新 WAITING 为新代次；已 SUCCEEDED 收件与 PENDING 保留

### 4.12 A08 pause 与 EXTERNAL Action

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A08ExternalPauseMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 2, Failures: 0
```

- EXTERNAL 路径：claim → 短事务写 `effectStartedAt`（重验 controlGeneration/PAUSED）→ 事务外调用 → 结果事务闭合；后续 pause 不撤销已开始调用
- pause 先：READY EXTERNAL 转 `CANCELLED/CONTROL_BARRIER`，无调用、无 `effectStartedAt`
- `effectStartedAt` 先：pause 后仍完成调用并 `SUCCEEDED`，Attempt 保留开始证据

### 4.13 A09 EXTERNAL 崩溃与租约回收

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A09ExternalCrashMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 3, Failures: 0
```

- 调用前崩溃（RUNNING 且无 `effectStartedAt`）：租约回收 → `RETRY_WAIT` / Attempt=`RETRYABLE_FAILURE`，可再次执行并成功；旧 token CAS 不能覆盖
- 调用中崩溃（已提交 `effectStartedAt`）：租约回收 → `UNKNOWN`，不自动重发、Worker 不再调用
- 结果已提交：保持 `SUCCEEDED`，不在过期 RUNNING 集合中

### 4.14 A10 LOCAL_TRANSACTIONAL CAS 失败整笔回滚

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A10LocalCasMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 1, Failures: 0
```

- LOCAL 路径：同事务 claim → 写 inbox → 闭合 Attempt → CAS 完结；CAS 失败抛错整笔回滚
- 强制 token 失配后：Action 仍为 READY、无 Attempt、无 inbox；再次执行后恰好 1 条 inbox

### 4.15 A15 死锁/锁等待整笔事务重试

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A15TxRetryMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 3, Failures: 0
```

- `TransactionBoundary` 对死锁/锁等待完整重开事务，最多 3 次；成功路径无部分写入
- 3 次耗尽 → `RETRY_LATER`，回滚后无残留 `tt_command_dedup` 行
- EXTERNAL 已提交 `effectStartedAt` 后，结果事务重试不增加 handler 调用次数

---

## 5. 双进程与性能验收

**状态: PASS**（专项证据见下列各节；2026-09-15 人工验收确认）

```
命令: ./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify
退出码: 0
Failsafe: Tests run: 5, Failures: 0
（TakeoverSlaDualProcessIT + FairnessBacklogDualProcessIT + DualClaimMysqlIT + A04A20DualProcessIT）
recordedAtUtc: 2026-09-13T08:39:59Z
```

已验证:
- 并发 SKIP LOCKED 领取互斥（同一 Signal 不被两个 claim 同时拿走）
- A04 / A20：真二 JVM 领取后 `destroyForcibly`，租约回收接管；旧 token CAS=0；Signal 仅一条 `source_type=SIGNAL` 迁移；Action 恰好 1 条 inbox
- A16 / A22：退避/Policy 退还/EXPIRED 与 OWNER·RECIPIENT·超限规则（见 §5.2）
- A03 / A11：并发规划唯一事实与改时间规则屏障（见 §5.3）
- A06 / A14 / A19 / A25–A27 / A29–A33 / A38 / A40–A42 等：见 §5.4

已验证（续）:
- A39：local/test 非回环地址启动失败；liveness 不依赖 DB；停机 admission 拒绝写（503 RETRY_LATER）并停止 claim；宽限后租约回收 → RETRY_WAIT，旧 token CAS=0（见 §5.6）
- 07 §6 独立接管真时钟 SLA：子进程领取后被杀，父进程调度在原租约到期后 `2 × scan(2000ms) + 10s` 内接管；恰好 1 条 inbox；旧 token CAS=0（见 §5.7）
- 07 §6 独立公平性积压：10×101 READY 积压；单轮 ≤100；新到期 S01 10s 内收件（见 §5.8）

发布时残留说明（不阻塞 RELEASED）:
- A01—A42 完整验收矩阵总表勾选（多项已有专项证据，人工验收确认收口）
- information_schema/EXPLAIN 等数据验收长尾单列复跑（以既有 Flyway/Arch 证据为准）

已验证（续·性能）:
- 07 §6 性能门槛：专用库双进程；1万 ACTIVE；预热+3 次正式；P95 迁移/inbox 均达标（见 §5.11）
- 07 §5 公共 HTTP 边界：见 §5.12

### 5.1 A04 / A20 双 JVM 崩溃接管

```
命令: ./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A04A20DualProcessIT -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 2, Failures: 0
抽样回归: S01OnceMysqlIT,A07A13A28MysqlIT,A09ExternalCrashMysqlIT,A15TxRetryMysqlIT → Tests run: 10, Failures: 0
```

- Signal：短事务领取提交 RUNNING+token；子进程 `ClaimAndHoldMain` 领取后被杀；`SignalLeaseReaper` → RETRY_WAIT；父进程 `processSignal` 接管；旧 token `completeWithToken` 为 false；一条 SIGNAL 迁移
- Action：同上路径经 `ActionLeaseReaper` + `ActionWorker`；最终 1 条 inbox；旧 token 无权回写
- 实现要点：`processSignal` 改为 claim 与处理两段事务；Signal 完成 CAS 要求 RUNNING+token；新增 `SignalLeaseReaper`

### 5.2 A16 / A22 Action 退避与接收人规则

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A16A22MysqlIT -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 2, Failures: 0
抽样回归: S01OnceMysqlIT,S02RecurringTodoMysqlIT,A07A13A28MysqlIT,A09ExternalCrashMysqlIT,A10LocalCasMysqlIT,A15TxRetryMysqlIT,A16A22MysqlIT → Tests run: 15, Failures: 0
recordedAtUtc: 2026-09-13T05:27:51Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01，端口 13306）
```

- A16：EXTERNAL 可重试失败首次退避约 5s；`attempt_count` 达 `max_attempts` → DEAD；副作用前 Policy DENY 退还计数并记 `POLICY_BLOCKED`；`expires_at` 到界 → EXPIRED
- A22：无 RECIPIENT 时通知 OWNER；显式 RECIPIENT 不隐式含 OWNER；>10 人 Signal `IGNORED` 且无 Action
- 实现要点：`RecipientRules`；S01/S02 Signal 发出 `PENDING_EXPAND` 模板由平台按接收人展开；`completeRetryableFailure` / `releasePolicyBlocked` / `expireIfDue`；claim 要求 `attempt_count < max_attempts`

### 5.3 A03 / A11 并发规划与改时屏障

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A03A11MysqlIT -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 2, Failures: 0
抽样回归: A03A11MysqlIT,S01OnceMysqlIT,S02RecurringTodoMysqlIT,DefinitionUpdateMysqlIT,A07A13A28MysqlIT,A12ResumeMysqlIT,A16A22MysqlIT → Tests run: 13, Failures: 0
recordedAtUtc: 2026-09-13T05:37:50Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01，端口 13306）
```

- A03：两线程并发 `planBinding` 同一 occurrence → 仅 1 instance / 1 signal、规划期无 Action；游标单调；Signal 迁移后唯一 Action
- A11：日历 update 与旧未来 Signal Worker 并发；PENDING 历史快照保留；旧 WAITING/READY 取消或 Signal-first 保留；`scheduleGeneration` +1 并重建新窗口；旧 IGNORED Signal 不能再次迁移
- 实现要点：Planner 每绑定 `REQUIRES_NEW` 短事务；锁序 definition → binding；修复自调用导致事务未生效

### 5.4 剩余 A 矩阵补齐（本轮串行）

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A06CompleteNotificationMysqlIT,A19SchemaMysqlIT,A26S01TerminalMysqlIT,A25A29ScaleMysqlIT,A32RedriveMysqlIT,A14A30LocalProfileMysqlIT,A14A30SecurityMysqlIT,A27InterleaveMysqlIT,A31A41CatchupMysqlIT,A38SchemaMysqlIT,A40SpiMysqlIT,A42DeliveryStateMysqlIT,S01OnceMysqlIT,A16A22MysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
抽样结果: Failsafe 新套件 + 回归相关用例 PASS（见各 IT；A31/A42 单独复核 PASS）
recordedAtUtc: 2026-09-13T05:50:22Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01，端口 13306）
```

已补专项证据:
- A06：complete 与站内通知并发；终态后未开始 Action 取消；成功收件保留
- A14 / A30：test profile 跨 tenant 读隔离 + debug 身份；local 固定 Actor；非 USER 拒绝
- A19 / A38：12 表 information_schema；CHECK/UNIQUE 拒绝非法行；租约字段诊断
- A25 / A29：参与人上限与 JSON 字节上限写前拒绝（TransitionPlan 上限见单测）
- A26：S01 终态 Transition 保留 INITIAL，先前 decoy READY → CANCELLED
- A27：Planner/Signal/pause 交错；锁序 definition→trigger→instance→Signal
- A31 / A41：Planner 追赶幂等；过期 Action → EXPIRED 且无陈旧 inbox（pause 半边仍见 A12）
- A32：I06/I07 DEAD 重驱链、幂等、EXTERNAL 拒绝、最多 3 次
- A33：Handler timeout 租约安全余量（ActionWorker）；actionKey 格式单测
- A40：SPI 注册键唯一；E01/未知命令映射
- A42：deliveryState 按 04 优先级（NOT_SCHEDULED/…/DELIVERED）

### 5.5 A34 / A35 分页弱一致与 live-schema 就绪

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A34PaginationMysqlIT,A35ExtensionSchemaMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 4, Failures: 0
全量复跑: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify
退出码: 0
Failsafe: Tests run: 75, Failures: 0
recordedAtUtc: 2026-09-13T06:12:00Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01，端口 13306）
```

- A34：E05/E08 不透明 base64url keyset cursor（冻结 `updatedAt`/`occurrenceAt`+id 边界）；并发 touch `updated_at` 单次遍历无重复；`asOf` 为响应时间；首页刷新可收敛
- A35：`liveSchema` HealthIndicator 纳入 readiness；未终结 Signal schema 无读取器 → readiness DOWN；终结后恢复 UP；历史 TERMINAL 公共快照仍可读；NoChange/Rejected 与技术异常分流
- 测试侧 `spring.task.scheduling.enabled=false`；IN_APP inbox 对唯一键冲突幂等成功；IT 按 `action_job_id` 定点执行，避免脏库 READY 队列饿死

仍 NOT_RUN / 未收口:
- 公共 HTTP 边界（07 §5）；E/I 端点矩阵见 §5.9–§5.10
- 07 §6 性能门槛：见 §5.11

### 5.6 A39 回环绑定、健康边界与优雅停机

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=A39ShutdownMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 3, Failures: 0
全量复跑: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify
退出码: 0
Failsafe: Tests run: 78, Failures: 0
recordedAtUtc: 2026-09-13T07:52:24Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01，端口 13306）
```

- A39：`LoopbackAddressEnvironmentPostProcessor` — local/test 非回环 `SERVER_ADDRESS`/`server.address` → 启动失败
- A39：liveness 不依赖 DB；readiness 在 migrate/registry/live-schema/DB 失败时拒绝流量（既有指示器 + REFUSING → `OUT_OF_SERVICE`）
- A39：`RuntimeAdmission` + `ShutdownAdmissionLifecycle` — 停机先 `beginShutdown` + readiness REFUSING；`ShutdownWriteRejectFilter` 拒绝公开写（503 `RETRY_LATER`）；Signal/Action/Planner 停止领取；租约回收至 `RETRY_WAIT`，无伪造批量 READY；旧 `execution_token` CAS=0

### 5.7 07 §6 独立接管真时钟 SLA

```
命令: ./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=TakeoverSlaDualProcessIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 1, Failures: 0
全量 dual-process-it: Tests run: 4, Failures: 0（连续两遍）
recordedAtUtc: 2026-09-13T08:20:45Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01，端口 13306）
```

- 子进程 `ClaimAndHoldMain` 以 `LEASE_SECONDS=10` 领取 Action 后 `destroyForcibly`；**不**人为改写 `lease_until`
- 父进程开启 `spring.task.scheduling.enabled=true`；effective scan interval = Worker/Reaper `fixedDelay` 2000ms；门槛 = 租约到期后 `2×2000ms + 10s`
- 父进程调度 reaper → RETRY_WAIT（含退避）→ ActionWorker 接管执行；断言 SUCCEEDED 落在 deadline 内、inbox=1、旧 token CAS=0
- 附带稳定：`DualClaimMysqlIT` 改为同事务 SELECT SKIP LOCKED + UPDATE RUNNING，并对齐 MySQL UTC

### 5.8 07 §6 独立公平性积压

```
命令: ./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=FairnessBacklogDualProcessIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 1, Failures: 0
全量 dual-process-it: Tests run: 5, Failures: 0
recordedAtUtc: 2026-09-13T08:39:59Z
```

- `CLAIM_BATCH_SIZE` 接入 Signal/Action Worker（钳制 1—100）；单轮 `pollOnceForTests` ≤100 且满批
- Action 领取时间片公平：半批 `available_at DESC`（新到期）+ 半批最旧 `next_attempt_at ASC`
- 构造 10 定义 × 101 READY 积压后，新到期 S01 在 10 秒内产生恰好 1 条 inbox（显式 poll，关闭 `@Scheduled` 避免跨测试上下文抢库）

仍 NOT_RUN / 未收口:
- 公共 HTTP 边界（07 §5）；E/I 端点矩阵见 §5.9–§5.10
- 07 §6 性能门槛：见 §5.11

### 5.9 I01—I07 独立 HTTP 契约矩阵

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=IMatrixMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 5, Failures: 0
recordedAtUtc: 2026-09-13T09:25:26Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01，端口 13306）
类: timeimprint-task-boot-loader/.../IMatrixMysqlIT.java
```

覆盖（对照 [07 §5](../../07-ACCEPTANCE.md) 端点维度；不适用项写明）:

| 端点 | 已覆盖 | 部分 / 未覆盖 |
| --- | --- | --- |
| [I01](../../04-API.md) POST `/internal/v1/task-signals/{providerKey}` | 成功受理；同 key 同摘要 `duplicated=true`；同 key 异摘要 `IDEMPOTENCY_CONFLICT`；非法 `requestId` → `INVALID_REQUEST`；未知 definition → `RESOURCE_NOT_FOUND`；`SignalAcceptedView` 字段集 | 畸形 `occurredAt` 拒绝但错误码未强制 `INVALID_REQUEST`（PARTIAL）；未知 JSON 字段未断言；身份覆盖 N/A（local 固定 Actor） |
| [I02](../../04-API.md) GET `/internal/v1/task-signals/{signalId}` | 成功诊断字段集；无 `executionToken`/`payload`/`payloadHash`；缺失 → `RESOURCE_NOT_FOUND` | 身份归属 N/A |
| [I03](../../04-API.md) GET `/internal/v1/action-jobs` | 按 definitionId 列表；`status`+`handlerKey=in_app_notification` 过滤；字段集 | cursor 翻页未单测 |
| [I04](../../04-API.md) GET `/internal/v1/action-jobs/{actionJobId}` | 详情字段集；`storedStatus`/`effectiveStatus`；`attempts`；无 token/payload | — |
| [I05](../../04-API.md) GET `/internal/v1/task-transitions` | definitionId / instanceId 列表；`TransitionDiagnosticView` 字段集 | cursor 翻页未单测 |
| [I06](../../04-API.md) POST `.../task-signals/{id}/commands/redrive` | 真 HTTP：DEAD→新行 `redriveNo=1`；同 `requestId` 幂等回放 | 最大次数仍见 A32 gateway |
| [I07](../../04-API.md) POST `.../action-jobs/{id}/commands/redrive` | 真 HTTP：LOCAL DEAD→新行；幂等；库中有 EXTERNAL 时 `STATE_CONFLICT` | 最大 3 次仍见 A32 |

说明: 此前 [A32](../../07-ACCEPTANCE.md) 以 gateway 直调覆盖 I06/I07 业务规则；本类补齐 **HTTP 信封与诊断读模型**。不得据此宣称 E01—E13 矩阵完成。

### 5.10 E01—E13 独立 HTTP 契约矩阵

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=EMatrixMysqlIT,A40SpiMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 10, Failures: 0
recordedAtUtc: 2026-09-13T09:34:24Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01，端口 13306）
类: timeimprint-task-boot-loader/.../EMatrixMysqlIT.java
```

覆盖（对照 [07 §5](../../07-ACCEPTANCE.md)；不适用项写明）:

| 端点 | 已覆盖 | 部分 / 未覆盖 |
| --- | --- | --- |
| [E01](../../04-API.md) GET `/api/v1/task-scenarios` | Page 信封；`ScenarioMetadataView` 字段集；含 reminder/recurring_todo；无 lease/token | cursor 翻页未单测；身份 N/A |
| [E02](../../04-API.md) POST `/api/v1/task-definitions/preview` | 成功字段集；S01 `dueAt=null`；不写 Signal；limit/scenarioKey 校验 → `INVALID_REQUEST` | 未知字段未断言 |
| [E03](../../04-API.md) POST `/api/v1/task-definitions` | 创建字段集；同 requestId 幂等；摘要冲突 `IDEMPOTENCY_CONFLICT`；非法 UUID | update 六字段完整替换见既有 DefinitionUpdate IT |
| [E04](../../04-API.md)/[E05](../../04-API.md) | 详情/列表字段集；scenarioKey+controlState 过滤；缺失 `RESOURCE_NOT_FOUND` | keyset 翻页见 A34 |
| [E06](../../04-API.md) pause/resume | `CommandResultView`；幂等；错误 revision → `REVISION_CONFLICT` | retire/update 见 A02/DefinitionUpdate |
| [E07](../../04-API.md)/[E08](../../04-API.md)/[E09](../../04-API.md) | 实例字段集+deliverySummary；列表过滤；complete 幂等；未知 command → `COMMAND_NOT_SUPPORTED` | skip/snooze 见 S02/A07 |
| [E10](../../04-API.md)—[E13](../../04-API.md) | inbox 列表/详情/未读数字段集；mark-read 幂等保留 readAt；校验失败 | scenarioKey 过滤未单测（Controller 当前无该参） |

顺带契约对齐（同提交）:
- E02 预览：S01 `dueAt` 改为 null（此前误等于 occurrenceAt）；gateway 空安全序列化
- E09 未声明 commandKey：由 `EXTENSION_NOT_FOUND` 改为 `COMMAND_NOT_SUPPORTED`（对齐 04；A40 同步）

说明: 公共 HTTP 边界（未知路径/方法/媒体类型/64KiB 等）见 §5.12 PASS。

### 5.11 07 §6 本地性能门槛（1万 / 1000 / P95）

```
命令: ./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=PerfGateDualProcessIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 2, Failures: 0（seed + warmup/formal；类内 631.2s）
recordedAtUtc: 2026-09-13T09:55:13Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01）
库: timeimprint_task_perf（专用空库，避免 local 脏积压干扰；Flyway 同版本迁移）
进程: 父 SpringBootTest + 子 PerfWorkerMain（双 JVM，CLAIM_BATCH_SIZE=100，scheduling=true）
类: timeimprint-task-boot-loader/.../PerfGateDualProcessIT.java
suiteId: 0e42c193
```

结果（最近秩 P50/P95/P99；三次正式均 PASS，未只保留最好一次）:

| 轮次 | runId | 收件 | 重复 | DEAD/EXPIRED | 积压 | P95 迁移 | P95 inbox | 清空耗时 | 判定 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 预热 | warmup-fe65e75b | 1000 | 0 | 0 | 0 | 2000ms | 2000ms | PT2M28S | PASS |
| 正式1 | formal-1-31cfc580 | 1000 | 0 | 0 | 0 | 3000ms | 3000ms | PT2M27S | PASS |
| 正式2 | formal-2-ea70345f | 1000 | 0 | 0 | 0 | 2000ms | 2000ms | PT2M28S | PASS |
| 正式3 | formal-3-4072c426 | 1000 | 0 | 0 | 0 | 3000ms | 3000ms | PT2M28S | PASS |

门槛核对:
- 预建 9000 远未来 ACTIVE + 每轮 1000 目标 = 1 万 ACTIVE 定义；目标在 60s 窗均匀到期
- occurrenceAt→SIGNAL Transition P95 ≤5s；occurrenceAt→inbox P95 ≤10s（正式三次均满足）
- 1000 目标各恰好 1 条 inbox；无 DEAD/EXPIRED；末条到期后 30s 内本 runId 无 READY/RUNNING/RETRY_WAIT
- 背景 seed：9000 ok，took PT16.6S

说明: 清空耗时含窗口前 90s 创建余量 + 60s 到期窗；drain 断言以 backlog=0 与 deadline 为准。公共 HTTP 边界见 §5.12。

### 5.12 07 §5 公共 HTTP 边界

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify \
  -Dit.test=HttpBoundaryMysqlIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
退出码: 0
Failsafe: Tests run: 1, Failures: 0
抽样回归: EMatrixMysqlIT,A40SpiMysqlIT,A14A30LocalProfileMysqlIT → PASS（同会话先前跑次）
recordedAtUtc: 2026-09-13T10:00:22Z
JDK: Amazon Corretto 21.0.12；MySQL 9.7.2（容器 tit-mysql-t01，端口 13306）
类: timeimprint-task-boot-loader/.../HttpBoundaryMysqlIT.java
```

覆盖:
- 未知路径 → 404 `RESOURCE_NOT_FOUND`；错误方法 → 400 `INVALID_REQUEST`
- 不支持媒体类型 → 415 `UNSUPPORTED_MEDIA_TYPE`；空体/畸形 JSON/重复键/未知字段 → 400 `INVALID_REQUEST`
- 请求体 >64KiB → 413 `REQUEST_TOO_LARGE`（`RequestBodySizeFilter` + Tomcat max post）
- 未捕获异常（IT 探针）→ 500 `INTERNAL_ERROR`，消息/体无 SQL/堆栈/凭据
- 统一信封字段集 + `traceId`；错误 `data=null`；不泄露 executionToken/lease*
- Actuator 仅 health（`/actuator/env`、`/beans` 不可达）；liveness 可达
- local 忽略 `X-Debug-Actor-Id`（E01 仍 OK）

实现侧: 扩展 `ApiExceptionHandler`；ObjectMapper `FAIL_ON_UNKNOWN_PROPERTIES` + `STRICT_DUPLICATE_DETECTION`；`COMMAND_NOT_SUPPORTED` HTTP 映射对齐 409。

---

## 6. T08 · 07 第7章全量回归（现有套件）

**状态: PASS**（现有套件 PASS；2026-09-15 人工验收确认 RELEASED。information_schema/EXPLAIN 等长尾以既有 Flyway/Arch 证据为准，不阻塞发布）

recordedAtUtc: 2026-09-13T07:52:24Z
JDK: Amazon Corretto 21.0.12；MySQL `9.7.2` / `MySQL Community Server - GPL`（容器 `tit-mysql-t01`，端口 13306）

| 步骤 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 真库 IT | `./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify` | 0 | Failsafe Tests run: 78, Failures: 0（边界后未再全量复跑） |

说明:
- 组合 profile 时 Failsafe `groups` 以 `dual-process-it` 为准，只跑双进程标签用例；mysql-it 全量须单独执行。
- 脏库大量到期 READY 曾导致 `pollAndExecute` 批次饿死目标 Action；已用定点 `executeAction` + inbox 幂等修复。

验收备注（不阻塞 RELEASED）:
- E01—E13 / I01—I07：端点矩阵已记 §5.9–§5.10（部分维度 PARTIAL）
- 07 §6 性能门槛：PASS（证据 §5.11）
- 公共 HTTP 边界：PASS（证据 §5.12）
- information_schema/EXPLAIN 等数据验收长尾：以既有 Flyway/Arch 证据为准

---

## 7. 遗留问题与阻塞项

| 编号 | 问题 | 状态 |
| --- | --- | --- |
| — | 公共 HTTP 边界（07 §5） | PASS（证据 §5.12） |
| — | 07 §6 性能门槛（1万/1000/P95；预热+3 次） | PASS（证据 §5.11） |
| — | E01—E13 独立 HTTP 契约矩阵 | PASS（证据 §5.10；部分维度 PARTIAL） |
| — | I01—I07 HTTP 契约矩阵 | PASS（证据 §5.9；畸形时间/未知字段等 PARTIAL） |
| — | P01 人工最终验收与 RELEASED | PASS（2026-09-15 用户确认；标签 `p01`） |

---

*本文在 RELEASED 后冻结。所有证据来自真实命令执行或用户人工验收确认。*
