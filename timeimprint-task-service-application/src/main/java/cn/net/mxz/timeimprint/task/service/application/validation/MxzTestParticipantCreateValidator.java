package cn.net.mxz.timeimprint.task.service.application.validation;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.recipient.MxzRecipientRules;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MxzTestParticipantCreateValidator implements MxzParticipantCreateValidator {

    @Override
    public void validateParticipants(
            String actorId, List<MxzCreateDefinitionCommand.ParticipantInput> participants) {
        boolean hasOwner = false;
        for (var p : participants) {
            if (!"USER".equals(p.principalType())) {
                throw new MxzApplicationException("INVALID_REQUEST", "only USER principal supported");
            }
            if ("OWNER".equals(p.roleCode())) {
                if (!actorId.equals(p.principalId())) {
                    throw new MxzApplicationException("INVALID_REQUEST", "OWNER must match current actor");
                }
                hasOwner = true;
            } else if ("RECIPIENT".equals(p.roleCode())) {
                // test profile: RECIPIENT may differ from actor
            } else {
                throw new MxzApplicationException("INVALID_REQUEST", "unsupported role " + p.roleCode());
            }
        }
        if (!hasOwner) {
            throw new MxzApplicationException("INVALID_REQUEST", "OWNER required");
        }
        MxzRecipientRules.resolveFromInputs(
                participants.stream()
                        .map(p -> new MxzRecipientRules.ParticipantRef(p.principalId(), p.roleCode()))
                        .toList());
    }
}
