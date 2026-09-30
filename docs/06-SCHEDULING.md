# 06 · Signal、迁移与Action运行协议

> 阅读入口与阶段状态见[00开发导航](00-READING-ORDER.md)。本文是规划、事务、锁、异步执行、恢复和并发结果的正式来源；协议已评审不代表代码已经实现。

版本2.3；前：[数据库](05-DATABASE.md)，后：[验收](07-ACCEPTANCE.md)。本文定义命令、时间规划、Signal处理、TransitionPlan提交、Action执行、租约恢复和暂停屏障。公共业务规则以01为准，场景专有规则以[场景目录](scenarios/README.md)为准，能力规则以[能力目录](capabilities/README.md)为准。

## 1. 全局不变量

1. 同一tenant/providerKey/signalKey最多处理一次确定输入；同键不同摘要必须冲突。
2. 同一触发绑定、规则批次、定义控制代次和occurrenceKey最多生成一个实例。
3. 同一tenant/handlerKey/actionKey最多生成一个Action Job。
4. 场景不能直接更新公共表；所有状态变化必须形成TransitionPlan并由application提交。
5. 业务状态事务中禁止HTTP、IM、文件、模型或其他不可回滚外部调用。
6. Action技术终态不自动等于实例业务终态；结果需要改变业务时，以新Signal进入迁移管道。
7. 所有最终提交重新检查definition控制状态、controlGeneration、instance revision、租约和executionToken，候选扫描结果不能作为提交权限。
8. 同一实例的Transition revision连续且唯一；历史快照不可被新定义覆盖。

## 2. 时间、事务重试与锁顺序

一次业务事务只读取一次业务时间T，来自注入Clock并截断为UTC整秒；所有该事务的状态、审计和幂等时间基于T。租约领取、是否过期和回收使用数据库UTC时间L，不用应用Clock模拟租约。

数据库死锁或锁等待超时只允许对完整、尚未执行外部副作用的事务最多重试3次。每次重新开启事务、重新读取T和状态、重新计算TransitionPlan；不能只重试最后一条UPDATE。外部调用开始后不得通过完整事务重试再次调用。

同步命令事务先取得自身幂等键，再进入公共业务锁；业务锁顺序固定为：

```text
command dedup（仅同步命令）
  → definition
  → trigger binding（涉及时）
  → instance（涉及时）
  → participant
  → signal（Signal处理时）
  → transition
  → action / notification
  → attempt（Action执行时）
  → inbox / capability result
  → audit
```

Signal领取事务只锁候选Signal并提交。处理事务先普通读取Signal的父级标识，再按definition→trigger binding→instance→Signal顺序取得锁；命令事务不反向锁Signal。Planner先普通扫描候选主键，再按definition→trigger binding锁定单个候选。Action的领取和过期回收事务只锁Action→Attempt，不再请求父级锁；执行前屏障、EXTERNAL副作用开始标记和LOCAL_TRANSACTIONAL结果物化事务按definition→instance→Action→Attempt顺序锁定。EXTERNAL调用完成后的结果事务只锁Action→Attempt→能力自有结果行，不持有Action锁再进入definition/instance业务迁移。同一类别涉及多行时一律按主键升序锁定。

## 3. 定义创建与时间窗口规划

E02预览只运行配置解码、场景校验和calendar算法，不写数据库。E03创建顺序：

1. 校验ActorContext、requestId、场景与触发配置版本。
2. 创建幂等占位并锁定唯一键。
3. 调用ScenarioExtension生成初始定义计划。
4. 写definition、定义级participants和trigger bindings。
5. 对ACTIVE的calendar绑定同步规划未来7天窗口；一次创建事务跨全部绑定最多生成100个occurrence，S01/S02只允许一个calendar绑定。
6. 对每次发生只创建WAITING实例和计划Signal；每个新实例都写一条revision 0→1的初始Transition。定义创建历史、Audit和成功Dedup分别按05所定义的对象落库。
7. 同一事务提交；任一步失败全部回滚。

计划Signal在规划时已持久化，`occurred_at`为未来名义发生时间，`received_at`为规划时间，`next_attempt_at`为发生时间。这样未来实例可以提前查询，同时真正激活仍经过统一Signal管道。

窗口阶段不得预创建Action。时间Signal实际处理并提交迁移时才生成通知与Action：S01在同一迁移中进入TERMINAL/TRIGGERED，同时生成的INITIAL Action可以执行；此前其他Transition遗留的未终结Action必须取消。S02在该迁移中进入ACTIVE/PENDING并生成INITIAL与CHASE槽位。

后台Trigger Planner先无锁扫描到期trigger binding主键，再逐个开启短事务，按definition→trigger binding锁序重新校验状态、调用纯calendar算法、按唯一键插入窗口数据并推进cursor。Planner不能先锁trigger再补锁definition。每绑定每轮最多100次，仍有窗口空间时把next_fire_at设置为下一候选；规则耗尽设置exhausted。

修改时间规则增加scheduleGeneration，锁内结清旧批次：已发生历史不变；旧批次未来Signal转IGNORED，旧批次已存在的未终结Action转CANCELLED；旧WAITING/PLANNED实例迁移为TERMINAL/CANCELLED；新批次同步重建7天窗口。任何冲突或生成失败使整个修改回滚。

服务停机不是定义暂停。首期固定使用`ALL_MISSED`，不开放配置：恢复后Planner从已提交cursor按occurrenceAt、occurrenceKey升序追赶，不跳过历史发生；每绑定每轮仍最多100个occurrence，并在定义之间公平轮转。追赶时已经超过业务expiresAt的Action直接落为EXPIRED且不发送陈旧通知；S02每个历史发生仍建立独立PENDING实例，S01每个历史发生仍建立TERMINAL/TRIGGERED历史实例。一次停机无论多长都不能改用“只取最新”或“直接跳过”；变更此产品取舍必须先修改对应场景契约、01、04、06和07。

任何同步规划都先完整计算并校验规模，再开始写入。超过03规定的参与人、接收人、绑定、occurrence、Action或场景数据上限时，整笔请求返回INVALID_REQUEST；不截断、不拆成多个事务，也不先提交定义再由后台补齐首个窗口。

## 4. Signal接收、领取与处理

### 4.1 接收

内部I01先把输入解析为且仅为一个definition，再按tenant/providerKey/signalKey和payloadHash去重。同键同摘要返回首次signalId；同键不同摘要返回IDEMPOTENCY_CONFLICT。接收事务只持久化合法Signal，不调用场景、不执行Action。首期不接受无目标、延迟解析或广播Signal。

calendar计划Signal由平台内部生成；event、condition和dependency未来由capability-trigger接入。所有provider payload在落库前按schemaVersion强类型校验；无法唯一识别目标必须明确失败，不能进入READY，也不能把坏数据当条件成立。

### 4.2 领取

Signal Worker在短事务中从READY/RETRY_WAIT且next_attempt_at≤L的记录领取最多CLAIM_BATCH_SIZE条，使用SKIP LOCKED，写RUNNING、lease_until、executionToken并增加attempt_count后立即提交。实际领取数不得超过线程池空闲数。

### 4.3 处理提交

每个Signal单独开启事务：

1. 普通读取Signal以取得definition、trigger binding和instance标识。
2. 按definition→trigger binding→instance→Signal锁定，并以signalId + RUNNING + executionToken检查租约；重验控制状态、controlGeneration、生命周期、schemaVersion和Policy。
3. 调用ScenarioExtension的Signal处理入口进行确定性计算；TaskCommandHandler只处理同步命令。
4. 校验TransitionPlan，写实例/参与人变化、Transition、Action、能力意图和Audit。
5. 把Signal置为SUCCEEDED或IGNORED，并同时清空lease_owner、lease_until、executionToken，同事务提交。

确定性业务拒绝进入IGNORED并记录resultCode；可重试技术错误进入RETRY_WAIT；不可恢复的配置损坏或尝试耗尽进入DEAD。Signal处理没有外部副作用，因此结果事务失败时保留RUNNING并由租约回收后安全重算。

## 5. 同步命令处理

E06/E09不经过后台Signal队列，但与Signal共享同一TransitionPlan校验和提交器：

1. 基础格式与ActorContext校验。
2. 插入PROCESSING command dedup行；唯一键冲突时等待原事务完成后核对requestHash并重放COMPLETED结果，原事务回滚时重新竞争插入。
3. 按固定顺序锁definition、instance和participants。
4. 重验Policy、controlState、lifecycle、scenarioState和expectedRevision。
5. 将payload按scenarioKey + commandKey + schemaVersion转换为强类型命令。
6. 场景纯计算TransitionPlan。
7. 原子提交资源变化、Transition、Action、能力意图、Audit，并把Dedup置为COMPLETED；确定性业务拒绝也保存可重放结果，技术异常和回滚不保留PROCESSING行。

同requestId同摘要返回首次CommandResultView；不同摘要冲突。相同目标终态的重复命令可以由场景返回NoChange，平台不得增加revision、Transition或Action，但可以写本次新的幂等结果。相反终态或不支持命令返回冲突且不写业务结果。

### 5.1 定义级命令的事务规模

`update/pause/resume/retire`由application实现，不路由TaskCommandHandler。update严格使用04的完整替换payload；锁内先规划全部7天窗口变化并估算总行数，再决定提交。

- 创建ACTIVE定义、ACTIVE状态下的update和resume必须在一个事务中完整写入新的7天窗口；PAUSED状态下的update只保存定义/绑定变化并保持无未来窗口。S01/S02只有一个calendar绑定且每天最多一次，正常最多7个occurrence，仍需受03的100 occurrence和2000行硬上限。超过即整笔拒绝，不先提交definition再补首窗。
- pause、retire和时间规则update在同一事务终结当前窗口中全部WAITING实例及其计划Signal；这些对象数量受已提交窗口边界限制。不得在该事务扫描或锁定全部历史ACTIVE实例和全部Action。
- definition控制状态或controlGeneration提交后，所有旧代次Action立即失去执行资格。旧代次READY/RETRY_WAIT Action由后续Worker领取、过期扫描或诊断清理按批次转CANCELLED；在物理状态更新前，公开DeliverySummary按CANCELLED计数，内部诊断同时返回storedStatus与effectiveStatus=CANCELLED，不得向使用者显示为仍可执行。
- pause或retire前已经RUNNING且effectStartedAt已提交的EXTERNAL Action按既有结果闭合；其他旧代次工作不能重新排队。resume不为暂停前ACTIVE/PENDING的S02实例重建提醒，只允许其complete/skip。

这样控制命令的同步事务上界由“定义 + 当前7天窗口”决定，历史Action清理不进入用户请求事务；controlGeneration保证逻辑生效不依赖清理速度。

## 6. TransitionPlan校验与原子提交

TransitionPlan进入持久化前必须验证：

- 目标definition/instance与当前锁定资源一致，fromRevision等于数据库revision。
- lifecycleCategory与terminalAt组合合法；TERMINAL不能通过普通命令重新打开。
- scenarioState和payload版本由当前场景处理器确认。
- participant变更不产生重复角色，instance与definition归属一致。
- Action handler存在、schemaVersion可读、actionKey稳定且payload通过对应Handler校验。
- ScenarioDataMutation的scenarioKey与当前场景一致，mutationKey/schemaVersion有唯一注册的物化器，payload已完成强类型校验。
- 场景不能声明直接SQL、类名、URL任意执行、事务传播或绕过审计的指令。
- 审计摘要和幂等响应满足大小及敏感信息限制。
- 单个TransitionPlan最多100个Action、32个ScenarioDataMutation且mutation payload合计不超过64KiB；单个JSON值最多65536字节、TransitionPlan规范化后最多1048576字节、整笔写事务最多500个Action且总变更行数最多2000。参与人、接收人、绑定和occurrence同时受03的固定上限约束；业务写事务超时为5秒且配置只能在1—30秒之间。

平台使用CAS更新目标revision，并在同一事务中按显式注册表调用ScenarioDataMaterializer写入本场景专有表。所有平台Mapper、能力结果物化器和场景物化器都必须返回实际受影响行数；提交器统一累计，超过2000立即抛错并使整笔事务回滚，不能只相信写前估算。物化器不得写公共表、其他场景表或发起外部调用。任何影响行数不符合预期、唯一键冲突含义不一致、能力结果或场景专有数据物化失败时整笔回滚。相同唯一键且摘要一致可按幂等已有事实处理；摘要不同必须报告完整性冲突，不能静默覆盖。

## 7. Action领取、执行与结果提交

Action分为三个明确阶段：

### 7.1 领取事务

从READY/RETRY_WAIT且`available_at≤L`、`next_attempt_at≤L`、未过期的记录中SKIP LOCKED领取。写RUNNING、lease、executionToken、attempt_count并插入STARTED attempt后立即提交。attempt_no取该Action历史最大序号+1，不能用可退还的attempt_count推导。在数据库时间`L >= expiresAt`时动作先转EXPIRED，不得再领取；达到maxAttempts的动作进入DEAD。

### 7.2 执行前屏障与处理器调用

调用前先用普通读取取Action的父级标识，再以短事务按definition→instance→Action→Attempt顺序加锁，最后重验RUNNING、executionToken、租约和definitionControlGeneration。父定义PAUSED/RETIRED、控制代次不一致、实例WAITING、批次失效或Policy阻断时，不执行外部副作用。可恢复Policy屏障把Action安全退回READY/RETRY_WAIT、退还本次attempt_count并闭合Attempt为POLICY_BLOCKED；attempt_no仍永久保留。S01/S02的PAUSED/RETIRED、旧控制代次或失效批次属于业务永久取消，Action转CANCELLED。实例TERMINAL时，仅允许由使实例进入该终态的同一Transition生成的Action继续，其他未终结Action转CANCELLED。普通读只用于定位锁定顺序，不能作为执行资格判断。

ActionHandler声明执行模式和`timeoutSeconds`，后者必须小于等于`LEASE_SECONDS - ACTION_LEASE_SAFETY_SECONDS`；HTTP/SDK客户端的连接、读取和总超时都必须受该预算约束。`LOCAL_TRANSACTIONAL`处理器不在此阶段产生效果，留到结果事务原子写能力自有表。`EXTERNAL`处理器在调用前必须再开一个短事务，同样按definition→instance→Action→Attempt锁定，重验父级屏障、RUNNING、executionToken、租约和effectStartedAt为空，然后写入Attempt.effectStartedAt。该事务还要确认剩余租约大于处理器超时预算和安全余量；提交后才在事务外调用。这一提交是“副作用已开始”的并发边界：暂停先提交则本次不调用；标记先提交则后续暂停只能阻止新副作用，不能撤回该次调用。Handler不得自行循环重试、修改公共任务表或调用其他场景内部代码；一次调用只返回SUCCEEDED、RETRYABLE_FAILURE、PERMANENT_FAILURE或UNKNOWN及去敏结果。

### 7.3 结果事务

LOCAL_TRANSACTIONAL结果事务按definition→instance→Action→Attempt加锁，重验父级屏障、RUNNING、executionToken和租约后才执行处理器，并通过自己的结果物化器写所属表，例如IN_APP插入tt_inbox。EXTERNAL调用后的结果事务以actionJobId + RUNNING + executionToken锁定Action→Attempt，只物化已经取得的返回结果，不再以后续暂停否定已开始的调用。物化器不能修改kernel表或其他能力表。处理结果需要推进业务时，同事务插入一个以actionKey派生signalKey的结果Signal，不能直接更新实例。随后同事务闭合Attempt并CAS写Action结果：

- SUCCEEDED → SUCCEEDED。
- RETRYABLE_FAILURE且次数未耗尽 → RETRY_WAIT并计算退避。
- RETRYABLE_FAILURE已耗尽或PERMANENT_FAILURE → DEAD。
- 外部请求已经发起但无法确认 → UNKNOWN，停止自动重试。

每个合法结果分支必须在同一事务中同时清空Action的lease_owner、lease_until和executionToken；退回READY/RETRY_WAIT、业务取消、过期和租约回收同样遵守这一规则。任何CAS影响0行都不得物化结果或覆盖新执行者状态。

结果事务失败时Action保持RUNNING，由租约恢复。LOCAL_TRANSACTIONAL效果随事务回滚，可以安全重试；EXTERNAL根据effectStartedAt和providerReference判断，不能假设未发送。

## 8. 通知能力执行

场景产生notification意图时，capability-notification在Transition事务内保存tt_notification，并为每个接收人/渠道创建独立Action Job。渠道失败相互隔离；一个飞书失败不能回滚已经提交的站内信或改变S01“提醒已触发”。

IN_APP没有外部网络副作用：结果事务原子插入tt_inbox、闭合Attempt并把Action置SUCCEEDED。唯一键冲突时只有内容和接收人一致才视为幂等成功，否则报告完整性错误。

未来飞书、京ME、邮件Handler在事务外调用。每次必须携带平台actionKey或渠道支持的幂等键；记录受理号、错误分类和UNKNOWN。渠道SDK自身隐藏重试必须关闭或纳入一次调用的明确超时预算，防止平台与SDK叠加成不可控重试。

### 8.1 S02首版提醒与snooze

S02每个occurrence的`occurrenceAt`等于定义中的`dueAt`。INITIAL在dueAt可用；CHASE按`chaseOffsetsMinutes`相对dueAt生成，默认分别为+60、+240、+720分钟。所有提醒共享`expiresAt = dueAt + notificationExpireAfterMinutes`，默认+1440分钟。实例创建时只把业务槽位计划写入场景快照，时间Signal迁移时据此一次性创建Action；技术重试不得增加槽位。

S01与S02使用同一接收人规则：没有显式RECIPIENT时，最终接收人取OWNER；一旦声明RECIPIENT，则只向显式RECIPIENT发送。去重后的最终接收人最多10人。一期公共API只接受USER主体，本地配置中的OWNER和RECIPIENT必须等于固定Actor；多身份只允许测试配置模拟。Action key的规范输入至少包含instanceId、提醒用途、slotIndex、actionGeneration、recipient和channel，最终存储为用途短前缀加SHA-256 Base64URL摘要，不得直接拼接接收人、使用随机数或当前时间。

complete或skip原子终结实例，并取消尚未开始的READY/RETRY_WAIT提醒；RUNNING调用和已经产生的收件事实按正常结果闭合且保留。skip必须保存1—500个Unicode码点的原因；complete原因可选。

snooze只允许ACTIVE/PENDING实例且`snoozeCount < maxSnoozeCount`。命令先按固定锁序锁实例，再按action_job_id稳定顺序锁定本批次未开始的提醒；目标时间必须晚于业务时间T且早于expiresAt。最早一个仍可执行提醒移动到snoozeUntil，其余仍可执行提醒整体平移相同差值；原批次统一CANCELLED，新批次以递增actionGeneration创建相同数量的新Action。SUCCEEDED、RUNNING、DEAD、EXPIRED或CANCELLED不移动、不复活。只要任一新时间达到或超过expiresAt，整笔命令拒绝且不写任何变化。

## 9. 暂停、恢复、退役与版本屏障

- PAUSED状态提交时增加definition.controlGeneration，并立即阻止新窗口规划、时间Signal业务迁移和未开始的Action副作用；历史查询不受影响。已有未来数据可以由有界后台任务分批清理，但父级屏障从pause事务提交时已经生效，不能等清理结束才阻断。
- 暂停区间已预生成的WAITING/PLANNED实例迁移为TERMINAL/CANCELLED，未来时间Signal终结为IGNORED，未开始Action转CANCELLED；该区间不产生可办理实例或提醒，不能因是否提前物化得到不同业务结果。恢复后不重放暂停期间已经到期的提醒。
- 恢复再次增加controlGeneration并设置人工水位，只从恢复后的下一合法发生继续；首期不补暂停期间的S01/S02。新代次参与instance唯一键和计划Signal稳定键，因此不依赖旧清理完成即可重建未来计划。
- S02处于PAUSED时，暂停前已经ACTIVE/PENDING的实例仍可complete或skip，但不能snooze。定义内容、参与人或S02配置更新只重建未来WAITING快照和未开始Action；ACTIVE与TERMINAL实例保留原快照。时间规则更新仍按scheduleGeneration重建窗口。
- RETIRED增加controlGeneration并永久阻止新发生，把WAITING/PLANNED实例迁移为TERMINAL/CANCELLED、未来Signal终结为IGNORED、未开始Action转CANCELLED；历史、收件、Transition、Attempt和Audit保留。退役前已经ACTIVE/PENDING的S02实例仍可complete或skip，但不能snooze。
- 每次提交重验definition revision、controlGeneration、trigger scheduleGeneration、instance revision和Action token。旧Worker、旧Signal、旧批次和旧命令不能越过屏障。

锁内清理原因优先级固定为：定义RETIRED → 定义PAUSED/恢复水位 → 规则批次失效 → 实例TERMINAL → expiresAt到界 → 尝试耗尽。一次处理只写一个主resultCode，其他命中条件可以写入安全诊断摘要。

## 10. 重试、租约恢复与公平性

Signal和Action的技术退避为5、30、120、600秒，最多5次。领取时同时写lease_owner=INSTANCE_ID、lease_until和executionToken；离开RUNNING必须同时清空三者。lease_owner只用于诊断而不参与所有权判定，队列行提交资格由主键 + RUNNING + executionToken决定；涉及业务变化时另校验definition/instance revision。租约到期回收使用有界批次和SKIP LOCKED：

- Signal RUNNING过期：清除旧token；未耗尽进入RETRY_WAIT，耗尽进入DEAD。
- Action RUNNING过期且executionMode=LOCAL_TRANSACTIONAL，或EXTERNAL但effectStartedAt为空：闭合Attempt为RETRYABLE_FAILURE并重试或DEAD。
- Action为EXTERNAL且effectStartedAt非空：闭合Attempt为UNKNOWN并把Action置UNKNOWN，不自动重复；即使实际调用可能尚未发出，也采用保守结果。

回收不能删除Attempt、减少attempt_count或复用executionToken。旧执行者最终提交CAS必须失败。

Planner、Signal Worker和Action Worker每轮都限制批量；按nextAttemptAt/availableAt和ID稳定排序。历史积压与新到期工作使用分批或时间片公平处理，不能由单个definition长期占满线程。领取数受线程空闲和数据库连接池共同约束，不先领取再放入无界内存队列。

### 10.1 人工重驱

I06只允许把DEAD Signal作为来源，I07只允许把DEAD且executionMode=LOCAL_TRANSACTIONAL的Action作为来源。请求必须提供requestId、expectedStatus=DEAD和1—500码点原因；同一正常根对象最多创建3次重驱。服务锁定根行并分配redriveNo，新行的parent始终指向根，再重新走正常领取流程；全部历史行永久保留且状态不变。

EXTERNAL或UNKNOWN Action禁止重驱，因为无法仅凭平台状态证明副作用没有发生；必须先完成独立对账，再由业务创建具有新语义的新Action或补偿Signal。来源definitionControlGeneration必须等于当前controlGeneration，并重新通过当前控制状态、实例生命周期和Policy屏障；重驱不能用来回放暂停区间或复活已失去业务资格的通知。幂等、父链、次数限制与这些屏障都在单个短事务中校验。

## 11. 并发提交判据

以下竞态必须得到唯一、可解释结果：

| 竞态 | 判据 |
| --- | --- |
| 两节点规划同一发生 | instance/signal唯一键只允许一套计划事实；游标单调推进，Action仅由实际迁移产生 |
| complete与通知同时发生 | definition→instance锁序决定先后；终态后未开始Action取消，已成功收件保留 |
| pause与Signal同时发生 | 统一definition→…→Signal锁序并重验controlGeneration；pause先提交则旧Signal不迁移，Signal先提交则保留已提交事实 |
| pause与外部Action同时发生 | 副作用前屏障可阻断；调用已经发起则保留结果或UNKNOWN，不能宣称撤回 |
| update/resume与旧工作同时发生 | controlGeneration、scheduleGeneration和instance revision阻止旧Signal/Action提交 |
| 相同requestId并发 | 一个事务提交首次结果，其他等待后重放；不同摘要冲突 |
| 租约到期与旧Worker回写 | 新token接管后旧token CAS为0，不能覆盖新结果 |
| Action成功与结果Signal重复 | actionKey和providerKey/signalKey分别去重，业务迁移revision唯一 |
| 清理延迟与恢复重建同时发生 | 新旧controlGeneration隔离；清理只处理旧代次，不会取消新代次工作 |
| 人工重驱并发提交 | requestId与根对象/redriveNo唯一性只允许一条新行；原DEAD行不变 |

06的可行性必须由MySQL真库和双进程测试证明，不能用单进程锁、Mock仓储或人为删除RUNNING记录代替。外部渠道未接入前只能验证UNKNOWN协议和测试Handler，不能宣称真实渠道恰好一次。
