package cn.net.mxz.timeimprint.task.service.capability.notification.handler;

import cn.net.mxz.timeimprint.task.service.capability.notification.mapper.InboxMapper;
import cn.net.mxz.timeimprint.task.service.capability.notification.row.InboxRow;
import cn.net.mxz.timeimprint.task.service.extension.action.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionExecutionResult;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.spi.ActionHandler;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * LOCAL_TRANSACTIONAL IN_APP handler.
 * handler_key = "in_app_notification", schema_version = 1.
 *
 * Payload fields (MxzJsonPayload):
 *   notificationId, definitionId, instanceId, tenantId, recipientType, recipientId
 */
@Component
public class MxzInAppNotificationHandler implements ActionHandler {

    public static final String HANDLER_KEY = "in_app_notification";
    public static final int SCHEMA_VERSION = 1;

    private final InboxMapper inboxMapper;

    public MxzInAppNotificationHandler(InboxMapper inboxMapper) {
        this.inboxMapper = inboxMapper;
    }

    @Override
    public ActionHandlerKey registrationKey() {
        return new ActionHandlerKey(HANDLER_KEY, SCHEMA_VERSION);
    }

    @Override
    public ActionExecutionMode executionMode() {
        return ActionExecutionMode.LOCAL_TRANSACTIONAL;
    }

    @Override
    public int timeoutSeconds() {
        return 5;
    }

    @Override
    public Set<Integer> supportedSchemaVersions() {
        return Set.of(SCHEMA_VERSION);
    }

    @Override
    public MxzActionExecutionResult execute(MxzActionExecutionContext context) {
        try {
            Map<String, Object> fields = context.payload() instanceof MxzJsonPayload jp
                    ? jp.fields() : Map.of();

            long notificationId = toLong(fields.get("notificationId"));
            long definitionId = toLong(fields.get("definitionId"));
            long instanceId = toLong(fields.get("instanceId"));
            String tenantId = toString(fields.get("tenantId"), "local");
            String recipientType = toString(fields.get("recipientType"), "USER");
            String recipientId = toString(fields.get("recipientId"), "local_actor");

            InboxRow row = new InboxRow();
            row.setTenantId(tenantId);
            row.setNotificationId(notificationId);
            row.setActionJobId(context.actionJobId());
            row.setDefinitionId(definitionId);
            row.setInstanceId(instanceId);
            row.setRecipientType(recipientType);
            row.setRecipientId(recipientId);
            row.setReadAt(null);
            row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));

            try {
                inboxMapper.insert(row);
                return new MxzActionExecutionResult(ActionHandlerOutcome.SUCCEEDED, "INBOX_CREATED", null);
            } catch (RuntimeException insertEx) {
                // At-least-once / dirty-queue safe: unique recipient or action_job already materialised.
                if (isDuplicateKey(insertEx)) {
                    return new MxzActionExecutionResult(
                            ActionHandlerOutcome.SUCCEEDED, "INBOX_ALREADY_EXISTS", null);
                }
                throw insertEx;
            }
        } catch (Exception e) {
            return new MxzActionExecutionResult(ActionHandlerOutcome.PERMANENT_FAILURE,
                    "PAYLOAD_PARSE_ERROR", e.getMessage());
        }
    }

    private static boolean isDuplicateKey(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = c.getMessage();
            if (msg != null
                    && (msg.contains("uk_inbox_notification_recipient")
                            || msg.contains("uk_inbox_action")
                            || msg.contains("Duplicate entry"))) {
                return true;
            }
        }
        return false;
    }

    private long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Expected number, got: " + o);
    }

    private String toString(Object o, String defaultVal) {
        return o != null ? String.valueOf(o) : defaultVal;
    }
}
