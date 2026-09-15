package cn.net.mxz.timeimprint.task.service.application.model;

import java.time.Instant;

public record TriggerBindingRecord(
        long triggerBindingId,
        long definitionId,
        String bindingKey,
        String providerKey,
        int schemaVersion,
        String configJson,
        String bindingState,
        long scheduleGeneration,
        Instant nextFireAt,
        boolean exhausted,
        long revision) {}
