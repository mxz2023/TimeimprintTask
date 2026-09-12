package cn.net.mxz.timeimprint.task.service.extension.registry;

public record ScenarioExtensionKey(String scenarioKey, int contractVersion) {
    public ScenarioExtensionKey {
        if (scenarioKey == null || scenarioKey.isBlank()) {
            throw new IllegalArgumentException("scenarioKey required");
        }
        if (contractVersion < 1) {
            throw new IllegalArgumentException("contractVersion must be >= 1");
        }
    }
}
