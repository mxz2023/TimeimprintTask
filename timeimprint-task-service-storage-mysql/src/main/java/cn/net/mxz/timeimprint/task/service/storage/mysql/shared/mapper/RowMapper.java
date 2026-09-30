package cn.net.mxz.timeimprint.task.service.storage.mysql.shared.mapper;

import cn.net.mxz.timeimprint.task.service.application.action.model.ActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.signal.model.SignalRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.model.TriggerBindingRecord;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.storage.mysql.action.row.ActionJobRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.definition.row.TaskDefinitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.instance.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.participant.row.TaskParticipantRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.signal.row.TaskSignalRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.row.TriggerBindingRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time.StorageTime;

public final class RowMapper {
    private RowMapper() {}

    public static TaskDefinitionSnapshot toDefinition(TaskDefinitionRow r) {
        if (r == null) return null;
        return new TaskDefinitionSnapshot(
                r.getDefinitionId(),
                r.getTenantId(),
                r.getScenarioKey(),
                r.getScenarioSchemaVersion(),
                r.getTitle(),
                r.getDescription(),
                r.getScenarioConfigJson(),
                ControlState.valueOf(r.getControlState()),
                r.getControlGeneration(),
                r.getRevision(),
                StorageTime.toInstant(r.getCreatedAt()),
                StorageTime.toInstant(r.getUpdatedAt()));
    }

    public static TaskInstanceSnapshot toInstance(TaskInstanceRow r) {
        if (r == null) return null;
        return new TaskInstanceSnapshot(
                r.getInstanceId(),
                r.getDefinitionId(),
                r.getTriggerBindingId(),
                r.getScheduleGeneration(),
                r.getDefinitionControlGeneration(),
                r.getOccurrenceKey(),
                StorageTime.toInstant(r.getOccurrenceAt()),
                StorageTime.toInstant(r.getDueAt()),
                LifecycleCategory.valueOf(r.getLifecycleCategory()),
                r.getScenarioState(),
                r.getScenarioSchemaVersion(),
                r.getScenarioSnapshotJson(),
                r.getTitleSnapshot(),
                r.getDescriptionSnapshot(),
                r.getRevision(),
                StorageTime.toInstant(r.getTerminalAt()));
    }

    public static ParticipantRecord toParticipant(TaskParticipantRow r) {
        return new ParticipantRecord(
                r.getParticipantId(),
                r.getDefinitionId(),
                r.getInstanceId(),
                r.getPrincipalType(),
                r.getPrincipalId(),
                r.getRoleCode(),
                r.getSourceCode());
    }

    public static TriggerBindingRecord toBinding(TriggerBindingRow r) {
        return new TriggerBindingRecord(
                r.getTriggerBindingId(),
                r.getDefinitionId(),
                r.getBindingKey(),
                r.getProviderKey(),
                r.getSchemaVersion(),
                r.getConfigJson(),
                r.getBindingState(),
                r.getScheduleGeneration(),
                StorageTime.toInstant(r.getNextFireAt()),
                Boolean.TRUE.equals(r.getExhausted()),
                r.getRevision());
    }

    public static SignalRecord toSignal(TaskSignalRow r) {
        if (r == null) return null;
        return new SignalRecord(
                r.getSignalId(),
                r.getTenantId(),
                r.getDefinitionId(),
                r.getTriggerBindingId(),
                r.getInstanceId(),
                r.getDefinitionControlGeneration(),
                r.getProviderKey(),
                r.getSignalKey(),
                r.getSchemaVersion(),
                StorageTime.toInstant(r.getOccurredAt()),
                StorageTime.toInstant(r.getReceivedAt()),
                r.getPayloadJson(),
                r.getProcessStatus(),
                r.getAttemptCount() == null ? 0 : r.getAttemptCount(),
                r.getMaxAttempts() == null ? 0 : r.getMaxAttempts(),
                StorageTime.toInstant(r.getNextAttemptAt()),
                r.getResultCode(),
                StorageTime.toInstant(r.getProcessedAt()),
                r.getParentSignalId(),
                r.getRedriveNo() == null ? 0 : r.getRedriveNo(),
                r.getLeaseOwner(),
                StorageTime.toInstant(r.getLeaseUntil()),
                r.getExecutionToken(),
                r.getResultSummary());
    }

    public static ActionJobRecord toAction(ActionJobRow r) {
        if (r == null) return null;
        return new ActionJobRecord(
                r.getActionJobId(),
                r.getTenantId(),
                r.getDefinitionId(),
                r.getInstanceId(),
                r.getTransitionId() == null ? 0L : r.getTransitionId(),
                r.getDefinitionControlGeneration() == null ? 0L : r.getDefinitionControlGeneration(),
                r.getHandlerKey(),
                r.getActionKey(),
                r.getExecutionMode(),
                r.getSchemaVersion() == null ? 0 : r.getSchemaVersion(),
                r.getTargetType(),
                r.getTargetId(),
                r.getPayloadJson(),
                StorageTime.toInstant(r.getAvailableAt()),
                StorageTime.toInstant(r.getExpiresAt()),
                r.getStatus(),
                r.getAttemptCount() == null ? 0 : r.getAttemptCount(),
                r.getMaxAttempts() == null ? 0 : r.getMaxAttempts(),
                StorageTime.toInstant(r.getNextAttemptAt()),
                r.getLeaseOwner(),
                StorageTime.toInstant(r.getLeaseUntil()),
                r.getOutcomeCode(),
                r.getOutcomeSummary(),
                StorageTime.toInstant(r.getCompletedAt()),
                r.getParentActionJobId(),
                r.getRedriveNo() == null ? 0 : r.getRedriveNo());
    }
}
