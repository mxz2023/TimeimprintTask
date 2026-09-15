package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.ActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.model.AttemptRecord;
import java.util.List;
import java.util.Optional;

public interface ActionJobExecutionPort {

    List<ActionJobRecord> listByInstance(long instanceId);

    Optional<ActionJobRecord> findByIdForUpdate(long actionJobId);

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

    /** Close EXTERNAL/local RUNNING Action by executionToken CAS. @return true if CAS matched. */
    boolean completeWithToken(
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

    /**
     * If READY/RETRY_WAIT and expiresAt &lt;= now, mark EXPIRED.
     * @return true if expired by this call
     */
    boolean expireIfDue(long actionJobId, java.time.Instant now);

    /**
     * Technical RETRYABLE_FAILURE: RUNNING → RETRY_WAIT with backoff, or DEAD if attempts exhausted.
     */
    void completeRetryableFailure(
            long actionJobId, String executionToken, String outcomeCode, String summary, java.time.Instant now);

    /**
     * Policy barrier before side-effect: close Attempt as POLICY_BLOCKED, refund attempt_count, READY.
     */
    void releasePolicyBlocked(
            long actionJobId, String executionToken, String outcomeCode, java.time.Instant now);

    Optional<ActionJobRecord> findById(long actionJobId);

    /** List READY action jobs due for execution (for worker polling, no lock). */
    List<Long> listReadyDueIds(java.time.Instant now, int limit);

    /**
     * Fairness share: due READY/RETRY_WAIT ordered by newest {@code available_at} first
     * (06 §10 time-slice for fresh work alongside oldest-first drain).
     */
    List<Long> listReadyDueIdsNewestFirst(java.time.Instant now, int limit);

    /** List action jobs with optional filters (for I03 diagnostic). */
    List<ActionJobRecord> listFiltered(Long definitionId, Long instanceId,
            String status, String handlerKey, int limit, String cursor);

    List<AttemptRecord> listAttempts(long actionJobId);

    int countRedrives(long rootActionJobId);

    long insertRedrive(ActionJobRecord template, long rootActionJobId, int redriveNo, java.time.Instant now);
}
