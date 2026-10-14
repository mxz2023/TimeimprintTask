package cn.net.mxz.timeimprint.task.service.scenario.deadline.anniversary.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.net.mxz.timeimprint.task.service.extension.materialization.context.ScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AnniversarySubjectMaterializerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void recordsPreviousMonthDayBeforeUpdate() {
        when(jdbc.queryForObject(contains("COUNT"), eq(Integer.class), eq(3L))).thenReturn(1);
        when(jdbc.queryForMap(contains("SELECT month_value"), eq(3L)))
                .thenReturn(Map.of("month_value", 10, "day_value", 14, "start_year", 2000));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        String config = "{\"kind\":\"BIRTHDAY\",\"subjectName\":\"林安\",\"month\":11,\"dayOfMonth\":2,"
                + "\"localTime\":\"09:00:00\",\"startYear\":2000}";
        var materializer = new AnniversarySubjectMaterializer(jdbc, JsonMapper.builder().build());
        int rows = materializer.materialize(context(config));
        assertEquals(2, rows);
        verify(jdbc).update(contains("tt_anniversary_revision"), eq(3L), eq(10), eq(14), eq(2000), any());
    }

    private static ScenarioDataMaterializationContext context(String config) {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        var definition = new TaskDefinitionSnapshot(3L, "local", "anniversary", 1, "林安", "", config,
                ControlState.ACTIVE, 1L, 1L, now, now);
        return new ScenarioDataMaterializationContext(
                definition, null, 9L, new ScenarioDataMutation("anniversary", "sync_subject", 1, new JsonPayload(Map.of())));
    }
}
