package cn.net.mxz.timeimprint.task.service.extension.shared.context;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;

/** 已解码的定义配置校验输入。 */
public record DefinitionConfigValidationContext(
        String scenarioKey, int scenarioSchemaVersion, ScenarioMutationPayload scenarioConfig) {}
