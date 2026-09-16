package cn.net.mxz.timeimprint.task.service.extension.command.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskCommandHandlerKey record components for P02 refactor safety. */
class TaskCommandHandlerKeyTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TaskCommandHandlerKey.class.isRecord());
        List<String> actual = Arrays.stream(TaskCommandHandlerKey.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("scenarioKey",
                "scope",
                "commandKey",
                "commandSchemaVersion"), actual);
    }
}
