# 03 · 技术基线、环境与模块装配契约

> 人工审核与阶段准入以[00审核台账](00-READING-ORDER.md)为准。本文2.0已于2026-09-10 20:45通过人工审核；这只批准环境与装配契约，不代表实际环境已验证或放行工程实施。

版本2.0；前：[架构](02-AI-CODING-GUIDE.md)，后：[API](04-API.md)。本文定义一期13模块使用的技术基线、配置、迁移加载、扩展注册、运行观测和环境验收边界，不声称已完成本机验证。

## 1. 固定技术基线

| 项目 | 契约 |
| --- | --- |
| Java | JDK17，编译release17；实施时记录发行版和完整补丁版本 |
| Maven | 3.9.x；T01固定可获取的Wrapper版本，禁止动态版本范围 |
| Spring Boot | 因现有公司基建约束固定3.4.11，由父POM/BOM统一管理 |
| MyBatis Starter | `org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.5` |
| MySQL | 8.4.x LTS、InnoDB、utf8mb4及公司要求的utf8mb4_bin |
| 隔离与时间 | READ COMMITTED；连接session `time_zone='+00:00'`；DATETIME(0)存UTC；首期业务ZoneId为Asia/Shanghai |
| SQL迁移 | Flyway core + flyway-mysql，由Boot BOM管理版本，T01记录实际解析结果 |
| 驱动 | `com.mysql:mysql-connector-j`，由Boot BOM管理版本 |
| 测试 | Spring Boot Test/JUnit5；Surefire单元测试，Failsafe集成测试，ArchUnit架构测试 |
| 首期运行 | 同一构建制品启动两个本地Java进程，不同端口，共享独立测试库 |

Spring Boot 3.4要求Java 17或以上；MyBatis Starter 3.0.x声明支持Java 17以及Spring Boot 3.2—3.5。上述只证明官方版本范围相容，不能代替本项目依赖解析、Spring上下文、MyBatis映射和真库测试。[Spring Boot 3.4系统要求](https://docs.spring.io/spring-boot/3.4/system-requirements.html)；[MyBatis Starter兼容说明](https://github.com/mybatis/spring-boot-starter)

Spring Boot 3.4.x已经退出开源维护，3.4.11是公司基建兼容约束，不是长期生产推荐。T01必须验证公司依赖能否与该组合实际解析和启动；不兼容时记录阻塞并重新审核，不能自行升降版本。生产上线前必须重新确认受维护版本或商业支持来源。

MySQL 8.4 InnoDB支持READ COMMITTED和队列领取所需的SKIP LOCKED；SKIP LOCKED只用于领取队列候选，不能作为一般一致性查询。[MySQL 8.4事务隔离](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html)；[MySQL 8.4锁定读取](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)

既有本地MySQL不符合8.4目标时，T01记录实际版本并在专用8.4实例验证，或者提交明确的兼容性变更审核。禁止使用其他项目数据库、H2、内存仓储或本机锁替代目标验证。

## 2. 模块构建与装配

父POM必须显式列出02批准的13个一期模块。所有service模块使用平级`joytask-service-*`名称；不得生成旧`joytask-domain`、`joytask-dao`、巨型`joytask-service`、`joytask-cache`或任何cache替代模块。

`joytask-boot-loader`是唯一组合根，负责：

- 选择storage、capability和scenario实现并加入运行时类路径。
- 加载@ConfigurationProperties及第三方Bean。
- 汇总Flyway迁移目录。
- 注册ScenarioExtension、TriggerProvider、TaskCommandHandler、ActionHandler和Policy。
- 提供应用启动入口以及mysql-it、dual-process-it集成测试入口。

其他模块不得通过扫描整个classpath、反射字符串类名或ServiceLoader绕过显式装配。允许Spring按类型收集已引入模块的实现，但boot-loader必须清楚声明制品依赖；新增扩展需要修改根POM和组合根不算修改内核。

Maven Enforcer至少检查Java/Maven版本、依赖收敛、禁止循环和禁止内核引入Spring/MyBatis/Web依赖。ArchUnit至少检查：kernel不引用框架和外层模块；场景不引用storage或其他场景；能力不反向引用场景；web不越过gateway/application访问Mapper。

## 3. 配置入口

凭据没有文档默认值。DB_JDBC_URL必须是MySQL且包含明确数据库名，应用不得通过URL自动创建、删除或清空数据库。

| 环境变量 | 默认值 / 约束 |
| --- | --- |
| `SERVER_PORT` | 8080；第二进程默认8081 |
| `DB_JDBC_URL` | 必填，例如`jdbc:mysql://127.0.0.1:3306/joytask_local`，不含密码 |
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

平台常量：`PLANNING_DAYS=7`、`PLAN_BATCH=100`、`MAX_TECH_ATTEMPTS=5`。通用技术重试在第1—4次失败后分别延迟5、30、120、600秒，第5次失败进入DEAD。S01的24小时有效期、S02的催办和稍后提醒上限属于scenario-basic规则，不能伪装成所有Action的全局常量。

所有进程必须使用相同数据库、时区、扩展版本和业务常量。端口、INSTANCE_ID、WORKER_ENABLED、扫描间隔、线程数和连接池可按进程调整，但必须满足范围。启动日志只记录去敏配置摘要。

生产业务Clock使用`Clock.systemUTC()`并截断到整秒，业务显示按ZoneId转换；租约有效性以数据库`UTC_TIMESTAMP(0)`为准。HTTP请求不得传入或覆盖系统“当前时间”。

首期没有飞书、京ME、邮件、Webhook、外部日历、用户目录、AI或cache配置。未来某个实现只有在boot-loader明确启用时才校验其专属配置；已启用实现缺少凭据或端点必须启动失败，未启用实现不得要求无关凭据。

## 4. 数据库迁移与启动顺序

迁移资源按所有权放置：

```text
joytask-service-storage-mysql/src/main/resources/db/migration/platform/
joytask-service-capability-*/src/main/resources/db/migration/capability/<capabilityKey>/
joytask-service-scenario-*/src/main/resources/db/migration/scenario/<scenarioKey>/
```

平台公共表迁移只属于storage-mysql。通知收件、投递等专有表属于capability-notification；审批、缴费等专有表属于对应scenario模块。模块没有专有数据时不创建空迁移目录或空表。

所有已启用模块的迁移版本在整个应用内唯一，建议使用带日期时间的版本号；boot-loader显式汇总locations。已执行迁移不得原地修改，升级只能新增版本。Flyway失败时应用不就绪；禁止clean；多进程并发启动必须验证Flyway协调，不能重复初始化或重置业务数据。

启动顺序固定为：

1. 校验环境变量、数据库产品、库名、UTC和隔离级别。
2. 执行并校验已启用模块的Flyway迁移。
3. 构建五类扩展注册表，检查key唯一、契约版本和schemaVersion兼容性。
4. 校验场景声明所需Trigger、Action和Policy全部存在。
5. 启动API与Worker并进入就绪状态。

重复扩展key、缺少处理器、配置版本无法读取、迁移失败或数据库不可用都必须阻止就绪。不得切换到内存成功模式。当前不实现运行时上传代码；扩展变化通过新构建制品发布。

## 5. 双进程与恢复边界

双进程使用同一制品、同一数据库和相同扩展集合，只允许端口及INSTANCE_ID不同。两个进程都启用Worker时，Signal和Action Job通过数据库租约竞争；正确性不能依赖INSTANCE_ID绝对唯一、线程中断或本机锁。

停止进程时先停止新领取，再等待正在提交的短事务；超时退出后由租约恢复。不得批量把RUNNING手工改成READY。旧执行者恢复后必须因executionToken或revision不匹配而无法提交。

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

T01必须记录`java -version`、Wrapper版本、effective POM、依赖树、数据库版本、隔离级别和session时区，并验证13模块依赖图、Spring装配和无库单元测试。mysql-it验证迁移、约束、MyBatis映射和锁语义；dual-process-it验证两个真实进程的领取、崩溃和租约恢复。

缺少真实MySQL只阻塞真库部分，不能改用H2后宣称通过。未执行项记录NOT_RUN或BLOCKED；03文档通过只确认目标技术环境和验收方法，不代表环境已经验证，也不单独放行T01。生产网络、身份、容量、备份和部署仍不在首期本地验收范围。
