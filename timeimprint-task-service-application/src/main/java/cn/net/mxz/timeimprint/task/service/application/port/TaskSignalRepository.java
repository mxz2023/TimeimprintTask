package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalRecord;
import java.util.List;
import java.util.Optional;

public interface TaskSignalRepository {

    Optional<Long> findIdByTenantProviderAndSignalKey(String tenantId, String providerKey, String signalKey);

    Optional<MxzSignalRecord> findById(long signalId);

    Optional<MxzSignalRecord> findByIdForUpdate(long signalId);

    List<MxzSignalRecord> listReadyDue(java.time.Instant now, int limit);

    /** Return IDs of READY/RETRY_WAIT signals due for processing (no claim). */
    List<Long> listReadyDueIds(java.time.Instant now, int limit);

    void markSucceeded(long signalId, String resultCode, String summary, java.time.Instant processedAt);

    void markIgnored(long signalId, String resultCode, String summary, java.time.Instant processedAt);

    int countRedrives(long rootSignalId);

    long insertRedrive(MxzSignalRecord template, long rootSignalId, int redriveNo, java.time.Instant now);
}
