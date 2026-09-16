package cn.net.mxz.timeimprint.task.service.application.shared.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze ActorContextProvider method surface for P02 refactor safety. */
class ActorContextProviderContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(ActorContextProvider.class.isInterface());
        List<String> actual = Arrays.stream(ActorContextProvider.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("currentActor/0",
                "requireCurrentActor/0"), actual);
    }
}
