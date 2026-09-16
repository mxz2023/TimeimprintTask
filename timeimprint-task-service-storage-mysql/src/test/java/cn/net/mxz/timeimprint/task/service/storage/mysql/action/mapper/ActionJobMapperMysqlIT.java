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
 * Owner mapping test for ActionJobMapper.
 * Freezes Mapper method surface and XML namespace; row-level MySQL coverage remains in boot-loader ITs.
 */
class ActionJobMapperMysqlIT {

    @Test
    void freezesMapperMethodsAndXmlNamespace() throws Exception {
        List<String> actual = Arrays.stream(ActionJobMapper.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("cancelByControlGeneration/5",
                "cancelReadyByDefinition/4",
                "cancelReadyById/4",
                "cancelReadyByInstance/4",
                "cancelReadyByInstanceExceptTransition/5",
                "cancelRunning/5",
                "claimAction/5",
                "claimReadyActions/6",
                "completeAction/7",
                "completeToRetryWait/6",
                "countByParent/1",
                "insert/1",
                "markExpiredIfDue/5",
                "recoverToRetryWait/4",
                "recoverToTerminal/7",
                "refundPolicyBlocked/4",
                "selectById/1",
                "selectByIdForUpdate/1",
                "selectByInstanceId/1",
                "selectExpiredRunningIds/1",
                "selectList/6",
                "selectReadyDueIds/2",
                "selectReadyDueIdsNewestFirst/2"), actual);

        String resource = "/mapper/ActionJobMapper.xml";
        try (InputStream in = ActionJobMapper.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing classpath resource " + resource);
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("namespace=\"cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper.ActionJobMapper\""), xml);
            for (String sig : actual) {
                String method = sig.substring(0, sig.indexOf('/'));
                assertTrue(xml.contains("id=\"" + method + "\""), () -> "XML missing statement id for " + method);
            }
        }
    }
}
