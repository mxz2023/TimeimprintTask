package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskDefinitionSnapshot;

/** 创建定义时的初始实例/Signal 规划输入。 */
public record InitialDefinitionContext(TaskDefinitionSnapshot definitionSnapshot) {}
