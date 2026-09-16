package cn.net.mxz.timeimprint.task.service.capability.notification.inapp.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InboxRow public method surface for P02 refactor safety. */
class InboxRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InboxRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getActionJobId/0",
                "getCreatedAt/0",
                "getDefinitionId/0",
                "getInboxId/0",
                "getInstanceId/0",
                "getNotificationId/0",
                "getReadAt/0",
                "getRecipientId/0",
                "getRecipientType/0",
                "getTenantId/0",
                "setActionJobId/1",
                "setCreatedAt/1",
                "setDefinitionId/1",
                "setInboxId/1",
                "setInstanceId/1",
                "setNotificationId/1",
                "setReadAt/1",
                "setRecipientId/1",
                "setRecipientType/1",
                "setTenantId/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
