package cn.net.mxz.timeimprint.task.service.scenario.deadline.anniversary.extension;

import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.extension.scenario.context.ScenarioExtensionDescriptor;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.scenario.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.DefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.InitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionTarget;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** S03 公历生日与纪念日。 */
@Component
public class AnniversaryScenarioExtension implements ScenarioExtension {

    public static final String SCENARIO_KEY = "anniversary";
    static final ZoneId ZONE = CalendarOccurrenceCalculator.BUSINESS_ZONE;
    private static final Set<String> KINDS = Set.of("BIRTHDAY", "ANNIVERSARY");
    private static final Set<String> KEYS =
            Set.of("kind", "subjectName", "month", "dayOfMonth", "localTime", "startYear");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public ScenarioExtensionKey registrationKey() {
        return new ScenarioExtensionKey(SCENARIO_KEY, 1);
    }

    @Override
    public ScenarioExtensionDescriptor descriptor() {
        return new ScenarioExtensionDescriptor(SCENARIO_KEY, 1, List.of(), List.of("calendar", "notification"));
    }

    @Override
    public void validateDefinitionConfig(DefinitionConfigValidationContext context) {
        read(fields(context.scenarioConfig()));
    }

    @Override
    public HandlerResult planInitialDefinition(InitialDefinitionContext context) {
        TransitionTarget target = new TransitionTarget(
                TransitionResourceType.DEFINITION,
                context.definitionSnapshot().definitionId(),
                context.definitionSnapshot().revision());
        var mutation = new ScenarioDataMutation(SCENARIO_KEY, "sync_subject", 1, new JsonPayload(Map.of()));
        return new HandlerResult.Applied(new TransitionPlan(
                target, null, null, List.of(), List.of(), List.of(), List.of(), List.of(mutation), "sync_anniversary"));
    }

    @Override
    public HandlerResult processSignal(SignalProcessContext context) {
        var inst = context.instanceSnapshot();
        if (inst == null || !"PLANNED".equals(inst.scenarioState())) {
            return new HandlerResult.NoChange("not_planned");
        }
        Map<String, Object> config = parseJson(context.definitionSnapshot().scenarioConfigJson());
        AnniversaryConfig parsed = read(config);
        LocalDate occ = inst.occurrenceAt().atZone(ZONE).toLocalDate();
        boolean today = CalendarOccurrenceCalculator.anniversaryDate(occ.getYear(), parsed.month(), parsed.day())
                .equals(occ);
        LocalDate next = occ.plusDays(1);
        boolean tomorrow = CalendarOccurrenceCalculator.anniversaryDate(next.getYear(), parsed.month(), parsed.day())
                .equals(next);
        int year = today ? occ.getYear() : next.getYear();
        String body = body(parsed, today, tomorrow, year);
        return new HandlerResult.Applied(noticePlan(context, inst.instanceId(), inst.revision(), parsed.name(), body, "TRIGGERED"));
    }

    @Override
    public void validateScenarioState(String scenarioState, int scenarioSchemaVersion) {
        if (!List.of("PLANNED", "TRIGGERED", "CANCELLED").contains(scenarioState)) {
            throw new IllegalArgumentException("Invalid anniversary scenario state: " + scenarioState);
        }
    }

    static String body(AnniversaryConfig parsed, boolean today, boolean tomorrow, int year) {
        String when = tomorrow && !today ? "明天是" : "今天是";
        String number = "";
        if (parsed.startYear() != null) {
            int delta = year - parsed.startYear();
            if (delta > 0) {
                number = "BIRTHDAY".equals(parsed.kind()) ? "，" + delta + "岁" : "，第" + delta + "周年";
            }
        }
        String kind = "BIRTHDAY".equals(parsed.kind()) ? "生日" : "纪念日";
        return when + parsed.name() + "的" + kind + number;
    }

    public static AnniversaryConfig read(Map<String, Object> fields) {
        if (fields == null) {
            throw new IllegalArgumentException("scenarioConfig must be object");
        }
        for (String key : fields.keySet()) {
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("unknown scenarioConfig field: " + key);
            }
        }
        String kind = String.valueOf(fields.get("kind"));
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("kind must be BIRTHDAY or ANNIVERSARY");
        }
        String name = String.valueOf(fields.get("subjectName"));
        if (name.isBlank() || name.length() > 40) {
            throw new IllegalArgumentException("subjectName length must be 1..40");
        }
        int month = asInt(fields.get("month"));
        int day = asInt(fields.get("dayOfMonth"));
        try {
            CalendarOccurrenceCalculator.anniversaryDate(2024, month, day);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("dayOfMonth is not valid for month");
        }
        if (month != 2 || day != 29) {
            LocalDate.of(2021, month, 1).withDayOfMonth(day);
        }
        String localTime = String.valueOf(fields.get("localTime"));
        LocalTime.parse(localTime, TIME);
        Integer startYear = fields.containsKey("startYear") && fields.get("startYear") != null
                ? asInt(fields.get("startYear"))
                : null;
        if (startYear != null && (startYear < 1900 || startYear > 2100)) {
            throw new IllegalArgumentException("startYear out of range");
        }
        if (startYear != null && month == 2 && day == 29 && !java.time.Year.isLeap(startYear)) {
            throw new IllegalArgumentException("startYear must be a leap year for Feb 29");
        }
        return new AnniversaryConfig(kind, name, month, day, startYear, localTime);
    }

    static TransitionPlan noticePlan(
            SignalProcessContext context, long instanceId, long revision, String title, String body, String state) {
        Instant at = context.instanceSnapshot().occurrenceAt();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", "INITIAL");
        payload.put("slotIndex", 0);
        payload.put("actionGeneration", 1);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("definitionId", context.definitionSnapshot().definitionId());
        payload.put("instanceId", instanceId);
        payload.put("tenantId", context.definitionSnapshot().tenantId());
        var action = new ActionJobIntent(
                "in_app_notification",
                1,
                "INITIAL:PENDING_EXPAND",
                "LOCAL_TRANSACTIONAL",
                null,
                null,
                at,
                at.plus(24, ChronoUnit.HOURS),
                new JsonPayload(payload));
        var transition = new InstanceStateTransition(
                LifecycleCategory.WAITING, LifecycleCategory.TERMINAL, "PLANNED", state);
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, instanceId, revision);
        return new TransitionPlan(
                target, null, transition, List.of(), List.of(action), List.of(), List.of(), List.of(), "anniversary_signal");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fields(Object payload) {
        if (payload instanceof JsonPayload json) {
            return json.fields();
        }
        return Map.of();
    }

    private static Map<String, Object> parseJson(String json) {
        try {
            return new tools.jackson.databind.json.JsonMapper().readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static int asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    public record AnniversaryConfig(String kind, String name, int month, int day, Integer startYear, String localTime) {}
}
