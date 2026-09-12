package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalRecord;
import cn.net.mxz.timeimprint.task.service.application.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskSignalMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MxzTaskSignalRepositoryImpl implements TaskSignalRepository {

    private final TaskSignalMapper mapper;

    public MxzTaskSignalRepositoryImpl(TaskSignalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Long> findIdByTenantProviderAndSignalKey(String tenantId, String providerKey, String signalKey) {
        var row = mapper.selectBySourceKey(tenantId, providerKey, signalKey);
        return row == null ? Optional.empty() : Optional.of(row.getSignalId());
    }

    @Override
    public Optional<MxzSignalRecord> findById(long signalId) {
        return Optional.ofNullable(MxzRowMapper.toSignal(mapper.selectById(signalId)));
    }

    @Override
    public Optional<MxzSignalRecord> findByIdForUpdate(long signalId) {
        return Optional.ofNullable(MxzRowMapper.toSignal(mapper.selectByIdForUpdate(signalId)));
    }

    @Override
    public List<MxzSignalRecord> listReadyDue(Instant now, int limit) {
        var nowLdt = MxzStorageTime.toUtcLdt(now);
        // Lightweight due-list only; claim/lease happens inside processSignal's transaction.
        return mapper.selectReadyDueIds(nowLdt, limit).stream()
                .map(mapper::selectById)
                .filter(r -> r != null)
                .map(MxzRowMapper::toSignal)
                .toList();
    }

    @Override
    public List<Long> listReadyDueIds(Instant now, int limit) {
        var nowLdt = MxzStorageTime.toUtcLdt(now);
        return mapper.selectReadyDueIds(nowLdt, limit);
    }

    @Override
    public void markSucceeded(long signalId, String resultCode, String summary, Instant processedAt) {
        var row = mapper.selectByIdForUpdate(signalId);
        if (row == null) {
            return;
        }
        String token = row.getExecutionToken() == null ? UUID.randomUUID().toString() : row.getExecutionToken();
        if (row.getExecutionToken() == null) {
            mapper.claimSignal(
                    signalId,
                    "sync",
                    MxzStorageTime.toUtcLdt(processedAt).plusMinutes(1),
                    token,
                    MxzStorageTime.toUtcLdt(processedAt));
        }
        mapper.completeSignal(
                signalId,
                token,
                "SUCCEEDED",
                resultCode,
                summary,
                MxzStorageTime.toUtcLdt(processedAt),
                MxzStorageTime.toUtcLdt(processedAt));
    }

    @Override
    public void markIgnored(long signalId, String resultCode, String summary, Instant processedAt) {
        var row = mapper.selectByIdForUpdate(signalId);
        if (row == null) {
            return;
        }
        String token = row.getExecutionToken() == null ? UUID.randomUUID().toString() : row.getExecutionToken();
        if (row.getExecutionToken() == null) {
            mapper.claimSignal(
                    signalId,
                    "sync",
                    MxzStorageTime.toUtcLdt(processedAt).plusMinutes(1),
                    token,
                    MxzStorageTime.toUtcLdt(processedAt));
        }
        mapper.completeSignal(
                signalId,
                token,
                "IGNORED",
                resultCode,
                summary,
                MxzStorageTime.toUtcLdt(processedAt),
                MxzStorageTime.toUtcLdt(processedAt));
    }
}
