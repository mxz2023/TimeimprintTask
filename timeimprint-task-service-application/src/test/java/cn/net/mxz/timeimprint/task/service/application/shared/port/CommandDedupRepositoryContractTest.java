package cn.net.mxz.timeimprint.task.service.application.shared.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze CommandDedupRepository method surface for P02 refactor safety. */
class CommandDedupRepositoryContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(CommandDedupRepository.class.isInterface());
        List<String> actual = Arrays.stream(CommandDedupRepository.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("complete/9",
                "find/4",
                "findCompletedResponseJson/4",
                "tryBegin/5"), actual);
    }
}
