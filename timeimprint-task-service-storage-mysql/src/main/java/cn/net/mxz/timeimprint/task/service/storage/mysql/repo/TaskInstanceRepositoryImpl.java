package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.storage.mysql.RowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TaskInstanceRepositoryImpl implements TaskInstanceRepository {

    private final TaskInstanceMapper mapper;

    public TaskInstanceRepositoryImpl(TaskInstanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TaskInstanceSnapshot> findById(long instanceId) {
        return Optional.ofNullable(RowMapper.toInstance(mapper.selectById(instanceId)));
    }

    @Override
    public Optional<TaskInstanceSnapshot> findByIdForUpdate(long instanceId) {
        return Optional.ofNullable(RowMapper.toInstance(mapper.selectByIdForUpdate(instanceId)));
    }

    @Override
    public List<TaskInstanceSnapshot> listByDefinition(long definitionId) {
        return mapper.selectByDefinitionIdAll(definitionId).stream().map(RowMapper::toInstance).toList();
    }

    @Override
    public List<TaskInstanceSnapshot> list(
            String tenantId,
            Long definitionId,
            String scenarioKey,
            String lifecycleCategory,
            String scenarioState,
            Instant from,
            Instant to,
            Instant cursorOccurrenceAt,
            Long cursorInstanceId,
            int limit) {
        return mapper.selectList(
                        tenantId,
                        definitionId,
                        scenarioKey,
                        lifecycleCategory,
                        scenarioState,
                        from == null ? null : StorageTime.toUtcLdt(from),
                        to == null ? null : StorageTime.toUtcLdt(to),
                        cursorOccurrenceAt == null ? null : StorageTime.toUtcLdt(cursorOccurrenceAt),
                        cursorInstanceId,
                        limit)
                .stream()
                .map(RowMapper::toInstance)
                .toList();
    }
}
