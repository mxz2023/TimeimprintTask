package cn.net.mxz.timeimprint.task.service.extension.policy.spi;

import cn.net.mxz.timeimprint.task.service.extension.policy.context.PolicyEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.policy.result.PolicyDecision;
import cn.net.mxz.timeimprint.task.service.extension.policy.registry.PolicyRegistrationKey;

public interface Policy {

    PolicyRegistrationKey registrationKey();

    int order();

    PolicyDecision evaluate(PolicyEvaluationContext context);
}
