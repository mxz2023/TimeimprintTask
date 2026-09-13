package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzAttemptRecord;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobRepository;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.ActionAttemptMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.ActionJobMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.ActionJobRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MxzActionJobRepositoryImpl implements ActionJobRepository, ActionJobExecutionPort {

    private final ActionJobMapper mapper;
    private final ActionAttemptMapper attemptMapper;

    public MxzActionJobRepositoryImpl(ActionJobMapper mapper, ActionAttemptMapper attemptMapper) {
        this.mapper = mapper;
        this.attemptMapper = attemptMapper;
    }

    @Override
    public Optional<Long> findIdForUpdate(long actionJobId) {
        var row = mapper.selectByIdForUpdate(actionJobId);
        return row == null ? Optional.empty() : Optional.of(row.getActionJobId());
    }

    @Override
    public List<MxzActionJobRecord> listByInstance(long instanceId) {
        return mapper.selectByInstanceId(instanceId).stream().map(MxzRowMapper::toAction).toList();
    }

    @Override
    public Optional<MxzActionJobRecord> findByIdForUpdate(long actionJobId) {
        return Optional.ofNullable(MxzRowMapper.toAction(mapper.selectByIdForUpdate(actionJobId)));
    }

    @Override
    public Optional<MxzActionJobRecord> findById(long actionJobId) {
        return Optional.ofNullable(MxzRowMapper.toAction(mapper.selectById(actionJobId)));
    }

    @Override
    public List<Long> listReadyDueIds(Instant now, int limit) {
        return mapper.selectReadyDueIds(MxzStorageTime.toUtcLdt(now), limit);
    }

    @Override
    public List<MxzActionJobRecord> listFiltered(Long definitionId, Long instanceId,
            String status, String handlerKey, int limit, String cursor) {
        Long cursorId = parseCursor(cursor);
        return mapper.selectList(definitionId, instanceId, status, handlerKey, limit, cursorId)
                .stream().map(MxzRowMapper::toAction).toList();
    }

    @Override
    public List<MxzAttemptRecord> listAttempts(long actionJobId) {
        return attemptMapper.selectByActionJobId(actionJobId).stream()
                .map(r -> new MxzAttemptRecord(
                        r.getAttemptNo() == null ? 0 : r.getAttemptNo(),
                        MxzStorageTime.toInstant(r.getStartedAt()),
                        MxzStorageTime.toInstant(r.getFinishedAt()),
                        MxzStorageTime.toInstant(r.getEffectStartedAt()),
                        r.getOutcome(),
                        r.getErrorClass(),
                        r.getErrorCode(),
                        r.getProviderReference(),
                        r.getSafeSummary()))
                .toList();
    }

    @Override
    public int countRedrives(long rootActionJobId) {
        return mapper.countByParent(rootActionJobId);
    }

    @Override
    public long insertRedrive(MxzActionJobRecord template, long rootActionJobId, int redriveNo, Instant now) {
        var src = mapper.selectById(template.actionJobId());
        if (src == null) {
            throw new MxzApplicationException("RESOURCE_NOT_FOUND", "actionJob");
        }
        ActionJobRow row = new ActionJobRow();
        row.setTenantId(src.getTenantId());
        row.setDefinitionId(src.getDefinitionId());
        row.setInstanceId(src.getInstanceId());
        row.setTransitionId(src.getTransitionId());
        row.setDefinitionControlGeneration(src.getDefinitionControlGeneration());
        row.setParentActionJobId(rootActionJobId);
        row.setRedriveNo(redriveNo);
        row.setHandlerKey(src.getHandlerKey());
        row.setActionKey(src.getActionKey() + ":redrive:" + redriveNo);
        row.setExecutionMode(src.getExecutionMode());
        row.setSchemaVersion(src.getSchemaVersion());
        row.setTargetType(src.getTargetType());
        row.setTargetId(src.getTargetId());
        row.setPayloadJson(src.getPayloadJson());
        row.setPayloadHash(src.getPayloadHash());
        row.setAvailableAt(MxzStorageTime.toUtcLdt(now));
        row.setExpiresAt(src.getExpiresAt());
        row.setStatus("READY");
        row.setAttemptCount(0);
        row.setMaxAttempts(src.getMaxAttempts());
        row.setNextAttemptAt(MxzStorageTime.toUtcLdt(now));
        row.setCreatedAt(MxzStorageTime.toUtcLdt(now));
        row.setUpdatedAt(MxzStorageTime.toUtcLdt(now));
        mapper.insert(row);
        return row.getActionJobId();
    }

    @Override
    public void markSucceeded(long actionJobId, String outcomeCode, String summary, Instant completedAt) {
        var row = mapper.selectByIdForUpdate(actionJobId);
        if (row == null) {
            return;
        }
        String token = row.getExecutionToken();
        if (token == null) {
            token = UUID.randomUUID().toString();
            mapper.claimAction(
                    actionJobId,
                    "local-tx",
                    MxzStorageTime.toUtcLdt(completedAt).plusMinutes(1),
                    token,
                    MxzStorageTime.toUtcLdt(completedAt));
        }
        mapper.completeAction(
                actionJobId,
                token,
                "SUCCEEDED",
                outcomeCode,
                summary,
                MxzStorageTime.toUtcLdt(completedAt),
                MxzStorageTime.toUtcLdt(completedAt));
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new MxzApplicationException("INVALID_CURSOR", "cursor");
        }
    }
}
