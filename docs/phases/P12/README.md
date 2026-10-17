# P12 · 验收库名与验收脚本

本阶段把真库验收用的数据库名改成 `db_timeimprint_task` 与 `db_timeimprint_task_perf`，并把重建空库和全量验证的脚本放进本仓库。不改任务业务、公开接口、公共表或 `actor_key`（操作者键）。

用户于 2026-10-17 授权实施。自动证据已通过，阶段现为 VERIFYING（验证中）。证据见 [DELIVERY](DELIVERY.md)。

## 1. 阶段身份

| 项目 | 当前值 |
| --- | --- |
| 阶段 | P12 |
| 排期身份 | CURRENT |
| 总体状态 | VERIFYING |
| 文档基线 | 2.3 |
| 基础发布 | P11；Git 标签 `v20261017-P11` |
| baselineGitRef | `8240ee977d08c57a9e544d9398e693912173e0e9` |
| 工程状态 | 自动证据已通过 |
| Git发布标签 | 无 |
| 人工验收 | 2026-10-17 用户确认本期无此类步骤 |
| 下一动作 | 用户确认可以发布后转 RELEASED |
| 实施任务 | [IMPLEMENTATION](IMPLEMENTATION.md) |
| 交付证据 | [DELIVERY](DELIVERY.md)：K01、K02 均为 PASS |

P01—P11 已冻结，本阶段不得改写它们。

## 2. 要解决的问题

验收和本地默认连接仍写着 `timeimprint_task_local`、`timeimprint_task_perf`。共用 MySQL 上的业务库命名是 `db_timeimprint_<服务>`，任务库应为 `db_timeimprint_task`，性能门槛库应为 `db_timeimprint_task_perf`。

全量真库验证的命令还写在 MyStudio 的 `Deploy/scripts/07-run-task-acceptance.sh`，重建空库写在 `Deploy/scripts/06-recreate-task-acceptance-mysql.sh`。这两件事只服务本仓库，应改为本仓库的 `scripts/run-task-acceptance.sh` 与 `scripts/recreate-acceptance-mysql.sh`。

[03](../../03-INTEGRATION-CONTRACTS.md)（配置契约）里监听地址和端口的说明与当前部署不一致。源码 local profile 写 `127.0.0.1`，部署入口追加 `--server.address=0.0.0.0`，默认端口是 `18080`。本期只改正文档，不改监听行为。

## 3. 已记录的目标

| 口径 | 结论 |
| --- | --- |
| 技术候选 | X07。不新增场景，不改变 S01—S17 与已发布能力的三维状态 |
| 库名 | 功能验收库 `db_timeimprint_task`。性能门槛库 `db_timeimprint_task_perf`。不再使用 `timeimprint_task_local`、`timeimprint_task_perf` |
| 脚本 | 在本仓库执行 `scripts/recreate-acceptance-mysql.sh` 重建 `tit-mysql-t01`，再用 `scripts/run-task-acceptance.sh` 先跑 `mysql-it`，通过后再跑 `dual-process-it`。单次仍不得超过 20 分钟 |
| 监听说明 | [03](../../03-INTEGRATION-CONTRACTS.md) 写成源码 `127.0.0.1`、部署追加 `0.0.0.0`、默认端口 `18080` |
| 业务 | 不改任务数据、公开接口、令牌核对和 `actor_key` 形状 |

## 4. 允许与禁止

允许（仅在本阶段转为 IMPLEMENTING（实施中）之后）：

- 默认数据源、README 连接串、`scripts/reset-acceptance-dbs.sh` 和集成测试里的 JDBC 库名。
- 新增 `scripts/recreate-acceptance-mysql.sh`、`scripts/run-task-acceptance.sh`。
- 把验收命令从 MyStudio 脚本改写到本仓库脚本的说明：`AGENTS.md`、`README.md`、`.cursor/rules/run-acceptance-mysql-after-code.mdc`、[03](../../03-INTEGRATION-CONTRACTS.md)、[07](../../07-ACCEPTANCE.md)（验收）。
- 在 [07](../../07-ACCEPTANCE.md) 写入 K01、K02。

禁止：

- 改写已发布的 P01—P11。
- 改变 kernel 业务语义、平台公共表、任务公开 API 或 [INV-01](../../02-AI-CODING-GUIDE.md)—[INV-09](../../02-AI-CODING-GUIDE.md)。
- 把 `icon.png` 纳入本期。
- 在本阶段里改 MyStudio 的 Compose、入口、环境变量示例或 Wiki。那些文件若仍指向旧脚本，另作部署对照，不混进本期任务。
- DRAFT 或 READY 期间继续改代码、测试、配置或脚本。

## 5. 必要人工步骤

本期无此类步骤。库名和验收脚本没有用户可感知的外部系统或界面操作。全量真库回归是自动证据。

用户于 2026-10-17 确认这句话。阶段因此转为 READY。实施仍须另行授权。

## 6. 不变量

本阶段不改变 [INV-01](../../02-AI-CODING-GUIDE.md)—[INV-09](../../02-AI-CODING-GUIDE.md)。场景与能力的三维状态保持 P11 发布时的结论。

## 7. 验收编号

| 编号 | 要求 |
| --- | --- |
| K01 | 功能验收库名为 `db_timeimprint_task`，性能门槛库名为 `db_timeimprint_task_perf`。默认 JDBC 与集成测试地址使用这两个名字 |
| K02 | `scripts/run-task-acceptance.sh` 先重建空库，再按顺序跑 `mysql-it` 与 `dual-process-it`，退出码均为 0，且没有超过 20 分钟 |
