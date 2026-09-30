package cn.net.mxz.timeimprint.task.service.kernel.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze PlannedSignalIntent record components for P02 refactor safety. */
class PlannedSignalIntentTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(PlannedSignalIntent.class.isRecord());
        List<String> actual = Arrays.stream(PlannedSignalIntent.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("providerKey",
                "signalKey",
                "schemaVersion",
                "definitionId",
                "instanceId",
                "occurredAt",
                "payload"), actual);
    }
}
