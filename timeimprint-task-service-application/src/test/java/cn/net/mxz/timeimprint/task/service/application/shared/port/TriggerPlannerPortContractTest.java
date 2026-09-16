package cn.net.mxz.timeimprint.task.service.application.shared.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze TriggerPlannerPort method surface for P02 refactor safety. */
class TriggerPlannerPortContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(TriggerPlannerPort.class.isInterface());
        List<String> actual = Arrays.stream(TriggerPlannerPort.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("planBinding/3",
                "planDueBindings/3"), actual);
    }
}
