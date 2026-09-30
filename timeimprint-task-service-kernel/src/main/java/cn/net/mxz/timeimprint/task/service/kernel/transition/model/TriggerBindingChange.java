package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;

/** 触发绑定后续变化声明。 */
public record TriggerBindingChange(
        String bindingKey,
        String providerKey,
        int schemaVersion,
        ScenarioMutationPayload configPayload,
        Long nextFireAtEpochSecond,
        Boolean exhausted,
        Long scheduleGeneration) {}
