package cn.net.mxz.timeimprint.task.common.wake;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 同一进程内的收件变化唤醒。调用方必须在收件事务提交之后调用 {@link #wake}。
 * 没有监听者时直接返回，不保留待投递事件。监听者抛出的异常不会传回调用方。
 */
public final class InboxWake {

    private static final List<Consumer<Recipient>> LISTENERS = new CopyOnWriteArrayList<>();

    private InboxWake() {}

    public static Subscription listen(Consumer<Recipient> listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    public static void wake(String tenantId, String recipientType, String recipientId) {
        Recipient recipient = new Recipient(tenantId, recipientType, recipientId);
        for (Consumer<Recipient> listener : LISTENERS) {
            try {
                listener.accept(recipient);
            } catch (RuntimeException ignored) {
                // 推送失败不能影响已经提交的收件。
            }
        }
    }

    public record Recipient(String tenantId, String recipientType, String recipientId) {}

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
