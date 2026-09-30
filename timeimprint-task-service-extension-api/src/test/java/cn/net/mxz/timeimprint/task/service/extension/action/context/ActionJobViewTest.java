package cn.net.mxz.timeimprint.task.service.extension.action.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionJobView record components for P02 refactor safety. */
class ActionJobViewTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ActionJobView.class.isRecord());
        List<String> actual = Arrays.stream(ActionJobView.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("actionJobId",
                "actionKey",
                "status",
                "availableAt",
                "expiresAt",
                "payloadJson"), actual);
    }
}
