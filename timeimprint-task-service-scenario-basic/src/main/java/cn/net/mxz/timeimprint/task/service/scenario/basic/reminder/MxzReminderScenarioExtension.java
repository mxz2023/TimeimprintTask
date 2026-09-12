package cn.net.mxz.timeimprint.task.service.scenario.basic.reminder;

import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.capability.notification.handler.MxzInAppNotificationHandler;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzDefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzInitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzScenarioExtensionDescriptor;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzSignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.extension.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.LifecycleCategory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * S01 reminder scenario: PLANNED → TRIGGERED/CANCELLED.
 * scenarioKey = "reminder", contractVersion = 1.
 */
@Component
public class MxzReminderScenarioExtension implements ScenarioExtension {

    public static final String SCENARIO_KEY = "reminder";
    public static final int CONTRACT_VERSION = 1;
    private static final long EXPIRES_AFTER_HOURS = 24L;

    @Override
    public ScenarioExtensionKey registrationKey() {
        return new ScenarioExtensionKey(SCENARIO_KEY, CONTRACT_VERSION);
    }

    @Override
    public MxzScenarioExtensionDescriptor descriptor() {
        return new MxzScenarioExtensionDescriptor(
                SCENARIO_KEY,
                CONTRACT_VERSION,
                List.of(), // no instance commands for S01
                List.of("calendar", "notification"));
    }

    @Override
    public void validateDefinitionConfig(MxzDefinitionConfigValidationContext context) {
        // S01 scenarioConfig must be empty object {} - validated at HTTP layer
    }

    @Override
    public HandlerResult planInitialDefinition(MxzInitialDefinitionContext context) {
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
    public HandlerResult processSignal(MxzSignalProcessContext context) {
        var instSnapshot = context.instanceSnapshot();
        if (instSnapshot == null) {
            return new HandlerResult.Rejected("INVALID_STATE", "No instance for signal");
        }

        if (!"PLANNED".equals(instSnapshot.scenarioState())) {
            return new HandlerResult.NoChange("already_not_planned:" + instSnapshot.scenarioState());
        }

        // Extract info from signal payload
        Map<String, Object> sigFields = context.payload() instanceof MxzJsonPayload jp
                ? jp.fields() : Map.of();
        String title = toString(sigFields.get("title"), "");
        String body = toString(sigFields.get("body"), null);
        String tenantId = toString(sigFields.get("tenantId"), "local");
        String recipientType = toString(sigFields.get("recipientType"), "USER");
        String recipientId = toString(sigFields.get("recipientId"), "local-actor");

        Instant occurrenceAt = instSnapshot.occurrenceAt() != null ? instSnapshot.occurrenceAt() : Instant.now();
        Instant expiresAt = occurrenceAt.plus(EXPIRES_AFTER_HOURS, ChronoUnit.HOURS);
        if (title == null || title.isBlank()) {
            title = instSnapshot.titleSnapshot() == null ? "" : instSnapshot.titleSnapshot();
        }
        if (body == null) {
            body = instSnapshot.descriptionSnapshot();
        }

        // Build stable action key
        String actionKeyInput = "in_app:" + instSnapshot.instanceId() + ":INITIAL:0:USER:" + recipientId;
        String actionKey = "ntf_" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MxzSha256.sha256Bytes(actionKeyInput.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        // Build notification payload
        Map<String, Object> notifPayload = new LinkedHashMap<>();
        notifPayload.put("purpose", "INITIAL");
        notifPayload.put("title", title);
        notifPayload.put("body", body);
        notifPayload.put("definitionId", instSnapshot.definitionId());
        notifPayload.put("instanceId", instSnapshot.instanceId());
        notifPayload.put("tenantId", tenantId);
        notifPayload.put("recipientType", recipientType);
        notifPayload.put("recipientId", recipientId);

        ActionJobIntent actionIntent = new ActionJobIntent(
                MxzInAppNotificationHandler.HANDLER_KEY,
                MxzInAppNotificationHandler.SCHEMA_VERSION,
                actionKey,
                "LOCAL_TRANSACTIONAL",
                "USER",
                recipientId,
                occurrenceAt,
                expiresAt,
                new MxzJsonPayload(notifPayload));

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
