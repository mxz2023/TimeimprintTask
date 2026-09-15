package cn.net.mxz.timeimprint.task.boot.fixture;

import cn.net.mxz.timeimprint.task.service.extension.context.ScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.extension.spi.ScenarioDataMaterializer;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.JsonPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * T07 test fixture: writes to test-only table tt_test_approval_data within the same
 * transition transaction. Proves that ScenarioDataMutation + registered materializer
 * pattern works without touching kernel production sources or public Flyway DDL.
 *
 * This class lives in boot-loader test sources only.
 */
@Component
public class ApprovalDataMaterializer implements ScenarioDataMaterializer {

    private static final String INSERT_SQL =
            "INSERT INTO tt_test_approval_data (definition_id, transition_id, approval_meta_json, created_at) "
            + "VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ApprovalDataMaterializer(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScenarioDataMaterializerKey registrationKey() {
        return new ScenarioDataMaterializerKey(
                ApprovalFixture.SCENARIO_KEY, ApprovalFixture.MUTATION_KEY, ApprovalFixture.CONTRACT_VERSION);
    }

    @Override
    public int materialize(ScenarioDataMaterializationContext context) {
        var def = context.definitionSnapshot();
        String metaJson;
        try {
            Map<String, Object> fields = context.mutation().payload() instanceof JsonPayload jp
                    ? jp.fields() : Map.of();
            metaJson = objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            metaJson = "{}";
        }
        jdbcTemplate.update(INSERT_SQL,
                def.definitionId(),
                context.transitionId(),
                metaJson,
                LocalDateTime.now(ZoneOffset.UTC));
        return 1;
    }
}
