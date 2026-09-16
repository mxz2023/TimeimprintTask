package cn.net.mxz.timeimprint.task.service.extension.policy.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze PolicyRegistry method surface for P02 refactor safety. */
class PolicyRegistryContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(PolicyRegistry.class.isInterface());
        List<String> actual = Arrays.stream(PolicyRegistry.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("policiesForPhase/1"), actual);
    }
}
