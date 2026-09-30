package cn.net.mxz.timeimprint.task.service.application.shared.validation;

import cn.net.mxz.timeimprint.task.service.application.definition.model.CreateDefinitionCommand;
import java.util.List;
public interface ParticipantCreateValidator {

    void validateParticipants(String actorId, List<CreateDefinitionCommand.ParticipantInput> participants);
}
