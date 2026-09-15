# 阶段索引

本文件只回答三个问题：以前完成了什么、当前正在做什么、未来可能做什么。详细规则由链接文件负责，不能在此创建另一套业务定义。

## 当前阶段

当前没有 CURRENT 阶段。下一阶段须从[场景索引](../scenarios/README.md)、[能力索引](../capabilities/README.md)和[09演进路线](../09-SCENARIO-ROADMAP.md)的 NEXT_REVIEW 选择范围，经规则细化与用户确认后创建。

## 已发布阶段

| 阶段 | 状态 | 交付与标签 |
| --- | --- | --- |
| P01 第一期稳定核心与基础场景 | RELEASED | [DELIVERY](P01/DELIVERY.md)；Git 标签 [`p01`](P01/README.md)；人工验收 2026-09-15 |

## 未来阶段

P02、P03尚未定义，也不得提前创建空任务。未来工作从[场景索引](../scenarios/README.md)、[能力索引](../capabilities/README.md)和[09演进路线](../09-SCENARIO-ROADMAP.md)选择范围，经规则细化、核心影响分析和用户确认后创建下一阶段目录。

## 更新规则

- 阶段变为RELEASED时，把它移入“已发布阶段”，补充DELIVERY和Git标签链接。
- 创建下一阶段时，只增加一行CURRENT记录，不复制上一阶段任务或状态。
- 阶段详细状态以其README为准，任务状态以其IMPLEMENTATION为准，交付事实以其DELIVERY为准。
- 若本索引与阶段README不一致，立即停止实施并修正，不允许智能体自行选择。
