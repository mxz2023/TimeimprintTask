package cn.net.mxz.timeimprint.task.service.storage.mysql.command.adapter;

import cn.net.mxz.timeimprint.task.service.application.shared.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.port.CommandDedupRepository.CommandDedupSnapshot;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.command.mapper.CommandDedupMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.command.row.CommandDedupRow;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CommandDedupRepositoryImpl implements CommandDedupRepository {

    private final CommandDedupMapper mapper;

    public CommandDedupRepositoryImpl(CommandDedupMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<String> findCompletedResponseJson(
            String tenantId, String actorId, String operation, String requestId) {
        return find(tenantId, actorId, operation, requestId)
                .filter(s -> "COMPLETED".equals(s.processStatus()))
                .map(CommandDedupSnapshot::responseJson);
    }

    @Override
    public Optional<CommandDedupSnapshot> find(
            String tenantId, String actorId, String operation, String requestId) {
        var row = mapper.selectByKey(tenantId, actorId, operation, requestId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new CommandDedupSnapshot(
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
        row.setCreatedAt(StorageTime.toUtcLdt(now));
        row.setUpdatedAt(StorageTime.toUtcLdt(now));
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
                StorageTime.toUtcLdt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
    }
}
