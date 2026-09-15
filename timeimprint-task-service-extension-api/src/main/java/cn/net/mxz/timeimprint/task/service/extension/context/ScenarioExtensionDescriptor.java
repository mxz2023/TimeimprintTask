package cn.net.mxz.timeimprint.task.service.extension.context;

import java.util.List;

/** 场景扩展注册描述（公开命令清单与能力依赖等）。 */
public record ScenarioExtensionDescriptor(
        String scenarioKey,
        int contractVersion,
        List<String> declaredInstanceCommandKeys,
        List<String> requiredCapabilityKeys) {}
