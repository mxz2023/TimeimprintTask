package cn.net.mxz.timeimprint.task.service.scenario.deadline.maintenance.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.net.mxz.timeimprint.task.service.extension.materialization.context.ScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
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
class MaintenanceSubjectMaterializerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void syncInsertsSubjectWhenBindingMatches() {
        when(jdbc.queryForObject(contains("COUNT"), eq(Integer.class), eq(4L))).thenReturn(0);
        when(jdbc.queryForObject(contains("config_json"), eq(String.class), eq(4L))).thenReturn(binding("2026-10-14"));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        var materializer = new MaintenanceSubjectMaterializer(jdbc, JsonMapper.builder().build());
        int rows = materializer.materialize(context("sync", null));
        assertEquals(1, rows);
        verify(jdbc).update(contains("tt_maintenance_subject"), any(Object[].class));
    }

    @Test
    void completeAppendsHistoryAndMovesDueDate() {
        when(jdbc.queryForObject(contains("config_json"), eq(String.class), eq(4L))).thenReturn(binding("2026-10-17"));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        var materializer = new MaintenanceSubjectMaterializer(jdbc, JsonMapper.builder().build());
        int rows = materializer.materialize(context("complete", instance()));
        assertEquals(2, rows);
        verify(jdbc).update(contains("tt_maintenance_completion"), any(Object[].class));
    }

    private static String binding(String anchor) {
        return "{\"type\":\"ANCHOR_OFFSETS\",\"anchorDate\":\"" + anchor
                + "\",\"offsetDays\":[0],\"dailyAfterAnchor\":false,\"localTime\":\"09:00:00\",\"zoneId\":\"Asia/Shanghai\"}";
    }

    private static ScenarioDataMaterializationContext context(String action, TaskInstanceSnapshot instance) {
        Instant at = Instant.parse("2026-10-14T01:00:00Z");
        String config = "{\"subjectName\":\"滤芯\",\"intervalDays\":3,\"nextDueDate\":\"2026-10-14\",\"localTime\":\"09:00:00\"}";
        var definition = new TaskDefinitionSnapshot(4L, "local", "maintenance", 1, "换滤芯", "", config,
                ControlState.ACTIVE, 1L, 1L, at, at);
        Map<String, Object> fields = action.equals("complete")
                ? Map.of("action", "complete", "completedOn", "2026-10-14",
                        "previousDueDate", "2026-10-14", "nextDueDate", "2026-10-17")
                : Map.of("action", "sync");
        return new ScenarioDataMaterializationContext(
                definition, instance, 9L, new ScenarioDataMutation("maintenance", "apply", 1, new JsonPayload(fields)));
    }

    private static TaskInstanceSnapshot instance() {
        Instant at = Instant.parse("2026-10-14T01:00:00Z");
        return new TaskInstanceSnapshot(8L, 4L, 1L, 1L, 1L, "occ", at, at,
                LifecycleCategory.TERMINAL, "COMPLETED", 1, "{}", "换滤芯", "", 2L, at);
    }
}
