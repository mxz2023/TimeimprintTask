package cn.net.mxz.timeimprint.task.boot.fixture;

import cn.net.mxz.timeimprint.task.service.extension.action.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.ActionExecutionResult;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.spi.ActionHandler;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * T07/A08 test fixture: EXTERNAL ActionHandler "webhook_action".
 * Supports invoke counting and a hold gate so ITs can pause between effectStartedAt and the call.
 */
@Component
public class WebhookActionFixture implements ActionHandler {

    public static final String HANDLER_KEY = "webhook_action";

    public static final AtomicInteger INVOKE_COUNT = new AtomicInteger();
    private static final AtomicInteger FAIL_REMAINING = new AtomicInteger();
    private static final AtomicReference<CountDownLatch> HOLD = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> ENTERED = new AtomicReference<>();

    public static void reset() {
        INVOKE_COUNT.set(0);
        FAIL_REMAINING.set(0);
        HOLD.set(null);
        ENTERED.set(null);
    }

    /** Next {@code n} execute() calls return RETRYABLE_FAILURE, then SUCCEEDED. */
    public static void failNext(int n) {
        FAIL_REMAINING.set(n);
    }

    /** Next execute() blocks after entering until {@link #releaseHold()}. */
    public static void armHold() {
        HOLD.set(new CountDownLatch(1));
        ENTERED.set(new CountDownLatch(1));
    }

    public static boolean awaitEntered(long timeoutMs) throws InterruptedException {
        CountDownLatch entered = ENTERED.get();
        return entered != null && entered.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public static void releaseHold() {
        CountDownLatch hold = HOLD.get();
        if (hold != null) {
            hold.countDown();
        }
    }

    @Override
    public ActionHandlerKey registrationKey() {
        return new ActionHandlerKey(HANDLER_KEY, 1);
    }

    @Override
    public ActionExecutionMode executionMode() {
        return ActionExecutionMode.EXTERNAL;
    }

    @Override
    public int timeoutSeconds() {
        // Must be <= LEASE_SECONDS - ACTION_LEASE_SAFETY_SECONDS (30-5).
        return 20;
    }

    @Override
    public Set<Integer> supportedSchemaVersions() {
        return Set.of(1);
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        CountDownLatch entered = ENTERED.get();
        if (entered != null) {
            entered.countDown();
        }
        CountDownLatch hold = HOLD.get();
        if (hold != null) {
            try {
                if (!hold.await(15, TimeUnit.SECONDS)) {
                    return new ActionExecutionResult(
                            ActionHandlerOutcome.UNKNOWN, "HOLD_TIMEOUT", "fixture hold timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ActionExecutionResult(
                        ActionHandlerOutcome.UNKNOWN, "HOLD_INTERRUPTED", "fixture hold interrupted");
            }
        }
        INVOKE_COUNT.incrementAndGet();
        if (FAIL_REMAINING.getAndDecrement() > 0) {
            return new ActionExecutionResult(
                    ActionHandlerOutcome.RETRYABLE_FAILURE, "FIXTURE_RETRY", "webhook fixture retryable");
        }
        return new ActionExecutionResult(ActionHandlerOutcome.SUCCEEDED, "FIXTURE_OK", "webhook fixture success");
    }
}
