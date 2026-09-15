package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.ParticipantRecord;
import java.util.List;

public interface ParticipantQuery {

    List<ParticipantRecord> listDefinitionLevel(long definitionId);
}
