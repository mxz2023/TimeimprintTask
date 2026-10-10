# P06 · 交付证据

> 本文只记录已经执行的命令和结果。
> 阶段身份见 [README](README.md)，任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-10-10T15:27:51Z
阶段状态: RELEASED（2026-10-10 用户确认人工验收通过；Git 标签 `v20261010-P06`；本文此后不再改写）。[U01](../../07-ACCEPTANCE.md)—[U10](../../07-ACCEPTANCE.md) 为 PASS。

---

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `844d2ffeddfb3583230637fab57dd83f565c97ec`（授权实施前的 HEAD，见 [README](README.md)） |
| 证据所测代码 | 当前工作区（含账号验收用例与参与人可见性）。先前全量回归对应提交 `8c1bd3e` |
| JDK | Amazon Corretto 21.0.12（`openjdk version "21.0.12" 2026-07-21 LTS`） |
| 操作系统 | macOS 27.0.1 |
| Maven Wrapper | 3.9.9 |
| MySQL 镜像 | `mysql:9.7.2`（容器 `tit-mysql-t01`，只监听 `127.0.0.1:13306`） |
| MySQL 镜像 Digest | `sha256:29abb0a179982e4a8928138bfc7f918af9eda64e7eeb1b1d084c1720a20159e6` |
| 验收库 | 每次命令前由 `Deploy/scripts/06-recreate-task-acceptance-mysql.sh` 重建空库 `timeimprint_task_local` 与 `timeimprint_task_perf` |
| 防卡死上限 | 单次 20 分钟（1200 秒）；超时退出码 124 |
| 基础发布 | P05；Git 标签 `v20261007-P05` |

## 2. 验证命令

| 命令 | 退出码 | 结果摘要 |
| --- | --- | --- |
| `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。总时间 59.6 秒。Failsafe **109** run / 0 fail / 0 error / 0 skipped（含 `AccountAcceptanceMysqlIT` 10 项）。结束于 2026-10-10T15:16:00Z。覆盖含腾讯云短信客户端的当前工作区；测试短信模式为 `capture` |
| `bash Deploy/scripts/07-run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 23 秒。Failsafe **7** run / 0 fail / 0 error。`PerfGateDualProcessIT`（`timeimprint-task-boot-loader` 模块）630.2 秒，预热与三次正式均为 inbox=1000、backlog=0、passed=true。结束于 2026-10-10T15:27:51Z。Failsafe 另有一条 native stream 警告，未改变退出码 |
| 单独的 `./mvnw -q test` | NOT_RUN | 单元测试只随上面两条 `verify` 一起执行，没有单独留下 Surefire 合计 |
| `git diff --check` | 0 | 工作区无空白错误 |

两条命令都在 MyStudio 仓库执行，入口脚本会先重建 `tit-mysql-t01`。执行前从环境中去掉了 `FEISHU_` 与 `TIMEIMPRINT_NOTIFICATION_` 前缀的变量，避免本机凭据改变结果。两次顺序执行，没有并发。短信模式为 `capture`，没有调用腾讯云。

## 3. U01—U10

| 编号 | 验收项 | 结论 | 证据 |
| --- | --- | --- | --- |
| [U01](../../07-ACCEPTANCE.md) | 短信与注册 | PASS | `AccountSmsChannelTest.failedSmsDoesNotInsertCode`：通道返回失败时不调用 `insertCode`。`AccountAcceptanceMysqlIT.u01RejectsBadExpiredAndDuplicateRegistration`：错误码与超过 10 分钟的码拒绝注册且不产生用户；同一手机号第二次注册为 `STATE_CONFLICT` |
| [U02](../../07-ACCEPTANCE.md) | 密码登录与授权 | PASS | `AccountAcceptanceMysqlIT.u02ChallengeThenTokenCanCallScenarioList`：注册只返回 challenge；错误授权字符串不写会话；正确字符串发令牌后 `GET /api/v1/task-scenarios`（[E01](../../04-API.md)）为 OK |
| [U03](../../07-ACCEPTANCE.md) | 令牌 | PASS | `AccountAcceptanceMysqlIT.u03StoresHashAndRejectsMissingBadAndDisabledTokens`：`tt_identity_session.token_hash` 为 SHA-256 且不含明文；退出、缺令牌、坏令牌、停用账号均为 `UNAUTHENTICATED` |
| [U04](../../07-ACCEPTANCE.md) | 调试头失效 | PASS | `AccountAcceptanceMysqlIT.u04DebugHeaderAndBodyUserIdDoNotChangeActor`：`X-Debug-Actor-Id: actor-b` 与请求体 `userId` 之后，`GET /api/v1/users/me` 仍是令牌账号 |
| [U05](../../07-ACCEPTANCE.md) | 微信登录 | PASS | `AccountAcceptanceMysqlIT.u05WeChatFindsSameOpenIdAndRejectsSecondBind`：同一 code 两次回调得到同一 `actorKey`；空 code 为 `INVALID_REQUEST`；该 openid 再绑到另一用户为 `STATE_CONFLICT` |
| [U06](../../07-ACCEPTANCE.md) | 绑定手机号合并 | PASS | `AccountAcceptanceMysqlIT.u06BindPhoneMergesAndFailedBindLeavesSocialUntouched`：绑定已有手机号后社交身份迁到目标用户、临时账号 `DISABLED`；目标账号已停用时拒绝，社交身份仍留在临时账号 |
| [U07](../../07-ACCEPTANCE.md) | 解绑 | PASS | `AccountAcceptanceMysqlIT.u07RejectsUnbindWithoutPhoneOrPassword`：无手机号且无密码时解绑为 `INVALID_REQUEST`，社交行仍在；补上手机号和密码后解绑成功 |
| [U08](../../07-ACCEPTANCE.md) | 管理员 | PASS | `AccountAcceptanceMysqlIT.u08FirstAccountIsAdminAndListHidesSecrets`：空库启动后第一个账号 `local-actor` 的 `is_admin=1`，其后完成授权的账号不是管理员；非管理员列用户为 `FORBIDDEN`；管理员列表正文不含 `password`、`passwordHash` 和预置令牌 |
| [U09](../../07-ACCEPTANCE.md) | 参与人 | PASS | `AccountAcceptanceMysqlIT.u09ActiveOwnerOnlyAndAccountsCannotSeeEachOther`：当前活跃用户可作为 OWNER；不存在或已停用的 id 创建定义为 `INVALID_REQUEST`；另一账号读取与列表都看不到该定义。读取与列表按参与人过滤 |
| [U10](../../07-ACCEPTANCE.md) | 模块边界 | PASS | `ArchitectureRulesTest.identityDoesNotDependOnKernel`、`smsAndWechatClientsStayInAdapter`、`noFeishuScanLoginSurface`，以及既有 `feishuSdkAndOutboundHttpStayInsideAdapter`（JDK HTTP 只在 adapter）。生产代码中无 `FeishuScan` / `FeishuOAuth` 类型 |

[T02](IMPLEMENTATION.md) 与 [T03](IMPLEMENTATION.md) 据此标 PASS。

## 4. T04

| 范围 | 结论 | 证据 |
| --- | --- | --- |
| 改密 [E21](../../04-API.md) | PASS | `AccountAcceptanceMysqlIT.t04ChangePasswordAndReset`：无令牌为 `UNAUTHENTICATED`；旧密码错误不改摘要；正确旧密码后旧密码登录失败、新密码登录得到令牌 |
| 忘记密码 [E23](../../04-API.md) | PASS | 同一方法：不存在的手机号不新增用户；超过 10 分钟的验证码不改摘要；重置后旧密码登录失败、新密码登录得到令牌 |
| 管理员列表与退出 | PASS | 第 3 节 [U08](../../07-ACCEPTANCE.md)、[U03](../../07-ACCEPTANCE.md) |
| 旧测试改为登录、全量回归、无飞书扫码 | PASS | 本节两条命令；[U10](../../07-ACCEPTANCE.md) |

[T04](IMPLEMENTATION.md) 据此标 PASS。

## 5. 人工验收

2026-10-10 用户确认人工验收通过。本机以 `IDENTITY_SMS_MODE=tencent` 调用 [E14](../../04-API.md)（发送短信验证码），返回 `OK` 与「验证码已发送」；手机收到的六位数字与 `tt_identity_verification_code` 中该次记录一致；随后 [E15](../../04-API.md)（校验短信验证码）返回「验证码有效」。自动化套件仍使用 `capture`，不访问腾讯云。

一并接受的范围：微信登录为捕获模式（code 当作 openid，不访问微信开放平台）；空库第一个管理员是预置账号 `local-actor`；参与人里的 OWNER 必须是当前调用账号；绑定手机号失败的证据是目标账号已停用时社交身份仍留在临时账号；飞书扫码没有端点。短信模板的第二个变量固定为 `1`。
