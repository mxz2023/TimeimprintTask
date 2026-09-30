package cn.net.mxz.timeimprint.task.service.application.signal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze SignalAcceptResult record components for P02 refactor safety. */
class SignalAcceptResultTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(SignalAcceptResult.class.isRecord());
        List<String> actual = Arrays.stream(SignalAcceptResult.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("signalId",
                "duplicated",
                "processStatus",
                "receivedAt"), actual);
    }
}
