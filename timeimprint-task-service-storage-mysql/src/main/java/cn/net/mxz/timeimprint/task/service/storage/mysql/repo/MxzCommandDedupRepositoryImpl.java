package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.port.CommandDedupRepository.MxzCommandDedupSnapshot;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.CommandDedupMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.CommandDedupRow;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MxzCommandDedupRepositoryImpl implements CommandDedupRepository {

    private final CommandDedupMapper mapper;

    public MxzCommandDedupRepositoryImpl(CommandDedupMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<String> findCompletedResponseJson(
            String tenantId, String actorId, String operation, String requestId) {
        return find(tenantId, actorId, operation, requestId)
                .filter(s -> "COMPLETED".equals(s.processStatus()))
                .map(MxzCommandDedupSnapshot::responseJson);
    }

    @Override
    public Optional<MxzCommandDedupSnapshot> find(
            String tenantId, String actorId, String operation, String requestId) {
        var row = mapper.selectByKey(tenantId, actorId, operation, requestId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new MxzCommandDedupSnapshot(
                row.getProcessStatus(), row.getRequestHash(), row.getResponseJson()));
    }

    @Override
    public boolean tryBegin(
            String tenantId, String actorId, String operation, String requestId, byte[] requestHash) {
        var existing = mapper.selectByKey(tenantId, actorId, operation, requestId);
        if (existing != null) {
            return false;
        }
        var row = new CommandDedupRow();
        row.setTenantId(tenantId);
        row.setActorId(actorId);
        row.setOperation(operation);
        row.setRequestId(requestId);
        row.setRequestHash(requestHash);
        row.setProcessStatus("PROCESSING");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        row.setCreatedAt(MxzStorageTime.toUtcLdt(now));
        row.setUpdatedAt(MxzStorageTime.toUtcLdt(now));
        return mapper.insertProcessing(row) == 1;
    }

    @Override
    public void complete(
            String tenantId,
            String actorId,
            String operation,
            String requestId,
            String resultCode,
            String resourceType,
            String resourceId,
            Long resourceRevision,
            String responseJson) {
        mapper.completeDedup(
                tenantId,
                actorId,
                operation,
                requestId,
                resultCode,
                resourceType,
                resourceId,
                resourceRevision,
                responseJson,
                MxzStorageTime.toUtcLdt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
    }
}
