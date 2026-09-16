package cn.net.mxz.timeimprint.task.service.extension.shared.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze DefinitionConfigValidationContext record components for P02 refactor safety. */
class DefinitionConfigValidationContextTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(DefinitionConfigValidationContext.class.isRecord());
        List<String> actual = Arrays.stream(DefinitionConfigValidationContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("scenarioKey",
                "scenarioSchemaVersion",
                "scenarioConfig"), actual);
    }
}
