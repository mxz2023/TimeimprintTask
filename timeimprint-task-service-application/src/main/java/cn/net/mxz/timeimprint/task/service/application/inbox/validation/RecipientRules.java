package cn.net.mxz.timeimprint.task.service.application.inbox.validation;

import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.shared.limit.PlatformLimits;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ParticipantRecord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * S01/S02 公共接收人规则：无 RECIPIENT 时回退 OWNER；有 RECIPIENT 时不隐式含 OWNER；去重后最多 10 人。
 */
public final class RecipientRules {

    public static final int MAX_RECIPIENTS = PlatformLimits.MAX_RECIPIENTS;

    private RecipientRules() {}

    public static List<String> resolve(List<ParticipantRecord> participants) {
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
            throw new ApplicationException("INVALID_REQUEST", "no recipients");
        }
        if (finalRecipients.size() > MAX_RECIPIENTS) {
            throw new ApplicationException("INVALID_REQUEST", "too many recipients");
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
            throw new ApplicationException("INVALID_REQUEST", "no recipients");
        }
        if (finalRecipients.size() > MAX_RECIPIENTS) {
            throw new ApplicationException("INVALID_REQUEST", "too many recipients");
        }
        return finalRecipients;
    }

    public record ParticipantRef(String principalId, String roleCode) {}
}
