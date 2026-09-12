package cn.net.mxz.timeimprint.task.service.extension.registry;

import cn.net.mxz.timeimprint.task.service.extension.policy.PolicyPhase;

public record PolicyRegistrationKey(String policyKey, PolicyPhase phase) {
    public PolicyRegistrationKey {
        if (policyKey == null || policyKey.isBlank()) {
            throw new IllegalArgumentException("policyKey required");
        }
        if (phase == null) {
            throw new IllegalArgumentException("phase required");
        }
    }
}
