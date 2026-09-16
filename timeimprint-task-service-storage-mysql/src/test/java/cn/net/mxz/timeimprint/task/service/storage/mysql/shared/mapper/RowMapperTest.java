package cn.net.mxz.timeimprint.task.service.storage.mysql.shared.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze RowMapper public method surface for P02 refactor safety. */
class RowMapperTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(RowMapper.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("toAction/1",
                "toBinding/1",
                "toDefinition/1",
                "toInstance/1",
                "toParticipant/1",
                "toSignal/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
