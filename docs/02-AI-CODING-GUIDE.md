# 02 · 通用任务平台架构与编码约束

> 阅读入口与阶段状态见[00开发导航](00-READING-ORDER.md)。本文是架构、模块依赖、扩展接口和Java编码规则的正式来源；当前不包含cache模块。

版本 2.3；先读[平台公共业务契约](01-MVP-SPEC.md)，下一份为[环境契约](03-INTEGRATION-CONTRACTS.md)。本文定义稳定底座、扩展边界和目标工程结构；01定义公共语义与首期范围，[场景目录](scenarios/README.md)定义场景，[能力目录](capabilities/README.md)定义可复用能力，09记录演进顺序。

## 1. 架构目标

TimeImprintTask 采用“稳定内核 + 可插拔能力”的 Java 模块化单体。所有 Maven 模块由一个 Spring Boot 应用装配和部署，不把模块拆分等同于微服务。

首期不是丢弃可靠性和扩展性的本地原型。运行身份暂时固定在本地适配器，但首期结束前必须完成下述稳定内核、扩展契约、公共存储和多进程运行机制。身份接入的简化不得渗入 kernel、TransitionPlan、持久化或 Worker；未来接入生产认证时只替换 ActorContext 接入实现和具体 Policy。

以下编号是跨阶段稳定不变量。后续阶段可以增加兼容不变量；修改或删除既有不变量属于核心模型变化，必须按08重新评审：

1. **INV-01 场景隔离**：已规划的新场景只增加场景模块、能力实现、场景专有表和装配声明，不修改`timeimprint-task-service-kernel`的业务语义和公共表结构。
2. **INV-02 通用事实**：内核只保存跨场景长期稳定的事实，包括任务身份、定义版本、实例生命周期类别、参与主体、迁移版本和审计关联。
3. **INV-03 内核无场景分支**：提醒、审批、缴费、会议等业务状态由场景定义，内核不得维护场景状态枚举或按`scenarioKey`编写分支。
4. **INV-04 统一输入输出**：时间、事件、条件和依赖统一转换为定位到单个任务定义的持久化Signal；通知、Webhook、同步和自动执行统一转换为Action Job。首期不接受无目标或广播Signal。
5. **INV-05 声明式迁移**：扩展只返回声明式`TransitionPlan`，不能直接更新公共任务表、提交事务或在状态事务中执行外部网络调用。
6. **INV-06 原子事实**：一次已应用迁移的资源状态、Transition、Action意图、Audit及场景/能力专有数据必须在同一事务全部提交或全部回滚。
7. **INV-07 并发屏障**：同步命令依靠持久化requestId与revision，异步队列依靠稳定业务键、父资源版本/代次及executionToken阻止重复效果和旧执行者回写。
8. **INV-08 接入不污染内核**：本地固定身份、HTTP、数据库和具体渠道都是外层适配；不得把环境身份、传输对象、Mapper或渠道SDK引入kernel。
9. **INV-09 模块克制**：模块数量按稳定边界和独立变化原因控制。通知渠道、日历算法和相近小场景先在所属模块内按包隔离，不默认一个实现一个Maven模块。

“新增场景不改底层”特指不修改内核语义、公共表、既有扩展契约和INV-01—INV-09。根POM、`timeimprint-task-boot-loader`装配清单、新模块迁移目录以及新增兼容契约可以变化，但必须通过架构检查与文档审核。

## 2. 当前一期模块

一期建立 13 个平级 Maven 模块：

| 模块 | 职责 | 允许直接项目依赖 |
| --- | --- | --- |
| `timeimprint-task-common` | 无业务含义的工具、基础技术类型 | 无 |
| `timeimprint-task-domain` | HTTP请求/响应DTO、统一响应信封与错误码；不含Controller或用例编排。业务领域值对象仍在`service-kernel` | common |
| `timeimprint-task-service-kernel` | 任务定义、实例、参与人、稳定生命周期和迁移计划值对象；纯 Java | common |
| `timeimprint-task-service-extension-api` | Scenario、Trigger、Command、Action、Policy 扩展契约和注册描述 | kernel、common |
| `timeimprint-task-service-application` | 用例编排、权限、幂等、事务、迁移计划校验和原子提交 | kernel、extension-api、common |
| `timeimprint-task-service-runtime` | Signal、触发规划、Action Job 领取、租约、重试和恢复 | kernel、extension-api、application、common |
| `timeimprint-task-service-storage-mysql` | Repository 实现、MyBatis Mapper/XML、查询、锁协议和平台迁移 | kernel、application、runtime、common |
| `timeimprint-task-service-capability-calendar` | 一次性、日/周/月/N 日规则和时间计算能力 | extension-api、kernel、common |
| `timeimprint-task-service-capability-notification` | 通知动作、投递、尝试、收件箱、通知策略和渠道实现 | extension-api、kernel、common |
| `timeimprint-task-service-scenario-basic` | 通用提醒、周期待办及其强类型配置、命令和投影 | extension-api、kernel、calendar、common |
| `timeimprint-task-gateway` | 接入编排：构造 ActorContext、domain DTO 与应用层转换、错误映射；不含 Controller | domain、application、kernel、common |
| `timeimprint-task-web` | HTTP Controller、过滤器、请求限制和统一异常处理 | domain、gateway、common |
| `timeimprint-task-boot-loader` | 启动、配置、模块装配、迁移加载和集成测试入口 | 选择全部运行时实现 |

一期不创建cache、独立AI能力、工作流、外部IM或未来场景模块。Spring AI只作为已批准的技术版本基线，不表示首期已实现AI业务能力。缓存能力及其工程形态暂不确定，后续根据真实性能需求单独设计和审批；不能用空接口、空Bean、空表或固定假数据声称完成了扩展能力。

## 3. 未来目标模块

未来模块按能力域和场景族扩展，而不是按每个渠道或单个场景扩展：

```text
timeimprint-task
├── timeimprint-task-common
├── timeimprint-task-domain
├── timeimprint-task-service-kernel
├── timeimprint-task-service-extension-api
├── timeimprint-task-service-application
├── timeimprint-task-service-runtime
├── timeimprint-task-service-storage-mysql
├── timeimprint-task-service-capability-calendar
├── timeimprint-task-service-capability-trigger
├── timeimprint-task-service-capability-notification
├── timeimprint-task-service-capability-collaboration
├── timeimprint-task-service-capability-workflow
├── timeimprint-task-service-capability-aggregation
├── timeimprint-task-service-capability-integration
├── timeimprint-task-service-capability-intelligence
├── timeimprint-task-service-scenario-basic
├── timeimprint-task-service-scenario-deadline
├── timeimprint-task-service-scenario-collaboration
├── timeimprint-task-service-scenario-workflow
├── timeimprint-task-service-scenario-automation
├── timeimprint-task-gateway
├── timeimprint-task-web
└── timeimprint-task-boot-loader
```

当前目标结构为23个模块，不包含cache模块。缓存仍是未来能力候选，但在实际需求和技术方案确定前不预设模块名称、依赖位置或产品实现。未来结构是边界规划，不是要求一期创建全部目录。

### 3.1 能力模块边界

本表只定义目标模块所有权；当前批准范围、未来能力项和真实实现状态以[能力域索引](capabilities/README.md)为准。

| 模块 | 聚合的能力 |
| --- | --- |
| `capability-calendar` | [calendar日历与时间](capabilities/CAP01-calendar.md) |
| `capability-trigger` | [trigger外部、条件与依赖触发](capabilities/CAP02-trigger.md) |
| `capability-notification` | [notification通知与收件](capabilities/CAP03-notification.md) |
| `capability-collaboration` | [collaboration协作与主体](capabilities/CAP04-collaboration.md) |
| `capability-workflow` | [workflow流程编排](capabilities/CAP05-workflow.md) |
| `capability-aggregation` | [aggregation聚合与统计](capabilities/CAP06-aggregation.md) |
| `capability-integration` | [integration外部系统集成](capabilities/CAP07-integration.md) |
| `capability-intelligence` | [intelligence智能理解与建议](capabilities/CAP08-intelligence.md) |

站内信、飞书、京ME、邮件首先是 `capability-notification` 内部渠道包。只有出现独立发布、重大 SDK 冲突、单独安全隔离或独立团队所有权时，才把某个渠道提取为新的适配器模块；提取前后保持同一个 `NotificationChannel` 契约。

### 3.2 场景模块边界

| 模块 | 聚合的场景 |
| --- | --- |
| `scenario-basic` | 单次提醒、周期提醒、个人待办、周期待办 |
| `scenario-deadline` | 到期、缴费、续约、保养、复查和逾期处理 |
| `scenario-collaboration` | 团队任务、会议协作、轮值、交接和资源参与 |
| `scenario-workflow` | 审批、会签、复核、退回和升级 |
| `scenario-automation` | 外部事件、条件监控、依赖联动和自动完成 |

同一场景族先按包隔离。只有独立业务数据、生命周期、所有权或发布节奏已经形成，才拆出新的平级 `timeimprint-task-service-scenario-*` 模块。

## 4. 依赖规则

依赖方向只允许向稳定契约收敛：

```text
web → gateway → application → kernel
             │          └── extension-api
             └── domain

runtime ────────────────→ application / kernel / extension-api
capability-* ───────────→ extension-api / kernel
scenario-* ─────────────→ extension-api / kernel / 已批准的 capability API
storage-* ──────────────→ kernel/application/runtime 中的存储端口
boot-loader ────────────→ 选择并装配全部运行实现
```

强制规则：

- kernel 不依赖 Spring、MyBatis、HTTP、具体能力或具体场景。
- extension-api 不依赖任何场景、能力、存储或接入模块。
- 场景模块不得依赖 storage 模块、其他场景模块或具体通知渠道。
- 能力模块不得反向依赖场景模块。
- 跨场景协作通过 Signal 或公开能力契约完成，不允许直接调用另一个场景的内部类或 Mapper。
- `boot-loader` 是组合根，可以依赖运行实现，但不得承载业务规则。
- 父 POM 用 Maven Enforcer 检查依赖收敛和禁用依赖，ArchUnit 检查生产源码边界；仅靠包命名不算隔离完成。

### 4.1 业务优先的包结构

每个Maven模块内部统一使用`<模块根包>.<业务功能>.<技术职责>[.<细分职责>]`。先回答“这个类服务哪个业务功能”，再回答“它在该功能中承担什么技术角色”。禁止新增`service.definition`、`repository.definition`、`controller.definition`这类技术分层优先的路径，应改为`definition.service`、`definition.adapter`、`definition.controller`。

| 模块 | 一级业务功能 | 允许的二级技术职责 |
| --- | --- | --- |
| `timeimprint-task-domain` | `definition`、`instance`、`signal`、`inbox`、`diagnostic`、`shared` | `request`、`view`、`response` |
| `timeimprint-task-service-kernel` | `definition`、`instance`、`participant`、`transition`、`shared` | `model`、`identity`、`state`、`revision` |
| `timeimprint-task-service-extension-api` | `scenario`、`trigger`、`command`、`action`、`policy`、`materialization`、`shared` | `spi`、`context`、`registry`、`result` |
| `timeimprint-task-service-application` | `definition`、`instance`、`signal`、`action`、`inbox`、`transition`、`extension`、`access`、`shared` | `service`、`port`、`model`、`validation`、`registry`、`limit`、`paging`、`transaction` |
| `timeimprint-task-service-runtime` | `trigger`、`signal`、`action`、`shared` | `worker`、`recovery`、`configuration`、`lifecycle` |
| `timeimprint-task-service-storage-mysql` | `definition`、`instance`、`participant`、`trigger`、`signal`、`transition`、`action`、`inbox`、`command`、`audit`、`shared` | `adapter`、`mapper`、`row`、`committer`、`configuration`、`mapping`、`time`、`transaction` |
| `timeimprint-task-service-capability-calendar` | `schedule` | `configuration`、`calculation`、`provider` |
| `timeimprint-task-service-capability-notification` | `notification`、`inapp` | `handler`、`adapter`、`mapper`、`row`、`port` |
| `timeimprint-task-service-scenario-basic` | `reminder`、`recurringtodo` | `extension`、`command`、`projection` |
| `timeimprint-task-gateway` | `definition`、`instance`、`signal`、`inbox`、`diagnostic`、`shared` | `gateway`、`mapper` |
| `timeimprint-task-web` | `definition`、`instance`、`signal`、`inbox`、`diagnostic`、`shared` | `controller`、`filter`、`error`、`configuration` |

`common`和`boot-loader`是没有业务切片的例外：`common`按`time`、`hashing`等稳定技术能力分包，`boot-loader`只允许`bootstrap`、`configuration`、`health`、`lifecycle`。不得为追求形式一致而虚构业务分类。

附加规则：

- Java目录必须与`package`完全一致；不保留空目录、视觉分组目录或第二套包路由。
- `impl`、`util`、`misc`、无边界的`model`不得作为新类的归宿；实现类放入具体的`adapter`、`handler`、`provider`或`committer`。
- MyBatis XML资源的namespace和目录必须与Mapper包同步，不得在Java类移动后保留旧包兼容壳。
- 一个生产类只承担一个主要变化原因。同时跨越请求转换、用例编排、业务校验、序列化或存储适配中三项及以上的类，必须先用特征测试固定行为，再按职责拆分。行数只是识别信号，不是单独拆分标准。
- 包移动、类拆分、业务行为变更和主要依赖升级不得混在同一个提交中。纯移动提交必须能证明除全限定类名、import、Spring/MyBatis声明外没有逻辑差异。
- 本次结构收敛不增删Maven模块，不改HTTP路径、JSON字段、表结构、SPI注册键或业务语义。

### 4.2 测试镜像与所有者测试

每个模块的`src/test/java`必须镜像本模块`src/main/java`的包路径。测试包不增加`.test`后缀，跨模块端到端测试仍位于`boot-loader`，但不能代替所属模块的所有者测试。

| 生产类型 | 必须存在的所有者测试 | 最低验证内容 |
| --- | --- | --- |
| 具体类、record、enum | `XxxTest` | 公开行为、边界、不变量或序列化契约 |
| 接口、端口、SPI | `XxxContractTest` | 签名、结果语义、实现约束或架构边界 |
| MyBatis Mapper | `XxxMysqlIT` | XML映射、参数、行映射和真MySQL结果 |
| Spring组合配置 | `XxxContextTest` | Bean数量、条件、主候选者和启动失败语义 |
| 包或模块边界 | `XxxArchitectureTest` | 允许依赖、禁止包和命名规则 |

所有顶层生产类型都必须有且只有一个所有者测试；`package-info.java`是唯一默认豁免。未来的生成代码只能通过带原因的显式白名单豁免。私有嵌套数据类由外层类测试覆盖；有独立业务行为的嵌套类必须提取为顶层类型。

构建必须自动比较生产类型和所有者测试，映射率必须为100%。测试必须包含可失败的业务、契约或架构断言；空断言、只构造对象、只检查类存在或只为提高覆盖率的测试不计为合格所有者测试。新增或修改行覆盖率不低于90%，新增或修改分支覆盖率不低于80%，模块总覆盖率不得低于迁移前记录的基线。

## 5. 五类扩展契约

| 契约 | 责任 | 明确禁止 |
| --- | --- | --- |
| `ScenarioExtension` | 场景描述、配置解码/校验、初始实例、Signal迁移、状态校验和读模型投影 | 处理HTTP命令、更新公共表、调用外部网络 |
| `TriggerProvider` | 把时间、事件、条件或依赖转换成去重 Signal | 直接改变任务业务状态 |
| `TaskCommandHandler` | 处理 complete、skip、approve、reject、assign 等强类型命令并返回 TransitionPlan | 接受未经校验的任意 Map、直接提交事务 |
| `ActionHandler` | 执行 notification、webhook、sync 等外部或内部动作 | 自行无限重试、绕过 Action Job 状态机 |
| `Policy` | 权限、频控、静默、执行资格和合规约束 | 隐式修改场景状态或伪造成功 |

扩展注册使用稳定 key、契约版本和配置 schemaVersion。重复 key、处理器缺失或版本不兼容时启动失败。扩展配置在接入边界完成强类型转换；版本升级必须提供兼容读取或显式迁移，不能让任意 JSON/Map 进入业务深处。

五类契约必须共享以下最小语义，具体Java签名在[P01实施任务](phases/P01/IMPLEMENTATION.md)的G01门槛中先以契约测试固定，再进入S01纵向实现：

- 输入是不可变、已鉴权、已完成schema解码的强类型上下文；不得把HTTP DTO、MyBatis对象或任意Map传入场景决策。
- 业务处理只返回Applied、NoChange或Rejected及声明式计划；可预期业务拒绝不得伪装成技术异常，技术异常不得伪装成业务结果。
- 决策实现默认无状态且线程安全；需要状态的数据必须来自显式输入或专有数据读取端口，不能依赖进程内可变缓存。
- 每个处理器声明支持的schema版本、所需能力和执行约束；仍被ACTIVE/WAITING/RUNNING数据引用的旧版本必须可读，否则应用不得进入就绪状态。
- ActionHandler额外声明执行模式和最大执行时间；外部客户端必须配置连接、读取和总调用超时，不能只依赖线程中断。

### 5.1 注册键与唯一数量

注册表不得靠Spring Bean名称或“找到第一个实现”路由，唯一键和数量固定如下：

| 类型 | 唯一注册键 | 数量规则 |
| --- | --- | --- |
| ScenarioExtension | `(scenarioKey, contractVersion)` | 每个已启用scenarioKey在当前contractVersion恰好1个 |
| TriggerProvider | `(providerKey, contractVersion)` | 每个已启用providerKey在当前contractVersion恰好1个；实现自行声明可读的config/payload schemaVersion集合 |
| TaskCommandHandler | `(scenarioKey, scope, commandKey, commandSchemaVersion)` | 每个声明支持的命令恰好1个；scope仅为DEFINITION或INSTANCE |
| ActionHandler | `(handlerKey, actionSchemaVersion)` | 每个仍有未终结Action引用的版本恰好1个 |
| Policy | `(policyKey, phase)` | 每键每阶段至多1个；同阶段允许多个不同key组合 |
| ScenarioDataMaterializer | `(scenarioKey, mutationKey, schemaVersion)` | 每个声明的Mutation版本恰好1个 |

`contractVersion`是Java扩展契约的大版本，一期固定为1；`schemaVersion`是持久化配置或载荷版本，两者不能混用。注册描述必须列出公开状态、命令、支持版本和所需能力；声明与实际注册不一致、重复键、缺少依赖或未终结数据版本不可读时启动失败。

### 5.2 场景与命令的责任分界

- `ScenarioExtension`不再声明可执行命令逻辑，只提供场景元数据、定义配置校验、初始实例/Signal迁移、状态合法性和投影。它必须声明公开的commandKey清单，该清单只作描述，并与注册的TaskCommandHandler逐项一致。
- `TaskCommandHandler`只处理一个明确的场景、资源层级、commandKey和commandSchemaVersion，输入是已解码命令及锁内快照，输出Applied/NoChange/Rejected。它不能处理Signal。
- `update/pause/resume/retire`是平台定义级命令，由application实现通用控制语义；其中update必须调用ScenarioExtension重新校验完整配置，不能路由到场景命令处理器。`complete/skip/snooze/approve/reject`等业务命令才路由TaskCommandHandler。
- Signal只路由当前definition的ScenarioExtension；commandKey只路由TaskCommandHandler。两条入口不互相回退，缺失处理器即确定性错误，不能尝试另一种处理方式。

### 5.3 Policy组合与结果映射

Policy阶段固定为`DEFINITION_READ`、`INSTANCE_READ`、`COMMAND_EXECUTE`、`SIGNAL_PROCESS`和`ACTION_EXECUTE`。同阶段策略按`order`升序、再按policyKey字典序执行；所有适用策略都ALLOW才可继续。首个DENY或RETRY_LATER立即停止，Policy不得修改状态或TransitionPlan。相同`order`允许存在，稳定key次序保证结果可重现。

扩展结果只允许：`Applied(plan)`、`NoChange(result)`、`Rejected(reasonCode, safeMessage)`。NoChange以HTTP 200返回既有快照且不增加revision、Transition或Action；Rejected的reasonCode必须在注册描述中声明，并由application映射到`INVALID_REQUEST`、`FORBIDDEN`、`STATE_CONFLICT`、`COMMAND_NOT_SUPPORTED`或`POLICY_REJECTED`，扩展不能直接选择HTTP状态。未声明reasonCode、空plan、非法plan、解码错误和普通运行时异常都是技术错误；同步入口映射INTERNAL_ERROR/RETRY_LATER，Signal/Action入口按06的可重试分类处理，不能伪装成业务拒绝。

## 6. 状态与事务边界

平台维护四条相互独立的状态轴：

1. 定义控制状态：`ACTIVE / PAUSED / RETIRED`，只表示是否接受新输入。
2. 实例生命周期类别：`WAITING / ACTIVE / TERMINAL`，用于通用查询、并发屏障和保留策略。
3. 场景业务状态：由场景声明，例如 `WAITING_APPROVAL / APPROVED / REJECTED`；内核只保存代码和值，不解释枚举。
4. 动作技术状态：`READY / RUNNING / RETRY_WAIT / SUCCEEDED / DEAD / CANCELLED / EXPIRED / UNKNOWN`。

Command与Signal有不同入口：同步Command先取得requestId对应的command dedup锁，再锁业务资源；异步Signal先持久化，Worker领取提交后普通读取父标识，再按definition、trigger binding、instance、Signal顺序进入业务事务。两者只从“调用场景纯计算 → 得到TransitionPlan → 校验平台不变量 → 原子写入状态、参与人、动作意图、迁移记录和审计 → 提交”开始共享实现；平台不得先把Command转换成Signal。

TransitionPlan一期只允许声明七类内容：目标资源及fromRevision、实例新生命周期/场景状态、参与人增删、待创建Action Job、后续Signal或触发绑定变化、强类型ScenarioDataMutation、安全审计摘要。缺少目标/fromRevision的Applied计划非法；不需要任何变化必须返回NoChange而不是空Applied。新增第八类内容属于扩展契约变更，必须先升级contractVersion并复审。平台有权拒绝非法计划；扩展不能绕过revision、权限、终态和所有权检查。

ScenarioDataMutation 只描述场景专有数据的业务变更，不包含任意 SQL、Mapper 名、Java 类名、脚本、URL 或事务传播方式。application 按 scenarioKey、mutationKey 和 schemaVersion 查找显式注册的 ScenarioDataMaterializer，并在同一迁移事务中调用；物化器只能写本场景拥有的表，并返回实际受影响行数供统一事务预算累计。物化失败、影响行数与声明预期不符或累计超限，必须使公共状态、Transition、Action、审计和专有数据整笔回滚。场景决策处理器保持纯计算，不能直接调用物化器或 Mapper。

application在进入事务写入前校验03规定的参与人、接收人、触发绑定、发生、Action、专有数据变更、预计变更行数、单值与TransitionPlan总载荷及事务时间上限。任何一项超限都拒绝整个请求或缩小尚未开始的后台候选批次，不能截断TransitionPlan、只提交前N项或让扩展自行拆分后绕过上限。

controlGeneration是定义暂停、恢复和退役的持久屏障。定义每次真实控制状态迁移都递增；Instance、Signal和Action保存创建时的代次，Worker最终提交必须与当前定义代次一致。历史实例命令可由场景明确放行，但旧代次异步工作不能因恢复ACTIVE而重新获得执行资格。

窗口规划只创建WAITING Instance和计划Signal；正常Action必须由实际发生的Signal或同步Command所提交的TransitionPlan创建。受控人工重驱是唯一技术派生例外：它引用原Action及原Transition，创建新Action行但不伪造业务Transition。实例TERMINAL时，仅允许其终态Transition自身声明的Action及其合法重驱继续执行，更早Transition遗留的Action统一取消。

任何 HTTP、IM、文件、模型调用或其他不可回滚副作用都不得出现在状态事务中。ActionHandler必须声明`LOCAL_TRANSACTIONAL`或`EXTERNAL`执行模式：本地模式只在Action结果事务中写能力自有表；外部模式先持久化副作用开始证据，再在事务外调用。Action Worker以MySQL租约领取，用status + executionToken条件提交结果；涉及业务迁移时另校验definition/instance revision。外部调用超时或崩溃后无法判断结果时记录`UNKNOWN`，不能直接当作失败重复发送。

## 7. 数据所有权

公共平台表固定为以下10张：

```text
tt_task_definition
tt_task_instance
tt_task_participant
tt_trigger_binding
tt_task_signal
tt_action_job
tt_action_attempt
tt_task_transition
tt_command_dedup
tt_audit_log
```

通知投递、收件箱等由通知能力拥有；审批、缴费等专有事实由对应场景模块拥有，例如 `tt_approval_instance`、`tt_payment_subject`。专有表通过 definitionId/instanceId 关联公共身份，不向公共表增加 `approvalLevel`、`paymentAmount`、`meetingRoom` 等字段。

公共平台迁移归 `storage-mysql`；场景和能力专有迁移随所属模块提供，由 boot-loader 显式加载，版本号在整个应用内唯一。迁移一旦在共享环境执行不得原地重写。

## 8. API与身份边界

通用 API 以任务定义、任务实例、命令、Signal 和 Action Job 为资源；提醒、待办等易用接口可作为通用命令的友好包装，但不得形成第二套状态模型。

网关通过 ActorContextProvider 从可信接入信息构造 `ActorContext`，包含调用主体、租户、来源、授权范围和 traceId。首期 local profile 使用配置提供的固定 tenantId 和 actorId；请求不得覆盖它们。test profile 可以提供受控测试身份切换；非 local/test 环境没有正式 ActorContextProvider 时，公开API不得进入就绪状态。请求体中的 userId 不能作为长期可信身份依据。资源归属、办理权限和管理权限由 Policy 决定；“收到通知”不自动等于“有权完成任务”。

所有写操作包含 requestId；修改已有资源时包含 expectedRevision。幂等记录绑定调用方、操作、请求摘要和首次确定结果；相同 requestId 不同内容必须冲突。

## 9. Spring、Java与交付纪律

- 使用Java 21 LTS并编译release=21；Spring AI、Spring Boot、MyBatis和MySQL版本以03当前基线为准，完成T01实测前不得宣称环境已确认。
- 项目自定义 Java 类型不使用 `Mxz` 类名前缀；按模块与职责命名即可。
- 自有应用对象使用构造器注入；第三方对象和组合装配使用 `@Bean`。事务必须经过 Spring 代理边界。
- 时间使用可注入 Clock；数据库租约使用数据库 UTC 时间。业务时间精确到秒。
- Mapper 使用 XML 参数化 SQL；动态排序使用白名单；禁止用内存仓储替代 MySQL。
- 日志关联 traceId、definitionId、instanceId、signalId、transitionId、actionJobId；租约诊断只记录`executionTokenFingerprint`，其值固定为executionToken做SHA-256后的前12位小写十六进制，不记录原始executionToken。日志不得输出正文、密码、凭据或任何原始令牌值。
- 新场景验收必须证明 kernel 源码和公共平台 DDL 零修改；若做不到，必须说明缺失的跨场景稳定语义并重新审核契约，不能静默修改底层。
- 首次进入项目读00—10；未得到用户明确实施授权前不得开始编码，也不得引入当前文档未定义的旧接口、DDL或任务计划。

## 10. 编码前原则与明确延期

首期完整核心采用“真实场景驱动稳定、发布前允许回正、发布后兼容演进”的原则：

1. 先让S01 ONCE以最终MySQL表、HTTP、Signal、TransitionPlan、Action和inbox跑通最小纵向链路，再完成全部通用能力；不得用内存仓储或一次性旁路代码制造假闭环。
2. T02—T06期间，S01/S02真实链路发现契约不合理时可以回到前序任务修正，并同步文档和回归测试。首期发布门槛通过后，既有扩展契约和公共表才视为稳定基线。
3. 对已经出现的业务变化建立最小稳定接口；对只存在于路线图、没有实际输入输出和失败样例的能力，不提前建立空SPI、万能配置或占位表。
4. 场景schemaVersion用于持久化配置/载荷兼容；同一构建制品内的Java接口不额外模拟远程插件协商。只有独立部署或动态加载获得批准后，才设计更复杂的契约协商。
5. 先保证正确性、幂等、可恢复和可诊断，再以真实执行计划及性能证据决定缓存、MQ或拆服务；不得以未来可能需要为理由增加当前写路径。

首期明确不设计或实现：MQ、cache/JimDB、工作流引擎、动态加载插件、AI模型运行、多时区、农历、企业工作日历、通用补偿框架、跨系统事务、任意广播Signal、生产级归档冷热分层以及未来23模块的内部接口。09只保留其需求与重新评审入口；上述延期项不得出现在首期生产依赖、Bean、表、配置或“暂未使用”的代码中。
