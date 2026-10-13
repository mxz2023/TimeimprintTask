package cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.materialization;

import cn.net.mxz.timeimprint.task.service.extension.materialization.context.ScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializer;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.extension.PaymentScenarioExtension;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.extension.PaymentScenarioExtension.PaymentConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 写入缴费主体和入账、冲正、取消事件。 */
@Component
public class PaymentSubjectMaterializer implements ScenarioDataMaterializer {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public PaymentSubjectMaterializer(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ScenarioDataMaterializerKey registrationKey() {
        return new ScenarioDataMaterializerKey(PaymentScenarioExtension.SCENARIO_KEY, "apply", 1);
    }

    @Override
    public int materialize(ScenarioDataMaterializationContext context) {
        Map<String, Object> fields = context.mutation().payload() instanceof JsonPayload json ? json.fields() : Map.of();
        String action = String.valueOf(fields.getOrDefault("action", "sync"));
        long definitionId = context.definitionSnapshot().definitionId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        return switch (action) {
            case "confirm" -> writeConfirm(definitionId, fields, now);
            case "reverse" -> reverse(definitionId, fields, now);
            case "cancel" -> writeEventAndStatus(definitionId, "CANCEL", 0, "CANCELLED", fields, now);
            case "overdue" -> jdbc.update(
                    "UPDATE tt_payment_subject SET status='OVERDUE', updated_at=? WHERE definition_id=? AND status IN ('UNPAID','PARTIAL')",
                    now, definitionId);
            default -> sync(definitionId, context.definitionSnapshot().scenarioConfigJson(), now);
        };
    }

    private int writeConfirm(long definitionId, Map<String, Object> fields, LocalDateTime now) {
        jdbc.update(
                "UPDATE tt_payment_subject SET paid_fen=?, status=?, updated_at=? WHERE definition_id=?",
                asLong(fields.get("paidFen")),
                String.valueOf(fields.get("status")),
                now,
                definitionId);
        jdbc.update(
                "INSERT INTO tt_payment_event (definition_id, event_type, amount_fen, request_id, recorded_at) VALUES (?,?,?,?,?)",
                definitionId, "CONFIRM", asLong(fields.get("amountFen")), String.valueOf(fields.get("requestId")), now);
        return 2;
    }

    private int writeEventAndStatus(
            long definitionId, String event, long paid, String status, Map<String, Object> fields, LocalDateTime now) {
        jdbc.update(
                "UPDATE tt_payment_subject SET paid_fen=?, status=?, updated_at=? WHERE definition_id=?",
                paid, status, now, definitionId);
        jdbc.update(
                "INSERT INTO tt_payment_event (definition_id, event_type, amount_fen, request_id, recorded_at) VALUES (?,?,?,?,?)",
                definitionId, event, 0, String.valueOf(fields.get("requestId")), now);
        return 2;
    }

    private int reverse(long definitionId, Map<String, Object> fields, LocalDateTime now) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT due_date, local_time FROM tt_payment_subject WHERE definition_id=?", definitionId);
        LocalDate due = row.get("due_date") instanceof LocalDate date
                ? date
                : LocalDate.parse(String.valueOf(row.get("due_date")).substring(0, 10));
        LocalTime time = LocalTime.parse(String.valueOf(row.get("local_time")));
        boolean late = LocalDateTime.now(CalendarOccurrenceCalculator.BUSINESS_ZONE).isAfter(due.atTime(time));
        return writeEventAndStatus(definitionId, "REVERSE", 0, late ? "OVERDUE" : "UNPAID", fields, now);
    }

    private int sync(long definitionId, String configJson, LocalDateTime now) {
        PaymentConfig config = PaymentScenarioExtension.read(jsonMapper.readValue(configJson, new TypeReference<>() {}));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_payment_subject WHERE definition_id=?", Integer.class, definitionId);
        if (count == null || count == 0) {
            jdbc.update(
                    "INSERT INTO tt_payment_subject (definition_id, amount_fen, paid_fen, period_label, due_date, status, local_time, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    definitionId, config.amountFen(), 0, config.periodLabel(), config.dueDate(), "UNPAID", config.localTime(), now, now);
            return 1;
        }
        Map<String, Object> existing = jdbc.queryForMap(
                "SELECT amount_fen, paid_fen FROM tt_payment_subject WHERE definition_id=?", definitionId);
        if (asLong(existing.get("paid_fen")) > 0 && asLong(existing.get("amount_fen")) != config.amountFen()) {
            throw new IllegalArgumentException("amountFen cannot change after payment");
        }
        jdbc.update(
                "UPDATE tt_payment_subject SET amount_fen=?, period_label=?, due_date=?, local_time=?, updated_at=? WHERE definition_id=?",
                config.amountFen(), config.periodLabel(), config.dueDate(), config.localTime(), now, definitionId);
        return 1;
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }
}
