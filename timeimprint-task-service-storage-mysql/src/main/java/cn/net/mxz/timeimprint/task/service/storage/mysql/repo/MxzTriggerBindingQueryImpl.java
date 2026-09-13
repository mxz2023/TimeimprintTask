package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.model.MxzTriggerBindingRecord;
import cn.net.mxz.timeimprint.task.service.application.port.TriggerBindingQuery;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TriggerBindingMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MxzTriggerBindingQueryImpl implements TriggerBindingQuery {

    private final TriggerBindingMapper mapper;

    public MxzTriggerBindingQueryImpl(TriggerBindingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MxzTriggerBindingRecord> listByDefinition(long definitionId) {
        return mapper.selectByDefinitionId(definitionId).stream().map(MxzRowMapper::toBinding).toList();
    }

    @Override
    public Optional<MxzTriggerBindingRecord> findById(long triggerBindingId) {
        return Optional.ofNullable(MxzRowMapper.toBinding(mapper.selectById(triggerBindingId)));
    }

    @Override
    public Optional<MxzTriggerBindingRecord> findByIdForUpdate(long triggerBindingId) {
        return Optional.ofNullable(MxzRowMapper.toBinding(mapper.selectByIdForUpdate(triggerBindingId)));
    }
}
