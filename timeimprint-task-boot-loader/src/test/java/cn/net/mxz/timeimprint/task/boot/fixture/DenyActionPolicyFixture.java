package cn.net.mxz.timeimprint.task.boot.fixture;

import cn.net.mxz.timeimprint.task.service.extension.policy.context.PolicyEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.policy.result.PolicyDecision;
import cn.net.mxz.timeimprint.task.service.extension.policy.spi.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.extension.policy.registry.PolicyRegistrationKey;
import cn.net.mxz.timeimprint.task.service.extension.policy.spi.Policy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** A16 test Policy: when armed, blocks ACTION_EXECUTE before side effects. */
@Component
public class DenyActionPolicyFixture implements Policy {

    public static final AtomicBoolean DENY = new AtomicBoolean(false);

    public static void reset() {
        DENY.set(false);
    }

    @Override
    public PolicyRegistrationKey registrationKey() {
        return new PolicyRegistrationKey("deny_action_fixture", PolicyPhase.ACTION_EXECUTE);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public PolicyDecision evaluate(PolicyEvaluationContext context) {
        return DENY.get() ? PolicyDecision.DENY : PolicyDecision.ALLOW;
    }
}
