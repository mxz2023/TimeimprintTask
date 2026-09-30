package cn.net.mxz.timeimprint.task.service.extension.shared.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test for HandlerResult sealed hierarchy. */
class HandlerResultContractTest {

    @Test
    void freezesSealedPermits() {
        assertTrue(HandlerResult.class.isSealed());
        assertTrue(HandlerResult.class.isInterface());
        List<String> permits = Arrays.stream(HandlerResult.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of("Applied", "NoChange", "Rejected"), permits);
        assertTrue(HandlerResult.Applied.class.isRecord());
        assertTrue(HandlerResult.NoChange.class.isRecord());
        assertTrue(HandlerResult.Rejected.class.isRecord());
    }
}
