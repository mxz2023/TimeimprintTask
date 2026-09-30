package cn.net.mxz.timeimprint.task.service.capability.notification.notification.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze NotificationMaterializationPort method surface for P02 refactor safety. */
class NotificationMaterializationPortContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(NotificationMaterializationPort.class.isInterface());
        List<String> actual = Arrays.stream(NotificationMaterializationPort.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("insertNotification/8"), actual);
    }
}
