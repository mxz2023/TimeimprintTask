package cn.net.mxz.timeimprint.task.web.inbox.controller;

import cn.net.mxz.timeimprint.task.common.wake.InboxWake;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 把已提交的收件变化写进当前进程上打开的 E30 响应。
 * 本进程由 {@link InboxWake} 立即唤醒；另一进程写入的变化靠 5 秒水位核对补上。
 */
@Component
public class InboxEventHub {

    static final long HEARTBEAT_MILLIS = 25_000L;
    static final long POLL_MILLIS = 5_000L;
    static final long MAX_AGE_MILLIS = 30L * 60L * 1000L;

    private final CursorSource cursors;
    private final ScheduledExecutorService scheduler;
    private final InboxWake.Subscription subscription;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Stream>> streams = new ConcurrentHashMap<>();

    @Autowired
    public InboxEventHub(TaskGateway gateway, BusinessClock clock) {
        this(
                (tenantId, recipientType, recipientId) -> new Cursor(
                        gateway.unreadCountFor(tenantId, recipientType, recipientId),
                        gateway.latestInboxId(tenantId, recipientType, recipientId),
                        clock.nowUtcSeconds().toString()),
                scheduler(),
                HEARTBEAT_MILLIS,
                POLL_MILLIS,
                MAX_AGE_MILLIS);
    }

    InboxEventHub(CursorSource cursors, ScheduledExecutorService scheduler, long heartbeatMillis, long pollMillis, long maxAgeMillis) {
        this.cursors = cursors;
        this.scheduler = scheduler;
        this.subscription = InboxWake.listen(this::onWake);
        this.heartbeatMillis = heartbeatMillis;
        this.pollMillis = pollMillis;
        this.maxAgeMillis = maxAgeMillis;
    }

    private final long heartbeatMillis;
    private final long pollMillis;
    private final long maxAgeMillis;

    public SseEmitter open(ActorContext actor) {
        SseEmitter emitter = new SseEmitter(maxAgeMillis);
        Stream stream = new Stream(actor, emitter);
        streams.computeIfAbsent(key(actor), ignored -> new CopyOnWriteArrayList<>()).add(stream);
        emitter.onCompletion(() -> remove(stream));
        emitter.onTimeout(() -> remove(stream));
        emitter.onError(error -> remove(stream));
        stream.heartbeat = scheduler.scheduleAtFixedRate(
                () -> heartbeat(stream), heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        stream.poll = scheduler.scheduleAtFixedRate(
                () -> publish(stream), pollMillis, pollMillis, TimeUnit.MILLISECONDS);
        scheduler.execute(() -> publish(stream));
        return emitter;
    }

    @PreDestroy
    public void close() {
        subscription.close();
        scheduler.shutdownNow();
        streams.clear();
    }

    static String frame(int unreadCount, Long latestInboxId, String asOf) {
        return "{\"unreadCount\":"
                + unreadCount
                + ",\"latestInboxId\":"
                + (latestInboxId == null ? "null" : latestInboxId.toString())
                + ",\"asOf\":\""
                + asOf
                + "\"}";
    }

    private void onWake(InboxWake.Recipient recipient) {
        List<Stream> open = streams.get(key(recipient.tenantId(), recipient.recipientType(), recipient.recipientId()));
        if (open == null) {
            return;
        }
        for (Stream stream : open) {
            scheduler.execute(() -> publish(stream));
        }
    }

    private void publish(Stream stream) {
        if (stream.closed) {
            return;
        }
        try {
            Cursor cursor = cursors.read(
                    stream.actor.tenantKey(), stream.actor.principalType(), stream.actor.principalId());
            String json = frame(cursor.unreadCount(), cursor.latestInboxId(), cursor.asOf());
            synchronized (stream) {
                if (json.equals(stream.lastFrame)) {
                    return;
                }
                stream.emitter.send(SseEmitter.event().name("inbox-changed").data(json, MediaType.APPLICATION_JSON));
                stream.lastFrame = json;
            }
        } catch (IOException | RuntimeException ex) {
            remove(stream);
        }
    }

    private void heartbeat(Stream stream) {
        if (stream.closed) {
            return;
        }
        try {
            stream.emitter.send(SseEmitter.event().comment(" ping"));
        } catch (IOException | RuntimeException ex) {
            remove(stream);
        }
    }

    private void remove(Stream stream) {
        if (stream.closed) {
            return;
        }
        stream.closed = true;
        if (stream.heartbeat != null) {
            stream.heartbeat.cancel(false);
        }
        if (stream.poll != null) {
            stream.poll.cancel(false);
        }
        List<Stream> open = streams.get(key(stream.actor));
        if (open != null) {
            open.remove(stream);
        }
        try {
            stream.emitter.complete();
        } catch (RuntimeException ignored) {
            // 响应尚未交给容器时，完成调用没有可写的输出。
        }
    }

    private static String key(ActorContext actor) {
        return key(actor.tenantKey(), actor.principalType(), actor.principalId());
    }

    private static String key(String tenantId, String recipientType, String recipientId) {
        return tenantId + "\0" + recipientType + "\0" + recipientId;
    }

    private static ScheduledExecutorService scheduler() {
        return Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "inbox-events");
            thread.setDaemon(true);
            return thread;
        });
    }

    @FunctionalInterface
    interface CursorSource {
        Cursor read(String tenantId, String recipientType, String recipientId);
    }

    record Cursor(int unreadCount, Long latestInboxId, String asOf) {}

    private static final class Stream {
        private final ActorContext actor;
        private final SseEmitter emitter;
        private ScheduledFuture<?> heartbeat;
        private ScheduledFuture<?> poll;
        private String lastFrame;
        private volatile boolean closed;

        private Stream(ActorContext actor, SseEmitter emitter) {
            this.actor = actor;
            this.emitter = emitter;
        }
    }
}
