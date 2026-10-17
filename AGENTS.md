# TimeImprintTask仓库协作规则

本文件适用于整个仓库，任何智能体开始工作前都必须遵守。MyStudio 工作站可以读取和修改本仓库，但必须按本文件和本仓库的阶段规则修改，不能只按工作站自己的部署方便处理。

## 行动前必读

1. 读取`docs/00-READING-ORDER.md`。
2. 读取`docs/phases/README.md`并确认唯一CURRENT阶段。
3. 读取该阶段的`README.md`和`IMPLEMENTATION.md`。
4. 读取`docs/08-AI-IMPLEMENTATION-TASKS.md`中的跨阶段治理规则。
5. 读取`docs/scenarios/README.md`和`docs/capabilities/README.md`，核对目标条目的三维状态。
6. 修改或实现某个场景前，读取其永久文件和全部依赖能力文件，再读取当前阶段指定的核心契约。

修改文件前必须说明当前阶段、阶段状态、当前任务、允许范围、禁止范围、适用的`INV-*`及验证方式。找不到CURRENT阶段、存在多个CURRENT阶段、状态不允许当前操作或文档互相冲突时，必须停止实现并先修正文档或请求决策，不能自行猜测。

没有已经开始处理的阶段时，不得修改代码、测试、配置或部署脚本。DRAFT 和 READY 只允许写该阶段的目标文档。先把目标写入阶段 README，用户授权并转为 IMPLEMENTING 之后才能动手。这条规则同样适用于 TimeImprintUser、TimeImprintWebsite 和以后新建的子项目。P12 已于 2026-10-18 RELEASED。没有 CURRENT 阶段时，不得修改代码、测试、配置或部署脚本。

改`actor_key`形状、`timeimprint.*`配置键或 Compose 已绑定的环境变量时，阶段允许范围必须先列入 MyStudio 的`Deploy/docker/timeimprint-task/entrypoint.sh`、`docker-compose.yml`、`docker-compose.local.yml`、`.env.example`、`.env.local.example`和`Wiki/Deploy/environment.md`，并在同一轮修改它们。`scripts/run-task-acceptance.sh`须静态对照当前`actor_key`前缀与入口映射，不能只靠预置账号`local-actor`的测试。接收人或对外投递变化时，必要人工步骤必须是真实登录账号在飞书收到消息；微信扫码登录成功不能代替。不要等用户提醒。

账号短信不在 Task 模块。本地和生产部署的 `USER_SMS_MODE` 必须是 `tencent`，由 TimeImprintUser 发送真实短信。`capture` 只允许自动化测试使用，不能写进 Compose 默认值、env 示例或部署脚本。

## 权威边界

- `docs/00-READING-ORDER.md`只负责导航和当前阶段入口，不替代详细契约。
- `docs/01-MVP-SPEC.md`至`docs/07-ACCEPTANCE.md`按00声明的职责分别拥有当前平台契约。
- `docs/scenarios/`保存全部已规划场景；READY/RELEASED文件拥有场景专有业务契约，OUTLINE文件只保存规划且禁止直接编码。
- `docs/capabilities/`保存八个能力域、能力项和C编号主要归属；能力域存在不表示其中全部规划能力已经实现。
- `docs/08-AI-IMPLEMENTATION-TASKS.md`负责阶段生命周期、文档维护和AI执行治理。
- `docs/phases/Pxx/README.md`负责该阶段范围与总体状态，`IMPLEMENTATION.md`负责任务状态，`DELIVERY.md`负责已经验证的交付证据。
- `docs/09-SCENARIO-ROADMAP.md`只维护排期和跨项关系，不拥有详细场景或能力规则；NEXT_REVIEW不等于实施许可。
- `docs/10-TECHNICAL-REVIEW.md`和已接受ADR用于解释设计理由，不能覆盖当前正式契约。

新的用户明确指令优先，但继续受影响实现前，必须同步修改相关正式契约、验收项、阶段文件和术语表。遇到文档冲突不得静默选择其中一种解释。

## 稳定仓库规则

- 项目名为`TimeImprintTask`，制品和模块前缀为`timeimprint-task`，数据库表前缀为`tt_`。
- 项目自定义 Java 类型不使用 `Mxz` 类名前缀；按模块与职责命名即可。
- 所有Markdown表格的表头必须使用中文；协议字段、状态枚举和代码标识放在表格内容或正文中，不得直接用作英文表头。
- `docs/capabilities/`中的能力域文件固定使用`CAP01—CAP08`编号与`CAPxx-capabilityKey.md`命名；README不编号，CAP编号不得复用或因排期调整而改变。
- 遵守`docs/02-AI-CODING-GUIDE.md`中的INV-01—INV-09。修改任一项属于核心模型变化，必须执行08规定的ADR和审批流程。
- 不得恢复`docs/WORKLOG.md`、使用旧项目文档或创建平行状态来源。
- 任何智能体、插件、脚本或开发工具产生的临时计划、缓存、会话记录、检查输出和其他中间产物，都不得长期保存在仓库中。需要长期保留的结论必须迁入对应的正式契约、场景、能力、阶段、决策或验收文档；迁移完成后删除临时产物，禁止形成工具专属目录或第二权威来源。
- 不得根据文档状态宣称实现完成；只有当前阶段`DELIVERY.md`能够记录已经验证的实现证据。
- 只有目标场景及其所需能力项均为READY_FOR_IMPLEMENTATION、进入唯一CURRENT阶段且获得用户授权时才能编码；OUTLINE、NEXT_REVIEW或BACKLOG均不足以授权实现。
- 只有阶段DELIVERY提供真实通过证据后才能把implementationStatus改为VERIFIED；代码存在但未验收仍为IN_PROGRESS。
- 没有用户明确授权且阶段状态不允许时，不得启动实现或产生外部副作用。

## 完成纪律

执行与受影响契约相匹配的检查。文档修改至少验证Markdown链接、稳定编号、命名、CURRENT阶段唯一性和`git diff --check`。实现任务必须执行当前阶段规定的真实测试和证据命令；跳过或无法执行的验证保持NOT_RUN或BLOCKED。新阶段 README 必须写明必要人工步骤；用户未实际做完并说明结果前，不得把该阶段标为 RELEASED。该要求见`docs/08-AI-IMPLEMENTATION-TASKS.md`第 4 节，创建阶段时不得省略。
