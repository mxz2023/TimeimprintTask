package cn.net.mxz.timeimprint.task.service.application.validation;

import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.CreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.recipient.RecipientRules;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestParticipantCreateValidator implements ParticipantCreateValidator {

    @Override
    public void validateParticipants(
            String actorId, List<CreateDefinitionCommand.ParticipantInput> participants) {
        boolean hasOwner = false;
        for (var p : participants) {
            if (!"USER".equals(p.principalType())) {
                throw new ApplicationException("INVALID_REQUEST", "only USER principal supported");
            }
            if ("OWNER".equals(p.roleCode())) {
                if (!actorId.equals(p.principalId())) {
                    throw new ApplicationException("INVALID_REQUEST", "OWNER must match current actor");
                }
                hasOwner = true;
            } else if ("RECIPIENT".equals(p.roleCode())) {
                // test profile: RECIPIENT may differ from actor
            } else {
                throw new ApplicationException("INVALID_REQUEST", "unsupported role " + p.roleCode());
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
