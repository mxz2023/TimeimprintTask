# P03 · Jackson 3 原生迁移

本阶段只将运行时 JSON 实现从 Jackson 2 兼容模式迁移到 Spring Boot 4 BOM 管理的 Jackson 3.1.5，不改变 P01/P02 已发布的业务语义、公开 API、公共 DDL 或稳定 SPI。详细任务见[IMPLEMENTATION](IMPLEMENTATION.md)；目标契约见[03 §1.1–1.2](../../03-INTEGRATION-CONTRACTS.md)；验收见[07 §2.2 J01—J10](../../07-ACCEPTANCE.md)。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P03 |
| 排期身份 | CURRENT |
| 总体状态 | VERIFYING |
| 文档基线 | 2.3 |
| 基础发布 | P02；Git标签`v20260917-P02`（其上含 P01 `v20260915-P01` 业务基线） |
| 基线提交 | `e26e27bf8745dfa2c14c73d6b3722a72af25013f`（T01；见[T01清单](T01-JACKSON-BASELINE.md)） |
| 工程状态 | VERIFYING |
| 下一动作 | 审核[DELIVERY](DELIVERY.md)；确认后转 RELEASED 并打 `vyyyyMMdd-P03` 标签 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | [DELIVERY](DELIVERY.md) |

本阶段对应[09](../../09-SCENARIO-ROADMAP.md)技术候选 X05。用户已于 2026-09-17 授权完成全部 P03 任务；T01–T07 已 PASS，证据见[DELIVERY](DELIVERY.md)。

## 2. 目标与范围

P03 必须完成：

- 按[03 §1.2](../../03-INTEGRATION-CONTRACTS.md)顺序切换到 Boot BOM 管理的 Jackson 3.1.5（`tools.jackson.*` databind/core；annotations 官方例外保留 `com.fasterxml.jackson.annotation`）。
- 生产统一注入不可变 `JsonMapper`；全局配置仅在 `boot-loader`；MVC 使用 Boot 原生 Jackson 3 转换器。
- 运行制品清除 Jackson 2 core/databind/datatype；禁止交付双 Mapper 桥接。
- [J01](../../07-ACCEPTANCE.md)—[J10](../../07-ACCEPTANCE.md) 全部 PASS；HTTP/持久化 JSON/哈希与幂等相对 P01/P02 黄金契约兼容。
- 单元、打包、真 MySQL、双进程全量回归通过，证据写入本阶段 DELIVERY。

本阶段不新增业务场景或能力。S01、S02 以及 CAL-01—CAL-05、NOT-01—NOT-03 保持 RELEASED / VERIFIED；其他条目三维状态不变。场景与能力准入对本技术迁移阶段不适用。

### 2.1 立项时现状快照

以 P02 RELEASED 标签 `v20260917-P02` 为起点（只读观察，实施时由 T01 重生成可追溯清单）：

| 观察项 | 当前结果 | P03 含义 |
| --- | --- | --- |
| JSON 实现 | 仍为 Jackson 2（`com.fasterxml.jackson`）、ObjectMapper 与 Jackson 2 MVC 转换器覆盖 | 本阶段唯一迁移对象 |
| 黄金契约 | P02 已建立/运行 JSON 黄金样例（如 `JsonGoldenContractTest`） | 迁移前后必须保持可观察兼容 |
| 包结构 | P02 已完成 `root.biz.tech` 与测试镜像 | 本阶段不重做包移动 |
| 业务契约 | P01 API/DDL/SPI 语义冻结 | 不得借迁移改公开行为 |

## 3. 允许范围

- 各模块 POM 中 Jackson 坐标与直接依赖声明（按实际使用切换到 Jackson 3；保留 annotations 例外）。
- 非注解的 Jackson import、API/异常类型适配，以及显式规范化哈希所需的最小配置调整。
- `boot-loader` 内唯一 `JsonMapper` 构建配置（`JsonMapperBuilderCustomizer` / `JsonMapper.builder()`）。
- 删除 Jackson 2 `MappingJackson2HttpMessageConverter` 强制替换，改用 Boot 自动配置的 Jackson 3 转换器。
- 为 J01—J10 与回归新增或调整的测试、依赖树/制品扫描门禁。
- 仅当默认行为与 P01 黄金契约冲突时，用显式配置恢复兼容（不得借此扩大 API 语义）。

每批保持可编译；任一批失败回退到本阶段 `baselineGitRef`，不得批量重写库内 JSON、清空幂等记录或同时改 API。

## 4. 禁止范围

- 不改变 INV-01—INV-09、kernel 业务语义、公开 API 路径/字段/错误、12 张表及 Flyway、稳定 SPI 签名或 Maven 模块集合/依赖方向。
- 不新增场景/能力，不改场景或能力三维状态。
- 不修改数据库 JSON 内容、不做数据迁移脚本；不把默认序列化差异直接接受为升级结果。
- 不对 `com.fasterxml.jackson.annotation` 做机械替换；不保留进入交付的双 Mapper 临时桥接。
- 不覆盖 `jackson.version`、不使用动态版本范围；不顺手做包移动、业务功能或 X 以外依赖升级。
- 不修改已 RELEASED 的 P01/P02 README、IMPLEMENTATION、DELIVERY。

若黄金契约无法恢复、历史 JSON 不可读或哈希变化，阶段转 BLOCKED，按[08](../../08-AI-IMPLEMENTATION-TASKS.md)单独评审；不得扩大 P03 范围。

## 5. 不变量与验证

INV-01—INV-09 均适用且不允许变化。P03 重点验证 INV-02 模块依赖、INV-03 纯 Java 内核、INV-08 基础设施边界在依赖替换后仍成立；完整定义以[02](../../02-AI-CODING-GUIDE.md)为准。

阶段完成至少需要：

- [J01](../../07-ACCEPTANCE.md)—[J10](../../07-ACCEPTANCE.md) 全部 PASS，并写入 DELIVERY。
- `./mvnw test`、`package`、`-Pmysql-it`、`-Pdual-process-it` 真实通过。
- 相对 `v20260917-P02` / `v20260915-P01`：公开 API、DDL、稳定 SPI 语义差异为 0。
- 用户审核 DELIVERY 后，才可转 RELEASED 并打 `vyyyyMMdd-P03` 标签。

本文件不预填通过结论；未执行项一律为 NOT_RUN。
