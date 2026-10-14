package cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.command;

import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.extension.PaymentScenarioExtension;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.extension.PaymentScenarioExtension.ConfirmResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 累加已付金额；付清时退役定义。 */
@Component
public class PaymentConfirmHandler implements TaskCommandHandler {

    private final JdbcTemplate jdbc;

    public PaymentConfirmHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TaskCommandHandlerKey registrationKey() {
        return new TaskCommandHandlerKey(PaymentScenarioExtension.SCENARIO_KEY, CommandScope.INSTANCE, "confirmPayment", 1);
    }

    @Override
    public HandlerResult handle(CommandExecutionContext context) {
        if (!(context.commandPayload() instanceof JsonPayload json) || !json.fields().containsKey("amountFen")) {
            return new HandlerResult.Rejected("INVALID_REQUEST", "confirmPayment requires amountFen");
        }
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT amount_fen, paid_fen, status FROM tt_payment_subject WHERE definition_id=?",
                context.definitionSnapshot().definitionId());
        String status = String.valueOf(row.get("status"));
        if ("PAID".equals(status) || "CANCELLED".equals(status)) {
            return new HandlerResult.Rejected("INVALID_STATE", "payment is closed");
        }
        try {
            long add = json.fields().get("amountFen") instanceof Number n
                    ? n.longValue()
                    : Long.parseLong(String.valueOf(json.fields().get("amountFen")));
            long amount = row.get("amount_fen") instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(row.get("amount_fen")));
            long paid = row.get("paid_fen") instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(row.get("paid_fen")));
            ConfirmResult result = PaymentScenarioExtension.confirm(amount, paid, add);
            Map<String, Object> mutation = new LinkedHashMap<>();
            mutation.put("action", "confirm");
            mutation.put("paidFen", result.paidFen());
            mutation.put("status", result.status());
            mutation.put("amountFen", add);
            mutation.put("requestId", context.requestId());
            ControlState to = "PAID".equals(result.status()) ? ControlState.RETIRED : context.definitionSnapshot().controlState();
            return PaymentScenarioExtension.controlPlan(
                    context.definitionSnapshot().controlState(),
                    to,
                    context.instanceSnapshot().instanceId(),
                    context.instanceSnapshot().revision(),
                    mutation);
        } catch (IllegalArgumentException ex) {
            return new HandlerResult.Rejected("INVALID_REQUEST", ex.getMessage());
        }
    }
}
