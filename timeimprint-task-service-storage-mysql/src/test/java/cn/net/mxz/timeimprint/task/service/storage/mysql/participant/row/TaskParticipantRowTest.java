package cn.net.mxz.timeimprint.task.service.storage.mysql.participant.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskParticipantRow public method surface for P02 refactor safety. */
class TaskParticipantRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TaskParticipantRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getCreatedAt/0",
                "getDefinitionId/0",
                "getInstanceId/0",
                "getMetadataJson/0",
                "getParticipantId/0",
                "getPrincipalId/0",
                "getPrincipalType/0",
                "getRoleCode/0",
                "getSourceCode/0",
                "getUpdatedAt/0",
                "setCreatedAt/1",
                "setDefinitionId/1",
                "setInstanceId/1",
                "setMetadataJson/1",
                "setParticipantId/1",
                "setPrincipalId/1",
                "setPrincipalType/1",
                "setRoleCode/1",
                "setSourceCode/1",
                "setUpdatedAt/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
