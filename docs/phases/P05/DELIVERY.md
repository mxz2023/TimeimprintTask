# P05 · 交付证据

> 本文记录 P05 已经验证的实现证据。只有经过真实命令执行、退出码为零且测试通过的条目才能记为通过。
> 阶段身份与范围见 [README](README.md)，任务状态见 [IMPLEMENTATION](IMPLEMENTATION.md)，验收项定义见 [07 §6.1](../../07-ACCEPTANCE.md)。

recordedAtUtc: 2026-10-06T02:27:32Z
阶段状态: VERIFYING（证据已收集；尚未 RELEASED，未打 Git 标签，待人工验收）

---

## 1. 环境

| 项目 | 值 |
| --- | --- |
| baselineGitRef | `9d9dc87f494a4738cf948b4fc38f14b947d456a2`（T01 READY 提交；授权实施起点，见 [README](README.md)） |
| 证据所测代码基线 | `57161c5e2347163be82f0ad6b80982b132196cea`（T04 提交）之上的 T05 工作区改动；T05 改动在证据收集时**尚未提交**，提交后以提交哈希为准 |
| JDK | Amazon Corretto 21.0.12（`openjdk version "21.0.12" 2026-07-21 LTS`） |
| 操作系统 | macOS 27.0.1（arm64） |
| Maven Wrapper | 3.9.9 |
| MySQL 镜像 | `mysql:9.7.2`（容器 `tit-mysql-t01`，`127.0.0.1:13306`） |
| MySQL 镜像 Digest | `sha256:29abb0a179982e4a8928138bfc7f918af9eda64e7eeb1b1d084c1720a20159e6` |
| 验收库 | 功能库 `timeimprint_task_local`；性能库 `timeimprint_task_perf`（沿用 [P04 DELIVERY](../P04/DELIVERY.md) 约定） |
| 基础发布 | P04；Git 标签 `v20261003-P04` |

## 2. 验证命令

| 命令 | 退出码 | 结果摘要 |
| --- | --- | --- |
| `./mvnw -q -o test` | 0 | 14 个模块 Surefire 合计 **391** run / 0 fail / 0 error / 0 skipped；Enforcer 规则（含 T05 新增）随生命周期执行并通过 |
| `./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify` | 0 | BUILD SUCCESS；上游模块 Surefire 同为 391 项通过；boot-loader Failsafe **99** run / 0 fail / 0 error / 0 skipped（含 `FeishuCardActionMysqlIT` 7 项） |
| `./mvnw -Pdual-process-it -pl timeimprint-task-boot-loader -am verify` | 0 | BUILD SUCCESS；Failsafe **7** run / 0 fail / 0 error（含 `PerfGateDualProcessIT` 2 项、`A04A20DualProcessIT` 2 项、`FairnessBacklogDualProcessIT`、`TakeoverSlaDualProcessIT`、`DualClaimMysqlIT` 各 1 项） |
| `git diff --check` | 0 | 无空白错误 |

说明：

- 两条 IT 命令按 [P04 DELIVERY](../P04/DELIVERY.md) 的同一写法使用 `-pl timeimprint-task-boot-loader -am`，把上游模块一并构建；未执行把 `mysql-it,dual-process-it` 写在同一条 `-P` 的组合命令，两个 Profile 分别执行并各自通过。
- 真库 IT 与双进程 IT 在同一台本机顺序执行，未并发。
- 日志留存（本机，非仓库）：`/tmp/p05-unit-test.log`、`/tmp/p05-mysql-it.log`、`/tmp/p05-dual-it.log`。
- 本期没有重新执行 `package -DskipTests`；P05 未改打包方式。

## 3. F01—F10 逐项证据

下表“证据测试”均包含在上节命令内；PASS 仅表示该命令内相应测试通过，不等于飞书真实租户验证（见 §6）。

| 编号 | 验收项 | 结论 | 证据 |
| --- | --- | --- | --- |
| [F01](../../07-ACCEPTANCE.md) | 默认投递仅 `IN_APP` | PASS | `NotificationDeliveryPropertiesContextTest.defaultsToInAppOnly`、`SignalProcessingServiceTest.inAppOnlyKeepsLegacyActionKeyAndHandler`（既有 actionKey 不变）、`FeishuOutboundConfigurationContextTest.noChannelPropertyAtAllAssemblesNoClient`、`FeishuInboundEnabledConditionTest.disabledByDefaultAndWhenFeishuNotInChannels`、`FeishuInboundConfigurationContextTest.feishuChannelWithoutVerificationTokenAssemblesNothing`；`./mvnw -q -o test`。默认配置下真库全量 99 项通过（`./mvnw -Pmysql-it … verify`） |
| [F02](../../07-ACCEPTANCE.md) | 双渠展开 | PASS（单元/契约级） | `SignalProcessingServiceTest.recipientTimesChannelExpansionUsesDistinctActionKeys`（`IN_APP`+`FEISHU` 产生 `in_app_notification` 与 `feishu_im_notification` 各一条，EXTERNAL，actionKey 互异且不含接收人）、`NotificationDeliveryChannelAdapterTest.feishuMapsToExternalHandler`、`InAppActionKeyFormatTest`、`RecurringTodoCommandHandlerTest.snoozeEmitsOneIntentPerSlotWhenSameSlotHasMultipleChannelActions`；`./mvnw -q -o test`。**无**同时开启两渠道的真库展开 IT |
| [F03](../../07-ACCEPTANCE.md) | 出站卡片 | PASS | `FeishuImNotificationHandlerTest.s02CardCarriesThreeButtonsWithCommandValues`、`…s01CardIsDisplayOnlyWithoutButtons`、`FeishuCardBuilderTest.actionCardHasThreeCallbackButtonsInOrder`；`FeishuOutboundMockSendTest.s02SendsTokenThenInteractiveCardWithButtonsAndIdempotencyUuid`、`…s01SendsInteractiveCardWithoutButtons`（Mock HTTP 上断言 `msg_type=interactive`）；`HttpFeishuMessageClientTest`；`./mvnw -q -o test` |
| [F04](../../07-ACCEPTANCE.md) | 出站幂等与受理 | PASS | `HttpFeishuMessageClientTest`（`uuid` 随请求发送、返回 `message_id`、业务错误/限流/5xx/令牌失效的分类）、`FeishuImNotificationHandlerTest.uuidDerivedFromActionKeyIsStableAndAtMostFifty`、`…sendFailuresMapToOutcomeByCategory`、`FeishuOutboundMockSendTest.feishuRejectionIsObservableAsPermanentFailure`；`./mvnw -q -o test` |
| [F05](../../07-ACCEPTANCE.md) | 缺映射 | PASS（Handler 级） | `FeishuImNotificationHandlerTest.missingRecipientMapIsPermanentFailureAndNothingSent`（无 `recipient-map` → `PERMANENT_FAILURE`，`FEISHU_RECIPIENT_UNMAPPED`，不发 HTTP）；站内信与飞书是互相独立的 Action Job（见 F02 证据）。**无**同一用例内同时断言飞书失败与站内信成功的真库 IT；`./mvnw -q -o test` |
| [F06](../../07-ACCEPTANCE.md) | 入站完成 | PASS | `FeishuCardActionMysqlIT.completeCallbackTerminatesInstanceAndReplayIsIdempotent`（真库）；`FeishuCardActionBridgeTest` 命令映射；`./mvnw -Pmysql-it -pl timeimprint-task-boot-loader -am verify` |
| [F07](../../07-ACCEPTANCE.md) | 入站跳过 | PASS | `FeishuCardActionMysqlIT.skipCallbackUsesFixedReason`（原因「飞书卡片跳过」）；同上命令 |
| [F08](../../07-ACCEPTANCE.md) | 入站稍后 +1h | PASS | `FeishuCardActionMysqlIT.snoozeCallbackShiftsToEventTimePlusOneHourAndReplayDoesNotSnoozeTwice`；同上命令 |
| [F09](../../07-ACCEPTANCE.md) | 入站幂等与冲突 | PASS | `FeishuCardActionMysqlIT.completeCallback…ReplayIsIdempotent`、`…snoozeCallback…ReplayDoesNotSnoozeTwice`（同 `event_id` 重放不重复生效）、`…staleRevisionYieldsWarningToastWithoutOverwriting`（revision 冲突 toast）、`…forgedTokenIsUnauthorizedAndUnmappedOperatorIsRejected`、`…reminderScenarioInstanceIsRejectedWithErrorToast`；`FeishuCardActionBridgeTest` 结果映射；同上命令 |
| [F10](../../07-ACCEPTANCE.md) | 模块边界 | PASS | 见 §4。`ArchitectureRulesTest`（`timeimprint-task-boot-loader` 模块）8 项全绿，其中 T05 新增 4 项；Enforcer `enforce-no-adapter` / `enforce-adapter-leaf` 随 `./mvnw -q -o test` 通过 |

任务对应关系：[T02](IMPLEMENTATION.md)（adapter 骨架与渠道展开）、[T03](IMPLEMENTATION.md)（出站卡片）、[T04](IMPLEMENTATION.md)（入站回调）、[T05](IMPLEMENTATION.md)（依赖门禁与回归）。

## 4. F10 依赖门禁细节

| 门禁 | 位置 | 断言 |
| --- | --- | --- |
| kernel 与 scenario 不依赖 adapter | `ArchitectureRulesTest.kernelAndScenarioMustNotDependOnAdapter` | `service.kernel..`、`service.scenario..` 的类不引用 `cn.net.mxz.timeimprint.task.adapter..`；并断言 scenario 类已被导入，防止规则空转 |
| 仅允许的消费者可依赖 adapter | `ArchitectureRulesTest.onlyNotificationGatewayWebAndBootMayDependOnAdapter` | common、domain、kernel、extension、application、runtime、storage、calendar、scenario 的类均不引用 adapter；notification、gateway、web、boot-loader 为 [02](../../02-AI-CODING-GUIDE.md) 允许的消费者 |
| adapter 不反向依赖业务 | `ArchitectureRulesTest.adapterMustNotDependOnBusinessModules` | adapter 类不引用 `service..`、`domain..`、`gateway..`、`web..`、`boot..`；并断言 adapter 类已被导入 |
| 飞书 SDK 与出站 HTTP 仅在 adapter | `ArchitectureRulesTest.feishuSdkAndOutboundHttpStayInsideAdapter` | adapter 之外的生产类不引用 `com.lark..`、`com.larksuite..`、`java.net.http..`、`okhttp3..`、`org.apache.hc..`、`org.apache.http..`、Spring `RestClient`/`WebClient` 相关包 |
| Maven 依赖图 | 各模块 POM `enforce-no-adapter`（`maven-enforcer-plugin` `bannedDependencies`，含传递依赖） | `domain`、`kernel`、`extension-api`、`application`、`runtime`、`calendar`、`scenario-basic` 的依赖树中不得出现 `timeimprint-task-adapter` |
| adapter 叶子模块 | `timeimprint-task-adapter/pom.xml` `enforce-adapter-leaf` | 项目内依赖只允许 `timeimprint-task-common` |

负向验证：把 `FeishuOutboundConfiguration` 临时还原为 T04 版本（直接 `HttpClient.newBuilder()`）后，`feishuSdkAndOutboundHttpStayInsideAdapter` 失败并指出 `java.net.http.HttpClient` 调用位置；恢复后通过。

T05 为让门禁成立所做的生产改动：

| 改动 | 原因 |
| --- | --- |
| `timeimprint-task-service-scenario-basic` 移除对 `timeimprint-task-service-capability-notification` 的依赖，场景内用渠道中立展开键常量（取值仍为 `in_app_notification`、schema 1，既有 actionKey 不变） | 该依赖使 scenario 经 notification **传递依赖** adapter；且超出 [02](../../02-AI-CODING-GUIDE.md) 模块表允许的 scenario 依赖（extension-api、kernel、calendar、common） |
| `HttpFeishuMessageClient.create(…)`（`timeimprint-task-adapter` 模块）由 adapter 内部创建 JDK `HttpClient` 与令牌提供器；`FeishuOutboundConfiguration`（`timeimprint-task-service-capability-notification` 模块）改为调用它 | 让 JDK HTTP 只出现在 adapter；notification 不再直接引用 `java.net.http` |

## 5. 模块说明

| 项目 | 值 |
| --- | --- |
| 模块总数 | 14（P01—P04 的 13 个 + `timeimprint-task-adapter`；`ModuleBaselineTest` 通过） |
| adapter 包 | 仅 `cn.net.mxz.timeimprint.task.adapter.feishu`（`auth`、`callback`、`client`、`configuration`） |
| 飞书实现方式 | adapter 内用 JDK `HttpClient` + Jackson 3 调用飞书开放接口，**未引入飞书官方 SDK 坐标**；若将来引入，只能加在 adapter |
| 直接依赖 adapter 的模块 | `timeimprint-task-service-capability-notification`、`timeimprint-task-gateway`、`timeimprint-task-boot-loader`；`timeimprint-task-web` 的生产类不引用 adapter 类，POM 也无直接声明（验签在 gateway 的 `FeishuCardActionBridge` 内调用 adapter） |
| 已知保留的传递依赖 | `timeimprint-task-service-storage-mysql` 直接依赖 `capability-notification`（写 `tt_notification`/`tt_inbox` 的 Mapper），因此经 notification 传递依赖 adapter。该耦合在 P05 之前已存在；本期**未**对 storage 加 `enforce-no-adapter`，其类级依赖仍受上表 ArchUnit 规则约束 |

## 6. 已知限制

| 限制 | 说明 |
| --- | --- |
| 无真实飞书租户 | CI 与本机验证均使用 Mock HTTP（`FeishuMockServerFixture`、`FeishuOutboundMockSendTest`）和伪造 `card.action.trigger` 回调；真实飞书租户联调、真实卡片渲染、飞书侧 3 秒超时行为均未验证（NOT_RUN，不属于本地验收范围） |
| F02、F05 为单元/契约级 | 无同时开启 `IN_APP`+`FEISHU` 的真库出站展开与“飞书失败而站内信成功”的同用例 IT；人工验收时请确认是否接受 |
| 文字回复 | `im.message.receive_v1` 契约保留、P05 不实现，不在验收内 |
| 其他 IM | 微信、钉钉、Telegram 未实现 |
| storage-mysql 传递依赖 | 见 §5 |

## 7. 任务闭环

| 任务 | 状态 |
| --- | --- |
| T00—T05 | PASS（本证据）；阶段 VERIFYING，等待人工验收后才可 RELEASED 与打标签 `vyyyyMMdd-P05` |

[NOT-05](../../capabilities/CAP03-notification.md)（多渠道与飞书整体接入）的 implementationStatus 仍为 IN_PROGRESS；只有人工 RELEASED 后才可改为 VERIFIED。
