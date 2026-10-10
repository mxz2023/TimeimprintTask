# P06 · 多用户账号

本阶段把公开身份收成唯一的账号登录。本地开发账号和测试账号走同一套注册登录，不再使用固定身份或调试头。注册须先过手机短信验证；登录包含手机号密码和微信，飞书等扫码只留扩展。行为参照 TimeImprintServer 的 Django 账号实现。决策见已接受的 [ADR-0001](../../decisions/ADR-0001-multi-user-identity.md)，执行规则在 [01](../../01-MVP-SPEC.md)—[07](../../07-ACCEPTANCE.md)。

对应 [C13](../../capabilities/README.md)（身份与权限）里的账号子集，不实现团队协作。任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P06 |
| 排期身份 | RELEASED |
| 总体状态 | RELEASED |
| 文档基线 | 2.3 |
| 基础发布 | P05；Git 标签 `v20261007-P05` |
| baselineGitRef | `844d2ffeddfb3583230637fab57dd83f565c97ec`（授权实施前的 HEAD；账号契约当时尚未提交） |
| 工程状态 | RELEASED |
| Git发布标签 | `v20261010-P06`（打在本 RELEASED 文档冻结提交上） |
| 人工验收 | 2026-10-10 用户确认通过 |
| 下一动作 | 无。本阶段冻结；下一阶段须用户另选范围 |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | [DELIVERY](DELIVERY.md)；U01—U10 为 PASS |

用户于 2026-10-10 接受 [ADR-0001](../../decisions/ADR-0001-multi-user-identity.md)，并授权实施。同日用户确认人工验收通过。阶段已 RELEASED（标签 `v20261010-P06`）。本 README、IMPLEMENTATION、DELIVERY 此后不再改写。

## 2. 已接受口径

| 口径 | 结论 |
| --- | --- |
| 身份 | 只有账号。删除固定 actor 和 `X-Debug-Actor-Id` |
| 本地与测试 | 同一登录方式。本地开发账号与测试账号分开，测试先登录再调接口 |
| 注册 | 手机短信验证码通过后，才能用手机号和密码注册 |
| 登录 | 手机号密码；微信 code 登录、绑定、解绑、绑定手机号并合并账号 |
| 授权门禁 | 对齐 Django：未授权账号先返回 challenge，校验服务端授权字符串后才发令牌 |
| 以后的扫码 | 飞书等只留 provider 扩展，本期不实现 |
| 短信通道 | 腾讯云短信；密钥与模板走环境变量。测试用替身，不访问外网 |
| 令牌 | `Authorization: Bearer`；库中只存摘要 |
| 管理 | 空库第一个完成授权的账号为管理员 |
| 模块 | 新建 `timeimprint-task-identity`；短信与微信客户端进已有 adapter |
| 内核 | 不改 kernel 语义和平台公共表。监听地址仍为回环 |

## 3. 允许与禁止

允许（获得实施授权后）：

- 按 [IMPLEMENTATION](IMPLEMENTATION.md) 的 T02 起编写 identity 模块、专有迁移、短信与微信客户端，并把既有测试改为登录测试账号。
- 验收范围是 [U01](../../07-ACCEPTANCE.md)—[U10](../../07-ACCEPTANCE.md)。

禁止：

- 在用户明确授权实施之前编码，或预建 `DELIVERY.md`。
- 改写已 RELEASED 的 P01—P05。
- 修改 kernel 业务语义或平台公共表。
- 实现飞书扫码登录，或把短信密钥写入仓库。
