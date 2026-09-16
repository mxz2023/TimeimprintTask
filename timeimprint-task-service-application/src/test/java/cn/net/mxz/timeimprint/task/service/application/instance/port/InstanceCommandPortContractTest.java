package cn.net.mxz.timeimprint.task.service.application.instance.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze InstanceCommandPort method surface for P02 refactor safety. */
class InstanceCommandPortContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(InstanceCommandPort.class.isInterface());
        List<String> actual = Arrays.stream(InstanceCommandPort.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("cancelRemainingActions/2",
                "cancelRemainingActionsExceptTransition/3"), actual);
    }
}
