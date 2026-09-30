package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

/**
 * TransitionPlan 中的场景专有数据变更声明（不含 SQL、Mapper、类名或 URL）。
 */
public record ScenarioDataMutation(
        String scenarioKey, String mutationKey, int schemaVersion, ScenarioMutationPayload payload) {
    public ScenarioDataMutation {
        if (scenarioKey == null || scenarioKey.isBlank()) {
            throw new IllegalArgumentException("scenarioKey required");
        }
        if (mutationKey == null || mutationKey.isBlank()) {
            throw new IllegalArgumentException("mutationKey required");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload required");
        }
    }
}
