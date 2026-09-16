package cn.net.mxz.timeimprint.task.service.application.inbox.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InboxRecord record components for P02 refactor safety. */
class InboxRecordTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(InboxRecord.class.isRecord());
        List<String> actual = Arrays.stream(InboxRecord.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("inboxId",
                "tenantId",
                "notificationId",
                "actionJobId",
                "definitionId",
                "instanceId",
                "scenarioKey",
                "purpose",
                "title",
                "body",
                "recipientType",
                "recipientId",
                "readAt",
                "createdAt"), actual);
    }
}
