package cn.net.mxz.timeimprint.task.service.storage.mysql.definition.adapter;

import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.mapper.RowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.definition.mapper.TaskDefinitionMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TaskDefinitionRepositoryImpl implements TaskDefinitionRepository {

    private final TaskDefinitionMapper mapper;

    public TaskDefinitionRepositoryImpl(TaskDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TaskDefinitionSnapshot> findById(long definitionId) {
        return Optional.ofNullable(RowMapper.toDefinition(mapper.selectById(definitionId)));
    }

    @Override
    public Optional<TaskDefinitionSnapshot> findByIdForUpdate(long definitionId) {
        return Optional.ofNullable(RowMapper.toDefinition(mapper.selectByIdForUpdate(definitionId)));
    }

    @Override
    public List<TaskDefinitionSnapshot> list(
            String tenantId,
            String scenarioKey,
            String controlState,
            Instant cursorUpdatedAt,
            Long cursorDefinitionId,
            int limit) {
        return mapper.selectList(
                        tenantId,
                        scenarioKey,
                        controlState,
                        cursorUpdatedAt == null ? null : StorageTime.toUtcLdt(cursorUpdatedAt),
                        cursorDefinitionId,
                        limit)
                .stream()
                .map(RowMapper::toDefinition)
                .toList();
    }
}
