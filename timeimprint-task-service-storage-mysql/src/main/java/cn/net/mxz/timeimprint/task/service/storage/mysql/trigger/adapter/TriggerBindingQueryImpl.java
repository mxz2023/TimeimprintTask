package cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.adapter;

import cn.net.mxz.timeimprint.task.service.application.shared.model.TriggerBindingRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.port.TriggerBindingQuery;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.mapper.RowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.mapper.TriggerBindingMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TriggerBindingQueryImpl implements TriggerBindingQuery {

    private final TriggerBindingMapper mapper;

    public TriggerBindingQueryImpl(TriggerBindingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TriggerBindingRecord> listByDefinition(long definitionId) {
        return mapper.selectByDefinitionId(definitionId).stream().map(RowMapper::toBinding).toList();
    }

    @Override
    public Optional<TriggerBindingRecord> findById(long triggerBindingId) {
        return Optional.ofNullable(RowMapper.toBinding(mapper.selectById(triggerBindingId)));
    }

    @Override
    public Optional<TriggerBindingRecord> findByIdForUpdate(long triggerBindingId) {
        return Optional.ofNullable(RowMapper.toBinding(mapper.selectByIdForUpdate(triggerBindingId)));
    }
}
