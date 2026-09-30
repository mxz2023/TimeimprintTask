package cn.net.mxz.timeimprint.task.service.runtime.action.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Owner test for ActionExecutionSupport lease constants. */
class ActionExecutionSupportTest {

    @Test
    void freezesLeaseSafetyConstants() {
        assertEquals(30, ActionExecutionSupport.LEASE_SECONDS);
        assertEquals("action-worker", ActionExecutionSupport.LEASE_OWNER);
        assertTrue(ActionExecutionSupport.MAX_HANDLER_TIMEOUT_SECONDS < ActionExecutionSupport.LEASE_SECONDS);
        assertEquals(25, ActionExecutionSupport.MAX_HANDLER_TIMEOUT_SECONDS);
    }
}
