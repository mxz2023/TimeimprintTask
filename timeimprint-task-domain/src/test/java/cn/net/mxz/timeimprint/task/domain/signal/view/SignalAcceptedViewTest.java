package cn.net.mxz.timeimprint.task.domain.signal.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze SignalAcceptedView record components for P02 refactor safety. */
class SignalAcceptedViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(SignalAcceptedView.class.isRecord());
        List<String> actual = Arrays.stream(SignalAcceptedView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("signalId",
                "duplicated",
                "processStatus",
                "receivedAt"), actual);
    }
}
