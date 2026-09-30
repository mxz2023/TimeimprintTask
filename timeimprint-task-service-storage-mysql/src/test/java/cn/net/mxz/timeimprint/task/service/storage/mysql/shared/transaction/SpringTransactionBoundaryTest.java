package cn.net.mxz.timeimprint.task.service.storage.mysql.shared.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze SpringTransactionBoundary public method surface for P02 refactor safety. */
class SpringTransactionBoundaryTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(SpringTransactionBoundary.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("attemptCount/0",
                "execute/1",
                "isRetryableLockFailure/1",
                "resetAttemptCount/0"), actual);
        assertFalse(actual.isEmpty());
    }
}
