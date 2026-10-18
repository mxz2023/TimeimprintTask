package cn.net.mxz.timeimprint.task.gateway.shared.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.net.mxz.timeimprint.task.domain.inbox.view.InboxClearReadView;
import cn.net.mxz.timeimprint.task.domain.inbox.view.InboxDeleteView;
import cn.net.mxz.timeimprint.task.domain.shared.request.MarkReadRequest;
import cn.net.mxz.timeimprint.task.service.application.inbox.service.InboxService;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner characterization test: freeze TaskGateway public method surface before T03 split. */
class TaskGatewayTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TaskGateway.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("acceptSignal/2",
                "clearReadInbox/1",
                "create/1",
                "deleteInbox/2",
                "executeDefinitionCommand/3",
                "executeInstanceCommand/3",
                "getActionJob/1",
                "getDefinition/1",
                "getInbox/1",
                "getInstance/1",
                "getSignal/1",
                "latestInboxId/3",
                "listActionJobs/6",
                "listDefinitions/5",
                "listInbox/2",
                "listInstances/9",
                "listScenarios/2",
                "listTransitions/4",
                "markRead/2",
                "preview/1",
                "processSignal/1",
                "redriveAction/2",
                "redriveSignal/2",
                "unreadCount/0",
                "unreadCountFor/3"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void exposesReadCommandAndIngressFacades() {
        List<String> names = Arrays.stream(TaskGateway.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .distinct()
                .toList();
        assertTrue(names.contains("preview") && names.contains("create") && names.contains("listDefinitions"));
        assertTrue(names.contains("executeInstanceCommand") && names.contains("executeDefinitionCommand"));
        assertTrue(names.contains("acceptSignal") && names.contains("processSignal"));
        assertTrue(names.contains("listInbox") && names.contains("markRead") && names.contains("unreadCount"));
        assertTrue(names.contains("deleteInbox") && names.contains("clearReadInbox"));
        assertTrue(names.contains("redriveSignal") && names.contains("redriveAction"));
    }

    @Test
    void deleteAndClearReadMapServiceResults() {
        InboxService inbox = mock(InboxService.class);
        when(inbox.delete(8L, "11111111-1111-1111-1111-111111111111"))
                .thenReturn(new InboxService.DeletedInbox(8L, Instant.parse("2026-10-18T10:54:00Z")));
        when(inbox.clearRead("22222222-2222-2222-2222-222222222222"))
                .thenReturn(new InboxService.ClearedRead(3));
        TaskGateway gateway = new TaskGateway(
                null, null, null, null, null, inbox, null, null, null, null, null, null, null);
        MarkReadRequest deleteRequest = new MarkReadRequest("11111111-1111-1111-1111-111111111111");
        InboxDeleteView deleted = gateway.deleteInbox(8L, deleteRequest);
        assertEquals("8", deleted.inboxId());
        assertEquals("2026-10-18T10:54:00Z", deleted.deletedAt());
        InboxClearReadView cleared = gateway.clearReadInbox(
                new MarkReadRequest("22222222-2222-2222-2222-222222222222"));
        assertEquals(3, cleared.clearedCount());
    }
}
