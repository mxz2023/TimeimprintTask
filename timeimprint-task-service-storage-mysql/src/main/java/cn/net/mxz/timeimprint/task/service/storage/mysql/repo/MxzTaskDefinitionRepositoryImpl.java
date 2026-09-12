package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskDefinitionMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MxzTaskDefinitionRepositoryImpl implements TaskDefinitionRepository {

    private final TaskDefinitionMapper mapper;

    public MxzTaskDefinitionRepositoryImpl(TaskDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<MxzTaskDefinitionSnapshot> findById(long definitionId) {
        return Optional.ofNullable(MxzRowMapper.toDefinition(mapper.selectById(definitionId)));
    }

    @Override
    public Optional<MxzTaskDefinitionSnapshot> findByIdForUpdate(long definitionId) {
        return Optional.ofNullable(MxzRowMapper.toDefinition(mapper.selectByIdForUpdate(definitionId)));
    }
}
