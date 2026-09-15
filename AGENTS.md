# TimeImprintTask仓库协作规则

本文件适用于整个仓库，任何智能体开始工作前都必须遵守。

## 行动前必读

1. 读取`docs/00-READING-ORDER.md`。
2. 读取`docs/phases/README.md`并确认唯一CURRENT阶段。
3. 读取该阶段的`README.md`和`IMPLEMENTATION.md`。
4. 读取`docs/08-AI-IMPLEMENTATION-TASKS.md`中的跨阶段治理规则。
5. 读取`docs/scenarios/README.md`和`docs/capabilities/README.md`，核对目标条目的三维状态。
6. 修改或实现某个场景前，读取其永久文件和全部依赖能力文件，再读取当前阶段指定的核心契约。

修改文件前必须说明当前阶段、阶段状态、当前任务、允许范围、禁止范围、适用的`INV-*`及验证方式。找不到CURRENT阶段、存在多个CURRENT阶段、状态不允许当前操作或文档互相冲突时，必须停止实现并先修正文档或请求决策，不能自行猜测。

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

执行与受影响契约相匹配的检查。文档修改至少验证Markdown链接、稳定编号、命名、CURRENT阶段唯一性和`git diff --check`。实现任务必须执行当前阶段规定的真实测试和证据命令；跳过或无法执行的验证保持NOT_RUN或BLOCKED。
