package cn.net.mxz.timeimprint.task.service.kernel.participant.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ParticipantChange record components for P02 refactor safety. */
class ParticipantChangeTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ParticipantChange.class.isRecord());
        List<String> actual = Arrays.stream(ParticipantChange.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("changeKind",
                "principalType",
                "principalId",
                "roleCode",
                "definitionLevel"), actual);
    }
}
