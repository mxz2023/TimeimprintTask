package cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
class PaymentSubjectMaterializerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void reverseBeforeDueReturnsUnpaid() {
        when(jdbc.queryForMap(contains("due_date"), eq(5L)))
                .thenReturn(Map.of("due_date", LocalDate.parse("2099-01-01"), "local_time", "09:00:00"));
        var materializer = new PaymentSubjectMaterializer(jdbc, JsonMapper.builder().build());
        assertEquals(2, materializer.materialize(context(Map.of("action", "reverse", "requestId", "req-rev"), "{}")));
        verify(jdbc).update(contains("status=?"), eq(0L), eq("UNPAID"), any(), eq(5L));
    }

    @Test
    void amountChangeAfterPaymentIsRejected() {
        when(jdbc.queryForObject(contains("COUNT"), eq(Integer.class), eq(5L))).thenReturn(1);
        when(jdbc.queryForMap(contains("amount_fen"), eq(5L)))
                .thenReturn(Map.of("amount_fen", 10000L, "paid_fen", 100L));
        String config = "{\"amountFen\":20000,\"periodLabel\":\"2026-10房租\",\"dueDate\":\"2026-10-14\","
                + "\"localTime\":\"09:00:00\",\"offsetDays\":[0]}";
        var materializer = new PaymentSubjectMaterializer(jdbc, JsonMapper.builder().build());
        assertThrows(IllegalArgumentException.class, () -> materializer.materialize(context(Map.of("action", "sync"), config)));
    }

    private static ScenarioDataMaterializationContext context(Map<String, Object> mutation, String config) {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        var definition = new TaskDefinitionSnapshot(5L, "local", "payment", 1, "房租", "", config,
                ControlState.ACTIVE, 1L, 1L, now, now);
        return new ScenarioDataMaterializationContext(definition, null, 9L,
                new ScenarioDataMutation("payment", "apply", 1, new JsonPayload(mutation)));
    }
}
