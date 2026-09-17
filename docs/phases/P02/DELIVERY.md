# P02 · 交付证据

> 本文记录P02已经验证的实现证据。只有经过真实命令执行、退出码为零且测试通过的条目才能记录于此。
> 阶段身份与范围见 [README](README.md)，任务状态见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-09-17T00:34:10Z
阶段状态: VERIFYING（等待用户审核后转 RELEASED 并打标签）

---

## 1. 环境

| 项目 | 值 |
| --- | --- |
| JDK | Amazon Corretto 21.0.12 (`openjdk version "21.0.12" 2026-07-21 LTS`) |
| Maven Wrapper | 3.9.9 |
| Spring Boot | 4.0.8 |
| MySQL 镜像 | `mysql:9.7.2` |
| MySQL 镜像 Digest | `sha256:29abb0a179982e4a8928138bfc7f918af9eda64e7eeb1b1d084c1720a20159e6` |
| MySQL SELECT VERSION() | `9.7.2` |
| MySQL 事务隔离级别 | `READ-COMMITTED` |
| 证据记录时 HEAD | `026e640d078e097e1149e13e2237423b17eebe03`（T05；本 DELIVERY 提交另计） |
| 基线提交 | `0c29c8a09f45644e68596730a6f610703390fb61` |
| P01 发布标签 | `v20260915-P01` |

---

## 2. T06 验证命令

| 命令 | 退出码 | 结果摘要 |
| --- | --- | --- |
| `./mvnw -q test` | 0 | Surefire 按类汇总 **273** run / 0 fail / 0 error |
| `./mvnw -q package -DskipTests` | 0 | BUILD SUCCESS |
| `./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify` | 0 | Failsafe **91** / 0 / 0 |
| `./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify` | 0 | Failsafe **7** / 0 / 0（合并跑通；此前共享库污染时曾按类隔离复跑亦全过） |
| `git diff --check` | 0 | 无空白错误 |

日志留存（本机，非仓库）：`/tmp/p02-t06-mysql-it4.log`、`/tmp/p02-t06-dual-final.log`。

---

## 3. Q01—Q08

| 编号 | 结论 | 证据 |
| --- | --- | --- |
| [Q01](../../07-ACCEPTANCE.md) | PASS | `StructureQualityGate` / `StructureQualityGateTest` |
| [Q02](../../07-ACCEPTANCE.md) | PASS | 同上（路径/package/XML/空目录） |
| [Q03](../../07-ACCEPTANCE.md) | PASS | 同上（测试镜像与后缀） |
| [Q04](../../07-ACCEPTANCE.md) | PASS | 同上（所有者映射 100%） |
| [Q05](../../07-ACCEPTANCE.md) | PASS | 同上（所有者可观察断言）+ 负例 `StructureQualityGateNegativeTest` |
| [Q06](../../07-ACCEPTANCE.md) | PASS | 特征测试清单仍存在（gate Q06） |
| [Q07](../../07-ACCEPTANCE.md) | PASS | `ArchitectureRulesTest`；Jacoco 不下降 [T05-COVERAGE-BASELINE.json](T05-COVERAGE-BASELINE.json) / `CoverageNoDeclineGateTest` |
| [Q08](../../07-ACCEPTANCE.md) | PASS | 见第 4 节；P01 回归 mysql-it 91/0、dual-process-it 7/0 |

---

## 4. 相对 P01 的契约差异（应为 0）

对照标签 `v20260915-P01`：

| 检查项 | 结果 |
| --- | --- |
| Flyway 公共 DDL | `git diff v20260915-P01..HEAD -- '**/db/migration/**'` 为空 |
| 公开 HTTP 路径集合 | Controller `@*Mapping` 路径集合与 P01 一致（无增删） |
| 稳定 SPI 方法签名 | `ScenarioExtension` / `TriggerProvider` / `TaskCommandHandler` / `ActionHandler` / `Policy` / `ScenarioDataMaterializer` 方法签名集合相等 |
| Maven `<module>` 列表 | 无变更 |
| Jackson 坐标 | 仍为 Jackson 2（`com.fasterxml.jackson`）；未做 X05 迁移 |
| Kernel/DDL SHA | `T07KernelIntegrityTest` PASS（P02 T04 已按新包路径刷新基线） |

说明：生产 Java **包路径**按 02 `root.biz.tech` 迁移，属本阶段允许范围；公开 API/DDL/SPI **语义**无变更。

---

## 5. T06 期间修复（结构回归暴露，非业务范围扩大）

| 问题 | 修复 |
| --- | --- |
| `@MapperScan` 扫到 `NotificationMaterializationPort` 导致双 Bean | `MybatisStorageConfig` 增加 `annotationClass = Mapper.class` |
| [A40](../../07-ACCEPTANCE.md) 残留 `Mxz` 类名前缀断言 | 改为断言 `DefinitionCommandService` |
| 双进程公平性 IT 受共享库 READY 污染 | `FairnessBacklogDualProcessIT`：播种前清理、未来 `available_at` 播种后再激活、排除非本轮 due READY |

---

## 6. 任务闭环

| 任务 | 状态 |
| --- | --- |
| T00—T05 | PASS（既有提交） |
| T06 | PASS（本证据） |

用户审核本 DELIVERY 后，可将阶段 README 转为 RELEASED，并按 `vyyyyMMdd-P02` 打发布标签；在此之前不得宣称 RELEASED。
