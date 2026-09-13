package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MxzTaskInstanceRepositoryImpl implements TaskInstanceRepository {

    private final TaskInstanceMapper mapper;

    public MxzTaskInstanceRepositoryImpl(TaskInstanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<MxzTaskInstanceSnapshot> findById(long instanceId) {
        return Optional.ofNullable(MxzRowMapper.toInstance(mapper.selectById(instanceId)));
    }

    @Override
    public Optional<MxzTaskInstanceSnapshot> findByIdForUpdate(long instanceId) {
        return Optional.ofNullable(MxzRowMapper.toInstance(mapper.selectByIdForUpdate(instanceId)));
    }

    @Override
    public List<MxzTaskInstanceSnapshot> listByDefinition(long definitionId) {
        return mapper.selectByDefinitionIdAll(definitionId).stream().map(MxzRowMapper::toInstance).toList();
    }

    @Override
    public List<MxzTaskInstanceSnapshot> list(
            String tenantId,
            Long definitionId,
            String scenarioKey,
            String lifecycleCategory,
            String scenarioState,
            Instant from,
            Instant to,
            String cursor,
            int limit) {
        Long cursorId = parseCursor(cursor);
        return mapper.selectList(
                        tenantId,
                        definitionId,
                        scenarioKey,
                        lifecycleCategory,
                        scenarioState,
                        from == null ? null : MxzStorageTime.toUtcLdt(from),
                        to == null ? null : MxzStorageTime.toUtcLdt(to),
                        cursorId,
                        limit)
                .stream()
                .map(MxzRowMapper::toInstance)
                .toList();
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
