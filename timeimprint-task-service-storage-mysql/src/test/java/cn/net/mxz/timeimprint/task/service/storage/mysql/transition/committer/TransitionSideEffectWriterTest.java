package cn.net.mxz.timeimprint.task.service.storage.mysql.transition.committer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test for TransitionSideEffectWriter. */
class TransitionSideEffectWriterTest {

    @Test
    void freezesPackageWriteEntry() {
        List<String> actual = Arrays.stream(TransitionSideEffectWriter.class.getDeclaredMethods())
                .filter(m -> !Modifier.isPrivate(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("write/9"), actual);
    }
}
