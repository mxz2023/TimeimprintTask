# P06 · 交付证据

> 本文只记录已经执行的命令和结果。没有单独用例证明的 [U01](../../07-ACCEPTANCE.md)—[U10](../../07-ACCEPTANCE.md) 记为 NOT_RUN，不因为全量回归通过就写成 PASS。
> 阶段身份见 [README](README.md)，任务见 [IMPLEMENTATION](IMPLEMENTATION.md)。

recordedAtUtc: 2026-10-10T09:54:18Z
阶段状态: IMPLEMENTING。全量回归已通过，账号专项尚未逐项证明，因此不转入 VERIFYING。

---

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `844d2ffeddfb3583230637fab57dd83f565c97ec`（授权实施前的 HEAD，见 [README](README.md)） |
| 证据所测代码 | 当时工作区；其后提交为 `8c1bd3e`（账号实现）。规则提交 `eb1b336` 不改变运行行为 |
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
| `bash Deploy/scripts/07-run-task-acceptance.sh mysql-it` | 0 | BUILD SUCCESS。总时间 55.120 秒。Failsafe **99** run / 0 fail / 0 error / 0 skipped。结束于 2026-10-10T09:09:11Z |
| `bash Deploy/scripts/07-run-task-acceptance.sh dual-process-it` | 0 | BUILD SUCCESS。总时间 11 分 25 秒。Failsafe **7** run / 0 fail / 0 error。`PerfGateDualProcessIT`（`timeimprint-task-boot-loader` 模块）628.9 秒，预热与三次正式均为 inbox=1000、backlog=0、passed=true。结束于 2026-10-10T09:54:18Z |
| 单独的 `./mvnw -q test` | NOT_RUN | 单元测试只随上面两条 `verify` 一起执行，没有单独留下 Surefire 合计 |
| `git diff --check` | NOT_RUN | 本次未执行 |

两条命令都在 MyStudio 仓库执行，入口脚本会先重建 `tit-mysql-t01`。执行前从环境中去掉了 `FEISHU_` 与 `TIMEIMPRINT_NOTIFICATION_` 前缀的变量，避免本机凭据改变结果。两次顺序执行，没有并发。短信模式为 `capture`，没有调用腾讯云。

## 3. U01—U10

全量回归说明既有任务用例在测试账号的 Bearer 令牌下仍能通过。下列专项没有对应断言，全部 NOT_RUN。现有 `IdentityServiceTest` 只检查令牌摘要长度，`UserControllerTest` 只检查方法存在，`BearerSessionFilterTest` 只检查路径是否要求令牌，都不能代替下表。

| 编号 | 验收项 | 结论 | 证据 |
| --- | --- | --- | --- |
| [U01](../../07-ACCEPTANCE.md) | 短信与注册 | NOT_RUN | 无通道失败、错误验证码、过期验证码、重复手机号的用例 |
| [U02](../../07-ACCEPTANCE.md) | 密码登录与授权 | NOT_RUN | 无 challenge、错误授权字符串、登录后调用 [E01](../../04-API.md) 的用例 |
| [U03](../../07-ACCEPTANCE.md) | 令牌 | NOT_RUN | 无库中只存摘要、退出失效、无令牌/坏令牌/停用账号返回 401 的用例 |
| [U04](../../07-ACCEPTANCE.md) | 调试头失效 | NOT_RUN | 无 `X-Debug-Actor-Id` 或请求体 userId 不能改写 ActorContext 的用例 |
| [U05](../../07-ACCEPTANCE.md) | 微信登录 | NOT_RUN | 无 code/state、按 openid 查找或创建、同一微信不能绑两人的用例 |
| [U06](../../07-ACCEPTANCE.md) | 绑定手机号合并 | NOT_RUN | 无同一事务迁入微信身份并停用临时账号的用例 |
| [U07](../../07-ACCEPTANCE.md) | 解绑 | NOT_RUN | 无缺少手机号和密码时拒绝解绑的用例 |
| [U08](../../07-ACCEPTANCE.md) | 管理员 | NOT_RUN | 无首个授权账号成为管理员、非管理员不能列用户的用例 |
| [U09](../../07-ACCEPTANCE.md) | 参与人 | NOT_RUN | 无停用或不存在的用户 id 被拒绝、两个测试账号互不可见的用例 |
| [U10](../../07-ACCEPTANCE.md) | 模块边界 | NOT_RUN | `ArchitectureRulesTest` 8 项随全量回归通过，但没有逐条证明短信/微信客户端只在 adapter、identity 不依赖 kernel、飞书扫码无端点 |

因此 [T02](IMPLEMENTATION.md)—[T04](IMPLEMENTATION.md) 不能标 PASS。阶段保持 IMPLEMENTING。
