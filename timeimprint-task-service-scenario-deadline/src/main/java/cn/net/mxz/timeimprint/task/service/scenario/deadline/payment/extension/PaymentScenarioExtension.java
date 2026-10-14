package cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.extension;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** S05 人工缴费。 */
@Component
public class PaymentScenarioExtension implements ScenarioExtension {

    public static final String SCENARIO_KEY = "payment";
    private static final Set<Integer> OFFSETS = Set.of(30, 7, 1, 0);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final JdbcTemplate jdbc;

    public PaymentScenarioExtension(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ScenarioExtensionKey registrationKey() {
        return new ScenarioExtensionKey(SCENARIO_KEY, 1);
    }

    @Override
    public ScenarioExtensionDescriptor descriptor() {
        return new ScenarioExtensionDescriptor(
                SCENARIO_KEY, 1, List.of("cancel", "confirmPayment", "reverse"), List.of("calendar", "notification"));
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
                List.of(mutation(Map.of("action", "sync"))), "sync_payment"));
    }

    @Override
    public HandlerResult processSignal(SignalProcessContext context) {
        var inst = context.instanceSnapshot();
        if (inst == null || !"PLANNED".equals(inst.scenarioState())) {
            return new HandlerResult.NoChange("not_planned");
        }
        PaymentConfig config = read(parse(context.definitionSnapshot().scenarioConfigJson()));
        Map<String, Object> subject = jdbc.queryForMap(
                "SELECT paid_fen, status FROM tt_payment_subject WHERE definition_id=?",
                context.definitionSnapshot().definitionId());
        String status = String.valueOf(subject.get("status"));
        if ("PAID".equals(status) || "CANCELLED".equals(status)) {
            return new HandlerResult.NoChange("payment_closed");
        }
        long paid = asLong(subject.get("paid_fen"));
        LocalDate occ = inst.occurrenceAt().atZone(CalendarOccurrenceCalculator.BUSINESS_ZONE).toLocalDate();
        boolean overdue = !occ.isBefore(config.dueDate());
        String body = (overdue ? "已到应付日，" : "即将应付，")
                + config.periodLabel()
                + " 应付 "
                + yuan(config.amountFen())
                + " 元，已付 "
                + yuan(paid)
                + " 元";
        var plan = notice(context, inst.instanceId(), inst.revision(), context.definitionSnapshot().title(), body);
        if (overdue) {
            plan = new TransitionPlan(
                    plan.target(), null, plan.instanceStateTransition(), plan.participantChanges(),
                    plan.actionJobIntents(), List.of(), List.of(), List.of(mutation(Map.of("action", "overdue"))),
                    plan.auditSummary());
        }
        return new HandlerResult.Applied(plan);
    }

    public static ConfirmResult confirm(long amountFen, long paidFen, long addFen) {
        if (addFen <= 0) {
            throw new IllegalArgumentException("amountFen must be positive");
        }
        if (paidFen + addFen > amountFen) {
            throw new IllegalArgumentException("payment exceeds balance");
        }
        long next = paidFen + addFen;
        return new ConfirmResult(next, next == amountFen ? "PAID" : "PARTIAL");
    }

    @Override
    public void validateScenarioState(String scenarioState, int scenarioSchemaVersion) {
        if (!List.of("PLANNED", "TRIGGERED", "CANCELLED").contains(scenarioState)) {
            throw new IllegalArgumentException("Invalid payment scenario state: " + scenarioState);
        }
    }

    public static HandlerResult controlPlan(
            ControlState from,
            ControlState to,
            long instanceId,
            long instanceRevision,
            Map<String, Object> mutationFields) {
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, instanceId, instanceRevision);
        DefinitionControlTransition control = from == to ? null : new DefinitionControlTransition(from, to);
        return new HandlerResult.Applied(new TransitionPlan(
                target, control, null, List.of(), List.of(), List.of(), List.of(),
                List.of(mutation(mutationFields)), "payment_command"));
    }

    private static String yuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }

    public static PaymentConfig read(Map<String, Object> fields) {
        long amount = asLong(fields.get("amountFen"));
        if (amount <= 0 || amount > 100_000_000_000L) {
            throw new IllegalArgumentException("amountFen out of range");
        }
        String label = String.valueOf(fields.get("periodLabel"));
        if (label.isBlank() || label.length() > 40) {
            throw new IllegalArgumentException("periodLabel length must be 1..40");
        }
        LocalDate due = LocalDate.parse(String.valueOf(fields.get("dueDate")), DAY);
        String localTime = String.valueOf(fields.get("localTime"));
        LocalTime.parse(localTime, TIME);
        if (!(fields.get("offsetDays") instanceof List<?> list) || list.isEmpty() || list.size() > 4) {
            throw new IllegalArgumentException("offsetDays must contain 1..4 values");
        }
        for (Object item : list) {
            int value = item instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(item));
            if (!OFFSETS.contains(value)) {
                throw new IllegalArgumentException("offsetDays must be chosen from 30, 7, 1, 0");
            }
        }
        return new PaymentConfig(amount, label, due, localTime);
    }

    static ScenarioDataMutation mutation(Map<String, Object> fields) {
        return new ScenarioDataMutation(SCENARIO_KEY, "apply", 1, new JsonPayload(fields));
    }

    private static TransitionPlan notice(
            SignalProcessContext context, long instanceId, long revision, String title, String body) {
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
                LifecycleCategory.WAITING, LifecycleCategory.TERMINAL, "PLANNED", "TRIGGERED");
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, instanceId, revision);
        return new TransitionPlan(
                target, null, transition, List.of(), List.of(action), List.of(), List.of(), List.of(), "payment_signal");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        try {
            return new tools.jackson.databind.json.JsonMapper().readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    public record PaymentConfig(long amountFen, String periodLabel, LocalDate dueDate, String localTime) {}

    public record ConfirmResult(long paidFen, String status) {}
}
