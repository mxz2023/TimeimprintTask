package cn.net.mxz.timeimprint.task.service.extension.registry;

import cn.net.mxz.timeimprint.task.service.extension.policy.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.extension.spi.Policy;
import java.util.List;

public interface PolicyRegistry {

    List<Policy> policiesForPhase(PolicyPhase phase);
}
