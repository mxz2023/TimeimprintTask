package cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TriggerPlannerPortImpl public method surface for P02 refactor safety. */
class TriggerPlannerPortImplTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TriggerPlannerPortImpl.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("planBinding/3",
                "planDueBindings/3"), actual);
        assertFalse(actual.isEmpty());
    }
}
