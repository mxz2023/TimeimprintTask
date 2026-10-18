package cn.net.mxz.timeimprint.task.web.inbox.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.net.mxz.timeimprint.task.domain.inbox.view.InboxClearReadView;
import cn.net.mxz.timeimprint.task.domain.inbox.view.InboxDeleteView;
import cn.net.mxz.timeimprint.task.domain.shared.request.MarkReadRequest;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.access.model.TestActorContextHolder;
import cn.net.mxz.timeimprint.task.web.inbox.controller.InboxEventHub.Cursor;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Owner test: freeze InboxController public method surface for P02 refactor safety. */
class InboxControllerTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InboxController.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("clearRead/1",
                "delete/2",
                "events/1",
                "get/1",
                "list/2",
                "markRead/2",
                "unread/0"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void eventsOpensStreamForCurrentActor() {
        TestActorContextHolder.set(new ActorContext("USER", "actor", "tenant"));
        InboxEventHub hub = new InboxEventHub(
                (tenantId, recipientType, recipientId) -> new Cursor(0, null, "2026-10-12T14:00:00Z"),
                Executors.newScheduledThreadPool(1, runnable -> {
                    Thread thread = new Thread(runnable, "inbox-events-controller-test");
                    thread.setDaemon(true);
                    return thread;
                }),
                60_000L,
                60_000L,
                InboxEventHub.MAX_AGE_MILLIS);
        try {
            HttpServletResponse response = new MockHttpServletResponse();
            InboxController controller = new InboxController(null, hub);
            assertNotNull(controller.events(response));
            assertEquals("no-cache", response.getHeader("Cache-Control"));
            assertEquals("no", response.getHeader("X-Accel-Buffering"));
        } finally {
            hub.close();
            TestActorContextHolder.clear();
        }
    }

    @Test
    void deleteAndClearReadReturnCommandViews() {
        TaskGateway gateway = mock(TaskGateway.class);
        when(gateway.deleteInbox(eq(8L), any()))
                .thenReturn(new InboxDeleteView("8", "2026-10-18T10:54:00Z"));
        when(gateway.clearReadInbox(any())).thenReturn(new InboxClearReadView(2));
        InboxController controller = new InboxController(gateway, null);
        MarkReadRequest request = new MarkReadRequest("11111111-1111-1111-1111-111111111111");
        assertEquals("8", controller.delete(8L, request).data().inboxId());
        assertEquals("2026-10-18T10:54:00Z", controller.delete(8L, request).data().deletedAt());
        assertEquals(2, controller.clearRead(request).data().clearedCount());
    }
}
