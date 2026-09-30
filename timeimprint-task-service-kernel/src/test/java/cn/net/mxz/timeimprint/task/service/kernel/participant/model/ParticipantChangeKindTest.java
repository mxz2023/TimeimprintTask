package cn.net.mxz.timeimprint.task.service.kernel.participant.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ParticipantChangeKind enum constants for P02 refactor safety. */
class ParticipantChangeKindTest {

    @Test
    void freezesEnumConstants() {
        String[] actual = Arrays.stream(ParticipantChangeKind.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] {"ADD",
                "REMOVE"}, actual);
        assertEquals(2, ParticipantChangeKind.values().length);
    }
}
