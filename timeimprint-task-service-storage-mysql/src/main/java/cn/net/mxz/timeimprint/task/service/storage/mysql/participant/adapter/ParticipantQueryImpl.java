package cn.net.mxz.timeimprint.task.service.storage.mysql.participant.adapter;

import cn.net.mxz.timeimprint.task.service.application.shared.model.ParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ParticipantQuery;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.mapper.RowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.participant.mapper.TaskParticipantMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ParticipantQueryImpl implements ParticipantQuery {

    private final TaskParticipantMapper mapper;

    public ParticipantQueryImpl(TaskParticipantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ParticipantRecord> listDefinitionLevel(long definitionId) {
        return mapper.selectByDefinitionId(definitionId, null).stream()
                .filter(r -> r.getInstanceId() == null)
                .map(RowMapper::toParticipant)
                .toList();
    }
}
