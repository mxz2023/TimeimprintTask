package cn.net.mxz.timeimprint.task.service.application.shared.validation;

import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.definition.model.CreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.inbox.validation.RecipientRules;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component
@Profile("!test")
public class LocalParticipantCreateValidator implements ParticipantCreateValidator {

    @Override
    public void validateParticipants(
            String actorId, List<CreateDefinitionCommand.ParticipantInput> participants) {
        boolean hasOwner = false;
        for (var p : participants) {
            if (!"USER".equals(p.principalType())) {
                throw new ApplicationException("INVALID_REQUEST", "only USER principal supported");
            }
            if (!actorId.equals(p.principalId())) {
                throw new ApplicationException("INVALID_REQUEST", "local actor must match participants");
            }
            if ("OWNER".equals(p.roleCode())) {
                hasOwner = true;
            }
        }
        if (!hasOwner) {
            throw new ApplicationException("INVALID_REQUEST", "OWNER required");
        }
        RecipientRules.resolveFromInputs(
                participants.stream()
                        .map(p -> new RecipientRules.ParticipantRef(p.principalId(), p.roleCode()))
                        .toList());
    }
}
