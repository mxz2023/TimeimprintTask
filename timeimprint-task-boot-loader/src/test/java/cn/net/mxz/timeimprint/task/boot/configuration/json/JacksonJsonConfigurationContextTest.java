package cn.net.mxz.timeimprint.task.boot.configuration.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;

/** Owner context test for JacksonJsonConfiguration. */
class JacksonJsonConfigurationContextTest {

    @Test
    void isSpringConfigurationExposingCustomizerBeanFactory() {
        assertTrue(JacksonJsonConfiguration.class.isAnnotationPresent(Configuration.class));
        List<String> actual = Arrays.stream(JacksonJsonConfiguration.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("timeImprintJacksonCompatibility/0"), actual);
    }
}
