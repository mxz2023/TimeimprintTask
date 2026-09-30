package cn.net.mxz.timeimprint.task.boot.fixture;

import cn.net.mxz.timeimprint.task.service.extension.trigger.context.TriggerEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.trigger.registry.TriggerProviderKey;
import cn.net.mxz.timeimprint.task.service.kernel.shared.model.PlannedSignalIntent;
import cn.net.mxz.timeimprint.task.service.extension.trigger.spi.TriggerProvider;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * T07 test fixture: a TriggerProvider registered as "event_trigger" that always produces
 * no signals. Proves that a new trigger type can be registered via the stable TriggerProvider
 * SPI without modifying kernel production sources or platform public DDL.
 *
 * This class lives in boot-loader test sources only.
 */
@Component
public class EventTriggerFixture implements TriggerProvider {

    public static final String PROVIDER_KEY = "event_trigger";

    @Override
    public TriggerProviderKey registrationKey() {
        return new TriggerProviderKey(PROVIDER_KEY, 1);
    }

    @Override
    public Set<Integer> supportedSchemaVersions() {
        return Set.of(1);
    }

    /**
     * This fixture produces no planned signals; a real EventTrigger would generate
     * signals based on external events. Returning an empty list proves the SPI
     * contract is satisfied without external I/O.
     */
    @Override
    public List<PlannedSignalIntent> evaluate(TriggerEvaluationContext context) {
        return List.of();
    }
}
