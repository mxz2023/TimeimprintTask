package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.MxzActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzAttemptRecord;
import java.util.List;
import java.util.Optional;

public interface ActionJobExecutionPort {

    List<MxzActionJobRecord> listByInstance(long instanceId);

    Optional<MxzActionJobRecord> findByIdForUpdate(long actionJobId);

    void markSucceeded(long actionJobId, String outcomeCode, String summary, java.time.Instant completedAt);

    /** Permanent cancel for READY/RETRY_WAIT when control barrier rejects execution. */
    void markCancelled(long actionJobId, String outcomeCode, java.time.Instant completedAt);

    Optional<MxzActionJobRecord> findById(long actionJobId);

    /** List READY action jobs due for execution (for worker polling, no lock). */
    List<Long> listReadyDueIds(java.time.Instant now, int limit);

    /** List action jobs with optional filters (for I03 diagnostic). */
    List<MxzActionJobRecord> listFiltered(Long definitionId, Long instanceId,
            String status, String handlerKey, int limit, String cursor);

    List<MxzAttemptRecord> listAttempts(long actionJobId);

    int countRedrives(long rootActionJobId);

    long insertRedrive(MxzActionJobRecord template, long rootActionJobId, int redriveNo, java.time.Instant now);
}
