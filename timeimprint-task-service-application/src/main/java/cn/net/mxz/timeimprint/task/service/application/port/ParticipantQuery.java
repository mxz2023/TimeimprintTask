package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.MxzParticipantRecord;
import java.util.List;

public interface ParticipantQuery {

    List<MxzParticipantRecord> listDefinitionLevel(long definitionId);
}
