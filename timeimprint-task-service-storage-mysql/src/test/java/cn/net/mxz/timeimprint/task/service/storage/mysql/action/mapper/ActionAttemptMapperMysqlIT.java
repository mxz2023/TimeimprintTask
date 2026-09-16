package cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Owner mapping test for ActionAttemptMapper.
 * Freezes Mapper method surface and XML namespace; row-level MySQL coverage remains in boot-loader ITs.
 */
class ActionAttemptMapperMysqlIT {

    @Test
    void freezesMapperMethodsAndXmlNamespace() throws Exception {
        List<String> actual = Arrays.stream(ActionAttemptMapper.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("completeAttempt/7",
                "insert/1",
                "markEffectStarted/3",
                "selectByActionJobId/1",
                "selectMaxAttemptNo/1"), actual);

        String resource = "/mapper/ActionAttemptMapper.xml";
        try (InputStream in = ActionAttemptMapper.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing classpath resource " + resource);
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("namespace=\"cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper.ActionAttemptMapper\""), xml);
            for (String sig : actual) {
                String method = sig.substring(0, sig.indexOf('/'));
                assertTrue(xml.contains("id=\"" + method + "\""), () -> "XML missing statement id for " + method);
            }
        }
    }
}
