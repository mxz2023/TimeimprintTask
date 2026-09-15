package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.SignalRecord;
import java.util.List;
import java.util.Optional;

public interface TaskSignalRepository {

    Optional<Long> findIdByTenantProviderAndSignalKey(String tenantId, String providerKey, String signalKey);

    Optional<SignalRecord> findById(long signalId);

    Optional<SignalRecord> findByIdForUpdate(long signalId);

    List<SignalRecord> listReadyDue(java.time.Instant now, int limit);

    /** Return IDs of READY/RETRY_WAIT signals due for processing (no claim). */
    List<Long> listReadyDueIds(java.time.Instant now, int limit);

    /**
     * Claim READY/RETRY_WAIT → RUNNING with lease + executionToken.
     * Empty if not claimable.
     */
    java.util.Optional<String> claimForProcessing(
            long signalId, String leaseOwner, java.time.Instant leaseUntil, java.time.Instant now);

    /** Close RUNNING Signal by executionToken CAS. @return true if CAS matched. */
    boolean completeWithToken(
            long signalId,
            String executionToken,
            String processStatus,
            String resultCode,
            String summary,
            java.time.Instant processedAt);

    void markSucceeded(long signalId, String resultCode, String summary, java.time.Instant processedAt);

    void markIgnored(long signalId, String resultCode, String summary, java.time.Instant processedAt);

    /** RUNNING rows whose lease_until is before DB UTC. */
    List<Long> listExpiredRunningIds(int limit);

    /**
     * Recover one expired RUNNING lease → RETRY_WAIT or DEAD.
     * @return true if this call recovered the row
     */
    boolean recoverExpiredLease(long signalId, java.time.Instant now);

    int countRedrives(long rootSignalId);

    long insertRedrive(SignalRecord template, long rootSignalId, int redriveNo, java.time.Instant now);
}
