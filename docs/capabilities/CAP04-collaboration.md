# CAP04 · collaboration · 协作与主体能力

> 本文保存`capabilityKey=collaboration`的范围。账号子集已可编码，其余协作能力仍不是可编码契约。账号子集的实现模块是`timeimprint-task-identity`，不提前创建 collaboration 模块。

| 项目 | 值 |
| --- | --- |
| 能力编号 | CAP04 |
| 目标模块 | `timeimprint-task-service-capability-collaboration` |
| planningPosition | P06（仅账号子集）/ BACKLOG（其余） |
| contractStatus | RELEASED（仅账号子集）/ OUTLINE（其余） |
| implementationStatus | VERIFIED（仅账号子集）/ NOT_STARTED（其余） |
| 主要场景 | S06、S07、S08、S09、S15 |
| DELIVERY证据 | [P06 DELIVERY](../phases/P06/DELIVERY.md)（账号子集；标签 `v20261010-P06`） |

## 已规划能力项

| 能力项 | 内容 | 关联 | 状态 |
| --- | --- | --- | --- |
| COL-01 | 可信主体解析、团队隔离和权限 | C13 | 账号子集：P06 / RELEASED / VERIFIED（标签 `v20261010-P06`）；团队隔离与权限矩阵仍为 BACKLOG / OUTLINE / NOT_STARTED |
| COL-02 | 负责人、办理人、协作者、关注人和参会人 | S06—S09、S15 | BACKLOG / OUTLINE / NOT_STARTED |
| COL-03 | 分派、转交、轮值、换班和人员离开 | S07、S08 | BACKLOG / OUTLINE / NOT_STARTED |
| COL-04 | 通知委托和多接收人授权 | C16 | BACKLOG / OUTLINE / NOT_STARTED |

已明确：所有者、办理人和通知接收人是不同概念；收到通知不自动获得完成权限；调用者必须来自会话构造的 ActorContext，不能信任请求体自报 userId。账号子集已在 [P06](../phases/P06/README.md) 交付并 VERIFIED。团队隔离和权限矩阵还没有进入实施。

[P06](../phases/P06/README.md) 的账号子集已经写入 [01](../01-MVP-SPEC.md)—[07](../07-ACCEPTANCE.md)：短信验证后的手机号注册、手机号密码登录、一次性授权、微信登录与绑定，以及会话令牌。飞书等扫码登录本期不实现。规则见 [ADR-0001](../decisions/ADR-0001-multi-user-identity.md)。证据见 [DELIVERY](../phases/P06/DELIVERY.md)。团队/组织来源、角色权限矩阵、任一人或全部完成、转交历史、成员离开、代理期限、轮值算法和跨租户边界仍待后续阶段决定。

本能力可以提供主体解析和Policy实现，但不能隐式改变场景状态。参与人变化产生的业务迁移仍由场景通过TransitionPlan声明；通知投影交给notification执行。
