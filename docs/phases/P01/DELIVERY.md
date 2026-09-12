# P01 · 交付证据

> 本文记录P01已经验证的实现证据。只有经过真实命令执行、退出码为零且测试通过的条目才能记录于此。
> 阶段身份与范围见 [README](README.md)，任务状态见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-09-12
阶段状态: IMPLEMENTING (T07+T08完成验证)

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
- 13个平级Maven模块编译通过（`MxzModuleBaselineTest.approvedModulesExist` PASS）
- ArchUnit规则验证kernel无Spring/MyBatis/Web依赖（`MxzArchitectureRulesTest.kernelMustRemainPureJava` PASS）
- API契约测试通过（`MxzApiContractTest` PASS）
- 扩展SPI契约测试通过（`MxzExtensionSpiContractTest` PASS）
- Calendar算法纯函数测试通过（`MxzCalendarOccurrenceCalculatorTest` PASS）
- TransitionPlan契约测试通过（`MxzTransitionPlanContractTest` PASS）
- Flyway表集合测试通过（`MxzFlywaySchemaInformationSchemaTest.contractListsTwelveTables` PASS）
- 模块基线测试通过（`MxzModuleBaselineTest.approvedModulesExist` PASS）

---

## 3. T02 · S01 ONCE最小纵向闭环

**状态: PASS**

```
命令: ./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify
退出码: 0
测试: MxzS01OnceMysqlIT#s01OnceVerticalLoop PASS
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
  MxzS01OnceMysqlIT#s01OnceVerticalLoop                  PASS
  MxzS02BasicMysqlIT#s02RecurringTodoOncePlanToPendingToCompleted   PASS
  MxzS02BasicMysqlIT#s02RecurringTodoDailyPlanToPendingToSkipped    PASS
  MxzS02RecurringTodoMysqlIT#s02DailyBasicLoop            PASS
  MxzT07FixtureMysqlIT#approvalFixtureRegistersAndMaterializes      PASS
  MxzT07FixtureMysqlIT#eventTriggerFixtureIsRegistered    PASS
  MxzT07FixtureMysqlIT#webhookActionFixtureIsRegistered   PASS
```

### 4.1 Kernel + Platform DDL 零变更断言

- SHA-256基线记录在 [BASELINE-SHA256.txt](BASELINE-SHA256.txt)（"Kernel production sources"和"Platform public Flyway DDL"节）
- 基线在T07夹具加入前生成（2026-09-12）
- 单元测试 `MxzT07KernelIntegrityTest#kernelAndPlatformDdlMatchBaseline` 验证所有文件哈希与基线一致

### 4.2 三个测试夹具

| 夹具 | 实现类 | 接口 | 位置 |
| --- | --- | --- | --- |
| ApprovalFixture | `ApprovalFixture` + `ApprovalDataMaterializer` | `ScenarioExtension` + `ScenarioDataMaterializer` | boot-loader test sources |
| EventTriggerFixture | `EventTriggerFixture` | `TriggerProvider` | boot-loader test sources |
| WebhookActionFixture | `WebhookActionFixture` | `ActionHandler` | boot-loader test sources |

夹具均通过稳定扩展SPI装配，不修改kernel生产源码或平台公共DDL。

### 4.3 E09 实例级命令接口

- `MxzInstanceCommandService` 实现complete/skip命令管道（dedup → 父级锁 → 子级锁 → 执行Handler → 提交 → 返回CommandResultView）
- `MxzTaskInstanceController#executeCommand` 实现E09端点
- `MxzRecurringTodoCompleteHandler` 和 `MxzRecurringTodoSkipHandler` 已注册（`service-scenario-basic`）

### 4.4 S02 纵向闭环（MxzS02BasicMysqlIT）

| 场景 | 流程 | 结果 |
| --- | --- | --- |
| ONCE | 预览→创建→PLANNED→processSignal→PENDING→E09 complete→COMPLETED | PASS |
| DAILY | 创建→PLANNED（取最早实例）→processSignal→PENDING→E09 skip→SKIPPED | PASS |

---

## 5. 双进程与性能验收

**状态: 局部PASS / 全量NOT_RUN**

```
命令: ./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify
退出码: 0
测试: MxzDualClaimMysqlIT#concurrentClaimsDoNotDuplicateSignal PASS
```

已验证：
- 并发 SKIP LOCKED 领取互斥（同一 Signal 不被两个 claim 同时拿走）

尚未执行（保持 NOT_RUN）：
- 完整双 JVM 进程崩溃接管 / 优雅停机
- M01—M10 性能门槛
- A01—A42 完整验收矩阵
- E01—E13、I01—I07 全量回归

---

## 6. 遗留问题与阻塞项

| 编号 | 问题 | 状态 |
| --- | --- | --- |
| — | 完整双JVM进程 IT | NOT_RUN |
| — | Performance gate / A01—A42 全矩阵 | NOT_RUN |
| — | P01 人工最终验收与 RELEASED | 待用户确认 |

---

*本文由实施过程自动更新；所有证据必须来自真实命令执行，不得预填写预期结果。*
