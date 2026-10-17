package cn.net.mxz.timeimprint.task.service.scenario.deadline.maintenance.extension;

import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.extension.scenario.context.ScenarioExtensionDescriptor;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.scenario.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.DefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.InitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TriggerBindingChange;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** S14 保养与复查。下一次从实际完成日按自然日重算。 */
@Component
public class MaintenanceScenarioExtension implements ScenarioExtension {

    public static final String SCENARIO_KEY = "maintenance";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<String> FIELDS = Set.of("subjectName", "intervalDays", "nextDueDate", "localTime");

    @Override
    public ScenarioExtensionKey registrationKey() {
        return new ScenarioExtensionKey(SCENARIO_KEY, 1);
    }

    @Override
    public ScenarioExtensionDescriptor descriptor() {
        return new ScenarioExtensionDescriptor(
                SCENARIO_KEY, 1, List.of("complete"), List.of("calendar", "notification"));
    }

    @Override
    public void validateDefinitionConfig(DefinitionConfigValidationContext context) {
        read(context.scenarioConfig() instanceof JsonPayload json ? json.fields() : Map.of());
    }

    @Override
    public HandlerResult planInitialDefinition(InitialDefinitionContext context) {
        var target = new TransitionTarget(
                TransitionResourceType.DEFINITION,
                context.definitionSnapshot().definitionId(),
                context.definitionSnapshot().revision());
        return new HandlerResult.Applied(new TransitionPlan(
                target, null, null, List.of(), List.of(), List.of(), List.of(),
                List.of(mutation(Map.of("action", "sync"))), "sync_maintenance"));
    }

    @Override
    public HandlerResult processSignal(SignalProcessContext context) {
        var inst = context.instanceSnapshot();
        if (inst == null || !"PLANNED".equals(inst.scenarioState())) {
            return new HandlerResult.NoChange("not_planned");
        }
        MaintenanceConfig config = read(parse(context.definitionSnapshot().scenarioConfigJson()));
        LocalDate occ = inst.occurrenceAt().atZone(CalendarOccurrenceCalculator.BUSINESS_ZONE).toLocalDate();
        if (!occ.equals(config.nextDueDate())) {
            return new HandlerResult.NoChange("not_due");
        }
        return new HandlerResult.Applied(notice(context, inst, config));
    }

    @Override
    public void validateScenarioState(String scenarioState, int scenarioSchemaVersion) {
        if (!List.of("PLANNED", "REMINDED", "COMPLETED", "CANCELLED").contains(scenarioState)) {
            throw new IllegalArgumentException("Invalid maintenance scenario state: " + scenarioState);
        }
    }

    public static HandlerResult complete(
            TaskDefinitionSnapshot definition, TaskInstanceSnapshot instance, Instant now, Map<String, Object> payload) {
        if (payload == null || !payload.isEmpty()) {
            return new HandlerResult.Rejected("INVALID_REQUEST", "complete payload must be empty");
        }
        if (definition.controlState() != ControlState.ACTIVE) {
            return new HandlerResult.Rejected("INVALID_STATE", "complete requires ACTIVE definition");
        }
        if ("COMPLETED".equals(instance.scenarioState())) {
            return new HandlerResult.NoChange("already_completed");
        }
        if (!"PLANNED".equals(instance.scenarioState()) && !"REMINDED".equals(instance.scenarioState())) {
            return new HandlerResult.Rejected("STATE_CONFLICT", "complete requires PLANNED or REMINDED");
        }
        MaintenanceConfig config = read(parse(definition.scenarioConfigJson()));
        LocalDate completedOn = now.atZone(CalendarOccurrenceCalculator.BUSINESS_ZONE).toLocalDate();
        LocalDate next = completedOn.plusDays(config.intervalDays());
        Map<String, Object> scenarioConfig = scenarioConfig(config.subjectName(), config.intervalDays(), next, config.localTime());
        Map<String, Object> bindingConfig = bindingConfig(next, config.localTime());
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("bindingConfig", bindingConfig);
        replacement.put("scenarioConfig", scenarioConfig);
        Map<String, Object> mutationFields = new LinkedHashMap<>();
        mutationFields.put("action", "complete");
        mutationFields.put("completedOn", completedOn.toString());
        mutationFields.put("previousDueDate", config.nextDueDate().toString());
        mutationFields.put("nextDueDate", next.toString());
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, instance.instanceId(), instance.revision());
        var transition = new InstanceStateTransition(
                instance.lifecycleCategory(), LifecycleCategory.TERMINAL, instance.scenarioState(), "COMPLETED");
        var binding = new TriggerBindingChange(
                "primary", "calendar", 2, new JsonPayload(replacement), null, false, null);
        return new HandlerResult.Applied(new TransitionPlan(
                target, null, transition, List.of(), List.of(), List.of(binding), List.of(),
                List.of(mutation(mutationFields)), "complete_maintenance"));
    }

    public static MaintenanceConfig read(Map<String, Object> fields) {
        if (fields == null || !fields.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("scenarioConfig fields must be subjectName, intervalDays, nextDueDate, localTime");
        }
        String name = String.valueOf(fields.get("subjectName"));
        if (name.isBlank() || name.codePointCount(0, name.length()) > 40) {
            throw new IllegalArgumentException("subjectName length must be 1..40");
        }
        int interval = fields.get("intervalDays") instanceof Number n
                ? n.intValue()
                : Integer.parseInt(String.valueOf(fields.get("intervalDays")));
        if (interval < 1 || interval > 3650) {
            throw new IllegalArgumentException("intervalDays must be 1..3650");
        }
        LocalDate due = LocalDate.parse(String.valueOf(fields.get("nextDueDate")), DAY);
        String localTime = String.valueOf(fields.get("localTime"));
        LocalTime.parse(localTime, TIME);
        return new MaintenanceConfig(name, interval, due, localTime);
    }

    public static Map<String, Object> scenarioConfig(String subjectName, int intervalDays, LocalDate nextDueDate, String localTime) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("subjectName", subjectName);
        config.put("intervalDays", intervalDays);
        config.put("nextDueDate", nextDueDate.toString());
        config.put("localTime", localTime);
        return config;
    }

    public static Map<String, Object> bindingConfig(LocalDate anchorDate, String localTime) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "ANCHOR_OFFSETS");
        config.put("anchorDate", anchorDate.toString());
        config.put("offsetDays", List.of(0));
        config.put("dailyAfterAnchor", false);
        config.put("localTime", localTime);
        config.put("zoneId", "Asia/Shanghai");
        return config;
    }

    static ScenarioDataMutation mutation(Map<String, Object> fields) {
        return new ScenarioDataMutation(SCENARIO_KEY, "apply", 1, new JsonPayload(fields));
    }

    private static TransitionPlan notice(SignalProcessContext context, TaskInstanceSnapshot inst, MaintenanceConfig config) {
        Instant at = inst.occurrenceAt();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", "INITIAL");
        payload.put("slotIndex", 0);
        payload.put("actionGeneration", 1);
        payload.put("title", context.definitionSnapshot().title());
        payload.put("body", "今天该保养：" + config.subjectName());
        payload.put("definitionId", context.definitionSnapshot().definitionId());
        payload.put("instanceId", inst.instanceId());
        payload.put("tenantId", context.definitionSnapshot().tenantId());
        var action = new ActionJobIntent(
                "in_app_notification", 1, "INITIAL:PENDING_EXPAND", "LOCAL_TRANSACTIONAL",
                null, null, at, at.plus(24, ChronoUnit.HOURS), new JsonPayload(payload));
        var transition = new InstanceStateTransition(
                LifecycleCategory.WAITING, LifecycleCategory.WAITING, "PLANNED", "REMINDED");
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, inst.instanceId(), inst.revision());
        return new TransitionPlan(
                target, null, transition, List.of(), List.of(action), List.of(), List.of(), List.of(),
                "maintenance_signal");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        try {
            return new tools.jackson.databind.json.JsonMapper().readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public record MaintenanceConfig(String subjectName, int intervalDays, LocalDate nextDueDate, String localTime) {}
}
