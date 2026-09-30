package cn.net.mxz.timeimprint.task.service.application.action.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionJobRecord record components for P02 refactor safety. */
class ActionJobRecordTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ActionJobRecord.class.isRecord());
        List<String> actual = Arrays.stream(ActionJobRecord.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("actionJobId",
                "tenantId",
                "definitionId",
                "instanceId",
                "transitionId",
                "definitionControlGeneration",
                "handlerKey",
                "actionKey",
                "executionMode",
                "schemaVersion",
                "targetType",
                "targetId",
                "payloadJson",
                "availableAt",
                "expiresAt",
                "status",
                "attemptCount",
                "maxAttempts",
                "nextAttemptAt",
                "leaseOwner",
                "leaseUntil",
                "outcomeCode",
                "outcomeSummary",
                "completedAt",
                "parentActionJobId",
                "redriveNo"), actual);
    }
}
