package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.model.MxzActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobRepository;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.ActionJobMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MxzActionJobRepositoryImpl implements ActionJobRepository, ActionJobExecutionPort {

    private final ActionJobMapper mapper;

    public MxzActionJobRepositoryImpl(ActionJobMapper mapper) {
        this.mapper = mapper;
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
        return mapper.selectList(definitionId, instanceId, status, handlerKey, limit, cursor)
                .stream().map(MxzRowMapper::toAction).toList();
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
}
