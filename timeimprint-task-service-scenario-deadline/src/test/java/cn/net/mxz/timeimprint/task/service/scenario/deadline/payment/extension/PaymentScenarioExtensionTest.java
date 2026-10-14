package cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentScenarioExtensionTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void partialPaymentKeepsRemainder() {
        assertEquals("PARTIAL", PaymentScenarioExtension.confirm(10_000, 4_000, 1_000).status());
    }

    @Test
    void overpayIsRejectedWhole() {
        assertThrows(IllegalArgumentException.class, () -> PaymentScenarioExtension.confirm(10_000, 9_000, 2_000));
    }

    @Test
    void noticeIncludesPaidYuan() {
        when(jdbc.queryForMap(contains("paid_fen"), eq(5L))).thenReturn(Map.of("paid_fen", 2500L, "status", "PARTIAL"));
        Instant occurrence = Instant.parse("2026-10-13T01:00:00Z");
        String config = "{\"amountFen\":10000,\"periodLabel\":\"2026-10房租\",\"dueDate\":\"2026-10-14\","
                + "\"localTime\":\"09:00:00\",\"offsetDays\":[1,0]}";
        var definition = new TaskDefinitionSnapshot(5L, "local", "payment", 1, "房租", "", config,
                ControlState.ACTIVE, 1L, 1L, occurrence, occurrence);
        var instance = new TaskInstanceSnapshot(9L, 5L, 1L, 1L, 1L, "occ", occurrence, occurrence,
                LifecycleCategory.WAITING, "PLANNED", 1, "{}", "房租", "", 1L, null);
        var applied = assertInstanceOf(HandlerResult.Applied.class, new PaymentScenarioExtension(jdbc).processSignal(
                new SignalProcessContext(definition, instance, 1L, "calendar", "primary", 1, new JsonPayload(Map.of()))));
        String body = String.valueOf(applied.plan().actionJobIntents().getFirst().payload());
        assertTrue(body.contains("应付 100.00 元，已付 25.00 元"), body);
    }
}
