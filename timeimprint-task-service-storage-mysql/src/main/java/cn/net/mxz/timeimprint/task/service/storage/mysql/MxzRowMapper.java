package cn.net.mxz.timeimprint.task.service.storage.mysql;

import cn.net.mxz.timeimprint.task.service.application.model.MxzActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzTriggerBindingRecord;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.ActionJobRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskDefinitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskParticipantRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskSignalRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TriggerBindingRow;

public final class MxzRowMapper {
    private MxzRowMapper() {}

    public static MxzTaskDefinitionSnapshot toDefinition(TaskDefinitionRow r) {
        if (r == null) return null;
        return new MxzTaskDefinitionSnapshot(
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
                MxzStorageTime.toInstant(r.getCreatedAt()),
                MxzStorageTime.toInstant(r.getUpdatedAt()));
    }

    public static MxzTaskInstanceSnapshot toInstance(TaskInstanceRow r) {
        if (r == null) return null;
        return new MxzTaskInstanceSnapshot(
                r.getInstanceId(),
                r.getDefinitionId(),
                r.getTriggerBindingId(),
                r.getScheduleGeneration(),
                r.getDefinitionControlGeneration(),
                r.getOccurrenceKey(),
                MxzStorageTime.toInstant(r.getOccurrenceAt()),
                MxzStorageTime.toInstant(r.getDueAt()),
                LifecycleCategory.valueOf(r.getLifecycleCategory()),
                r.getScenarioState(),
                r.getScenarioSchemaVersion(),
                r.getScenarioSnapshotJson(),
                r.getTitleSnapshot(),
                r.getDescriptionSnapshot(),
                r.getRevision(),
                MxzStorageTime.toInstant(r.getTerminalAt()));
    }

    public static MxzParticipantRecord toParticipant(TaskParticipantRow r) {
        return new MxzParticipantRecord(
                r.getParticipantId(),
                r.getDefinitionId(),
                r.getInstanceId(),
                r.getPrincipalType(),
                r.getPrincipalId(),
                r.getRoleCode(),
                r.getSourceCode());
    }

    public static MxzTriggerBindingRecord toBinding(TriggerBindingRow r) {
        return new MxzTriggerBindingRecord(
                r.getTriggerBindingId(),
                r.getDefinitionId(),
                r.getBindingKey(),
                r.getProviderKey(),
                r.getSchemaVersion(),
                r.getConfigJson(),
                r.getBindingState(),
                r.getScheduleGeneration(),
                MxzStorageTime.toInstant(r.getNextFireAt()),
                Boolean.TRUE.equals(r.getExhausted()),
                r.getRevision());
    }

    public static MxzSignalRecord toSignal(TaskSignalRow r) {
        if (r == null) return null;
        return new MxzSignalRecord(
                r.getSignalId(),
                r.getTenantId(),
                r.getDefinitionId(),
                r.getTriggerBindingId(),
                r.getInstanceId(),
                r.getDefinitionControlGeneration(),
                r.getProviderKey(),
                r.getSignalKey(),
                r.getSchemaVersion(),
                MxzStorageTime.toInstant(r.getOccurredAt()),
                MxzStorageTime.toInstant(r.getReceivedAt()),
                r.getPayloadJson(),
                r.getProcessStatus(),
                r.getAttemptCount() == null ? 0 : r.getAttemptCount(),
                r.getMaxAttempts() == null ? 0 : r.getMaxAttempts(),
                MxzStorageTime.toInstant(r.getNextAttemptAt()),
                r.getResultCode(),
                MxzStorageTime.toInstant(r.getProcessedAt()),
                r.getParentSignalId(),
                r.getRedriveNo() == null ? 0 : r.getRedriveNo(),
                r.getLeaseOwner(),
                MxzStorageTime.toInstant(r.getLeaseUntil()),
                r.getResultSummary());
    }

    public static MxzActionJobRecord toAction(ActionJobRow r) {
        if (r == null) return null;
        return new MxzActionJobRecord(
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
                MxzStorageTime.toInstant(r.getAvailableAt()),
                MxzStorageTime.toInstant(r.getExpiresAt()),
                r.getStatus(),
                r.getAttemptCount() == null ? 0 : r.getAttemptCount(),
                r.getMaxAttempts() == null ? 0 : r.getMaxAttempts(),
                MxzStorageTime.toInstant(r.getNextAttemptAt()),
                r.getLeaseOwner(),
                MxzStorageTime.toInstant(r.getLeaseUntil()),
                r.getOutcomeCode(),
                r.getOutcomeSummary(),
                MxzStorageTime.toInstant(r.getCompletedAt()),
                r.getParentActionJobId(),
                r.getRedriveNo() == null ? 0 : r.getRedriveNo());
    }
}
