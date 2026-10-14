package cn.net.mxz.timeimprint.task.service.scenario.deadline.deadline.materialization;

import cn.net.mxz.timeimprint.task.service.extension.materialization.context.ScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializer;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.deadline.extension.DeadlineScenarioExtension;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.deadline.extension.DeadlineScenarioExtension.DeadlineConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 同步到期主体，并记录续期、过期和取消。 */
@Component
public class DeadlineSubjectMaterializer implements ScenarioDataMaterializer {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public DeadlineSubjectMaterializer(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ScenarioDataMaterializerKey registrationKey() {
        return new ScenarioDataMaterializerKey(DeadlineScenarioExtension.SCENARIO_KEY, "apply", 1);
    }

    @Override
    public int materialize(ScenarioDataMaterializationContext context) {
        String action = "sync";
        if (context.mutation().payload() instanceof JsonPayload json && json.fields().get("action") != null) {
            action = String.valueOf(json.fields().get("action"));
        }
        long definitionId = context.definitionSnapshot().definitionId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        return switch (action) {
            case "expire" -> jdbc.update(
                    "UPDATE tt_deadline_subject SET status='EXPIRED', updated_at=? WHERE definition_id=? AND status='ACTIVE'",
                    now, definitionId);
            case "cancel" -> jdbc.update(
                    "UPDATE tt_deadline_subject SET status='CANCELLED', updated_at=? WHERE definition_id=? AND status='ACTIVE'",
                    now, definitionId);
            default -> sync(definitionId, context.definitionSnapshot().scenarioConfigJson(), now);
        };
    }

    private int sync(long definitionId, String configJson, LocalDateTime now) {
        DeadlineConfig config = DeadlineScenarioExtension.read(jsonMapper.readValue(configJson, new TypeReference<>() {}));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_deadline_subject WHERE definition_id=?", Integer.class, definitionId);
        if (count == null || count == 0) {
            jdbc.update(
                    "INSERT INTO tt_deadline_subject (definition_id, subject_type, due_date, status, local_time, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                    definitionId, config.subjectType(), config.dueDate(), "ACTIVE", config.localTime(), now, now);
            return 1;
        }
        LocalDate previous = jdbc.queryForObject(
                "SELECT due_date FROM tt_deadline_subject WHERE definition_id=?", LocalDate.class, definitionId);
        int rows = 1;
        if (previous != null && !previous.equals(config.dueDate())) {
            jdbc.update(
                    "INSERT INTO tt_deadline_renewal (definition_id, previous_due_date, new_due_date, recorded_at) VALUES (?,?,?,?)",
                    definitionId, previous, config.dueDate(), now);
            rows++;
        }
        jdbc.update(
                "UPDATE tt_deadline_subject SET subject_type=?, due_date=?, local_time=?, status='ACTIVE', updated_at=? WHERE definition_id=?",
                config.subjectType(), config.dueDate(), config.localTime(), now, definitionId);
        return rows;
    }
}
