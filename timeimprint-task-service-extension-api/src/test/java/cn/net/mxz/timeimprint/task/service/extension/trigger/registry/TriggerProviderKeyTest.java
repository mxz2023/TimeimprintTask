package cn.net.mxz.timeimprint.task.service.extension.trigger.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TriggerProviderKey record components for P02 refactor safety. */
class TriggerProviderKeyTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TriggerProviderKey.class.isRecord());
        List<String> actual = Arrays.stream(TriggerProviderKey.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("providerKey",
                "contractVersion"), actual);
    }
}
