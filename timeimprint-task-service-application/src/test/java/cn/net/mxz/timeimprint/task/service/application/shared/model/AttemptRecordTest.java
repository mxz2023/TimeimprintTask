package cn.net.mxz.timeimprint.task.service.application.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze AttemptRecord record components for P02 refactor safety. */
class AttemptRecordTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(AttemptRecord.class.isRecord());
        List<String> actual = Arrays.stream(AttemptRecord.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("attemptNo",
                "startedAt",
                "finishedAt",
                "effectStartedAt",
                "outcome",
                "errorClass",
                "errorCode",
                "providerReference",
                "safeSummary"), actual);
    }
}
