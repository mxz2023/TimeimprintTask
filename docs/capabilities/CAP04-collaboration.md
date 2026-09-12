# CAP04 · collaboration · 协作与主体能力

> 本文保存`capabilityKey=collaboration`的已规划范围。目前没有进入实施阶段，不是可编码契约。

| 项目 | 值 |
| --- | --- |
| 能力编号 | CAP04 |
| 目标模块 | `timeimprint-task-service-capability-collaboration` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 主要场景 | S06、S07、S08、S09、S15 |
| DELIVERY证据 | 无 |

## 已规划能力项

| 能力项 | 内容 | 关联 | 状态 |
| --- | --- | --- | --- |
| COL-01 | 可信主体解析、团队隔离和权限 | C13 | BACKLOG / OUTLINE / NOT_STARTED |
| COL-02 | 负责人、办理人、协作者、关注人和参会人 | S06—S09、S15 | BACKLOG / OUTLINE / NOT_STARTED |
| COL-03 | 分派、转交、轮值、换班和人员离开 | S07、S08 | BACKLOG / OUTLINE / NOT_STARTED |
| COL-04 | 通知委托和多接收人授权 | C16 | BACKLOG / OUTLINE / NOT_STARTED |

已明确：所有者、办理人和通知接收人是不同概念；收到通知不自动获得完成权限；团队协作必须经过ActorContext和Policy；未来真实身份接入不能信任请求体自报userId。P01公共Participant模型和local固定身份只是核心基础，不代表本能力已经实现。

待细化：团队/组织来源、角色权限矩阵、任一人或全部完成、转交历史、成员离开、代理期限、轮值算法、跨租户边界和用户映射失败结果。选择S06—S09或S15进入阶段时必须逐项决定。

本能力可以提供主体解析和Policy实现，但不能隐式改变场景状态。参与人变化产生的业务迁移仍由场景通过TransitionPlan声明；通知投影交给notification执行。
