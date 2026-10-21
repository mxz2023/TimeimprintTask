# 阶段索引

本文件只回答三个问题：以前完成了什么、当前正在做什么、未来可能做什么。详细规则由链接文件负责，不能在此创建另一套业务定义。

## 当前阶段

无。下一期在新建阶段 README 并转为 READY 之前，不得修改代码、测试、配置、迁移和部署脚本。

## 已发布阶段

| 阶段 | 状态 | 交付与标签 |
| --- | --- | --- |
| P01 第一期稳定核心与基础场景 | RELEASED | [DELIVERY](P01/DELIVERY.md)；Git 标签 [`v20260915-P01`](P01/README.md)；人工验收 2026-09-15 |
| [P02工程结构与测试镜像](P02/README.md) | RELEASED | [DELIVERY](P02/DELIVERY.md)；Git 标签 `v20260917-P02`；人工验收 2026-09-17 |
| [P03 Jackson 3 原生迁移](P03/README.md) | RELEASED | [DELIVERY](P03/DELIVERY.md)；Git 标签 `v20260917-P03`；人工验收 2026-09-17 |
| [P04 标题与锁序](P04/README.md) | RELEASED | [DELIVERY](P04/DELIVERY.md)；Git 标签 `v20261003-P04`；人工验收 2026-10-03 |
| [P05 飞书整体接入](P05/README.md) | RELEASED | [DELIVERY](P05/DELIVERY.md)；Git 标签 `v20261007-P05`；人工验收 2026-10-07 |
| [P06 多用户账号](P06/README.md) | RELEASED | [DELIVERY](P06/DELIVERY.md)；Git 标签 `v20261010-P06`；人工验收 2026-10-10 |
| [P07 微信扫码与小时分钟间隔](P07/README.md) | RELEASED | [DELIVERY](P07/DELIVERY.md)；Git 标签 `v20261011-P07`；人工验收 2026-10-11 |
| [P08 站内信实时提示](P08/README.md) | RELEASED | [DELIVERY](P08/DELIVERY.md)；Git 标签 `v20261014-P08`；人工验收 2026-10-13 |
| [P09 生日、到期与缴费](P09/README.md) | RELEASED | [DELIVERY](P09/DELIVERY.md)；Git 标签 `v20261014-P09`；用户于 2026-10-14 确认可以发布 |
| [P10 保养与复查](P10/README.md) | RELEASED | [DELIVERY](P10/DELIVERY.md)；Git 标签 `v20261014-P10`；用户于 2026-10-14 确认可以发布 |
| [P11 去掉 Task 冗余账号表](P11/README.md) | RELEASED | [DELIVERY](P11/DELIVERY.md)；Git 标签 `v20261017-P11`；用户于 2026-10-17 确认两步人工结果 |
| [P12 验收库名与验收脚本](P12/README.md) | RELEASED | [DELIVERY](P12/DELIVERY.md)；Git 标签 `v20261018-P12`；用户于 2026-10-18 确认可以发布 |
| [P13 站内信软删除](P13/README.md) | RELEASED | [DELIVERY](P13/DELIVERY.md)；Git 标签 `v20261018-P13`；用户于 2026-10-18 确认可以发布 |
| [P14 提醒时间段](P14/README.md) | RELEASED | [DELIVERY](P14/DELIVERY.md)；Git 标签 `v20261021-P14`；用户于 2026-10-21 确认人工验收通过 |

## 未来阶段

C12 中微信/钉钉/Telegram 等非飞书 IM 在 P05 飞书交付后再排。S06—S13、S15—S17 仍是长期保留。S14 已随 P10 发布。X06 已随 P11 发布。X07 已随 [P12](P12/README.md) 发布。站内信软删除已随 [P13](P13/README.md) 发布。提醒时间段已随 [P14](P14/README.md) 发布。标签 `v20261021-P14`。

## 更新规则

- 同一时刻只有一个 CURRENT。
- 阶段状态只允许 DRAFT、READY、IMPLEMENTING、BLOCKED、VERIFYING、RELEASED。扭转以 docs/08-AI-IMPLEMENTATION-TASKS.md 的「阶段规则」为准。
- 新阶段 README 必须写明目标、允许范围、禁止范围和必要人工步骤。未写不得转为 READY。没有外部步骤时写「本期无此类步骤」，转 READY 前由用户确认。
- DRAFT 和 READY 不得改代码。IMPLEMENTING 须用户另行授权。
- 自动证据通过但人工步骤未做完时保持 VERIFYING。用户未实际做完不得标 RELEASED。
- 发布后把该阶段移入「已发布阶段」，停止改写 README、IMPLEMENTATION、DELIVERY。
- 阶段详细状态以该阶段 README 为准，任务状态以 IMPLEMENTATION 为准，证据以 DELIVERY 为准。索引与阶段 README 不一致时停止实施并修正。
