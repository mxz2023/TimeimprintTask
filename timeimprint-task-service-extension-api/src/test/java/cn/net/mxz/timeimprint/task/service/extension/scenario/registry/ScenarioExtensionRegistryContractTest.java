package cn.net.mxz.timeimprint.task.service.extension.scenario.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze ScenarioExtensionRegistry method surface for P02 refactor safety. */
class ScenarioExtensionRegistryContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(ScenarioExtensionRegistry.class.isInterface());
        List<String> actual = Arrays.stream(ScenarioExtensionRegistry.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("find/1",
                "listAll/0",
                "require/1"), actual);
    }
}
