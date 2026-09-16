package cn.net.mxz.timeimprint.task.service.storage.mysql.signal.mapper;

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
 * Owner mapping test for TaskSignalMapper.
 * Freezes Mapper method surface and XML namespace; row-level MySQL coverage remains in boot-loader ITs.
 */
class TaskSignalMapperMysqlIT {

    @Test
    void freezesMapperMethodsAndXmlNamespace() throws Exception {
        List<String> actual = Arrays.stream(TaskSignalMapper.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("claimReadySignals/6",
                "claimSignal/5",
                "completeSignal/7",
                "countByParent/1",
                "ignoreByBindingGeneration/6",
                "insert/1",
                "recoverToDead/6",
                "recoverToRetryWait/4",
                "selectById/1",
                "selectByIdForUpdate/1",
                "selectBySourceKey/3",
                "selectExpiredRunningIds/1",
                "selectReadyDueIds/2"), actual);

        String resource = "/mapper/TaskSignalMapper.xml";
        try (InputStream in = TaskSignalMapper.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing classpath resource " + resource);
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("namespace=\"cn.net.mxz.timeimprint.task.service.storage.mysql.signal.mapper.TaskSignalMapper\""), xml);
            for (String sig : actual) {
                String method = sig.substring(0, sig.indexOf('/'));
                assertTrue(xml.contains("id=\"" + method + "\""), () -> "XML missing statement id for " + method);
            }
        }
    }
}
