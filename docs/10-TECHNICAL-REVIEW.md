# 10 · 技术评审结论与风险记录

> 阅读入口与阶段状态见[00开发导航](00-READING-ORDER.md)。本文只汇总设计理由、已处理问题和实施期验证风险，不覆盖01—08正式契约，也不表示实现已经完成。

版本2.2；前：[场景路线](09-SCENARIO-ROADMAP.md)，入口：[00](00-READING-ORDER.md)。

## 1. 总体技术结论

方案具备实现条件，适合作为通用任务平台的一期底座。核心原因是任务身份与生命周期、参与人、Signal、TransitionPlan和Action被建模为稳定公共事实；日历、通知、协作、工作流等变化被放在能力扩展；提醒、待办、审批、自动化等变化被放在场景扩展。

这里的“通用”有可验证边界：场景目录中的S01—S17和能力目录中的已知能力项可以在现有抽象下演进；新增场景必须做到kernel生产源码和公共平台DDL零修改。未来若出现当前五类扩展契约确实无法表达的新基础原语，应新增兼容的契约版本并复审，而不是修改既有语义或让旧场景随之改变。因此不能承诺未经定义的所有未来需求绝对零新增契约，但可以保证既有能力不被破坏、已知规划不要求推翻底层。

首期采用本地固定身份，但不是简化内核原型：固定身份只存在于接入适配器，首期仍完成稳定核心、公共存储、Signal/Action运行时、场景专有数据原子物化和双进程恢复。产品交付面明确为本地API优先后端，目标使用者是开发者、集成者和运维者，不把尚未建设的前端误报为可用能力。当前仍只有文档，没有Java工程、SQL执行、真实依赖解析、双进程结果或性能证据。S02首版细则、控制代次、父级优先锁序、完整事务规模和最小运维闭环已经确认；只有07全部证据PASS后才能表示“实现已验证”。

## 2. 关键问题关闭记录

| 编号 | 原问题 | 已采用方案与权威位置 | 当前结论 |
| --- | --- | --- | --- |
| R01 | 以提醒为中心，难以支持其他任务 | 01改为Definition/Instance/Participant/Signal/Transition/Action通用模型 | 设计关闭；待实现验证 |
| R02 | 新场景可能修改内核和公共表 | 02定义五类扩展契约；07用审批、事件、Webhook夹具证明零kernel/DDL修改 | 设计关闭；T07验证 |
| R03 | 单模块职责混杂或细粒度模块爆炸 | 一期13个平级模块；渠道和算法先在能力模块内按包隔离，达到独立发布条件再拆 | 设计关闭 |
| R04 | 模块名称与工程习惯不一致 | service层统一`timeimprint-task-service-*`；MySQL模块为`timeimprint-task-service-storage-mysql` | 设计关闭 |
| R05 | cache概念被误解 | 当前不创建任何cache模块；C14仅保留待决需求，不预设JimDB或模块名 | 设计关闭；未来另审 |
| R06 | scenario-lifecycle与内核生命周期混淆 | 未来场景模块更名为`timeimprint-task-service-scenario-deadline` | 设计关闭 |
| R07 | owner、接收人、办理人混为一体 | 01/05将owner、assignee、collaborator、approver、follower、recipient建模为Participant | 设计关闭；权限用例待验证 |
| R08 | 只有时间调度，事件/条件/依赖无法接入 | 所有输入统一为Signal，TriggerProvider只负责产生强类型Signal | 设计关闭；事件夹具待验证 |
| R09 | Action固定为通知 | ActionHandler通用化，声明LOCAL_TRANSACTIONAL或EXTERNAL；通知只是一个能力 | 设计关闭；两种模式待验证 |
| R10 | 场景可能绕过事务直接改公共状态或无法原子写专有表 | 场景只返回含强类型ScenarioDataMutation的TransitionPlan，由application统一校验、CAS、审计并调用注册物化器原子提交 | 设计关闭；专有表回滚用例待验证 |
| R11 | 外部调用崩溃后可能重复发送 | 调用前持久化effectStartedAt；结果不可确认时UNKNOWN且不自动重发 | 风险受控；无法承诺外部世界恰好一次 |
| R12 | 命令幂等与业务资源、Signal、Action可能反向加锁 | 同步命令先锁command dedup；Signal领取与处理分离，处理前普通读父标识再按definition→trigger→instance→Signal；Planner同样先扫描再按父到子锁；领取/回收不持子锁再请求父锁 | 设计关闭；真MySQL待验证 |
| R13 | 多节点扫描产生重复实例或动作 | MySQL唯一键、SKIP LOCKED、租约、executionToken、revision和最终重验组合 | 设计关闭；双进程待验证 |
| R14 | API随场景增加Controller方法 | 04以通用定义、实例和动态commandKey路由；场景payload在边界强类型化 | 设计关闭；20个端点待验证 |
| R15 | JSON变成任意脚本或任意执行入口 | JSON只作版本化配置/快照；禁止类名、URL、SQL、表达式任意执行 | 设计关闭 |
| R16 | 验收只能证明提醒，不能证明平台通用 | 07增加三类扩展夹具、架构规则、真库、双进程和故障矩阵 | 设计关闭；证据未运行 |
| R17 | 实施先铺空模块，长期不能形成闭环或过早冻结错误抽象 | T02先用最终技术链打通S01 ONCE，T03再收敛核心；T02—T06发现问题可回正，T07才以扩展夹具冻结稳定门槛 | 设计关闭；未授权实施 |
| R18 | 未来能力和场景没有模块落点 | 场景/能力目录给出永久入口、三维状态和归属；02给出五个场景族、八个能力域、一期13模块和未来23模块映射 | 设计关闭；OUTLINE和模块名称不等于批准或实现 |
| R19 | 技术版本或数据库假设可能不可用 | 03锁定目标版本，T01必须记录真实解析、MySQL版本、时区和隔离级别 | 设计关闭；ENV_PENDING |
| R20 | 文档评审完成被误报成代码完成 | 00分离REVIEWED、T00 READY与工程NOT_STARTED；07/08要求真实命令、测试数和证据路径 | 设计关闭 |
| R21 | 本地固定身份被误解为简化内核或未来难以接入认证 | local身份只实现ActorContextProvider；核心不硬编码本地用户，其他profile缺少正式提供器时公开API不就绪 | 设计关闭；接入测试待验证 |
| R22 | 单次创建或迁移无限扩张，导致长事务、锁放大和部分事实 | 03/06规定参与人、接收人、绑定、occurrence、Action和场景数据硬上限；写前完整校验，超限整笔拒绝，禁止截断或拆分一个TransitionPlan | 设计关闭；A25待验证 |
| R23 | S02的截止、催办、snooze、接收人和定义控制语义不足以编码 | S02永久场景契约给出首版默认配置和边界；04/06落实命令、调度、取消及快照行为；07增加A21—A24 | 设计关闭；业务可在实现反馈后按版本调整 |
| R24 | 窗口预建Action与S01在时间Signal后立即TERMINAL相互冲突 | 窗口只建WAITING Instance和计划Signal；实际迁移才建Action；允许同一终态Transition的Action执行，取消更早遗留Action | 设计关闭；A26待验证 |
| R25 | Planner和Signal先锁子表再锁父表，与pause/update形成锁序倒置 | 候选领取/扫描与处理事务分离；所有业务处理统一按父到子锁，同类多行按主键升序 | 设计关闭；A27待验证 |
| R26 | pause异步清理与快速resume竞争，旧工作可能复活或误删新计划 | definition.controlGeneration作为同步屏障并进入计划唯一身份；清理按旧代次隔离 | 设计关闭；A28待验证 |
| R27 | 只有对象数量上限，仍可能由大JSON、总变更行数或长事务放大风险 | 增加单JSON、TransitionPlan总字节、写事务总行数与事务超时硬限制，写前完整校验 | 设计关闭；A29待验证 |
| R28 | S01接收人及首期主体类型未明确 | S01/S02统一OWNER/RECIPIENT规则；一期公共API仅USER，local只接受固定Actor，多身份限定test | 设计关闭；A30待验证 |
| R29 | Signal目标、长停机追赶与失败重驱缺少闭环 | Signal入READY前唯一解析definition；停机按cursor有界追赶；提供DEAD Signal与DEAD本地Action的有限新行重驱 | 设计关闭；A31/A32待验证 |
| R30 | Action超时与幂等键可能导致租约穿透、键过长或泄露主体 | Handler声明受租约安全余量约束的超时；actionKey采用用途前缀+规范身份SHA-256 Base64URL | 设计关闭；A33待验证 |
| R31 | 扩展返回语义、存量版本可读性和分页一致性容易被误解 | 扩展使用不可变强类型输入与Applied/NoChange/Rejected；启动核对未终结版本；分页明确为弱一致keyset且asOf不是快照 | 设计关闭；A34/A35待验证 |
| R32 | 暂停、改期、退役取消未来实例时缺少可落库的场景状态 | S01明确PLANNED/TRIGGERED/CANCELLED，S02明确PLANNED/PENDING/COMPLETED/SKIPPED/CANCELLED；系统取消不借用用户SKIPPED | 设计关闭；A36待验证 |
| R33 | 首期到底是本地工具、后端平台还是终端产品不清楚 | 01固定为本地单身份、API优先后端；README/API示例/健康诊断必交付，GUI明确延期 | 设计关闭；交付定位不再混用 |
| R34 | 五种日历规则只有WEEKLY示例，字段、锚点与规范化不足以编码 | 04逐种固定schemaVersion 1字段、格式、边界、occurrenceKey和PreviewResult | 设计关闭；A37待验证 |
| R35 | definition update缺少全量/合并、null/省略和版本变更语义 | 04固定完整替换六字段、description=null、NoChange及scheduleGeneration条件 | 设计关闭；A37待验证 |
| R36 | 公共与诊断读模型不完整，使用者看不出通知执行/失败/过期 | 04固定全部视图字段和DeliverySummary唯一推导顺序，区分Handler成功、入箱和已读 | 设计关闭；A42待验证 |
| R37 | ScenarioExtension与TaskCommandHandler责任重叠，SPI数量和Policy顺序不确定 | 02固定六类注册键、数量、命令/Signal路由、Policy排序短路及结果错误映射 | 设计关闭；A40待验证 |
| R38 | audit/notification/inbox字段不精确，participant有效期与唯一键冲突，租约所有者无落点 | 05完整定义三表，participant只存当前关系，inbox增加tenant链，Signal/Action增加lease_owner与索引 | 设计关闭；A38待验证 |
| R39 | local无认证可能误监听外网，缺少就绪与优雅停机契约 | 03固定回环监听、最小Actuator健康端点、readiness条件和停机顺序 | 设计关闭；A39待验证 |
| R40 | T02依赖最终DDL/SPI，而T03才定义完成，实施顺序循环 | P01实施计划增加T02入口G01，先固定可编译DTO/SPI、全量DDL和最小端口，再做S01纵向闭环 | 设计关闭；实施时执行G01 |
| R41 | 暂停恢复和非暂停停机对历史提醒的产品行为仍可有两种解释 | 01/06固定resume不重建既有PENDING提醒；普通停机固定ALL_MISSED并对过期Action只记EXPIRED | 设计关闭；A41待验证 |
| R42 | MySQL 9.7.3被误当成必然的服务端版本字符串 | 03区分官方镜像标签/digest与`SELECT VERSION()`；9.7.3是Docker镜像补丁基线 | 设计关闭；T01实测 |
| R43 | definition控制迁移要求写Transition但表中没有前后controlState | 05增加from/to_control_state并固定定义初始、定义后续、实例初始和实例后续四种字段组合；I05同步返回 | 设计关闭；A38待验证 |
| R44 | 文档要求Signal/Action用revision回写，但队列表没有revision列 | 队列技术状态统一用主键 + RUNNING + executionToken；只有业务资源迁移另校验definition/instance revision | 设计关闭；A04/A20待验证 |

## 3. 关键时序复核

### 3.1 创建与发生

创建定义在一个事务中写定义、参与人、触发绑定、7天内WAITING实例、计划Signal和初始Transition，不预建Action。写入前必须完整计算并通过对象数、字节数、总变更行数和事务时限；不能截断首个窗口或拆分一个TransitionPlan。未来发生时间到达后，Signal统一迁移实例并在同一Transition中生成Action；S01虽同时进入TERMINAL，该Transition自身的Action仍可执行。失败时整笔回滚，重复创建由持久化幂等返回首次结果。

### 3.2 命令与通知竞争

complete/skip/snooze是同步Command，不先转成Signal；它们只与Signal共用TransitionPlan提交器。命令先锁command dedup，再按definition→instance→Action加锁，因此终态与站内信只会形成一种提交先后：未开始的通知可取消，已经成功写入的收件保留。

Signal处理不持有子锁再请求父锁：领取事务先提交，处理事务普通读取父标识后按definition→trigger→instance→Signal锁定。Planner同样先扫描主键再按definition→trigger锁定。这一顺序与pause、update、resume一致。

### 3.3 暂停与外部调用竞争

外部Action在真正调用前，以父到子锁序重新检查屏障并提交effectStartedAt。暂停先提交则调用不发生；开始标记先提交则暂停不能撤回已开始调用，只阻止后续副作用。调用结果无法确认时转UNKNOWN，避免平台自动重复调用。

### 3.4 崩溃恢复与控制代次

Signal和Action都通过数据库租约与executionToken领取。租约过期后新Worker生成新token接管，旧Worker的CAS回写必须为0。pause/resume/retire增加controlGeneration，使异步清理延迟也不能复活旧工作。LOCAL_TRANSACTIONAL副作用随结果事务回滚，可以重试；EXTERNAL一旦有开始证据就采用保守UNKNOWN。DEAD重驱创建新行且有次数上限，不修改历史；UNKNOWN不允许直接重驱。

上述结论在逻辑上闭合，但06明确要求用MySQL 9.7.3和两个真实Java进程验证，不能用内存锁、Mock仓储或手工改状态替代。

## 4. 已知限制与后续决策入口

| 风险/限制 | 当前处理 | 何时必须重新决策 |
| --- | --- | --- |
| 实际JDK、Maven、依赖和MySQL环境未知 | T01记录并验证；MySQL分别核对镜像标签/digest与服务端报告版本，不满足则BLOCKED | 开始实施时 |
| Spring AI 2.0.x与Spring Boot 4.0.x补丁兼容性 | 一期按03固定具体版本并在T01验证；不擅自升级 | 依赖解析不兼容、生产部署或版本线变化时 |
| 外部渠道的幂等、超时和错误码尚未知 | 一期只实现站内信；WebhookFixture只验证协议 | 接飞书、京ME或邮件前 |
| 可信身份和内部网络边界未接生产体系 | 一期local profile使用配置固定身份并完成ActorContextProvider边界；不宣称生产身份已完成 | 对外部署前 |
| 本地性能目标不是生产容量承诺 | 07保留固定本地门槛 | 生产容量规划前 |
| 数据归档、保留、删除、冷热分层未定 | 不在一期DDL物理清理 | C18/C19进入实施前 |
| cache/JimDB价值和边界未定 | 当前无cache模块 | C14有明确场景与一致性要求时 |
| 未预见场景可能要求新基础原语 | 只允许增加兼容契约版本并复审 | 现有五类SPI无法表达时 |
| MQ、工作流引擎、动态插件、AI运行能力及多时区等尚无一期需求 | 02明确不创建依赖、表、Bean、配置或空SPI | 对应09候选被明确选入实施时 |

这些限制不阻塞本地一期后端实现，也不能被写成已经具备的生产能力。

## 5. 外部技术依据

外部资料只支持选型判断，不能代替本项目的真实验证：

- Spring AI 2.0.x支持Spring Boot 4.0.x和4.1.x：[Spring AI入门与兼容说明](https://docs.spring.io/spring-ai/reference/getting-started.html)。
- Java 21 LTS满足Spring Boot 4.0.x的Java要求：[Spring Boot系统要求](https://docs.spring.io/spring-boot/system-requirements.html)。
- MyBatis Starter 4.0.1对应Spring Boot 4.0.x：[MyBatis Spring Boot Starter发布记录](https://github.com/mybatis/spring-boot-starter/releases)。
- MySQL 9.7是LTS线；9.7.3发布说明限定为MySQL Server Docker镜像安全补丁，所以镜像制品与服务端报告版本分别验收：[MySQL发布模型](https://dev.mysql.com/doc/refman/9.7/en/mysql-releases.html)、[MySQL 9.7.3发布说明](https://dev.mysql.com/doc/relnotes/mysql/9.7/en/news-9-7-3.html)。
- `SKIP LOCKED`适合队列型候选领取，但不会提供一致性视图，所以06只把它用于领取，业务提交仍重新加锁：[MySQL 9.7锁定读说明](https://dev.mysql.com/doc/refman/9.7/en/innodb-locking-reads.html)。
- MySQL默认隔离级别及锁行为必须以实际测试会话核验：[MySQL 9.7事务隔离说明](https://dev.mysql.com/doc/refman/9.7/en/innodb-transaction-isolation-levels.html)。

## 6. 最终评审判定

稳定核心、本地身份适配、场景专有数据物化及已知场景扩展路径在设计层面可以兼容；核心表和内核不应包含提醒、待办、审批或具体通知渠道的专有字段/分支。08要求首期完成核心，但没有要求实现未来23个模块或未来业务能力。

当前文档已收敛日历、更新、读模型、SPI、数据库、安全运行和实施顺序；00以T00 READY表示具备实施条件，不表示设计绝对无误。Java工程、SQL、测试和部署继续保持NOT_STARTED；只有用户明确授权开始T01或开始实现后，才进入工程实施。
