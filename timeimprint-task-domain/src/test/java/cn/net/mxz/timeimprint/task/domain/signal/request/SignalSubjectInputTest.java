package cn.net.mxz.timeimprint.task.domain.signal.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze SignalSubjectInput record components for P02 refactor safety. */
class SignalSubjectInputTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(SignalSubjectInput.class.isRecord());
        List<String> actual = Arrays.stream(SignalSubjectInput.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("definitionId",
                "instanceId"), actual);
    }
}
