package cn.net.mxz.timeimprint.task.service.storage.mysql.audit.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze AuditLogRow public method surface for P02 refactor safety. */
class AuditLogRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(AuditLogRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getActorId/0",
                "getActorType/0",
                "getAuditId/0",
                "getCreatedAt/0",
                "getDefinitionId/0",
                "getDetailJson/0",
                "getEventType/0",
                "getInstanceId/0",
                "getResourceId/0",
                "getResourceType/0",
                "getTenantId/0",
                "getTraceId/0",
                "getTransitionId/0",
                "setActorId/1",
                "setActorType/1",
                "setAuditId/1",
                "setCreatedAt/1",
                "setDefinitionId/1",
                "setDetailJson/1",
                "setEventType/1",
                "setInstanceId/1",
                "setResourceId/1",
                "setResourceType/1",
                "setTenantId/1",
                "setTraceId/1",
                "setTransitionId/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
