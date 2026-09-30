package cn.net.mxz.timeimprint.task.service.application.shared.port;

import cn.net.mxz.timeimprint.task.service.application.shared.model.ParticipantRecord;
import java.util.List;

public interface ParticipantQuery {

    List<ParticipantRecord> listDefinitionLevel(long definitionId);
}
