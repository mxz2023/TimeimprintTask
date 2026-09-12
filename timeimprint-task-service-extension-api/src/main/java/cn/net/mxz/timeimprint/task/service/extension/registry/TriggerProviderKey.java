package cn.net.mxz.timeimprint.task.service.extension.registry;

public record TriggerProviderKey(String providerKey, int contractVersion) {
    public TriggerProviderKey {
        if (providerKey == null || providerKey.isBlank()) {
            throw new IllegalArgumentException("providerKey required");
        }
        if (contractVersion < 1) {
            throw new IllegalArgumentException("contractVersion must be >= 1");
        }
    }
}
