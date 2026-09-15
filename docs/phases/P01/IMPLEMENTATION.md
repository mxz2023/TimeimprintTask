# P01 · 第一期实施任务与阶段门槛

> 当前阶段、范围、状态和授权门槛见[P01阶段入口](README.md)，全局规则见[08实施治理](../../08-AI-IMPLEMENTATION-TASKS.md)。本文是第一期T01—T08实施范围、顺序和任务状态的正式来源。

阶段P01；核心文档基线2.2；[场景目录](../../scenarios/README.md)，[能力目录](../../capabilities/README.md)，[验收](../../07-ACCEPTANCE.md)，[演进路线](../../09-SCENARIO-ROADMAP.md)。首期运行使用本地固定身份，但交付目标是完整稳定核心；任务按可运行证据递增，不以“创建了模块或接口”作为完成证据。

## 1. 总体顺序与状态

```text
T00 文档准备（READY）
  → T01 13模块与环境基线
  → G01 编码契约冻结门槛
  → T02 S01 ONCE最小纵向闭环
  → T03 稳定内核、扩展契约与MySQL基础
  → T04 通用命令、Signal与API
  → T05 完整日历、运行时与S01
  → T06 S02业务闭环
  → T07 扩展通用性、恢复与双进程
  → T08 全量验收与交付
```

| 任务 | 当前状态 | 转为通过的必要条件 |
| --- | --- | --- |
| T00 | READY | 当前没有已知的阻塞性文档分歧，具备开始T01的文档条件；不表示设计绝对无误或已被代码验证 |
| T01 | PASS | `./mvnw -q test`与`./mvnw -q package`通过；环境证据见[T01-ENV-EVIDENCE.txt](T01-ENV-EVIDENCE.txt)；MySQL锁定`mysql:9.7.2` digest |
| T02 | PASS | S01 ONCE 真HTTP+真MySQL闭环：`S01OnceMysqlIT` PASS |
| T03 | PASS | 12表Mapper/提交器/锁序/租约领取已落地；kernel ArchUnit与Flyway information_schema断言 PASS |
| T04 | PASS | 本地ActorContext、E02/E03/E04/E06/E07/E09/E10—E13/I01及统一TransitionPlan提交管道已验证 |
| T05 | PASS | 五种日历算法单测 + S01 ONCE闭环 + Signal/Action/Planner Worker；E02五规则预览矩阵 `CalendarFiveRulesPreviewMysqlIT` PASS |
| T06 | PASS | S02 recurring_todo：`S02BasicMysqlIT`/`S02RecurringTodoMysqlIT` PASS（complete/skip） |
| T07 | PASS | 三夹具 + kernel/DDL零变更断言 + `DualClaimMysqlIT`（SKIP LOCKED互斥）PASS；完整双JVM进程仍待扩展 |
| T08 | IN_PROGRESS | DELIVERY已创建；双进程局部与S01/S02证据已记录；A01—A42全矩阵与性能门槛仍为NOT_RUN |

T00使用READY表示文档具备实施条件；T01—T08状态只允许NOT_STARTED、IN_PROGRESS、PASS或BLOCKED。不得用空实现、固定假数据、跳过测试、占位异常或TODO转PASS。环境或契约问题使关键证据无法取得时标BLOCKED，不得跳过进入下一阶段。

获得实施授权后，T01—T07在技术门槛PASS时可自动衔接；T08只能依据真实证据判定完成。生产部署、真实飞书/京ME/邮件发送和允许名单外数据库操作仍需另行授权。

## 2. 各阶段实施契约

### T00 · 文档收敛

输入00—10、S01/S02、calendar/notification能力契约及本阶段README。产物为完整2.2开发契约、P01阶段包和当前状态；旧项目`WORKLOG.md`不得恢复或参与阶段判断，真实实施证据只写入本目录未来的`DELIVERY.md`。

门槛：模块名、场景/能力归属、API编号、数据表、状态、时间、幂等、锁序、用例编号和实施依赖无冲突；待决事项不得偷换成已批准实施项。

T00已确认S02的dueAt、提醒时点、snooze、接收人、暂停/修改/退役规则，以及Command/Signal边界、父级优先锁序、controlGeneration、事务规模上限、停机追赶、最小人工重驱和分阶段实施原则；并固定日历/更新/读模型、SPI路由、完整表定义、网络与就绪边界。T00 READY不等于工程实施授权。

### T01 · 13模块与环境基线

允许范围：父POM、Maven Wrapper、13个子模块的最小可编译结构、boot-loader配置、测试基础和README。

- 生成02确定的13个平级模块，无cache模块；设置Java 21 LTS、Spring Boot 4.0.8、MyBatis Starter 4.0.1和统一revision，并在父POM固定实施时选定的Spring AI 2.0.x稳定补丁版。
- 写入任何Java或Flyway文件前，在P01 README记录不可变baselineGitRef；仓库尚不能形成提交时，生成并保存核心文档、kernel生产源码和公共Flyway目录的SHA-256基线清单，供T07零底层变更检查和T08交付复现。
- 配置Maven Enforcer、ArchUnit和依赖规则。空模块只是基线产物，不代表业务能力完成。
- 分离无库单元测试与mysql-it/dual-process-it；固定03指定的MySQL镜像标签与digest，分别记录`SELECT VERSION()`和`@@version_comment`，校验测试库允许名单、UTC和隔离级别。
- local/test必须只监听127.0.0.1；配置仅开放liveness/readiness的Actuator健康端点并验证优雅停机顺序。
- 环境不可用时保留可编译产物并标BLOCKED，不用H2代替锁和迁移证据。

门槛：`./mvnw -q test`、`./mvnw -q package`通过；有实际JDK/Maven/MySQL证据；13个模块与02一致。

### G01 · 编码契约冻结门槛

G01不是独立交付阶段，不单独标PASS；它是T02开始业务实现前必须完成的一组可编译契约。目的只是消除T02依赖尚未定义的T03接口这一循环，不要求先把全部内核实现完。

- 在domain模块固定04全部请求/响应字段、null/省略规则、枚举与错误码的编译契约测试。
- 在kernel/extension-api固定02六类注册键、不可变输入、Applied/NoChange/Rejected、TransitionPlan/ScenarioDataMutation值对象的Java签名；项目自定义类型不使用Mxz类名前缀。
- 在storage-mysql先提交05全部12张表的首版Flyway DDL及information_schema断言；T02不得使用缩减临时表，T03只补Mapper、锁协议和未覆盖约束，不重新发明表结构。
- 固定最小Repository端口、TransitionPlan提交器端口、Clock与事务边界，使S01只能沿最终链路接入；端口不得包含reminder专用方法。
- 对上述契约做一次交叉审查：每个字段有唯一所有者、每个注册键有唯一实现、每个写结果有错误映射、每个持久化状态有读模型。任何未决项先回文档，不在S01代码中临时决定。

G01产物可以在T02真实闭环反馈后于首期发布前修正，但每次修正必须同步契约测试、DDL迁移规则和文档；已经执行过的Flyway文件不得原地改写。

### T02 · S01 ONCE最小纵向闭环

主要模块：按真实链路使用13个正式模块中需要的部分，不建立内存仓储或临时旁路。

- 先用S01 ONCE打通E02预览、E03创建、E04/E05查询、7天窗口中的WAITING实例与时间Signal、Signal触发的TransitionPlan/Action、站内信和E10—E13查询/已读；创建窗口时必须断言尚无Action。
- 使用G01已固定的12张正式表、HTTP DTO、SPI签名、真实事务和稳定键；只实现该闭环必须的最小应用行为和处理器，不删减表字段，不用固定假数据、同步直写收件箱或“以后替换”的临时协议。
- 同步命令取得command dedup锁后再锁业务资源；时间Signal独立持久化和异步处理，处理时按父级到子级锁定，两者只共享TransitionPlan校验与提交器。
- 先验证单进程下同requestId重放、时间Signal重放、Action重放和整笔事务回滚。

门槛：真HTTP与真MySQL可重复完成一条S01 ONCE闭环，且定义、实例、Signal、Transition、Action、通知、收件和审计均可核对。T02的接口仍可在首期发布前根据真实闭环回正，不提前宣称稳定核心已经冻结。

### T03 · 稳定内核、扩展契约与MySQL基础

主要模块：`timeimprint-task-common`、`timeimprint-task-domain`、`timeimprint-task-service-kernel`、`timeimprint-task-service-extension-api`、`timeimprint-task-service-application`、`timeimprint-task-service-storage-mysql`和`timeimprint-task-boot-loader`。

- 基于T02反馈完整实现TaskDefinition、TaskInstance、Participant、Signal、Action、TransitionPlan、revision、controlGeneration和终态不变量；如需修正G01契约，先同步文档和契约测试。
- 完整实现ScenarioExtension、TriggerProvider、TaskCommandHandler、ActionHandler、Policy五类行为，以及ScenarioDataMutation、ScenarioDataMaterializer、不可变强类型输入、Applied/NoChange/Rejected结果、业务/技术错误分类、支持版本声明、显式注册表和重复key启动失败；未终结持久化版本不可读时应用不得就绪。
- 在G01全量DDL上完成12张表的Mapper/XML、存储端口、command dedup、Signal/Action唯一键与重驱父链、Transition CAS、leaseOwner/lease/token、父级优先锁序、完整事务重试和03规定的对象数/字节数/总变更行数/事务超时上限。
- 在03固定标签与digest的官方MySQL 9.7.2镜像上执行空库迁移、重复启动、坏迁移、information_schema、CHECK/FK/唯一键、JSON、UTC整秒和EXPLAIN验证；数据库报告版本按03单独记录。用ArchUnit阻止Mapper、实现类和反射越界。
- 不建立空的未来场景表、未来SPI或只为路线图候选准备的配置。

门槛：kernel保持纯Java且无scenarioKey特例；五类契约和专有数据物化契约测试通过；真MySQL结构、锁序、原子回滚和A25、A27—A29边界PASS。发现T02抽象错误时允许修正并完整回归。

### T04 · 应用编排、Signal和通用API

主要模块：`timeimprint-task-service-application`、`timeimprint-task-gateway`、`timeimprint-task-web`、`timeimprint-task-boot-loader`；使用T02契约与T03存储。

- 实现ActorContextProvider、local固定身份适配器、ActorContext、Policy、TransitionPlan校验/提交器、定义/实例命令管道、Signal接收/领取/处理和审计；test profile保留受控身份切换，其他profile没有正式身份提供器时公开API不就绪。
- 落地E01—E09、I01—I07以及统一响应、弱一致keyset分页、错误、大小边界、schemaVersion、幂等、归属和防泄露契约；I01入READY前必须唯一解析目标，I06/I07只创建符合限制的关联重驱行。
- 暂无实际场景处理器的路径返回正确EXTENSION_NOT_FOUND或COMMAND_NOT_SUPPORTED，不返回假成功。
- 明确Command是同步业务意图、Signal是持久化异步事实；分别验证各自幂等键和失败语义，禁止先把Command伪装成Signal再处理。
- 本阶段不加入扩展夹具；先完成E01—E09、I01—I07和通用提交管道。

门槛：同步命令和Signal只共享一个TransitionPlan提交器；幂等与revision并发用例PASS；HTTP不泄露内部token或敏感payload；应用边界不包含S01/S02特例。

### T05 · 完整日历、运行时与S01

主要模块：`timeimprint-task-service-capability-calendar`、`timeimprint-task-service-runtime`、`timeimprint-task-service-application`、`timeimprint-task-service-storage-mysql`。

- 实现ONCE、DAILY、WEEKLY、MONTHLY、EVERY_N_DAYS的强类型配置、预览、7天窗口、每绑定每轮100条、游标和scheduleGeneration。
- 实现Planner的普通候选扫描与definition→trigger锁定、Signal Worker的父级优先锁定、Action Worker的有界领取、固定排序、连接池/线程池反压、租约回收和公平性。
- 预览与规划共用纯日历算法；覆盖月末、闰年、锚点、时区、严格after和规则耗尽。
- 按[S01场景契约](../../scenarios/S01-reminder.md)完成PLANNED/TRIGGERED/CANCELLED状态、全部五类日历规则和通知闭环，保证Action只由实际Signal迁移生成且终态迁移自身Action可执行；补齐修改、暂停、恢复、退役、controlGeneration隔离和非暂停停机追赶。

门槛：M01—M05和S01相关A用例PASS；日历纯算法全覆盖；同一Action最终只有一条站内信；T02的ONCE闭环在完整运行时下继续通过。

### T06 · S02业务闭环

主要模块：`timeimprint-task-service-scenario-basic`，并补齐application/runtime/notification已定义的通用机制；不得为S02在平台写特例分支。

- 实现recurring_todo、PLANNED/PENDING/COMPLETED/SKIPPED/CANCELLED、complete/skip/snooze、0—3个CHASE、历史快照和实例独立；系统取消不得借用SKIPPED。
- 实现[S02场景契约](../../scenarios/S02-recurring-todo.md)确定的默认配置、有效期、接收人回退、snooze整体平移与批次重建、同目标NoChange和相反终态冲突。
- 实现update/pause/resume/retire、恢复水位、controlGeneration、未来WAITING重建、ACTIVE/TERMINAL快照保留、终态取消和暂停/退役后的命令边界。
- 完成M06—M10、A05、A06、A12、A16、A21—A24和S02相关API回归；技术重试不得增加业务槽位。

门槛：S02五类规则、默认和边界配置、并发命令、接收人、暂停/退役行为在真MySQL下PASS；S01/S02共用平台机制，kernel和公共DDL不得出现S02专有字段或分支。

### T07 · 扩展通用性、恢复与双进程

主要模块：测试源集及application/runtime/storage的通用恢复机制；不得为测试夹具建立生产Maven模块。

- 在平台生产源码和公共DDL基线完成后加入ApprovalFixture、EventTriggerFixture和WebhookActionFixture；ApprovalFixture通过ScenarioDataMutation和注册物化器写测试专有表。
- 加入前后以版本库diff，或在无版本库时以SHA-256清单，证明kernel生产源码和公共Flyway DDL零变更；三个夹具只能经稳定契约装配。
- 完成退避、租约回收、DEAD/EXPIRED/UNKNOWN、Signal及LOCAL_TRANSACTIONAL Action的最小人工重驱、Handler超时预算、Policy阻断计数返还、审计原因优先级和公平性。
- 双进程验证规划竞争、完成与通知、pause/resume与Signal/Action、异步清理延迟、进程中断接管、旧token、外部effectStartedAt、停机积压追赶和公平性。
- 完成A01—A42、E01—E13、I01—I07整体回归。

门槛：`./mvnw -Pmysql-it,dual-process-it verify`真实执行并PASS；三个夹具的专有数据与公共事实同事务提交/回滚；不得用手工删除RUNNING、改计数或单进程模拟冒充恢复。

T07同时是首期核心稳定门槛：重新运行S01、S02和三个扩展夹具后，kernel不得出现首期场景特例，公共DDL不得包含场景专有字段，后续同类场景应只增加扩展、能力、专有数据和装配。T02—T06期间真实纵向闭环发现契约缺陷时可以回到前序阶段修正，但必须同步文档和回归证据，不能为了维持阶段状态冻结错误抽象。

### T08 · 全量验收与交付

本阶段不新增业务范围。产物为可运行包、项目README、环境变量与启动说明、迁移/升级说明、API示例、恢复手册和`docs/phases/P01/DELIVERY.md`。首期完成必须覆盖完整稳定核心，不得只交付本地身份下能运行的S01/S02特例。

- 执行07的六层验收、三次正式性能测试、积压公平性和双进程中断接管。
- 保留命令、退出码、测试数、环境、证据路径和所有重试；自动核对全部M/A/E/I编号。
- 核对场景/能力索引和09的全部未实施范围及C14 cache待决需求，不将其误标完成或删除。
- 任一项为FAIL、BLOCKED、NOT_RUN或证据路径缺失，整体不得宣称完成。

门槛：07第7章全部命令真实通过；报告可复现；结论只覆盖本地后端，不冒充生产容量、生产身份或真实外部渠道验证。

## 3. 实施期执行规则

每个子任务启动前必须写明任务ID、允许修改的模块/文件、对应契约、用例编号和验证命令。先写失败测试或约束检查，再写最小实现，最后运行本阶段全部回归。

同一业务规则只保留一个权威实现。发现已批准文档冲突、扩展契约无法表达需求，或必须修改kernel/公共DDL才能增加测试夹具时，立即标BLOCKED并记录冲突、影响和修正方案，不得静默选择。

不得覆盖用户已有修改；不得在未授权时部署、发送外部消息、写真实凭据、升降03锁定的版本或扩大数据库范围。新发现的一期外场景写入09，不自动变成当前实施范围。

完成记录必须包含：任务ID、契约/用例ID、修改文件、命令、退出码、测试数、PASS/FAIL/BLOCKED/NOT_RUN、证据路径、实际环境、剩余问题和已解锁依赖。
