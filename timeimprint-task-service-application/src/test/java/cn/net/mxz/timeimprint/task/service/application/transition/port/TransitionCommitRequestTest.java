package cn.net.mxz.timeimprint.task.service.application.transition.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TransitionCommitRequest record components for P02 refactor safety. */
class TransitionCommitRequestTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TransitionCommitRequest.class.isRecord());
        List<String> actual = Arrays.stream(TransitionCommitRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("plan",
                "definitionId",
                "instanceId",
                "sourceType",
                "sourceKey",
                "instanceSnapshotJson"), actual);
    }
}
