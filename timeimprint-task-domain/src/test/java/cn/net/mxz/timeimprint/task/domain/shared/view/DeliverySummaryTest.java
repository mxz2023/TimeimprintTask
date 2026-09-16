package cn.net.mxz.timeimprint.task.domain.shared.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze DeliverySummary record components for P02 refactor safety. */
class DeliverySummaryTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(DeliverySummary.class.isRecord());
        List<String> actual = Arrays.stream(DeliverySummary.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("deliveryState",
                "totalCount",
                "readyCount",
                "runningCount",
                "retryWaitCount",
                "succeededCount",
                "deadCount",
                "cancelledCount",
                "expiredCount",
                "unknownCount",
                "inboxCount",
                "unreadInboxCount",
                "updatedAt"), actual);
    }
}
