# S07 · 团队共享任务规划

> 本文是S07的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 未确定 |
| 目标模块 | `timeimprint-task-service-scenario-collaboration` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [collaboration](../capabilities/CAP04-collaboration.md)、[notification](../capabilities/CAP03-notification.md) |

## 具体事例

本节帮助理解规划用途，不是可编码契约。团队成员从哪里来、权限如何判断，仍以「编码前必须决定」为准。

- 「准备发布清单」派给小组：任意一名成员做完，整项完成。
- 「每人提交本周周报」：必须所有被指派的人都完成后，整项才完成。
- 一项任务有负责人、协作者和关注人；关注人收到进度，但不能代替负责人办理。
- 负责人把任务转交给另一名成员，转交记录留在历史里。
- 已分派的任务被撤回，重新回到待分配。
- 成员离开团队后，他名下未完成的任务改派给其他人，已完成的记录仍能查看。
- 子任务全部完成后，父任务才算完成。

只有自己办理、没有团队和转交的待办，继续使用 [S02](S02-recurring-todo.md)。

## 已规划内容

支持给团队分配任务，表达负责人、协作者和关注人，并支持分派与转交。必须区分“任一人完成”和“所有人完成”，以及成员离开团队后的任务归属和访问权限。

## 编码前必须决定

- 团队与成员的可信来源、租户边界和角色权限矩阵。
- 单负责人、多负责人、任一完成、全部完成及子任务关系。
- 分派、接单、转交、撤回、成员离开和历史审计。
- 通知对象与办理权限的差异、暂停/退役行为、专有表和验收。
- scenarioKey、schemaVersion、状态与命令。

P01的Participant公共模型不表示团队协作能力已经实现；正式身份与Policy未准备前本场景不能进入编码。
