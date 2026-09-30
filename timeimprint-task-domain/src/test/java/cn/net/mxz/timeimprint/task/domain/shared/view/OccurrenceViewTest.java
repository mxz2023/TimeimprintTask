package cn.net.mxz.timeimprint.task.domain.shared.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze OccurrenceView record components for P02 refactor safety. */
class OccurrenceViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(OccurrenceView.class.isRecord());
        List<String> actual = Arrays.stream(OccurrenceView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("occurrenceKey",
                "occurrenceAt",
                "dueAt"), actual);
    }
}
