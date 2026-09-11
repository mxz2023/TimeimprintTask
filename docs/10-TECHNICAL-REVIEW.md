# 10 · 技术评审结论与风险记录

> 审批状态以[00审核台账](00-READING-ORDER.md)为准。本文只汇总01—09的设计结论和剩余风险，不覆盖前述正式契约，也不表示实现已经完成。

版本2.0；前：[场景路线](09-SCENARIO-ROADMAP.md)，入口：[00](00-READING-ORDER.md)。

## 1. 总体技术结论

方案具备实现条件，适合作为通用任务平台的一期底座。核心原因是任务身份与生命周期、参与人、Signal、TransitionPlan和Action被建模为稳定公共事实；日历、通知、协作、工作流等变化被放在能力扩展；提醒、待办、审批、自动化等变化被放在场景扩展。

这里的“通用”有可验证边界：09中已知的S01—S17可以在现有抽象下演进；新增场景必须做到kernel生产源码和公共平台DDL零修改。未来若出现当前五类扩展契约确实无法表达的新基础原语，应新增兼容的契约版本并复审，而不是修改既有语义或让旧场景随之改变。因此不能承诺未经定义的所有未来需求绝对零新增契约，但可以保证既有能力不被破坏、已知规划不要求推翻底层。

当前仍只有文档，没有Java工程、SQL执行、真实依赖解析、双进程结果或性能证据。文档APPROVED表示“设计可实施”，只有07全部证据PASS后才能表示“实现已验证”。

## 2. 关键问题关闭记录

| 编号 | 原问题 | 已采用方案与权威位置 | 当前结论 |
| --- | --- | --- | --- |
| R01 | 以提醒为中心，难以支持其他任务 | 01改为Definition/Instance/Participant/Signal/Transition/Action通用模型 | 设计关闭；待实现验证 |
| R02 | 新场景可能修改内核和公共表 | 02定义五类扩展契约；07用审批、事件、Webhook夹具证明零kernel/DDL修改 | 设计关闭；T02—T04验证 |
| R03 | 单模块职责混杂或细粒度模块爆炸 | 一期13个平级模块；渠道和算法先在能力模块内按包隔离，达到独立发布条件再拆 | 设计关闭 |
| R04 | 模块名称与工程习惯不一致 | service层统一`joytask-service-*`；MySQL模块为`joytask-service-storage-mysql` | 设计关闭 |
| R05 | cache概念被误解 | 当前不创建任何cache模块；C14仅保留待决需求，不预设JimDB或模块名 | 设计关闭；未来另审 |
| R06 | scenario-lifecycle与内核生命周期混淆 | 未来场景模块更名为`joytask-service-scenario-deadline` | 设计关闭 |
| R07 | owner、接收人、办理人混为一体 | 01/05将owner、assignee、collaborator、approver、follower、recipient建模为Participant | 设计关闭；权限用例待验证 |
| R08 | 只有时间调度，事件/条件/依赖无法接入 | 所有输入统一为Signal，TriggerProvider只负责产生强类型Signal | 设计关闭；事件夹具待验证 |
| R09 | Action固定为通知 | ActionHandler通用化，声明LOCAL_TRANSACTIONAL或EXTERNAL；通知只是一个能力 | 设计关闭；两种模式待验证 |
| R10 | 场景可能绕过事务直接改公共状态 | 场景只返回TransitionPlan，由application统一校验、CAS、审计和原子提交 | 设计关闭；并发用例待验证 |
| R11 | 外部调用崩溃后可能重复发送 | 调用前持久化effectStartedAt；结果不可确认时UNKNOWN且不自动重发 | 风险受控；无法承诺外部世界恰好一次 |
| R12 | 命令、Signal、Action可能反向加锁 | 06统一父到子锁序；领取/回收不持子锁再请求父锁；外部结果不回进业务迁移 | 设计关闭；真MySQL待验证 |
| R13 | 多节点扫描产生重复实例或动作 | MySQL唯一键、SKIP LOCKED、租约、executionToken、revision和最终重验组合 | 设计关闭；双进程待验证 |
| R14 | API随场景增加Controller方法 | 04以通用定义、实例和动态commandKey路由；场景payload在边界强类型化 | 设计关闭；18个端点待验证 |
| R15 | JSON变成任意脚本或任意执行入口 | JSON只作版本化配置/快照；禁止类名、URL、SQL、表达式任意执行 | 设计关闭 |
| R16 | 验收只能证明提醒，不能证明平台通用 | 07增加三类扩展夹具、架构规则、真库、双进程和故障矩阵 | 设计关闭；证据未运行 |
| R17 | 实施先铺空模块，长期不能形成闭环 | 08按内核→存储→通用管道→运行时→S01→S02的纵向顺序推进 | 设计关闭；未授权实施 |
| R18 | 未来能力和场景没有模块落点 | 09给出八个能力域、五个场景族、一期13模块和未来23模块映射 | 设计关闭；候选不等于批准实施 |
| R19 | 技术版本或数据库假设可能不可用 | 03锁定目标版本，T01必须记录真实解析、MySQL版本、时区和隔离级别 | 设计关闭；ENV_PENDING |
| R20 | 文档通过被误报成代码完成 | 00分离APPROVED、NOT_STARTED；07/08要求真实命令、测试数和证据路径 | 设计关闭 |

## 3. 关键时序复核

### 3.1 创建与发生

创建定义在一个事务中写定义、参与人、触发绑定、7天内WAITING实例、计划Signal、初始Transition和预定Action。未来发生时间到达后，Signal统一激活实例；Action受WAITING和父定义状态屏障。失败时整笔回滚，重复创建由持久化幂等返回首次结果。

### 3.2 命令与通知竞争

complete/skip/snooze直接执行但与Signal共用TransitionPlan提交器。命令和LOCAL_TRANSACTIONAL通知都按definition→instance→Action加锁，因此终态与站内信只会形成一种提交先后：未开始的通知可取消，已经成功写入的收件保留。

### 3.3 暂停与外部调用竞争

外部Action在真正调用前，以父到子锁序重新检查屏障并提交effectStartedAt。暂停先提交则调用不发生；开始标记先提交则暂停不能撤回已开始调用，只阻止后续副作用。调用结果无法确认时转UNKNOWN，避免平台自动重复调用。

### 3.4 崩溃恢复

Signal和Action都通过数据库租约与executionToken领取。租约过期后新Worker生成新token接管，旧Worker的CAS回写必须为0。LOCAL_TRANSACTIONAL副作用随结果事务回滚，可以重试；EXTERNAL一旦有开始证据就采用保守UNKNOWN。

上述结论在逻辑上闭合，但06明确要求用MySQL 8.4和两个真实Java进程验证，不能用内存锁、Mock仓储或手工改状态替代。

## 4. 已知限制与后续决策入口

| 风险/限制 | 当前处理 | 何时必须重新决策 |
| --- | --- | --- |
| 实际JDK、Maven、依赖和MySQL环境未知 | T01记录并验证；不满足则BLOCKED | 开始实施时 |
| Spring Boot 3.4维护状态与公司基建约束 | 一期按03锁定版本；不擅自升级 | 生产部署或公司基建变化时 |
| 外部渠道的幂等、超时和错误码尚未知 | 一期只实现站内信；WebhookFixture只验证协议 | 接飞书、京ME或邮件前 |
| 可信身份和内部网络边界未接生产体系 | 一期仅定义ActorContext和本地测试入口 | 对外部署前 |
| 本地性能目标不是生产容量承诺 | 07保留固定本地门槛 | 生产容量规划前 |
| 数据归档、保留、删除、冷热分层未定 | 不在一期DDL物理清理 | C18/C19进入实施前 |
| cache/JimDB价值和边界未定 | 当前无cache模块 | C14有明确场景与一致性要求时 |
| 未预见场景可能要求新基础原语 | 只允许增加兼容契约版本并复审 | 现有五类SPI无法表达时 |

这些限制不阻塞本地一期后端实现，也不能被写成已经具备的生产能力。

## 5. 外部技术依据

外部资料只支持选型判断，不能代替本项目的真实验证：

- Java 17满足Spring Boot 3.4要求：[Spring Boot 3.4系统要求](https://docs.spring.io/spring-boot/3.4/system-requirements.html)。
- MyBatis Starter 3.0.x支持Java 17和Spring Boot 3.2—3.5：[MyBatis Spring Boot Starter官方仓库](https://github.com/mybatis/spring-boot-starter)。
- `SKIP LOCKED`适合队列型候选领取，但不会提供一致性视图，所以06只把它用于领取，业务提交仍重新加锁：[MySQL 8.4锁定读说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)。
- MySQL默认隔离级别及锁行为必须以实际测试会话核验：[MySQL 8.4事务隔离说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html)。

## 6. 最终评审判定

01—09在设计层面彼此可兼容，已知场景的扩展路径明确，核心表和内核不含提醒、待办、审批或具体通知渠道的专有字段/分支。08的实施顺序可逐段产生可运行证据，没有要求先实现未来23个模块。

因此，本评审建议：2.0文档基线通过；T00可在完成全量一致性检查后转PASS。Java工程、SQL、测试和部署继续保持NOT_STARTED，直到用户明确授权实施。
