package cn.net.mxz.timeimprint.task.service.extension.materialization.spi;

public record ScenarioDataMaterializerKey(String scenarioKey, String mutationKey, int schemaVersion) {
    public ScenarioDataMaterializerKey {
        if (scenarioKey == null || scenarioKey.isBlank()) {
            throw new IllegalArgumentException("scenarioKey required");
        }
        if (mutationKey == null || mutationKey.isBlank()) {
            throw new IllegalArgumentException("mutationKey required");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
    }
}
