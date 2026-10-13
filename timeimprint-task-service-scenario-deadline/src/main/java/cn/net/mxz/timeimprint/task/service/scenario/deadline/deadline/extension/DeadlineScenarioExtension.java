package cn.net.mxz.timeimprint.task.service.scenario.deadline.deadline.extension;

import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.extension.scenario.context.ScenarioExtensionDescriptor;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.scenario.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.DefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.InitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.DefinitionControlTransition;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** S04 到期对象。 */
@Component
public class DeadlineScenarioExtension implements ScenarioExtension {

    public static final String SCENARIO_KEY = "deadline";
    private static final Set<String> TYPES = Set.of("DOCUMENT", "CONTRACT", "SUBSCRIPTION", "WARRANTY");
    private static final Set<Integer> OFFSETS = Set.of(30, 7, 1, 0);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public ScenarioExtensionKey registrationKey() {
        return new ScenarioExtensionKey(SCENARIO_KEY, 1);
    }

    @Override
    public ScenarioExtensionDescriptor descriptor() {
        return new ScenarioExtensionDescriptor(SCENARIO_KEY, 1, List.of("cancel"), List.of("calendar", "notification"));
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
                List.of(mutation("sync")), "sync_deadline"));
    }

    @Override
    public HandlerResult processSignal(SignalProcessContext context) {
        var inst = context.instanceSnapshot();
        if (inst == null || !"PLANNED".equals(inst.scenarioState())) {
            return new HandlerResult.NoChange("not_planned");
        }
        DeadlineConfig config = read(parse(context.definitionSnapshot().scenarioConfigJson()));
        LocalDate occ = inst.occurrenceAt().atZone(CalendarOccurrenceCalculator.BUSINESS_ZONE).toLocalDate();
        boolean due = occ.equals(config.dueDate());
        String state = due ? "EXPIRED" : "TRIGGERED";
        String body = due ? "今天到期" : "将于 " + config.dueDate() + " 到期";
        var plan = notice(context, inst.instanceId(), inst.revision(), context.definitionSnapshot().title(), body, state);
        if (due) {
            plan = new TransitionPlan(
                    plan.target(), plan.definitionControlTransition(), plan.instanceStateTransition(),
                    plan.participantChanges(), plan.actionJobIntents(), plan.triggerBindingChanges(),
                    plan.plannedSignalIntents(), List.of(mutation("expire")), plan.auditSummary());
        }
        return new HandlerResult.Applied(plan);
    }

    @Override
    public void validateScenarioState(String scenarioState, int scenarioSchemaVersion) {
        if (!List.of("PLANNED", "TRIGGERED", "EXPIRED", "CANCELLED").contains(scenarioState)) {
            throw new IllegalArgumentException("Invalid deadline scenario state: " + scenarioState);
        }
    }

    public static HandlerResult cancel(long definitionId, long definitionRevision, ControlState control, long instanceId, long instanceRevision) {
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, instanceId, instanceRevision);
        var retire = control == ControlState.RETIRED
                ? null
                : new DefinitionControlTransition(control, ControlState.RETIRED);
        var transition = new InstanceStateTransition(
                LifecycleCategory.WAITING, LifecycleCategory.TERMINAL, "PLANNED", "CANCELLED");
        return new HandlerResult.Applied(new TransitionPlan(
                target, retire, transition, List.of(), List.of(), List.of(), List.of(),
                List.of(mutation("cancel")), "cancel_deadline"));
    }

    public static DeadlineConfig read(Map<String, Object> fields) {
        String type = String.valueOf(fields.get("subjectType"));
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("subjectType is invalid");
        }
        LocalDate due = LocalDate.parse(String.valueOf(fields.get("dueDate")), DAY);
        LocalTime.parse(String.valueOf(fields.get("localTime")), TIME);
        if (!(fields.get("offsetDays") instanceof List<?> list) || list.isEmpty() || list.size() > 4) {
            throw new IllegalArgumentException("offsetDays must contain 1..4 values");
        }
        for (Object item : list) {
            int value = item instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(item));
            if (!OFFSETS.contains(value)) {
                throw new IllegalArgumentException("offsetDays must be chosen from 30, 7, 1, 0");
            }
        }
        return new DeadlineConfig(type, due, String.valueOf(fields.get("localTime")));
    }

    static ScenarioDataMutation mutation(String action) {
        return new ScenarioDataMutation(SCENARIO_KEY, "apply", 1, new JsonPayload(Map.of("action", action)));
    }

    private static TransitionPlan notice(
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
                "in_app_notification", 1, "INITIAL:PENDING_EXPAND", "LOCAL_TRANSACTIONAL",
                null, null, at, at.plus(24, ChronoUnit.HOURS), new JsonPayload(payload));
        var transition = new InstanceStateTransition(
                LifecycleCategory.WAITING, LifecycleCategory.TERMINAL, "PLANNED", state);
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, instanceId, revision);
        return new TransitionPlan(
                target, null, transition, List.of(), List.of(action), List.of(), List.of(), List.of(), "deadline_signal");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        try {
            return new tools.jackson.databind.json.JsonMapper().readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public record DeadlineConfig(String subjectType, LocalDate dueDate, String localTime) {}
}
