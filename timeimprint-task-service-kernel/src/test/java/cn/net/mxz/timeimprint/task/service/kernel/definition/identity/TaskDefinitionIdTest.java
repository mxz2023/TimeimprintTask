package cn.net.mxz.timeimprint.task.service.kernel.definition.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskDefinitionId record components for P02 refactor safety. */
class TaskDefinitionIdTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TaskDefinitionId.class.isRecord());
        List<String> actual = Arrays.stream(TaskDefinitionId.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("value"), actual);
    }
}
