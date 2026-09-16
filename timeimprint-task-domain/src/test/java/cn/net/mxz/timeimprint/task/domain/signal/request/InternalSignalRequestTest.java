package cn.net.mxz.timeimprint.task.domain.signal.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InternalSignalRequest record components for P02 refactor safety. */
class InternalSignalRequestTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(InternalSignalRequest.class.isRecord());
        List<String> actual = Arrays.stream(InternalSignalRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("requestId",
                "signalKey",
                "schemaVersion",
                "occurredAt",
                "subject",
                "payload"), actual);
    }
}
