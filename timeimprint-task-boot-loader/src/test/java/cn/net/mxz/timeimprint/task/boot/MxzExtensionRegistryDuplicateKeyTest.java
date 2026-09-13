package cn.net.mxz.timeimprint.task.boot;

import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.net.mxz.timeimprint.task.service.application.registry.MxzExtensionRegistryImpl;
import cn.net.mxz.timeimprint.task.service.extension.policy.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.extension.registry.PolicyRegistrationKey;
import cn.net.mxz.timeimprint.task.service.extension.spi.Policy;
import java.util.List;
import org.junit.jupiter.api.Test;

class MxzExtensionRegistryDuplicateKeyTest {

    @Test
    void duplicatePolicyRegistrationKeyFailsStartupAssembly() {
        PolicyRegistrationKey key = new PolicyRegistrationKey("dup-policy", PolicyPhase.ACTION_EXECUTE);
        Policy first = stubPolicy(key, 1);
        Policy second = stubPolicy(key, 2);
        assertThrows(
                IllegalStateException.class,
                () -> new MxzExtensionRegistryImpl(
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
            public cn.net.mxz.timeimprint.task.service.extension.policy.PolicyDecision evaluate(
                    cn.net.mxz.timeimprint.task.service.extension.context.MxzPolicyEvaluationContext context) {
                return cn.net.mxz.timeimprint.task.service.extension.policy.PolicyDecision.ALLOW;
            }
        };
    }
}
