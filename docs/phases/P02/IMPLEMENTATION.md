# P02 · 实施任务与门槛

本文件只定义P02的执行顺序。阶段已 RELEASED（2026-09-17；标签 `v20260917-P02`）；本文冻结，不再改写。T01–T06均已PASS，证据见[DELIVERY](DELIVERY.md)。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 2.3结构、测试与迁移契约收敛 | PASS | 用户要求形成落地文档 | 文档链接、编号、CURRENT唯一性和差异检查通过 |
| T01 | 记录基线与生成结构清单 | PASS | 用户明确授权，阶段转IMPLEMENTING | `baselineGitRef`=`0c29c8a09f45644e68596730a6f610703390fb61`；[T01清单](T01-STRUCTURE-INVENTORY.md)与[JSON](T01-STRUCTURE-INVENTORY.json) |
| T02 | 建立行为安全网 | PASS | T01 PASS | 所有者测试覆盖全部 226 顶层生产类型；四大类特征断言；`JsonGoldenContractTest` 通过 |
| T03 | 按职责拆分多职责类 | PASS | T02 PASS | 四大类按变化原因拆分；入口契约保留；`./mvnw test` 266/0；无新跨模块依赖 |
| T04 | 按业务优先规则迁移包 | PASS | T03 PASS | `./mvnw test` 262/0；包路径已迁至 `<root>.<biz>.<tech>`；MyBatis XML/MapperScan/mapper-locations 同步；P01 kernel baseline 已按新路径刷新 |
| T05 | 固化架构与覆盖率门禁 | PASS | T04 PASS | Q01—Q06 `StructureQualityGate`；Q07 ArchUnit + Jacoco 不下降（[T05-COVERAGE-BASELINE.json](T05-COVERAGE-BASELINE.json)）；负例失败；`./mvnw test` 294/0 |
| T06 | 全量回归与交付 | PASS | T05 PASS | 见[DELIVERY](DELIVERY.md)：unit 273/0；mysql-it 91/0；dual-process-it 7/0；Q01—Q08 PASS |

任务只能按顺序推进。任一任务BLOCKED时不得开始后续任务；失败测试不能通过删除断言、排除测试或降低阈值解决。

## 2. T01清单标准

在首次源码变更前记录不可变`baselineGitRef`。清单必须以构建可见事实为准，至少包括：

- 每个模块的生产Java文件、顶层类型、当前包、目标业务功能包和目标技术职责包。
- 每个顶层生产类型对应的所有者测试；`package-info.java`单独列为默认豁免，其他豁免必须写原因并经审核。
- 生产与测试包不一致、无测试模块、空测试、重复所有者测试和跨模块测试归属。
- 超大类、多职责类、Spring配置/扫描字符串、MyBatis XML namespace、反射类名和序列化类型名等迁移敏感点。
- Jackson 2当前依赖、import、Mapper与转换器仅作为X05基线记录；P02不得迁移。

清单是DELIVERY证据，不得作为新的长期权威文档散落在仓库；阶段结束时只把结论写入DELIVERY。

## 3. 执行策略

1. 先为当前实现补齐所有者测试和关键特征测试，再拆类，再移动包。
2. 大类按业务责任和变化原因拆分，不按方法数量机械切割；保持原入口的外部契约，逐步把职责委托给新类型。
3. 包迁移以02的模块映射为唯一目标。低耦合叶子优先，但每批必须包含所有直接消费者的引用更新，始终保持可编译。
4. 每次提交只包含一种结构轴：特征测试、职责拆分或包移动。禁止与功能修改、格式化全库、依赖升级或Jackson迁移混合。
5. Java路径、`package`、import、Spring扫描/配置、MyBatis XML namespace和测试路径在同一批完成；不得保留兼容转发类形成第二套结构。
6. 每批执行受影响模块测试和架构检查；T06再执行全量测试。行为差异先判断是否为缺陷暴露，未经契约评审不得接受为“重构结果”。

每个批次必须能从单独提交完整回退，不能依赖长期保留旧包转发类。回退以该批次前提交为边界，并重新运行受影响测试；禁止用删除测试、降低门槛或保留两套包结构代替回退。

建议迁移顺序为边界接入模块与能力/场景模块、运行与存储适配器、application、extension-api、kernel、domain、common；boot-loader在所有装配目标稳定后收口。实际批次可按依赖分析微调，但不得改变依赖方向或形成循环。

## 4. 自动化门禁

- 生产包必须匹配02定义的`<模块根包>.<业务功能>.<技术职责>[.<子职责>]`；禁止无批准的顶层`impl`、`util`、`misc`。
- 测试包与被测生产包完全一致；测试类型命名只使用02规定的后缀。
- 每个顶层生产类型恰有一个所有者测试；辅助测试可以多个，但不能替代所有者测试。
- 所有者测试必须执行至少一个可观察断言；空方法、禁用、只验证非空、只启动上下文或复制生产算法均不计入。
- 模块依赖、kernel纯Java、扩展边界和MyBatis映射必须继续通过现有架构门禁。
- 覆盖率按07阈值执行，但覆盖率不能替代契约、真库和双进程回归。

## 5. T06验证命令

至少实际执行并记录退出码、测试数、失败项和证据路径：

```text
./mvnw -q test
./mvnw -q package
./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify
./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify
git diff --check
```

如果现有profile的真实入口与上式不同，先按03和构建配置核实并在DELIVERY解释，不得静默跳过。完成后还要核对P01发布标签到P02的公开API、DDL、稳定SPI和业务黄金样例均无语义差异。

## 6. Jackson 3交接门槛

P02只建立和运行JSON黄金样例，不修改Jackson实现。只有P02 RELEASED后，才可把X05建立为独立CURRENT阶段；该阶段必须重新记录基线、执行07的J01—J10并获得新的用户授权。P02中出现Jackson迁移差异时直接回退该差异，不得顺手完成X05。
