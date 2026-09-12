package cn.net.mxz.timeimprint.task.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.domain.request.CreateTaskDefinitionRequest;
import cn.net.mxz.timeimprint.task.domain.request.DefinitionCommandRequest;
import cn.net.mxz.timeimprint.task.domain.request.InstanceCommandRequest;
import cn.net.mxz.timeimprint.task.domain.request.InternalSignalRequest;
import cn.net.mxz.timeimprint.task.domain.request.MarkReadRequest;
import cn.net.mxz.timeimprint.task.domain.request.ParticipantInput;
import cn.net.mxz.timeimprint.task.domain.request.PreviewRequest;
import cn.net.mxz.timeimprint.task.domain.request.RedriveRequest;
import cn.net.mxz.timeimprint.task.domain.request.SignalSubjectInput;
import cn.net.mxz.timeimprint.task.domain.request.TriggerBindingInput;
import cn.net.mxz.timeimprint.task.domain.view.ActionJobDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.AttemptSummary;
import cn.net.mxz.timeimprint.task.domain.view.CommandMetadataView;
import cn.net.mxz.timeimprint.task.domain.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.view.DeliverySummary;
import cn.net.mxz.timeimprint.task.domain.view.InboxView;
import cn.net.mxz.timeimprint.task.domain.view.OccurrenceView;
import cn.net.mxz.timeimprint.task.domain.view.Page;
import cn.net.mxz.timeimprint.task.domain.view.ParticipantView;
import cn.net.mxz.timeimprint.task.domain.view.PreviewResult;
import cn.net.mxz.timeimprint.task.domain.view.ScenarioMetadataView;
import cn.net.mxz.timeimprint.task.domain.view.SignalAcceptedView;
import cn.net.mxz.timeimprint.task.domain.view.SignalDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.TaskDefinitionView;
import cn.net.mxz.timeimprint.task.domain.view.TaskInstanceView;
import cn.net.mxz.timeimprint.task.domain.view.TransitionDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.TriggerBindingView;
import cn.net.mxz.timeimprint.task.domain.view.UnreadCountView;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Compile-time contract guard for {@code docs/04-API.md} JSON field names.
 */
class MxzApiContractTest {

    @Test
    void envelopeMatchesSection2() {
        assertRecordFields(
                MxzApiResponse.class,
                "code",
                "message",
                "traceId",
                "data");
    }

    @Test
    void errorCodesMatchSection7() {
        Set<String> expected = Set.of(
                "OK",
                "INVALID_REQUEST",
                "INVALID_CURSOR",
                "UNSUPPORTED_SCHEMA_VERSION",
                "UNAUTHENTICATED",
                "FORBIDDEN",
                "RESOURCE_NOT_FOUND",
                "EXTENSION_NOT_FOUND",
                "IDEMPOTENCY_CONFLICT",
                "REVISION_CONFLICT",
                "STATE_CONFLICT",
                "COMMAND_NOT_SUPPORTED",
                "REQUEST_TOO_LARGE",
                "UNSUPPORTED_MEDIA_TYPE",
                "POLICY_REJECTED",
                "INTERNAL_ERROR",
                "RETRY_LATER");
        Set<String> actual = Arrays.stream(MxzApiErrorCodes.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(expected, actual);
    }

    @Test
    void publicViewsMatchSection35() {
        assertRecordFields(
                ScenarioMetadataView.class,
                "scenarioKey",
                "displayName",
                "contractVersion",
                "supportedScenarioSchemaVersions",
                "definitionCommands",
                "instanceCommands",
                "requiredCapabilities");
        assertRecordFields(CommandMetadataView.class, "commandKey", "supportedCommandSchemaVersions");
        assertRecordFields(
                ParticipantView.class, "principalType", "principalId", "roleCode", "sourceCode");
        assertRecordFields(
                TriggerBindingView.class,
                "bindingId",
                "bindingKey",
                "providerKey",
                "schemaVersion",
                "config",
                "bindingState",
                "scheduleGeneration",
                "nextFireAt",
                "exhausted",
                "revision");
        assertRecordFields(
                TaskDefinitionView.class,
                "definitionId",
                "scenarioKey",
                "scenarioSchemaVersion",
                "title",
                "description",
                "controlState",
                "controlGeneration",
                "revision",
                "participants",
                "triggerBindings",
                "scenarioConfig",
                "allowedCommands",
                "createdAt",
                "updatedAt",
                "pausedAt",
                "retiredAt");
        assertRecordFields(
                TaskInstanceView.class,
                "instanceId",
                "definitionId",
                "scenarioKey",
                "scenarioSchemaVersion",
                "lifecycleCategory",
                "scenarioState",
                "revision",
                "titleSnapshot",
                "descriptionSnapshot",
                "participants",
                "occurrenceAt",
                "dueAt",
                "allowedCommands",
                "scenarioProjection",
                "deliverySummary",
                "createdAt",
                "updatedAt",
                "terminalAt");
        assertRecordFields(
                DeliverySummary.class,
                "deliveryState",
                "totalCount",
                "readyCount",
                "runningCount",
                "retryWaitCount",
                "succeededCount",
                "deadCount",
                "cancelledCount",
                "expiredCount",
                "unknownCount",
                "inboxCount",
                "unreadInboxCount",
                "updatedAt");
        assertRecordFields(
                CommandResultView.class,
                "resourceType",
                "resourceId",
                "resourceRevision",
                "changed",
                "resourceSnapshot",
                "scenarioResult");
        assertRecordFields(
                InboxView.class,
                "inboxId",
                "notificationId",
                "actionJobId",
                "definitionId",
                "instanceId",
                "scenarioKey",
                "purpose",
                "title",
                "body",
                "readAt",
                "createdAt");
        assertRecordFields(
                PreviewResult.class,
                "scenarioKey",
                "scenarioSchemaVersion",
                "normalizedTriggerBindings",
                "normalizedScenarioConfig",
                "occurrences");
        assertRecordFields(OccurrenceView.class, "occurrenceKey", "occurrenceAt", "dueAt");
        assertRecordFields(Page.class, "items", "nextCursor", "hasMore", "asOf");
        assertRecordFields(
                UnreadCountView.class, "unreadCount", "asOf");
    }

    @Test
    void internalViewsMatchSection35() {
        assertRecordFields(
                SignalAcceptedView.class, "signalId", "duplicated", "processStatus", "receivedAt");
        assertRecordFields(
                SignalDiagnosticView.class,
                "signalId",
                "definitionId",
                "instanceId",
                "providerKey",
                "signalKey",
                "schemaVersion",
                "processStatus",
                "attemptCount",
                "maxAttempts",
                "nextAttemptAt",
                "leaseOwner",
                "leaseUntil",
                "resultCode",
                "resultSummary",
                "occurredAt",
                "receivedAt",
                "processedAt",
                "parentSignalId",
                "redriveNo");
        assertRecordFields(
                ActionJobDiagnosticView.class,
                "actionJobId",
                "definitionId",
                "instanceId",
                "transitionId",
                "handlerKey",
                "executionMode",
                "schemaVersion",
                "targetType",
                "storedStatus",
                "effectiveStatus",
                "attemptCount",
                "maxAttempts",
                "availableAt",
                "expiresAt",
                "nextAttemptAt",
                "leaseOwner",
                "leaseUntil",
                "outcomeCode",
                "outcomeSummary",
                "completedAt",
                "parentActionJobId",
                "redriveNo",
                "attempts");
        assertRecordFields(
                AttemptSummary.class,
                "attemptNo",
                "startedAt",
                "finishedAt",
                "effectStarted",
                "outcome",
                "errorClass",
                "errorCode",
                "providerReference",
                "safeSummary");
        assertRecordFields(
                TransitionDiagnosticView.class,
                "transitionId",
                "definitionId",
                "instanceId",
                "sourceType",
                "sourceKey",
                "commandKey",
                "fromControlState",
                "toControlState",
                "fromLifecycle",
                "toLifecycle",
                "fromScenarioState",
                "toScenarioState",
                "fromRevision",
                "toRevision",
                "actorType",
                "actorId",
                "traceId",
                "createdAt");
    }

    @Test
    void requestsMatchSection3And5() {
        assertRecordFields(
                CreateTaskDefinitionRequest.class,
                "requestId",
                "scenarioKey",
                "scenarioSchemaVersion",
                "title",
                "description",
                "participants",
                "triggerBindings",
                "scenarioConfig");
        assertRecordFields(
                DefinitionCommandRequest.class,
                "requestId",
                "expectedRevision",
                "commandSchemaVersion",
                "payload");
        assertRecordFields(
                InstanceCommandRequest.class,
                "requestId",
                "expectedRevision",
                "commandSchemaVersion",
                "payload");
        assertRecordFields(
                PreviewRequest.class,
                "scenarioKey",
                "scenarioSchemaVersion",
                "triggerBindings",
                "scenarioConfig",
                "after",
                "limit");
        assertRecordFields(MarkReadRequest.class, "requestId");
        assertRecordFields(
                InternalSignalRequest.class,
                "requestId",
                "signalKey",
                "schemaVersion",
                "occurredAt",
                "subject",
                "payload");
        assertRecordFields(SignalSubjectInput.class, "definitionId", "instanceId");
        assertRecordFields(RedriveRequest.class, "requestId", "expectedStatus", "reason");
        assertRecordFields(
                ParticipantInput.class, "principalType", "principalId", "roleCode");
        assertRecordFields(
                TriggerBindingInput.class, "bindingKey", "providerKey", "schemaVersion", "config");
    }

    private static void assertRecordFields(Class<?> type, String... expectedNames) {
        assertTrue(type.isRecord(), () -> type.getName() + " must be a Java record");
        Set<String> actual = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        Set<String> expected = Set.of(expectedNames);
        assertEquals(expected, actual, () -> "Field contract mismatch on " + type.getSimpleName());
    }
}
