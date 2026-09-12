package cn.net.mxz.timeimprint.task.boot.fixture;

import cn.net.mxz.timeimprint.task.service.extension.action.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionExecutionResult;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.spi.ActionHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * T07 test fixture: an ActionHandler registered as "webhook_action" that simulates a
 * webhook delivery by returning SUCCEEDED immediately. Proves that a new action handler
 * type can be registered via the stable ActionHandler SPI without modifying kernel
 * production sources or platform public DDL.
 *
 * This class lives in boot-loader test sources only.
 */
@Component
public class WebhookActionFixture implements ActionHandler {

    public static final String HANDLER_KEY = "webhook_action";

    @Override
    public ActionHandlerKey registrationKey() {
        return new ActionHandlerKey(HANDLER_KEY, 1);
    }

    @Override
    public ActionExecutionMode executionMode() {
        // EXTERNAL so the fixture does not try to make real network calls;
        // a real WebhookAction handler would be EXTERNAL and complete via callback.
        return ActionExecutionMode.EXTERNAL;
    }

    @Override
    public int timeoutSeconds() {
        return 30;
    }

    @Override
    public Set<Integer> supportedSchemaVersions() {
        return Set.of(1);
    }

    /**
     * Simulates a successful webhook delivery without making any real HTTP calls.
     * A production implementation would POST to the configured URL and handle retries.
     */
    @Override
    public MxzActionExecutionResult execute(MxzActionExecutionContext context) {
        return new MxzActionExecutionResult(ActionHandlerOutcome.SUCCEEDED, "FIXTURE_OK", "webhook fixture success");
    }
}
