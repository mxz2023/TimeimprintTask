package cn.net.mxz.timeimprint.task.domain.shared.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TriggerBindingView record components for P02 refactor safety. */
class TriggerBindingViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TriggerBindingView.class.isRecord());
        List<String> actual = Arrays.stream(TriggerBindingView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("bindingId",
                "bindingKey",
                "providerKey",
                "schemaVersion",
                "config",
                "bindingState",
                "scheduleGeneration",
                "nextFireAt",
                "exhausted",
                "revision"), actual);
    }
}
