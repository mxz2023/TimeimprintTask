package cn.net.mxz.timeimprint.task.service.extension.trigger.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TriggerEvaluationContext record components for P02 refactor safety. */
class TriggerEvaluationContextTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TriggerEvaluationContext.class.isRecord());
        List<String> actual = Arrays.stream(TriggerEvaluationContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("definitionSnapshot",
                "bindingKey",
                "configSchemaVersion",
                "bindingConfig"), actual);
    }
}
