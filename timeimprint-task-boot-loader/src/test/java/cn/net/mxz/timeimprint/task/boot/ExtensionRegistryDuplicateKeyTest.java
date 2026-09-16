package cn.net.mxz.timeimprint.task.boot;

import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.net.mxz.timeimprint.task.service.application.extension.registry.ExtensionRegistryImpl;
import cn.net.mxz.timeimprint.task.service.extension.policy.spi.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.extension.policy.registry.PolicyRegistrationKey;
import cn.net.mxz.timeimprint.task.service.extension.policy.spi.Policy;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.net.mxz.timeimprint.task.service.extension.policy.context.PolicyEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.policy.result.PolicyDecision;
import org.junit.jupiter.api.Test;

class ExtensionRegistryDuplicateKeyTest {

    @Test
    void duplicatePolicyRegistrationKeyFailsStartupAssembly() {
        PolicyRegistrationKey key = new PolicyRegistrationKey("dup-policy", PolicyPhase.ACTION_EXECUTE);
        Policy first = stubPolicy(key, 1);
        Policy second = stubPolicy(key, 2);
        assertThrows(
                IllegalStateException.class,
                () -> new ExtensionRegistryImpl(
                        List.of(), List.of(), List.of(), List.of(), List.of(first, second), List.of()));
    }

    private static Policy stubPolicy(PolicyRegistrationKey key, int order) {
        return new Policy() {
            @Override
            public PolicyRegistrationKey registrationKey() {
                return key;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public cn.net.mxz.timeimprint.task.service.extension.policy.result.PolicyDecision evaluate(
                    cn.net.mxz.timeimprint.task.service.extension.policy.context.PolicyEvaluationContext context) {
                return cn.net.mxz.timeimprint.task.service.extension.policy.result.PolicyDecision.ALLOW;
            }
        };
    }
}
