package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;
import org.junit.jupiter.api.Test;

class FeishuImNotificationHandlerTest {

    private final FeishuImNotificationHandler handler = new FeishuImNotificationHandler();

    @Test
    void registersExternalHandlerKey() {
        assertEquals("feishu_im_notification", handler.registrationKey().handlerKey());
        assertEquals(1, handler.registrationKey().actionSchemaVersion());
        assertEquals(ActionExecutionMode.EXTERNAL, handler.executionMode());
    }

    @Test
    void t02StubReturnsPermanentFailure() {
        var result = handler.execute(null);
        assertEquals(ActionHandlerOutcome.PERMANENT_FAILURE, result.outcome());
        assertEquals("FEISHU_NOT_IMPLEMENTED", result.outcomeCode());
    }
}
