# 03 · 技术基线、环境与模块装配契约

> 阅读入口与阶段状态见[00开发导航](00-READING-ORDER.md)。本文是技术版本、配置、装配、迁移和运行环境的正式来源；P01实际环境证据见其DELIVERY，后续阶段仍须重新验证受影响部分。

版本2.3；前：[架构](02-AI-CODING-GUIDE.md)，后：[API](04-API.md)。本文定义经批准模块使用的技术基线、配置、迁移加载、扩展注册、运行观测和环境验收边界；P01验证结论以[P01 DELIVERY](phases/P01/DELIVERY.md)为准。P05 起模块集合含`timeimprint-task-adapter`，见[02](02-AI-CODING-GUIDE.md)与[P05](phases/P05/README.md)。

## 1. 固定技术基线

| 项目 | 契约 |
| --- | --- |
| Java | Java 21 LTS，编译release21；实施时记录JDK发行版和完整补丁版本 |
| Maven | 3.9.x；T01固定可获取的Wrapper版本，禁止动态版本范围 |
| Spring AI | 2.0.x稳定线；T01固定实施时可获取的具体稳定补丁版，禁止使用里程碑、候选版或Snapshot |
| Spring Boot | 4.0.x稳定线，当前具体版本固定4.0.8，由父POM/BOM统一管理 |
| JSON | P01发布实现仍为Jackson 2兼容模式；已批准目标为Spring Boot 4.0.8 BOM管理的Jackson 3.1.5，在P02发布后的独立阶段迁移 |
| MyBatis Starter | `org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1` |
| MySQL | 9.7.x LTS；本地验收部署制品固定为官方MySQL Server Docker镜像`9.7.2`（digest在T01锁定）；InnoDB、utf8mb4及公司要求的utf8mb4_bin。说明：T01实测时Docker Hub `library/mysql`尚无`9.7.3`标签，故锁定当时可获取的同线官方镜像`9.7.2` |
| 隔离与时间 | READ COMMITTED；连接session `time_zone='+00:00'`；DATETIME(0)存UTC；首期业务ZoneId为Asia/Shanghai |
| SQL迁移 | Flyway core + flyway-mysql，由Boot BOM管理版本，T01记录实际解析结果 |
| 驱动 | `com.mysql:mysql-connector-j`，由Boot BOM管理版本 |
| 测试 | Spring Boot Test/JUnit5；Surefire单元测试，Failsafe集成测试，ArchUnit架构测试 |
| 首期运行 | 同一构建制品启动两个本地Java进程，不同端口，共享独立测试库 |

Spring AI 2.0.x支持Spring Boot 4.0.x和4.1.x；Spring Boot 4.0.x最低要求Java 17，本项目固定使用Java 21 LTS；MyBatis Starter 4.0.1对应Spring Boot 4.0.x。上述只证明官方版本范围相容，不能代替本项目依赖解析、Spring上下文、Spring AI版本基线解析、MyBatis映射和真库测试。[Spring AI入门与兼容说明](https://docs.spring.io/spring-ai/reference/getting-started.html)；[Spring Boot系统要求](https://docs.spring.io/spring-boot/system-requirements.html)；[MyBatis Starter发布记录](https://github.com/mybatis/spring-boot-starter/releases)

T01必须验证Spring AI 2.0.x具体补丁版能够在dependencyManagement中解析，并验证Spring Boot 4.0.8、Java 21 LTS和MyBatis Starter 4.0.1能否与项目依赖实际解析、编译和启动；不兼容时记录阻塞并重新审核，不能自行升降版本。Spring AI使用2.0.x稳定线不等于允许动态版本范围，父POM必须固定实施时选定的具体补丁号。

首期没有AI业务能力，不引入任何Spring AI Starter、模型客户端或自动配置到运行依赖。父POM只固定经T01验证的Spring AI版本基线；S17或其他AI能力获批后，才由对应能力模块引入所需依赖并执行Spring上下文验证。

MySQL 9.7 LTS的InnoDB支持READ COMMITTED和队列领取所需的SKIP LOCKED；SKIP LOCKED只用于领取队列候选，不能作为一般一致性查询。官方9.7.3说明明确指出该补丁仅更新MySQL Server Docker镜像，因此“镜像标签/digest”和数据库内`SELECT VERSION()`返回值必须分别记录，验收不得硬编码两者字符串相同。[MySQL 9.7.3发布说明](https://dev.mysql.com/doc/relnotes/mysql/9.7/en/news-9-7-3.html)；[MySQL 9.7事务隔离](https://dev.mysql.com/doc/refman/9.7/en/innodb-transaction-isolation-levels.html)；[MySQL 9.7锁定读取](https://dev.mysql.com/doc/refman/9.7/en/innodb-locking-reads.html)

既有本地MySQL不符合9.7 LTS目标时，T01记录实际版本并使用上述固定官方镜像验证，或者提交明确的兼容性变更审核。T01必须保存镜像标签、不可变digest、`SELECT VERSION()`、`@@version_comment`和平台架构；标签或digest不符时失败，服务端返回9.7 LTS线内实际版本即可，不要求伪装成9.7.3。禁止使用其他项目数据库、H2、内存仓储或本机锁替代目标验证。

### 1.1 Jackson 3目标契约

本节是[P03](phases/P03/README.md)（X05）的实施契约。P01/P02已发布制品保持Jackson 2兼容行为；只有P03获授权后才可修改Jackson依赖、import、Mapper Bean或HTTP消息转换器。

迁移目标固定如下：

- 版本只使用Spring Boot 4.0.8 BOM当前管理的Jackson 3.1.5，父POM不另行覆盖`jackson.version`，不使用动态范围。
- `jackson-databind`使用`tools.jackson.core:jackson-databind`；`jackson-core`、databind及相关Java包迁移到`tools.jackson.*`。
- `jackson-annotations`是官方例外，继续使用`com.fasterxml.jackson.core:jackson-annotations`和`com.fasterxml.jackson.annotation.*`。禁止对注解包做机械替换。
- 删除`jackson-datatype-jsr310`、`jackson-datatype-jdk8`和`jackson-module-parameter-names`的显式依赖与手工注册；Jackson 3 databind已内建Java时间、Optional和参数名支持。
- 生产代码统一注入`tools.jackson.databind.json.JsonMapper`。只有真正格式无关的边界才可使用`ObjectMapper`，并必须在代码评审中说明原因。
- Jackson 3 Mapper按不可变对象使用，通过Boot的`JsonMapperBuilderCustomizer`或`JsonMapper.builder()`在构建前配置，禁止在Bean创建后调用可变`configure/set*`风格的兼容API。
- 全局JSON配置只属于`boot-loader` 的`configuration.json`组合根；`runtime`、`web`或业务模块不得另建全局Mapper。
- Spring MVC使用Boot自动配置的Jackson 3 `JacksonJsonHttpMessageConverter`；删除强制Jackson 2的`MappingJackson2HttpMessageConverter`替换逻辑。
- 直接引用Jackson类型的模块必须声明直接依赖，不得依赖其他项目模块传递Jackson。
- 迁移完成后，业务与一方案代码、直接依赖与打包入口不得再引入 Jackson 2 的`jackson-core`、`jackson-databind`和`com.fasterxml.jackson.datatype:*`；仅允许官方保留的 annotations 制品。已知例外：Flyway 11 在插件复制路径仍反射依赖`com.fasterxml.jackson.databind.ObjectMapper`，允许其作为**唯一**传递残留，且禁止业务代码 import 或注入该坐标。双 Mapper 只可用于迁移分支的临时对照，不得进入阶段交付。

迁移前必须通过黄金样例固定04定义的HTTP JSON、数据库JSON、幂等响应重放、S01/S02配置/快照/投影、Signal/Action/TransitionPlan载荷和哈希输入。任何用于`request_hash`、`payload_hash`、`config_hash`、`snapshot_hash`或Action Key的JSON必须使用项目显式定义的规范化规则，不得依赖Jackson 2或3的默认属性顺序。

### 1.2 Jackson 3迁移顺序与回滚

P03（X05）独立阶段必须按以下顺序执行，每一步保持可编译并运行对应的J01—J10子集：

1. 从P02发布标签记录依赖树、打包制品、Mapper Bean和转换器清单，运行HTTP、持久化JSON及哈希黄金契约。
2. 按实际直接使用关系把各模块POM切换到Jackson 3坐标；保留annotations官方例外，移除不再需要的datatype和parameter-names模块。
3. 迁移非注解import、API差异和异常类型；禁止机械替换`com.fasterxml.jackson.annotation`。
4. 在boot-loader建立唯一Jackson 3 JsonMapper构建配置，逐项显式恢复P01解析与输出行为；业务模块只注入，不自行配置。
5. 删除Jackson 2 MVC转换器覆盖，使用Boot原生Jackson 3转换器并运行全部HTTP契约。
6. 验证P01历史JSON可读、新旧哈希与幂等结果一致，再执行单元、打包、真MySQL和双进程全量回归。
7. 扫描源码、依赖树和最终制品，清除Jackson 2核心残留、双Mapper桥接与临时豁免后才可交付。

迁移不修改数据库JSON内容或API版本，因此不需要数据迁移脚本。任一黄金契约无法恢复、历史JSON不可读或哈希变化时，停止并回退到P03的`baselineGitRef`；不得通过批量重写数据库JSON、清空幂等记录或同时发布API变更规避失败。若确需改变兼容行为，先将阶段转BLOCKED并按08单独评审。

## 2. 模块构建与装配

父POM必须显式列出[02](02-AI-CODING-GUIDE.md)批准的模块（P01—P04 为13个；[P05](phases/P05/README.md) READY 后含`timeimprint-task-adapter`共14个）。所有service模块使用平级`timeimprint-task-service-*`名称；HTTP传输契约模块固定为`timeimprint-task-domain`，接入编排模块固定为`timeimprint-task-gateway`；第三方 SDK 宿主固定为`timeimprint-task-adapter`。不得再引入平行的`timeimprint-task-api`、`timeimprint-task-api-gateway`、`timeimprint-task-dao`、巨型`timeimprint-task-service`、`timeimprint-task-cache`或任何cache替代模块。

`timeimprint-task-boot-loader`是唯一组合根，负责：

- 选择storage、capability和scenario实现并加入运行时类路径。
- 加载@ConfigurationProperties及第三方Bean。
- 汇总Flyway迁移目录。
- 注册ScenarioExtension、TriggerProvider、TaskCommandHandler、ActionHandler和Policy。
- 注册场景专有数据物化器以及local/test环境的ActorContextProvider。
- 提供应用启动入口以及mysql-it、dual-process-it集成测试入口。

其他模块不得通过扫描整个classpath、反射字符串类名或ServiceLoader绕过显式装配。允许Spring按类型收集已引入模块的实现，但boot-loader必须清楚声明制品依赖；新增扩展需要修改根POM和组合根不算修改内核。

Maven Enforcer至少检查Java/Maven版本、依赖收敛、禁止循环和禁止内核引入Spring/MyBatis/Web依赖。ArchUnit至少检查：kernel不引用框架和外层模块；场景不引用storage、其他场景或adapter；能力不反向引用场景；web不越过gateway/application访问Mapper；adapter不依赖kernel/scenario/application；飞书等官方 SDK 坐标只允许出现在adapter模块。

## 3. 配置入口

凭据没有文档默认值。DB_JDBC_URL必须是MySQL且包含明确数据库名，应用不得通过URL自动创建、删除或清空数据库。

| 环境变量 | 默认值 / 约束 |
| --- | --- |
| `SERVER_ADDRESS` | local与test profile固定`127.0.0.1`且不允许外部覆盖为非回环地址；其他profile必须由部署明确提供并先完成可信身份与网络审核 |
| `SERVER_PORT` | 8080；第二进程默认8081 |
| `DB_JDBC_URL` | 必填，例如`jdbc:mysql://127.0.0.1:3306/timeimprint-task_local`，不含密码 |
| `DB_USERNAME` / `DB_PASSWORD` | 必须外部注入，不得写入仓库或日志 |
| `INSTANCE_ID` | 每进程唯一，1—64字符；用于诊断和租约所有者标识 |
| `WORKER_ENABLED` | 默认true；false时只提供API和查询，不领取后台工作 |
| `TRIGGER_SCAN_MS` | 默认1000，范围100—60000 |
| `SIGNAL_SCAN_MS` | 默认1000，范围100—60000 |
| `ACTION_SCAN_MS` | 默认1000，范围100—60000 |
| `CLAIM_BATCH_SIZE` | 默认50，范围1—100；实际领取不超过线程池空闲容量 |
| `SIGNAL_THREADS` | 默认4，范围1—16，等待队列容量为0 |
| `ACTION_THREADS` | 默认4，范围1—16，等待队列容量为0 |
| `DB_POOL_MAX` | 默认20，必须不少于SIGNAL_THREADS + ACTION_THREADS + 4 |
| `LEASE_SECONDS` | 默认30，范围10—300；一期不提供无限续租 |
| `BUSINESS_TX_TIMEOUT_SECONDS` | 默认5，范围1—30；包括锁等待和数据库写入，不包括事务外EXTERNAL调用 |
| `ACTION_LEASE_SAFETY_SECONDS` | 默认5，范围1—30，且必须小于LEASE_SECONDS |
| `SHUTDOWN_GRACE_SECONDS` | 默认40，范围10—300；停止领取、拒绝新写请求并等待短事务/Handler闭合的总宽限 |
| `LOCAL_TENANT_ID` | 必填的单一租户标识；HTTP 请求不得覆盖。P06 起不再有固定调用主体配置 |

### 3.1 通知投递与飞书（P05）

下列项由 Spring 配置（如`application.yml` / 环境变量绑定）拥有，**不是**场景`scenarioConfig`字段。未启用飞书时不得要求飞书凭据。

| 配置键 | 默认 / 约束 |
| --- | --- |
| `timeimprint.notification.delivery-channels` | 列表；默认仅`IN_APP`。P05 合法值：`[IN_APP]`或`[IN_APP, FEISHU]`；缺少`IN_APP`则启动失败 |
| `timeimprint.notification.feishu.app-id` | 启用`FEISHU`时必填；不得写入仓库 |
| `timeimprint.notification.feishu.app-secret` | 启用`FEISHU`时必填；不得写入仓库或日志 |
| `timeimprint.notification.feishu.verification-token` | 启用飞书入站回调时必填；用于校验回调 |
| `timeimprint.notification.feishu.encrypt-key` | 可选；若开发者后台开启加密则必填 |
| `timeimprint.notification.feishu.base-url` | 默认`https://open.feishu.cn` |
| `timeimprint.notification.feishu.timeout-seconds` | 必须落入该飞书 ActionHandler 声明的`timeoutSeconds`预算内 |
| `timeimprint.notification.feishu.recipient-map.<platformUserId>` | 平台`recipientId`→飞书`open_id`；缺映射时仍创建飞书 Action，执行结果为`PERMANENT_FAILURE` |
| 飞书卡片回调路径 | 固定`POST /callbacks/v1/feishu/card-action`（web 模块）；须在飞书开发者后台配置为可公网访问的请求地址；本地联调可用隧道，**不进 CI** |

启用`FEISHU`时：出站与入站配置须齐全，否则启动失败。默认`delivery-channels`不含`FEISHU`时，不装配对外飞书发信，回调入口可拒绝或未映射。

平台常量：`PLANNING_DAYS=7`、`PLAN_BATCH=100`、`MAX_TECH_ATTEMPTS=5`、`MAX_MANUAL_REDRIVES=3`。通用技术重试在第1—4次失败后分别延迟5、30、120、600秒，第5次失败进入DEAD。

单次事务硬上限：

| 常量 | 值 | 约束对象 |
| --- | ---: | --- |
| `MAX_PARTICIPANTS_PER_SCOPE` | 50 | 一个定义或实例的有效参与关系 |
| `MAX_RECIPIENTS_PER_INSTANCE` | 10 | 去重后的最终通知接收人 |
| `MAX_TRIGGER_BINDINGS_PER_DEFINITION` | 8 | 一个定义的触发绑定；S01/S02首期场景校验进一步限制为1个calendar绑定 |
| `MAX_OCCURRENCES_PER_WRITE_TX` | 100 | 同步创建或Planner单笔事务生成的发生总数，不按binding分别放大 |
| `MAX_ACTIONS_PER_TRANSITION` | 100 | 一个TransitionPlan产生的Action数 |
| `MAX_ACTIONS_PER_WRITE_TX` | 500 | 单笔事务产生的Action总数 |
| `MAX_SCENARIO_MUTATIONS_PER_TRANSITION` | 32 | 一个TransitionPlan的专有数据变更数 |
| `MAX_SCENARIO_MUTATION_BYTES` | 65536 | 一个TransitionPlan全部专有数据变更规范化后的UTF-8总字节数 |
| `MAX_JSON_VALUE_BYTES` | 65536 | 单个配置、快照、Signal、Action、参与人metadata或扩展结果JSON的规范化UTF-8字节数 |
| `MAX_TRANSITION_PLAN_BYTES` | 1048576 | 一个TransitionPlan全部声明内容规范化后的UTF-8总字节数 |
| `MAX_MUTATED_ROWS_PER_WRITE_TX` | 2000 | 单笔事务预计插入、更新和逻辑终结的总行数 |

上限在进入写事务前根据强类型计划校验；Planner重算后仍需在锁内复核。预计行数包括参与人复制、Transition、Signal、Action、Attempt、通知、收件、审计和专有数据；提交器还要累计实际受影响行数，超过上限立即抛错并回滚，防止估算遗漏。总字节数不能通过把一个大计划拆成多个JSON字段绕过。超限整体失败或缩小尚未开始的后台候选批次，不能截断一个TransitionPlan，也不能提交部分同步创建结果。S01的24小时有效期和S02默认`chaseOffsetsMinutes=[60,240,720]`、`notificationExpireAfterMinutes=1440`、`maxSnoozeCount=3`属于scenario-basic配置，不是所有Action的全局常量。

所有进程必须使用相同数据库、时区、扩展版本和业务常量。端口、INSTANCE_ID、WORKER_ENABLED、扫描间隔、线程数和连接池可按进程调整，但必须满足范围。启动日志只记录去敏配置摘要。

生产业务Clock使用`Clock.systemUTC()`并截断到整秒，业务显示按ZoneId转换；租约有效性以数据库`UTC_TIMESTAMP(0)`为准。HTTP请求不得传入或覆盖系统“当前时间”。

P01 基线没有京ME、邮件、外部日历、模型服务、AI业务能力或cache配置。[P05](phases/P05/README.md) 起可按 §3.1 **显式启用**飞书消息；默认仍不启用。[P06](phases/P06/README.md) 起账号登录为公开身份的唯一方式。引入Spring AI版本基线不等于启用AI功能；未来某个实现只有在boot-loader明确启用时才校验其专属配置，已启用实现缺少凭据或端点必须启动失败，未启用实现不得要求无关凭据。

### 3.2 账号、短信与微信（P06）

身份不再按 profile 切换。local 与 test 只区分数据库、端口、监听地址和预置账号。公开任务接口从 `Authorization: Bearer` 解析用户。缺少令牌、令牌摘要不匹配或账号停用时返回 401，不能退回固定身份。

| 配置键 | 默认 / 约束 |
| --- | --- |
| `timeimprint.identity.authorization-string` | 必填；一次性登录授权字符串。只从环境读取，不得写入仓库或日志 |
| `timeimprint.identity.sms.secret-id` / `secret-key` | 正式发短信时必填；不得写入仓库或日志 |
| `timeimprint.identity.sms.sdk-app-id` / `sign-name` / `template-id` | 正式发短信时必填；不得写入仓库 |
| `timeimprint.identity.sms.mode` | `tencent` 或 `capture`。自动化测试只用 `capture`，验证码留在测试可读记录，不访问腾讯云 |
| `timeimprint.identity.wechat.<appType>.app-id` / `app-secret` / `redirect-uri` | `appType` 为 `web`、`ios`、`android`。启用对应微信登录时必填 |
| 飞书扫码登录 | 本期不配置、不装配 |

测试库由测试夹具创建测试账号，口令只出现在测试代码。本地库不在仓库中写死口令；开发者用短信注册本地开发账号。短信正式模式缺少凭据，或已启用的微信应用缺少凭据时，对应登录方式启动失败。`capture` 模式不得出现在面向真实用户的部署。

local与test profile的HTTP监听地址必须是回环地址，内部端点和健康检查也不得暴露到局域网。若部署者把`SERVER_ADDRESS`改为非回环地址，应用必须启动失败；这一限制只能在增加正式身份、网络入口和安全审核后的新profile中解除。

每个ActionHandler在注册描述中声明`timeoutSeconds`，范围1至`LEASE_SECONDS - ACTION_LEASE_SAFETY_SECONDS`。IN_APP等LOCAL_TRANSACTIONAL处理器还受BUSINESS_TX_TIMEOUT_SECONDS限制；EXTERNAL处理器必须把连接、读取和总调用超时控制在timeoutSeconds内。无法满足时启动失败，不以运行时续租掩盖不受控调用。

## 4. 数据库迁移与启动顺序

迁移资源按所有权放置：

```text
timeimprint-task-service-storage-mysql/src/main/resources/db/migration/platform/
timeimprint-task-service-capability-*/src/main/resources/db/migration/capability/<capabilityKey>/
timeimprint-task-service-scenario-*/src/main/resources/db/migration/scenario/<scenarioKey>/
timeimprint-task-identity/src/main/resources/db/migration/identity/
```

平台公共表迁移只属于storage-mysql。通知收件、投递等专有表属于capability-notification；账号、验证码、会话和社交绑定属于 identity；审批、缴费等专有表属于对应scenario模块。模块没有专有数据时不创建空迁移目录或空表。

所有已启用模块的迁移版本在整个应用内唯一，文件名固定为`VyyyyMMddHHmmss__<owner>_<description>.sql`，时间部分使用UTC且同一秒只能分配一个版本；owner使用platform、capability key、scenario key或identity。boot-loader显式汇总locations。已执行迁移不得原地修改，升级只能新增版本。Flyway失败时应用不就绪；禁止clean；多进程并发启动必须验证Flyway协调，不能重复初始化或重置业务数据。

启动顺序固定为：

1. 校验环境变量、数据库产品、库名、UTC和隔离级别。
2. 执行并校验已启用模块的Flyway迁移。
3. 构建五类扩展及ScenarioDataMaterializer注册表，检查key唯一、契约版本和schemaVersion兼容性。
4. 校验场景声明所需Trigger、Action、Policy和专有数据物化器全部存在。
5. 查询非终结数据使用到的scenario/provider/handler key及schemaVersion；任一版本没有兼容读取器时阻止就绪。历史终态数据可以使用公共快照读取，不要求已卸载扩展重新执行。
6. 启动HTTP监听；liveness只表示进程主循环可运行，不能依赖数据库。
7. 数据库校验、迁移、注册表、存量版本检查和Worker初始化全部成功后，readiness才变为ACCEPTING_TRAFFIC。

重复扩展key、缺少处理器、配置版本无法读取、迁移失败或数据库不可用都必须阻止就绪。不得切换到内存成功模式。当前不实现运行时上传代码；扩展变化通过新构建制品发布。

boot-loader引入Spring Boot Actuator，仅在回环地址提供`/actuator/health/liveness`与`/actuator/health/readiness`，不开放env、beans、heapdump等管理端点。readiness至少汇总数据库可连接、Flyway已完成、扩展注册完整、未终结schema可读和Worker初始化结果；运行中数据库失联时readiness转REFUSING_TRAFFIC，liveness保持UP以便外部决定是否重启。

## 5. 双进程与恢复边界

双进程使用同一制品、同一数据库和相同扩展集合，只允许端口及INSTANCE_ID不同。两个进程都启用Worker时，Signal和Action Job通过数据库租约竞争；正确性不能依赖INSTANCE_ID绝对唯一、线程中断或本机锁。

收到正常停止信号时，顺序固定为：readiness先转REFUSING_TRAFFIC；拒绝新的写请求并停止Planner/Signal/Action领取；允许已进入的短事务和已开始Handler在`SHUTDOWN_GRACE_SECONDS`内闭合；随后停止HTTP并退出。超时不延长租约、不批量把RUNNING改成READY，由另一进程在租约到期后恢复。旧队列执行者恢复后必须因status + executionToken条件不匹配而无法提交；涉及业务资源的旧计算还必须受revision阻断。只读请求是否在宽限期继续由Web服务器优雅停机机制决定，但不得在拒绝新写后重新接受写入。

外部副作用开始前遇到父级暂停或政策阻断，可以安全释放执行权；副作用已经发起但结果无法确认时记录UNKNOWN，不得直接当作未执行。首期站内信在本地数据库闭环，不据此宣称未来外部渠道exactly-once。

## 6. 运行观测

日志、指标或诊断SQL必须能按以下标识关联：traceId、definitionId、instanceId、signalId、transitionId、actionJobId、executionToken、scenarioKey、providerKey和handlerKey。executionToken只记录是否存在或脱敏摘要，不输出原值。

至少观测：

- triggerLag、Signal积压及最老积压时间。
- Transition冲突、幂等冲突和非法迁移数量。
- Action READY/RUNNING/RETRY_WAIT/UNKNOWN/DEAD数量及最老积压时间。
- 租约回收、旧token拒绝、扩展缺失和迁移失败。
- 通知能力自己的渠道成功、失败、未知和站内信可查询延迟。

首次进入DEAD、UNKNOWN持续超限、积压超过阈值、重复租约回收和扩展缺失必须产生ERROR级结构化日志。首期不要求接入独立监控平台，也不默认开放管理HTTP端点；运行手册提供安全诊断SQL。日志不得包含通知正文、场景敏感载荷、密码、连接串凭据或访问令牌。

## 7. 环境验收与阶段边界

T01必须记录`java -version`、Wrapper版本、effective POM、依赖树、MySQL镜像标签与digest、`SELECT VERSION()`、`@@version_comment`、隔离级别和session时区，并验证13模块依赖图、Spring装配、回环监听、liveness/readiness和无库单元测试。mysql-it验证迁移、约束、MyBatis映射和锁语义；dual-process-it验证两个真实进程的领取、崩溃、优雅停机和租约恢复。

缺少真实MySQL只阻塞真库部分，不能改用H2后宣称通过。未执行项记录NOT_RUN或BLOCKED；03文档通过只确认目标技术环境和验收方法，不代表环境已经验证，也不单独放行T01。生产网络、身份、容量、备份和部署仍不在首期本地验收范围。

真库验收实例是一次性的。每次运行 mysql-it 或 dual-process-it 之前，在 MyStudio 仓库执行 `Deploy/scripts/06-recreate-task-acceptance-mysql.sh`，删除并重建容器 `tit-mysql-t01`（只监听 `127.0.0.1:13306`，镜像 `mysql:9.7.2`）。脚本给出空库 `timeimprint_task_local` 与 `timeimprint_task_perf`，关闭 binlog，容器停止后连同数据卷删除。测试数据不保留。测试进程内仍不得 CREATE DATABASE、DROP DATABASE、TRUNCATE 或 Flyway clean，见 [07](07-ACCEPTANCE.md)。使用该容器的全量验证由代理在修改代码后自动执行，命令是 `Deploy/scripts/07-run-task-acceptance.sh`，先 `mysql-it`，通过后再 `dual-process-it`。单次不得超过 20 分钟；超时终止本次 Maven 进程并删除容器，结果为失败。人工可以执行同一命令。单元测试不在这条限制内，也不通过 Surefire 或 Failsafe 的进程超时实现。
