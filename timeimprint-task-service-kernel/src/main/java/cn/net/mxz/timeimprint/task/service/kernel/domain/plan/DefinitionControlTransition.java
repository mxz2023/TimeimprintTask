package cn.net.mxz.timeimprint.task.service.kernel.domain.plan;

import cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState;

/** 定义级控制状态迁移（仅 DEFINITION 目标时有效）。 */
public record DefinitionControlTransition(ControlState fromControlState, ControlState toControlState) {}
