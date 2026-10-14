package cn.net.mxz.timeimprint.task.service.scenario.deadline.anniversary.materialization;

import cn.net.mxz.timeimprint.task.service.extension.materialization.context.ScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializer;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.anniversary.extension.AnniversaryScenarioExtension;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.anniversary.extension.AnniversaryScenarioExtension.AnniversaryConfig;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 把纪念日配置写入专有表，并在月日变化时追加修订。 */
@Component
public class AnniversarySubjectMaterializer implements ScenarioDataMaterializer {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public AnniversarySubjectMaterializer(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ScenarioDataMaterializerKey registrationKey() {
        return new ScenarioDataMaterializerKey(AnniversaryScenarioExtension.SCENARIO_KEY, "sync_subject", 1);
    }

    @Override
    public int materialize(ScenarioDataMaterializationContext context) {
        long definitionId = context.definitionSnapshot().definitionId();
        AnniversaryConfig config = AnniversaryScenarioExtension.read(readConfig(context.definitionSnapshot().scenarioConfigJson()));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_anniversary_subject WHERE definition_id = ?",
                Integer.class,
                definitionId);
        if (count == null || count == 0) {
            jdbc.update(
                    "INSERT INTO tt_anniversary_subject (definition_id, kind, subject_name, month_value, day_value, start_year, local_time, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    definitionId,
                    config.kind(),
                    config.name(),
                    config.month(),
                    config.day(),
                    config.startYear(),
                    config.localTime(),
                    now,
                    now);
            return 1;
        }
        Map<String, Object> old = jdbc.queryForMap(
                "SELECT month_value, day_value, start_year FROM tt_anniversary_subject WHERE definition_id = ?",
                definitionId);
        int rows = 1;
        if (asInt(old.get("month_value")) != config.month()
                || asInt(old.get("day_value")) != config.day()
                || !sameYear(old.get("start_year"), config.startYear())) {
            jdbc.update(
                    "INSERT INTO tt_anniversary_revision (definition_id, month_value, day_value, start_year, recorded_at) VALUES (?,?,?,?,?)",
                    definitionId,
                    asInt(old.get("month_value")),
                    asInt(old.get("day_value")),
                    old.get("start_year"),
                    now);
            rows++;
        }
        jdbc.update(
                "UPDATE tt_anniversary_subject SET kind=?, subject_name=?, month_value=?, day_value=?, start_year=?, local_time=?, updated_at=? WHERE definition_id=?",
                config.kind(),
                config.name(),
                config.month(),
                config.day(),
                config.startYear(),
                config.localTime(),
                now,
                definitionId);
        return rows;
    }

    private Map<String, Object> readConfig(String json) {
        return jsonMapper.readValue(json, new TypeReference<>() {});
    }

    private static boolean sameYear(Object stored, Integer next) {
        if (stored == null) {
            return next == null;
        }
        return next != null && asInt(stored) == next;
    }

    private static int asInt(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }
}
