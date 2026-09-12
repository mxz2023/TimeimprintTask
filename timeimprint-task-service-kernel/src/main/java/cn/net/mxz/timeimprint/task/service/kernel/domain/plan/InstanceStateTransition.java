package cn.net.mxz.timeimprint.task.service.kernel.domain.plan;

import cn.net.mxz.timeimprint.task.service.kernel.domain.state.LifecycleCategory;

/**
 * 实例生命周期类别与场景业务状态迁移（场景状态由场景声明，内核只保存代码）。
 */
public record InstanceStateTransition(
        LifecycleCategory fromLifecycleCategory,
        LifecycleCategory toLifecycleCategory,
        String fromScenarioState,
        String toScenarioState) {}
