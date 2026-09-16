package cn.net.mxz.timeimprint.task.service.application.access.model;

import java.util.Optional;

/** Request-scoped test identity; populated by {@code test} profile servlet filter. */
public final class TestActorContextHolder {

    private static final ThreadLocal<ActorContext> CURRENT = new ThreadLocal<>();

    private TestActorContextHolder() {}

    public static void set(ActorContext context) {
        CURRENT.set(context);
    }

    public static Optional<ActorContext> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
