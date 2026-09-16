package cn.net.mxz.timeimprint.task.domain.shared.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze PreviewResult record components for P02 refactor safety. */
class PreviewResultTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(PreviewResult.class.isRecord());
        List<String> actual = Arrays.stream(PreviewResult.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("scenarioKey",
                "scenarioSchemaVersion",
                "normalizedTriggerBindings",
                "normalizedScenarioConfig",
                "occurrences"), actual);
    }
}
