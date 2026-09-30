package cn.net.mxz.timeimprint.task.domain.shared.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze AttemptSummary record components for P02 refactor safety. */
class AttemptSummaryTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(AttemptSummary.class.isRecord());
        List<String> actual = Arrays.stream(AttemptSummary.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("attemptNo",
                "startedAt",
                "finishedAt",
                "effectStarted",
                "outcome",
                "errorClass",
                "errorCode",
                "providerReference",
                "safeSummary"), actual);
    }
}
