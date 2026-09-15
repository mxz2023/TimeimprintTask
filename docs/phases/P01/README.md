# P01 · 第一期稳定核心与基础场景

## 1. 阶段身份

| 项目 | 值 |
| --- | --- |
| 阶段编号 | P01 |
| 阶段角色 | RELEASED |
| 总体状态 | RELEASED |
| 核心文档基线 | 2.2（2026-09-12） |
| 基础发布 | 无；P01是初始实现 |
| baselineGitRef | 无可用不可变提交基线；见[BASELINE-SHA256.txt](BASELINE-SHA256.txt)（写代码前核心文档清单） |
| 工程状态 | RELEASED |
| Git发布标签 | `p01`（打在本 RELEASED 文档冻结提交上） |
| 人工验收 | 2026-09-15 用户确认通过 |
| 下一任务 | 无；从[场景索引](../../scenarios/README.md)/[09](../../09-SCENARIO-ROADMAP.md)的 NEXT_REVIEW 选择范围后创建下一阶段 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md)（冻结，不再改写） |
| 交付证据 | [DELIVERY.md](DELIVERY.md)（冻结，不再改写） |
| 手动HTTP联调 | [MANUAL-HTTP.md](MANUAL-HTTP.md)（curl）；可选本地 Vue 控制台 [TaskWebsite](../../TaskWebsite/README.md) |

P01 已由用户人工验收通过并形成发布基线。证据见[DELIVERY](DELIVERY.md)；S01/S02 与 CAP01/CAP03 的 P01 范围已标 VERIFIED。本阶段 README、IMPLEMENTATION、DELIVERY 此后不再改写。

## 2. 此前已经完成

- 将旧项目命名统一为TimeImprintTask、工程前缀`timeimprint-task`、表前缀`tt_`。
- 明确项目自定义 Java 类型不使用 `Mxz` 类名前缀。
- 完成2.2业务、场景、能力、架构、技术环境、API、数据库、调度和验收契约评审。
- 固定本地身份接入但完整稳定核心的首期边界，以及S01/S02第一版业务规则。
- 固定Command/Signal边界、TransitionPlan提交、幂等、父级锁序、事务规模、租约恢复、公开/内部读模型和12张表结构。
- 固定INV-01—INV-09跨阶段核心不变量，并要求用架构、契约和真库测试防止实现偏离。
- 删除旧项目`WORKLOG.md`及审核流水；它们不再是开发输入。

以上全部是文档成果，不是已经实现的产品能力。

## 3. 本期目标

- 建立13个平级Maven模块及03规定的技术基线。
- 实现稳定kernel、扩展契约、公共MySQL存储、统一TransitionPlan提交器和Signal/Action运行时。
- 实现本地固定ActorContextProvider，但不得把固定身份写入kernel业务语义。
- 实现calendar五种规则、S01 reminder、S02 recurring_todo和IN_APP站内收件。
- 用Approval、EventTrigger、WebhookAction测试夹具证明新增场景/触发/动作不需要修改kernel和公共DDL。
- 完成真HTTP、真MySQL、双进程、恢复、公平性和本地性能验收。

## 4. 本期不做

- 生产多租户身份、生产部署和生产容量承诺。
- 飞书、京ME、邮件、Webhook等真实外部渠道接入。
- MQ、cache、工作流引擎、动态插件和通用补偿框架。
- Spring AI模型运行、自然语言配置和场景索引中除S01/S02之外的规划场景。
- GUI、管理后台或以Swagger页面代替API/README交付。

## 5. 必读文档

实施前必须依次读取：

1. [00开发导航](../../00-READING-ORDER.md)。
2. [01公共业务契约](../../01-MVP-SPEC.md)。
3. [场景索引](../../scenarios/README.md)、[S01](../../scenarios/S01-reminder.md)和[S02](../../scenarios/S02-recurring-todo.md)。
4. [能力索引](../../capabilities/README.md)、[calendar](../../capabilities/CAP01-calendar.md)和[notification](../../capabilities/CAP03-notification.md)。
5. [02架构与编码契约](../../02-AI-CODING-GUIDE.md)。
6. [03环境契约](../../03-INTEGRATION-CONTRACTS.md)。
7. [04 API契约](../../04-API.md)。
8. [05数据库契约](../../05-DATABASE.md)。
9. [06运行协议](../../06-SCHEDULING.md)。
10. [07验收契约](../../07-ACCEPTANCE.md)。
11. [08实施治理](../../08-AI-IMPLEMENTATION-TASKS.md)。
12. [本期实施任务](IMPLEMENTATION.md)。
13. [09演进路线](../../09-SCENARIO-ROADMAP.md)，用于确认哪些内容不属于本期。

[10技术评审](../../10-TECHNICAL-REVIEW.md)只用于理解设计理由和风险，可以按需查阅，不能覆盖正式契约。

## 6. 允许与禁止

P01是首次实现阶段，允许按02创建约定的13个模块，并按05创建首版公共及通知能力迁移。所有实现必须沿正式契约完成，不得使用内存仓储、缩减临时表、同步直写收件或场景专用内核分支形成假闭环。

未经重新评审禁止：

- 改变01或S01/S02场景契约中的业务行为、次数、时间窗口或暂停恢复语义。
- 改变03锁定的版本线，或将Spring AI运行依赖引入首期业务。
- 增减04的公开字段和端点，或建立第二套任务状态接口。
- 绕过05/06的幂等、Transition、锁序、租约和原子事务。
- 实现场景/能力索引中不属于P01或仍为OUTLINE的能力项与场景。
- 部署生产环境、连接非允许数据库、写真实凭据或发送真实外部通知。

## 7. 完成条件（已满足）

T01—T08 均已 PASS，`DELIVERY.md` 已记录真实环境与结果，并经 2026-09-15 用户人工验收；P01 已转为 RELEASED（Git 标签 `p01`）。本 README、IMPLEMENTATION、DELIVERY 此后不再改写。
