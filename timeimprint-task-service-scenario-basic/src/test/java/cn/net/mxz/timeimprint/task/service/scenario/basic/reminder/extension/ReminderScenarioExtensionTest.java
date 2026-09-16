package cn.net.mxz.timeimprint.task.service.scenario.basic.reminder.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ReminderScenarioExtension public method surface for P02 refactor safety. */
class ReminderScenarioExtensionTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(ReminderScenarioExtension.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("descriptor/0",
                "planInitialDefinition/1",
                "processSignal/1",
                "registrationKey/0",
                "validateDefinitionConfig/1",
                "validateScenarioState/2"), actual);
        assertFalse(actual.isEmpty());
    }
}
