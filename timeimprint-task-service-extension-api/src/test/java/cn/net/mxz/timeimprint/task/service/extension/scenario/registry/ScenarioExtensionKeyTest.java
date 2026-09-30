package cn.net.mxz.timeimprint.task.service.extension.scenario.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ScenarioExtensionKey record components for P02 refactor safety. */
class ScenarioExtensionKeyTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ScenarioExtensionKey.class.isRecord());
        List<String> actual = Arrays.stream(ScenarioExtensionKey.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("scenarioKey",
                "contractVersion"), actual);
    }
}
