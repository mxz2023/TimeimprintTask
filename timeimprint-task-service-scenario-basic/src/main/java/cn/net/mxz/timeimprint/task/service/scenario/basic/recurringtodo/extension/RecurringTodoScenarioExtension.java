package cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo.extension;

import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.DefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.InitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.scenario.context.ScenarioExtensionDescriptor;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.extension.scenario.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
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
 * Instance commands: complete / skip / snooze routed via RecurringTodoCommandHandler.
 * scenarioKey = "recurring_todo", contractVersion = 1.
 */
@Component
public class RecurringTodoScenarioExtension implements ScenarioExtension {

    public static final String SCENARIO_KEY = "recurring_todo";
    public static final int CONTRACT_VERSION = 1;
    public static final String MUTATION_REPLACE_INSTANCE_SNAPSHOT = "REPLACE_INSTANCE_SNAPSHOT";

    // Default config values per S02 contract
    static final List<Integer> DEFAULT_CHASE_OFFSETS = List.of(60, 240, 720);
    static final int DEFAULT_NOTIFICATION_EXPIRE_MINUTES = 1440;
    /**
     * 渠道中立的展开键：场景只声明「待展开的通知意图」，由 application 按已启用渠道展开为具体 Handler。
     * 取值与站内信历史 handlerKey 一致，以保持既有 actionKey 不变；场景不依赖 notification/adapter 模块。
     */
    private static final String NEUTRAL_NOTIFICATION_HANDLER_KEY = "in_app_notification";
    private static final int NEUTRAL_NOTIFICATION_SCHEMA_VERSION = 1;
    public static final int DEFAULT_MAX_SNOOZE_COUNT = 3;

    private final JsonMapper objectMapper;

    public RecurringTodoScenarioExtension(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ScenarioExtensionKey registrationKey() {
        return new ScenarioExtensionKey(SCENARIO_KEY, CONTRACT_VERSION);
    }

    @Override
    public ScenarioExtensionDescriptor descriptor() {
        return new ScenarioExtensionDescriptor(
                SCENARIO_KEY,
                CONTRACT_VERSION,
                List.of("complete", "skip", "snooze"),
                List.of("calendar", "notification"));
    }

    @Override
    public void validateDefinitionConfig(DefinitionConfigValidationContext context) {
        if (!(context.scenarioConfig() instanceof JsonPayload(Map<String, Object> fields))) {
            throw new IllegalArgumentException("scenarioConfig must be object");
        }
        expandDefaults(fields);
    }

    /**
     * Expand omitted S02 defaults and validate schemaVersion 1 rule.
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
                raw.getOrDefault("notificationExpireAfterMinutes", DEFAULT_NOTIFICATION_EXPIRE_MINUTES));
        out.put(
                "maxSnoozeCount",
                raw.getOrDefault("maxSnoozeCount", DEFAULT_MAX_SNOOZE_COUNT));
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
    public HandlerResult planInitialDefinition(InitialDefinitionContext context) {
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

        Map<String, Object> scenarioConfig = parseScenarioConfig(context.definitionSnapshot().scenarioConfigJson());
        List<Integer> chaseOffsets = getChaseOffsets(scenarioConfig);
        int expireMinutes = getExpireMinutes(scenarioConfig);
        int maxSnooze = getMaxSnoozeCount(scenarioConfig);

        Instant dueAt = instSnapshot.dueAt() != null ? instSnapshot.dueAt()
                : (instSnapshot.occurrenceAt() != null ? instSnapshot.occurrenceAt() : Instant.now());
        Instant expiresAt = dueAt.plus(expireMinutes, ChronoUnit.MINUTES);

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
        String titleSnapshot = instSnapshot.titleSnapshot();
        String body = notificationBody(instSnapshot.descriptionSnapshot());
        List<ActionJobIntent> actions = new ArrayList<>();
        actions.add(buildExpandableActionIntent(
                definitionId, instSnapshot.instanceId(), "INITIAL", 0, 1, dueAt, expiresAt,
                notificationTitle("INITIAL", titleSnapshot), body));

        for (int i = 0; i < chaseOffsets.size(); i++) {
            Instant chaseAt = dueAt.plus(chaseOffsets.get(i), ChronoUnit.MINUTES);
            if (chaseAt.isBefore(expiresAt)) {
                actions.add(buildExpandableActionIntent(
                        definitionId, instSnapshot.instanceId(), "CHASE", i + 1, 1, chaseAt, expiresAt,
                        notificationTitle("CHASE", titleSnapshot), body));
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

    public static ScenarioDataMutation replaceSnapshotMutation(Map<String, Object> snapshot) {
        return new ScenarioDataMutation(
                SCENARIO_KEY,
                MUTATION_REPLACE_INSTANCE_SNAPSHOT,
                1,
                new JsonPayload(snapshot));
    }

    /**
     * INITIAL uses the instance title snapshot. CHASE prefixes the same snapshot with
     * the full-width 「催办：」 and no space. An empty description becomes an empty body.
     */
    static String notificationTitle(String purpose, String titleSnapshot) {
        String title = titleSnapshot == null ? "" : titleSnapshot;
        if ("CHASE".equals(purpose)) {
            return "催办：" + title;
        }
        return title;
    }

    static String notificationBody(String descriptionSnapshot) {
        return descriptionSnapshot == null ? "" : descriptionSnapshot;
    }

    /** Template Action; platform expands to one job per final recipient. */
    static ActionJobIntent buildExpandableActionIntent(
            long definitionId,
            long instanceId,
            String purpose,
            int slotIndex,
            int actionGeneration,
            Instant availableAt,
            Instant expiresAt,
            String title,
            String body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", purpose);
        payload.put("slotIndex", slotIndex);
        payload.put("actionGeneration", actionGeneration);
        payload.put("definitionId", definitionId);
        payload.put("instanceId", instanceId);
        payload.put("title", title);
        payload.put("body", body);

        return new ActionJobIntent(
                NEUTRAL_NOTIFICATION_HANDLER_KEY,
                NEUTRAL_NOTIFICATION_SCHEMA_VERSION,
                purpose + ":PENDING_EXPAND",
                "LOCAL_TRANSACTIONAL",
                null,
                null,
                availableAt,
                expiresAt,
                new JsonPayload(payload));
    }

    public static ActionJobIntent buildActionIntent(
            long definitionId, long instanceId, String purpose, int slotIndex, int actionGeneration,
            String recipientId, Instant availableAt, Instant expiresAt,
            String title, String body) {

        String canon = instanceId + ":" + purpose + ":" + slotIndex + ":" + actionGeneration
                + ":" + recipientId + ":" + NEUTRAL_NOTIFICATION_HANDLER_KEY;
        String actionKey = purpose + ":" + base64Url(Sha256.digestUtf8(canon));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", purpose);
        payload.put("slotIndex", slotIndex);
        payload.put("actionGeneration", actionGeneration);
        payload.put("definitionId", definitionId);
        payload.put("instanceId", instanceId);
        payload.put("recipientType", "USER");
        payload.put("recipientId", recipientId);
        payload.put("title", title == null ? "" : title);
        payload.put("body", body == null ? "" : body);

        return new ActionJobIntent(
                NEUTRAL_NOTIFICATION_HANDLER_KEY,
                NEUTRAL_NOTIFICATION_SCHEMA_VERSION,
                actionKey,
                "LOCAL_TRANSACTIONAL",
                "USER",
                recipientId,
                availableAt,
                expiresAt,
                new JsonPayload(payload));
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

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
