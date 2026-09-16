package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Owner contract test for marker payload SPI. */
class ScenarioMutationPayloadContractTest {

    @Test
    void isPublicMarkerInterface() {
        assertTrue(ScenarioMutationPayload.class.isInterface());
        assertEqualsZeroDeclaredMethods();
    }

    private static void assertEqualsZeroDeclaredMethods() {
        long count = java.util.Arrays.stream(ScenarioMutationPayload.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(0, count);
    }
}
