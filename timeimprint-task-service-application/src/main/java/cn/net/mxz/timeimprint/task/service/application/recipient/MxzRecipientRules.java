package cn.net.mxz.timeimprint.task.service.application.recipient;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzParticipantRecord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * S01/S02 公共接收人规则：无 RECIPIENT 时回退 OWNER；有 RECIPIENT 时不隐式含 OWNER；去重后最多 10 人。
 */
public final class MxzRecipientRules {

    public static final int MAX_RECIPIENTS = 10;

    private MxzRecipientRules() {}

    public static List<String> resolve(List<MxzParticipantRecord> participants) {
        Set<String> recipients = new LinkedHashSet<>();
        Set<String> owners = new LinkedHashSet<>();
        for (var p : participants) {
            if ("RECIPIENT".equals(p.roleCode())) {
                recipients.add(p.principalId());
            } else if ("OWNER".equals(p.roleCode())) {
                owners.add(p.principalId());
            }
        }
        List<String> finalRecipients =
                recipients.isEmpty() ? List.copyOf(owners) : List.copyOf(recipients);
        if (finalRecipients.isEmpty()) {
            throw new MxzApplicationException("INVALID_REQUEST", "no recipients");
        }
        if (finalRecipients.size() > MAX_RECIPIENTS) {
            throw new MxzApplicationException("INVALID_REQUEST", "too many recipients");
        }
        return finalRecipients;
    }

    /** Create-time check from command inputs (principalId + roleCode). */
    public static List<String> resolveFromInputs(List<ParticipantRef> inputs) {
        Set<String> recipients = new LinkedHashSet<>();
        Set<String> owners = new LinkedHashSet<>();
        for (var p : inputs) {
            if ("RECIPIENT".equals(p.roleCode())) {
                recipients.add(p.principalId());
            } else if ("OWNER".equals(p.roleCode())) {
                owners.add(p.principalId());
            }
        }
        List<String> finalRecipients =
                recipients.isEmpty() ? List.copyOf(owners) : List.copyOf(recipients);
        if (finalRecipients.isEmpty()) {
            throw new MxzApplicationException("INVALID_REQUEST", "no recipients");
        }
        if (finalRecipients.size() > MAX_RECIPIENTS) {
            throw new MxzApplicationException("INVALID_REQUEST", "too many recipients");
        }
        return finalRecipients;
    }

    public record ParticipantRef(String principalId, String roleCode) {}
}
