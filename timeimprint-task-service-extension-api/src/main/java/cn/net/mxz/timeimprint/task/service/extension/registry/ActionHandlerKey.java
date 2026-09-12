package cn.net.mxz.timeimprint.task.service.extension.registry;

public record ActionHandlerKey(String handlerKey, int actionSchemaVersion) {
    public ActionHandlerKey {
        if (handlerKey == null || handlerKey.isBlank()) {
            throw new IllegalArgumentException("handlerKey required");
        }
        if (actionSchemaVersion < 1) {
            throw new IllegalArgumentException("actionSchemaVersion must be >= 1");
        }
    }
}
