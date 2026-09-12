package cn.net.mxz.timeimprint.task.service.extension.spi;

import cn.net.mxz.timeimprint.task.service.extension.context.MxzDefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzInitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzScenarioExtensionDescriptor;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzSignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;

/**
 * 场景元数据、配置校验、初始规划、Signal 迁移与投影；不处理 HTTP 命令、不更新公共表。
 */
public interface ScenarioExtension {

    ScenarioExtensionKey registrationKey();

    MxzScenarioExtensionDescriptor descriptor();

    /** 校验完整定义配置；失败以技术异常或 Rejected 表达，由 application 映射。 */
    void validateDefinitionConfig(MxzDefinitionConfigValidationContext context);

    HandlerResult planInitialDefinition(MxzInitialDefinitionContext context);

    HandlerResult processSignal(MxzSignalProcessContext context);

    /** 校验场景业务状态代码在当前 schema 下是否合法。 */
    void validateScenarioState(String scenarioState, int scenarioSchemaVersion);
}
