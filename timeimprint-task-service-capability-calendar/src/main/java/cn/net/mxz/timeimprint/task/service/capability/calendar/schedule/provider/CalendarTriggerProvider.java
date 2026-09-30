package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.provider;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.extension.trigger.context.TriggerEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.trigger.registry.TriggerProviderKey;
import cn.net.mxz.timeimprint.task.service.extension.trigger.spi.TriggerProvider;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.shared.model.PlannedSignalIntent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * calendar TriggerProvider：将配置评估为计划 Signal 意图（不改业务状态）。
 */
@Component
public class CalendarTriggerProvider implements TriggerProvider {

    public static final String PROVIDER_KEY = "calendar";

    private final BusinessClock clock;

    public CalendarTriggerProvider(BusinessClock clock) {
        this.clock = clock;
    }

    @Override
    public TriggerProviderKey registrationKey() {
        return new TriggerProviderKey(PROVIDER_KEY, 1);
    }

    @Override
    public Set<Integer> supportedSchemaVersions() {
        return Set.of(1);
    }

    @Override
    public List<PlannedSignalIntent> evaluate(TriggerEvaluationContext context) {
        if (context.configSchemaVersion() != 1) {
            throw new IllegalArgumentException("UNSUPPORTED_SCHEMA_VERSION");
        }
        var rule = CalendarConfigParser.parse(context.bindingConfig());
        Instant after = clock.nowUtcSeconds();
        Instant windowEnd = after.plus(7, ChronoUnit.DAYS);
        var occurrences = CalendarOccurrenceCalculator.preview(rule, after, 100);
        List<PlannedSignalIntent> out = new ArrayList<>();
        long definitionId = context.definitionSnapshot().definitionId();
        long controlGen = context.definitionSnapshot().controlGeneration();
        for (var occ : occurrences) {
            if (occ.occurrenceAt().isAfter(windowEnd)) {
                break;
            }
            String signalKey = signalKey(definitionId, context.bindingKey(), 1L, controlGen, occ.occurrenceKey());
            var payload = new JsonPayload(Map.of(
                    "occurrenceKey", occ.occurrenceKey(),
                    "occurrenceAt", occ.occurrenceAt().toString(),
                    "bindingKey", context.bindingKey()));
            out.add(new PlannedSignalIntent(
                    PROVIDER_KEY,
                    signalKey,
                    1,
                    definitionId,
                    null,
                    occ.occurrenceAt(),
                    payload));
        }
        return out;
    }

    public static String signalKey(
            long definitionId, String bindingKey, long scheduleGeneration, long controlGeneration, String occurrenceKey) {
        return "cal:"
                + definitionId
                + ":"
                + bindingKey
                + ":sg"
                + scheduleGeneration
                + ":cg"
                + controlGeneration
                + ":"
                + occurrenceKey;
    }
}
