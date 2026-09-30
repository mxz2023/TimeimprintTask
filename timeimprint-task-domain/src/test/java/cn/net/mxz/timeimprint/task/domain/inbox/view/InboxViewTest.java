package cn.net.mxz.timeimprint.task.domain.inbox.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InboxView record components for P02 refactor safety. */
class InboxViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(InboxView.class.isRecord());
        List<String> actual = Arrays.stream(InboxView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("inboxId",
                "notificationId",
                "actionJobId",
                "definitionId",
                "instanceId",
                "scenarioKey",
                "purpose",
                "title",
                "body",
                "readAt",
                "createdAt"), actual);
    }
}
