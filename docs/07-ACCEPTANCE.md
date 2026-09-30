# 07 · 验收与证据正式契约

> 阅读入口与阶段状态见[00开发导航](00-READING-ORDER.md)。本文是实现完成标准、测试环境和证据要求的正式来源；文档评审不等于代码或测试通过。

版本2.3；前：[运行协议](06-SCHEDULING.md)，后：[实施治理](08-AI-IMPLEMENTATION-TASKS.md)，当前任务见[P03实施计划](phases/P03/IMPLEMENTATION.md)。P01结果已冻结在[P01 DELIVERY](phases/P01/DELIVERY.md)；P02结果已冻结在[P02 DELIVERY](phases/P02/DELIVERY.md)；P03的J01—J10在真实执行前均为NOT_RUN。

## 1. 验收分层与环境

| 层级 | 验证对象 | 必须使用的环境 |
| --- | --- | --- |
| Unit | 时间计算、强类型解码、Policy、场景迁移、退避 | 固定或可推进Clock；无Spring、无真库 |
| Architecture | 13模块依赖、扩展SPI、禁止跨边界引用 | Maven Enforcer、ArchUnit和依赖报告 |
| Contract | E01—E13、I01—I07、DTO、错误、幂等、安全 | 启动后的真HTTP边界 |
| MySQL IT | DDL、索引、事务、锁、租约、恢复 | 03固定标签与digest的官方MySQL 9.7.2镜像；禁止H2代替 |
| Dual-process IT | 两节点竞争、崩溃、旧token和公平性 | 同一制品、同一数据库、两个Java进程 |
| Performance | 到期处理、收件可查、积压恢复 | 真时钟、真MySQL、两进程 |

集成测试使用`*IT`命名，`mysql-it`绑定Failsafe的integration-test与verify阶段，`dual-process-it`在其上启动两进程。两个profile在配置缺失、测试数为0、子进程未就绪或证据未生成时必须失败，不得静默跳过。

真库测试默认只允许`timeimprint-task_test`；其他专用测试库必须在测试配置明确列入允许名单。测试不得执行CREATE DATABASE、DROP DATABASE、TRUNCATE或Flyway clean。每次生成唯一runId，只删除由该runId记录的根资源及子表数据，不得无条件或模糊删除。

受控交错、故障注入和可推进Clock只能存在于测试源集/配置，不得暴露为生产HTTP端点或装入生产制品。所有报告和日志必须脱敏。

## 2. 架构通用性的必须证明

除13个正式模块的边界检查外，必须在测试源集实现三个最小扩展夹具；它们不是一期业务交付，不创建生产Maven模块：

| 扩展夹具 | 要证明的能力 | 通过标准 |
| --- | --- | --- |
| ApprovalFixture | 新场景、审批人参与角色、approve/reject命令、自有状态和专有表 | 只依赖extension-api稳定契约，通过统一命令和TransitionPlan提交，并由注册的ScenarioDataMaterializer写测试专有表 |
| EventTriggerFixture | 外部事件触发 | 通过I01受理和Signal Worker处理，不在kernel中增加事件分支 |
| WebhookActionFixture | 外部网络Action和UNKNOWN | 声明EXTERNAL模式，验证effectStartedAt、幂等键、超时和不自动重发 |

扩展证明的硬门槛：

- INV-01—INV-09每项都必须至少对应一个可重复的架构、契约或真库测试；P01交付报告逐项列出测试和结果，不能只引用本段文字。
- 实现三个夹具时，`timeimprint-task-service-kernel`生产源码和公共Flyway DDL的变更数均为0。
- 夹具不能引用公共Mapper、runtime实现类或其他场景内部类，不能通过Spring Bean名/反射绕过契约。
- ApprovalFixture的专有数据与公共状态必须同事务成功或回滚；其物化器只能写测试场景自有表，不能修改公共DDL或公共表。
- Maven依赖无环；common无业务状态；api不依赖实现；kernel不依赖Spring、MyBatis、web、storage或任何scenario/capability。
- ArchUnit或等价源码检查必须证明kernel纯Java与模块边界；项目自定义类型不使用`Mxz`类名前缀。
- 平台不得按`reminder`、`recurring_todo`、`approval`等scenarioKey编写if/switch；扩展通过显式注册表装配并在启动时检测重复key。

任一条失败即表示“新场景不改底层”尚未证明，不能用S01/S02自身可运行替代。

### 2.1 P02工程结构与测试镜像验收

以下项目只在P02真实实施后判定；当前均为NOT_RUN：

| 编号 | 验收对象 | 通过标准 |
| --- | --- | --- |
| Q01 | 业务优先包结构 | 13个模块全部符合02的业务功能→技术职责映射，不存在未经批准的平铺包或顶层`impl/util/misc` |
| Q02 | 路径与声明 | 每个Java文件路径与`package`一致；MyBatis XML namespace与迁移后的类型一致；无空目录或旧包转发壳 |
| Q03 | 测试镜像 | 每个测试文件的包与被测生产类型完全一致；测试类型只使用规定后缀，测试代码不集中在boot-loader代替模块内测试 |
| Q04 | 所有者测试 | 除`package-info.java`外，每个顶层生产类型恰有一个可识别所有者测试；映射覆盖率100%，其他豁免均有审核理由 |
| Q05 | 测试有效性 | 所有者测试具有业务、契约、边界或失败路径断言；禁用、空方法、仅非空、仅启动上下文或复制实现算法不计入 |
| Q06 | 职责拆分 | 多职责大类先有特征测试再拆分；拆分按业务责任和变化原因，公开入口、事务、SQL、Bean和序列化行为不变 |
| Q07 | 覆盖与架构门禁 | 新增/修改行覆盖率≥90%、分支覆盖率≥80%，各模块总覆盖率不下降；模块依赖、kernel纯Java和扩展边界检查通过 |
| Q08 | 无行为变化 | P01全部单元、契约、真MySQL、双进程、API/DDL/SPI黄金对比通过；Maven模块、公共DDL、公开API和稳定SPI差异为0 |

包移动、职责拆分和行为改变不得放在同一提交。Q01—Q04必须由可重复的自动化检查产生失败结果，而不只依赖人工目录观察。

### 2.2 Jackson 3专项验收

以下项目属于[P03](phases/P03/README.md)（X05）专项阶段；当前均为NOT_RUN：

| 编号 | 验收对象 | 通过标准 |
| --- | --- | --- |
| J01 | 版本与BOM | 使用Spring Boot 4.0.8 BOM管理的Jackson 3.1.5，不覆盖版本属性、不使用动态版本 |
| J02 | 依赖纯度 | 业务/一方直接依赖与源码不含Jackson 2 core/databind/datatype；仅允许annotations，以及Flyway传递的唯一Jackson 2残留（不得被业务引用） |
| J03 | 包名与直接依赖 | databind/core/dataformat引用全部使用`tools.jackson`；直接使用Jackson类型的模块声明直接依赖，注解继续使用`com.fasterxml.jackson.annotation` |
| J04 | Mapper装配 | 生产代码统一注入不可变`JsonMapper`；全局策略由boot-loader单点配置，无运行期可变配置和无理由的额外Mapper |
| J05 | MVC转换器 | 使用Boot自动配置的Jackson 3转换器；不存在`MappingJackson2HttpMessageConverter`或Jackson 2强制替换逻辑 |
| J06 | HTTP黄金契约 | E01—E13、I01—I07以及未知字段、重复键、非法类型、尾随内容、空值、枚举、时间、数字和错误信封与P01兼容 |
| J07 | 持久化JSON | P01产生的配置、快照、Signal、Action、TransitionPlan和幂等响应可读；新写内容满足03/04格式且可稳定重放 |
| J08 | 哈希与幂等 | 所有JSON摘要和Action Key使用显式规范化，迁移前后`request/config/snapshot/payload`哈希及幂等判定稳定 |
| J09 | 场景与运行回归 | S01/S02、日历、通知、Worker、恢复、真MySQL和双进程测试全部通过，无Bean歧义或启动退化 |
| J10 | 迁移收口 | 源码、依赖树、打包制品和配置扫描均无Jackson 2核心残留；不存在双Mapper临时桥接或未记录豁免 |

若Jackson默认行为与P01黄金契约不同，先以显式配置恢复兼容；确需改变公开行为时，必须退出专项迁移并按08重新评审公共API，不得把差异直接接受为升级结果。

## 3. 一期纵向场景矩阵

固定基准为北京时间2026-09-08 08:00:00，本地执行时间为09:00:00。S01与S02均覆盖ONCE、DAILY、WEEKLY、MONTHLY、EVERY_N_DAYS：

| 用例 | 场景/规则 | 首次发生（北京时） | 关键断言 |
| --- | --- | --- | --- |
| M01 | S01 / ONCE：2026-09-09 | 2026-09-09 09:00 | 一个实例、一个INITIAL，规则耗尽 |
| M02 | S01 / DAILY | 2026-09-08 09:00 | 第二次为9月9日 |
| M03 | S01 / WEEKLY：周五 | 2026-09-11 09:00 | 第二次为9月18日 |
| M04 | S01 / MONTHLY：31日 | 2026-09-30 09:00 | 月末回退但快照仍为31 |
| M05 | S01 / EVERY_N_DAYS：9月1日起3天 | 2026-09-10 09:00 | 不以重启或完成时间重置锚点 |
| M06 | S02 / ONCE | 2026-09-09 09:00 | occurrenceAt=dueAt；INITIAL在09:00，默认CHASE在10:00、13:00、21:00，次日09:00失效 |
| M07 | S02 / DAILY | 2026-09-08 09:00 | 上一实例未完成不阻断下一次，各自按默认时点提醒 |
| M08 | S02 / WEEKLY | 2026-09-11 09:00 | 每个occurrence实例、槽位、actionGeneration和接收人独立 |
| M09 | S02 / MONTHLY：31日 | 2026-09-30 09:00 | 跨大小月与闰年正确，dueAt和规则快照不漂移 |
| M10 | S02 / EVERY_N_DAYS | 2026-09-10 09:00 | N=1和N>1都有覆盖，未完成实例不改变周期锚点 |

每个M用例都须走E02预览→E03创建→E04/E05查询→时间Signal→Action→E07/E08/E10—E13查询。先断言窗口期只有WAITING实例和计划Signal、没有Action；再断言时间Signal迁移后Action、Transition、通知和收件唯一，且参与人/接收人一致。全程断言7天窗口、occurrenceKey、规则快照和controlGeneration。超出7天的M04/M09先断言未物化且游标正确，再推进窗口。

S01在通知Action意图提交后显示“提醒已触发”，不将收件生成或已读当作业务终结条件。S02默认1个INITIAL和3个CHASE，默认催办偏移为60/240/720分钟、通知有效期1440分钟、最多snooze 3次；技术重试不增加业务槽位或收件数。

## 4. 状态、故障和并发必测项

| 编号 | 操作/故障 | 必须结果 |
| --- | --- | --- |
| A01 | 预览边界、月31日、闰年、N日锚点、JVM默认时区改变 | after严格排除；自然日和锚点不漂移；结果不受JVM时区影响 |
| A02 | 创建中间失败、响应丢失、同requestId并发与重试 | 定义/窗口/历史/幂等全部回滚，或返回首次COMPLETED结果；数据库无已提交PROCESSING占位，同键竞争不形成反向锁 |
| A03 | 两进程同时规划相同发生 | 只有一组instance/signal计划事实且没有预建Action，游标单调；Signal迁移后才有唯一Action |
| A04 | Signal领取后崩溃，旧Worker恢复回写 | 租约到期后可接管；旧token CAS为0；只有一条迁移链 |
| A05 | complete、skip、snooze并发与重放 | 同目标不同requestId返回NoChange且不增revision；相反终态冲突；skip原因必填且符合长度；snooze原批次取消、新批次等量、整体平移，不移动RUNNING/SUCCEEDED且不复活DEAD/EXPIRED |
| A06 | complete与站内通知同时提交 | 固定锁序得到唯一先后；终态后未开始Action取消；已成功收件保留 |
| A07 | pause与Signal同时提交 | pause先提交则Signal不迁移；Signal先提交则保留已提交事实 |
| A08 | pause与EXTERNAL Action同时提交 | pause先则无调用；effectStartedAt先则调用可继续并记录结果/UNKNOWN |
| A09 | 外部Action调用前、调用中和返回后分别崩溃 | 未开始可重试；effectStartedAt非空但不能确认时转UNKNOWN且不自动重发 |
| A10 | LOCAL_TRANSACTIONAL在效果写入后CAS前失败 | 效果、Attempt和Action结果全部回滚，可安全重试，最终一条inbox |
| A11 | 修改时间规则与旧批次Worker同时提交 | 历史快照不变；旧未来事实取消；旧generation/revision不能越过屏障 |
| A12 | 暂停跨周期再恢复 | 暂停区间不补发；恢复后从下一合法发生继续；预生成与未生成的业务结果一致 |
| A13 | 同requestId不同内容、同revision不同requestId | 前者IDEMPOTENCY_CONFLICT；后者只一个迁移成功 |
| A14 | test profile尝试跨tenant/跨参与人读写，并在local profile尝试通过请求覆盖固定身份 | 查询不串数，命令被Policy拒绝，不泄露资源是否存在；local固定tenant/actor不被请求改变 |
| A15 | 死锁、锁等待超时、结果提交失败 | 副作用前只完整事务重试最多3次；无部分状态；外部副作用开始后不重放调用 |
| A16 | 连续可重试失败、Policy阻断、expiresAt恰到界 | 5/30/120/600秒退避，最多5次；只有副作用前Policy阻断退还计数；到界EXPIRED |
| A17 | 收件重复执行、标记已读重放、同时分页 | 收件唯一；readAt保留首次；同秒以ID稳定排序且不重不漏 |
| A18 | 非法schemaVersion、未知字段、时间小数秒、过去/=当前的ONCE | 04规定的错误；不落业务数据或成功幂等结果 |
| A19 | Flyway重复启动、损坏迁移、非法CHECK/FK/唯一键 | 重复启动安全；损坏迁移不就绪；真MySQL拒绝非法数据 |
| A20 | 两进程一个领取后终止，另一个接管 | 在规定租约窗口内完成；旧token无权回写；最终只有一份本地效果 |
| A21 | S02配置缺省、边界和非法组合 | 缺省得到60/240/720、1440和3；偏移严格递增且小于有效期；0—3项、有效期60—10080分钟、maxSnoozeCount 0—3；越界或重复均拒绝且不落数据 |
| A22 | 只有OWNER、显式RECIPIENT、重复接收人和超过10人 | 无RECIPIENT时向OWNER；有RECIPIENT时不隐式通知OWNER；最终接收人去重且不超过10；超限整笔拒绝 |
| A23 | PAUSED/RETIRED定义下操作既有S02实例 | 暂停或退役前的ACTIVE/PENDING仍可complete/skip，snooze被拒绝；WAITING/PLANNED迁移为TERMINAL/CANCELLED、未来Signal为IGNORED、未开始Action为CANCELLED；恢复不补暂停积压 |
| A24 | 修改内容、参与人、S02配置或时间规则 | 只重建未来WAITING快照和未开始Action；ACTIVE/TERMINAL快照不变；时间规则增加scheduleGeneration并立即重建7天窗口 |
| A25 | 请求达到和超过事务规模上限 | 等于上限可完整提交；超过任一上限返回INVALID_REQUEST且定义、实例、Signal、Action、Transition、专有数据和幂等成功结果均无部分写入；不得截断或异步补齐 |
| A26 | S01时间Signal使实例进入TERMINAL并生成INITIAL Action | 同一终态Transition生成的Action可以执行并产生一条收件；此前Transition遗留的未终结Action被取消 |
| A27 | Planner、Signal、pause/update按受控交错并发 | Planner使用definition→trigger，Signal使用definition→trigger→instance→Signal；同类多行按主键升序，不形成已知反向锁，死锁重试仍只产生一套事实 |
| A28 | pause后清理未完成即resume并与旧Worker并发 | pause/resume各增加controlGeneration；新代次可立即重建，旧Signal/Action被忽略或取消，旧清理不影响新代次 |
| A29 | JSON、TransitionPlan、总变更行数和事务超时边界 | 等于65536字节/1048576字节/2000行可提交，任一超过均整笔拒绝；业务事务超过配置超时整笔回滚 |
| A30 | S01/S02主体与接收人边界 | 两场景都要求OWNER并应用RECIPIENT回退；公共API拒绝非USER；local拒绝非固定Actor，多身份仅test可模拟 |
| A31 | 非暂停状态下停机跨过多个发生与通知有效期后恢复 | Planner从原cursor有界追赶不跳过；S01保留历史已触发事实，S02保留PENDING实例；过期Action直接EXPIRED且不产生陈旧收件 |
| A32 | DEAD Signal与Action人工重驱、并发重放和越权类型 | I06/I07的新行始终指向正常根行，全部历史行不变，requestId可重放、每根最多3次；仅当前controlGeneration且仍有业务资格的DEAD Signal和DEAD LOCAL_TRANSACTIONAL Action允许，跨代次、EXTERNAL/UNKNOWN拒绝 |
| A33 | Handler超时预算与超长/敏感业务身份 | handler timeout不超过租约减安全余量，客户端各层超时受预算约束；actionKey为用途前缀+SHA-256 Base64URL且稳定、定长、不泄露接收人 |
| A34 | 定义/实例游标分页期间并发更新 | 结果为明确的弱一致keyset分页；asOf仅为响应生成时间，不宣称快照；客户端从首页刷新可收敛，无重复游标循环 |
| A35 | 扩展返回NoChange/Rejected、抛技术异常及卸载旧schema读取器后重启 | 三类结果与业务/技术错误映射明确且不产生部分事实；输入不可变、并发调用无共享可变状态；未终结数据版本不可读时不就绪，纯历史终态仍可从公共快照查询 |
| A36 | S01/S02从规划、发生到系统取消的状态组合 | S01为WAITING/PLANNED→TERMINAL/TRIGGERED，S02为WAITING/PLANNED→ACTIVE/PENDING→TERMINAL/COMPLETED或SKIPPED；暂停、改期、退役只把未发生实例置TERMINAL/CANCELLED，不冒充SKIPPED且terminalAt必填 |
| A37 | 五种calendar配置、E02规范化和definition update | 每种schemaVersion 1对象逐字段校验，缺字段/多字段/非法锚点被拒绝；预览、创建、Planner的occurrenceKey和时间完全一致；update必须完整替换，description=null可清空，省略字段失败，NoChange不增revision，只有日历配置变化增加scheduleGeneration |
| A38 | 12张表的完整字段、迁移状态、租户链和租约诊断 | audit/notification/inbox的类型、空值、FK/索引逐项匹配05；Transition四种状态列组合及source/command组合正确；Action target成对、Signal计划引用成组、正常/重驱父链合法；inbox按tenant隔离；Signal/Action RUNNING同时具有leaseOwner/leaseUntil/token，离开RUNNING全部清空，错配tenant或资源链整笔回滚 |
| A39 | local网络边界、健康状态和优雅停机 | local/test只监听127.0.0.1，改为非回环启动失败；liveness不依赖数据库，readiness在迁移/注册/存量版本/数据库失败时拒绝流量；停机先拒绝新写和停止领取，宽限后由租约接管且无重复效果 |
| A40 | SPI注册、命令路由、Policy组合和错误映射 | 六类注册键数量唯一；定义控制命令不进TaskCommandHandler，Signal不进命令处理器；Policy按order/key稳定短路；Applied/NoChange/Rejected及未声明reasonCode、技术异常映射与02/04一致 |
| A41 | 暂停后既有S02与非暂停停机追赶 | resume不为暂停前ACTIVE/PENDING重建提醒但仍允许complete/skip；非暂停停机固定ALL_MISSED，按序分批追赶所有S01/S02 occurrence，过期通知只形成EXPIRED且不入箱 |
| A42 | 公开/内部读模型、集合顺序和投递状态 | E/I视图逐字段与04一致；participants/bindings/commands/occurrences/attempts顺序稳定；代次失配的未清理Action等效CANCELLED；deliveryState按固定优先级推导并区分Handler成功、入箱、未读；内部视图不泄露payload/hash/token/正文 |

每个用例必须同时断言HTTP结果、关键数据库事实和必要的Transition/Attempt/Audit，不能只断言最后状态。

## 5. API、数据与安全验收

E01—E13和I01—I07每个端点都要有独立契约用例，至少覆盖：成功、必填/类型/范围/未知字段、身份和资源归属、状态与revision、幂等重放/冲突、时间格式和返回字段。不适用的维度必须在交付报告写明原因。

公共HTTP边界覆盖未知路径、错误方法、媒体类型、空体、畸形JSON、64KiB边界和未捕获异常。所有响应统一信封并产生traceId。公开响应不得泄露executionToken、leaseOwner、leaseUntil、SQL、堆栈、密码、连接串或敏感payload；受信内部诊断仅可按04返回leaseOwner/leaseUntil，仍不得返回executionToken、payload/hash或通知正文。local profile始终使用配置中的固定tenant/actor且拒绝请求覆盖；`X-Debug-Actor-Id`只在test profile有效；其他profile缺少正式ActorContextProvider时公开API不就绪；内部端点不向公网安全配置暴露。E01—E13逐字段断言04固定读模型，尤其验证deliveryState推导、未读与执行成功分离、诊断视图不含payload/token。Actuator只开放回环liveness/readiness，其他管理端点不可达。

通过information_schema比对05的12张表、字段、可空性、默认值、注释、索引、外键及动作、CHECK、唯一键、字符集和排序规则，包括controlGeneration、leaseOwner、redrive父链、tenant安全索引和live-schema索引。数据库会话使用UTC，业务时间为DATETIME(0)且无小数秒。代表性数据量下保留分页、Planner、Signal/Action领取、租约回收的EXPLAIN证据。参与人50、接收人10、绑定8、每笔occurrence 100、单Transition Action 100、每笔Action 500、单Transition mutation 32及64KiB、单JSON 65536字节、TransitionPlan 1048576字节和总变更2000行的边界必须分别覆盖；S01/S02额外验证只能有一个calendar绑定。

## 6. 本地性能与恢复门槛

这是一期本地实验目标，不是生产SLA。使用真时钟、03固定的MySQL镜像和两进程，预建1万个ACTIVE定义，再在60秒内均匀到期1000个S01站内通知。先预热1次，随后以独立runId连续运行3次，三次都要满足：

- 1000个目标最终全部有且只有1条inbox，无DEAD/EXPIRED。
- occurrenceAt→业务Transition提交的P95≤5秒；occurrenceAt→inbox提交的P95≤10秒。
- 最后一条到期后30秒内，本runId不再有READY、RUNNING、RETRY_WAIT的Signal或Action。
- 按最近秩计算P50/P95/P99，保留原始查询、成功数、重复数、终态数和积压清空时间；不得只保留最好一次。

独立公平性测试构造至少10个定义、每个超过100条积压，验证单轮上限100且新到期S01在10秒内产生收件。独立接管测试在领取后终止一个进程，另一进程须在原租约到期后`2 × scan interval + 10秒`内接管，最终只有一份本地效果。

如当前机器无法达到目标，结果必须为FAIL或BLOCKED并记录瓶颈；不得自行放宽门槛后标PASS。

## 7. 交付证据与总判定

P01进入T08验证时创建`docs/phases/P01/DELIVERY.md`，每项记录：契约/任务/用例编号、实现文件、实际命令、退出码、测试数、PASS/FAIL/BLOCKED/NOT_RUN、证据路径、实际JDK/MySQL/驱动/操作系统/进程数与关键配置。重试保留所有尝试，不只显示最后一次成功；后续阶段写入各自目录的DELIVERY，不覆盖P01证据。

必须实际执行：

```text
./mvnw -q test
./mvnw -q package
./mvnw -Pmysql-it verify
./mvnw -Pmysql-it,dual-process-it verify
```

P02还必须执行其[IMPLEMENTATION](phases/P02/IMPLEMENTATION.md)列出的模块限定真库与双进程命令，并把Q01—Q08逐项记录在P02 DELIVERY。P03须把[J01](07-ACCEPTANCE.md)—[J10](07-ACCEPTANCE.md)逐项记录在其DELIVERY；在真实执行前不得预填PASS。

交付校验自动核对M01—M10、A01—A42、E01—E13、I01—I07、架构扩展夹具、DDL、性能和恢复证据，不得重复或遗漏。只有所有必交付项均为PASS、证据路径存在且可由所记命令重现，且稳定核心、公共存储、Signal/Action运行时和场景专有数据原子物化均已完成，才能宣布首期实现完成。文档状态REVIEWED/T00 READY仅表示具备实施条件，不能写成实现VERIFIED。
