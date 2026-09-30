package cn.net.mxz.timeimprint.task.domain.diagnostic.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionJobDiagnosticView record components for P02 refactor safety. */
class ActionJobDiagnosticViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ActionJobDiagnosticView.class.isRecord());
        List<String> actual = Arrays.stream(ActionJobDiagnosticView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("actionJobId",
                "definitionId",
                "instanceId",
                "transitionId",
                "handlerKey",
                "executionMode",
                "schemaVersion",
                "targetType",
                "storedStatus",
                "effectiveStatus",
                "attemptCount",
                "maxAttempts",
                "availableAt",
                "expiresAt",
                "nextAttemptAt",
                "leaseOwner",
                "leaseUntil",
                "outcomeCode",
                "outcomeSummary",
                "completedAt",
                "parentActionJobId",
                "redriveNo",
                "attempts"), actual);
    }
}
