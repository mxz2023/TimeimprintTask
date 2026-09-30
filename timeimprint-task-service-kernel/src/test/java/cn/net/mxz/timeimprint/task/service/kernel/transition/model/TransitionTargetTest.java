package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TransitionTarget record components for P02 refactor safety. */
class TransitionTargetTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TransitionTarget.class.isRecord());
        List<String> actual = Arrays.stream(TransitionTarget.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("resourceType",
                "resourceId",
                "fromRevision"), actual);
    }
}
