package cn.net.mxz.timeimprint.task.service.application.action.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze ActionJobRepository method surface for P02 refactor safety. */
class ActionJobRepositoryContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(ActionJobRepository.class.isInterface());
        List<String> actual = Arrays.stream(ActionJobRepository.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("findIdForUpdate/1"), actual);
    }
}
