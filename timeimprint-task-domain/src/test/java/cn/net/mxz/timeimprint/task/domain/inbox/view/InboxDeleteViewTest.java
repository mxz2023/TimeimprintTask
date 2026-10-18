package cn.net.mxz.timeimprint.task.domain.inbox.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test for InboxDeleteView. */
class InboxDeleteViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(InboxDeleteView.class.isRecord());
        List<String> actual = Arrays.stream(InboxDeleteView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("inboxId", "deletedAt"), actual);
        InboxDeleteView view = new InboxDeleteView("8", "2026-10-18T10:54:00Z");
        assertEquals("8", view.inboxId());
        assertEquals("2026-10-18T10:54:00Z", view.deletedAt());
    }
}
