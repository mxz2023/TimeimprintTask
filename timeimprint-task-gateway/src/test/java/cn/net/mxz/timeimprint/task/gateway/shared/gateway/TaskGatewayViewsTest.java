package cn.net.mxz.timeimprint.task.gateway.shared.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Owner test for TaskGatewayViews deliveryState derivation (A42). */
class TaskGatewayViewsTest {

    @Test
    void deriveDeliveryStateMatchesPriorityContract() {
        assertEquals("NOT_SCHEDULED", TaskGatewayViews.deriveDeliveryState(0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals("UNKNOWN", TaskGatewayViews.deriveDeliveryState(2, 0, 0, 0, 1, 0, 0, 0, 1));
        assertEquals("IN_PROGRESS", TaskGatewayViews.deriveDeliveryState(2, 1, 0, 0, 0, 0, 0, 0, 0));
        assertEquals("DELIVERED", TaskGatewayViews.deriveDeliveryState(2, 0, 0, 0, 2, 0, 0, 0, 0));
        assertEquals("PARTIALLY_DELIVERED", TaskGatewayViews.deriveDeliveryState(2, 0, 0, 0, 1, 1, 0, 0, 0));
        assertEquals("FAILED", TaskGatewayViews.deriveDeliveryState(2, 0, 0, 0, 0, 2, 0, 0, 0));
        assertEquals("EXPIRED", TaskGatewayViews.deriveDeliveryState(1, 0, 0, 0, 0, 0, 0, 1, 0));
        assertEquals("CANCELLED", TaskGatewayViews.deriveDeliveryState(1, 0, 0, 0, 0, 0, 1, 0, 0));
    }
}
