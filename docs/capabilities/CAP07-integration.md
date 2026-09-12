# CAP07 · integration · 外部系统集成能力

> 本文保存`capabilityKey=integration`的已规划范围。目前没有进入实施阶段，不是可编码契约。

| 项目 | 值 |
| --- | --- |
| 能力编号 | CAP07 |
| 目标模块 | `timeimprint-task-service-capability-integration` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 主要场景 | S05、S06、S11、S12、S15、S16 |
| DELIVERY证据 | 无 |

## 已规划能力项

| 能力项 | 内容 | 关联 | 状态 |
| --- | --- | --- | --- |
| INT-01 | Webhook和第三方回调 | S11、X01 | BACKLOG / OUTLINE / NOT_STARTED |
| INT-02 | 外部事件与业务对象映射 | S05、S11、S12 | BACKLOG / OUTLINE / NOT_STARTED |
| INT-03 | 第三方日历增量同步 | S06、S15、S16 | BACKLOG / OUTLINE / NOT_STARTED |
| INT-04 | 外部身份映射 | S06、S07、S15 | BACKLOG / OUTLINE / NOT_STARTED |

已明确：外部调用不可放在业务状态事务内；Webhook等副作用必须使用EXTERNAL Action模式并处理UNKNOWN；外部事件必须去重；日历同步必须保留外部ID并面对变更、删除和冲突。

待细化：具体系统与协议、认证和凭据、速率限制、超时、重试、回调验签、来源优先级、双向/单向同步、循环抑制、数据映射和错误补偿。没有真实协议不得虚构客户端或配置。

本能力负责外部边界适配，不拥有场景业务状态。外部输入经Signal进入，外部输出经Action执行；是否引入MQ属于X01技术决策，不因存在integration域而默认采用。
