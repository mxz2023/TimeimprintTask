package cn.net.mxz.timeimprint.task.service.scenario.basic.reminder.extension;

import cn.net.mxz.timeimprint.task.service.capability.notification.inapp.handler.InAppNotificationHandler;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.DefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.InitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.scenario.context.ScenarioExtensionDescriptor;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.extension.scenario.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * S01 reminder scenario: PLANNED → TRIGGERED/CANCELLED.
 * scenarioKey = "reminder", contractVersion = 1.
 */
@Component
public class ReminderScenarioExtension implements ScenarioExtension {

    public static final String SCENARIO_KEY = "reminder";
    public static final int CONTRACT_VERSION = 1;
    private static final long EXPIRES_AFTER_HOURS = 24L;

    @Override
    public ScenarioExtensionKey registrationKey() {
        return new ScenarioExtensionKey(SCENARIO_KEY, CONTRACT_VERSION);
    }

    @Override
    public ScenarioExtensionDescriptor descriptor() {
        return new ScenarioExtensionDescriptor(
                SCENARIO_KEY,
                CONTRACT_VERSION,
                List.of(), // no instance commands for S01
                List.of("calendar", "notification"));
    }

    @Override
    public void validateDefinitionConfig(DefinitionConfigValidationContext context) {
        if (!(context.scenarioConfig() instanceof JsonPayload(Map<String, Object> fields))) {
            throw new IllegalArgumentException("scenarioConfig must be object");
        }
        if (!fields.isEmpty()) {
            throw new IllegalArgumentException("reminder scenarioConfig must be empty object");
        }
    }

    @Override
    public HandlerResult planInitialDefinition(InitialDefinitionContext context) {
        // Calendar provider handles instance/signal planning via Planner; nothing needed at creation
        TransitionTarget target = new TransitionTarget(
                TransitionResourceType.DEFINITION,
                context.definitionSnapshot().definitionId(),
                context.definitionSnapshot().revision());
        TransitionPlan plan = new TransitionPlan(target, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), "initial_definition");
        return new HandlerResult.Applied(plan);
    }

    @Override
    public HandlerResult processSignal(SignalProcessContext context) {
        var instSnapshot = context.instanceSnapshot();
        if (instSnapshot == null) {
            return new HandlerResult.Rejected("INVALID_STATE", "No instance for signal");
        }

        if (!"PLANNED".equals(instSnapshot.scenarioState())) {
            return new HandlerResult.NoChange("already_not_planned:" + instSnapshot.scenarioState());
        }

        // Extract info from signal payload. Final recipients are expanded by the platform
        // (PENDING_EXPAND); scenarios must not bind a single recipient into the Action template.
        Map<String, Object> sigFields = context.payload() instanceof JsonPayload(Map<String, Object> fields)
                ? fields : Map.of();
        String title = toString(sigFields.get("title"), "");
        String body = toString(sigFields.get("body"), null);
        String tenantId = toString(sigFields.get("tenantId"), "local");

        Instant occurrenceAt = instSnapshot.occurrenceAt() != null ? instSnapshot.occurrenceAt() : Instant.now();
        Instant expiresAt = occurrenceAt.plus(EXPIRES_AFTER_HOURS, ChronoUnit.HOURS);
        if (title == null || title.isBlank()) {
            title = instSnapshot.titleSnapshot() == null ? "" : instSnapshot.titleSnapshot();
        }
        if (body == null) {
            body = instSnapshot.descriptionSnapshot();
        }

        Map<String, Object> notifPayload = new LinkedHashMap<>();
        notifPayload.put("purpose", "INITIAL");
        notifPayload.put("slotIndex", 0);
        notifPayload.put("actionGeneration", 1);
        notifPayload.put("title", title);
        notifPayload.put("body", body);
        notifPayload.put("definitionId", instSnapshot.definitionId());
        notifPayload.put("instanceId", instSnapshot.instanceId());
        notifPayload.put("tenantId", tenantId);

        ActionJobIntent actionIntent = new ActionJobIntent(
                InAppNotificationHandler.HANDLER_KEY,
                InAppNotificationHandler.SCHEMA_VERSION,
                "INITIAL:PENDING_EXPAND",
                "LOCAL_TRANSACTIONAL",
                null,
                null,
                occurrenceAt,
                expiresAt,
                new JsonPayload(notifPayload));

        InstanceStateTransition transition = new InstanceStateTransition(
                LifecycleCategory.WAITING,
                LifecycleCategory.TERMINAL,
                "PLANNED",
                "TRIGGERED");

        TransitionTarget target = new TransitionTarget(
                TransitionResourceType.INSTANCE,
                instSnapshot.instanceId(),
                instSnapshot.revision());

        TransitionPlan plan = new TransitionPlan(
                target,
                null,
                transition,
                List.of(),
                List.of(actionIntent),
                List.of(),
                List.of(),
                List.of(),
                "signal_triggered:" + context.signalKey());

        return new HandlerResult.Applied(plan);
    }

    @Override
    public void validateScenarioState(String scenarioState, int scenarioSchemaVersion) {
        if (!List.of("PLANNED", "TRIGGERED", "CANCELLED").contains(scenarioState)) {
            throw new IllegalArgumentException("Invalid reminder scenario state: " + scenarioState);
        }
    }

    private String toString(Object o, String defaultVal) {
        return o != null ? String.valueOf(o) : defaultVal;
    }
}
