package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;

/** 已解码的定义配置校验输入。 */
public record DefinitionConfigValidationContext(
        String scenarioKey, int scenarioSchemaVersion, ScenarioMutationPayload scenarioConfig) {}
