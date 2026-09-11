# 07 · 验收与证据正式契约

> 审批状态以[00审核台账](00-READING-ORDER.md)为准。本文定义实现完成后必须提供的客观证据；文档通过不等于代码或测试已通过。

版本2.0；前：[运行协议](06-SCHEDULING.md)，后：[实施任务](08-AI-IMPLEMENTATION-TASKS.md)。当前所有实现用例均为NOT_RUN，实施时才能根据真实输出标记PASS。

## 1. 验收分层与环境

| 层级 | 验证对象 | 必须使用的环境 |
| --- | --- | --- |
| Unit | 时间计算、强类型解码、Policy、场景迁移、退避 | 固定或可推进Clock；无Spring、无真库 |
| Architecture | 13模块依赖、扩展SPI、禁止跨边界引用 | Maven Enforcer、ArchUnit和依赖报告 |
| Contract | E01—E13、I01—I05、DTO、错误、幂等、安全 | 启动后的真HTTP边界 |
| MySQL IT | DDL、索引、事务、锁、租约、恢复 | 独立MySQL 8.4测试库；禁止H2代替 |
| Dual-process IT | 两节点竞争、崩溃、旧token和公平性 | 同一制品、同一数据库、两个Java进程 |
| Performance | 到期处理、收件可查、积压恢复 | 真时钟、真MySQL、两进程 |

集成测试使用`*IT`命名，`mysql-it`绑定Failsafe的integration-test与verify阶段，`dual-process-it`在其上启动两进程。两个profile在配置缺失、测试数为0、子进程未就绪或证据未生成时必须失败，不得静默跳过。

真库测试默认只允许`joytask_test`；其他专用测试库必须在测试配置明确列入允许名单。测试不得执行CREATE DATABASE、DROP DATABASE、TRUNCATE或Flyway clean。每次生成唯一runId，只删除由该runId记录的根资源及子表数据，不得无条件或模糊删除。

受控交错、故障注入和可推进Clock只能存在于测试源集/配置，不得暴露为生产HTTP端点或装入生产制品。所有报告和日志必须脱敏。

## 2. 架构通用性的必须证明

除13个正式模块的边界检查外，必须在测试源集实现三个最小扩展夹具；它们不是一期业务交付，不创建生产Maven模块：

| 扩展夹具 | 要证明的能力 | 通过标准 |
| --- | --- | --- |
| ApprovalFixture | 新场景、审批人参与角色、approve/reject命令、自有状态 | 只依赖extension-api稳定契约，通过统一命令和TransitionPlan提交 |
| EventTriggerFixture | 外部事件触发 | 通过I01受理和Signal Worker处理，不在kernel中增加事件分支 |
| WebhookActionFixture | 外部网络Action和UNKNOWN | 声明EXTERNAL模式，验证effectStartedAt、幂等键、超时和不自动重发 |

扩展证明的硬门槛：

- 实现三个夹具时，`joytask-service-kernel`生产源码和公共Flyway DDL的变更数均为0。
- 夹具不能引用公共Mapper、runtime实现类或其他场景内部类，不能通过Spring Bean名/反射绕过契约。
- Maven依赖无环；common无业务状态；api不依赖实现；kernel不依赖Spring、MyBatis、web、storage或任何scenario/capability。
- 平台不得按`reminder`、`recurring_todo`、`approval`等scenarioKey编写if/switch；扩展通过显式注册表装配并在启动时检测重复key。

任一条失败即表示“新场景不改底层”尚未证明，不能用S01/S02自身可运行替代。

## 3. 一期纵向场景矩阵

固定基准为北京时间2026-09-08 08:00:00，本地执行时间为09:00:00。S01与S02均覆盖ONCE、DAILY、WEEKLY、MONTHLY、EVERY_N_DAYS：

| 用例 | 场景/规则 | 首次发生（北京时） | 关键断言 |
| --- | --- | --- | --- |
| M01 | S01 / ONCE：2026-09-09 | 2026-09-09 09:00 | 一个实例、一个INITIAL，规则耗尽 |
| M02 | S01 / DAILY | 2026-09-08 09:00 | 第二次为9月9日 |
| M03 | S01 / WEEKLY：周五 | 2026-09-11 09:00 | 第二次为9月18日 |
| M04 | S01 / MONTHLY：31日 | 2026-09-30 09:00 | 月末回退但快照仍为31 |
| M05 | S01 / EVERY_N_DAYS：9月1日起3天 | 2026-09-10 09:00 | 不以重启或完成时间重置锚点 |
| M06 | S02 / ONCE | 2026-09-09 09:00 | 1个INITIAL + 配置的0—3个CHASE |
| M07 | S02 / DAILY | 2026-09-08 09:00 | 上一实例未完成不阻断下一次 |
| M08 | S02 / WEEKLY | 2026-09-11 09:00 | 每个occurrence实例和槽位独立 |
| M09 | S02 / MONTHLY：31日 | 2026-09-30 09:00 | 跨大小月与闰年正确 |
| M10 | S02 / EVERY_N_DAYS | 2026-09-10 09:00 | N=1和N>1都有覆盖 |

每个M用例都须走E02预览→E03创建→E04/E05查询→时间Signal→Action→E07/E08/E10—E13查询。断言7天窗口、occurrenceKey、规则快照、实例、Action、Transition、通知和收件唯一，且参与人/接收人一致。超出7天的M04/M09先断言未物化且游标正确，再推进窗口。

S01在通知Action意图提交后显示“提醒已触发”，不将收件生成或已读当作业务终结条件。S02默认1个INITIAL和3个CHASE；技术重试不增加业务槽位或收件数。

## 4. 状态、故障和并发必测项

| 编号 | 操作/故障 | 必须结果 |
| --- | --- | --- |
| A01 | 预览边界、月31日、闰年、N日锚点、JVM默认时区改变 | after严格排除；自然日和锚点不漂移；结果不受JVM时区影响 |
| A02 | 创建中间失败、响应丢失、同requestId重试 | 定义/窗口/历史/幂等全部回滚，或返回首次已提交结果；无空占位 |
| A03 | 两进程同时规划相同发生 | 只有一组instance/signal/action，游标单调 |
| A04 | Signal领取后崩溃，旧Worker恢复回写 | 租约到期后可接管；旧token CAS为0；只有一条迁移链 |
| A05 | complete、skip、snooze并发与重放 | 同目标幂等；相反终态冲突；snooze不增加槽位、不复活DEAD/EXPIRED |
| A06 | complete与站内通知同时提交 | 固定锁序得到唯一先后；终态后未开始Action取消；已成功收件保留 |
| A07 | pause与Signal同时提交 | pause先提交则Signal不迁移；Signal先提交则保留已提交事实 |
| A08 | pause与EXTERNAL Action同时提交 | pause先则无调用；effectStartedAt先则调用可继续并记录结果/UNKNOWN |
| A09 | 外部Action调用前、调用中和返回后分别崩溃 | 未开始可重试；effectStartedAt非空但不能确认时转UNKNOWN且不自动重发 |
| A10 | LOCAL_TRANSACTIONAL在效果写入后CAS前失败 | 效果、Attempt和Action结果全部回滚，可安全重试，最终一条inbox |
| A11 | 修改时间规则与旧批次Worker同时提交 | 历史快照不变；旧未来事实取消；旧generation/revision不能越过屏障 |
| A12 | 暂停跨周期再恢复 | 暂停区间不补发；恢复后从下一合法发生继续；预生成与未生成的业务结果一致 |
| A13 | 同requestId不同内容、同revision不同requestId | 前者IDEMPOTENCY_CONFLICT；后者只一个迁移成功 |
| A14 | 尝试跨tenant/跨参与人读写 | 查询不串数，命令被Policy拒绝，不泄露资源是否存在 |
| A15 | 死锁、锁等待超时、结果提交失败 | 副作用前只完整事务重试最多3次；无部分状态；外部副作用开始后不重放调用 |
| A16 | 连续可重试失败、Policy阻断、expiresAt恰到界 | 5/30/120/600秒退避，最多5次；只有副作用前Policy阻断退还计数；到界EXPIRED |
| A17 | 收件重复执行、标记已读重放、同时分页 | 收件唯一；readAt保留首次；同秒以ID稳定排序且不重不漏 |
| A18 | 非法schemaVersion、未知字段、时间小数秒、过去/=当前的ONCE | 04规定的错误；不落业务数据或成功幂等结果 |
| A19 | Flyway重复启动、损坏迁移、非法CHECK/FK/唯一键 | 重复启动安全；损坏迁移不就绪；真MySQL拒绝非法数据 |
| A20 | 两进程一个领取后终止，另一个接管 | 在规定租约窗口内完成；旧token无权回写；最终只有一份本地效果 |

每个用例必须同时断言HTTP结果、关键数据库事实和必要的Transition/Attempt/Audit，不能只断言最后状态。

## 5. API、数据与安全验收

E01—E13和I01—I05每个端点都要有独立契约用例，至少覆盖：成功、必填/类型/范围/未知字段、身份和资源归属、状态与revision、幂等重放/冲突、时间格式和返回字段。不适用的维度必须在交付报告写明原因。

公共HTTP边界覆盖未知路径、错误方法、媒体类型、空体、畸形JSON、64KiB边界和未捕获异常。所有响应统一信封并产生traceId，不泄露executionToken、leaseUntil、SQL、堆栈、密码、连接串或敏感payload。`X-Debug-Actor-Id`在非本地/测试环境必须无效；内部端点不向公网安全配置暴露。

通过information_schema比对05的12张表、字段、可空性、默认值、注释、索引、外键、CHECK、唯一键、字符集和排序规则。数据库会话使用UTC，业务时间为DATETIME(0)且无小数秒。代表性数据量下保留分页、Planner、Signal/Action领取、租约回收的EXPLAIN证据。

## 6. 本地性能与恢复门槛

这是一期本地实验目标，不是生产SLA。使用真时钟、MySQL 8.4和两进程，预建1万个ACTIVE定义，再在60秒内均匀到期1000个S01站内通知。先预热1次，随后以独立runId连续运行3次，三次都要满足：

- 1000个目标最终全部有且只有1条inbox，无DEAD/EXPIRED。
- occurrenceAt→业务Transition提交的P95≤5秒；occurrenceAt→inbox提交的P95≤10秒。
- 最后一条到期后30秒内，本runId不再有READY、RUNNING、RETRY_WAIT的Signal或Action。
- 按最近秩计算P50/P95/P99，保留原始查询、成功数、重复数、终态数和积压清空时间；不得只保留最好一次。

独立公平性测试构造至少10个定义、每个超过100条积压，验证单轮上限100且新到期S01在10秒内产生收件。独立接管测试在领取后终止一个进程，另一进程须在原租约到期后`2 × scan interval + 10秒`内接管，最终只有一份本地效果。

如当前机器无法达到目标，结果必须为FAIL或BLOCKED并记录瓶颈；不得自行放宽门槛后标PASS。

## 7. 交付证据与总判定

实施时创建`docs/11-DELIVERY-REPORT.md`，每项记录：契约/任务/用例编号、实现文件、实际命令、退出码、测试数、PASS/FAIL/BLOCKED/NOT_RUN、证据路径、实际JDK/MySQL/驱动/操作系统/进程数与关键配置。重试保留所有尝试，不只显示最后一次成功。

必须实际执行：

```text
./mvnw -q test
./mvnw -q package
./mvnw -Pmysql-it verify
./mvnw -Pmysql-it,dual-process-it verify
```

交付校验自动核对M01—M10、A01—A20、E01—E13、I01—I05、架构扩展夹具、DDL、性能和恢复证据，不得重复或遗漏。只有所有必交付项均为PASS、证据路径存在且可由所记命令重现，才能宣布实现完成。文档APPROVED仅表示契约可实现，不能写成实现VERIFIED。
