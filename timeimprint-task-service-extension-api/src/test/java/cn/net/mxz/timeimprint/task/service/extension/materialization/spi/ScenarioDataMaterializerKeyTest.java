package cn.net.mxz.timeimprint.task.service.extension.materialization.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ScenarioDataMaterializerKey record components for P02 refactor safety. */
class ScenarioDataMaterializerKeyTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ScenarioDataMaterializerKey.class.isRecord());
        List<String> actual = Arrays.stream(ScenarioDataMaterializerKey.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("scenarioKey",
                "mutationKey",
                "schemaVersion"), actual);
    }
}
