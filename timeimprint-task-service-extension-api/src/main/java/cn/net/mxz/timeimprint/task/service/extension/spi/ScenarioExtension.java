package cn.net.mxz.timeimprint.task.service.extension.spi;

import cn.net.mxz.timeimprint.task.service.extension.context.MxzDefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzInitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzScenarioExtensionDescriptor;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzSignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;

/**
 * 一种「任务玩法」的扩展插件（例如提醒 reminder、周期待办 recurring_todo）。
 *
 * <p>可以把它理解成：平台内核负责存表、锁、幂等、Worker；本接口负责「这种玩法自己的规则」。
 * 进程启动时由 Spring 注册进扩展表；公开接口 E01（GET /api/v1/task-scenarios）列出的就是这些已装配实现，
 * 不是数据库里的业务行，因此也没有「创建场景」HTTP 接口。
 *
 * <p><b>本接口负责：</b>
 * <ul>
 *   <li>对外说明自己支持什么（元数据、命令清单、依赖能力）</li>
 *   <li>创建定义前/时校验场景配置是否合法</li>
 *   <li>创建定义时算出「第一次该怎么落实例 / Signal / Action」的计划</li>
 *   <li>收到 Signal 时算出状态怎么变、要不要发通知等</li>
 *   <li>校验某个场景状态码（如 PLANNED、PENDING）在当前版本下是否合法</li>
 * </ul>
 *
 * <p><b>本接口不负责：</b>
 * <ul>
 *   <li>HTTP 入参解析、鉴权、幂等键（由 web / application 做）</li>
 *   <li>用户点的业务命令如 complete/skip（由 {@link TaskCommandHandler} 做）</li>
 *   <li>直接 UPDATE/INSERT 公共表（只返回 {@link HandlerResult} / TransitionPlan，由统一提交器落库）</li>
 * </ul>
 */
public interface ScenarioExtension {

    /**
     * 注册主键：场景键 + 契约版本。
     *
     * <p>例如 {@code reminder} + 契约版本号。同一进程内必须唯一，供扩展表查找与 E01 目录使用。
     */
    ScenarioExtensionKey registrationKey();

    /**
     * 给人（和 AI）看的场景说明书。
     *
     * <p>包括展示名、支持的 schema 版本、定义级/实例级有哪些 commandKey、依赖哪些能力等。
     * E01 返回的 {@code ScenarioMetadataView} 主要来自这里。
     */
    MxzScenarioExtensionDescriptor descriptor();

    /**
     * 校验「创建/更新任务定义」时带来的场景配置是否完整合法。
     *
     * <p>例如 reminder 要求 {@code scenarioConfig} 为空对象；recurring_todo 校验追催间隔等。
     * 失败时抛技术异常或通过后续结果表达拒绝，由 application 映射成 HTTP 错误码。
     * 只做校验，不写库。
     */
    void validateDefinitionConfig(MxzDefinitionConfigValidationContext context);

    /**
     * 创建定义时的「初始规划」：第一次该生成哪些实例、Signal、Action 意图。
     *
     * <p>在 E03 创建事务里由 application 调用。这里只计算计划（TransitionPlan 等），
     * 真正落公共表由平台统一提交器完成，避免场景模块各自写 SQL。
     */
    HandlerResult planInitialDefinition(MxzInitialDefinitionContext context);

    /**
     * 处理一条已领取的 Signal：按玩法规则决定实例状态如何迁移、要不要发站内信等。
     *
     * <p>典型路径：日历到期 Signal → S01 变为 TRIGGERED 并产生通知 Action；
     * S02 变为 PENDING 并挂上 INITIAL/CHASE 等 Action。同样只返回计划，不直接改表。
     */
    HandlerResult processSignal(MxzSignalProcessContext context);

    /**
     * 检查某个场景业务状态字符串在当前 schema 版本下是否允许出现。
     *
     * <p>例如 S01 允许 {@code PLANNED}/{@code TRIGGERED}，S02 允许 {@code PENDING}/{@code COMPLETED} 等。
     * 用于读模型/写入前的防护，防止脏状态码进入公共表或对外暴露。
     */
    void validateScenarioState(String scenarioState, int scenarioSchemaVersion);
}
