package cn.net.mxz.timeimprint.task.service.application.shared.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze TransactionBoundary method surface for P02 refactor safety. */
class TransactionBoundaryContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(TransactionBoundary.class.isInterface());
        List<String> actual = Arrays.stream(TransactionBoundary.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("execute/1"), actual);
    }
}
