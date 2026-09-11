# 06 · Signal、迁移与Action运行协议

> 人工审核与阶段准入以[00审核台账](00-READING-ORDER.md)为准。本文已由Codex完成技术可行性和并发推演审核；文档通过表示协议具备实现条件，不代表代码已实现。

版本2.0；前：[数据库](05-DATABASE.md)，后：[验收](07-ACCEPTANCE.md)。本文定义命令、时间规划、Signal处理、TransitionPlan提交、Action执行、租约恢复和暂停屏障。场景业务规则以01为准。

## 1. 全局不变量

1. 同一tenant/providerKey/signalKey最多处理一次确定输入；同键不同摘要必须冲突。
2. 同一触发绑定、规则批次和occurrenceKey最多生成一个实例。
3. 同一tenant/handlerKey/actionKey最多生成一个Action Job。
4. 场景不能直接更新公共表；所有状态变化必须形成TransitionPlan并由application提交。
5. 业务状态事务中禁止HTTP、IM、文件、模型或其他不可回滚外部调用。
6. Action技术终态不自动等于实例业务终态；结果需要改变业务时，以新Signal进入迁移管道。
7. 所有最终提交重新检查definition控制状态、instance revision、租约和executionToken，候选扫描结果不能作为提交权限。
8. 同一实例的Transition revision连续且唯一；历史快照不可被新定义覆盖。

## 2. 时间、事务重试与锁顺序

一次业务事务只读取一次业务时间T，来自注入Clock并截断为UTC整秒；所有该事务的状态、审计和幂等时间基于T。租约领取、是否过期和回收使用数据库UTC时间L，不用应用Clock模拟租约。

数据库死锁或锁等待超时只允许对完整、尚未执行外部副作用的事务最多重试3次。每次重新开启事务、重新读取T和状态、重新计算TransitionPlan；不能只重试最后一条UPDATE。外部调用开始后不得通过完整事务重试再次调用。

业务锁顺序固定为：

```text
definition
  → trigger binding（涉及时）
  → instance（涉及时）
  → participant
  → transition
  → action / notification
  → audit
  → command dedup
```

Signal Worker先锁定自己持有token的Signal行，再按上述definition→instance顺序处理；命令事务不反向锁Signal。Action的领取和过期回收事务只锁Action→Attempt，不再请求父级锁；执行前屏障、EXTERNAL副作用开始标记和LOCAL_TRANSACTIONAL结果物化事务按definition→instance→Action→Attempt顺序锁定。EXTERNAL调用完成后的结果事务只锁Action→Attempt→能力自有结果行，不持有Action锁再进入definition/instance业务迁移。

## 3. 定义创建与时间窗口规划

E02预览只运行配置解码、场景校验和calendar算法，不写数据库。E03创建顺序：

1. 校验ActorContext、requestId、场景与触发配置版本。
2. 创建幂等占位并锁定唯一键。
3. 调用ScenarioExtension生成初始定义计划。
4. 写definition、定义级participants和trigger bindings。
5. 对ACTIVE的calendar绑定同步规划未来7天窗口，最多每绑定100次。
6. 对每次发生创建WAITING实例、计划Signal和场景声明的预定Action；每个新实例都写一条revision 0→1的初始Transition，每个Action引用所属实例的该Transition；定义创建历史、Audit和成功Dedup分别按05所定义的对象落库。
7. 同一事务提交；任一步失败全部回滚。

计划Signal在规划时已持久化，`occurred_at`为未来名义发生时间，`received_at`为规划时间，`next_attempt_at`为发生时间。这样未来实例可以提前查询，同时真正激活仍经过统一Signal管道。

预定Action可以提前创建，但`available_at`不得早于发生时间，并受实例WAITING屏障约束。Action Worker在实例仍WAITING时不得调用处理器；Signal先把实例迁移到可执行状态。S01在时间Signal提交迁移后标记“提醒已触发”，S02迁移为ACTIVE/PENDING。

后台Trigger Planner使用`FOR UPDATE SKIP LOCKED`领取到期规划的trigger binding，在同一短事务内重新校验definition/binding状态、调用纯calendar算法、按唯一键插入窗口数据并推进cursor。每绑定每轮最多100次，仍有窗口空间时把next_fire_at设置为下一候选；规则耗尽设置exhausted。

修改时间规则增加scheduleGeneration，锁内结清旧批次：已发生历史不变；旧批次未来Signal和Action转CANCELLED；旧WAITING实例按场景原因终结；新批次同步重建7天窗口。任何冲突或生成失败使整个修改回滚。

## 4. Signal接收、领取与处理

### 4.1 接收

内部I01先按tenant/providerKey/signalKey和payloadHash去重。同键同摘要返回首次signalId；同键不同摘要返回IDEMPOTENCY_CONFLICT。接收事务只持久化合法Signal，不调用场景、不执行Action。

calendar计划Signal由平台内部生成；event、condition和dependency未来由capability-trigger接入。所有provider payload在落库前按schemaVersion强类型校验；无法识别目标可以先持久化为可诊断的IGNORED或明确失败，不能把坏数据当条件成立。

### 4.2 领取

Signal Worker在短事务中从READY/RETRY_WAIT且next_attempt_at≤L的记录领取最多CLAIM_BATCH_SIZE条，使用SKIP LOCKED，写RUNNING、lease_until、executionToken并增加attempt_count后立即提交。实际领取数不得超过线程池空闲数。

### 4.3 处理提交

每个Signal单独开启事务：

1. 以signalId + RUNNING + executionToken锁Signal并检查租约。
2. 锁definition和目标instance，重验控制状态、生命周期、schemaVersion和Policy。
3. 调用ScenarioExtension/TaskCommandHandler的Signal处理入口进行确定性计算。
4. 校验TransitionPlan，写实例/参与人变化、Transition、Action、能力意图和Audit。
5. 把Signal置为SUCCEEDED或IGNORED并清除租约，同事务提交。

确定性业务拒绝进入IGNORED并记录resultCode；可重试技术错误进入RETRY_WAIT；不可恢复的配置损坏或尝试耗尽进入DEAD。Signal处理没有外部副作用，因此结果事务失败时保留RUNNING并由租约回收后安全重算。

## 5. 同步命令处理

E06/E09不经过后台Signal队列，但与Signal共享同一TransitionPlan校验和提交器：

1. 基础格式与ActorContext校验。
2. 插入/等待command dedup唯一键并核对requestHash。
3. 按固定顺序锁definition、instance和participants。
4. 重验Policy、controlState、lifecycle、scenarioState和expectedRevision。
5. 将payload按scenarioKey + commandKey + schemaVersion转换为强类型命令。
6. 场景纯计算TransitionPlan。
7. 原子提交资源变化、Transition、Action、能力意图、Audit和Dedup结果。

同requestId同摘要返回首次CommandResultView；不同摘要冲突。相同目标终态的重复命令可以由场景返回NoChange，平台不得增加revision、Transition或Action，但可以写本次新的幂等结果。相反终态或不支持命令返回冲突且不写业务结果。

## 6. TransitionPlan校验与原子提交

TransitionPlan进入持久化前必须验证：

- 目标definition/instance与当前锁定资源一致，fromRevision等于数据库revision。
- lifecycleCategory与terminalAt组合合法；TERMINAL不能通过普通命令重新打开。
- scenarioState和payload版本由当前场景处理器确认。
- participant变更不产生重复角色，instance与definition归属一致。
- Action handler存在、schemaVersion可读、actionKey稳定且payload通过对应Handler校验。
- 场景不能声明直接SQL、类名、URL任意执行、事务传播或绕过审计的指令。
- 审计摘要和幂等响应满足大小及敏感信息限制。

平台使用CAS更新目标revision；任何影响行数不符合预期、唯一键冲突含义不一致或能力结果物化失败时整笔回滚。相同唯一键且摘要一致可按幂等已有事实处理；摘要不同必须报告完整性冲突，不能静默覆盖。

## 7. Action领取、执行与结果提交

Action分为三个明确阶段：

### 7.1 领取事务

从READY/RETRY_WAIT且`available_at≤L`、`next_attempt_at≤L`、未过期的记录中SKIP LOCKED领取。写RUNNING、lease、executionToken、attempt_count并插入STARTED attempt后立即提交。attempt_no取该Action历史最大序号+1，不能用可退还的attempt_count推导。达到expiresAt的动作直接EXPIRED；达到maxAttempts的动作进入DEAD。

### 7.2 执行前屏障与处理器调用

调用前先用普通读取取Action的父级标识，再以短事务按definition→instance→Action→Attempt顺序加锁，最后重验RUNNING、executionToken和租约。父定义PAUSED/RETIRED、实例WAITING/TERMINAL、批次失效或Policy阻断时，不执行外部副作用。可恢复屏障把Action安全退回READY/RETRY_WAIT、退还本次attempt_count并闭合Attempt为POLICY_BLOCKED；attempt_no仍永久保留。业务永久取消转CANCELLED。普通读只用于定位锁定顺序，不能作为执行资格判断。

ActionHandler声明执行模式。`LOCAL_TRANSACTIONAL`处理器不在此阶段产生效果，留到结果事务原子写能力自有表。`EXTERNAL`处理器在调用前必须再开一个短事务，同样按definition→instance→Action→Attempt锁定，重验父级屏障、RUNNING、executionToken、租约和effectStartedAt为空，然后写入Attempt.effectStartedAt。该事务还要确认剩余租约大于处理器超时预算和安全余量；提交后才在事务外调用。这一提交是“副作用已开始”的并发边界：暂停先提交则本次不调用；标记先提交则后续暂停只能阻止新副作用，不能撤回该次调用。Handler不得自行循环重试、修改公共任务表或调用其他场景内部代码；一次调用只返回SUCCEEDED、RETRYABLE_FAILURE、PERMANENT_FAILURE或UNKNOWN及去敏结果。

### 7.3 结果事务

LOCAL_TRANSACTIONAL结果事务按definition→instance→Action→Attempt加锁，重验父级屏障、RUNNING、executionToken和租约后才执行处理器，并通过自己的结果物化器写所属表，例如IN_APP插入jt_inbox。EXTERNAL调用后的结果事务以actionJobId + RUNNING + executionToken锁定Action→Attempt，只物化已经取得的返回结果，不再以后续暂停否定已开始的调用。物化器不能修改kernel表或其他能力表。处理结果需要推进业务时，同事务插入一个以actionKey派生signalKey的结果Signal，不能直接更新实例。随后同事务闭合Attempt并CAS写Action结果：

- SUCCEEDED → SUCCEEDED。
- RETRYABLE_FAILURE且次数未耗尽 → RETRY_WAIT并计算退避。
- RETRYABLE_FAILURE已耗尽或PERMANENT_FAILURE → DEAD。
- 外部请求已经发起但无法确认 → UNKNOWN，停止自动重试。

结果事务失败时Action保持RUNNING，由租约恢复。LOCAL_TRANSACTIONAL效果随事务回滚，可以安全重试；EXTERNAL根据effectStartedAt和providerReference判断，不能假设未发送。

## 8. 通知能力执行

场景产生notification意图时，capability-notification在Transition事务内保存jt_notification，并为每个接收人/渠道创建独立Action Job。渠道失败相互隔离；一个飞书失败不能回滚已经提交的站内信或改变S01“提醒已触发”。

IN_APP没有外部网络副作用：结果事务原子插入jt_inbox、闭合Attempt并把Action置SUCCEEDED。唯一键冲突时只有内容和接收人一致才视为幂等成功，否则报告完整性错误。

未来飞书、京ME、邮件Handler在事务外调用。每次必须携带平台actionKey或渠道支持的幂等键；记录受理号、错误分类和UNKNOWN。渠道SDK自身隐藏重试必须关闭或纳入一次调用的明确超时预算，防止平台与SDK叠加成不可控重试。

## 9. 暂停、恢复、退役与版本屏障

- PAUSED状态提交后立即阻止新窗口规划、新Signal业务迁移和未开始的Action副作用；历史查询不受影响。已有未来数据可以由有界后台任务分批清理，但父级屏障从pause事务提交时已经生效，不能等清理结束才阻断。
- 暂停区间已预生成的WAITING实例、Signal和Action按场景规则统一终结或跳过，不能因是否提前物化得到不同业务结果。
- 恢复设置人工水位，只从恢复后的下一合法发生继续；首期不补暂停期间的S01/S02。
- RETIRED永久阻止新发生和普通业务命令；历史、收件、Transition、Attempt和Audit保留。
- 每次提交重验definition revision、trigger scheduleGeneration、instance revision和Action token。旧Worker、旧Signal、旧批次和旧命令不能越过屏障。

锁内清理原因优先级固定为：定义RETIRED → 定义PAUSED/恢复水位 → 规则批次失效 → 实例TERMINAL → expiresAt到界 → 尝试耗尽。一次处理只写一个主resultCode，其他命中条件可以写入安全诊断摘要。

## 10. 重试、租约恢复与公平性

Signal和Action的技术退避为5、30、120、600秒，最多5次。租约到期回收使用有界批次和SKIP LOCKED：

- Signal RUNNING过期：清除旧token；未耗尽进入RETRY_WAIT，耗尽进入DEAD。
- Action RUNNING过期且executionMode=LOCAL_TRANSACTIONAL，或EXTERNAL但effectStartedAt为空：闭合Attempt为RETRYABLE_FAILURE并重试或DEAD。
- Action为EXTERNAL且effectStartedAt非空：闭合Attempt为UNKNOWN并把Action置UNKNOWN，不自动重复；即使实际调用可能尚未发出，也采用保守结果。

回收不能删除Attempt、减少attempt_count或复用executionToken。旧执行者最终提交CAS必须失败。

Planner、Signal Worker和Action Worker每轮都限制批量；按nextAttemptAt/availableAt和ID稳定排序。历史积压与新到期工作使用分批或时间片公平处理，不能由单个definition长期占满线程。领取数受线程空闲和数据库连接池共同约束，不先领取再放入无界内存队列。

## 11. 并发提交判据

以下竞态必须得到唯一、可解释结果：

| 竞态 | 判据 |
| --- | --- |
| 两节点规划同一发生 | instance/signal/action唯一键只允许一套事实；游标单调推进 |
| complete与通知同时发生 | definition→instance锁序决定先后；终态后未开始Action取消，已成功收件保留 |
| pause与Signal同时发生 | 最终提交重验controlState；pause先提交则Signal不迁移，Signal先提交则保留已提交事实 |
| pause与外部Action同时发生 | 副作用前屏障可阻断；调用已经发起则保留结果或UNKNOWN，不能宣称撤回 |
| update与旧批次Action同时发生 | scheduleGeneration和instance revision阻止旧批次提交 |
| 相同requestId并发 | 一个事务提交首次结果，其他等待后重放；不同摘要冲突 |
| 租约到期与旧Worker回写 | 新token接管后旧token CAS为0，不能覆盖新结果 |
| Action成功与结果Signal重复 | actionKey和providerKey/signalKey分别去重，业务迁移revision唯一 |

06的可行性必须由MySQL真库和双进程测试证明，不能用单进程锁、Mock仓储或人为删除RUNNING记录代替。外部渠道未接入前只能验证UNKNOWN协议和测试Handler，不能宣称真实渠道恰好一次。
