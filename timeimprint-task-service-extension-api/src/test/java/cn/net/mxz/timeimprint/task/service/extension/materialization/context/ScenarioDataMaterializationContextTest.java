package cn.net.mxz.timeimprint.task.service.extension.materialization.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ScenarioDataMaterializationContext record components for P02 refactor safety. */
class ScenarioDataMaterializationContextTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ScenarioDataMaterializationContext.class.isRecord());
        List<String> actual = Arrays.stream(ScenarioDataMaterializationContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("definitionSnapshot",
                "instanceSnapshot",
                "transitionId",
                "mutation"), actual);
    }
}
