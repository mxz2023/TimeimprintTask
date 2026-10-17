# P11 · 去掉 Task 冗余账号表

本阶段删掉 Task 里与用户服务重复的账号表。线上尚未部署，没有需要保留的旧账号，不做数据复制。公开任务的路径、字段和状态语义保持不变。

用户于 2026-10-17 确认两步人工结果。阶段已 RELEASED。任务见 [IMPLEMENTATION](IMPLEMENTATION.md)，证据见 [DELIVERY](DELIVERY.md)。本 README、IMPLEMENTATION、DELIVERY 此后不再改写。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P11 |
| 排期身份 | RELEASED |
| 总体状态 | RELEASED |
| 文档基线 | 2.3 |
| 基础发布 | P10；Git 标签 `v20261014-P10` |
| baselineGitRef | `9e8313a8744ae8c07a1f132d0fa0437044dcb20e` |
| 工程状态 | RELEASED |
| Git发布标签 | `v20261017-P11`（打在紧接本文档冻结之后的代码提交上） |
| 人工验收 | 2026-10-17 用户确认两步都通过：登录后能进入自己的任务；退出后原令牌立即失效 |
| 下一动作 | 无。本阶段冻结；下一阶段须用户另选范围 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | [DELIVERY](DELIVERY.md)：全量回归与两步人工确认均为通过 |

P01—P10 已冻结，本阶段不得改写它们。

## 2. 要解决的问题

网页登录已经走 TimeImprintUser 的 `/api/v1/users/**`，新账号写在 `tu_*`。任务接口也已经向用户服务核对令牌：`BearerSessionFilter`（`timeimprint-task-web` 模块）把 `Authorization: Bearer` 交给 `UserSessionLookup`（同模块），请求 `POST /internal/v1/sessions/introspect`。用户服务返回 401 时任务接口返回 401。用户服务未配置、不可达或拒绝服务令牌时返回 503，不伪装成未登录。请求路径不查询 `tt_identity_session`。

账号数据已经只由用户服务的 `tu_*` 保存。Task 里残留的同一套表没有请求读取，属于冗余：

| Task 表 | 用户服务中的对应表 |
| --- | --- |
| `tt_identity_user` | `tu_user` |
| `tt_identity_verification_code` | `tu_verification_code` |
| `tt_identity_session` | `tu_session` |
| `tt_identity_social_account` | `tu_social_account` |
| `tt_identity_oauth_state` | `tu_oauth_state` |

P11 删除这五张表，以及只为写入它们而存在的账号存储和启动预置。任务上的 `actor_key`（操作者键）仍是字符串，不依赖这些表。已签发令牌的有效和失效仍由用户服务决定。

## 3. 已记录的目标

| 口径 | 结论 |
| --- | --- |
| 技术候选 | X06。不新增场景，不改变 S01—S17 与已发布能力的三维状态 |
| 数据 | 不复制账号。删除上表五张 `tt_identity_*`。不改任务业务表 |
| 令牌 | 继续使用不透明令牌及其摘要。不改成 JWT，以免改变退出和停用的失效语义 |
| 任务鉴权 | 已经向用户服务 `POST /internal/v1/sessions/introspect` 核对。确认失败是 401。用户服务未配置、不可达或拒绝服务令牌时是 503，不得伪装成未登录。本期不改这条路径 |
| 公开接口 | `/api/v1/users/**` 仍由用户服务提供。任务公开接口的路径和字段不改 |
| 预置测试账号 | 删掉只写 `tt_identity_*` 的启动预置。自动化仍使用既有测试令牌，由测试里的用户服务核对提供身份 |
| 飞书 | `actor_key` 形状保持 `wx-`、`u-` 和预置 `local-actor`。接收人映射不改 |

## 4. 允许与禁止

允许：

- 新增 Flyway 版本，删除五张 `tt_identity_*`。不改已经执行过的迁移文件。
- 删除只写这些表的账号存储和启动预置。测试令牌常量留在测试代码。
- 保留 `timeimprint-task-identity` 模块，用来放置删除迁移和 `actor_key`（操作者键）形状。
- 修正 [02](../../02-AI-CODING-GUIDE.md)、[03](../../03-INTEGRATION-CONTRACTS.md)、[05](../../05-DATABASE.md) 里已经失真的账号表描述。
- 调整 `Deploy/scripts/07-run-task-acceptance.sh` 读取 `actor_key` 形状的源文件。不改 `actor_key` 形状，因此不改入口、Compose、环境变量示例和 Wiki。

禁止：

- 改写已发布的 P01—P10。
- 改变 kernel 业务语义、平台公共表或任务公开 API。
- 把用户服务的 `tu_*` 表查询写进 Task。
- 把 `tt_identity_*` 的行复制到 `tu_*`。线上没有需要保留的旧账号。
- 在生产配置里打开测试令牌。

## 5. 必要人工步骤

两步都已于 2026-10-17 由用户确认通过。

1. 用用户服务登录一个账号，打开任务页，确认能进入自己的任务。
2. 退出登录后，用刚才的令牌再访问任务接口，确认立即失效。

本期不改接收人和 `actor_key`，不要求在飞书里实收一条消息。

## 6. 不变量

本阶段不改变 [INV-01](../../02-AI-CODING-GUIDE.md)—[INV-09](../../02-AI-CODING-GUIDE.md)。场景与能力的三维状态保持 P10 发布时的结论。
