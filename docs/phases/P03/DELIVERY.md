# P03 · 交付证据

> 本文记录P03已经验证的实现证据。只有经过真实命令执行、退出码为零且测试通过的条目才能记录于此。
> 阶段身份与范围见 [README](README.md)，任务状态见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-09-17T15:25:00Z
阶段状态: RELEASED（2026-09-17 用户人工验收通过；Git 标签 `v20260917-P03`；本文此后不再改写）

---

## 1. 环境

| 项目 | 值 |
| --- | --- |
| JDK | Amazon Corretto 21.0.12 (`openjdk version "21.0.12" 2026-07-21 LTS`) |
| Maven Wrapper | 3.9.9 |
| Spring Boot | 4.0.8 |
| Jackson 目标 | Boot BOM 管理的 `tools.jackson` **3.1.5** |
| MySQL 镜像 | `mysql:9.7.2` |
| MySQL 镜像 Digest | `sha256:29abb0a179982e4a8928138bfc7f918af9eda64e7eeb1b1d084c1720a20159e6` |
| 证据记录时 HEAD | VERIFYING 入仓于 `97a8f76f21857defaf2e782b74c96c807d8ecb9d`；基线 `e26e27bf8745dfa2c14c73d6b3722a72af25013f`；RELEASED 冻结提交见标签 `v20260917-P03` |
| P02 发布标签 | `v20260917-P02` |

---

## 2. T07 验证命令

| 命令 | 退出码 | 结果摘要 |
| --- | --- | --- |
| `./mvnw -q test` | 0 | Surefire 按类汇总 **275** run / 0 fail / 0 error |
| `./mvnw -q package -DskipTests` | 0 | BUILD SUCCESS |
| `./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify` | 0 | Failsafe **91** / 0 / 0 |
| `./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify` | 0 | Failsafe **7** / 0 / 0（合并跑通；此前 PerfGate p95 边界抖动曾单独复跑亦 PASS） |
| `git diff --check` | 0 | 无空白错误 |

---

## 3. J01—J10

| 编号 | 结论 | 证据 |
| --- | --- | --- |
| [J01](../../07-ACCEPTANCE.md) | PASS | 依赖树：`tools.jackson.core:jackson-databind:3.1.5`；无覆盖 `jackson.version` |
| [J02](../../07-ACCEPTANCE.md) | PASS | 业务源码无 Jackson 2 databind/core import（`JacksonMigrationPurityGateTest`）；classpath 仅 annotations + Flyway 传递的 Jackson 2（03 已记录唯一例外） |
| [J03](../../07-ACCEPTANCE.md) | PASS | 模块 POM 使用 `tools.jackson.core:jackson-databind`；annotations 仍为 `com.fasterxml.jackson` |
| [J04](../../07-ACCEPTANCE.md) | PASS | `boot-loader` `JacksonJsonConfiguration` + `JsonMapperBuilderCustomizer`；`TimeImprintJacksonDefaults`；`RuntimeBeans` 不再自建 Mapper |
| [J05](../../07-ACCEPTANCE.md) | PASS | 已删除 `Jackson2WebConfig` / `MappingJackson2HttpMessageConverter` 覆盖 |
| [J06](../../07-ACCEPTANCE.md) | PASS | `JsonGoldenContractTest` + mysql-it HTTP 矩阵 |
| [J07](../../07-ACCEPTANCE.md) | PASS | mysql-it 读写既有 JSON 列与幂等路径 |
| [J08](../../07-ACCEPTANCE.md) | PASS | 哈希仍基于显式字符串/`Sha256`；黄金与回归稳定 |
| [J09](../../07-ACCEPTANCE.md) | PASS | unit + mysql-it + dual-process（含 S01/S02） |
| [J10](../../07-ACCEPTANCE.md) | PASS | 无双 Mapper；无业务 Jackson 2 残留；Flyway 例外已文档化 |

---

## 4. 相对 P02 的契约差异（应为 0）

| 检查项 | 结果 |
| --- | --- |
| Flyway 公共 DDL | 相对 `v20260917-P02` 无 migration 差异 |
| 公开 HTTP / 稳定 SPI | 语义不变（实现换 Jackson 3） |
| Maven `<module>` 列表 | 无变更 |

---

## 5. 主要实现要点

| 项 | 说明 |
| --- | --- |
| POM | 7 模块切到 `tools.jackson`；移除 `jackson-datatype-jsr310`；notification 去掉未用 databind |
| 配置 | `TimeImprintJacksonDefaults`（`configureForJackson2` + unknown/duplicate）；boot-loader Customizer |
| MVC | 删除 Jackson2 强制转换器，使用 Boot 原生 Jackson 3 |
| IT | Fairness 强化隔离；`pollOnceForTests` 与生产一致吞单任务失败 |

---

## 6. 任务闭环

| 任务 | 状态 |
| --- | --- |
| T00—T07 | PASS（本证据） |

2026-09-17 用户人工验收确认 RELEASED；发布标签 `v20260917-P03`。本文此后不再改写。
