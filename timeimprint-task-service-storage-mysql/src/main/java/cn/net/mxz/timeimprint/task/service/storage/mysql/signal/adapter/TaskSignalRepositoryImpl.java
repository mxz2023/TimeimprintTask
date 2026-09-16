package cn.net.mxz.timeimprint.task.service.storage.mysql.signal.adapter;

import cn.net.mxz.timeimprint.task.service.application.signal.model.SignalRecord;
import cn.net.mxz.timeimprint.task.service.application.signal.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.mapper.RowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.signal.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.signal.row.TaskSignalRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;

@Repository
public class TaskSignalRepositoryImpl implements TaskSignalRepository {

    private final TaskSignalMapper mapper;

    public TaskSignalRepositoryImpl(TaskSignalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Long> findIdByTenantProviderAndSignalKey(String tenantId, String providerKey, String signalKey) {
        var row = mapper.selectBySourceKey(tenantId, providerKey, signalKey);
        return row == null ? Optional.empty() : Optional.of(row.getSignalId());
    }

    @Override
    public Optional<SignalRecord> findById(long signalId) {
        return Optional.ofNullable(RowMapper.toSignal(mapper.selectById(signalId)));
    }

    @Override
    public Optional<SignalRecord> findByIdForUpdate(long signalId) {
        return Optional.ofNullable(RowMapper.toSignal(mapper.selectByIdForUpdate(signalId)));
    }

    @Override
    public List<SignalRecord> listReadyDue(Instant now, int limit) {
        var nowLdt = StorageTime.toUtcLdt(now);
        // Lightweight due-list only; claim/lease happens inside processSignal's transaction.
        return mapper.selectReadyDueIds(nowLdt, limit).stream()
                .map(mapper::selectById)
                .filter(r -> r != null)
                .map(RowMapper::toSignal)
                .toList();
    }

    @Override
    public List<Long> listReadyDueIds(Instant now, int limit) {
        var nowLdt = StorageTime.toUtcLdt(now);
        return mapper.selectReadyDueIds(nowLdt, limit);
    }

    @Override
    public Optional<String> claimForProcessing(
            long signalId, String leaseOwner, Instant leaseUntil, Instant now) {
        var row = mapper.selectByIdForUpdate(signalId);
        if (row == null) {
            return Optional.empty();
        }
        String status = row.getProcessStatus();
        if (!"READY".equals(status) && !"RETRY_WAIT".equals(status)) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        int claimed = mapper.claimSignal(
                signalId,
                leaseOwner,
                StorageTime.toUtcLdt(leaseUntil),
                token,
                StorageTime.toUtcLdt(now));
        return claimed == 1 ? Optional.of(token) : Optional.empty();
    }

    @Override
    public boolean completeWithToken(
            long signalId,
            String executionToken,
            String processStatus,
            String resultCode,
            String summary,
            Instant processedAt) {
        return mapper.completeSignal(
                        signalId,
                        executionToken,
                        processStatus,
                        resultCode,
                        summary,
                        StorageTime.toUtcLdt(processedAt),
                        StorageTime.toUtcLdt(processedAt))
                == 1;
    }

    @Override
    public void markSucceeded(long signalId, String resultCode, String summary, Instant processedAt) {
        closeWithClaimIfNeeded(signalId, "SUCCEEDED", resultCode, summary, processedAt);
    }

    @Override
    public void markIgnored(long signalId, String resultCode, String summary, Instant processedAt) {
        closeWithClaimIfNeeded(signalId, "IGNORED", resultCode, summary, processedAt);
    }

    private void closeWithClaimIfNeeded(
            long signalId, String processStatus, String resultCode, String summary, Instant processedAt) {
        var row = mapper.selectByIdForUpdate(signalId);
        if (row == null) {
            return;
        }
        String token = row.getExecutionToken();
        if (token == null) {
            token = UUID.randomUUID().toString();
            int claimed = mapper.claimSignal(
                    signalId,
                    "sync",
                    StorageTime.toUtcLdt(processedAt).plusMinutes(1),
                    token,
                    StorageTime.toUtcLdt(processedAt));
            if (claimed != 1) {
                throw new cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException(
                        "STATE_CONFLICT", "signal claim failed");
            }
        }
        int closed = mapper.completeSignal(
                signalId,
                token,
                processStatus,
                resultCode,
                summary,
                StorageTime.toUtcLdt(processedAt),
                StorageTime.toUtcLdt(processedAt));
        if (closed != 1) {
            throw new cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException(
                    "STATE_CONFLICT", "signal CAS failed");
        }
    }

    @Override
    public List<Long> listExpiredRunningIds(int limit) {
        return mapper.selectExpiredRunningIds(limit);
    }

    @Override
    public boolean recoverExpiredLease(long signalId, Instant now) {
        var row = mapper.selectByIdForUpdate(signalId);
        if (row == null || !"RUNNING".equals(row.getProcessStatus()) || row.getExecutionToken() == null) {
            return false;
        }
        String token = row.getExecutionToken();
        LocalDateTime nowLdt = StorageTime.toUtcLdt(now);
        int attemptCount = row.getAttemptCount() == null ? 0 : row.getAttemptCount();
        int maxAttempts = row.getMaxAttempts() == null ? 5 : row.getMaxAttempts();
        if (attemptCount >= maxAttempts) {
            return mapper.recoverToDead(
                            signalId,
                            token,
                            "LEASE_EXPIRED",
                            "attempts exhausted after lease expiry",
                            nowLdt,
                            nowLdt)
                    == 1;
        }
        Instant next = now.plusSeconds(backoffSeconds(attemptCount));
        return mapper.recoverToRetryWait(signalId, token, StorageTime.toUtcLdt(next), nowLdt) == 1;
    }

    static long backoffSeconds(int attemptCount) {
        int[] delays = {5, 30, 120, 600};
        int idx = Math.min(Math.max(attemptCount, 1), delays.length) - 1;
        return delays[idx];
    }

    @Override
    public int countRedrives(long rootSignalId) {
        return mapper.countByParent(rootSignalId);
    }

    @Override
    public long insertRedrive(SignalRecord template, long rootSignalId, int redriveNo, Instant now) {
        var src = mapper.selectById(template.signalId());
        if (src == null) {
            throw new cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException(
                    "RESOURCE_NOT_FOUND", "signal");
        }
        TaskSignalRow row = new TaskSignalRow();
        row.setTenantId(src.getTenantId());
        row.setDefinitionId(src.getDefinitionId());
        row.setTriggerBindingId(src.getTriggerBindingId());
        row.setInstanceId(src.getInstanceId());
        row.setDefinitionControlGeneration(src.getDefinitionControlGeneration());
        row.setParentSignalId(rootSignalId);
        row.setRedriveNo(redriveNo);
        row.setProviderKey(src.getProviderKey());
        row.setSignalKey(src.getSignalKey() + ":redrive:" + redriveNo);
        row.setSchemaVersion(src.getSchemaVersion());
        row.setOccurredAt(src.getOccurredAt());
        row.setReceivedAt(StorageTime.toUtcLdt(now));
        row.setPayloadJson(src.getPayloadJson());
        row.setPayloadHash(src.getPayloadHash());
        row.setProcessStatus("READY");
        row.setAttemptCount(0);
        row.setMaxAttempts(src.getMaxAttempts());
        row.setNextAttemptAt(StorageTime.toUtcLdt(now));
        row.setCreatedAt(StorageTime.toUtcLdt(now));
        row.setUpdatedAt(StorageTime.toUtcLdt(now));
        mapper.insert(row);
        return row.getSignalId();
    }
}
