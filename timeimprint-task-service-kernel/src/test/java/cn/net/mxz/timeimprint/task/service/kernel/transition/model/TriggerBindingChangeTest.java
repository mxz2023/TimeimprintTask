package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TriggerBindingChange record components for P02 refactor safety. */
class TriggerBindingChangeTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TriggerBindingChange.class.isRecord());
        List<String> actual = Arrays.stream(TriggerBindingChange.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("bindingKey",
                "providerKey",
                "schemaVersion",
                "configPayload",
                "nextFireAtEpochSecond",
                "exhausted",
                "scheduleGeneration"), actual);
    }
}
