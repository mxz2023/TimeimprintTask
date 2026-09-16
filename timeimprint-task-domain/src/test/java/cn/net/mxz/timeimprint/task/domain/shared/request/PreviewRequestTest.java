package cn.net.mxz.timeimprint.task.domain.shared.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze PreviewRequest record components for P02 refactor safety. */
class PreviewRequestTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(PreviewRequest.class.isRecord());
        List<String> actual = Arrays.stream(PreviewRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("scenarioKey",
                "scenarioSchemaVersion",
                "triggerBindings",
                "scenarioConfig",
                "after",
                "limit"), actual);
    }
}
