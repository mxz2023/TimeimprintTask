package cn.net.mxz.timeimprint.task.service.extension.shared.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze ExtensionRegistry method surface for P02 refactor safety. */
class ExtensionRegistryContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(ExtensionRegistry.class.isInterface());
        List<String> actual = Arrays.stream(ExtensionRegistry.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("actionHandlers/0",
                "commandHandlers/0",
                "policies/0",
                "scenarioDataMaterializers/0",
                "scenarioExtensions/0",
                "triggerProviders/0"), actual);
    }
}
