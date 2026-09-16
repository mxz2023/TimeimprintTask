package cn.net.mxz.timeimprint.task.domain.instance.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InstanceCommandRequest record components for P02 refactor safety. */
class InstanceCommandRequestTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(InstanceCommandRequest.class.isRecord());
        List<String> actual = Arrays.stream(InstanceCommandRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("requestId",
                "expectedRevision",
                "commandSchemaVersion",
                "payload"), actual);
    }
}
