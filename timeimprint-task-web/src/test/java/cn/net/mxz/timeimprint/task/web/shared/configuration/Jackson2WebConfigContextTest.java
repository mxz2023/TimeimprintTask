package cn.net.mxz.timeimprint.task.web.shared.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;

/** Owner context test for Jackson2WebConfig. */
class Jackson2WebConfigContextTest {

    @Test
    void isSpringConfigurationAndFreezesPublicMethods() {
        assertTrue(
                Jackson2WebConfig.class.isAnnotationPresent(Configuration.class)
                        || Arrays.stream(Jackson2WebConfig.class.getAnnotations())
                                .anyMatch(a -> a.annotationType().getSimpleName().contains("Configuration")));
        List<String> actual = Arrays.stream(Jackson2WebConfig.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("extendMessageConverters/1"), actual);
    }
}
