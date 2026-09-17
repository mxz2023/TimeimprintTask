# P03 · 实施任务与门槛

本文件只定义 P03 的执行顺序。阶段为 VERIFYING；T00—T07 均已 PASS，证据见[DELIVERY](DELIVERY.md)。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立 P03 阶段包与入口同步 | PASS | 用户要求建阶段（X05） | README/IMPLEMENTATION 存在；CURRENT 唯一；00/08/09/阶段索引一致 |
| T01 | 记录基线与 Jackson 2 清单 | PASS | 用户明确授权，阶段转 IMPLEMENTING | `baselineGitRef`=`e26e27bf8745dfa2c14c73d6b3722a72af25013f`；[T01清单](T01-JACKSON-BASELINE.md)；JsonGoldenContractTest 迁移前 PASS |
| T02 | 切换模块 POM 至 Jackson 3 坐标 | PASS | T01 PASS | `tools.jackson.core:jackson-databind`；移除 jsr310；notification 去掉未用依赖 |
| T03 | 迁移非注解 import 与 API 差异 | PASS | T02 PASS | `tools.jackson.*`；annotations 未机械替换；`JsonNode.propertyNames` 等 API 适配 |
| T04 | boot-loader 唯一 JsonMapper 配置 | PASS | T03 PASS | `JacksonJsonConfiguration` + `TimeImprintJacksonDefaults`；RuntimeBeans 不再建 Mapper |
| T05 | 删除 Jackson 2 MVC 覆盖 | PASS | T04 PASS | 删除 `Jackson2WebConfig`；Boot 原生 Jackson 3 转换器 |
| T06 | 历史 JSON、哈希与幂等验证 | PASS | T05 PASS | JsonGolden + mysql-it；哈希路径稳定 |
| T07 | 残留清零、全量回归与交付 | PASS | T06 PASS | 见[DELIVERY](DELIVERY.md)：unit 275/0；mysql-it 91/0；dual-process-it 7/0；J01—J10 PASS |

任务只能按顺序推进。任一任务 BLOCKED 时不得开始后续任务；失败测试不能通过删除断言、排除测试、批量改库内 JSON 或降低阈值解决。

## 2. T01 清单标准

在首次源码/依赖变更前记录不可变 `baselineGitRef`（建议锚定 `v20260917-P02` 对应提交，除非授权时 HEAD 另有说明）。清单至少包括：

- 各模块 Jackson 相关直接/传递依赖（含 datatype、parameter-names）。
- 生产中 `ObjectMapper` / `JsonMapper` / MVC 转换器 Bean 与配置类位置。
- 含 `com.fasterxml.jackson`（非 annotation）引用的生产与测试文件计数及路径摘要。
- 迁移前 HTTP/持久化/哈希黄金契约命令与通过证据（只记录，不改实现）。
- 明确 annotations 例外与禁止机械替换说明。

清单是 DELIVERY 证据材料；阶段结束时结论写入 DELIVERY，不另建长期权威文档。

## 3. 执行策略

严格按[03 §1.2](../../03-INTEGRATION-CONTRACTS.md)七步推进，映射到 T01—T07：

1. 基线与黄金契约（T01）。
2. POM 坐标切换（T02）。
3. 非注解 import/API（T03）。
4. 唯一 JsonMapper 构建配置（T04）。
5. MVC 转换器切换（T05）。
6. 历史 JSON + 哈希/幂等（T06）。
7. 残留扫描 + 全量回归 + DELIVERY（T07）。

每批保持可编译；双 Mapper 只允许迁移分支临时对照，不得进入 T07 交付。默认行为与黄金契约冲突时先显式配置恢复兼容；无法恢复则回退 `baselineGitRef` 并将阶段标 BLOCKED。

## 4. 自动化门禁

- [J01](../../07-ACCEPTANCE.md)—[J05](../../07-ACCEPTANCE.md)、[J10](../../07-ACCEPTANCE.md) 须有可重复的依赖树/源码/制品扫描失败信号，不能只靠人工观察。
- [J06](../../07-ACCEPTANCE.md)—[J09](../../07-ACCEPTANCE.md) 以契约测试与既有 mysql-it / dual-process-it 为准。
- 现有 ArchUnit、结构门禁、所有者测试与覆盖率不下降要求继续有效；本阶段不得借迁移关闭。

## 5. T07 验证命令

至少实际执行并记录退出码、测试数、失败项和证据路径：

```text
./mvnw -q test
./mvnw -q package
./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify
./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify
git diff --check
```

另须记录 J01—J10 逐项证据，并核对相对 `v20260917-P02` / `v20260915-P01` 的公开 API、DDL、稳定 SPI 语义差异为 0。profile 入口若与上式不同，先核实并在 DELIVERY 解释，不得静默跳过。

## 6. 授权与回滚

- 仅当用户明确授权后，可将 README 转为 IMPLEMENTING 并开始 T01。
- 回滚边界为本阶段 `baselineGitRef`；禁止用数据修补或 API 变更掩盖不兼容。
- 用户审核 DELIVERY 前不得宣称 RELEASED，不得打 `vyyyyMMdd-P03` 标签。
