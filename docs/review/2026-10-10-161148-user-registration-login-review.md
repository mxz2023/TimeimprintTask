# 用户注册、登录与多用户隔离代码审查报告

## 审查信息

| 项目 | 内容 |
| --- | --- |
| 审查时间 | 2026-10-10 16:11:48 CST（Asia/Shanghai） |
| 当前阶段 | P06，`CURRENT / IMPLEMENTING` |
| 当前任务 | T02，`IN_PROGRESS` |
| 审查范围 | 当前工作区中用户注册、登录、身份绑定、会话认证及多用户数据访问相关改动 |
| 审查基线 | `844d2ffeddfb3583230637fab57dd83f565c97ec` |
| 审查结论 | **阻塞合并**。存在跨用户越权、生产环境固定凭证和认证流程可绕过等高风险问题。 |

本报告是审查时点的历史快照，不替代正式契约、阶段状态或 `DELIVERY.md` 中的验证证据。

## 审查发现

### P0：同一租户内缺少用户级数据隔离

列表和详情查询主要按租户过滤，没有把当前用户限定为任务的拥有者、参与者或被授权者；实例命令和定义控制路径也未执行等价的用户级授权判断。登录后只要知道对象标识，同租户用户可能读取或操作其他用户的任务。

涉及位置：

- [`ListQueryService.java`](../../timeimprint-task-service-application/src/main/java/cn/net/mxz/timeimprint/task/service/application/shared/service/ListQueryService.java)
- [`TaskQueryService.java`](../../timeimprint-task-service-application/src/main/java/cn/net/mxz/timeimprint/task/service/application/shared/service/TaskQueryService.java)
- [`InstanceCommandService.java`](../../timeimprint-task-service-application/src/main/java/cn/net/mxz/timeimprint/task/service/application/instance/service/InstanceCommandService.java)
- [`DefinitionControlExecutor.java`](../../timeimprint-task-service-application/src/main/java/cn/net/mxz/timeimprint/task/service/application/definition/service/DefinitionControlExecutor.java)

影响：违反多用户隔离要求，可能造成任务数据泄露和未授权状态变更。应在查询与命令入口统一执行主体授权策略，并增加两个普通用户之间的负向集成测试。

### P0：应用启动时无条件创建固定账号和已知令牌

`IdentityBootstrap` 作为常规组件在所有环境执行，启动时创建四个固定账号和可预测的原始 Bearer 令牌，并会重置对应会话。配置项 `timeimprint.identity.bootstrap.enabled` 未参与该组件的启停判断。

涉及位置：

- [`IdentityBootstrap.java`](../../timeimprint-task-identity/src/main/java/cn/net/mxz/timeimprint/task/identity/account/service/IdentityBootstrap.java)

影响：任何部署环境都可能暴露已知认证凭证。应默认禁用，只允许在明确的本地开发或测试 Profile 中启用，并确保生产配置无法回退到该行为。

### P1：认证外部依赖采用不安全的成功默认值

授权字符串默认值为 `local-dev-authorization`；短信和微信客户端默认使用 capture 模式，未知模式也回退到 capture。配置缺失时，系统不是拒绝启动或拒绝认证，而是进入模拟成功路径。

涉及位置：

- [`application.yml`](../../timeimprint-task-bootstrap/src/main/resources/application.yml)
- [`IdentityClientConfiguration.java`](../../timeimprint-task-identity/src/main/java/cn/net/mxz/timeimprint/task/identity/config/IdentityClientConfiguration.java)

影响：部署配置错误可能静默降低认证强度。生产相关模式应 fail closed；capture 客户端只能由测试或显式开发 Profile 装配。

### P1：腾讯短信实现未形成可用的真实发送请求

当前实现未完成腾讯云短信所需的请求签名、动作、版本、应用标识、签名、模板参数等协议要素，发送内容中也没有完整承载验证码。

涉及位置：

- [`TencentSmsSender.java`](../../timeimprint-task-identity/src/main/java/cn/net/mxz/timeimprint/task/identity/sms/client/TencentSmsSender.java)

影响：切换到真实短信模式后注册、登录或找回密码流程无法可靠工作。需要基于官方协议或 SDK 实现，并用可控的契约测试验证请求内容和错误处理。

### P1：短信验证码可在有效期内重复使用

验证码校验只检查匹配与有效期，没有原子消费、用途绑定、失败次数限制或发送频率限制。验证码成功使用后仍可在十分钟内继续用于敏感操作。

涉及位置：

- [`IdentityService.java`](../../timeimprint-task-identity/src/main/java/cn/net/mxz/timeimprint/task/identity/account/service/IdentityService.java)

影响：泄露或截获的验证码可以被重放，尤其会放大密码重置和身份绑定风险。验证码记录应绑定用途与主体，成功后原子失效，并限制尝试次数及发送频率。

### P1：首个管理员判定存在语义偏差和并发竞态

注册流程在授权完成前通过 `countUsers() == 0` 决定管理员身份。并发注册可能同时看到空库并获得管理员权限；启动引导账号还会导致首个真实完成授权的用户无法成为管理员。

涉及位置：

- [`IdentityService.java`](../../timeimprint-task-identity/src/main/java/cn/net/mxz/timeimprint/task/identity/account/service/IdentityService.java)

影响：与“首个完成授权的用户”契约不一致，并可能产生多个管理员。需要用数据库唯一约束、锁或单次初始化记录原子化管理员归属。

### P1：微信 `unionid` 未建立唯一性约束

迁移只对 `(provider, app_type, openid)` 建立唯一约束，而 `(provider, unionid)` 只是普通索引；查询还使用 `LIMIT 1`。不同应用类型或并发请求可能把同一 `unionid` 绑定到不同用户。

涉及位置：

- [`V6__identity_account.sql`](../../timeimprint-task-bootstrap/src/main/resources/db/migration/V6__identity_account.sql)

影响：同一微信主体可能分裂为多个本地账号，造成身份混淆。应按正式身份合并规则建立数据库级唯一性或显式的全局身份映射。

### P1：核心认证行为缺少有效测试覆盖

已选身份模块测试虽然通过，但覆盖报告显示 `IdentityStore` 和 `UserController` 没有行覆盖，`IdentityService` 仅有极少量覆盖；现有测试主要验证结构、反射结果或简单访问器，没有覆盖注册、登录、登出、验证码消费、并发管理员、跨用户访问等核心行为。

影响：U01—U10 相关验收场景无法由当前测试证明。应优先增加服务级和 HTTP 集成测试，并为所有越权路径增加负向用例。

### P1：阶段状态文档存在互相冲突

`docs/00-READING-ORDER.md` 的开篇阶段入口和阶段索引已表述为 `IMPLEMENTING`，但该文件内的多处表格/摘要以及 `docs/07-ACCEPTANCE.md`、`docs/08-AI-IMPLEMENTATION-TASKS.md` 仍保留 `READY` 表述。多个正式入口对当前阶段状态的描述不一致。

影响：后续智能体和维护者无法可靠判断实施许可。应先统一正式阶段状态来源，再继续更新交付证据。

## 验证记录

| 验证项 | 结果 | 说明 |
| --- | --- | --- |
| 身份模块定向测试 | 通过 | `./mvnw -pl timeimprint-task-identity -am test -Dtest='Identity*,LoginResultTest' -Dsurefire.failIfNoSpecifiedTests=false`，共 7 项 |
| Web 模块定向测试 | 通过 | `./mvnw -pl timeimprint-task-web -am test -Dtest='UserControllerTest,BearerSessionFilterTest' -Dsurefire.failIfNoSpecifiedTests=false`，共 2 项 |
| 聚合测试 | 阻塞 | 适配器模块的既有飞书 Mock Server 测试因当前沙箱禁止本地端口监听而失败，未观察到可归因于本次身份改动的确定性失败 |
| 代码覆盖检查 | 不通过 | 核心身份存储、控制器和主要服务逻辑缺少实质覆盖 |
| `git diff --check` | 通过 | 审查报告落盘后检查无空白错误 |

## 建议处理顺序

1. 先封堵跨用户读写和固定凭证两个 P0 问题。
2. 将所有认证依赖改为生产环境 fail closed，并实现可验证的真实短信发送。
3. 补齐验证码原子消费、管理员原子初始化和微信身份唯一性。
4. 用 U01—U10 对应的服务与 HTTP 集成测试证明行为，并补充跨用户负向用例。
5. 统一 P06 正式阶段状态文档，完成真实验证后再在 `DELIVERY.md` 记录证据。
