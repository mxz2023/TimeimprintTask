package cn.net.mxz.timeimprint.task.domain.inbox.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test for InboxClearReadView. */
class InboxClearReadViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(InboxClearReadView.class.isRecord());
        List<String> actual = Arrays.stream(InboxClearReadView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("clearedCount"), actual);
        assertEquals(2, new InboxClearReadView(2).clearedCount());
    }
}
