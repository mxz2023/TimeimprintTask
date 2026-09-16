package cn.net.mxz.timeimprint.task.gateway.shared.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
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
                "create/1",
                "executeDefinitionCommand/3",
                "executeInstanceCommand/3",
                "getActionJob/1",
                "getDefinition/1",
                "getInbox/1",
                "getInstance/1",
                "getSignal/1",
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
                "unreadCount/0"), actual);
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
        assertTrue(names.contains("redriveSignal") && names.contains("redriveAction"));
    }
}
