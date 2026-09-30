package cn.net.mxz.timeimprint.task.service.extension.shared.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze SignalProcessContext record components for P02 refactor safety. */
class SignalProcessContextTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(SignalProcessContext.class.isRecord());
        List<String> actual = Arrays.stream(SignalProcessContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("definitionSnapshot",
                "instanceSnapshot",
                "signalId",
                "providerKey",
                "signalKey",
                "schemaVersion",
                "payload"), actual);
    }
}
