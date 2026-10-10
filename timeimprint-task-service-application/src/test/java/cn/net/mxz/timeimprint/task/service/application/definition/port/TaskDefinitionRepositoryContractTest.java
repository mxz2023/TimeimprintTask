package cn.net.mxz.timeimprint.task.service.application.definition.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze TaskDefinitionRepository method surface for P02 refactor safety. */
class TaskDefinitionRepositoryContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(TaskDefinitionRepository.class.isInterface());
        List<String> actual = Arrays.stream(TaskDefinitionRepository.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("findById/1",
                "findByIdForUpdate/1",
                "list/7"), actual);
    }
}
