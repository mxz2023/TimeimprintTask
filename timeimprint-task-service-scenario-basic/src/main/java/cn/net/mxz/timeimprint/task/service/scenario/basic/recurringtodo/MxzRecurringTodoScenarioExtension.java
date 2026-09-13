package cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo;

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
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.LifecycleCategory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * S02 recurring_todo scenario extension.
 * Signal processing: PLANNED (WAITING) → PENDING (ACTIVE).
 * Instance commands: complete / skip / snooze routed via MxzRecurringTodoCommandHandler.
 *
 * scenarioKey = "recurring_todo", contractVersion = 1.
 */
@Component
public class MxzRecurringTodoScenarioExtension implements ScenarioExtension {

    public static final String SCENARIO_KEY = "recurring_todo";
    public static final int CONTRACT_VERSION = 1;
    public static final String MUTATION_REPLACE_INSTANCE_SNAPSHOT = "REPLACE_INSTANCE_SNAPSHOT";

    // Default config values per S02 contract
    static final List<Integer> DEFAULT_CHASE_OFFSETS = List.of(60, 240, 720);
    static final int DEFAULT_NOTIFICATION_EXPIRE_MINUTES = 1440;
    static final int DEFAULT_MAX_SNOOZE_COUNT = 3;

    private final ObjectMapper objectMapper;

    public MxzRecurringTodoScenarioExtension(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ScenarioExtensionKey registrationKey() {
        return new ScenarioExtensionKey(SCENARIO_KEY, CONTRACT_VERSION);
    }

    @Override
    public MxzScenarioExtensionDescriptor descriptor() {
        return new MxzScenarioExtensionDescriptor(
                SCENARIO_KEY,
                CONTRACT_VERSION,
                List.of("complete", "skip", "snooze"),
                List.of("calendar", "notification"));
    }

    @Override
    public void validateDefinitionConfig(MxzDefinitionConfigValidationContext context) {
        if (!(context.scenarioConfig() instanceof MxzJsonPayload jp)) {
            throw new IllegalArgumentException("scenarioConfig must be object");
        }
        expandDefaults(jp.fields());
    }

    /**
     * Expand omitted S02 defaults and validate schemaVersion 1 rules.
     * Explicit null / unknown fields / illegal ranges are rejected.
     */
    public static Map<String, Object> expandDefaults(Map<String, Object> raw) {
        if (raw == null) {
            throw new IllegalArgumentException("scenarioConfig required");
        }
        for (String key : raw.keySet()) {
            if (!Set.of(
                            "chaseOffsetsMinutes",
                            "notificationExpireAfterMinutes",
                            "maxSnoozeCount")
                    .contains(key)) {
                throw new IllegalArgumentException("unknown scenarioConfig field: " + key);
            }
            if (raw.get(key) == null) {
                throw new IllegalArgumentException(key + " cannot be null");
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
                "chaseOffsetsMinutes",
                raw.containsKey("chaseOffsetsMinutes")
                        ? raw.get("chaseOffsetsMinutes")
                        : List.copyOf(DEFAULT_CHASE_OFFSETS));
        out.put(
                "notificationExpireAfterMinutes",
                raw.containsKey("notificationExpireAfterMinutes")
                        ? raw.get("notificationExpireAfterMinutes")
                        : DEFAULT_NOTIFICATION_EXPIRE_MINUTES);
        out.put(
                "maxSnoozeCount",
                raw.containsKey("maxSnoozeCount") ? raw.get("maxSnoozeCount") : DEFAULT_MAX_SNOOZE_COUNT);
        validateExpanded(out);
        return out;
    }

    @SuppressWarnings("unchecked")
    static void validateExpanded(Map<String, Object> cfg) {
        Object expireObj = cfg.get("notificationExpireAfterMinutes");
        if (!(expireObj instanceof Number)) {
            throw new IllegalArgumentException("notificationExpireAfterMinutes must be int");
        }
        int expire = ((Number) expireObj).intValue();
        if (expire < 60 || expire > 10080) {
            throw new IllegalArgumentException("notificationExpireAfterMinutes must be 60..10080");
        }
        Object maxObj = cfg.get("maxSnoozeCount");
        if (!(maxObj instanceof Number)) {
            throw new IllegalArgumentException("maxSnoozeCount must be int");
        }
        int maxSnooze = ((Number) maxObj).intValue();
        if (maxSnooze < 0 || maxSnooze > 3) {
            throw new IllegalArgumentException("maxSnoozeCount must be 0..3");
        }
        Object offsetsObj = cfg.get("chaseOffsetsMinutes");
        if (!(offsetsObj instanceof List<?> list)) {
            throw new IllegalArgumentException("chaseOffsetsMinutes must be an array");
        }
        if (list.size() > 3) {
            throw new IllegalArgumentException("chaseOffsetsMinutes length must be 0..3");
        }
        int prev = 0;
        for (Object item : list) {
            if (!(item instanceof Number)) {
                throw new IllegalArgumentException("chaseOffsetsMinutes items must be int");
            }
            int v = ((Number) item).intValue();
            if (v < 1 || v >= expire) {
                throw new IllegalArgumentException(
                        "chaseOffsetsMinutes items must be >=1 and < notificationExpireAfterMinutes");
            }
            if (v <= prev) {
                throw new IllegalArgumentException("chaseOffsetsMinutes must be strictly increasing");
            }
            prev = v;
        }
    }

    @Override
    public HandlerResult planInitialDefinition(MxzInitialDefinitionContext context) {
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

        Map<String, Object> scenarioConfig = parseScenarioConfig(context.definitionSnapshot().scenarioConfigJson());
        List<Integer> chaseOffsets = getChaseOffsets(scenarioConfig);
        int expireMinutes = getExpireMinutes(scenarioConfig);
        int maxSnooze = getMaxSnoozeCount(scenarioConfig);

        Instant dueAt = instSnapshot.dueAt() != null ? instSnapshot.dueAt()
                : (instSnapshot.occurrenceAt() != null ? instSnapshot.occurrenceAt() : Instant.now());
        Instant expiresAt = dueAt.plus(expireMinutes, ChronoUnit.MINUTES);

        String recipientId = extractRecipient(context);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scenarioState", "PENDING");
        snapshot.put("dueAt", dueAt.toString());
        snapshot.put("expiresAt", expiresAt.toString());
        snapshot.put("chaseOffsetsMinutes", chaseOffsets);
        snapshot.put("notificationExpireAfterMinutes", expireMinutes);
        snapshot.put("maxSnoozeCount", maxSnooze);
        snapshot.put("snoozeCount", 0);
        snapshot.put("actionGeneration", 1);

        long definitionId = context.definitionSnapshot().definitionId();
        List<ActionJobIntent> actions = new ArrayList<>();
        actions.add(buildActionIntent(definitionId, instSnapshot.instanceId(), "INITIAL", 0, 1,
                recipientId, dueAt, expiresAt));

        for (int i = 0; i < chaseOffsets.size(); i++) {
            Instant chaseAt = dueAt.plus(chaseOffsets.get(i), ChronoUnit.MINUTES);
            if (chaseAt.isBefore(expiresAt)) {
                actions.add(buildActionIntent(definitionId, instSnapshot.instanceId(), "CHASE", i + 1, 1,
                        recipientId, chaseAt, expiresAt));
            }
        }

        InstanceStateTransition transition = new InstanceStateTransition(
                LifecycleCategory.WAITING,
                LifecycleCategory.ACTIVE,
                "PLANNED",
                "PENDING");

        TransitionTarget target = new TransitionTarget(
                TransitionResourceType.INSTANCE,
                instSnapshot.instanceId(),
                instSnapshot.revision());

        TransitionPlan plan = new TransitionPlan(
                target,
                null,
                transition,
                List.of(),
                actions,
                List.of(),
                List.of(),
                List.of(replaceSnapshotMutation(snapshot)),
                "signal_due:" + context.signalKey());

        return new HandlerResult.Applied(plan);
    }

    @Override
    public void validateScenarioState(String scenarioState, int scenarioSchemaVersion) {
        List<String> valid = List.of("PLANNED", "PENDING", "COMPLETED", "SKIPPED", "CANCELLED");
        if (!valid.contains(scenarioState)) {
            throw new IllegalArgumentException("Invalid recurring_todo scenario state: " + scenarioState);
        }
    }

    static ScenarioDataMutation replaceSnapshotMutation(Map<String, Object> snapshot) {
        return new ScenarioDataMutation(
                SCENARIO_KEY,
                MUTATION_REPLACE_INSTANCE_SNAPSHOT,
                1,
                new MxzJsonPayload(snapshot));
    }

    static ActionJobIntent buildActionIntent(
            long definitionId, long instanceId, String purpose, int slotIndex, int actionGeneration,
            String recipientId, Instant availableAt, Instant expiresAt) {

        String canon = instanceId + ":" + purpose + ":" + slotIndex + ":" + actionGeneration
                + ":" + recipientId + ":in_app_notification";
        String actionKey = purpose + ":" + base64Url(MxzSha256.digestUtf8(canon));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", purpose);
        payload.put("slotIndex", slotIndex);
        payload.put("actionGeneration", actionGeneration);
        payload.put("definitionId", definitionId);
        payload.put("instanceId", instanceId);
        payload.put("recipientType", "USER");
        payload.put("recipientId", recipientId);

        return new ActionJobIntent(
                MxzInAppNotificationHandler.HANDLER_KEY,
                MxzInAppNotificationHandler.SCHEMA_VERSION,
                actionKey,
                "LOCAL_TRANSACTIONAL",
                "USER",
                recipientId,
                availableAt,
                expiresAt,
                new MxzJsonPayload(payload));
    }

    @SuppressWarnings("unchecked")
    static List<Integer> getChaseOffsets(Map<String, Object> cfg) {
        Object v = cfg.get("chaseOffsetsMinutes");
        if (v instanceof List<?> list) {
            return list.stream().map(e -> ((Number) e).intValue()).toList();
        }
        return DEFAULT_CHASE_OFFSETS;
    }

    static int getExpireMinutes(Map<String, Object> cfg) {
        Object v = cfg.get("notificationExpireAfterMinutes");
        if (v instanceof Number n) return n.intValue();
        return DEFAULT_NOTIFICATION_EXPIRE_MINUTES;
    }

    static int getMaxSnoozeCount(Map<String, Object> cfg) {
        Object v = cfg.get("maxSnoozeCount");
        if (v instanceof Number n) return n.intValue();
        return DEFAULT_MAX_SNOOZE_COUNT;
    }

    Map<String, Object> parseScenarioConfig(String json) {
        try {
            if (json == null || json.isBlank() || "{}".equals(json.trim())) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String extractRecipient(MxzSignalProcessContext context) {
        if (context.payload() instanceof MxzJsonPayload jp) {
            Object r = jp.fields().get("recipientId");
            if (r != null) return String.valueOf(r);
        }
        return "local-actor";
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
