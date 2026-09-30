package cn.net.mxz.timeimprint.task.service.storage.mysql.command.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze CommandDedupRow public method surface for P02 refactor safety. */
class CommandDedupRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(CommandDedupRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getActorId/0",
                "getCreatedAt/0",
                "getDedupId/0",
                "getOperation/0",
                "getProcessStatus/0",
                "getRequestHash/0",
                "getRequestId/0",
                "getResourceId/0",
                "getResourceRevision/0",
                "getResourceType/0",
                "getResponseJson/0",
                "getResultCode/0",
                "getTenantId/0",
                "getUpdatedAt/0",
                "setActorId/1",
                "setCreatedAt/1",
                "setDedupId/1",
                "setOperation/1",
                "setProcessStatus/1",
                "setRequestHash/1",
                "setRequestId/1",
                "setResourceId/1",
                "setResourceRevision/1",
                "setResourceType/1",
                "setResponseJson/1",
                "setResultCode/1",
                "setTenantId/1",
                "setUpdatedAt/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
