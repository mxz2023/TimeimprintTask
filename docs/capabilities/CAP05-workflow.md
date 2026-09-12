# CAP05 · workflow · 流程编排能力

> 本文保存`capabilityKey=workflow`的已规划范围。目前没有进入实施阶段，不是可编码契约，也不代表已经选择工作流引擎。

| 项目 | 值 |
| --- | --- |
| 能力编号 | CAP05 |
| 目标模块 | `timeimprint-task-service-capability-workflow` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 主要场景 | S08、S09 |
| DELIVERY证据 | 无 |

## 已规划能力项

| 能力项 | 内容 | 关联 | 状态 |
| --- | --- | --- | --- |
| WFL-01 | 串行步骤和依赖推进 | C06、S09 | BACKLOG / OUTLINE / NOT_STARTED |
| WFL-02 | 并行、会签、分支和汇合 | C06、S09 | BACKLOG / OUTLINE / NOT_STARTED |
| WFL-03 | 退回、升级、取消和有限补偿 | S08、S09、X02 | BACKLOG / OUTLINE / NOT_STARTED |

已明确：A完成触发B、B/C完成触发D等组合必须定义幂等、汇合和取消；审批、会签、退回和超时升级是场景业务状态，不应全部硬编码进kernel；业务流程能力与是否采用工作流引擎是两个决策。

待细化：流程定义版本、实例迁移、会签规则、分支表达、超时计时、撤回/退回、补偿边界、历史流程升级及自研有限状态机与引擎的选择标准。

当前不创建通用脚本、万能JSON流程DSL、空引擎适配器或补偿框架。只有多个已选择场景出现相同且稳定的编排语义时才提炼能力契约；场景专有审批字段保留在场景数据中。
