package cn.net.mxz.timeimprint.task.service.extension.command.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze CommandExecutionContext record components for P02 refactor safety. */
class CommandExecutionContextTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(CommandExecutionContext.class.isRecord());
        List<String> actual = Arrays.stream(CommandExecutionContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("scope",
                "commandKey",
                "commandSchemaVersion",
                "requestId",
                "definitionSnapshot",
                "instanceSnapshot",
                "commandPayload",
                "nowUtc",
                "actionJobs"), actual);
    }
}
