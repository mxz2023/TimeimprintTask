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

## 已规划内容

支持给团队分配任务，表达负责人、协作者和关注人，并支持分派与转交。必须区分“任一人完成”和“所有人完成”，以及成员离开团队后的任务归属和访问权限。

## 编码前必须决定

- 团队与成员的可信来源、租户边界和角色权限矩阵。
- 单负责人、多负责人、任一完成、全部完成及子任务关系。
- 分派、接单、转交、撤回、成员离开和历史审计。
- 通知对象与办理权限的差异、暂停/退役行为、专有表和验收。
- scenarioKey、schemaVersion、状态与命令。

P01的Participant公共模型不表示团队协作能力已经实现；正式身份与Policy未准备前本场景不能进入编码。
