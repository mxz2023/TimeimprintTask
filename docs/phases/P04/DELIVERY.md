# P04 · 交付证据

> 本文记录 P04 已经验证的实现证据。只有经过真实命令执行、退出码为零且测试通过的条目才能记为通过。
> 阶段身份与范围见 [README](README.md)，任务状态见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-10-03T13:47:41Z
阶段状态: RELEASED（2026-10-03 用户人工验收通过；Git 标签 `v20261003-P04`；本文此后不再改写）

---

## 1. 环境

| 项目 | 值 |
| --- | --- |
| JDK | Amazon Corretto 21.0.12（`openjdk version "21.0.12" 2026-07-21 LTS`） |
| Maven Wrapper | 3.9.9 |
| Spring Boot | 4.0.8 |
| MySQL 镜像 | `mysql:9.7.2` |
| MySQL 镜像 Digest | `sha256:29abb0a179982e4a8928138bfc7f918af9eda64e7eeb1b1d084c1720a20159e6` |
| MySQL SELECT VERSION() | `9.7.2` |
| MySQL 事务隔离级别 | `READ-COMMITTED` |
| 验收库 | 容器 `tit-mysql-t01`，`127.0.0.1:13306`；功能库 `timeimprint_task_local`；性能库 `timeimprint_task_perf` |
| VERIFYING 证据入仓 | `c750443`（对齐验收库隔离与清库脚本） |
| RELEASED 冻结提交 | 见标签 `v20261003-P04` |
| 基础发布 | `v20260917-P03` |

---

## 2. 验证命令

| 命令 | 退出码 | 结果摘要 |
| --- | --- | --- |
| `./mvnw -q test` | 0 | Surefire 按类汇总 **283** run / 0 fail / 0 error |
| `./mvnw -q package -DskipTests` | 0 | BUILD SUCCESS |
| `./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify` | 0 | Failsafe **92** run / 0 fail / 0 error（处理遗留 `event` 信号之后复跑） |
| `./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify -Dit.test=PerfGateDualProcessIT` | 0 | Failsafe **2** run / 0 fail。预热与三次正式均为 inbox=1000、backlog=0；P95 迁移与收件 ≤3s。专用库 `timeimprint_task_perf` |
| `./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify` | 0 | Failsafe **7** run / 0 fail / 0 error（含性能门槛） |
| `git diff --check` | 0 | 无空白错误 |

日志留存（本机，非仓库）：`/tmp/p04-unit-test.log`、`/tmp/p04-mysql-it.log`、`/tmp/p04-mysql-it2.log`、`/tmp/p04-dual-it.log`、`/tmp/p04-perf-gate4.log`、`/tmp/p04-dual-it2.log`。

第一次真库全量失败时，`timeimprint_task_local` 里有一条仍为 `READY` 的信号：`signal_id=1542`，`provider_key=event`。`LiveSchemaReadinessIndicator`（`timeimprint-task-boot-loader` 模块）把没有读取器的未终态信号算作未就绪。处理：该行已标为 `IGNORED`；[I01](../../04-API.md)（POST `/internal/v1/task-signals/event`）的 `IMatrixMysqlIT` 在断言 `READY` 之后把刚写入的信号标为 `IGNORED`。

性能库与功能库分开是有意的，见 [07](../../07-ACCEPTANCE.md)。领取仍按到期时间全库排序；`PerfGateDualProcessIT`（`timeimprint-task-boot-loader` 模块）在测量前暂停本 suite 以外的绑定，并把已到期信号与动作推后。清库脚本见 `scripts/reset-acceptance-dbs.sh`。

本期任务对应证据：

| 任务 | 证据 |
| --- | --- |
| T01 | `RecurringTodoScenarioExtensionTest` 3 项，含在 `./mvnw -q test` |
| T02 | `RecurringTodoCommandHandlerTest`，含在 `./mvnw -q test` |
| T03 | `S02RecurringTodoMysqlIT` 2 项，含在真库全量 |
| T04 | `InstanceCommandServiceTest` 3 项，含在 `./mvnw -q test` |
| T05 | `InstanceCommandPortImplTest` 3 项；`A06CompleteNotificationMysqlIT` 2 项 |

---

## 3. 相对 P03 的契约差异

| 检查项 | 结果 |
| --- | --- |
| 公共表 | 未改 DDL |
| 公开 HTTP 字段 | 未改字段形状 |
| kernel（稳定内核）与稳定 SPI（稳定扩展接口） | 未改业务语义和注册键 |
| 场景实现状态 | [S02](../../scenarios/S02-recurring-todo.md)（周期待办）仍为 P01 已验收的 VERIFIED |

催办标题为全角「催办：」紧接实例标题快照。实例命令先普通读取父级编号，再锁定义，再锁实例。取消未发通知按 `action_job_id` 升序逐行锁定。

---

## 4. 任务闭环

| 任务 | 状态 |
| --- | --- |
| T00—T05 | PASS（本证据） |

2026-10-03 用户人工验收确认 RELEASED；发布标签 `v20261003-P04`。本文此后不再改写。
