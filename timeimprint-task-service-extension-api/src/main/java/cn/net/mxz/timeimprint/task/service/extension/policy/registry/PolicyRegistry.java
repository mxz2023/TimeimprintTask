package cn.net.mxz.timeimprint.task.service.extension.policy.registry;

import cn.net.mxz.timeimprint.task.service.extension.policy.spi.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.extension.policy.spi.Policy;
import java.util.List;

public interface PolicyRegistry {

    List<Policy> policiesForPhase(PolicyPhase phase);
}
