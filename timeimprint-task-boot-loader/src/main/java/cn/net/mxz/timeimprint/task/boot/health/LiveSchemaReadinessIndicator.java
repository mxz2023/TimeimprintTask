package cn.net.mxz.timeimprint.task.boot.health;

import cn.net.mxz.timeimprint.task.service.extension.shared.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.action.spi.ActionHandler;
import cn.net.mxz.timeimprint.task.service.extension.trigger.spi.TriggerProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A35 / 03 §4：未终结 Signal/Action/Binding 与活跃场景行所用 schemaVersion 必须仍有兼容读取器；
 * 否则 readiness 拒绝流量。纯历史终态不阻塞就绪。
 */
@Component("liveSchema")
public class LiveSchemaReadinessIndicator implements HealthIndicator {

    private final JdbcTemplate jdbc;
    private final ExtensionRegistry extensionRegistry;
    private final List<TriggerProvider> triggerProviders;
    private final List<ActionHandler> actionHandlers;

    public LiveSchemaReadinessIndicator(
            JdbcTemplate jdbc,
            ExtensionRegistry extensionRegistry,
            List<TriggerProvider> triggerProviders,
            List<ActionHandler> actionHandlers) {
        this.jdbc = jdbc;
        this.extensionRegistry = extensionRegistry;
        this.triggerProviders = List.copyOf(triggerProviders);
        this.actionHandlers = List.copyOf(actionHandlers);
    }

    @Override
    public Health health() {
        List<String> missing = new ArrayList<>();
        missing.addAll(checkSignals());
        missing.addAll(checkActions());
        missing.addAll(checkBindings());
        missing.addAll(checkLiveScenarios());
        if (missing.isEmpty()) {
            return Health.up().withDetail("liveSchema", "readable").build();
        }
        return Health.down()
                .withDetail("liveSchema", "unreadable")
                .withDetail("missingReaders", missing)
                .build();
    }

    private List<String> checkSignals() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT DISTINCT provider_key AS k, schema_version AS v
                FROM tt_task_signal
                WHERE process_status IN ('READY','RUNNING','RETRY_WAIT')
                """);
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String provider = String.valueOf(row.get("k"));
            int version = ((Number) row.get("v")).intValue();
            if (!triggerReadable(provider, version)) {
                missing.add("signal:" + provider + "/" + version);
            }
        }
        return missing;
    }

    private List<String> checkActions() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT DISTINCT handler_key AS k, schema_version AS v
                FROM tt_action_job
                WHERE status IN ('READY','RUNNING','RETRY_WAIT')
                """);
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String handler = String.valueOf(row.get("k"));
            int version = ((Number) row.get("v")).intValue();
            if (!actionReadable(handler, version)) {
                missing.add("action:" + handler + "/" + version);
            }
        }
        return missing;
    }

    private List<String> checkBindings() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT DISTINCT provider_key AS k, schema_version AS v
                FROM tt_trigger_binding
                WHERE binding_state IN ('ACTIVE','PAUSED')
                """);
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String provider = String.valueOf(row.get("k"));
            int version = ((Number) row.get("v")).intValue();
            if (!triggerReadable(provider, version)) {
                missing.add("binding:" + provider + "/" + version);
            }
        }
        return missing;
    }

    private List<String> checkLiveScenarios() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT DISTINCT scenario_key AS k, scenario_schema_version AS v
                FROM tt_task_definition
                WHERE control_state IN ('ACTIVE','PAUSED')
                UNION
                SELECT DISTINCT d.scenario_key AS k, i.scenario_schema_version AS v
                FROM tt_task_instance i
                JOIN tt_task_definition d ON d.definition_id = i.definition_id
                WHERE i.lifecycle_category IN ('WAITING','ACTIVE')
                """);
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String scenario = String.valueOf(row.get("k"));
            int version = ((Number) row.get("v")).intValue();
            if (!scenarioReadable(scenario, version)) {
                missing.add("scenario:" + scenario + "/" + version);
            }
        }
        return missing;
    }

    private boolean triggerReadable(String providerKey, int schemaVersion) {
        return triggerProviders.stream()
                .anyMatch(p -> p.registrationKey().providerKey().equals(providerKey)
                        && p.supportedSchemaVersions().contains(schemaVersion));
    }

    private boolean actionReadable(String handlerKey, int schemaVersion) {
        return actionHandlers.stream()
                .anyMatch(h -> h.registrationKey().handlerKey().equals(handlerKey)
                        && (h.registrationKey().actionSchemaVersion() == schemaVersion
                                || h.supportedSchemaVersions().contains(schemaVersion)));
    }

    private boolean scenarioReadable(String scenarioKey, int schemaVersion) {
        // P01 scenarios only ship schemaVersion=1 readers; unregistered keys are unreadable.
        return schemaVersion == 1
                && extensionRegistry.scenarioExtensions().find(new ScenarioExtensionKey(scenarioKey, 1)).isPresent();
    }
}
