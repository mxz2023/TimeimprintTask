package cn.net.mxz.timeimprint.task.domain.definition.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze DefinitionCommandRequest record components for P02 refactor safety. */
class DefinitionCommandRequestTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(DefinitionCommandRequest.class.isRecord());
        List<String> actual = Arrays.stream(DefinitionCommandRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("requestId",
                "expectedRevision",
                "commandSchemaVersion",
                "payload"), actual);
    }
}
