package cn.net.mxz.timeimprint.task.domain.instance.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskInstanceView record components for P02 refactor safety. */
class TaskInstanceViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TaskInstanceView.class.isRecord());
        List<String> actual = Arrays.stream(TaskInstanceView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("instanceId",
                "definitionId",
                "scenarioKey",
                "scenarioSchemaVersion",
                "lifecycleCategory",
                "scenarioState",
                "revision",
                "titleSnapshot",
                "descriptionSnapshot",
                "participants",
                "occurrenceAt",
                "dueAt",
                "allowedCommands",
                "scenarioProjection",
                "deliverySummary",
                "createdAt",
                "updatedAt",
                "terminalAt"), actual);
    }
}
