package cn.net.mxz.timeimprint.task.service.scenario.deadline.maintenance.materialization;

import cn.net.mxz.timeimprint.task.service.extension.materialization.context.ScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializer;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.maintenance.extension.MaintenanceScenarioExtension;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.maintenance.extension.MaintenanceScenarioExtension.MaintenanceConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 同步保养主体，并在完成时追加一条历史。 */
@Component
public class MaintenanceSubjectMaterializer implements ScenarioDataMaterializer {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public MaintenanceSubjectMaterializer(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ScenarioDataMaterializerKey registrationKey() {
        return new ScenarioDataMaterializerKey(MaintenanceScenarioExtension.SCENARIO_KEY, "apply", 1);
    }

    @Override
    public int materialize(ScenarioDataMaterializationContext context) {
        String action = "sync";
        Map<String, Object> fields = Map.of();
        if (context.mutation().payload() instanceof JsonPayload json) {
            fields = json.fields();
            if (json.fields().get("action") != null) {
                action = String.valueOf(json.fields().get("action"));
            }
        }
        long definitionId = context.definitionSnapshot().definitionId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        if ("complete".equals(action)) {
            return complete(definitionId, context.instanceSnapshot().instanceId(), fields, now);
        }
        return sync(definitionId, context.definitionSnapshot().scenarioConfigJson(), now);
    }

    private int complete(long definitionId, long instanceId, Map<String, Object> fields, LocalDateTime now) {
        LocalDate completedOn = LocalDate.parse(String.valueOf(fields.get("completedOn")));
        LocalDate previous = LocalDate.parse(String.valueOf(fields.get("previousDueDate")));
        LocalDate next = LocalDate.parse(String.valueOf(fields.get("nextDueDate")));
        jdbc.update(
                "INSERT INTO tt_maintenance_completion (definition_id, instance_id, completed_on, previous_due_date, next_due_date, recorded_at) VALUES (?,?,?,?,?,?)",
                definitionId, instanceId, completedOn, previous, next, now);
        jdbc.update(
                "UPDATE tt_maintenance_subject SET next_due_date=?, updated_at=? WHERE definition_id=?",
                next, now, definitionId);
        assertBinding(definitionId, next, null);
        return 2;
    }

    private int sync(long definitionId, String configJson, LocalDateTime now) {
        MaintenanceConfig config = MaintenanceScenarioExtension.read(
                jsonMapper.readValue(configJson, new TypeReference<>() {}));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_maintenance_subject WHERE definition_id=?", Integer.class, definitionId);
        if (count == null || count == 0) {
            jdbc.update(
                    "INSERT INTO tt_maintenance_subject (definition_id, subject_name, interval_days, next_due_date, status, local_time, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                    definitionId, config.subjectName(), config.intervalDays(), config.nextDueDate(),
                    "ACTIVE", config.localTime(), now, now);
            assertBinding(definitionId, config.nextDueDate(), config.localTime());
            return 1;
        }
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT interval_days, next_due_date FROM tt_maintenance_subject WHERE definition_id=?", definitionId);
        int storedInterval = row.get("interval_days") instanceof Number n
                ? n.intValue()
                : Integer.parseInt(String.valueOf(row.get("interval_days")));
        LocalDate storedDue = row.get("next_due_date") instanceof LocalDate date
                ? date
                : LocalDate.parse(String.valueOf(row.get("next_due_date")));
        if (storedInterval != config.intervalDays() || !storedDue.equals(config.nextDueDate())) {
            throw new IllegalArgumentException("nextDueDate and intervalDays change only through complete");
        }
        jdbc.update(
                "UPDATE tt_maintenance_subject SET subject_name=?, local_time=?, updated_at=? WHERE definition_id=?",
                config.subjectName(), config.localTime(), now, definitionId);
        assertBinding(definitionId, storedDue, config.localTime());
        return 1;
    }

    private void assertBinding(long definitionId, LocalDate anchor, String localTime) {
        String json = jdbc.queryForObject(
                "SELECT config_json FROM tt_trigger_binding WHERE definition_id=? AND binding_key='primary'",
                String.class, definitionId);
        Map<String, Object> config = jsonMapper.readValue(json, new TypeReference<>() {});
        if (!"ANCHOR_OFFSETS".equals(String.valueOf(config.get("type")))
                || !anchor.toString().equals(String.valueOf(config.get("anchorDate")))
                || Boolean.TRUE.equals(config.get("dailyAfterAnchor"))
                || !(config.get("offsetDays") instanceof List<?> offsets)
                || offsets.size() != 1
                || intValue(offsets.get(0)) != 0) {
            throw new IllegalArgumentException("maintenance binding must be a single zero offset on nextDueDate");
        }
        if (localTime != null && !localTime.equals(String.valueOf(config.get("localTime")))) {
            throw new IllegalArgumentException("binding localTime must match scenarioConfig");
        }
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }
}
