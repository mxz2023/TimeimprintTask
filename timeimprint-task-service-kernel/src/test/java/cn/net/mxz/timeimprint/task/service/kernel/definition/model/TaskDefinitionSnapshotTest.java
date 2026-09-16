package cn.net.mxz.timeimprint.task.service.kernel.definition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskDefinitionSnapshot record components for P02 refactor safety. */
class TaskDefinitionSnapshotTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TaskDefinitionSnapshot.class.isRecord());
        List<String> actual = Arrays.stream(TaskDefinitionSnapshot.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("definitionId",
                "tenantId",
                "scenarioKey",
                "scenarioSchemaVersion",
                "title",
                "description",
                "scenarioConfigJson",
                "controlState",
                "controlGeneration",
                "revision",
                "createdAt",
                "updatedAt"), actual);
    }
}
