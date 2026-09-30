package cn.net.mxz.timeimprint.task.service.application.signal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze SignalAcceptCommand record components for P02 refactor safety. */
class SignalAcceptCommandTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(SignalAcceptCommand.class.isRecord());
        List<String> actual = Arrays.stream(SignalAcceptCommand.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("tenantId",
                "actorId",
                "requestId",
                "providerKey",
                "signalKey",
                "schemaVersion",
                "occurredAt",
                "definitionId",
                "instanceId",
                "payloadJson",
                "now"), actual);
    }
}
