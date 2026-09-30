package cn.net.mxz.timeimprint.task.service.extension.materialization.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze ScenarioDataMaterializer method surface for P02 refactor safety. */
class ScenarioDataMaterializerContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(ScenarioDataMaterializer.class.isInterface());
        List<String> actual = Arrays.stream(ScenarioDataMaterializer.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("materialize/1",
                "registrationKey/0"), actual);
    }
}
