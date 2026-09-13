package cn.net.mxz.timeimprint.task.service.application.validation;

import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import java.util.List;

public interface MxzParticipantCreateValidator {

    void validateParticipants(String actorId, List<MxzCreateDefinitionCommand.ParticipantInput> participants);
}
