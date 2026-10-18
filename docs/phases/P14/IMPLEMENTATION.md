# P14 · 实施任务

> 阶段身份与范围见 [README](README.md)。当前总体状态是 DRAFT。DRAFT 与 READY 期间只写文档，不改代码、测试、配置、迁移或部署脚本。

## 1. 任务状态

| 任务 | 内容 | 状态 | 进入条件 | 完成证据 |
| --- | --- | --- | --- | --- |
| T00 | 建立阶段包，登记时间段方案与接口草案 | PASS | 老板 2026-10-19 确认需求（经 MyStudio） | 本文件与 README 存在；00 与阶段索引指向 P14 |
| T01 | 按老板对 README 第 8 节的答复定稿；把 calendar `schemaVersion=3`、`timeWindow`、原因键、日类型判定、过滤规划与 L01—L09 写入 01、03、04、06、07、09、CAP01、能力索引、S01、S02、HUMAN-GLOSSARY；CAL-07（中国大陆子集）与 CAL-17 转 READY_FOR_IMPLEMENTATION | NOT_STARTED | T00；转 READY 前完成 | 正式契约与 README 第 2、3、7 节一致；Markdown 链接、编号与 `git diff --check` 通过 |
| T02 | calendar：解码 3、段校验与原因键、日类型判定、内置 2026 年数据文件及加载校验、过滤与 400 天搜索（小时/分钟规则跳段） | NOT_STARTED | 用户将本阶段转为 IMPLEMENTING 并授权；写代码前重记 baselineGitRef | L02、L03、L04、L05 单元与契约测试 |
| T03 | 预览、创建、更新（1/2→3 升级与 NoChange）、控制与 Planner 接入；`next_fire_at`/`exhausted` 推进；S01/S02 接受 3，S03—S05/S14 拒绝 | NOT_STARTED | T02 | L01、L06、L07、L08 真库 PASS |
| T04 | readiness 与健康详情：数据文件校验、缺年份告警、A35 接受 3 | NOT_STARTED | T02 | L05 中 readiness 部分 PASS |
| T05 | 全量真库回归并创建 DELIVERY，转 VERIFYING | NOT_STARTED | T03、T04 | L09；`mysql-it`、`dual-process-it` 退出码 0，记录于 DELIVERY |
| T06 | 记录人工步骤结果并发布 | NOT_STARTED | T05；Website 同期阶段可用；README 第 6 节由人做完 | DELIVERY 记录每一步谁做、看到什么；标签 `v` + 日期 + `-P14` |

## 2. 阶段门槛

| 门槛 | 要求 |
| --- | --- |
| DRAFT → READY | 老板答复 README 第 8 节；T01 完成；用户确认第 6 节必要人工步骤 |
| READY → IMPLEMENTING | 用户明确授权实施；写代码前记录 baselineGitRef |
| IMPLEMENTING → VERIFYING | T02—T05 自动证据写入 DELIVERY |
| VERIFYING → RELEASED | 用户亲手完成第 6 节四步并确认结果；打标签并冻结本阶段 |

## 3. 合并方式

本阶段所有进入 `develop` 的合并使用 merge commit，不用 squash 或 rebase（工作站约定，2026-10-19）。
