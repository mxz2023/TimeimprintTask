package cn.net.mxz.timeimprint.task.domain.shared.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze CommandResultView record components for P02 refactor safety. */
class CommandResultViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(CommandResultView.class.isRecord());
        List<String> actual = Arrays.stream(CommandResultView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("resourceType",
                "resourceId",
                "resourceRevision",
                "changed",
                "resourceSnapshot",
                "scenarioResult"), actual);
    }
}
