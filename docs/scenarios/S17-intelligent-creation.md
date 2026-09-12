# S17 · 智能创建入口规划

> 本文是S17需求的长期规划入口，不是可编码契约。当前`OUTLINE + NOT_STARTED`。S17默认不是独立任务状态机，也不注册普通ScenarioExtension。

| 项目 | 值 |
| --- | --- |
| scenarioKey | 不适用；若未来改为独立场景须重新决策 |
| 目标模块 | 不新增场景模块 |
| planningPosition | BACKLOG |
| contractStatus | OUTLINE |
| implementationStatus | NOT_STARTED |
| 依赖能力 | [intelligence](../capabilities/CAP08-intelligence.md)及被创建目标场景的全部能力 |

## 已规划内容

将“每周五提醒交周报”等自然语言转换为目标场景的强类型配置，提供日期理解、规则解释、时间预览和修改建议。模型输出必须经过目标场景与能力校验，并由用户确认后调用普通创建流程。

## 编码前必须决定

- 首批允许生成哪些已RELEASED或READY场景。
- 歧义澄清、用户确认、修改反馈和失败降级交互。
- 模型供应商、隐私、费用、超时、审计和提示注入防护。
- 结构化输出schema、置信度及不可执行内容过滤。
- API/UI入口、权限、验收以及是否需要保存会话数据。

Spring AI 2.0.x技术版本基线不表示本能力存在。AI输出不得直接成为SQL、脚本、任意Action或绕过E02/E03、Policy和幂等的写操作。
