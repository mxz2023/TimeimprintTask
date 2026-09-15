package cn.net.mxz.timeimprint.task.service.extension.spi;

import cn.net.mxz.timeimprint.task.service.extension.context.PolicyEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.policy.PolicyDecision;
import cn.net.mxz.timeimprint.task.service.extension.registry.PolicyRegistrationKey;

public interface Policy {

    PolicyRegistrationKey registrationKey();

    int order();

    PolicyDecision evaluate(PolicyEvaluationContext context);
}
