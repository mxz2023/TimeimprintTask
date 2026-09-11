# 08 · AI实施任务与阶段门槛

> 审批与实施准入以[00审核台账](00-READING-ORDER.md)为准。本文通过只表示实施路径可行；当前仍未获得创建Java工程、执行SQL迁移或发送外部通知的授权。

版本2.0；前：[验收](07-ACCEPTANCE.md)，后：[场景路线](09-SCENARIO-ROADMAP.md)。任务按可运行纵向切片递增，不以“创建了模块或接口”作为完成证据。

## 1. 总体顺序与状态

```text
T00 文档收敛
  → T01 13模块与环境基线
  → T02 稳定内核与扩展契约
  → T03 MySQL存储与事务基础
  → T04 应用编排、Signal和通用API
  → T05 日历规划与运行时
  → T06 通知与S01纵向闭环
  → T07 S02、恢复与双进程
  → T08 全量验收与交付
```

| 任务 | 当前状态 | 转PASS的必要条件 |
| --- | --- | --- |
| T00 | PASS | 01—10的2.0当前内容全部APPROVED，一致性检查已通过 |
| T01—T08 | NOT_STARTED | 必须获得实施授权、上一阶段PASS、本阶段产物和验证全部PASS |

状态只允许NOT_STARTED、IN_PROGRESS、PASS或BLOCKED。不得用空实现、固定假数据、跳过测试、占位异常或TODO转PASS。环境或契约问题使关键证据无法取得时标BLOCKED，不得跳过进入下一阶段。

获得实施授权后，T01—T07在技术门槛PASS时可自动衔接；T08只能依据真实证据判定完成。生产部署、真实飞书/京ME/邮件发送和允许名单外数据库操作仍需另行授权。

## 2. 各阶段实施契约

### T00 · 文档收敛

输入00—10。产物为全部2.0契约、审核台账和与实际工程一致的WORKLOG。

门槛：模块名、场景/能力归属、API编号、数据表、状态、时间、幂等、锁序、用例编号和实施依赖无冲突；待决事项不得偷换成已批准实施项。

### T01 · 13模块与环境基线

允许范围：父POM、Maven Wrapper、13个子模块的最小可编译结构、boot-loader配置、测试基础和README。

- 生成02确定的13个平级模块，无cache模块；设置Java 17、Spring Boot 3.4.11、MyBatis Starter 3.0.5和统一revision。
- 配置Maven Enforcer、ArchUnit和依赖规则。空模块只是基线产物，不代表业务能力完成。
- 分离无库单元测试与mysql-it/dual-process-it；校验测试库允许名单、UTC、MySQL版本和隔离级别。
- 环境不可用时保留可编译产物并标BLOCKED，不用H2代替锁和迁移证据。

门槛：`./mvnw -q test`、`./mvnw -q package`通过；有实际JDK/Maven/MySQL证据；13个模块与02一致。

### T02 · 稳定内核与扩展契约

主要模块：`joytask-common`、`joytask-api`、`joytask-service-kernel`、`joytask-service-extension-api`、`joytask-service-application`。

- 测试先行实现TaskDefinition、TaskInstance、Participant、Signal、Action、TransitionPlan、revision与终态不变量。
- 实现ScenarioExtension、TriggerProvider、TaskCommandHandler、ActionHandler、Policy五类契约、强类型schema解码、显式注册表和重复key启动失败。
- 先为五类契约建立不含业务的一致性测试；三个扩展夹具在平台与DDL基线完成后由T04加入。
- 用ArchUnit阻止Mapper、实现类和反射越界。

门槛：kernel不依赖Spring、MyBatis、web、storage、scenario或capability；五类契约一致性测试通过；无scenarioKey硬编码分支。

### T03 · MySQL存储与事务基础

主要模块：`joytask-service-storage-mysql`、`joytask-service-application`、`joytask-boot-loader`，以及`joytask-service-capability-notification`的迁移资源（本阶段不实现通知业务）。

- 将05的12张表落入按所有权拆分的Flyway迁移，实现Mapper/XML、存储端口和关键索引查询。
- 实现command dedup、Signal/Action唯一键、Transition revision CAS、租约/token基础与完整事务重试边界。
- 在MySQL 8.4执行空库迁移、重复启动、坏迁移、information_schema、CHECK/FK/唯一键、JSON、UTC整秒和EXPLAIN验证。
- 数据访问不向场景暴露公共Mapper，不建立空的未来场景表。

门槛：真MySQL迁移和结构检查PASS；故意违反约束会失败；不得只凭DDL文本评审转PASS。

### T04 · 应用编排、Signal和通用API

主要模块：`joytask-service-application`、`joytask-api-gateway`、`joytask-web`、`joytask-boot-loader`；使用T02契约与T03存储。

- 实现ActorContext、Policy、TransitionPlan校验/提交器、定义/实例命令管道、Signal接收/领取/处理和审计。
- 落地E01—E09、I01—I05以及统一响应、分页、错误、64KiB、schemaVersion、幂等、归属和防泄露契约。
- 暂无实际场景处理器的路径返回正确EXTENSION_NOT_FOUND或COMMAND_NOT_SUPPORTED，不返回假成功。
- 在平台生产源码和公共DDL已完成的基线上，仅向测试源集加入ApprovalFixture、EventTriggerFixture和WebhookActionFixture，不创建生产模块。
- 加入前后以版本库diff，或在无版本库时以SHA-256清单，证明kernel生产源码和公共Flyway DDL零变更；用ApprovalFixture穿过HTTP命令与Signal管道。

门槛：同步命令和Signal共享一个提交器；三类夹具只经稳定契约装配且零kernel/公共DDL变更；幂等与revision并发用例PASS；HTTP不泄露内部token或敏感payload。

### T05 · 日历规划与运行时

主要模块：`joytask-service-capability-calendar`、`joytask-service-runtime`、`joytask-service-application`、`joytask-service-storage-mysql`。

- 实现ONCE、DAILY、WEEKLY、MONTHLY、EVERY_N_DAYS的强类型配置、预览、7天窗口、每绑定每轮100条、游标和scheduleGeneration。
- 实现Planner、Signal Worker、Action Worker的有界领取、固定排序、连接池/线程池反压、租约回收和公平性。
- 预览与规划共用纯日历算法；覆盖月末、闰年、锚点、时区、严格after和规则耗尽。
- 使用EventTriggerFixture证明runtime不限于时间触发。

门槛：M01—M10的日历预期由纯算法验证；A01、A03、A04、A11、A12、A20的底层机制通过真库/双进程证明。本阶段不宣称S01/S02已经交付。

### T06 · 通知与S01纵向闭环

主要模块：`joytask-service-capability-notification`、`joytask-service-scenario-basic`、`joytask-api-gateway`、`joytask-web`、`joytask-boot-loader`。

- 实现reminder场景、通知意图、LOCAL_TRANSACTIONAL站内信处理器、jt_notification/jt_inbox物化和E10—E13。
- 打通E02预览→E03创建→窗口实例→时间Signal→Transition→Action→inbox→已读的真HTTP/真库闭环。
- “提醒已触发”与送达/已读分离；渠道失败不回退S01业务终态。
- 用WebhookActionFixture验证EXTERNAL的effectStartedAt、UNKNOWN和不自动重发；不连接真实外部渠道。

门槛：M01—M05和S01相关A用例PASS；同一Action最终只有一条站内信；不得把测试Webhook写成真实外部渠道已验证。

### T07 · S02、恢复与双进程

主要模块：`joytask-service-scenario-basic`，并补齐application/runtime/notification已定义的通用机制；不得为S02在平台写特例分支。

- 实现recurring_todo、PENDING/COMPLETED/SKIPPED、complete/skip/snooze、0—3个额外催办、历史快照和实例独立。
- 实现update/pause/resume/retire、恢复水位、旧批次取消、终态屏障、退避/DEAD/EXPIRED、Policy阻断计数返还和审计原因优先级。
- 完成M06—M10、A01—A20、E01—E13、I01—I05整体回归；技术重试不得增加业务槽位。
- 双进程验证规划竞争、完成与通知、pause与Signal/Action、租约接管、旧token和公平性。

门槛：`./mvnw -Pmysql-it,dual-process-it verify`真实执行并PASS；S01/S02共用平台机制；不得用手工删除RUNNING、改计数或单进程模拟冒充恢复。

### T08 · 全量验收与交付

本阶段不新增业务范围。产物为可运行包、README、环境变量与启动说明、迁移/升级说明、API示例、恢复手册和`docs/11-DELIVERY-REPORT.md`。

- 执行07的六层验收、三次正式性能测试、积压公平性和双进程中断接管。
- 保留命令、退出码、测试数、环境、证据路径和所有重试；自动核对全部M/A/E/I编号。
- 核对09的未实施场景、能力和C14 cache待决需求，不将其误标完成或删除。
- 任一项为FAIL、BLOCKED、NOT_RUN或证据路径缺失，整体不得宣称完成。

门槛：07第7章全部命令真实通过；报告可复现；结论只覆盖本地后端，不冒充生产容量、生产身份或真实外部渠道验证。

## 3. 实施期执行规则

每个子任务启动前必须写明任务ID、允许修改的模块/文件、对应契约、用例编号和验证命令。先写失败测试或约束检查，再写最小实现，最后运行本阶段全部回归。

同一业务规则只保留一个权威实现。发现已批准文档冲突、扩展契约无法表达需求，或必须修改kernel/公共DDL才能增加测试夹具时，立即标BLOCKED并记录冲突、影响和修正方案，不得静默选择。

不得覆盖用户已有修改；不得在未授权时部署、发送外部消息、写真实凭据、升降03锁定的版本或扩大数据库范围。新发现的一期外场景写入09，不自动变成当前实施范围。

完成记录必须包含：任务ID、契约/用例ID、修改文件、命令、退出码、测试数、PASS/FAIL/BLOCKED/NOT_RUN、证据路径、实际环境、剩余问题和已解锁依赖。
