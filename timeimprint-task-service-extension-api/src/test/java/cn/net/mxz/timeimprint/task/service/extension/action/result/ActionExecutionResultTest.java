package cn.net.mxz.timeimprint.task.service.extension.action.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionExecutionResult record components for P02 refactor safety. */
class ActionExecutionResultTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ActionExecutionResult.class.isRecord());
        List<String> actual = Arrays.stream(ActionExecutionResult.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("outcome",
                "outcomeCode",
                "safeSummary"), actual);
    }
}
