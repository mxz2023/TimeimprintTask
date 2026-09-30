package cn.net.mxz.timeimprint.task.domain.shared.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TriggerBindingInput record components for P02 refactor safety. */
class TriggerBindingInputTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TriggerBindingInput.class.isRecord());
        List<String> actual = Arrays.stream(TriggerBindingInput.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("bindingKey",
                "providerKey",
                "schemaVersion",
                "config"), actual);
    }
}
