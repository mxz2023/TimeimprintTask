# CAP08 · intelligence · 智能理解与建议能力

> 本文保存`capabilityKey=intelligence`的已规划范围。目前没有进入实施阶段，不是可编码契约。Spring AI版本基线不表示系统已接入模型。

| 项目 | 值 |
| --- | --- |
| 能力编号 | CAP08 |
| 目标模块 | `timeimprint-task-service-capability-intelligence` |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 主要入口 | S17智能创建 |
| DELIVERY证据 | 无 |

## 已规划能力项

| 能力项 | 内容 | 关联 | 状态 |
| --- | --- | --- | --- |
| AI-01 | 自然语言意图和目标场景识别 | S17 | BACKLOG / OUTLINE / NOT_STARTED |
| AI-02 | 日期、周期和参与人信息提取 | S17 | BACKLOG / OUTLINE / NOT_STARTED |
| AI-03 | 强类型配置生成、解释和修改建议 | S17 | BACKLOG / OUTLINE / NOT_STARTED |

已明确：模型只能生成目标场景可校验的强类型配置；结果必须经过目标场景与能力校验、时间预览和用户确认后，才进入普通创建接口。AI帮助开发文档或代码不等于运行系统具有AI能力。

待细化：目标场景范围、模型供应商、提示和输出schema、歧义交互、置信度、隐私、费用、超时、降级、提示注入防护、可审计内容和用户确认方式。

S17不是独立任务业务状态机，默认不注册ScenarioExtension。intelligence不得绕过E02/E03、Policy、幂等或目标场景schema校验，也不得把模型文本直接作为可执行脚本、SQL或Action。
