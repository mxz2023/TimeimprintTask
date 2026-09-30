package cn.net.mxz.timeimprint.task.service.kernel.instance.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskInstanceSnapshot record components for P02 refactor safety. */
class TaskInstanceSnapshotTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TaskInstanceSnapshot.class.isRecord());
        List<String> actual = Arrays.stream(TaskInstanceSnapshot.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("instanceId",
                "definitionId",
                "triggerBindingId",
                "scheduleGeneration",
                "definitionControlGeneration",
                "occurrenceKey",
                "occurrenceAt",
                "dueAt",
                "lifecycleCategory",
                "scenarioState",
                "scenarioSchemaVersion",
                "scenarioSnapshotJson",
                "titleSnapshot",
                "descriptionSnapshot",
                "revision",
                "terminalAt"), actual);
    }
}
