package cn.net.mxz.timeimprint.task.service.scenario.deadline.deadline.materialization;

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
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DeadlineSubjectMaterializerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void renewalAppendsPreviousDueDate() {
        when(jdbc.queryForObject(contains("COUNT"), eq(Integer.class), eq(4L))).thenReturn(1);
        when(jdbc.queryForObject(contains("due_date"), eq(LocalDate.class), eq(4L)))
                .thenReturn(LocalDate.parse("2026-10-14"));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        String config = "{\"subjectType\":\"CONTRACT\",\"dueDate\":\"2027-10-14\",\"localTime\":\"09:00:00\",\"offsetDays\":[7,0]}";
        int rows = new DeadlineSubjectMaterializer(jdbc, JsonMapper.builder().build()).materialize(context(config));
        assertEquals(2, rows);
        verify(jdbc).update(contains("tt_deadline_renewal"), eq(4L), eq(LocalDate.parse("2026-10-14")),
                eq(LocalDate.parse("2027-10-14")), any());
    }

    private static ScenarioDataMaterializationContext context(String config) {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        var definition = new TaskDefinitionSnapshot(4L, "local", "deadline", 1, "合同", "", config,
                ControlState.ACTIVE, 1L, 1L, now, now);
        return new ScenarioDataMaterializationContext(definition, null, 9L,
                new ScenarioDataMutation("deadline", "apply", 1, new JsonPayload(Map.of("action", "sync"))));
    }
}
