package cn.net.mxz.timeimprint.task.service.capability.notification.inapp.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.net.mxz.timeimprint.task.common.wake.InboxWake;
import cn.net.mxz.timeimprint.task.service.capability.notification.inapp.mapper.InboxMapper;
import cn.net.mxz.timeimprint.task.service.capability.notification.inapp.row.InboxRow;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InAppNotificationHandler public method surface for P02 refactor safety. */
class InAppNotificationHandlerTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InAppNotificationHandler.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("execute/1",
                "executionMode/0",
                "registrationKey/0",
                "supportedSchemaVersions/0",
                "timeoutSeconds/0"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void createdInboxWakesRecipientAfterInsert() {
        AtomicReference<InboxWake.Recipient> seen = new AtomicReference<>();
        InboxWake.Subscription subscription = InboxWake.listen(seen::set);
        try {
            InAppNotificationHandler handler = new InAppNotificationHandler(new InsertOnlyMapper());
            var result = handler.execute(context());
            assertEquals(ActionHandlerOutcome.SUCCEEDED, result.outcome());
            assertEquals("INBOX_CREATED", result.outcomeCode());
            assertEquals("actor-a", seen.get().recipientId());
        } finally {
            subscription.close();
        }
    }

    @Test
    void duplicateInboxDoesNotWake() {
        AtomicReference<InboxWake.Recipient> seen = new AtomicReference<>();
        InboxWake.Subscription subscription = InboxWake.listen(seen::set);
        try {
            InAppNotificationHandler handler = new InAppNotificationHandler(new InsertOnlyMapper() {
                @Override
                public void insert(InboxRow row) {
                    throw new IllegalStateException("Duplicate entry uk_inbox_action");
                }
            });
            var result = handler.execute(context());
            assertEquals("INBOX_ALREADY_EXISTS", result.outcomeCode());
            assertNull(seen.get());
        } finally {
            subscription.close();
        }
    }

    private static ActionExecutionContext context() {
        return new ActionExecutionContext(
                7L,
                1L,
                2L,
                3L,
                "key",
                1,
                new JsonPayload(Map.of(
                        "notificationId", 4L,
                        "definitionId", 1L,
                        "instanceId", 2L,
                        "tenantId", "tenant",
                        "recipientType", "USER",
                        "recipientId", "actor-a")),
                "token");
    }

    private static class InsertOnlyMapper implements InboxMapper {
        @Override
        public void insert(InboxRow row) {}

        @Override
        public InboxRow selectById(long inboxId, String tenantId, String recipientType, String recipientId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<InboxRow> selectByRecipient(
                String tenantId, String recipientType, String recipientId, boolean unreadOnly, int limit, String cursor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countUnread(String tenantId, String recipientType, String recipientId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markRead(long inboxId, String tenantId, String recipientType, String recipientId, LocalDateTime readAt) {
            throw new UnsupportedOperationException();
        }
    }
}
