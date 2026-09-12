package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.service.application.model.MxzParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.port.ParticipantQuery;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskParticipantMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MxzParticipantQueryImpl implements ParticipantQuery {

    private final TaskParticipantMapper mapper;

    public MxzParticipantQueryImpl(TaskParticipantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MxzParticipantRecord> listDefinitionLevel(long definitionId) {
        return mapper.selectByDefinitionId(definitionId, null).stream()
                .filter(r -> r.getInstanceId() == null)
                .map(MxzRowMapper::toParticipant)
                .toList();
    }
}
