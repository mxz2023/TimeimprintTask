# 00 · 开发导航与当前基线

本文是TimeImprintTask文档集的阅读入口和阶段状态入口，不重复定义业务、API、数据或运行规则。平台公共主题由01—08分别负责，场景由`scenarios/`、通用能力域由`capabilities/`长期维护；09只记录演进路线与跨项关系，10记录评审理由与尚待实施验证的风险。

当前文档基线版本为2.3，最后收敛日期为2026-09-16。项目目标是建设“稳定内核 + 可插拔能力”的通用任务平台；首期以本地固定身份运行，并完成后续同类场景可复用的核心、公共存储、Signal/Action运行时、场景专有数据原子物化和多进程恢复机制。2.3在不改变P01业务、API、数据和稳定SPI的前提下，增加业务优先包结构、测试镜像与Jackson 3迁移契约。P01已于2026-09-15人工验收通过并 RELEASED（Git 标签 `v20260915-P01`）。P02已于2026-09-17人工验收通过并 RELEASED（Git 标签 `v20260917-P02`）。P03已于2026-09-17人工验收通过并 RELEASED（Git 标签 `v20260917-P03`）。当前 CURRENT 为 [P04](phases/P04/README.md)（DRAFT），范围是 S02 站内信标题和实例命令锁序，未授权不得编码。

## 1. 阅读顺序

| 顺序 | 文件 | 负责内容 |
| --- | --- | --- |
| 00 | [本文](00-READING-ORDER.md) | 阅读导航、当前基线、状态和冲突处理 |
| 01 | [MVP-SPEC](01-MVP-SPEC.md) | 平台公共业务语义与首期范围 |
| 02 | [AI-CODING-GUIDE](02-AI-CODING-GUIDE.md) | 稳定内核、扩展契约、模块边界和编码规范 |
| 03 | [INTEGRATION-CONTRACTS](03-INTEGRATION-CONTRACTS.md) | 技术版本、配置、装配、迁移和运行环境 |
| 04 | [API](04-API.md) | HTTP请求、响应、读模型、错误和幂等协议 |
| 05 | [DATABASE](05-DATABASE.md) | 表、字段、约束、索引和跨表不变量 |
| 06 | [SCHEDULING](06-SCHEDULING.md) | 规划、事务、锁、队列、恢复和并发协议 |
| 07 | [ACCEPTANCE](07-ACCEPTANCE.md) | 验收场景、测试环境、指标和证据标准 |
| 08 | [AI-IMPLEMENTATION-TASKS](08-AI-IMPLEMENTATION-TASKS.md) | 所有阶段共同遵守的实施和文档维护规则 |
| 09 | [SCENARIO-ROADMAP](09-SCENARIO-ROADMAP.md) | 当前、下一评审、长期规划及跨项演进关系 |
| 10 | [TECHNICAL-REVIEW](10-TECHNICAL-REVIEW.md) | 设计理由、已处理问题和实施期验证风险 |
| 阶段 | [阶段索引](phases/README.md) | 已发布、当前和未来阶段的唯一索引 |
| 已发布 | [P01阶段入口](phases/P01/README.md) | 第一期 RELEASED；交付见[DELIVERY](phases/P01/DELIVERY.md)，标签 `v20260915-P01` |
| 已发布 | [P02工程结构与测试镜像](phases/P02/README.md) | 工程结构与测试镜像 RELEASED；交付见[DELIVERY](phases/P02/DELIVERY.md)，标签 `v20260917-P02` |
| 已发布 | [P03 Jackson 3 原生迁移](phases/P03/README.md) | Jackson 3 RELEASED；交付见[DELIVERY](phases/P03/DELIVERY.md)，标签 `v20260917-P03` |
| 当前 | [P04 标题与锁序](phases/P04/README.md) | DRAFT。催办标题为「催办：」加实例标题；实例命令改为先锁定义再锁实例。未授权不得编码 |
| 场景 | [场景索引](scenarios/README.md) | S01—S17规划、契约和实现状态及永久入口 |
| 能力 | [能力索引](capabilities/README.md) | 八个能力域、C01—C19归属、范围和实现状态 |
| 决策 | [核心决策索引](decisions/README.md) | 未来核心模型变化的理由、影响和替代关系 |
| 附录 | [HUMAN-GLOSSARY](HUMAN-GLOSSARY.md) | 英文术语、字段和状态的中文释义 |

首次参与项目的开发者或编码AI必须先读完00—10、场景/能力索引与阶段索引；再读取唯一CURRENT阶段的README和IMPLEMENTATION。开始某个场景实施任务前，还要读取目标场景文件及全部依赖能力文件。CURRENT为READY仍不等于已获编码授权；IMPLEMENTING后方可按任务顺序编码。不能只依据会话摘要、历史记录或单份文档编码。

## 2. 权威边界

00只是导航和状态入口，不是覆盖其他文档的“总需求”。发生问题时按以下边界处理：

- 跨场景公共业务行为和首期范围以01为准；各场景专有配置、状态、命令和异常结果以`scenarios/`中的永久文件为准。
- 跨场景可复用机制、当前支持范围和未来能力项以`capabilities/`中的永久文件为准；能力域名称存在不表示其全部能力已经实现。
- 架构、模块依赖、扩展接口和Java编码规则以02为准。
- 技术版本、环境、配置、装配和迁移加载以03为准。
- HTTP字段、读模型、错误和接口幂等以04为准。
- 数据字段、约束、索引和跨表不变量以05为准。
- 事务、锁、异步执行、恢复和并发结果以06为准。
- 是否达到完成标准以07要求的真实测试和证据为准。
- 实施范围、顺序和阶段准入以08为准。
- 09只负责规划排序和跨项关系；NEXT_REVIEW不是实施许可，场景及依赖能力必须达到READY并进入CURRENT阶段。
- 10用于理解设计理由和风险；若摘要与01—08正式契约不一致，以01—08为准并修正10。
- 场景/能力索引负责三维状态和永久入口；阶段交付记录证明实现，不替代当前规则定义。
- 核心决策记录解释为什么改变设计；ACCEPTED决策只有同步进入01—08后才成为可实施规则。

新的用户明确指令优先于现有文档，但必须先同步修改所有受影响的正式契约、验收项和实施任务，再继续受影响的编码。发现跨文档冲突时不得由实现者自行选择一种解释。

## 3. 当前有效基线

| 项目 | 当前结论 | 正式来源 |
| --- | --- | --- |
| 项目名称与工程前缀 | `TimeImprintTask`；Maven/工程前缀`timeimprint-task`；表前缀`tt_` | 02、03、05 |
| Java命名 | 项目自定义类型不使用`Mxz`前缀；按模块与职责命名 | 02 |
| 技术线 | Java 21 LTS、Spring Boot 4.0.8、MyBatis Starter 4.0.1、Spring AI 2.0.x稳定线、MySQL 9.7.x LTS | 03 |
| JSON技术线 | P01/P02 业务基线之上，P03已迁移到Boot BOM管理的Jackson 3.1.5（标签`v20260917-P03`） | 03、04、07、09、[P03](phases/P03/README.md) |
| 首期产品形态 | 本地固定身份、API优先的可运行后端，不把GUI或生产多租户身份作为首期交付条件 | 01、04 |
| 核心模型 | TaskDefinition → TaskInstance → Signal → TransitionPlan → Action Job | 01、02 |
| 首期场景 | S01 reminder、S02 recurring_todo及五种calendar规则 | [场景契约](scenarios/README.md)、01、04 |
| 首期能力状态 | calendar CAL-01—CAL-05、notification NOT-01—NOT-03 已 VERIFIED；其他能力域/能力项仍为 OUTLINE | [能力索引](capabilities/README.md) |
| 后续规划 | S03/S04/S05/S14为NEXT_REVIEW；其他后续场景与能力为BACKLOG，全部NOT_STARTED | [场景索引](scenarios/README.md)、[能力索引](capabilities/README.md)、09 |
| 扩展目标 | 增加现有类型场景时不修改kernel业务语义和公共表；允许增加场景/能力模块、专有表和装配声明 | 02、07 |
| 公共存储 | 10张平台公共表；通知能力另有2张专有表 | 05 |
| 首期明确延期 | MQ、cache、工作流引擎、动态插件、AI模型运行、多时区及真实外部通知渠道等 | 02、09 |

本表只用于快速核对，不能替代“正式来源”列对应文档中的完整规则。版本号、字段、状态、数量上限或业务行为改变时，必须同步修改正式来源、07验收和08实施任务。

## 4. 当前状态

| 范围 | 状态 | 含义 |
| --- | --- | --- |
| 2.3文档基线 | REVIEWED | 当前未保留已知的阻塞性文档分歧；不表示设计绝对无误或已经被代码验证 |
| 当前阶段 | [P04](phases/P04/README.md) DRAFT | S02 收件标题与正文，以及实例命令锁序；未授权不得编码 |
| P03 发布 | RELEASED（`v20260917-P03`） | 人工验收 2026-09-17；证据[DELIVERY](phases/P03/DELIVERY.md) |
| P02 发布 | RELEASED（`v20260917-P02`） | 人工验收 2026-09-17；证据[DELIVERY](phases/P02/DELIVERY.md) |
| P01 发布 | RELEASED（`v20260915-P01`） | 人工验收 2026-09-15；证据[DELIVERY](phases/P01/DELIVERY.md) |
| 实际环境 | ENV_VERIFIED | T01 实测见[T01-ENV-EVIDENCE](phases/P01/T01-ENV-EVIDENCE.txt)与 DELIVERY §1 |
| 生产能力 | OUT_OF_SCOPE | 生产部署、容量结论、可信身份实现和真实外部渠道不属于本地首期验收 |

P03已于2026-09-17人工验收通过并RELEASED（标签`v20260917-P03`）；证据见[P03 DELIVERY](phases/P03/DELIVERY.md)。当前 CURRENT 为[P04](phases/P04/README.md)（DRAFT）。P01/P02亦已RELEASED。

## 5. 实施期仍须验证

以下事项不能靠文档评审宣布通过：

- Spring AI具体稳定补丁与Spring Boot 4.0.8、Java 21、MyBatis Starter 4.0.1的实际依赖解析和启动。
- MySQL官方镜像标签/digest、数据库内部版本、Flyway迁移、CHECK/FK/唯一键、SQL执行计划和锁行为。
- G01中的Java DTO、SPI签名、12张表首版DDL和最小端口能否共同支持S01真实纵向闭环。
- 幂等、父级锁序、租约接管、旧token、外部副作用UNKNOWN、多进程竞争和优雅停机。
- A01—A42、E01—E13、I01—I07、M01—M10及性能门槛的真实结果。
- P02的包路径映射、测试目录镜像、生产类型所有者测试覆盖和无行为变化回归结果。
- Jackson 3专项阶段的依赖纯度、Mapper装配、HTTP/数据库JSON黄金契约及哈希稳定性。

验证失败时按08标记对应阶段FAIL或BLOCKED；如果原因是契约错误，先修正文档及测试，再恢复实施。不得为了维持`REVIEWED`或`READY`状态而迁就错误设计。

## 6. 文档与交付纪律

- 不保留旧版需求、旧接口、旧DDL或无编号副本作为开发输入。
- 旧项目的`WORKLOG.md`已经删除，不得恢复或作为需求、状态、进度来源。
- 阶段身份以阶段索引为唯一入口；存在 CURRENT 时，总体状态和下一动作以该阶段 README 为准；00只做摘要，二者不一致时暂停实施并先修正文档。
- 实施开始后，真实命令、退出码、测试数、环境和证据只写入当前阶段的`DELIVERY.md`；已 RELEASED 阶段的 DELIVERY 不再改写。P01 证据见[DELIVERY](phases/P01/DELIVERY.md)；P02 证据见[DELIVERY](phases/P02/DELIVERY.md)；P03 证据见[DELIVERY](phases/P03/DELIVERY.md)。
- 新增或修改英文术语、字段、状态、方法和配置时，同步维护HUMAN-GLOSSARY，但中文释义不能改变正式契约语义。
- 场景或能力状态变化时同步更新对应永久文档、索引、09和受影响阶段；没有DELIVERY证据不得标VERIFIED。
- 已执行的Flyway迁移不得原地修改；文档修改不能伪装已经完成的工程或数据升级。

## 7. 进入实施的条件

1. 必须存在唯一 CURRENT 阶段，且用户明确授权开始该阶段实施。仅确认文档、继续审核或讨论方案不构成实施授权。
2. 目标场景及所需能力项必须为READY_FOR_IMPLEMENTATION，并已写入该阶段范围。
3. 任一关键任务存在FAIL、BLOCKED或NOT_RUN时，不得进入依赖它的后续任务或宣称阶段完成。
4. 实施中发现需改变业务行为、能力、公开API、公共表或稳定扩展契约时，先修改对应文档并重新确认影响。
5. P01 已 RELEASED（标签 `v20260915-P01`）。P02 已 RELEASED（标签 `v20260917-P02`）。P03 已 RELEASED（标签 `v20260917-P03`）。当前 CURRENT 为 [P04](phases/P04/README.md)（DRAFT），未授权不得编码。S03 及以后须另选范围并授权。不得改写已冻结阶段。
