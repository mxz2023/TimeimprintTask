package cn.net.mxz.timeimprint.task.service.storage.mysql.transition.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskTransitionRow public method surface for P02 refactor safety. */
class TaskTransitionRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TaskTransitionRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getActorId/0",
                "getActorType/0",
                "getCommandKey/0",
                "getCreatedAt/0",
                "getDefinitionId/0",
                "getFromControlState/0",
                "getFromLifecycle/0",
                "getFromRevision/0",
                "getFromScenarioState/0",
                "getInstanceId/0",
                "getSourceKey/0",
                "getSourceType/0",
                "getSummaryJson/0",
                "getToControlState/0",
                "getToLifecycle/0",
                "getToRevision/0",
                "getToScenarioState/0",
                "getTraceId/0",
                "getTransitionId/0",
                "setActorId/1",
                "setActorType/1",
                "setCommandKey/1",
                "setCreatedAt/1",
                "setDefinitionId/1",
                "setFromControlState/1",
                "setFromLifecycle/1",
                "setFromRevision/1",
                "setFromScenarioState/1",
                "setInstanceId/1",
                "setSourceKey/1",
                "setSourceType/1",
                "setSummaryJson/1",
                "setToControlState/1",
                "setToLifecycle/1",
                "setToRevision/1",
                "setToScenarioState/1",
                "setTraceId/1",
                "setTransitionId/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
