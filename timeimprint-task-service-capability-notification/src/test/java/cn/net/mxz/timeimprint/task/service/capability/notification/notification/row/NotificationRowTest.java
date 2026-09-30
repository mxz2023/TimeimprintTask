package cn.net.mxz.timeimprint.task.service.capability.notification.notification.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze NotificationRow public method surface for P02 refactor safety. */
class NotificationRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(NotificationRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getBody/0",
                "getCreatedAt/0",
                "getDefinitionId/0",
                "getInstanceId/0",
                "getNotificationId/0",
                "getPurpose/0",
                "getTenantId/0",
                "getTitle/0",
                "getTransitionId/0",
                "setBody/1",
                "setCreatedAt/1",
                "setDefinitionId/1",
                "setInstanceId/1",
                "setNotificationId/1",
                "setPurpose/1",
                "setTenantId/1",
                "setTitle/1",
                "setTransitionId/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
