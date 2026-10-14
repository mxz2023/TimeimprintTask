# 阶段索引

本文件只回答三个问题：以前完成了什么、当前正在做什么、未来可能做什么。详细规则由链接文件负责，不能在此创建另一套业务定义。

## 当前阶段

当前阶段是 [P10 保养与复查](P10/README.md)，总体状态 VERIFYING。

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

## 未来阶段

[S14](../scenarios/S14-maintenance-follow-up.md) 已选入 P10。C12 中微信/钉钉/Telegram 等非飞书 IM 在 P05 飞书交付后再排。S03—S05 已随 P09 发布。

## 更新规则

- 阶段变为RELEASED时，把它移入“已发布阶段”，补充DELIVERY和Git标签链接。
- 创建下一阶段时，只增加一行CURRENT记录，不复制上一阶段任务或状态。新阶段 README 必须按 [08](../08-AI-IMPLEMENTATION-TASKS.md) 第 4 节写明本期必要人工步骤；未写不得转为 READY，用户未实际做完不得转为 RELEASED。
- 阶段详细状态以其README为准，任务状态以其IMPLEMENTATION为准，交付事实以其DELIVERY为准。
- 若本索引与阶段README不一致，立即停止实施并修正，不允许智能体自行选择。
