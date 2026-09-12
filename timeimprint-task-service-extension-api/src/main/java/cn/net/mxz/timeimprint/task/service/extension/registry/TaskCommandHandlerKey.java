package cn.net.mxz.timeimprint.task.service.extension.registry;

import cn.net.mxz.timeimprint.task.service.extension.command.CommandScope;

public record TaskCommandHandlerKey(
        String scenarioKey, CommandScope scope, String commandKey, int commandSchemaVersion) {
    public TaskCommandHandlerKey {
        if (scenarioKey == null || scenarioKey.isBlank()) {
            throw new IllegalArgumentException("scenarioKey required");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope required");
        }
        if (commandKey == null || commandKey.isBlank()) {
            throw new IllegalArgumentException("commandKey required");
        }
        if (commandSchemaVersion < 1) {
            throw new IllegalArgumentException("commandSchemaVersion must be >= 1");
        }
    }
}
