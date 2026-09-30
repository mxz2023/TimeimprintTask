package cn.net.mxz.timeimprint.task.domain.definition.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskDefinitionView record components for P02 refactor safety. */
class TaskDefinitionViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TaskDefinitionView.class.isRecord());
        List<String> actual = Arrays.stream(TaskDefinitionView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("definitionId",
                "scenarioKey",
                "scenarioSchemaVersion",
                "title",
                "description",
                "controlState",
                "controlGeneration",
                "revision",
                "participants",
                "triggerBindings",
                "scenarioConfig",
                "allowedCommands",
                "createdAt",
                "updatedAt",
                "pausedAt",
                "retiredAt"), actual);
    }
}
