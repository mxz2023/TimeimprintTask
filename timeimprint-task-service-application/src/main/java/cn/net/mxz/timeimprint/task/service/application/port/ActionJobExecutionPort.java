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

    /**
     * Claim READY/RETRY_WAIT → RUNNING, insert open Attempt, return executionToken.
     * Empty if not claimable.
     */
    java.util.Optional<String> claimForExecution(
            long actionJobId, String leaseOwner, java.time.Instant leaseUntil, java.time.Instant now);

    /**
     * EXTERNAL pre-call: set Attempt.effectStartedAt when still null.
     * Caller must have already verified parent control barrier in the same transaction.
     */
    boolean markEffectStarted(long actionJobId, String executionToken, java.time.Instant now);

    /** Cancel RUNNING Action that has not completed (barrier after claim / before or without effect). */
    void cancelRunning(
            long actionJobId, String executionToken, String outcomeCode, java.time.Instant completedAt);

    /** Close EXTERNAL/local RUNNING Action by executionToken CAS. */
    void completeWithToken(
            long actionJobId,
            String executionToken,
            String status,
            String outcomeCode,
            String summary,
            java.time.Instant completedAt);

    void completeAttempt(
            long actionJobId,
            String executionToken,
            String outcome,
            String errorClass,
            String errorCode,
            String summary,
            java.time.Instant finishedAt);

    /** RUNNING rows whose lease_until is before DB UTC. */
    List<Long> listExpiredRunningIds(int limit);

    /**
     * Recover one expired RUNNING lease.
     * EXTERNAL+effectStartedAt → UNKNOWN; otherwise RETRY_WAIT/DEAD.
     * @return true if this call recovered the row
     */
    boolean recoverExpiredLease(long actionJobId, java.time.Instant now);

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
