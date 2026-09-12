package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
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
}
