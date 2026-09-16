package cn.net.mxz.timeimprint.task.service.application.signal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze SignalRecord record components for P02 refactor safety. */
class SignalRecordTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(SignalRecord.class.isRecord());
        List<String> actual = Arrays.stream(SignalRecord.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("signalId",
                "tenantId",
                "definitionId",
                "triggerBindingId",
                "instanceId",
                "definitionControlGeneration",
                "providerKey",
                "signalKey",
                "schemaVersion",
                "occurredAt",
                "receivedAt",
                "payloadJson",
                "processStatus",
                "attemptCount",
                "maxAttempts",
                "nextAttemptAt",
                "resultCode",
                "processedAt",
                "parentSignalId",
                "redriveNo",
                "leaseOwner",
                "leaseUntil",
                "executionToken",
                "resultSummary"), actual);
    }
}
