package cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.mapper;

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
 * Owner mapping test for TriggerBindingMapper.
 * Freezes Mapper method surface and XML namespace; row-level MySQL coverage remains in boot-loader ITs.
 */
class TriggerBindingMapperMysqlIT {

    @Test
    void freezesMapperMethodsAndXmlNamespace() throws Exception {
        List<String> actual = Arrays.stream(TriggerBindingMapper.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("insert/1",
                "selectByDefinitionId/1",
                "selectById/1",
                "selectByIdForUpdate/1",
                "selectDueTriggerIds/3",
                "updateConfigAndSchedule/10",
                "updateCursor/7"), actual);

        String resource = "/mapper/TriggerBindingMapper.xml";
        try (InputStream in = TriggerBindingMapper.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing classpath resource " + resource);
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("namespace=\"cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.mapper.TriggerBindingMapper\""), xml);
            for (String sig : actual) {
                String method = sig.substring(0, sig.indexOf('/'));
                assertTrue(xml.contains("id=\"" + method + "\""), () -> "XML missing statement id for " + method);
            }
        }
    }
}
